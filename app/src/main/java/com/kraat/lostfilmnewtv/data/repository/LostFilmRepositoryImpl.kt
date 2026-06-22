package com.kraat.lostfilmnewtv.data.repository

import com.kraat.lostfilmnewtv.data.db.FavoriteReleaseCacheEntity
import com.kraat.lostfilmnewtv.data.db.FavoriteReleaseCacheMetadataEntity
import com.kraat.lostfilmnewtv.data.db.PageCacheMetadataEntity
import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.db.ReleaseDetailsEntity
import com.kraat.lostfilmnewtv.data.db.ReleaseSummaryEntity
import com.kraat.lostfilmnewtv.data.model.FavoriteMetadata
import com.kraat.lostfilmnewtv.data.model.FavoriteMutationResult
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.FavoriteSeriesResult
import com.kraat.lostfilmnewtv.data.model.FavoriteToggleNetworkResult
import com.kraat.lostfilmnewtv.data.model.LostFilmSearchItem
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.model.ScheduleItem
import com.kraat.lostfilmnewtv.data.model.ScheduleMonth
import com.kraat.lostfilmnewtv.data.model.SeriesGuide
import com.kraat.lostfilmnewtv.data.model.SeriesOverview
import com.kraat.lostfilmnewtv.data.model.TmdbImageUrls
import com.kraat.lostfilmnewtv.data.network.LostFilmConcurrencyLimits.FAVORITE_PUBLISH_CHECK_CONCURRENCY
import com.kraat.lostfilmnewtv.data.network.LostFilmConcurrencyLimits.FAVORITE_SERIES_LOAD_CONCURRENCY
import com.kraat.lostfilmnewtv.data.network.LostFilmConcurrencyLimits.SCHEDULE_IMAGE_ENRICHMENT_CONCURRENCY
import com.kraat.lostfilmnewtv.data.network.LostFilmConcurrencyLimits.SEARCH_ENRICHMENT_CONCURRENCY
import com.kraat.lostfilmnewtv.data.network.LostFilmConcurrencyLimits.SUMMARY_ENRICHMENT_CONCURRENCY
import com.kraat.lostfilmnewtv.data.network.LostFilmConcurrencyLimits.WATCHED_MARKS_LOAD_CONCURRENCY
import com.kraat.lostfilmnewtv.data.network.LostFilmHttpClient
import com.kraat.lostfilmnewtv.data.parser.BASE_URL
import com.kraat.lostfilmnewtv.data.parser.FavoriteSeriesRef
import com.kraat.lostfilmnewtv.data.parser.LostFilmDetailsParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmFavoriteSeriesParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmListParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmSearchParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmScheduleParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmSeriesCatalogParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmSeriesOverviewParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmSeasonEpisodesParser
import com.kraat.lostfilmnewtv.data.parser.absoluteUrl
import com.kraat.lostfilmnewtv.data.parser.extractYear
import com.kraat.lostfilmnewtv.data.parser.normalizeText
import com.kraat.lostfilmnewtv.data.parser.resolveUrl
import com.kraat.lostfilmnewtv.data.parser.textOrEmpty
import com.kraat.lostfilmnewtv.data.parser.toEntity
import com.kraat.lostfilmnewtv.data.parser.toSummaryEntities
import com.kraat.lostfilmnewtv.data.parser.toSummaryModels
import com.kraat.lostfilmnewtv.data.poster.TmdbPosterEnricher
import com.kraat.lostfilmnewtv.data.poster.TmdbPosterResolver
import com.kraat.lostfilmnewtv.data.poster.TmdbEnrichmentService
import dagger.Lazy
import android.util.Log
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

private const val TAG = "LostFilmRepository"
private const val FRESH_WINDOW_MS = 6 * 60 * 60 * 1000L
private const val RETENTION_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L
private const val FAVORITE_RELEASES_MAX_EPISODES_PER_SEASON = Int.MAX_VALUE
private const val FAVORITE_RELEASES_PAGE_SIZE = 30
private const val FAVORITE_RELEASES_MAX_SEASONS_PER_SERIES = 1

// How long the full favorite-releases listing (see [LostFilmRepositoryImpl.favoriteReleasesCache])
// stays valid before we re-fetch it from the network on page 1. Pages beyond the first always
// reuse whatever is cached, however stale, since they are only requested while a paging session
// for the same listing is still in progress.
private const val FAVORITE_RELEASES_CACHE_TTL_MS = 2 * 60 * 1000L

// How long the Room-persisted favorite-releases cache stays fresh. While the cache is fresh,
// observeFavoriteReleases skips the expensive fan-out and serves data directly from Room.
private const val FAVORITE_RELEASES_ROOM_FRESH_MS = 15 * 60 * 1000L

// How long the Room-persisted page 1 cache stays fresh. While the cache is fresh,
// observeNewReleases skips the network request and serves data directly from Room.
private const val NEW_RELEASES_ROOM_FRESH_MS = 10 * 60 * 1000L
private const val MOVIES_PAGE_SIZE = 20
private const val SERIES_CATALOG_PAGE_SIZE = 20
private const val CLEANUP_INTERVAL_MS = 6 * 60 * 60 * 1000L
private const val YEAR_AWARE_TMDB_MATCHING_MIN_FETCHED_AT_MS = 1777852800000L // 2026-05-04
private val paginatorRegex = Regex("""/new/page_(\d+)""")
private const val favoriteSeriesRoute = "/my/type_1"
private val seriesFavoritePageRegex = Regex("""${Regex.escape(BASE_URL)}/series/([^/]+)/season_\d+/episode_\d+/?""")
private val seriesRootUrlRegex = Regex("""${Regex.escape(BASE_URL)}/series/([^/]+)(?:/.*)?/?""")
private val searchWhitespaceRegex = Regex("""\s+""")
private val favoriteReleaseDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private data class FavoriteMetadataPage(
    val url: String,
    val html: String,
    val metadata: FavoriteMetadata,
)

