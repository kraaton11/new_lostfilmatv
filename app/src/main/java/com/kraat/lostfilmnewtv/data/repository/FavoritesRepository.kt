package com.kraat.lostfilmnewtv.data.repository

import android.util.Log
import com.kraat.lostfilmnewtv.data.db.FavoriteReleaseCacheEntity
import com.kraat.lostfilmnewtv.data.db.FavoriteReleaseCacheMetadataEntity
import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.model.FavoriteMetadata
import com.kraat.lostfilmnewtv.data.model.FavoriteMutationResult
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.FavoriteSeriesResult
import com.kraat.lostfilmnewtv.data.model.FavoriteToggleNetworkResult
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.network.LostFilmHttpClient
import com.kraat.lostfilmnewtv.data.parser.BASE_URL
import com.kraat.lostfilmnewtv.data.parser.FavoriteSeriesRef
import com.kraat.lostfilmnewtv.data.parser.LostFilmDetailsParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmFavoriteSeriesParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmSeasonEpisodesParser
import com.kraat.lostfilmnewtv.data.parser.resolveUrl
import com.kraat.lostfilmnewtv.data.parser.toEntity
import com.kraat.lostfilmnewtv.data.poster.TmdbEnrichmentService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val TAG = "FavoritesRepository"
private const val FAVORITE_RELEASES_MAX_EPISODES_PER_SEASON = Int.MAX_VALUE
private const val FAVORITE_RELEASES_PAGE_SIZE = 30
private const val FAVORITE_RELEASES_MAX_SEASONS_PER_SERIES = 1
private const val FAVORITE_RELEASES_CACHE_TTL_MS = 2 * 60 * 1000L
private const val FAVORITE_RELEASES_ROOM_FRESH_MS = 15 * 60 * 1000L
private const val FAVORITE_SERIES_LOAD_CONCURRENCY = 6
private const val FAVORITE_PUBLISH_CHECK_CONCURRENCY = 6

interface FavoritesRepository {
    fun observeFavoriteReleases(pageNumber: Int = 1): Flow<FavoriteReleasesResult>
    suspend fun loadFavoriteSeries(): FavoriteSeriesResult
    suspend fun setFavorite(detailsUrl: String, targetFavorite: Boolean): FavoriteMutationResult
    suspend fun invalidateCache()
}

