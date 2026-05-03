package com.kraat.lostfilmnewtv.data.poster

import android.util.Log
import com.kraat.lostfilmnewtv.data.db.TmdbPosterDao
import com.kraat.lostfilmnewtv.data.db.TmdbPosterMappingEntity
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.TmdbImageUrls
import com.kraat.lostfilmnewtv.data.model.TmdbMediaType
import com.kraat.lostfilmnewtv.data.network.TmdbPosterClient
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "TmdbPosterResolver"
private const val TMDB_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000

interface TmdbPosterResolver {
    suspend fun resolve(
        detailsUrl: String,
        titleRu: String,
        releaseDateRu: String,
        kind: ReleaseKind,
    ): TmdbImageUrls?
}

class TmdbPosterResolverImpl(
    private val tmdbClient: TmdbPosterClient,
    private val tmdbDao: TmdbPosterDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : TmdbPosterResolver {
    private val inMemoryCache = ConcurrentHashMap<String, TmdbImageUrls>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    override suspend fun resolve(
        detailsUrl: String,
        titleRu: String,
        releaseDateRu: String,
        kind: ReleaseKind,
    ): TmdbImageUrls? {
        inMemoryCache[detailsUrl]?.let {
            return it
        }

        val cached = tmdbDao.getByDetailsUrl(detailsUrl)
        if (cached != null && canReuseNegativeMapping(cached)) {
            return null
        }
        if (cached != null && canReuseCachedMapping(cached)) {
            val urls = TmdbImageUrls(
                posterUrl = cached.posterUrl,
                backdropUrl = cached.backdropUrl,
            )
            inMemoryCache[detailsUrl] = urls
            return urls
        }

        val mutex = locks.computeIfAbsent(detailsUrl) { Mutex() }
        return mutex.withLock {
            inMemoryCache[detailsUrl]?.let { return@withLock it }

            val rechecked = tmdbDao.getByDetailsUrl(detailsUrl)
            if (rechecked != null && canReuseNegativeMapping(rechecked)) {
                return@withLock null
            }
            if (rechecked != null && canReuseCachedMapping(rechecked)) {
                val urls = TmdbImageUrls(
                    posterUrl = rechecked.posterUrl,
                    backdropUrl = rechecked.backdropUrl,
                )
                inMemoryCache[detailsUrl] = urls
                return@withLock urls
            }

            val result = performSearch(detailsUrl, titleRu, kind)
            result?.let { inMemoryCache[detailsUrl] = it }
            locks.remove(detailsUrl)
            result
        }
    }

    private fun canReuseNegativeMapping(
        cached: TmdbPosterMappingEntity,
    ): Boolean {
        // Cache TMDB misses briefly so unmatched titles do not search again on every screen load.
        return cached.isNegative && !cached.isExpired(clock)
    }

    private fun canReuseCachedMapping(
        cached: TmdbPosterMappingEntity,
    ): Boolean {
        // Trust complete TMDB mappings for the full TTL to avoid a validation request per cached poster.
        if (cached.isExpired(clock)) {
            return false
        }

        if (cached.posterUrl.isBlank() || cached.backdropUrl.isBlank()) {
            return false
        }

        return true
    }

    private suspend fun performSearch(
        detailsUrl: String,
        titleRu: String,
        kind: ReleaseKind,
    ): TmdbImageUrls? {
        val tmdbType = when (kind) {
            ReleaseKind.SERIES -> TmdbMediaType.TV
            ReleaseKind.MOVIE -> TmdbMediaType.MOVIE
        }

        val englishSlug = extractEnglishSlug(detailsUrl)
        var searchFailed = false

        val slugResults = if (englishSlug != null && englishSlug.isNotBlank()) {
            try {
                tmdbClient.searchByTitle(englishSlug, null, tmdbType)
            } catch (e: Exception) {
                searchFailed = true
                Log.e(TAG, "TMDB slug search failed for $titleRu: ${e.message}")
                emptyList()
            }
        } else {
            emptyList()
        }

        val exactSlugMatch = englishSlug
            ?.normalizeForTmdbMatch()
            ?.takeIf { it.isNotBlank() }
            ?.let { normalizedSlug ->
                slugResults
                    .filter { it.matchesSlug(normalizedSlug) }
                    .maxByOrNull { it.popularity }
            }

        // Exact English slug matches are good enough to skip the Russian title query.
        val titleResults = if (exactSlugMatch == null) {
            try {
                tmdbClient.searchByTitle(titleRu, null, tmdbType)
            } catch (e: Exception) {
                searchFailed = true
                Log.e(TAG, "TMDB title search failed for $titleRu: ${e.message}")
                emptyList()
            }
        } else {
            emptyList()
        }

        val bestMatch = exactSlugMatch ?: pickBestMatch(englishSlug, slugResults, titleResults)

        Log.d(TAG, "TMDB search: slug='$englishSlug'→${slugResults.size} results, title='$titleRu'→${titleResults.size} results, pick=${bestMatch?.name}(id=${bestMatch?.id})")

        if (bestMatch == null) {
            Log.d(TAG, "No TMDB results for $titleRu")
            if (!searchFailed) {
                tmdbDao.upsert(
                    TmdbPosterMappingEntity.negative(
                        detailsUrl = detailsUrl,
                        tmdbType = tmdbType.name,
                        fetchedAt = clock(),
                    ),
                )
            }
            return null
        }

        val images = try {
            tmdbClient.getPosterAndBackdrop(bestMatch.id, tmdbType)
        } catch (e: Exception) {
            Log.e(TAG, "TMDB images failed for ${bestMatch.name}: ${e.message}")
            null
        }

        if (images == null) {
            Log.d(TAG, "No images for ${bestMatch.name}")
            return null
        }

        Log.d(TAG, "TMDB images for ${bestMatch.name}: poster=${images.posterUrl.take(60)}...")

        val entity = TmdbPosterMappingEntity.create(
            detailsUrl = detailsUrl,
            tmdbId = bestMatch.id,
            tmdbType = tmdbType.name,
            posterUrl = images.posterUrl,
            backdropUrl = images.backdropUrl,
            fetchedAt = clock(),
        )
        tmdbDao.upsert(entity)

        return images
    }

    private fun pickBestMatch(
        englishSlug: String?,
        slugResults: List<com.kraat.lostfilmnewtv.data.model.TmdbSearchResult>,
        titleResults: List<com.kraat.lostfilmnewtv.data.model.TmdbSearchResult>,
    ): com.kraat.lostfilmnewtv.data.model.TmdbSearchResult? {
        val normalizedSlug = englishSlug
            ?.normalizeForTmdbMatch()
            ?.takeIf { it.isNotBlank() }
        if (normalizedSlug != null) {
            slugResults
                .filter { it.matchesSlug(normalizedSlug) }
                .maxByOrNull { it.popularity }
                ?.let { return it }
        }

        if (slugResults.isEmpty()) return titleResults.firstOrNull()
        if (titleResults.isEmpty()) return slugResults.firstOrNull()

        val slugIdSet = slugResults.map { it.id }.toSet()
        val titleIdSet = titleResults.map { it.id }.toSet()
        val common = slugIdSet.intersect(titleIdSet)
        if (common.isNotEmpty()) {
            return slugResults.first { it.id in common }
        }

        return slugResults.first()
    }

    private fun com.kraat.lostfilmnewtv.data.model.TmdbSearchResult.matchesSlug(normalizedSlug: String): Boolean {
        return name.normalizeForTmdbMatch() == normalizedSlug ||
            originalName.normalizeForTmdbMatch() == normalizedSlug
    }

    private fun String.normalizeForTmdbMatch(): String {
        return lowercase()
            .replace(Regex("[^a-z0-9а-я]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun extractEnglishSlug(detailsUrl: String): String? {
        val match = Regex("""/series/([^/]+)/""").find(detailsUrl)
            ?: Regex("""/movies/([^/]+)/""").find(detailsUrl)
        return match?.groupValues?.getOrNull(1)
            ?.replace('_', ' ')
            ?.replace('-', ' ')
    }
}
