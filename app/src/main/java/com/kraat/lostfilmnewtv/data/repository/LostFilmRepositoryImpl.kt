package com.kraat.lostfilmnewtv.data.repository

import com.kraat.lostfilmnewtv.data.db.PageCacheMetadataEntity
import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.model.FavoriteMetadata
import com.kraat.lostfilmnewtv.data.model.FavoriteMutationResult
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.FavoriteToggleNetworkResult
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.network.LostFilmHttpClient
import com.kraat.lostfilmnewtv.data.parser.BASE_URL
import com.kraat.lostfilmnewtv.data.parser.LostFilmDetailsParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmFavoriteReleasesParser
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
private val favoriteRouteCandidates = listOf("/my/", "/my/type_0", "/my/type_1", "/my/serials")
private val seriesFavoritePageRegex = Regex("""${Regex.escape(BASE_URL)}/series/([^/]+)/season_\d+/episode_\d+/?""")

class LostFilmRepositoryImpl(
    private val httpClient: LostFilmHttpClient,
    private val releaseDao: ReleaseDao,
    private val listParser: LostFilmListParser,
    private val detailsParser: LostFilmDetailsParser,
    private val favoriteReleasesParser: LostFilmFavoriteReleasesParser,
    private val hasAuthenticatedSession: suspend () -> Boolean,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : LostFilmRepository {
    override suspend fun loadPage(pageNumber: Int): PageState {
        cleanupExpiredData()

        return try {
            val fetchedAt = clock()
            val hasAuthenticatedSession = hasAuthenticatedSession()
            val html = httpClient.fetchNewPage(pageNumber)
            val parsedItems = listParser.parse(
                html = html,
                pageNumber = pageNumber,
                fetchedAt = fetchedAt,
            )
            val itemsToPersist = if (hasAuthenticatedSession) {
                parsedItems
            } else {
                preserveWatchedState(
                    pageNumber = pageNumber,
                    parsedItems = parsedItems,
                )
            }

            releaseDao.replacePage(
                pageNumber = pageNumber,
                summaries = itemsToPersist.toSummaryEntities(),
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
            val cachedModel = enrichWithSummaryEpisodeTitle(
                refreshFavoriteMetadataIfNeeded(
                    refreshCachedTorrentLinksIfNeeded(cachedDetails.toModel()),
                ),
            )
            return DetailsResult.Success(
                details = cachedModel,
                isStale = false,
            )
        }

        return try {
            val html = httpClient.fetchDetails(normalizedDetailsUrl)
            val parsed = enrichWithSummaryEpisodeTitle(
                refreshFavoriteMetadataIfNeeded(
                    enrichWithTorrentLinks(parseDetails(html, normalizedDetailsUrl, now)),
                ),
            )
            releaseDao.upsertDetails(parsed.toEntity())

            DetailsResult.Success(
                details = parsed,
                isStale = false,
            )
        } catch (exception: Exception) {
            if (exception is IOException || exception is IllegalStateException) {
                if (cachedDetails != null && now - cachedDetails.fetchedAt < RETENTION_WINDOW_MS) {
                    DetailsResult.Success(
                        details = enrichWithSummaryEpisodeTitle(cachedDetails.toModel()),
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

    override suspend fun markEpisodeWatched(detailsUrl: String, playEpisodeId: String): Boolean {
        if (!hasAuthenticatedSession()) {
            return false
        }

        val normalizedDetailsUrl = resolveUrl(detailsUrl)
        return try {
            val detailsHtml = httpClient.fetchDetails(normalizedDetailsUrl)
            val ajaxSessionToken = detailsParser.parseAjaxSessionToken(detailsHtml)
            if (ajaxSessionToken == null) {
                return false
            }
            val marked = httpClient.markEpisodeWatched(
                detailsUrl = normalizedDetailsUrl,
                playEpisodeId = playEpisodeId,
                ajaxSessionToken = ajaxSessionToken,
            )
            val effectiveMarked = if (marked) {
                true
            } else {
                val refreshedDetailsHtml = httpClient.fetchDetails(normalizedDetailsUrl)
                detailsParser.parseWatchedState(refreshedDetailsHtml) == true
            }
            if (effectiveMarked) {
                releaseDao.updateSummaryWatched(
                    detailsUrl = normalizedDetailsUrl,
                    isWatched = true,
                )
            }
            effectiveMarked
        } catch (_: IOException) {
            false
        }
    }

    override suspend fun setFavorite(
        detailsUrl: String,
        targetFavorite: Boolean,
    ): FavoriteMutationResult {
        if (!hasAuthenticatedSession()) {
            return FavoriteMutationResult.RequiresLogin()
        }

        val normalizedDetailsUrl = resolveUrl(detailsUrl)
        val favoritePageUrl = favoriteMetadataPageUrl(normalizedDetailsUrl)
        return try {
            val favoritePageHtml = httpClient.fetchDetails(favoritePageUrl)
            val favoriteMetadata = detailsParser.parseFavoriteMetadata(favoritePageHtml)
                ?: return FavoriteMutationResult.Error("Не удалось обновить избранное")
            persistFavoriteMetadata(normalizedDetailsUrl, favoriteMetadata)

            if (favoriteMetadata.isFavorite == targetFavorite) {
                return FavoriteMutationResult.NoOp
            }

            val ajaxSessionToken = detailsParser.parseAjaxSessionToken(favoritePageHtml)
                ?: return FavoriteMutationResult.Error("Войдите в LostFilm")
            val toggleResult = httpClient.toggleFavorite(
                refererUrl = favoritePageUrl,
                favoriteTargetId = favoriteMetadata.targetId,
                ajaxSessionToken = ajaxSessionToken,
            )
            val refreshedHtml = httpClient.fetchDetails(favoritePageUrl)
            val refreshedFavoriteMetadata = detailsParser.parseFavoriteMetadata(refreshedHtml)
            val refreshedFavoriteState = refreshedFavoriteMetadata?.isFavorite
            persistFavoriteMetadata(normalizedDetailsUrl, refreshedFavoriteMetadata ?: favoriteMetadata)

            val effectiveFavoriteState = when (toggleResult) {
                FavoriteToggleNetworkResult.ToggledOn -> refreshedFavoriteState ?: true
                FavoriteToggleNetworkResult.ToggledOff -> refreshedFavoriteState ?: false
                FavoriteToggleNetworkResult.Unknown -> refreshedFavoriteState
            }

            if (effectiveFavoriteState == targetFavorite) {
                FavoriteMutationResult.Updated
            } else {
                FavoriteMutationResult.Error("Не удалось обновить избранное")
            }
        } catch (_: IOException) {
            FavoriteMutationResult.Error("Не удалось обновить избранное")
        }
    }

    override suspend fun loadFavoriteReleases(): FavoriteReleasesResult {
        if (!hasAuthenticatedSession()) {
            return FavoriteReleasesResult.Unavailable("Войдите в LostFilm")
        }

        return try {
            for (path in favoriteRouteCandidates) {
                val html = try {
                    httpClient.fetchAccountPage(path)
                } catch (_: IOException) {
                    continue
                }
                val items = favoriteReleasesParser.parse(
                    html = html,
                    fetchedAt = clock(),
                )
                if (items.isNotEmpty()) {
                    return FavoriteReleasesResult.Success(items)
                }
            }

            FavoriteReleasesResult.Unavailable()
        } catch (_: IOException) {
            FavoriteReleasesResult.Unavailable()
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

    private suspend fun preserveWatchedState(
        pageNumber: Int,
        parsedItems: List<com.kraat.lostfilmnewtv.data.model.ReleaseSummary>,
    ): List<com.kraat.lostfilmnewtv.data.model.ReleaseSummary> {
        val existingWatchedByUrl = releaseDao.getPageSummaries(pageNumber)
            .associate { entity -> entity.detailsUrl to entity.isWatched }
        return parsedItems.map { item ->
            if (existingWatchedByUrl[item.detailsUrl] == true) {
                item.copy(isWatched = true)
            } else {
                item
            }
        }
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
        val cachedExpanded = expandGenericTorrentLinks(details)
        if (cachedExpanded != null) {
            return cachedExpanded
        }

        val playEpisodeId = details.playEpisodeId ?: return details
        return try {
            val redirectHtml = httpClient.fetchTorrentRedirect(playEpisodeId)
            val torrentLinks = detailsParser.parseTorrentLinks(redirectHtml)
            val expandedLinks = if (torrentLinks.size == 1 && torrentLinks.first().label == "Вариант 1") {
                try {
                    val torrentPageHtml = httpClient.fetchTorrentPage(torrentLinks.first().url)
                    detailsParser.parseTorrentLinks(torrentPageHtml).ifEmpty { torrentLinks }
                } catch (_: IOException) {
                    torrentLinks
                }
            } else {
                torrentLinks
            }

            if (expandedLinks.isEmpty()) {
                return details
            }
            details.copy(
                torrentLinks = expandedLinks,
            )
        } catch (_: IOException) {
            details
        }
    }

    private suspend fun refreshCachedTorrentLinksIfNeeded(details: ReleaseDetails): ReleaseDetails {
        if (details.playEpisodeId == null) {
            return details
        }

        if (details.torrentLinks.isNotEmpty() && details.torrentLinks.none { it.label == "Вариант 1" }) {
            return details
        }

        if (details.torrentLinks.isEmpty() && !hasAuthenticatedSession()) {
            return details
        }

        val enriched = enrichWithTorrentLinks(details)
        if (enriched.torrentLinks.isNotEmpty()) {
            releaseDao.upsertDetails(enriched.toEntity())
        }
        return enriched
    }

    private suspend fun enrichWithSummaryEpisodeTitle(details: ReleaseDetails): ReleaseDetails {
        if (details.kind != ReleaseKind.SERIES || !details.episodeTitleRu.isNullOrBlank()) {
            return details
        }

        val episodeTitleRu = releaseDao.getSummary(details.detailsUrl)
            ?.episodeTitleRu
            ?.takeIf { it.isNotBlank() }
            ?: return details

        return details.copy(episodeTitleRu = episodeTitleRu)
    }

    private suspend fun refreshFavoriteMetadataIfNeeded(details: ReleaseDetails): ReleaseDetails {
        if (!hasAuthenticatedSession()) {
            return details
        }
        if (details.favoriteTargetId != null && details.isFavorite != null) {
            return details
        }

        return try {
            val favoritePageHtml = httpClient.fetchDetails(favoriteMetadataPageUrl(details.detailsUrl))
            val favoriteMetadata = detailsParser.parseFavoriteMetadata(favoritePageHtml)
                ?: return details
            val enriched = details.withFavoriteMetadata(favoriteMetadata)
            releaseDao.upsertDetails(enriched.toEntity())
            enriched
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

    private suspend fun expandGenericTorrentLinks(details: ReleaseDetails): ReleaseDetails? {
        val singleGenericLink = details.torrentLinks.singleOrNull()
            ?.takeIf { it.label == "Вариант 1" && it.url.contains("/V/?") }
            ?: return null

        return try {
            val torrentPageHtml = httpClient.fetchTorrentPage(singleGenericLink.url)
            val parsedLinks = detailsParser.parseTorrentLinks(torrentPageHtml)
            if (parsedLinks.isEmpty()) {
                details
            } else {
                details.copy(torrentLinks = parsedLinks)
            }
        } catch (_: IOException) {
            details
        }
    }

    private suspend fun persistFavoriteMetadata(
        detailsUrl: String,
        favoriteMetadata: FavoriteMetadata,
    ) {
        val cachedDetails = releaseDao.getReleaseDetails(detailsUrl)?.toModel() ?: return
        releaseDao.upsertDetails(
            cachedDetails.withFavoriteMetadata(favoriteMetadata).toEntity(),
        )
    }

    private fun favoriteMetadataPageUrl(detailsUrl: String): String {
        val normalizedDetailsUrl = resolveUrl(detailsUrl)
        val seriesMatch = seriesFavoritePageRegex.matchEntire(normalizedDetailsUrl)
        return if (seriesMatch != null) {
            "$BASE_URL/series/${seriesMatch.groupValues[1]}"
        } else {
            normalizedDetailsUrl
        }
    }

    private fun ReleaseDetails.withFavoriteMetadata(
        favoriteMetadata: FavoriteMetadata,
    ): ReleaseDetails {
        return copy(
            favoriteTargetId = favoriteMetadata.targetId,
            favoriteTargetKind = favoriteMetadata.targetKind,
            isFavorite = favoriteMetadata.isFavorite,
        )
    }
}