class FavoritesRepositoryImpl @Inject constructor(
    private val httpClient: LostFilmHttpClient,
    private val releaseDao: ReleaseDao,
    private val tmdbEnrichmentService: TmdbEnrichmentService,
    private val hasAuthenticatedSession: suspend () -> Boolean,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : FavoritesRepository {

    private val favoriteSeriesParser = LostFilmFavoriteSeriesParser()
    private val seasonEpisodesParser = LostFilmSeasonEpisodesParser()
    private val detailsParser = LostFilmDetailsParser()

    private val favoriteReleasesCacheMutex = Mutex()
    private var favoriteReleasesCache: FavoriteReleasesCache? = null

    private val favoriteSeriesRoute = "/my/type_1"
    private val seriesFavoritePageRegex = Regex("""${Regex.escape(BASE_URL)}/series/([^/]+)/season_\d+/episode_\d+/?""")
    private val seriesRootUrlRegex = Regex("""${Regex.escape(BASE_URL)}/series/([^/]+)(?:/.*)?/?""")
    private val favoriteReleaseDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    // Watched marks cache in memory to avoid repetitive AJAX network hits
    private val watchedMarksCache = ConcurrentHashMap<String, WatchedMarksCacheEntry>()

    private data class WatchedMarksCacheEntry(
        val watchedIds: Set<String>,
        val fetchedAt: Long,
    )

    private data class FavoriteReleasesCache(
        val allItems: List<ReleaseSummary>,
        val favoriteSeriesCount: Int,
        val fetchedAt: Long,
    )

    private data class FavoriteMetadataPage(
        val url: String,
        val html: String,
        val metadata: FavoriteMetadata,
    )

    private data class AllFavoriteReleasesFetch(
        val allItems: List<ReleaseSummary>,
        val favoriteSeriesCount: Int,
    )

    override fun observeFavoriteReleases(pageNumber: Int): Flow<FavoriteReleasesResult> = channelFlow {
        if (!hasAuthenticatedSession()) {
            send(FavoriteReleasesResult.Unavailable("Войдите в LostFilm"))
            return@channelFlow
        }

        val normalizedPageNumber = pageNumber.coerceAtLeast(1)

        try {
            val cached = favoriteReleasesCacheMutex.withLock { favoriteReleasesCache }
            val canReuseInMemoryCache = normalizedPageNumber > 1 &&
                cached != null &&
                (clock() - cached.fetchedAt) < FAVORITE_RELEASES_CACHE_TTL_MS

            val (allItems, favoriteSeriesCount) = if (canReuseInMemoryCache) {
                cached!!.allItems to cached.favoriteSeriesCount
            } else {
                val roomMetadata = releaseDao.getFavoriteReleaseCacheMetadata()
                val roomCacheFresh = roomMetadata != null &&
                    (clock() - roomMetadata.fetchedAt) < FAVORITE_RELEASES_ROOM_FRESH_MS

                if (roomMetadata != null) {
                    val roomItems = releaseDao.getFavoriteReleasesCache().map { it.toModel() }
                    val pageOffset = (normalizedPageNumber - 1) * FAVORITE_RELEASES_PAGE_SIZE
                    val pageItems = roomItems
                        .drop(pageOffset)
                        .take(FAVORITE_RELEASES_PAGE_SIZE)
                        .mapIndexed { index, item -> item.copy(positionInPage = pageOffset + index) }
                    val enrichedPageItems = tmdbEnrichmentService.enrichSummaries(pageItems)
                        .mapIndexed { index, item -> item.copy(positionInPage = pageOffset + index) }

                    favoriteReleasesCacheMutex.withLock {
                        favoriteReleasesCache = FavoriteReleasesCache(
                            allItems = roomItems,
                            favoriteSeriesCount = roomMetadata.favoriteSeriesCount,
                            fetchedAt = roomMetadata.fetchedAt,
                        )
                    }

                    if (roomCacheFresh) {
                        send(FavoriteReleasesResult.Success(
                            items = enrichedPageItems,
                            pageNumber = normalizedPageNumber,
                            hasNextPage = roomItems.size > pageOffset + FAVORITE_RELEASES_PAGE_SIZE,
                            favoriteSeriesCount = roomMetadata.favoriteSeriesCount,
                        ))
                        return@channelFlow
                    }

                    send(FavoriteReleasesResult.Partial(
                        items = enrichedPageItems,
                        favoriteSeriesCount = roomMetadata.favoriteSeriesCount,
                    ))
                }

                val fetched = fetchAllFavoriteReleasesStreaming { partialItems, total ->
                    send(FavoriteReleasesResult.Partial(items = partialItems, favoriteSeriesCount = total))
                } ?: run {
                    send(FavoriteReleasesResult.Unavailable())
                    return@channelFlow
                }
                val now = clock()
                favoriteReleasesCacheMutex.withLock {
                    favoriteReleasesCache = FavoriteReleasesCache(
                        allItems = fetched.allItems,
                        favoriteSeriesCount = fetched.favoriteSeriesCount,
                        fetchedAt = now,
                    )
                }
                fetched.allItems to fetched.favoriteSeriesCount
            }

            val pageOffset = (normalizedPageNumber - 1) * FAVORITE_RELEASES_PAGE_SIZE
            val items = allItems
                .drop(pageOffset)
                .take(FAVORITE_RELEASES_PAGE_SIZE)
                .mapIndexed { index, item -> item.copy(positionInPage = pageOffset + index) }

            val enrichedItems = tmdbEnrichmentService.enrichSummaries(items)
                .mapIndexed { index, item -> item.copy(positionInPage = pageOffset + index) }

            persistFavoriteReleasesToRoom(allItems = allItems, favoriteSeriesCount = favoriteSeriesCount)

            send(FavoriteReleasesResult.Success(
                items = enrichedItems,
                pageNumber = normalizedPageNumber,
                hasNextPage = allItems.size > pageOffset + FAVORITE_RELEASES_PAGE_SIZE,
                favoriteSeriesCount = favoriteSeriesCount,
            ))
        } catch (exception: CancellationException) {
            throw exception
        } catch (e: IOException) {
            Log.w(TAG, "Failed to load favorite releases", e)
            send(FavoriteReleasesResult.Unavailable())
        }
    }

    private suspend fun getOrFetchWatchedMarks(serialId: String, refererUrl: String): Set<String> {
        val now = clock()
        val cacheEntry = watchedMarksCache[serialId]
        if (cacheEntry != null && (now - cacheEntry.fetchedAt) < 30 * 60 * 1000L) { // 30 min cache TTL
            return cacheEntry.watchedIds
        }
        return try {
            val marksJson = httpClient.fetchSeasonWatchedEpisodeMarks(refererUrl, serialId)
            val parsedIds = seasonEpisodesParser.parseWatchedEpisodeIds(marksJson)
            watchedMarksCache[serialId] = WatchedMarksCacheEntry(parsedIds, now)
            parsedIds
        } catch (e: IOException) {
            Log.w(TAG, "Failed to fetch watched marks for serialId=$serialId", e)
            cacheEntry?.watchedIds ?: emptySet()
        }
    }

    private suspend fun fetchAllFavoriteReleasesStreaming(
        onPartial: suspend (items: List<ReleaseSummary>, favoriteSeriesCount: Int) -> Unit,
    ): AllFavoriteReleasesFetch? {
        val favoritesHtml = httpClient.fetchAccountPage(favoriteSeriesRoute)
        val favoriteSeries = favoriteSeriesParser.parse(favoritesHtml)
        if (favoriteSeries.isEmpty()) {
            return AllFavoriteReleasesFetch(allItems = emptyList(), favoriteSeriesCount = 0)
        }

        val fetchedAt = clock()
        val today = currentFavoriteReleaseDate()
        val totalSeriesCount = favoriteSeries.size

        val favoriteSeriesSemaphore = Semaphore(FAVORITE_SERIES_LOAD_CONCURRENCY)
        data class SeriesLoadResult(
            val items: List<ReleaseSummary>,
            val loaded: Boolean,
        )

        val accumulatedMutex = Mutex()
        var accumulatedItems: List<ReleaseSummary> = emptyList()

        val seriesResults = coroutineScope {
            favoriteSeries.map { series ->
                async {
                    favoriteSeriesSemaphore.withPermit {
                        val seasonsUrl = "${series.seriesUrl.trimEnd('/')}/seasons"
                        val seasonsHtml = try {
                            httpClient.fetchDetails(seasonsUrl)
                        } catch (e: IOException) {
                            Log.w(TAG, "Failed to fetch seasons page for favorite series", e)
                            return@withPermit SeriesLoadResult(emptyList(), loaded = false)
                        }

                        val serialId = seasonEpisodesParser.parseSerialId(seasonsHtml)

                        // Optimize watched marks: Only fetch AJAX marks if this series has a new release within the last 14 days,
                        // otherwise we fallback on seasonsHtml's checked buttons or local DB cache.
                        val parsedUncheckedEpisodes = withContext(Dispatchers.Default) {
                            seasonEpisodesParser.parse(
                                html = seasonsHtml,
                                series = series,
                                fetchedAt = fetchedAt,
                                watchedEpisodeIds = emptySet(),
                                maxEpisodesPerSeason = FAVORITE_RELEASES_MAX_EPISODES_PER_SEASON,
                                maxSeasons = FAVORITE_RELEASES_MAX_SEASONS_PER_SERIES,
                            )
                        }

                        val shouldFetchAJAXMarks = serialId != null && parsedUncheckedEpisodes.hasRecentRelease()

                        var watchedEpisodeIds = if (shouldFetchAJAXMarks && serialId != null) {
                            getOrFetchWatchedMarks(serialId, seasonsUrl)
                        } else {
                            emptySet()
                        }

                        if (watchedEpisodeIds.isEmpty() && shouldFetchAJAXMarks) {
                            try {
                                val rootHtml = httpClient.fetchDetails(series.seriesUrl)
                                watchedEpisodeIds = seasonEpisodesParser.parseWatchedEpisodeIdsFromPage(rootHtml)
                            } catch (e: IOException) {
                                Log.w(TAG, "Failed to fetch watched state from series root page", e)
                            }
                        }

                        val seriesItems = if (watchedEpisodeIds.isNotEmpty()) {
                            withContext(Dispatchers.Default) {
                                seasonEpisodesParser.parse(
                                    html = seasonsHtml,
                                    series = series,
                                    fetchedAt = fetchedAt,
                                    watchedEpisodeIds = watchedEpisodeIds,
                                    maxEpisodesPerSeason = FAVORITE_RELEASES_MAX_EPISODES_PER_SEASON,
                                    maxSeasons = FAVORITE_RELEASES_MAX_SEASONS_PER_SERIES,
                                )
                            }
                        } else {
                            parsedUncheckedEpisodes
                        }

                        if (seriesItems.isNotEmpty()) {
                            val snapshot = accumulatedMutex.withLock {
                                accumulatedItems = (accumulatedItems + seriesItems).distinctBy { it.detailsUrl }
                                accumulatedItems
                            }
                            onPartial(snapshot, totalSeriesCount)
                        }

                        SeriesLoadResult(items = seriesItems, loaded = true)
                    }
                }
            }.awaitAll()
        }

        val loadedAnySeasonPage = seriesResults.any { it.loaded }
        val rawItems = seriesResults
            .flatMap { it.items }
            .distinctBy { it.detailsUrl }

        val publishCheckSemaphore = Semaphore(FAVORITE_PUBLISH_CHECK_CONCURRENCY)
        val allItems = coroutineScope {
            rawItems.map { item ->
                async {
                    publishCheckSemaphore.withPermit {
                        val releaseDate = parseFavoriteReleaseDate(item.releaseDateRu) ?: return@withPermit null
                        when {
                            releaseDate.isBefore(today) -> item
                            releaseDate.isAfter(today) -> null
                            isFavoriteReleasePublishedToday(item.detailsUrl) -> item
                            else -> null
                        }
                    }
                }
            }.awaitAll()
        }
            .filterNotNull()
            .sortedByDescending { parseFavoriteReleaseDate(it.releaseDateRu) ?: LocalDate.MIN }
            .mapIndexed { index, item -> item.copy(positionInPage = index) }

        if (allItems.isEmpty() && !loadedAnySeasonPage) {
            return null
        }

        return AllFavoriteReleasesFetch(allItems = allItems, favoriteSeriesCount = favoriteSeries.size)
    }

    private suspend fun invalidateFavoriteReleasesCache() {
        favoriteReleasesCacheMutex.withLock { favoriteReleasesCache = null }
        releaseDao.deleteAllFavoriteReleasesCache()
        releaseDao.deleteAllFavoriteReleasesCacheMetadata()
    }

    override suspend fun invalidateCache() {
        invalidateFavoriteReleasesCache()
    }

    private suspend fun persistFavoriteReleasesToRoom(
        allItems: List<ReleaseSummary>,
        favoriteSeriesCount: Int,
    ) {
        val now = clock()
        val cacheEntities = allItems.mapIndexed { index, item ->
            FavoriteReleaseCacheEntity.fromModel(item, positionInList = index)
        }
        val metadata = FavoriteReleaseCacheMetadataEntity(
            fetchedAt = now,
            favoriteSeriesCount = favoriteSeriesCount,
            itemCount = allItems.size,
        )
        releaseDao.replaceFavoriteReleasesCache(cacheEntities, metadata)
    }

    override suspend fun loadFavoriteSeries(): FavoriteSeriesResult {
        if (!hasAuthenticatedSession()) {
            return FavoriteSeriesResult.Unavailable("Войдите в LostFilm")
        }

        return try {
            val fetchedAt = clock()
            val favoritesHtml = httpClient.fetchAccountPage(favoriteSeriesRoute)
            val items = favoriteSeriesParser.parse(favoritesHtml)
                .mapIndexed { index, series -> series.toFavoriteSeriesSummary(index, fetchedAt) }
            val enrichedItems = tmdbEnrichmentService.enrichSummaries(
                items = items,
                persistToCache = false,
            )

            FavoriteSeriesResult.Success(enrichedItems)
        } catch (e: IOException) {
            Log.w(TAG, "Failed to load favorite series list", e)
            FavoriteSeriesResult.Unavailable()
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
        return try {
            val favoritePage = fetchFavoriteMetadataPage(normalizedDetailsUrl)
                ?: return FavoriteMutationResult.Error("Не удалось обновить избранное")
            val favoriteMetadata = favoritePage.metadata
            persistFavoriteMetadata(normalizedDetailsUrl, favoriteMetadata)

            if (favoriteMetadata.isFavorite == targetFavorite) {
                return FavoriteMutationResult.NoOp
            }

            val ajaxSessionToken = detailsParser.parseAjaxSessionToken(favoritePage.html)
                ?: return FavoriteMutationResult.Error("Войдите в LostFilm")
            val toggleResult = httpClient.toggleFavorite(
                refererUrl = favoritePage.url,
                favoriteTargetId = favoriteMetadata.targetId,
                ajaxSessionToken = ajaxSessionToken,
            )

            val effectiveFavoriteState = when (toggleResult) {
                FavoriteToggleNetworkResult.ToggledOn -> true
                FavoriteToggleNetworkResult.ToggledOff -> false
                FavoriteToggleNetworkResult.Unknown -> {
                    val refreshedHtml = httpClient.fetchDetails(favoritePage.url)
                    detailsParser.parseFavoriteMetadata(refreshedHtml)?.isFavorite
                }
            }
            effectiveFavoriteState?.let { state ->
                persistFavoriteMetadata(normalizedDetailsUrl, favoriteMetadata.copy(isFavorite = state))
            }

            if (effectiveFavoriteState == targetFavorite) {
                invalidateFavoriteReleasesCache()
                FavoriteMutationResult.Updated
            } else {
                FavoriteMutationResult.Error("Не удалось обновить избранное")
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to toggle favorite", e)
            FavoriteMutationResult.Error("Не удалось обновить избранное")
        }
    }

    private fun List<ReleaseSummary>.hasRecentRelease(): Boolean {
        val fourteenDaysAgo = Instant.ofEpochMilli(clock())
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .minusDays(14)
        return any { item ->
            val releaseDate = parseFavoriteReleaseDate(item.releaseDateRu)
            releaseDate != null && !releaseDate.isBefore(fourteenDaysAgo)
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
            "$BASE_URL/series/${seriesMatch.groupValues[1]}/"
        } else {
            normalizedDetailsUrl
        }
    }

    private fun favoriteMetadataPageUrls(
        detailsUrl: String,
        kind: ReleaseKind? = null,
    ): List<String> {
        val normalizedDetailsUrl = resolveUrl(detailsUrl)
        val primaryUrl = when (kind) {
            ReleaseKind.SERIES -> seriesRootUrl(normalizedDetailsUrl) ?: favoriteMetadataPageUrl(normalizedDetailsUrl)
            ReleaseKind.MOVIE -> favoriteMetadataPageUrl(normalizedDetailsUrl)
            null -> favoriteMetadataPageUrl(normalizedDetailsUrl)
        }
        return listOf(primaryUrl, normalizedDetailsUrl).distinct()
    }

    private suspend fun fetchFavoriteMetadataPage(detailsUrl: String): FavoriteMetadataPage? {
        var lastException: IOException? = null
        for (candidateUrl in favoriteMetadataPageUrls(detailsUrl)) {
            try {
                val html = httpClient.fetchDetails(candidateUrl)
                val metadata = detailsParser.parseFavoriteMetadata(html) ?: continue
                return FavoriteMetadataPage(
                    url = candidateUrl,
                    html = html,
                    metadata = metadata,
                )
            } catch (exception: IOException) {
                lastException = exception
            }
        }
        lastException?.let { throw it }
        return null
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

    private fun seriesRootUrl(detailsUrl: String): String? {
        val normalizedDetailsUrl = resolveUrl(detailsUrl).trimEnd('/')
        val match = seriesRootUrlRegex.matchEntire(normalizedDetailsUrl) ?: return null
        return "$BASE_URL/series/${match.groupValues[1]}/"
    }

    private fun parseFavoriteReleaseDate(value: String): LocalDate? {
        return try {
            LocalDate.parse(value, favoriteReleaseDateFormatter)
        } catch (e: DateTimeParseException) {
            Log.w(TAG, "Failed to parse favorite release date", e)
            null
        }
    }

    private fun currentFavoriteReleaseDate(): LocalDate {
        return Instant.ofEpochMilli(clock())
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
    }

    private suspend fun isFavoriteReleasePublishedToday(detailsUrl: String): Boolean {
        return try {
            val detailsHtml = httpClient.fetchDetails(resolveUrl(detailsUrl))
            detailsParser.parsePlayEpisodeId(detailsHtml) != null
        } catch (e: IOException) {
            Log.w(TAG, "Failed to check if favorite release is published today", e)
            false
        }
    }

    private fun FavoriteSeriesRef.toFavoriteSeriesSummary(
        positionInPage: Int,
        fetchedAt: Long,
    ): ReleaseSummary = ReleaseSummary(
        id = seriesUrl,
        kind = ReleaseKind.SERIES,
        titleRu = titleRu,
        episodeTitleRu = null,
        seasonNumber = null,
        episodeNumber = null,
        releaseDateRu = "",
        posterUrl = posterUrl,
        detailsUrl = seriesUrl,
        pageNumber = 1,
        positionInPage = positionInPage,
        fetchedAt = fetchedAt,
    )
}
