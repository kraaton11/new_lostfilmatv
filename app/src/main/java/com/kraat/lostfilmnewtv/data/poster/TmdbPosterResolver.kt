package com.kraat.lostfilmnewtv.data.poster

import android.util.Log
import com.kraat.lostfilmnewtv.data.db.TmdbPosterDao
import com.kraat.lostfilmnewtv.data.db.TmdbPosterMappingEntity
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.TmdbEpisodeOverview
import com.kraat.lostfilmnewtv.data.model.TmdbImageUrls
import com.kraat.lostfilmnewtv.data.model.TmdbMediaType
import com.kraat.lostfilmnewtv.data.model.TmdbSearchResult
import com.kraat.lostfilmnewtv.data.network.TmdbPosterClient
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "TmdbPosterResolver"
private const val TMDB_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
private const val YEAR_AWARE_MATCHING_CACHE_MIN_FETCHED_AT_MS = 1777852800000L // 2026-05-04
private const val SERIES_YEAR_HINT_FIX_CACHE_MIN_FETCHED_AT_MS = 1777867930731L // 2026-05-04
private const val TMDB_RATING_CACHE_MIN_FETCHED_AT_MS = 1778025600000L // 2026-05-06
private const val TMDB_BEST_IMAGE_CACHE_MIN_FETCHED_AT_MS = 1778716800000L // 2026-05-14
private const val MEMORY_CACHE_MAX_SIZE = 500
private val seasonNumberRegex = Regex("""/season_(\d+)/""")
private val episodeNumberRegex = Regex("""/episode_(\d+)/?""")
private val tmdbMatchSeparatorsRegex = Regex("[^a-z0-9а-я]+")
private val tmdbMatchWhitespaceRegex = Regex("\\s+")
private val tmdbMatchAndRegex = Regex("""\band\b""")
private val slugYearRegex = Regex("""(?:^|[ _-])((?:19|20)\d{2})(?:$|[ _-])""")
private val slugYearSuffixRegex = Regex("""[ _-]+(?:19|20)\d{2}$""")
private val seriesSlugRegex = Regex("""/series/([^/?#]+)""")
private val movieSlugRegex = Regex("""/movies/([^/?#]+)""")
private val seriesCacheKeyRegex = Regex("""^(.*/series/[^/?#]+)""")
private val movieCacheKeyRegex = Regex("""^(.*/movies/[^/?#]+)""")

interface TmdbPosterResolver {
    suspend fun resolve(
        detailsUrl: String,
        titleRu: String,
        releaseDateRu: String,
        kind: ReleaseKind,
        originalReleaseYear: Int? = null,
    ): TmdbImageUrls?
}

