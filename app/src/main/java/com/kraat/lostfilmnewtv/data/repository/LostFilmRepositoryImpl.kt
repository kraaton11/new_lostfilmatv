package com.kraat.lostfilmnewtv.data.repository

import com.kraat.lostfilmnewtv.data.db.PageCacheMetadataEntity
import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.TorrentLink
import com.kraat.lostfilmnewtv.data.network.LostFilmHttpClient
import com.kraat.lostfilmnewtv.data.parser.LostFilmDetailsParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmListParser
import com.kraat.lostfilmnewtv.data.parser.resolveUrl
import com.kraat.lostfilmnewtv.data.parser.toEntity
import com.kraat.lostfilmnewtv.data.parser.toSummaryEntities
import com.kraat.lostfilmnewtv.data.parser.toSummaryModels
import java.io.IOException
import org.jsoup.Jsoup

private const val FRESH_WINDOW_MS = 6 * 60 * 60 * 1000L
private const val RETENTION_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L
private val paginatorRegex = Regex("""/new/page_(\d+)""")

class LostFilmRepositoryImpl(
    private val httpClient: LostFilmHttpClient,
    private val releaseDao: ReleaseDao,
    private val listParser: LostFilmListParser,
    private val detailsParser: LostFilmDetailsParser,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : LostFilmRepository {
    override suspend fun loadPage(pageNumber: Int): PageState {
        cleanupExpiredData()

        return try {
            val fetchedAt = clock()
            val html = httpClient.fetchNewPage(pageNumber)
            val parsedItems = listParser.parse(
                html = html,
                pageNumber = pageNumber,
                fetchedAt = fetchedAt,
            )

            releaseDao.replacePage(
                pageNumber = pageNumber,
                summaries = parsedItems.toSummaryEntities(),
                metadata = PageCacheMetadataEntity(
                    pageNumber = pageNumber,
                    fetchedAt = fetchedAt,
                    itemCount = parsedItems.size,
                ),
            )

            PageState.Content(
                pageNumber = pageNumber,
                items = releaseDao.getSummariesUpToPage(pageNumber).toSummaryModels(),
                hasNextPage = hasNextPage(html, pageNumber, parsedItems.isNotEmpty()),
                isStale = false,
            )
        } catch (exception: Exception) {
            if (exception is IOException || exception is IllegalStateException) {
                fallbackPageState(pageNumber, exception)
            } else {
                throw exception
            }
        }
    }

    override suspend fun loadDetails(detailsUrl: String): DetailsResult {
        val normalizedDetailsUrl = resolveUrl(detailsUrl)
        cleanupExpiredData()
        val cachedDetails = releaseDao.getReleaseDetails(normalizedDetailsUrl)
        val now = clock()

        if (cachedDetails != null && now - cachedDetails.fetchedAt <= FRESH_WINDOW_MS) {
            return DetailsResult.Success(
                details = cachedDetails.toModel(),
                isStale = false,
            )
        }

        return try {
            val html = httpClient.fetchDetails(normalizedDetailsUrl)
            val parsed = enrichWithTorrentLinks(parseDetails(html, normalizedDetailsUrl, now))
            releaseDao.upsertDetails(parsed.toEntity())

            DetailsResult.Success(
                details = parsed,
                isStale = false,
            )
        } catch (exception: Exception) {
            if (exception is IOException || exception is IllegalStateException) {
                if (cachedDetails != null && now - cachedDetails.fetchedAt < RETENTION_WINDOW_MS) {
                    DetailsResult.Success(
                        details = cachedDetails.toModel(),
                        isStale = true,
                    )
                } else {
                    DetailsResult.Error(
                        detailsUrl = normalizedDetailsUrl,
                        message = exception.message ?: "Unable to load details",
                    )
                }
            } else {
                throw exception
            }
        }
    }

    private suspend fun fallbackPageState(pageNumber: Int, exception: Exception): PageState {
        val now = clock()
        val cachedMetadata = releaseDao.getPageMetadata(pageNumber)

        if (cachedMetadata != null && now - cachedMetadata.fetchedAt < RETENTION_WINDOW_MS) {
            return PageState.Content(
                pageNumber = pageNumber,
                items = releaseDao.getSummariesUpToPage(pageNumber).toSummaryModels(),
                hasNextPage = cachedMetadata.itemCount > 0,
                isStale = now - cachedMetadata.fetchedAt > FRESH_WINDOW_MS,
                pagingErrorMessage = if (pageNumber > 1) {
                    exception.message ?: "Unable to load page $pageNumber"
                } else {
                    null
                },
            )
        }

        if (pageNumber > 1) {
            val previousItems = releaseDao.getSummariesUpToPage(pageNumber - 1).toSummaryModels()
            if (previousItems.isNotEmpty()) {
                return PageState.Content(
                    pageNumber = pageNumber - 1,
                    items = previousItems,
                    hasNextPage = true,
                    isStale = false,
                    pagingErrorMessage = exception.message ?: "Unable to load page $pageNumber",
                )
            }
        }

        return PageState.Error(
            pageNumber = pageNumber,
            message = exception.message ?: "Unable to load page $pageNumber",
        )
    }

    private suspend fun cleanupExpiredData() {
        releaseDao.deleteExpiredData(clock() - RETENTION_WINDOW_MS)
    }

    private suspend fun parseDetails(
        html: String,
        detailsUrl: String,
        fetchedAt: Long,
    ): ReleaseDetails {
        val summaryKind = releaseDao.getSummary(detailsUrl)?.kind
        val resolvedKind = when {
            summaryKind != null -> ReleaseKind.valueOf(summaryKind)
            detailsUrl.contains("/movies/") -> ReleaseKind.MOVIE
            else -> ReleaseKind.SERIES
        }

        return when (resolvedKind) {
            ReleaseKind.SERIES -> detailsParser.parseSeries(html, detailsUrl, fetchedAt)
            ReleaseKind.MOVIE -> detailsParser.parseMovie(html, detailsUrl, fetchedAt)
        }
    }

    private suspend fun enrichWithTorrentLinks(details: ReleaseDetails): ReleaseDetails {
        val playEpisodeId = details.playEpisodeId ?: return details
        return try {
            val redirectHtml = httpClient.fetchTorrentRedirect(playEpisodeId)
            val torrentUrl = detailsParser.parseTorrentRedirect(redirectHtml) ?: return details
            details.copy(
                torrentLinks = listOf(
                    TorrentLink(
                        label = "Вариант 1",
                        url = torrentUrl,
                    ),
                ),
            )
        } catch (_: IOException) {
            details
        }
    }

    private fun hasNextPage(
        html: String,
        pageNumber: Int,
        hasParsedItems: Boolean,
    ): Boolean {
        if (!hasParsedItems) {
            return false
        }

        val highestPageNumber = Jsoup.parse(html)
            .select("a[href*=/new/page_]")
            .mapNotNull { paginatorRegex.find(it.attr("href"))?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull()

        return highestPageNumber?.let { pageNumber < it } ?: true
    }
}