class LostFilmRepositoryImpl(
    private val httpClient: LostFilmHttpClient,
    private val anonymousHttpClient: LostFilmHttpClient = httpClient,
    private val releaseDao: ReleaseDao,
    private val listParser: LostFilmListParser,
    private val detailsParser: LostFilmDetailsParser,
    private val favoriteSeriesParser: LostFilmFavoriteSeriesParser = LostFilmFavoriteSeriesParser(),
    private val seasonEpisodesParser: LostFilmSeasonEpisodesParser = LostFilmSeasonEpisodesParser(),
    private val searchParser: LostFilmSearchParser = LostFilmSearchParser(),
    private val scheduleParser: LostFilmScheduleParser = LostFilmScheduleParser(),
    private val seriesCatalogParser: LostFilmSeriesCatalogParser = LostFilmSeriesCatalogParser(),
    private val seriesOverviewParser: LostFilmSeriesOverviewParser = LostFilmSeriesOverviewParser(),
    private val tmdbResolver: TmdbPosterResolver,
    private val tmdbEnrichmentService: TmdbEnrichmentService,
    private val favoritesRepository: Lazy<FavoritesRepository>,
    private val hasAuthenticatedSession: suspend () -> Boolean,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : LostFilmRepository {
    private val cleanupMutex = Mutex()
    private var lastCleanupAt = 0L
    // Кеш (isWatched, ajaxSessionToken) после последнего loadWatchedState — позволяет
    // setEpisodeWatched не делать повторный fetchDetails ради получения токена.
    private val watchedPageCache = ConcurrentHashMap<String, Pair<Boolean?, String?>>()

    override suspend fun loadPage(pageNumber: Int): PageState {
        cleanupExpiredDataIfNeeded()

        return try {
            val fetchedAt = clock()
            val hasAuthenticatedSession = hasAuthenticatedSession()
            val html = anonymousHttpClient.fetchNewPage(pageNumber)
            val parsedItems = withContext(Dispatchers.Default) {
                listParser.parse(
                    html = html,
                    pageNumber = pageNumber,
                    fetchedAt = fetchedAt,
                )
            }
            val itemsToPersist = mergeWatchedState(
                pageNumber = pageNumber,
                html = html,
                parsedItems = parsedItems,
                hasAuthenticatedSession = hasAuthenticatedSession,
            )

            val pageHasNext = hasNextPage(html, pageNumber, parsedItems.isNotEmpty())
            releaseDao.replacePage(
                pageNumber = pageNumber,
                summaries = itemsToPersist.toSummaryEntities(),
                metadata = PageCacheMetadataEntity(
                    pageNumber = pageNumber,
                    fetchedAt = fetchedAt,
                    itemCount = parsedItems.size,
                    hasNextPage = pageHasNext,
                ),
            )

            // Only enrich the freshly fetched page; previous pages already keep their TMDB posters in Room.
            val enrichedItems = tmdbEnrichmentService.enrichSummaries(
                items = itemsToPersist,
                persistToCache = true,
            )
            val pageItems = if (pageNumber > 1) {
                releaseDao.getSummariesUpToPage(pageNumber).toSummaryModels()
            } else {
                enrichedItems
            }

            PageState.Content(
                pageNumber = pageNumber,
                items = pageItems,
                hasNextPage = pageHasNext,
                isStale = false,
                isAppend = pageNumber > 1,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (exception is IOException || exception is IllegalStateException) {
                fallbackPageState(pageNumber, exception)
            } else {
                throw exception
            }
        }
    }

    override fun observeNewReleases(pageNumber: Int): Flow<PageState> = flow {
        // 1. Сначала отдаём кэш из Room (если он есть), без обращения к сети.
        //    Скелетон в HomeScreen не показывается, если items непустые и isInitialLoading=false —
        //    поэтому в HomeViewModel на cache-эмиссии нужно одновременно сбросить этот флаг
        //    и fullScreenErrorMessage, иначе UI решит, что данных нет.
        val cachedItems = releaseDao.getSummariesUpToPage(pageNumber).toSummaryModels()
        val metadata = if (cachedItems.isNotEmpty()) releaseDao.getPageMetadata(pageNumber) else null
        if (cachedItems.isNotEmpty()) {
            val cacheFresh = metadata != null &&
                (clock() - metadata.fetchedAt) < NEW_RELEASES_ROOM_FRESH_MS
            emit(
                PageState.Content(
                    pageNumber = pageNumber,
                    items = cachedItems,
                    hasNextPage = metadata?.hasNextPage ?: true,
                    isStale = !cacheFresh,
                ),
            )
            // Если кэш свежий — пропускаем сетевой запрос.
            if (cacheFresh) return@flow
        }
        // 2. Запускаем свежую загрузку. Если сеть упала, а кэш был показан,
        //    HomeViewModel сам решит не стирать items (retainVisibleItemsOnFailure-семантика).
        //    Контекст исполнения приходит от коллектора (ViewModel запускает collect на ioDispatcher).
        emit(loadPage(pageNumber))
    }

    override suspend fun loadMovies(pageNumber: Int): PageState {
        cleanupExpiredDataIfNeeded()

        return try {
            val fetchedAt = clock()
            val html = anonymousHttpClient.fetchMoviesPage(pageNumber)
            val parsedItems = withContext(Dispatchers.Default) {
                listParser.parse(
                    html = html,
                    pageNumber = pageNumber,
                    fetchedAt = fetchedAt,
                )
            }
            val enrichedItems = tmdbEnrichmentService.enrichSummaries(
                items = parsedItems,
                persistToCache = false,
            )

            PageState.Content(
                pageNumber = pageNumber,
                items = enrichedItems,
                hasNextPage = parsedItems.size >= MOVIES_PAGE_SIZE,
                isStale = false,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (exception is IOException || exception is IllegalStateException) {
                PageState.Error(
                    pageNumber = pageNumber,
                    message = exception.message ?: "Unable to load movies",
                )
            } else {
                throw exception
            }
        }
    }

    override suspend fun loadSeriesCatalog(pageNumber: Int): PageState {
        return try {
            val fetchedAt = clock()
            val json = anonymousHttpClient.fetchSeriesCatalogPage(pageNumber)
            val parsedItems = withContext(Dispatchers.Default) {
                seriesCatalogParser.parseSearchJson(
                    json = json,
                    pageNumber = pageNumber,
                    fetchedAt = fetchedAt,
                )
            }
            val enrichedItems = tmdbEnrichmentService.enrichSummaries(
                items = parsedItems,
                persistToCache = false,
            )

            PageState.Content(
                pageNumber = pageNumber,
                items = enrichedItems,
                hasNextPage = parsedItems.size >= SERIES_CATALOG_PAGE_SIZE,
                isStale = false,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (exception is IOException || exception is IllegalStateException) {
                PageState.Error(
                    pageNumber = pageNumber,
                    message = exception.message ?: "Unable to load series catalog",
                )
            } else {
                PageState.Error(
                    pageNumber = pageNumber,
                    message = exception.message ?: "Unable to parse series catalog",
                    retryable = false,
                )
            }
        }
    }

    override suspend fun loadDetails(detailsUrl: String): DetailsResult {
        val normalizedDetailsUrl = resolveUrl(detailsUrl)
        cleanupExpiredDataIfNeeded()
        val cachedDetails = releaseDao.getReleaseDetails(normalizedDetailsUrl)
        val now = clock()

        if (cachedDetails != null && now - cachedDetails.fetchedAt <= FRESH_WINDOW_MS) {
            val cachedModel = enrichWithSummaryEpisodeTitle(
                refreshFavoriteMetadataIfNeeded(
                    refreshCachedTorrentLinksIfNeeded(cachedDetails.toModel()),
                ),
            )
            // Fully enriched cached details already include TMDB artwork and LostFilm overview, so avoid touching the resolver.
            // Movie details cached before year-aware TMDB matching can contain a wrong same-title poster.
            if (
                cachedModel.hasCompleteArtwork() &&
                cachedModel.hasDetailsOverview() &&
                cachedModel.hasDetailsHeroOverview() &&
                !cachedDetails.needsYearAwareMovieArtworkRefresh()
            ) {
                return DetailsResult.Success(
                    details = cachedModel,
                    isStale = false,
                )
            }

            if (!cachedDetails.needsYearAwareMovieArtworkRefresh()) {
                val tmdbUrls = tmdbResolver.resolve(
                    detailsUrl = cachedModel.detailsUrl,
                    titleRu = cachedModel.titleRu,
                    releaseDateRu = cachedModel.releaseDateRu,
                    kind = cachedModel.kind,
                    originalReleaseYear = cachedModel.originalReleaseYear,
                )
                val enrichedCachedModel = TmdbPosterEnricher.enrichDetails(cachedModel, tmdbUrls)
                releaseDao.upsertDetails(enrichedCachedModel.toEntity())
                return DetailsResult.Success(
                    details = enrichedCachedModel,
                    isStale = false,
                )
            }
        }

        return try {
            val html = httpClient.fetchDetails(normalizedDetailsUrl)
            val parsed = enrichWithSummaryEpisodeTitle(
                refreshFavoriteMetadataIfNeeded(
                    enrichWithTorrentLinks(parseDetails(html, normalizedDetailsUrl, now)),
                ),
            )
            val tmdbUrls = tmdbResolver.resolve(
                detailsUrl = parsed.detailsUrl,
                titleRu = parsed.titleRu,
                releaseDateRu = parsed.releaseDateRu,
                kind = parsed.kind,
                originalReleaseYear = parsed.originalReleaseYear,
            )
            val enriched = TmdbPosterEnricher.enrichDetails(parsed, tmdbUrls)
            releaseDao.upsertDetails(enriched.toEntity())

            DetailsResult.Success(
                details = enriched,
                isStale = false,
            )
        } catch (exception: CancellationException) {
            throw exception
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

    override suspend fun loadDetailsPreview(detailsUrl: String): DetailsResult {
        val normalizedDetailsUrl = resolveUrl(detailsUrl)
        cleanupExpiredDataIfNeeded()
        val cachedDetails = releaseDao.getReleaseDetails(normalizedDetailsUrl)
        val now = clock()

        if (cachedDetails != null && now - cachedDetails.fetchedAt < RETENTION_WINDOW_MS) {
            return DetailsResult.Success(
                details = enrichWithSummaryEpisodeTitle(cachedDetails.toModel()),
                isStale = now - cachedDetails.fetchedAt > FRESH_WINDOW_MS,
            )
        }

        return try {
            val html = httpClient.fetchDetails(normalizedDetailsUrl)
            val parsed = enrichWithSummaryEpisodeTitle(parseDetails(html, normalizedDetailsUrl, now))
            releaseDao.upsertDetails(parsed.toEntity())
            DetailsResult.Success(
                details = parsed,
                isStale = false,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (exception is IOException || exception is IllegalStateException) {
                DetailsResult.Error(
                    detailsUrl = normalizedDetailsUrl,
                    message = exception.message ?: "Unable to load details",
                )
            } else {
                throw exception
            }
        }
    }

    override suspend fun refreshDetailsExtras(details: ReleaseDetails): DetailsResult {
        val normalizedDetailsUrl = resolveUrl(details.detailsUrl)
        return try {
            val enriched = coroutineScope {
                val torrentDetails = async {
                    try {
                        refreshCachedTorrentLinksIfNeeded(details)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to refresh cached torrent links", e)
                        details
                    }
                }
                val metadataDetails = async {
                    try {
                        refreshFavoriteMetadataIfNeeded(details)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to refresh favorite metadata", e)
                        details
                    }
                }
                val tmdbUrls = async {
                    try {
                        tmdbResolver.resolve(
                            detailsUrl = details.detailsUrl,
                            titleRu = details.titleRu,
                            releaseDateRu = details.releaseDateRu,
                            kind = details.kind,
                            originalReleaseYear = details.originalReleaseYear,
                        )
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to resolve TMDB poster for details extras", e)
                        null
                    }
                }

                TmdbPosterEnricher.enrichDetails(
                    details
                        .mergeTorrentLinksFrom(torrentDetails.await())
                        .mergeFavoriteMetadataFrom(metadataDetails.await()),
                    tmdbUrls.await(),
                )
            }
            val withSummary = enrichWithSummaryEpisodeTitle(enriched)
            releaseDao.upsertDetails(withSummary.toEntity())
            DetailsResult.Success(
                details = withSummary,
                isStale = false,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (exception is IOException || exception is IllegalStateException) {
                DetailsResult.Error(
                    detailsUrl = normalizedDetailsUrl,
                    message = exception.message ?: "Unable to refresh details",
                )
            } else {
                throw exception
            }
        }
    }

    override suspend fun loadSeriesGuide(detailsUrl: String): SeriesGuideResult {
        val normalizedDetailsUrl = resolveUrl(detailsUrl)
        val seriesRootUrl = seriesRootUrl(normalizedDetailsUrl)
            ?: return SeriesGuideResult.Error("Гид по сериям недоступен")
        val seasonsUrl = "${seriesRootUrl.trimEnd('/')}/seasons"

        return try {
            val seriesRootHtml = httpClient.fetchDetails(seriesRootUrl)
            val seasonsHtml = httpClient.fetchDetails(seasonsUrl)
            val watchedEpisodeIdsFromSeriesRoot = seasonEpisodesParser.parseWatchedEpisodeIdsFromPage(seriesRootHtml)
            val watchedEpisodeIdsFromMarks = if (hasAuthenticatedSession()) {
                seasonEpisodesParser.parseSerialId(seasonsHtml)
                    ?.let { serialId ->
                        try {
                            seasonEpisodesParser.parseWatchedEpisodeIds(
                                httpClient.fetchSeasonWatchedEpisodeMarks(
                                    refererUrl = seasonsUrl,
                                    serialId = serialId,
                                ),
                            )
                        } catch (e: IOException) {
                            Log.w(TAG, "Failed to fetch watched marks for series guide", e)
                            emptySet()
                        }
                    }
                    .orEmpty()
            } else {
                emptySet()
            }
            val guideDocument = Jsoup.parse(seasonsHtml, BASE_URL)
            val seriesTitleRu = guideDocument.selectFirst(".title-block .title-ru, h1.title-ru, h1")
                .textOrEmpty()
                .ifBlank { fallbackSeriesTitle(seriesRootUrl) }

            val tmdbUrls = tmdbResolver.resolve(
                detailsUrl = normalizedDetailsUrl,
                titleRu = seriesTitleRu,
                releaseDateRu = "",
                kind = ReleaseKind.SERIES,
            )
            val posterUrl = tmdbUrls?.posterUrl?.takeIf { it.isNotBlank() }.orEmpty()

            SeriesGuideResult.Success(
                guide = SeriesGuide(
                    seriesTitleRu = seriesTitleRu,
                    posterUrl = posterUrl,
                    selectedEpisodeDetailsUrl = normalizedDetailsUrl,
                    seasons = seasonEpisodesParser.parseGuide(
                        html = seasonsHtml,
                        watchedEpisodeIds = watchedEpisodeIdsFromSeriesRoot + watchedEpisodeIdsFromMarks,
                    ),
                ),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            SeriesGuideResult.Error(exception.message ?: "Не удалось загрузить гид по сериям")
        }
    }

    override suspend fun loadSeriesOverview(detailsUrl: String): SeriesOverviewResult {
        val normalizedDetailsUrl = resolveUrl(detailsUrl)
        val seriesRootUrl = seriesRootUrl(normalizedDetailsUrl)
            ?: return SeriesOverviewResult.Error("Обзор недоступен")

        return try {
            val overviewHtml = anonymousHttpClient.fetchDetails(seriesRootUrl)
            val parsedOverview = withContext(Dispatchers.Default) {
                seriesOverviewParser.parse(
                    html = overviewHtml,
                    seriesUrl = seriesRootUrl,
                )
            }
            val tmdbUrls = tmdbResolver.resolve(
                detailsUrl = normalizedDetailsUrl,
                titleRu = parsedOverview.titleRu,
                releaseDateRu = parsedOverview.premiereDateRu.orEmpty(),
                kind = ReleaseKind.SERIES,
            )

            SeriesOverviewResult.Success(
                overview = parsedOverview.copy(
                    posterUrl = tmdbUrls?.posterUrl?.takeIf { it.isNotBlank() },
                    backdropUrl = tmdbUrls?.backdropUrl?.takeIf { it.isNotBlank() } ?: parsedOverview.backdropUrl,
                    tmdbRating = tmdbUrls?.rating?.takeIf { it.isNotBlank() } ?: parsedOverview.tmdbRating,
                    descriptionRu = parsedOverview.descriptionRu
                        ?: tmdbUrls?.seriesOverviewRu?.takeIf { it.isNotBlank() },
                ),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            SeriesOverviewResult.Error(exception.message ?: "Не удалось загрузить обзор")
        }
    }

    override suspend fun search(query: String): SearchResultsResult {
        val normalizedQuery = query.normalizeSearchQuery()
        if (normalizedQuery.length < 2) {
            return SearchResultsResult.Success(
                query = normalizedQuery,
                items = emptyList(),
            )
        }

        return try {
            val encodedQuery = URLEncoder.encode(normalizedQuery, StandardCharsets.UTF_8.toString())
                .replace("+", "%20")
            val html = anonymousHttpClient.fetchDetails("$BASE_URL/search/?q=$encodedQuery")
            val parsedItems = withContext(Dispatchers.Default) {
                searchParser.parse(html)
            }

            SearchResultsResult.Success(
                query = normalizedQuery,
                items = tmdbEnrichmentService.enrichSearchItems(parsedItems),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (exception is IOException || exception is IllegalStateException) {
                SearchResultsResult.Error(
                    query = normalizedQuery,
                    message = exception.message ?: "Не удалось выполнить поиск",
                )
            } else {
                throw exception
            }
        }
    }

    override suspend fun loadSchedule(): ScheduleResult {
        return try {
            val html = anonymousHttpClient.fetchSchedulePage()
            val schedule = withContext(Dispatchers.Default) {
                scheduleParser.parse(html)
            }
            ScheduleResult.Success(enrichScheduleWithLostFilmImages(schedule))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (exception is IOException || exception is IllegalStateException) {
                ScheduleResult.Error(exception.message ?: "Не удалось загрузить расписание")
            } else {
                throw exception
            }
        }
    }

    private suspend fun enrichScheduleWithLostFilmImages(schedule: ScheduleMonth): ScheduleMonth {
        val items = schedule.days.flatMap { it.items }
        val missingPosterItems = items.filter { it.posterUrl.isNullOrBlank() }
        if (missingPosterItems.isEmpty()) {
            return schedule
        }

        val cachedSummariesByUrl = releaseDao.getSummaries(missingPosterItems.map { it.targetUrl })
            .associateBy { it.detailsUrl }
        val semaphore = Semaphore(SCHEDULE_IMAGE_ENRICHMENT_CONCURRENCY)
        val posterUrlsByTargetUrl = coroutineScope {
            missingPosterItems.map { item ->
                async {
                    item.targetUrl to schedulePosterUrlFromLostFilm(
                        item = item,
                        cachedSummaryPosterUrl = cachedSummariesByUrl[item.targetUrl]?.posterUrl,
                        semaphore = semaphore,
                    )
                }
            }.awaitAll()
        }.toMap()

        return schedule.copy(
            days = schedule.days.map { day ->
                day.copy(
                    items = day.items.map { item ->
                        item.copy(
                            posterUrl = item.posterUrl
                                ?: posterUrlsByTargetUrl[item.targetUrl]?.takeIf { it.isNotBlank() },
                        )
                    },
                )
            },
        )
    }

    private suspend fun schedulePosterUrlFromLostFilm(
        item: ScheduleItem,
        cachedSummaryPosterUrl: String?,
        semaphore: Semaphore,
    ): String? {
        cachedSummaryPosterUrl?.takeIf { it.isLostFilmImageUrl() }?.let { return it }
        releaseDao.getReleaseDetails(item.targetUrl)
            ?.posterUrl
            ?.takeIf { it.isLostFilmImageUrl() }
            ?.let { return it }

        return semaphore.withPermit {
            try {
                val fetchedPosterUrl = detailsParser.parsePosterUrl(anonymousHttpClient.fetchDetails(item.targetUrl))
                    .takeIf { it.isLostFilmImageUrl() }
                if (fetchedPosterUrl != null) {
                    releaseDao.getSummary(item.targetUrl)?.let { existing ->
                        releaseDao.upsertSummaries(listOf(existing.copy(posterUrl = fetchedPosterUrl)))
                    }
                }
                fetchedPosterUrl
            } catch (exception: CancellationException) {
                throw exception
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch schedule poster from LostFilm", e)
                null
            }
        }
    }

    override suspend fun loadWatchedState(detailsUrl: String): Boolean? {
        if (!hasAuthenticatedSession()) {
            return null
        }

        val normalizedDetailsUrl = resolveUrl(detailsUrl)
        return try {
            val detailsHtml = httpClient.fetchDetails(normalizedDetailsUrl)
            val watchedState = detailsParser.parseWatchedState(detailsHtml)
            val ajaxToken = detailsParser.parseAjaxSessionToken(detailsHtml)
            watchedPageCache[normalizedDetailsUrl] = watchedState to ajaxToken
            watchedState?.let { isWatched ->
                releaseDao.updateSummaryWatched(
                    detailsUrl = normalizedDetailsUrl,
                    isWatched = isWatched,
                )
            }
            watchedState
        } catch (e: IOException) {
            Log.w(TAG, "Failed to load watched state", e)
            null
        }
    }

    private suspend fun enrichSearchItems(items: List<LostFilmSearchItem>): List<LostFilmSearchItem> {
        if (items.isEmpty()) {
            return emptyList()
        }

        val semaphore = Semaphore(SEARCH_ENRICHMENT_CONCURRENCY)
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

    override suspend fun setEpisodeWatched(
        detailsUrl: String,
        playEpisodeId: String,
        targetWatched: Boolean,
    ): Boolean? {
        if (!hasAuthenticatedSession()) {
            return null
        }

        val normalizedDetailsUrl = resolveUrl(detailsUrl)
        return try {
            val cached = watchedPageCache[normalizedDetailsUrl]
            val (currentWatched, ajaxSessionToken) = if (cached != null) {
                cached
            } else {
                val detailsHtml = httpClient.fetchDetails(normalizedDetailsUrl)
                val ws = detailsParser.parseWatchedState(detailsHtml)
                val token = detailsParser.parseAjaxSessionToken(detailsHtml)
                Log.d(TAG, "setEpisodeWatched: page fetch ws=$ws hasToken=${token != null} target=$targetWatched")
                val fresh = ws to token
                watchedPageCache[normalizedDetailsUrl] = fresh
                fresh
            }
            Log.d(TAG, "setEpisodeWatched: currentWatched=$currentWatched target=$targetWatched")
            if (ajaxSessionToken == null) {
                Log.w(TAG, "setEpisodeWatched: no ajax token — session likely expired, cannot call API")
                return null
            }
            if (currentWatched == targetWatched) {
                Log.d(TAG, "setEpisodeWatched: already at target=$targetWatched, no-op")
                return currentWatched
            }
            val requestSucceeded = httpClient.setEpisodeWatched(
                detailsUrl = normalizedDetailsUrl,
                playEpisodeId = playEpisodeId,
                ajaxSessionToken = ajaxSessionToken,
                targetWatched = targetWatched,
            )
            Log.d(TAG, "setEpisodeWatched: AJAX requestSucceeded=$requestSucceeded target=$targetWatched")
            val effectiveWatched = if (requestSucceeded) {
                targetWatched
            } else {
                val refreshedDetailsHtml = httpClient.fetchDetails(normalizedDetailsUrl)
                val refreshedWatched = detailsParser.parseWatchedState(refreshedDetailsHtml)
                val refreshedToken = detailsParser.parseAjaxSessionToken(refreshedDetailsHtml)
                Log.d(TAG, "setEpisodeWatched: fallback page refreshedWatched=$refreshedWatched")
                watchedPageCache[normalizedDetailsUrl] = refreshedWatched to refreshedToken
                if (refreshedWatched == targetWatched) {
                    refreshedWatched
                } else {
                    refreshedWatched ?: currentWatched
                }
            }
            Log.d(TAG, "setEpisodeWatched: effectiveWatched=$effectiveWatched")
            if (effectiveWatched != null) {
                watchedPageCache[normalizedDetailsUrl] = effectiveWatched to ajaxSessionToken
                releaseDao.updateSummaryWatched(
                    detailsUrl = normalizedDetailsUrl,
                    isWatched = effectiveWatched,
                )
                favoritesRepository.get().invalidateCache()
            }
            effectiveWatched
        } catch (e: IOException) {
            Log.w(TAG, "Failed to set episode watched state", e)
            null
        }
    }


    private suspend fun fallbackPageState(pageNumber: Int, exception: Exception): PageState {
        val now = clock()
        val cachedMetadata = releaseDao.getPageMetadata(pageNumber)

        if (cachedMetadata != null && now - cachedMetadata.fetchedAt < RETENTION_WINDOW_MS) {
            val cachedItems = releaseDao.getSummariesUpToPage(pageNumber).toSummaryModels()
            val enrichedItems = tmdbEnrichmentService.enrichSummaries(
                items = cachedItems,
                persistToCache = true,
            )
            return PageState.Content(
                pageNumber = pageNumber,
                items = enrichedItems,
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
                val enrichedItems = tmdbEnrichmentService.enrichSummaries(
                    items = previousItems,
                    persistToCache = true,
                )
                return PageState.Content(
                    pageNumber = pageNumber - 1,
                    items = enrichedItems,
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


    private suspend fun cleanupExpiredDataIfNeeded() {
        val now = clock()
        if (now - lastCleanupAt < CLEANUP_INTERVAL_MS) {
            return
        }

        cleanupMutex.withLock {
            val lockedNow = clock()
            if (lockedNow - lastCleanupAt < CLEANUP_INTERVAL_MS) {
                return
            }
            releaseDao.deleteExpiredData(lockedNow - RETENTION_WINDOW_MS)
            lastCleanupAt = lockedNow
        }
    }

    private fun ReleaseDetails.hasCompleteArtwork(): Boolean {
        return posterUrl.isNotBlank() && !backdropUrl.isNullOrBlank()
    }

    private fun ReleaseDetails.hasDetailsOverview(): Boolean {
        return when (kind) {
            ReleaseKind.SERIES -> !episodeOverviewRu.isNullOrBlank()
            ReleaseKind.MOVIE -> !episodeOverviewRu.isNullOrBlank()
        }
    }

    private fun ReleaseDetails.hasDetailsHeroOverview(): Boolean {
        return kind != ReleaseKind.MOVIE || !movieOverviewRu.isNullOrBlank()
    }

    private fun ReleaseDetailsEntity.needsYearAwareMovieArtworkRefresh(): Boolean {
        return kind == ReleaseKind.MOVIE.name && fetchedAt < YEAR_AWARE_TMDB_MATCHING_MIN_FETCHED_AT_MS
    }

    private suspend fun mergeWatchedState(
        pageNumber: Int,
        html: String,
        parsedItems: List<com.kraat.lostfilmnewtv.data.model.ReleaseSummary>,
        hasAuthenticatedSession: Boolean,
    ): List<com.kraat.lostfilmnewtv.data.model.ReleaseSummary> {
        val watchedStateByUrl = if (hasAuthenticatedSession) {
            fetchWatchedStateByUrl(
                pageNumber = pageNumber,
                html = html,
            )
        } else {
            emptyMap()
        }
        val itemsWithRemoteState = parsedItems.map { item ->
            watchedStateByUrl[item.detailsUrl]
                ?.let { isWatched -> item.copy(isWatched = isWatched) }
                ?: item
        }

        return preserveWatchedState(
            pageNumber = pageNumber,
            parsedItems = itemsWithRemoteState,
            skipDetailsUrls = watchedStateByUrl.keys,
        )
    }

    private suspend fun fetchWatchedStateByUrl(
        pageNumber: Int,
        html: String,
    ): Map<String, Boolean> {
        val watchMarkers = listParser.parseWatchMarkers(html)
        if (watchMarkers.isEmpty()) {
            return emptyMap()
        }

        val refererUrl = if (pageNumber <= 1) {
            "$BASE_URL/new/"
        } else {
            "$BASE_URL/new/page_$pageNumber"
        }
        // Watched-marker AJAX calls are per serial id, so cap them independently from TMDB work.
        val watchedMarksSemaphore = Semaphore(WATCHED_MARKS_LOAD_CONCURRENCY)
        val watchedIdsBySerialId = coroutineScope {
            watchMarkers
                .map { it.serialId }
                .distinct()
                .map { serialId ->
                    async {
                        watchedMarksSemaphore.withPermit {
                            serialId to try {
                                seasonEpisodesParser.parseWatchedEpisodeIds(
                                    httpClient.fetchSeasonWatchedEpisodeMarks(
                                        refererUrl = refererUrl,
                                        serialId = serialId,
                                    ),
                                )
                            } catch (e: IOException) {
                                Log.w(TAG, "Failed to fetch watched marks for new releases page", e)
                                null
                            }
                        }
                    }
                }
                .awaitAll()
                .toMap()
        }

        return watchMarkers.mapNotNull { marker ->
            watchedIdsBySerialId[marker.serialId]?.let { watchedIds ->
                marker.detailsUrl to watchedIds.contains(marker.episodeId)
            }
        }.toMap()
    }

    private suspend fun preserveWatchedState(
        pageNumber: Int,
        parsedItems: List<com.kraat.lostfilmnewtv.data.model.ReleaseSummary>,
        skipDetailsUrls: Set<String> = emptySet(),
    ): List<com.kraat.lostfilmnewtv.data.model.ReleaseSummary> {
        val existingWatchedByUrl = releaseDao.getPageSummaries(pageNumber)
            .associate { entity -> entity.detailsUrl to entity.isWatched }
        return parsedItems.map { item ->
            if (item.detailsUrl !in skipDetailsUrls && existingWatchedByUrl[item.detailsUrl] == true) {
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

        return withContext(Dispatchers.Default) {
            when (resolvedKind) {
                ReleaseKind.SERIES -> detailsParser.parseSeries(html, detailsUrl, fetchedAt)
                ReleaseKind.MOVIE -> detailsParser.parseMovie(html, detailsUrl, fetchedAt)
            }
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
                } catch (e: IOException) {
                    Log.w(TAG, "Failed to expand single-variant torrent link", e)
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
        } catch (e: IOException) {
            Log.w(TAG, "Failed to fetch torrent redirect", e)
            details
        }
    }

    private suspend fun refreshCachedTorrentLinksIfNeeded(details: ReleaseDetails): ReleaseDetails {
        Log.d("REFRESH_TORRENT", "playEpisodeId=${details.playEpisodeId}, links=${details.torrentLinks.size}, hasAuth=${hasAuthenticatedSession()}, detailsUrl=${details.detailsUrl}")
        if (details.playEpisodeId == null) {
            if (!hasAuthenticatedSession()) {
                Log.d("REFRESH_TORRENT", "early return: no playEpisodeId, no auth")
                return details
            }
            Log.d("REFRESH_TORRENT", "playEpisodeId is null, re-fetching HTML")
            val refreshed = try {
                val html = httpClient.fetchDetails(resolveUrl(details.detailsUrl))
                val pid = detailsParser.parsePlayEpisodeId(html)
                if (pid != null) details.copy(playEpisodeId = pid) else null
            } catch (e: Exception) { Log.w(TAG, "Failed to re-fetch playEpisodeId", e); null }
            if (refreshed == null) {
                Log.d("REFRESH_TORRENT", "still no playEpisodeId after re-fetch")
                return details
            }
            Log.d("REFRESH_TORRENT", "re-fetched playEpisodeId=${refreshed.playEpisodeId}")
            releaseDao.upsertDetails(refreshed.toEntity())
            val enriched = enrichWithTorrentLinks(refreshed)
            Log.d("REFRESH_TORRENT", "enriched links=${enriched.torrentLinks.size}")
            if (enriched.torrentLinks.isNotEmpty()) {
                releaseDao.upsertDetails(enriched.toEntity())
            }
            return enriched
        }

        if (details.torrentLinks.isNotEmpty() && details.torrentLinks.none { it.label == "Вариант 1" }) {
            Log.d("REFRESH_TORRENT", "early return: already has resolved links")
            return details
        }

        if (details.torrentLinks.isEmpty() && !hasAuthenticatedSession()) {
            Log.d("REFRESH_TORRENT", "early return: no auth session")
            return details
        }

        val enriched = enrichWithTorrentLinks(details)
        Log.d("REFRESH_TORRENT", "enriched links=${enriched.torrentLinks.size}")
        if (enriched.torrentLinks.isNotEmpty()) {
            releaseDao.upsertDetails(enriched.toEntity())
        }
        return enriched
    }

    private fun ReleaseDetails.mergeTorrentLinksFrom(other: ReleaseDetails): ReleaseDetails {
        var merged = this
        if (other.torrentLinks.isNotEmpty()) {
            merged = merged.copy(torrentLinks = other.torrentLinks)
        }
        if (other.playEpisodeId != null) {
            merged = merged.copy(playEpisodeId = other.playEpisodeId)
        }
        return merged
    }

    private fun ReleaseDetails.mergeFavoriteMetadataFrom(other: ReleaseDetails): ReleaseDetails =
        copy(
            seriesStatusRu = other.seriesStatusRu ?: seriesStatusRu,
            favoriteTargetId = other.favoriteTargetId ?: favoriteTargetId,
            favoriteTargetKind = other.favoriteTargetKind ?: favoriteTargetKind,
            isFavorite = other.isFavorite ?: isFavorite,
        )

    private suspend fun enrichWithSummaryEpisodeTitle(details: ReleaseDetails): ReleaseDetails {
        if (
            details.kind != ReleaseKind.SERIES ||
            (!details.episodeTitleRu.isNullOrBlank() && !details.episodeOverviewRu.isNullOrBlank())
        ) {
            return details
        }

        val summary = releaseDao.getSummary(details.detailsUrl) ?: return details

        return details.copy(
            episodeTitleRu = details.episodeTitleRu
                ?: summary.episodeTitleRu?.takeIf { it.isNotBlank() },
            episodeOverviewRu = details.episodeOverviewRu
                ?: summary.episodeOverviewRu?.takeIf { it.isNotBlank() },
            episodeOverviewSource = details.episodeOverviewSource
                ?: summary.episodeOverviewSource?.takeIf { it.isNotBlank() },
        )
    }

    private suspend fun refreshFavoriteMetadataIfNeeded(details: ReleaseDetails): ReleaseDetails {
        val needsFavoriteMetadata = hasAuthenticatedSession() &&
            (details.favoriteTargetId == null || details.isFavorite == null)
        val needsSeriesStatus = details.kind == ReleaseKind.SERIES && details.seriesStatusRu.isNullOrBlank()

        if (!needsFavoriteMetadata && !needsSeriesStatus) {
            return details
        }

        val metadataPageUrls = favoriteMetadataPageUrls(details.detailsUrl, details.kind)

        var enriched = details
        for (metadataPageUrl in metadataPageUrls) {
            val metadataHtml = try {
                httpClient.fetchDetails(metadataPageUrl)
            } catch (e: IOException) {
                Log.w(TAG, "Failed to fetch favorite metadata page", e)
                continue
            }
            enriched = enriched.withSeriesStatus(detailsParser.parseSeriesStatus(metadataHtml))
            if (needsFavoriteMetadata) {
                val favoriteMetadata = detailsParser.parseFavoriteMetadata(metadataHtml)
                if (favoriteMetadata != null) {
                    enriched = enriched.withFavoriteMetadata(favoriteMetadata)
                    break
                }
            } else {
                break
            }
        }
        if (enriched != details) {
            releaseDao.upsertDetails(enriched.toEntity())
        }
        return enriched
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
        } catch (e: IOException) {
            Log.w(TAG, "Failed to expand generic torrent links", e)
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

    private fun ReleaseDetails.withSeriesStatus(seriesStatusRu: String?): ReleaseDetails {
        return if (seriesStatusRu.isNullOrBlank()) {
            this
        } else {
            copy(seriesStatusRu = seriesStatusRu)
        }
    }

    private fun seriesRootUrl(detailsUrl: String): String? {
        val normalizedDetailsUrl = resolveUrl(detailsUrl).trimEnd('/')
        val match = seriesRootUrlRegex.matchEntire(normalizedDetailsUrl) ?: return null
        return "$BASE_URL/series/${match.groupValues[1]}/"
    }

    private fun fallbackSeriesTitle(seriesRootUrl: String): String {
        return seriesRootUrl
            .removePrefix("$BASE_URL/series/")
            .trim('/')
            .replace('_', ' ')
            .normalizeText()
    }

}

private fun String.normalizeSearchQuery(): String = normalizeText().replace(searchWhitespaceRegex, " ")

private fun String.isLostFilmImageUrl(): Boolean {
    val normalized = lowercase()
    return normalized.startsWith("$BASE_URL/static/") ||
        normalized.startsWith("https://static.lostfilm.") ||
        normalized.startsWith("http://static.lostfilm.")
}
