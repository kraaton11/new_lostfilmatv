package com.kraat.lostfilmnewtv.data.poster

import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.db.ReleaseSummaryEntity
import com.kraat.lostfilmnewtv.data.model.LostFilmSearchItem
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.model.TmdbImageUrls
import com.kraat.lostfilmnewtv.data.parser.extractYear
import com.kraat.lostfilmnewtv.data.parser.toSummaryEntities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

interface TmdbEnrichmentService {
    suspend fun enrichSummaries(
        items: List<ReleaseSummary>,
        persistToCache: Boolean = false,
    ): List<ReleaseSummary>

    suspend fun enrichSearchItems(items: List<LostFilmSearchItem>): List<LostFilmSearchItem>
}

class TmdbEnrichmentServiceImpl @Inject constructor(
    private val tmdbResolver: TmdbPosterResolver,
    private val releaseDao: ReleaseDao,
) : TmdbEnrichmentService {
    private val tmdbEnrichCache = ConcurrentHashMap<String, Deferred<TmdbImageUrls?>>()

    override suspend fun enrichSummaries(
        items: List<ReleaseSummary>,
        persistToCache: Boolean,
    ): List<ReleaseSummary> {
        if (items.isEmpty()) {
            return emptyList()
        }

        val needsEnrichment = items.filterNot { it.hasCompleteArt() }
        if (needsEnrichment.isEmpty()) {
            if (persistToCache) {
                upsertChangedSummaries(items.toSummaryEntities())
            }
            return items
        }

        val semaphore = Semaphore(6) // Formerly LostFilmConcurrencyLimits.SUMMARY_ENRICHMENT_CONCURRENCY
        val enrichedItems = coroutineScope {
            items.map { item ->
                async {
                    if (item.hasCompleteArt()) {
                        return@async item
                    }
                    val tmdbUrlsDeferred = tmdbEnrichCache.getOrPut(item.detailsUrl) {
                        async {
                            semaphore.withPermit {
                                tmdbResolver.resolve(
                                    detailsUrl = item.detailsUrl,
                                    titleRu = item.titleRu,
                                    releaseDateRu = item.releaseDateRu,
                                    kind = item.kind,
                                    originalReleaseYear = item.originalReleaseYear,
                                )
                            }
                        }
                    }
                    val tmdbUrls = try {
                        tmdbUrlsDeferred.await()
                    } catch (exception: CancellationException) {
                        tmdbEnrichCache.remove(item.detailsUrl, tmdbUrlsDeferred)
                        throw exception
                    } catch (exception: Exception) {
                        tmdbEnrichCache.remove(item.detailsUrl, tmdbUrlsDeferred)
                        throw exception
                    }
                    tmdbEnrichCache.remove(item.detailsUrl, tmdbUrlsDeferred)
                    TmdbPosterEnricher.enrichSummary(item, tmdbUrls)
                }
            }.awaitAll()
        }

        if (persistToCache) {
            upsertChangedSummaries(enrichedItems.toSummaryEntities())
        }

        return enrichedItems
    }

    override suspend fun enrichSearchItems(items: List<LostFilmSearchItem>): List<LostFilmSearchItem> {
        if (items.isEmpty()) {
            return emptyList()
        }

        val semaphore = Semaphore(6) // Formerly LostFilmConcurrencyLimits.SEARCH_ENRICHMENT_CONCURRENCY
        return coroutineScope {
            items.map { item ->
                async {
                    semaphore.withPermit {
                        val tmdbUrls = tmdbResolver.resolve(
                            detailsUrl = item.targetUrl,
                            titleRu = item.titleRu,
                            releaseDateRu = item.subtitle.orEmpty(),
                            kind = item.kind,
                            originalReleaseYear = item.subtitle?.extractYear(),
                        )
                        item.copy(
                            posterUrl = tmdbUrls?.posterUrl?.ifBlank { item.posterUrl.orEmpty() }
                                ?.takeIf { it.isNotBlank() }
                                ?: item.posterUrl,
                            tmdbRating = tmdbUrls?.rating?.takeIf { it.isNotBlank() } ?: item.tmdbRating,
                        )
                    }
                }
            }.awaitAll()
        }
    }

    private fun ReleaseSummary.hasCompleteArt(): Boolean =
        posterUrl.isNotBlank() && !backdropUrl.isNullOrBlank()

    private suspend fun upsertChangedSummaries(entities: List<ReleaseSummaryEntity>) {
        if (entities.isEmpty()) return
        releaseDao.upsertSummaries(entities)
    }
}
