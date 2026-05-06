package com.kraat.lostfilmnewtv.data.poster

import android.util.Log
import com.kraat.lostfilmnewtv.data.db.TmdbPosterDao
import com.kraat.lostfilmnewtv.data.db.TmdbPosterMappingEntity
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.TmdbImageUrls
import com.kraat.lostfilmnewtv.data.model.TmdbMediaType
import com.kraat.lostfilmnewtv.data.model.TmdbSearchResult
import com.kraat.lostfilmnewtv.data.network.TmdbPosterClient
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "TmdbPosterResolver"
private const val TMDB_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
private const val YEAR_AWARE_MATCHING_CACHE_MIN_FETCHED_AT_MS = 1777852800000L // 2026-05-04
private const val SERIES_YEAR_HINT_FIX_CACHE_MIN_FETCHED_AT_MS = 1777867930731L // 2026-05-04
private const val TMDB_RATING_CACHE_MIN_FETCHED_AT_MS = 1778025600000L // 2026-05-06

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
    private val inMemoryCache = ConcurrentHashMap<String, TmdbImageUrls>()
    private val inMemoryTmdbIdCache = ConcurrentHashMap<String, Int>()
    private val episodeOverviewCache = ConcurrentHashMap<String, String>()
    private val seriesOverviewCache = ConcurrentHashMap<Int, String>()
    private val movieOverviewCache = ConcurrentHashMap<Int, String>()
    private val locks = ConcurrentHashMap<String, Mutex>()

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
            return it.copy(
                episodeOverviewRu = inMemoryTmdbIdCache[cacheKey]?.let { tmdbId ->
                    resolveEpisodeOverview(detailsUrl, tmdbId, kind)
                },
                seriesOverviewRu = inMemoryTmdbIdCache[cacheKey]?.let { tmdbId ->
                    resolveSeriesOverview(tmdbId, kind)
                },
                movieOverviewRu = inMemoryTmdbIdCache[cacheKey]?.let { tmdbId ->
                    resolveMovieOverview(tmdbId, kind)
                },
                rating = it.rating,
            )
        }

        val cached = tmdbDao.getByDetailsUrl(cacheKey)
        if (cached != null && canReuseNegativeMapping(cached) && !hasTmdbIdOverride) {
            return null
        }
        if (cached != null && canReuseCachedMapping(cached, originalReleaseYear)) {
            val urls = TmdbImageUrls(
                posterUrl = cached.posterUrl,
                backdropUrl = cached.backdropUrl,
                episodeOverviewRu = resolveEpisodeOverview(detailsUrl, cached.tmdbId, kind),
                seriesOverviewRu = resolveSeriesOverview(cached.tmdbId, kind),
                movieOverviewRu = resolveMovieOverview(cached.tmdbId, kind),
                rating = cached.rating,
            )
            inMemoryCache[cacheKey] = urls.copy(episodeOverviewRu = null)
            inMemoryTmdbIdCache[cacheKey] = cached.tmdbId
            return urls
        }

        val mutex = locks.computeIfAbsent(cacheKey) { Mutex() }
        return mutex.withLock {
            inMemoryCache[cacheKey]?.let {
                return@withLock it.copy(
                    episodeOverviewRu = inMemoryTmdbIdCache[cacheKey]?.let { tmdbId ->
                        resolveEpisodeOverview(detailsUrl, tmdbId, kind)
                    },
                    seriesOverviewRu = inMemoryTmdbIdCache[cacheKey]?.let { tmdbId ->
                        resolveSeriesOverview(tmdbId, kind)
                    },
                    movieOverviewRu = inMemoryTmdbIdCache[cacheKey]?.let { tmdbId ->
                        resolveMovieOverview(tmdbId, kind)
                    },
                    rating = it.rating,
                )
            }

            val rechecked = tmdbDao.getByDetailsUrl(cacheKey)
            if (rechecked != null && canReuseNegativeMapping(rechecked) && !hasTmdbIdOverride) {
                return@withLock null
            }
            if (rechecked != null && canReuseCachedMapping(rechecked, originalReleaseYear)) {
                val urls = TmdbImageUrls(
                    posterUrl = rechecked.posterUrl,
                    backdropUrl = rechecked.backdropUrl,
                    episodeOverviewRu = resolveEpisodeOverview(detailsUrl, rechecked.tmdbId, kind),
                    seriesOverviewRu = resolveSeriesOverview(rechecked.tmdbId, kind),
                    movieOverviewRu = resolveMovieOverview(rechecked.tmdbId, kind),
                    rating = rechecked.rating,
                )
                inMemoryCache[cacheKey] = urls.copy(episodeOverviewRu = null)
                inMemoryTmdbIdCache[cacheKey] = rechecked.tmdbId
                return@withLock urls
            }

            val result = performSearch(cacheKey, detailsUrl, titleRu, kind, originalReleaseYear)
            result?.let { inMemoryCache[cacheKey] = it.copy(episodeOverviewRu = null) }
            locks.remove(cacheKey)
            result
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
            }
            return null
        }

        val rating = bestMatch.rating ?: resolveRating(bestMatch.id, kind)
        val images = try {
            tmdbClient.getPosterAndBackdrop(bestMatch.id, tmdbType)
        } catch (e: Exception) {
            Log.e(TAG, "TMDB images failed for ${bestMatch.name}: ${e.message}")
            null
        }

        if (images == null && rating.isNullOrBlank()) {
            Log.d(TAG, "No images or rating for ${bestMatch.name}")
            return null
        }

        val episodeOverviewRu = resolveEpisodeOverview(detailsUrl, bestMatch.id, kind)
        val seriesOverviewRu = resolveSeriesOverview(bestMatch.id, kind)
        val movieOverviewRu = resolveMovieOverview(bestMatch.id, kind)
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
            episodeOverviewRu = episodeOverviewRu,
            seriesOverviewRu = seriesOverviewRu,
            movieOverviewRu = movieOverviewRu,
            rating = rating,
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
        } catch (e: Exception) {
            Log.e(TAG, "TMDB series overview failed for id=$tmdbId: ${e.message}")
            null
        }
    }

    private suspend fun resolveEpisodeOverview(
        detailsUrl: String,
        tmdbId: Int,
        kind: ReleaseKind,
    ): String? {
        if (kind != ReleaseKind.SERIES || tmdbId <= 0) {
            return null
        }
        val seasonNumber = Regex("""/season_(\d+)/""")
            .find(detailsUrl)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: return null
        val episodeNumber = Regex("""/episode_(\d+)/?""")
            .find(detailsUrl)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: return null
        val overviewKey = "$tmdbId:$seasonNumber:$episodeNumber"
        episodeOverviewCache[overviewKey]?.let { return it }

        return try {
            tmdbClient.getEpisodeOverviewRu(tmdbId, seasonNumber, episodeNumber)
                ?.also { episodeOverviewCache[overviewKey] = it }
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
            .replace(Regex("[^a-z0-9а-я]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("""\band\b"""), "")
            .replace(Regex("\\s+"), " ")
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
            ?.let { Regex("""(?:^|[ _-])((?:19|20)\d{2})(?:$|[ _-])""").find(it) }
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

    private fun String.removeYearSuffix(): String =
        replace(Regex("""[ _-]+(?:19|20)\d{2}$"""), "").trim()

    private fun extractEnglishSlug(detailsUrl: String): String? {
        val match = Regex("""/series/([^/?#]+)""").find(detailsUrl)
            ?: Regex("""/movies/([^/?#]+)""").find(detailsUrl)
        return match?.groupValues?.getOrNull(1)
            ?.replace('_', ' ')
    }

    private fun tmdbCacheKey(detailsUrl: String, kind: ReleaseKind): String {
        val seriesMatch = Regex("""^(.*/series/[^/?#]+)""").find(detailsUrl)
        if (kind == ReleaseKind.SERIES && seriesMatch != null) {
            return "${seriesMatch.groupValues[1]}/"
        }

        val movieMatch = Regex("""^(.*/movies/[^/?#]+)""").find(detailsUrl)
        if (kind == ReleaseKind.MOVIE && movieMatch != null) {
            return movieMatch.groupValues[1]
        }

        return detailsUrl
    }
}