class TmdbPosterResolverImpl(
    private val tmdbClient: TmdbPosterClient,
    private val tmdbDao: TmdbPosterDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : TmdbPosterResolver {
    private val inMemoryCache = LruMemoryCache<String, TmdbImageUrls>(MEMORY_CACHE_MAX_SIZE)
    private val negativeMemoryCache = LruMemoryCache<String, Unit>(MEMORY_CACHE_MAX_SIZE)
    private val inMemoryTmdbIdCache = LruMemoryCache<String, Int>(MEMORY_CACHE_MAX_SIZE)
    private val episodeOverviewCache = LruMemoryCache<String, TmdbEpisodeOverview>(MEMORY_CACHE_MAX_SIZE)
    private val seriesOverviewCache = LruMemoryCache<Int, String>(MEMORY_CACHE_MAX_SIZE)
    private val movieOverviewCache = LruMemoryCache<Int, String>(MEMORY_CACHE_MAX_SIZE)
    private val locks = ConcurrentHashMap<String, LockEntry>()

    override suspend fun resolve(
        detailsUrl: String,
        titleRu: String,
        releaseDateRu: String,
        kind: ReleaseKind,
        originalReleaseYear: Int?,
    ): TmdbImageUrls? {
        val cacheKey = tmdbCacheKey(detailsUrl, kind)
        val hasTmdbIdOverride = tmdbIdOverride(extractEnglishSlug(detailsUrl), kind) != null

        inMemoryCache[cacheKey]?.let {
            val overviews = resolveOverviews(
                detailsUrl = detailsUrl,
                tmdbId = inMemoryTmdbIdCache[cacheKey],
                kind = kind,
            )
            return it.copy(
                episodeOverviewRu = overviews.episodeOverview?.text,
                episodeOverviewSource = overviews.episodeOverview?.source?.name,
                seriesOverviewRu = overviews.seriesOverviewRu,
                movieOverviewRu = overviews.movieOverviewRu,
                rating = it.rating,
            )
        }
        if (!hasTmdbIdOverride && negativeMemoryCache[cacheKey] != null) {
            return null
        }

        val cached = tmdbDao.getByDetailsUrl(cacheKey)
        if (cached != null && canReuseNegativeMapping(cached) && !hasTmdbIdOverride) {
            negativeMemoryCache[cacheKey] = Unit
            return null
        }
        if (cached != null && canReuseCachedMapping(cached, originalReleaseYear)) {
            val overviews = resolveOverviews(
                detailsUrl = detailsUrl,
                tmdbId = cached.tmdbId,
                kind = kind,
            )
            val urls = TmdbImageUrls(
                posterUrl = cached.posterUrl,
                backdropUrl = cached.backdropUrl,
                episodeOverviewRu = overviews.episodeOverview?.text,
                episodeOverviewSource = overviews.episodeOverview?.source?.name,
                seriesOverviewRu = overviews.seriesOverviewRu,
                movieOverviewRu = overviews.movieOverviewRu,
                rating = cached.rating,
            )
            inMemoryCache[cacheKey] = urls.copy(episodeOverviewRu = null, episodeOverviewSource = null)
            inMemoryTmdbIdCache[cacheKey] = cached.tmdbId
            return urls
        }

        return withKeyLock(cacheKey) {
            inMemoryCache[cacheKey]?.let {
                val overviews = resolveOverviews(
                    detailsUrl = detailsUrl,
                    tmdbId = inMemoryTmdbIdCache[cacheKey],
                    kind = kind,
                )
                return@withKeyLock it.copy(
                    episodeOverviewRu = overviews.episodeOverview?.text,
                    episodeOverviewSource = overviews.episodeOverview?.source?.name,
                    seriesOverviewRu = overviews.seriesOverviewRu,
                    movieOverviewRu = overviews.movieOverviewRu,
                    rating = it.rating,
                )
            }
            if (!hasTmdbIdOverride && negativeMemoryCache[cacheKey] != null) {
                return@withKeyLock null
            }

            val rechecked = tmdbDao.getByDetailsUrl(cacheKey)
            if (rechecked != null && canReuseNegativeMapping(rechecked) && !hasTmdbIdOverride) {
                negativeMemoryCache[cacheKey] = Unit
                return@withKeyLock null
            }
            if (rechecked != null && canReuseCachedMapping(rechecked, originalReleaseYear)) {
                val overviews = resolveOverviews(
                    detailsUrl = detailsUrl,
                    tmdbId = rechecked.tmdbId,
                    kind = kind,
                )
                val urls = TmdbImageUrls(
                    posterUrl = rechecked.posterUrl,
                    backdropUrl = rechecked.backdropUrl,
                    episodeOverviewRu = overviews.episodeOverview?.text,
                    episodeOverviewSource = overviews.episodeOverview?.source?.name,
                    seriesOverviewRu = overviews.seriesOverviewRu,
                    movieOverviewRu = overviews.movieOverviewRu,
                    rating = rechecked.rating,
                )
                inMemoryCache[cacheKey] = urls.copy(episodeOverviewRu = null, episodeOverviewSource = null)
                inMemoryTmdbIdCache[cacheKey] = rechecked.tmdbId
                return@withKeyLock urls
            }

            val result = performSearch(cacheKey, detailsUrl, titleRu, kind, originalReleaseYear)
            result?.let { inMemoryCache[cacheKey] = it.copy(episodeOverviewRu = null, episodeOverviewSource = null) }
            result
        }
    }

    private suspend fun <T> withKeyLock(key: String, block: suspend () -> T): T {
        val entry = locks.compute(key) { _, existing ->
            (existing ?: LockEntry()).also { it.refs.incrementAndGet() }
        } ?: error("Unable to create TMDB lock for $key")

        return try {
            entry.mutex.withLock {
                block()
            }
        } finally {
            if (entry.refs.decrementAndGet() == 0) {
                locks.remove(key, entry)
            }
        }
    }

    private fun canReuseNegativeMapping(
        cached: TmdbPosterMappingEntity,
    ): Boolean {
        // Cache TMDB misses briefly so unmatched titles do not search again on every screen load.
        return cached.isNegative &&
            cached.fetchedAt >= SERIES_YEAR_HINT_FIX_CACHE_MIN_FETCHED_AT_MS &&
            !cached.isExpired(clock)
    }

    private fun canReuseCachedMapping(
        cached: TmdbPosterMappingEntity,
        originalReleaseYear: Int?,
    ): Boolean {
        // Trust complete TMDB mappings for the full TTL to avoid a validation request per cached poster.
        if (cached.isExpired(clock)) {
            return false
        }

        if (cached.fetchedAt < TMDB_BEST_IMAGE_CACHE_MIN_FETCHED_AT_MS) {
            return false
        }

        if (originalReleaseYear != null && cached.fetchedAt < YEAR_AWARE_MATCHING_CACHE_MIN_FETCHED_AT_MS) {
            return false
        }

        if (cached.posterUrl.isBlank() && cached.backdropUrl.isBlank()) {
            return !cached.rating.isNullOrBlank()
        }

        if (cached.posterUrl.isBlank() || cached.backdropUrl.isBlank()) {
            return false
        }

        if (cached.rating.isNullOrBlank() && cached.fetchedAt < TMDB_RATING_CACHE_MIN_FETCHED_AT_MS) {
            return false
        }

        return true
    }

    private suspend fun performSearch(
        cacheKey: String,
        detailsUrl: String,
        titleRu: String,
        kind: ReleaseKind,
        originalReleaseYear: Int?,
    ): TmdbImageUrls? {
        val tmdbType = when (kind) {
            ReleaseKind.SERIES -> TmdbMediaType.TV
            ReleaseKind.MOVIE -> TmdbMediaType.MOVIE
        }

        val englishSlug = extractEnglishSlug(detailsUrl)
        val tmdbIdOverride = tmdbIdOverride(englishSlug, kind)
        val releaseYearHint = when (kind) {
            ReleaseKind.MOVIE -> originalReleaseYear ?: englishSlug.extractYearFromSlug()
            ReleaseKind.SERIES -> englishSlug.extractYearFromSlug()
        }
        var searchFailed = false

        val slugResults = if (tmdbIdOverride != null) {
            emptyList()
        } else if (englishSlug != null && englishSlug.isNotBlank()) {
            try {
                tmdbClient.searchByTitle(englishSlug.removeYearSuffix(), releaseYearHint, tmdbType)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                searchFailed = true
                Log.e(TAG, "TMDB slug search failed for $titleRu: ${e.message}")
                emptyList()
            }
        } else {
            emptyList()
        }

        val exactSlugMatch = if (tmdbIdOverride != null) {
            TmdbSearchResult(
                id = tmdbIdOverride,
                name = englishSlug.orEmpty(),
                popularity = Double.MAX_VALUE,
            )
        } else {
            englishSlug
                ?.removeYearSuffix()
                ?.normalizeForTmdbMatch()
                ?.takeIf { it.isNotBlank() }
                ?.let { normalizedSlug ->
                    slugResults
                        .filter { it.matchesSlug(normalizedSlug) }
                        .bestByYearThenPopularity(releaseYearHint)
                }
        }

        // Exact English slug matches are good enough to skip the Russian title query.
        val titleResults = if (exactSlugMatch == null) {
            try {
                tmdbClient.searchByTitle(titleRu, releaseYearHint, tmdbType)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                searchFailed = true
                Log.e(TAG, "TMDB title search failed for $titleRu: ${e.message}")
                emptyList()
            }
        } else {
            emptyList()
        }

        val bestMatch = exactSlugMatch ?: pickBestMatch(englishSlug, releaseYearHint, slugResults, titleResults)

        Log.d(TAG, "TMDB search: slug='$englishSlug'→${slugResults.size} results, title='$titleRu'→${titleResults.size} results, pick=${bestMatch?.name}(id=${bestMatch?.id})")

        if (bestMatch == null) {
            Log.d(TAG, "No TMDB results for $titleRu")
            if (!searchFailed) {
                tmdbDao.upsert(
                    TmdbPosterMappingEntity.negative(
                        detailsUrl = cacheKey,
                        tmdbType = tmdbType.name,
                        fetchedAt = clock(),
                    ),
                )
                negativeMemoryCache[cacheKey] = Unit
            }
            return null
        }

        val rating = bestMatch.rating ?: resolveRating(bestMatch.id, kind)
        val images = try {
            tmdbClient.getPosterAndBackdrop(bestMatch.id, tmdbType)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "TMDB images failed for ${bestMatch.name}: ${e.message}")
            null
        }

        if (images == null && rating.isNullOrBlank()) {
            Log.d(TAG, "No images or rating for ${bestMatch.name}")
            return null
        }

        val overviews = resolveOverviews(
            detailsUrl = detailsUrl,
            tmdbId = bestMatch.id,
            kind = kind,
        )
        val resolvedImages = images ?: TmdbImageUrls(
            posterUrl = "",
            backdropUrl = "",
            rating = rating,
        )

        Log.d(TAG, "TMDB match for ${bestMatch.name}: poster=${resolvedImages.posterUrl.take(60)}..., rating=$rating")

        val entity = TmdbPosterMappingEntity.create(
            detailsUrl = cacheKey,
            tmdbId = bestMatch.id,
            tmdbType = tmdbType.name,
            posterUrl = resolvedImages.posterUrl,
            backdropUrl = resolvedImages.backdropUrl,
            fetchedAt = clock(),
            rating = rating,
        )
        tmdbDao.upsert(entity)
        inMemoryTmdbIdCache[cacheKey] = bestMatch.id

        return resolvedImages.copy(
            episodeOverviewRu = overviews.episodeOverview?.text,
            episodeOverviewSource = overviews.episodeOverview?.source?.name,
            seriesOverviewRu = overviews.seriesOverviewRu,
            movieOverviewRu = overviews.movieOverviewRu,
            rating = rating,
        )
    }

    private suspend fun resolveOverviews(
        detailsUrl: String,
        tmdbId: Int?,
        kind: ReleaseKind,
    ): ResolvedOverviews {
        if (tmdbId == null) {
            return ResolvedOverviews()
        }

        val overviews = coroutineScope {
            listOf(
                async { resolveEpisodeOverview(detailsUrl, tmdbId, kind) },
                async { resolveSeriesOverview(tmdbId, kind) },
                async { resolveMovieOverview(tmdbId, kind) },
            ).awaitAll()
        }
        return ResolvedOverviews(
            episodeOverview = overviews[0] as? TmdbEpisodeOverview,
            seriesOverviewRu = overviews[1] as? String,
            movieOverviewRu = overviews[2] as? String,
        )
    }

    private suspend fun resolveMovieOverview(
        tmdbId: Int,
        kind: ReleaseKind,
    ): String? {
        if (kind != ReleaseKind.MOVIE || tmdbId <= 0) {
            return null
        }
        movieOverviewCache[tmdbId]?.let { return it }

        return try {
            tmdbClient.getMovieOverviewRu(tmdbId)
                ?.also { movieOverviewCache[tmdbId] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "TMDB movie overview failed for id=$tmdbId: ${e.message}")
            null
        }
    }

    private suspend fun resolveRating(
        tmdbId: Int,
        kind: ReleaseKind,
    ): String? {
        if (tmdbId <= 0) {
            return null
        }
        val tmdbType = when (kind) {
            ReleaseKind.SERIES -> TmdbMediaType.TV
            ReleaseKind.MOVIE -> TmdbMediaType.MOVIE
        }

        return try {
            tmdbClient.getRating(tmdbId, tmdbType)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "TMDB rating failed for id=$tmdbId: ${e.message}")
            null
        }
    }

    private suspend fun resolveSeriesOverview(
        tmdbId: Int,
        kind: ReleaseKind,
    ): String? {
        if (kind != ReleaseKind.SERIES || tmdbId <= 0) {
            return null
        }
        seriesOverviewCache[tmdbId]?.let { return it }

        return try {
            tmdbClient.getSeriesOverviewRu(tmdbId)
                ?.also { seriesOverviewCache[tmdbId] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "TMDB series overview failed for id=$tmdbId: ${e.message}")
            null
        }
    }

    private suspend fun resolveEpisodeOverview(
        detailsUrl: String,
        tmdbId: Int,
        kind: ReleaseKind,
    ): TmdbEpisodeOverview? {
        if (kind != ReleaseKind.SERIES || tmdbId <= 0) {
            return null
        }
        val seasonNumber = seasonNumberRegex
            .find(detailsUrl)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: return null
        val episodeNumber = episodeNumberRegex
            .find(detailsUrl)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: return null
        val overviewKey = "$tmdbId:$seasonNumber:$episodeNumber"
        episodeOverviewCache[overviewKey]?.let { return it }

        return try {
            tmdbClient.getEpisodeOverview(tmdbId, seasonNumber, episodeNumber)
                ?.also { episodeOverviewCache[overviewKey] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "TMDB episode overview failed for $detailsUrl: ${e.message}")
            null
        }
    }

    private fun pickBestMatch(
        englishSlug: String?,
        releaseYearHint: Int?,
        slugResults: List<TmdbSearchResult>,
        titleResults: List<TmdbSearchResult>,
    ): TmdbSearchResult? {
        val normalizedSlug = englishSlug
            ?.removeYearSuffix()
            ?.normalizeForTmdbMatch()
            ?.takeIf { it.isNotBlank() }
        if (normalizedSlug != null) {
            slugResults
                .filter { it.matchesSlug(normalizedSlug) }
                .bestByYearThenPopularity(releaseYearHint)
                ?.let { return it }
        }

        if (slugResults.isEmpty()) return titleResults.bestByYearThenPopularity(releaseYearHint)
        if (titleResults.isEmpty()) return slugResults.bestByYearThenPopularity(releaseYearHint)

        val slugIdSet = slugResults.map { it.id }.toSet()
        val titleIdSet = titleResults.map { it.id }.toSet()
        val common = slugIdSet.intersect(titleIdSet)
        if (common.isNotEmpty()) {
            return slugResults
                .filter { it.id in common }
                .bestByYearThenPopularity(releaseYearHint)
        }

        return slugResults.bestByYearThenPopularity(releaseYearHint)
    }

    private fun TmdbSearchResult.matchesSlug(normalizedSlug: String): Boolean {
        return name.normalizeForTmdbMatch() == normalizedSlug ||
            originalName.normalizeForTmdbMatch() == normalizedSlug
    }

    private fun List<TmdbSearchResult>.bestByYearThenPopularity(releaseYearHint: Int?): TmdbSearchResult? {
        if (isEmpty()) return null

        return maxWithOrNull(
            compareBy<TmdbSearchResult> {
                if (releaseYearHint != null && it.releaseYear == releaseYearHint) 1 else 0
            }.thenBy { it.popularity },
        )
    }

    private fun String.normalizeForTmdbMatch(): String {
        return lowercase()
            .replace(tmdbMatchSeparatorsRegex, " ")
            .trim()
            .replace(tmdbMatchWhitespaceRegex, " ")
            .replace(tmdbMatchAndRegex, "")
            .replace(tmdbMatchWhitespaceRegex, " ")
            .trim()
    }

    private fun tmdbIdOverride(englishSlug: String?, kind: ReleaseKind): Int? {
        if (kind != ReleaseKind.SERIES) {
            return null
        }
        return when (englishSlug?.removeYearSuffix()?.normalizeForTmdbMatch()) {
            "his hers" -> 259731
            else -> null
        }
    }

    private fun String?.extractYearFromSlug(): Int? =
        this
            ?.let { slugYearRegex.find(it) }
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

    private fun String.removeYearSuffix(): String =
        replace(slugYearSuffixRegex, "").trim()

    private fun extractEnglishSlug(detailsUrl: String): String? {
        val match = seriesSlugRegex.find(detailsUrl)
            ?: movieSlugRegex.find(detailsUrl)
        return match?.groupValues?.getOrNull(1)
            ?.replace('_', ' ')
    }

    private fun tmdbCacheKey(detailsUrl: String, kind: ReleaseKind): String {
        val seriesMatch = seriesCacheKeyRegex.find(detailsUrl)
        if (kind == ReleaseKind.SERIES && seriesMatch != null) {
            return "${seriesMatch.groupValues[1]}/"
        }

        val movieMatch = movieCacheKeyRegex.find(detailsUrl)
        if (kind == ReleaseKind.MOVIE && movieMatch != null) {
            return movieMatch.groupValues[1]
        }

        return detailsUrl
    }
}

private class LockEntry {
    val mutex = Mutex()
    val refs = AtomicInteger(0)
}

private data class ResolvedOverviews(
    val episodeOverview: TmdbEpisodeOverview? = null,
    val seriesOverviewRu: String? = null,
    val movieOverviewRu: String? = null,
)

private class LruMemoryCache<K, V>(
    private val maxSize: Int,
) {
    private val lock = Any()
    private val values = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean {
            return size > maxSize
        }
    }

    operator fun get(key: K): V? = synchronized(lock) {
        values[key]
    }

    operator fun set(key: K, value: V) {
        synchronized(lock) {
            values[key] = value
        }
    }
}
