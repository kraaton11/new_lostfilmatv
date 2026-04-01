package com.kraat.lostfilmnewtv.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kraat.lostfilmnewtv.data.db.LostFilmDatabase
import com.kraat.lostfilmnewtv.data.db.PageCacheMetadataEntity
import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.db.ReleaseSummaryEntity
import com.kraat.lostfilmnewtv.data.db.ReleaseDetailsEntity
import com.kraat.lostfilmnewtv.data.db.TmdbPosterDao
import com.kraat.lostfilmnewtv.data.model.FavoriteMutationResult
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.FavoriteTargetKind
import com.kraat.lostfilmnewtv.data.model.FavoriteToggleNetworkResult
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.SeriesGuide
import com.kraat.lostfilmnewtv.data.network.LostFilmHttpClient
import com.kraat.lostfilmnewtv.data.parser.LostFilmDetailsParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmFavoriteSeriesParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmListParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmSeasonEpisodesParser
import com.kraat.lostfilmnewtv.data.parser.fixture
import com.kraat.lostfilmnewtv.data.poster.TmdbPosterResolver
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val NOW = 1_773_576_000_000L
private const val SIX_HOURS_MS = 6 * 60 * 60 * 1000L
private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LostFilmRepositoryTest {
    private lateinit var database: LostFilmDatabase
    private lateinit var releaseDao: ReleaseDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LostFilmDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        releaseDao = database.releaseDao()
    }

    @After
    fun tearDown() {
        if (this::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun staleCacheWithinRetention_isReturnedWithStaleFlag() = runTest {
        seedPage(pageNumber = 1, fetchedAt = NOW - SIX_HOURS_MS - 2_000L)
        val repository = createRepository(
            pageHandler = { throw IOException("offline") },
        )

        val result = repository.loadPage(1)

        assertTrue(result is PageState.Content)
        result as PageState.Content
        assertTrue(result.isStale)
        assertEquals("9-1-1", result.items.first().titleRu)
    }

    @Test
    fun expiredCacheOlderThanSevenDays_isDeletedAndNotReturned() = runTest {
        seedPage(pageNumber = 1, fetchedAt = NOW - SEVEN_DAYS_MS - 2_000L)
        val repository = createRepository(
            pageHandler = { throw IOException("offline") },
        )

        val result = repository.loadPage(1)

        assertTrue(result is PageState.Error)
        assertTrue(releaseDao.getPageSummaries(1).isEmpty())
        assertTrue(releaseDao.getPageMetadata(1) == null)
    }

    @Test
    fun loadsNextPage_andAppendsWithoutLosingExistingItems() = runTest {
        val repository = createRepository(
            pageHandler = { pageNumber ->
                when (pageNumber) {
                    1 -> fixture("new-page-1.html")
                    2 -> fixture("new-page-2.html")
                    else -> error("Unexpected page: $pageNumber")
                }
            },
        )

        val firstPage = repository.loadPage(1) as PageState.Content
        val secondPage = repository.loadPage(2) as PageState.Content

        assertTrue(secondPage.items.size > firstPage.items.size)
        assertEquals(firstPage.items.first().detailsUrl, secondPage.items.first().detailsUrl)
        assertTrue(secondPage.items.any { it.pageNumber == 2 })
        assertEquals(secondPage.items.map { it.detailsUrl }.distinct().size, secondPage.items.size)
    }

    @Test
    fun loadPage_withoutAuthenticatedSession_preservesExistingWatchedStateForMatchingItems() = runTest {
        val watchedDetailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"
        releaseDao.upsertSummaries(
            listOf(
                ReleaseSummaryEntity(
                    detailsUrl = watchedDetailsUrl,
                    kind = "SERIES",
                    titleRu = "9-1-1",
                    episodeTitleRu = "Маменькин сынок",
                    seasonNumber = 9,
                    episodeNumber = 13,
                    releaseDateRu = "14.03.2026",
                    posterUrl = "https://www.lostfilm.today/Static/Images/362/Posters/image_s9.jpg",
                    pageNumber = 1,
                    positionInPage = 0,
                    fetchedAt = NOW - 10_000L,
                    isWatched = true,
                ),
            ),
        )
        releaseDao.upsertPageMetadata(
            PageCacheMetadataEntity(
                pageNumber = 1,
                fetchedAt = NOW - 10_000L,
                itemCount = 1,
            ),
        )
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            isAuthenticated = false,
        )

        val result = repository.loadPage(1) as PageState.Content

        assertTrue(result.items.first { it.detailsUrl == watchedDetailsUrl }.isWatched)
        assertTrue(releaseDao.getSummary(watchedDetailsUrl)?.isWatched == true)
    }

    @Test
    fun loadDetails_enrichesSeriesWithTorrentLinkFromRedirectPage() = runTest {
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            detailsHandler = { fixture("series-details.html") },
            torrentHandler = { episodeId ->
                assertEquals("362009013", episodeId)
                fixture("torrent-redirect.html")
            },
            torrentPageHandler = { throw IOException("options page unavailable") },
        )

        val result = repository.loadDetails("/series/9-1-1/season_9/episode_13/") as DetailsResult.Success

        assertNotNull(result.details.torrentLinks.singleOrNull())
        assertEquals("Вариант 1", result.details.torrentLinks.single().label)
        assertEquals(
            "https://www.lostfilm.today/V/?c=1103&s=1&e=1&u=999999&h=fixturehash&n=1&newbie=&br=&ts=1773683822",
            result.details.torrentLinks.single().url,
        )
    }

    @Test
    fun loadDetails_enrichesSeriesWithMultipleTorrentQualities() = runTest {
        seedPage(pageNumber = 1, fetchedAt = NOW - 1_000L)
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            detailsHandler = { fixture("series-details.html") },
            torrentHandler = { fixture("torrent-redirect.html") },
            torrentPageHandler = { fixture("torrent-options-page.html") },
        )

        val result = repository.loadDetails("/series/9-1-1/season_9/episode_13/") as DetailsResult.Success

        assertEquals("Маменькин сынок", result.details.episodeTitleRu)
        assertEquals(listOf("SD", "1080p", "720p"), result.details.torrentLinks.map { it.label })
        assertEquals(3, result.details.torrentLinks.size)
    }

    @Test
    fun loadDetails_refreshesFreshCachedDetailsWithTorrentLinkAfterLogin() = runTest {
        val detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"
        val favoritePageUrl = "https://www.lostfilm.today/series/9-1-1"
        seedDetails(detailsUrl = detailsUrl, fetchedAt = NOW - 1_000L)
        val detailsRequests = mutableListOf<String>()
        var torrentRequests = 0
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            detailsHandler = { requestedUrl ->
                detailsRequests += requestedUrl
                when (requestedUrl) {
                    favoritePageUrl -> seriesFavoritePageHtml(isFavorite = false, includeSessionToken = false)
                    else -> error("Fresh cached details should not refetch episode HTML: $requestedUrl")
                }
            },
            torrentHandler = { episodeId ->
                torrentRequests += 1
                assertEquals("362009013", episodeId)
                fixture("torrent-redirect.html")
            },
            torrentPageHandler = { fixture("torrent-options-page.html") },
            isAuthenticated = true,
        )

        val result = repository.loadDetails(detailsUrl) as DetailsResult.Success

        assertEquals(listOf(favoritePageUrl), detailsRequests)
        assertEquals(1, torrentRequests)
        assertEquals(listOf("SD", "1080p", "720p"), result.details.torrentLinks.map { it.label })
        assertEquals(listOf("SD", "1080p", "720p"), releaseDao.getReleaseDetails(detailsUrl)?.toModel()?.torrentLinks?.map { it.label })
    }

    @Test
    fun loadDetails_refreshesFreshCachedDetailsWithFavoriteMetadataAfterLogin() = runTest {
        val detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"
        val favoritePageUrl = "https://www.lostfilm.today/series/9-1-1"
        seedDetails(detailsUrl = detailsUrl, fetchedAt = NOW - 1_000L)
        val detailsRequests = mutableListOf<String>()
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            detailsHandler = { requestedUrl ->
                detailsRequests += requestedUrl
                when (requestedUrl) {
                    favoritePageUrl -> seriesFavoritePageHtml(isFavorite = false, includeSessionToken = false)
                    else -> error("Fresh cached details should not refetch episode HTML: $requestedUrl")
                }
            },
            torrentHandler = { fixture("torrent-redirect.html") },
            torrentPageHandler = { fixture("torrent-options-page.html") },
            isAuthenticated = true,
        )

        val result = repository.loadDetails(detailsUrl) as DetailsResult.Success

        assertEquals(listOf(favoritePageUrl), detailsRequests)
        assertEquals(915, result.details.favoriteTargetId)
        assertEquals(true, releaseDao.getReleaseDetails(detailsUrl)?.toModel()?.favoriteTargetId == 915)
        assertEquals(false, result.details.isFavorite)
    }

    @Test
    fun loadSeriesGuide_normalizesEpisodeUrl_fetchesSeriesRootAndSeasons_andMergesWatchedIds() = runTest {
        val detailsRequests = mutableListOf<String>()
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            detailsHandler = { requestedUrl ->
                detailsRequests += requestedUrl
                when (requestedUrl) {
                    "https://www.lostfilm.today/series/Ted/" -> fixture("series-guide-ted-seasons.html")
                    "https://www.lostfilm.today/series/Ted/seasons" -> fixture("series-guide-ted-seasons.html")
                    else -> error("Unexpected details request: $requestedUrl")
                }
            },
            watchedEpisodeMarksHandler = { serialId ->
                assertEquals("810", serialId)
                """["810002007"]"""
            },
            isAuthenticated = true,
        )

        val result = repository.loadSeriesGuide("https://www.lostfilm.today/series/Ted/season_2/episode_8/")

        assertTrue(result is SeriesGuideResult.Success)
        result as SeriesGuideResult.Success
        assertEquals(
            listOf(
                "https://www.lostfilm.today/series/Ted/",
                "https://www.lostfilm.today/series/Ted/seasons",
            ),
            detailsRequests,
        )
        assertEquals(expectedTedGuide(), result.guide)
    }

    @Test
    fun loadSeriesGuide_returnsError_whenSeasonsPageFails() = runTest {
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            detailsHandler = { requestedUrl ->
                when (requestedUrl) {
                    "https://www.lostfilm.today/series/Ted/" -> fixture("series-guide-ted-seasons.html")
                    "https://www.lostfilm.today/series/Ted/seasons" -> throw IOException("offline")
                    else -> error("Unexpected details request: $requestedUrl")
                }
            },
            isAuthenticated = true,
        )

        val result = repository.loadSeriesGuide("https://www.lostfilm.today/series/Ted/season_2/episode_8/")

        assertEquals(SeriesGuideResult.Error("offline"), result)
    }

    @Test
    fun loadDetails_refreshesFreshCachedDetailsWhenFavoriteStateIsUnknown() = runTest {
        val detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"
        val favoritePageUrl = "https://www.lostfilm.today/series/9-1-1"
        seedDetails(detailsUrl = detailsUrl, fetchedAt = NOW - 1_000L)
        val cachedDetails = checkNotNull(releaseDao.getReleaseDetails(detailsUrl))
        releaseDao.upsertDetails(
            cachedDetails.copy(
                favoriteTargetId = 915,
                favoriteTargetKind = FavoriteTargetKind.SERIES.name,
                isFavorite = null,
            ),
        )
        val detailsRequests = mutableListOf<String>()
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            detailsHandler = { requestedUrl ->
                detailsRequests += requestedUrl
                when (requestedUrl) {
                    favoritePageUrl -> seriesFavoritePageHtml(isFavorite = false, includeSessionToken = false)
                    else -> error("Fresh cached details should not refetch episode HTML: $requestedUrl")
                }
            },
            torrentHandler = { fixture("torrent-redirect.html") },
            torrentPageHandler = { fixture("torrent-options-page.html") },
            isAuthenticated = true,
        )

        val result = repository.loadDetails(detailsUrl) as DetailsResult.Success

        assertEquals(listOf(favoritePageUrl), detailsRequests)
        assertEquals(915, result.details.favoriteTargetId)
        assertEquals(false, result.details.isFavorite)
    }

    @Test
    fun loadDetails_forSeriesWithoutEpisodeFavoriteMetadata_enrichesFromSeriesRootPage() = runTest {
        val detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"
        val favoritePageUrl = "https://www.lostfilm.today/series/9-1-1"
        val detailsRequests = mutableListOf<String>()
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            detailsHandler = { requestedUrl ->
                detailsRequests += requestedUrl
                when (requestedUrl) {
                    detailsUrl -> fixture("series-details.html")
                    favoritePageUrl -> seriesFavoritePageHtml(isFavorite = false, includeSessionToken = false)
                    else -> error("Unexpected details request: $requestedUrl")
                }
            },
            torrentHandler = { fixture("torrent-redirect.html") },
            torrentPageHandler = { fixture("torrent-options-page.html") },
            isAuthenticated = true,
        )

        val result = repository.loadDetails(detailsUrl) as DetailsResult.Success

        assertEquals(listOf(detailsUrl, favoritePageUrl), detailsRequests)
        assertEquals(915, result.details.favoriteTargetId)
        assertEquals(false, result.details.isFavorite)
    }

    @Test
    fun loadDetails_refreshesGenericCachedVariantIntoMultipleQualities() = runTest {
        val detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"
        val parsed = LostFilmDetailsParser().parseSeries(
            html = fixture("series-details.html"),
            detailsUrl = detailsUrl,
            fetchedAt = NOW - 1_000L,
        ).copy(
            torrentLinks = listOf(
                com.kraat.lostfilmnewtv.data.model.TorrentLink(
                    label = "Вариант 1",
                    url = "https://www.lostfilm.today/V/?c=1103&s=1&e=1&u=999999&h=fixturehash&n=1&newbie=&br=&ts=1773683822",
                ),
            ),
        )
        releaseDao.upsertDetails(ReleaseDetailsEntity.fromModel(parsed))

        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            detailsHandler = { error("Fresh cached details should not refetch HTML") },
            torrentHandler = { error("Existing V link should be expanded directly") },
            torrentPageHandler = { fixture("torrent-options-page.html") },
            isAuthenticated = false,
        )

        val result = repository.loadDetails(detailsUrl) as DetailsResult.Success

        assertEquals(listOf("SD", "1080p", "720p"), result.details.torrentLinks.map { it.label })
    }

    @Test
    fun markEpisodeWatched_updatesCachedSummaryWhenRemoteMarkSucceeds() = runTest {
        val detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"
        seedPage(pageNumber = 1, fetchedAt = NOW - 1_000L)
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            detailsHandler = {
                fixture("series-details.html").replace(
                    oldValue = "let UserData = {};",
                    newValue = """
                        let UserData = {};
                        UserData.id = 42;
                        UserData.session = 'ajax-session-token';
                    """.trimIndent(),
                )
            },
            markEpisodeHandler = { markedDetailsUrl, playEpisodeId, ajaxSessionToken ->
                assertEquals(detailsUrl, markedDetailsUrl)
                assertEquals("362009013", playEpisodeId)
                assertEquals("ajax-session-token", ajaxSessionToken)
                true
            },
            isAuthenticated = true,
        )

        val marked = repository.markEpisodeWatched(
            detailsUrl = detailsUrl,
            playEpisodeId = "362009013",
        )

        assertTrue(marked)
        assertTrue(releaseDao.getSummary(detailsUrl)?.isWatched == true)
    }

    @Test
    fun markEpisodeWatched_treatsRefetchedWatchedPageAsSuccessWhenAjaxResponseIsEmpty() = runTest {
        val detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"
        seedPage(pageNumber = 1, fetchedAt = NOW - 1_000L)
        var detailsRequestCount = 0
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            detailsHandler = {
                detailsRequestCount += 1
                when (detailsRequestCount) {
                    1 -> fixture("series-details.html").replace(
                        oldValue = "let UserData = {};",
                        newValue = """
                            let UserData = {};
                            UserData.id = 42;
                            UserData.session = 'ajax-session-token';
                        """.trimIndent(),
                    )

                    2 -> fixture("series-details.html")
                        .replace(
                            oldValue = """<div class="isawthat-btn " title="Пометить серию как просмотренную"""",
                            newValue = """<div class="isawthat-btn checked" title="Серия просмотрена"""",
                        )
                        .replace(
                            oldValue = "Серия не просмотрена</div>",
                            newValue = "Серия просмотрена</div>",
                        )

                    else -> error("Unexpected details request #$detailsRequestCount")
                }
            },
            markEpisodeHandler = { _, _, _ -> false },
            isAuthenticated = true,
        )

        val marked = repository.markEpisodeWatched(
            detailsUrl = detailsUrl,
            playEpisodeId = "362009013",
        )

        assertTrue(marked)
        assertTrue(releaseDao.getSummary(detailsUrl)?.isWatched == true)
    }

    @Test
    fun setFavorite_returnsNoOp_whenCurrentStateAlreadyMatchesTarget() = runTest {
        val detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"
        var toggleCalls = 0
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            detailsHandler = { seriesDetailsWithFavoriteState(isFavorite = true, includeSessionToken = true) },
            favoriteToggleHandler = { _, _, _ ->
                toggleCalls += 1
                FavoriteToggleNetworkResult.ToggledOn
            },
            isAuthenticated = true,
        )

        val result = repository.setFavorite(detailsUrl = detailsUrl, targetFavorite = true)

        assertEquals(FavoriteMutationResult.NoOp, result)
        assertEquals(0, toggleCalls)
    }

    @Test
    fun setFavorite_updatesCachedDetailsWhenRemoteToggleSucceeds() = runTest {
        val detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"
        val favoritePageUrl = "https://www.lostfilm.today/series/9-1-1"
        seedDetails(detailsUrl = detailsUrl, fetchedAt = NOW - 1_000L)
        var detailsRequestCount = 0
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            detailsHandler = { requestedUrl ->
                assertEquals(favoritePageUrl, requestedUrl)
                detailsRequestCount += 1
                when (detailsRequestCount) {
                    1 -> seriesFavoritePageHtml(isFavorite = false, includeSessionToken = true)
                    2 -> seriesFavoritePageHtml(isFavorite = true, includeSessionToken = true)
                    else -> error("Unexpected details request #$detailsRequestCount")
                }
            },
            favoriteToggleHandler = { refererUrl, favoriteTargetId, ajaxSessionToken ->
                assertEquals(favoritePageUrl, refererUrl)
                assertEquals(915, favoriteTargetId)
                assertEquals("ajax-session-token", ajaxSessionToken)
                FavoriteToggleNetworkResult.ToggledOn
            },
            isAuthenticated = true,
        )

        val result = repository.setFavorite(detailsUrl = detailsUrl, targetFavorite = true)

        assertEquals(FavoriteMutationResult.Updated, result)
        assertTrue(releaseDao.getReleaseDetails(detailsUrl)?.toModel()?.isFavorite == true)
    }

    @Test
    fun setFavorite_forSeries_readsAndTogglesAgainstSeriesRootPage() = runTest {
        val detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"
        val favoritePageUrl = "https://www.lostfilm.today/series/9-1-1"
        seedDetails(detailsUrl = detailsUrl, fetchedAt = NOW - 1_000L)
        var detailsRequestCount = 0
        val detailsRequests = mutableListOf<String>()
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            detailsHandler = { requestedUrl ->
                detailsRequests += requestedUrl
                assertEquals(favoritePageUrl, requestedUrl)
                detailsRequestCount += 1
                when (detailsRequestCount) {
                    1 -> seriesFavoritePageHtml(isFavorite = false, includeSessionToken = true)
                    2 -> seriesFavoritePageHtml(isFavorite = true, includeSessionToken = true)
                    else -> error("Unexpected details request #$detailsRequestCount")
                }
            },
            favoriteToggleHandler = { refererUrl, favoriteTargetId, ajaxSessionToken ->
                assertEquals(favoritePageUrl, refererUrl)
                assertEquals(915, favoriteTargetId)
                assertEquals("ajax-session-token", ajaxSessionToken)
                FavoriteToggleNetworkResult.ToggledOn
            },
            isAuthenticated = true,
        )

        val result = repository.setFavorite(detailsUrl = detailsUrl, targetFavorite = true)

        assertEquals(FavoriteMutationResult.Updated, result)
        assertEquals(listOf(favoritePageUrl, favoritePageUrl), detailsRequests)
        assertTrue(releaseDao.getReleaseDetails(detailsUrl)?.toModel()?.isFavorite == true)
        assertTrue(releaseDao.getReleaseDetails(detailsUrl)?.toModel()?.favoriteTargetId == 915)
    }

    @Test
    fun setFavorite_returnsRequiresLogin_whenSessionMissing() = runTest {
        val detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            isAuthenticated = false,
        )

        val result = repository.setFavorite(detailsUrl = detailsUrl, targetFavorite = true)

        assertEquals(FavoriteMutationResult.RequiresLogin(), result)
    }

    @Test
    fun loadFavoriteReleases_returnsEmptySuccess_whenFavoriteSeriesPageHasNoActiveFavorites() = runTest {
        val accountRequests = mutableListOf<String>()
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            accountPageHandler = { path ->
                accountRequests += path
                when (path) {
                    "/my/type_1" -> """
                        <html>
                            <body>
                                <div class="serials-list-box">
                                    <div class="serial-box">
                                        <div class="subscribe-box" title="Добавить сериал в избранное" id="fav_1"></div>
                                        <a class="body" href="/series/example_show">
                                            <div class="title-ru">Пример шоу</div>
                                        </a>
                                    </div>
                                </div>
                            </body>
                        </html>
                    """.trimIndent()
                    else -> error("Unexpected account path $path")
                }
            },
            isAuthenticated = true,
        )

        val result = repository.loadFavoriteReleases()

        assertTrue(result is FavoriteReleasesResult.Success)
        result as FavoriteReleasesResult.Success
        assertTrue(result.items.isEmpty())
        assertEquals(listOf("/my/type_1"), accountRequests)
    }

    @Test
    fun loadFavoriteReleases_buildsFeedFromFavoriteSeriesSeasons_insteadOfGenericMyNewMoviesBlock() = runTest {
        val accountRequests = mutableListOf<String>()
        val detailsRequests = mutableListOf<String>()
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            accountPageHandler = { path ->
                accountRequests += path
                when (path) {
                    "/my/type_1" -> favoriteSeriesAccountPageHtml()
                    else -> error("Unexpected account path $path")
                }
            },
            detailsHandler = { requestedUrl ->
                detailsRequests += requestedUrl
                when (requestedUrl) {
                    "https://www.lostfilm.today/series/alpha" -> "<html><body></body></html>"

                    "https://www.lostfilm.today/series/alpha/seasons" -> favoriteSeriesSeasonsPageHtml(
                        seriesSlug = "alpha",
                        serialId = "1001",
                        episodes = listOf(
                            SeasonEpisodeRow(
                                seasonNumber = 1,
                                episodeNumber = 2,
                                episodeTitle = "Alpha Episode",
                                releaseDateRu = "15.03.2026",
                                episodeId = "1001001002",
                            ),
                        ),
                    )

                    "https://www.lostfilm.today/series/beta" -> "<html><body></body></html>"

                    "https://www.lostfilm.today/series/beta/seasons" -> favoriteSeriesSeasonsPageHtml(
                        seriesSlug = "beta",
                        serialId = "2003",
                        episodes = listOf(
                            SeasonEpisodeRow(
                                seasonNumber = 3,
                                episodeNumber = 1,
                                episodeTitle = "Beta Episode",
                                releaseDateRu = "14.03.2026",
                                episodeId = "2003003001",
                            ),
                        ),
                    )

                    else -> error("Unexpected details request: $requestedUrl")
                }
            },
            isAuthenticated = true,
        )

        val result = repository.loadFavoriteReleases()

        assertTrue(result is FavoriteReleasesResult.Success)
        result as FavoriteReleasesResult.Success
        assertEquals(listOf("/my/type_1"), accountRequests)
        assertEquals(
            listOf(
                "https://www.lostfilm.today/series/alpha",
                "https://www.lostfilm.today/series/alpha/seasons",
                "https://www.lostfilm.today/series/beta",
                "https://www.lostfilm.today/series/beta/seasons",
            ),
            detailsRequests,
        )
        assertEquals(
            listOf(
                "https://www.lostfilm.today/series/alpha/season_1/episode_2/",
                "https://www.lostfilm.today/series/beta/season_3/episode_1/",
            ),
            result.items.map { it.detailsUrl },
        )
        assertEquals(listOf("Alpha", "Beta"), result.items.map { it.titleRu })
    }

    @Test
    fun loadFavoriteReleases_ignoresEpisodesWithFutureReleaseDate() = runTest {
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            accountPageHandler = { path ->
                when (path) {
                    "/my/type_1" -> favoriteSeriesAccountPageHtml()
                    else -> error("Unexpected account path $path")
                }
            },
            detailsHandler = { requestedUrl ->
                when (requestedUrl) {
                    "https://www.lostfilm.today/series/alpha" -> "<html><body></body></html>"

                    "https://www.lostfilm.today/series/alpha/seasons" -> favoriteSeriesSeasonsPageHtml(
                        seriesSlug = "alpha",
                        serialId = "1001",
                        episodes = listOf(
                            SeasonEpisodeRow(
                                seasonNumber = 1,
                                episodeNumber = 3,
                                episodeTitle = "Future Episode",
                                releaseDateRu = "16.03.2026",
                                episodeId = "1001001003",
                            ),
                            SeasonEpisodeRow(
                                seasonNumber = 1,
                                episodeNumber = 2,
                                episodeTitle = "Released Episode",
                                releaseDateRu = "15.03.2026",
                                episodeId = "1001001002",
                            ),
                        ),
                    )

                    "https://www.lostfilm.today/series/beta" -> "<html><body></body></html>"

                    "https://www.lostfilm.today/series/beta/seasons" -> favoriteSeriesSeasonsPageHtml(
                        seriesSlug = "beta",
                        serialId = "2003",
                        episodes = listOf(
                            SeasonEpisodeRow(
                                seasonNumber = 3,
                                episodeNumber = 1,
                                episodeTitle = "Beta Episode",
                                releaseDateRu = "14.03.2026",
                                episodeId = "2003003001",
                            ),
                        ),
                    )

                    else -> error("Unexpected details request: $requestedUrl")
                }
            },
            isAuthenticated = true,
        )

        val result = repository.loadFavoriteReleases()

        assertTrue(result is FavoriteReleasesResult.Success)
        result as FavoriteReleasesResult.Success
        assertEquals(
            listOf(
                "https://www.lostfilm.today/series/alpha/season_1/episode_2/",
                "https://www.lostfilm.today/series/beta/season_3/episode_1/",
            ),
            result.items.map { it.detailsUrl },
        )
    }

    @Test
    fun loadFavoriteReleases_marksWatchedEpisodesFromSeasonMarksResponse() = runTest {
        val watchedMarksRequests = mutableListOf<String>()
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            accountPageHandler = { path ->
                when (path) {
                    "/my/type_1" -> favoriteSeriesAccountPageHtml()
                    else -> error("Unexpected account path $path")
                }
            },
            detailsHandler = { requestedUrl ->
                when (requestedUrl) {
                    "https://www.lostfilm.today/series/alpha" -> "<html><body></body></html>"

                    "https://www.lostfilm.today/series/alpha/seasons" -> favoriteSeriesSeasonsPageHtml(
                        seriesSlug = "alpha",
                        serialId = "1072",
                        episodes = listOf(
                            SeasonEpisodeRow(
                                seasonNumber = 1,
                                episodeNumber = 2,
                                episodeTitle = "Alpha Episode",
                                releaseDateRu = "15.03.2026",
                                episodeId = "1072001002",
                            ),
                            SeasonEpisodeRow(
                                seasonNumber = 1,
                                episodeNumber = 1,
                                episodeTitle = "Pilot",
                                releaseDateRu = "14.03.2026",
                                episodeId = "1072001001",
                            ),
                        ),
                    )

                    "https://www.lostfilm.today/series/beta" -> "<html><body></body></html>"

                    "https://www.lostfilm.today/series/beta/seasons" -> favoriteSeriesSeasonsPageHtml(
                        seriesSlug = "beta",
                        serialId = "2088",
                        episodes = listOf(
                            SeasonEpisodeRow(
                                seasonNumber = 3,
                                episodeNumber = 1,
                                episodeTitle = "Beta Episode",
                                releaseDateRu = "13.03.2026",
                                episodeId = "2088003001",
                            ),
                        ),
                    )

                    else -> error("Unexpected details request: $requestedUrl")
                }
            },
            watchedEpisodeMarksHandler = { serialId ->
                watchedMarksRequests += serialId
                when (serialId) {
                    "1072" -> """["1072001002"]"""
                    "2088" -> "[]"
                    else -> error("Unexpected serial id $serialId")
                }
            },
            isAuthenticated = true,
        )

        val result = repository.loadFavoriteReleases()

        assertTrue(result is FavoriteReleasesResult.Success)
        result as FavoriteReleasesResult.Success
        assertEquals(listOf("1072", "2088"), watchedMarksRequests)
        assertTrue(result.items.first { it.detailsUrl.endsWith("/season_1/episode_2/") }.isWatched)
        assertFalse(result.items.first { it.detailsUrl.endsWith("/season_1/episode_1/") }.isWatched)
        assertFalse(result.items.first { it.detailsUrl.endsWith("/season_3/episode_1/") }.isWatched)
    }

    @Test
    fun loadFavoriteReleases_marksWatchedEpisodesFromSeriesRootGuide_whenSeasonMarksAreEmpty() = runTest {
        val detailsRequests = mutableListOf<String>()
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            accountPageHandler = { path ->
                when (path) {
                    "/my/type_1" -> """
                        <html>
                            <body>
                                <div class="serials-list-box">
                                    <div class="serial-box">
                                        <img src="/Static/Images/1/Posters/icon.jpg" class="avatar" />
                                        <div class="subscribe-box active" id="fav_1"></div>
                                        <a class="body" href="/series/marshals">
                                            <div class="title-ru">Маршалы</div>
                                        </a>
                                    </div>
                                </div>
                            </body>
                        </html>
                    """.trimIndent()

                    else -> error("Unexpected account path $path")
                }
            },
            detailsHandler = { requestedUrl ->
                detailsRequests += requestedUrl
                when (requestedUrl) {
                    "https://www.lostfilm.today/series/marshals" -> favoriteSeriesRootPageHtml(
                        seriesSlug = "marshals",
                        watchedEpisodeIds = setOf("1072001003", "1072001002", "1072001001"),
                    )

                    "https://www.lostfilm.today/series/marshals/seasons" -> favoriteSeriesSeasonsPageHtml(
                        seriesSlug = "marshals",
                        serialId = "1072",
                        episodes = listOf(
                            SeasonEpisodeRow(
                                seasonNumber = 1,
                                episodeNumber = 4,
                                episodeTitle = "The Gathering Storm",
                                releaseDateRu = "15.03.2026",
                                episodeId = "1072001004",
                            ),
                            SeasonEpisodeRow(
                                seasonNumber = 1,
                                episodeNumber = 3,
                                episodeTitle = "Road to Nowhere",
                                releaseDateRu = "14.03.2026",
                                episodeId = "1072001003",
                            ),
                            SeasonEpisodeRow(
                                seasonNumber = 1,
                                episodeNumber = 2,
                                episodeTitle = "Zone of Death",
                                releaseDateRu = "10.03.2026",
                                episodeId = "1072001002",
                            ),
                            SeasonEpisodeRow(
                                seasonNumber = 1,
                                episodeNumber = 1,
                                episodeTitle = "Riya Wicioni",
                                releaseDateRu = "04.03.2026",
                                episodeId = "1072001001",
                            ),
                        ),
                    )

                    else -> error("Unexpected details request: $requestedUrl")
                }
            },
            watchedEpisodeMarksHandler = { "[]" },
            isAuthenticated = true,
        )

        val result = repository.loadFavoriteReleases()

        assertTrue(result is FavoriteReleasesResult.Success)
        result as FavoriteReleasesResult.Success
        assertEquals(
            listOf(
                "https://www.lostfilm.today/series/marshals",
                "https://www.lostfilm.today/series/marshals/seasons",
            ),
            detailsRequests,
        )
        assertTrue(result.items.first { it.detailsUrl.endsWith("/season_1/episode_3/") }.isWatched)
        assertTrue(result.items.first { it.detailsUrl.endsWith("/season_1/episode_2/") }.isWatched)
        assertTrue(result.items.first { it.detailsUrl.endsWith("/season_1/episode_1/") }.isWatched)
        assertFalse(result.items.first { it.detailsUrl.endsWith("/season_1/episode_4/") }.isWatched)
    }

    @Test
    fun loadFavoriteReleases_returnsUnavailable_whenFavoriteSeriesPageFails() = runTest {
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            accountPageHandler = { throw IOException("offline") },
            isAuthenticated = true,
        )

        val result = repository.loadFavoriteReleases()

        assertTrue(result is FavoriteReleasesResult.Unavailable)
    }

    private suspend fun seedPage(pageNumber: Int, fetchedAt: Long) {
        val parsed = LostFilmListParser().parse(
            html = fixture("new-page-1.html"),
            pageNumber = pageNumber,
            fetchedAt = fetchedAt,
        )

        releaseDao.replacePage(
            pageNumber = pageNumber,
            summaries = parsed.map(ReleaseSummaryEntity::fromModel),
            metadata = PageCacheMetadataEntity(
                pageNumber = pageNumber,
                fetchedAt = fetchedAt,
                itemCount = parsed.size,
            ),
        )
    }

    private suspend fun seedDetails(detailsUrl: String, fetchedAt: Long) {
        val parsed = LostFilmDetailsParser().parseSeries(
            html = fixture("series-details.html"),
            detailsUrl = detailsUrl,
            fetchedAt = fetchedAt,
        )

        releaseDao.upsertDetails(ReleaseDetailsEntity.fromModel(parsed))
    }

    private fun createRepository(
        pageHandler: suspend (Int) -> String,
        detailsHandler: suspend (String) -> String = { error("Unexpected details request: $it") },
        torrentHandler: suspend (String) -> String = { error("Unexpected torrent request: $it") },
        torrentPageHandler: suspend (String) -> String = { error("Unexpected torrent page request: $it") },
        markEpisodeHandler: suspend (String, String, String) -> Boolean = { _, _, _ ->
            error("Unexpected mark episode request")
        },
        watchedEpisodeMarksHandler: suspend (String) -> String = { "[]" },
        favoriteToggleHandler: suspend (String, Int, String) -> FavoriteToggleNetworkResult = { _, _, _ ->
            error("Unexpected favorite toggle request")
        },
        accountPageHandler: suspend (String) -> String = { error("Unexpected account page request: $it") },
        isAuthenticated: Boolean = false,
    ): LostFilmRepository {
        return LostFilmRepositoryImpl(
            httpClient = FakeLostFilmHttpClient(
                pageHandler = pageHandler,
                detailsHandler = detailsHandler,
                torrentHandler = torrentHandler,
                torrentPageHandler = torrentPageHandler,
                markEpisodeHandler = markEpisodeHandler,
                watchedEpisodeMarksHandler = watchedEpisodeMarksHandler,
                favoriteToggleHandler = favoriteToggleHandler,
                accountPageHandler = accountPageHandler,
            ),
            releaseDao = releaseDao,
            listParser = LostFilmListParser(),
            detailsParser = LostFilmDetailsParser(),
            favoriteSeriesParser = LostFilmFavoriteSeriesParser(),
            seasonEpisodesParser = LostFilmSeasonEpisodesParser(),
            hasAuthenticatedSession = { isAuthenticated },
            tmdbResolver = createFakeTmdbResolver(),
            clock = { NOW },
        )
    }
}

private class FakeLostFilmHttpClient(
    private val pageHandler: suspend (Int) -> String,
    private val detailsHandler: suspend (String) -> String,
    private val torrentHandler: suspend (String) -> String,
    private val torrentPageHandler: suspend (String) -> String,
    private val markEpisodeHandler: suspend (String, String, String) -> Boolean,
    private val watchedEpisodeMarksHandler: suspend (String) -> String,
    private val favoriteToggleHandler: suspend (String, Int, String) -> FavoriteToggleNetworkResult,
    private val accountPageHandler: suspend (String) -> String,
) : LostFilmHttpClient {
    override suspend fun fetchNewPage(pageNumber: Int): String = pageHandler(pageNumber)

    override suspend fun fetchDetails(detailsUrl: String): String {
        return detailsHandler(detailsUrl)
    }

    override suspend fun fetchSeasonWatchedEpisodeMarks(refererUrl: String, serialId: String): String {
        return watchedEpisodeMarksHandler(serialId)
    }

    override suspend fun fetchTorrentRedirect(playEpisodeId: String): String = torrentHandler(playEpisodeId)

    override suspend fun fetchTorrentPage(url: String): String = torrentPageHandler(url)

    override suspend fun markEpisodeWatched(
        detailsUrl: String,
        playEpisodeId: String,
        ajaxSessionToken: String,
    ): Boolean {
        return markEpisodeHandler(detailsUrl, playEpisodeId, ajaxSessionToken)
    }

    override suspend fun toggleFavorite(
        refererUrl: String,
        favoriteTargetId: Int,
        ajaxSessionToken: String,
    ): FavoriteToggleNetworkResult {
        return favoriteToggleHandler(refererUrl, favoriteTargetId, ajaxSessionToken)
    }

    override suspend fun fetchAccountPage(path: String): String = accountPageHandler(path)
}

private fun seriesDetailsWithFavoriteState(
    isFavorite: Boolean,
    includeSessionToken: Boolean,
): String {
    val favoriteBlock = if (isFavorite) {
        """
        <div class="favorites-btn" title="Сериал в избранном" onClick="FollowSerial(915, false)">
            <div class="icon"></div>убрать из избранного
        </div>
        """.trimIndent()
    } else {
        """
        <div class="favorites-btn2" title="Добавить сериал в избранное" onClick="FollowSerial(915, false)">
            <div class="icon"></div>добавить в избранное
        </div>
        """.trimIndent()
    }
    val withFavoriteButton = fixture("series-details.html").replace(
        oldValue = """<div class="breadcrumbs-pane">""",
        newValue = "$favoriteBlock\n\t\t<div class=\"breadcrumbs-pane\">",
    )

    if (!includeSessionToken) {
        return withFavoriteButton
    }

    return withFavoriteButton.replace(
        oldValue = "let UserData = {};",
        newValue = """
            let UserData = {};
            UserData.id = 42;
            UserData.session = 'ajax-session-token';
        """.trimIndent(),
    )
}

private fun seriesFavoritePageHtml(
    isFavorite: Boolean,
    includeSessionToken: Boolean,
): String {
    val favoriteBlock = if (isFavorite) {
        """
        <div class="favorites-btn" title="Сериал в избранном" onClick="FollowSerial(915, false)">
            <div class="icon"></div>убрать из избранного
        </div>
        """.trimIndent()
    } else {
        """
        <div class="favorites-btn2" title="Добавить сериал в избранное" onClick="FollowSerial(915, false)">
            <div class="icon"></div>добавить в избранное
        </div>
        """.trimIndent()
    }

    val userData = if (includeSessionToken) {
        """
        <script>
            let UserData = {};
            UserData.id = 42;
            UserData.session = 'ajax-session-token';
        </script>
        """.trimIndent()
    } else {
        "<script>let UserData = {};</script>"
    }

    return """
        <html>
            <body>
                $favoriteBlock
                $userData
            </body>
        </html>
    """.trimIndent()
}

private fun expectedTedGuide(): SeriesGuide = SeriesGuide(
    seriesTitleRu = "Третий лишний",
    posterUrl = "https://www.lostfilm.today/Static/Images/810/Posters/image.jpg",
    selectedEpisodeDetailsUrl = "https://www.lostfilm.today/series/Ted/season_2/episode_8/",
    seasons = listOf(
        com.kraat.lostfilmnewtv.data.model.SeriesGuideSeason(
            seasonNumber = 2,
            episodes = listOf(
                com.kraat.lostfilmnewtv.data.model.SeriesGuideEpisode(
                    detailsUrl = "https://www.lostfilm.today/series/Ted/season_2/episode_8/",
                    episodeId = "810002008",
                    seasonNumber = 2,
                    episodeNumber = 8,
                    episodeTitleRu = "Левые новости",
                    releaseDateRu = "24.03.2026",
                    isWatched = false,
                ),
                com.kraat.lostfilmnewtv.data.model.SeriesGuideEpisode(
                    detailsUrl = "https://www.lostfilm.today/series/Ted/season_2/episode_7/",
                    episodeId = "810002007",
                    seasonNumber = 2,
                    episodeNumber = 7,
                    episodeTitleRu = "Сьюзен мотает срок",
                    releaseDateRu = "21.03.2026",
                    isWatched = true,
                ),
            ),
        ),
        com.kraat.lostfilmnewtv.data.model.SeriesGuideSeason(
            seasonNumber = 1,
            episodes = listOf(
                com.kraat.lostfilmnewtv.data.model.SeriesGuideEpisode(
                    detailsUrl = "https://www.lostfilm.today/series/Ted/season_1/episode_3/",
                    episodeId = "810001003",
                    seasonNumber = 1,
                    episodeNumber = 3,
                    episodeTitleRu = "Эяктильная дисфункция",
                    releaseDateRu = "27.01.2024",
                    isWatched = true,
                ),
                com.kraat.lostfilmnewtv.data.model.SeriesGuideEpisode(
                    detailsUrl = "https://www.lostfilm.today/series/Ted/season_1/episode_1/",
                    episodeId = "810001001",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    episodeTitleRu = "Просто скажи «да»",
                    releaseDateRu = "15.01.2024",
                    isWatched = false,
                ),
            ),
        ),
    ),
)

private fun redirectWrapperHtml(): String = """
    <html>
        <head>
            <script type="text/javascript">
                top.location.replace("/");
            </script>
        </head>
        <body></body>
    </html>
""".trimIndent()

private fun partialFavoriteFeedHtml(): String = """
    <html>
        <body>
            <a class="new-movie" href="/series/example_show/season_4/episode_7/" title="Пример шоу">
                <div class="title">4 сезон 7 серия</div>
            </a>
        </body>
    </html>
""".trimIndent()

private fun favoriteSeriesAccountPageHtml(): String = """
    <html>
        <body>
            <div class="serials-list-box">
                <div class="serial-box">
                    <img src="/Static/Images/1/Posters/icon.jpg" class="avatar" />
                    <div class="subscribe-box active" title="Сериал добавлен в избранное. Удалить из избранного?" id="fav_1"></div>
                    <a class="body" href="/series/alpha">
                        <div class="title-ru">Alpha</div>
                    </a>
                </div>
                <div class="serial-box">
                    <img src="/Static/Images/2/Posters/icon.jpg" class="avatar" />
                    <div class="subscribe-box active" title="Сериал добавлен в избранное. Удалить из избранного?" id="fav_2"></div>
                    <a class="body" href="/series/beta">
                        <div class="title-ru">Beta</div>
                    </a>
                </div>
                <div class="serial-box">
                    <img src="/Static/Images/3/Posters/icon.jpg" class="avatar" />
                    <div class="subscribe-box" title="Добавить сериал в избранное" id="fav_3"></div>
                    <a class="body" href="/series/outsider">
                        <div class="title-ru">Outsider</div>
                    </a>
                </div>
            </div>
            <div class="new_episodes">
                <a class="new-movie" href="/series/outsider/season_9/episode_9/" title="Outsider">
                    <div class="date">17.03.2026</div>
                    <img src="/Static/Images/3/Posters/image_s9.jpg" />
                </a>
            </div>
        </body>
    </html>
""".trimIndent()

private fun favoriteSeriesSeasonsPageHtml(
    seriesSlug: String,
    serialId: String,
    episodes: List<SeasonEpisodeRow>,
): String {
    val rows = episodes.joinToString(separator = "\n") { episode ->
        """
        <tr>
            <td class="alpha">
                <div class="haveseen-btn" data-episode="${episode.episodeId}"></div>
            </td>
            <td class="beta" onClick="goTo('/series/$seriesSlug/season_${episode.seasonNumber}/episode_${episode.episodeNumber}/',false)">
                ${episode.seasonNumber} сезон ${episode.episodeNumber} серия
            </td>
            <td class="gamma" onClick="goTo('/series/$seriesSlug/season_${episode.seasonNumber}/episode_${episode.episodeNumber}/',false)">
                ${episode.episodeTitle}
            </td>
            <td class="delta">
                Ru: ${episode.releaseDateRu}<br />
            </td>
        </tr>
        """.trimIndent()
    }

    return """
        <html>
            <head>
                <script>
                    var serial_id = '$serialId';
                </script>
            </head>
            <body>
                <table class="movie-details-block">
                    $rows
                </table>
            </body>
        </html>
    """.trimIndent()
}

private fun favoriteSeriesRootPageHtml(
    seriesSlug: String,
    watchedEpisodeIds: Set<String>,
): String {
    val rows = listOf(
        Triple("1072001004", "1 сезон 4 серия", false),
        Triple("1072001003", "1 сезон 3 серия", true),
        Triple("1072001002", "1 сезон 2 серия", true),
        Triple("1072001001", "1 сезон 1 серия", true),
    ).joinToString(separator = "\n") { (episodeId, label, available) ->
        val checkedClass = if (watchedEpisodeIds.contains(episodeId)) " checked" else ""
        val title = when {
            !available -> "Пометить серию как просмотренную можно только после выхода серии"
            watchedEpisodeIds.contains(episodeId) -> "Серия просмотрена"
            else -> "Пометить серию как просмотренную"
        }
        val rowClass = if (available) "" else """ class="not-available""""
        """
        <tr$rowClass>
            <td class="alpha">
                <div class="haveseen-btn$checkedClass" title="$title" data-episode="$episodeId"></div>
            </td>
            <td class="beta" onClick="goTo('/series/$seriesSlug/season_1/episode_${episodeId.takeLast(1)}/',false)">$label</td>
        </tr>
        """.trimIndent()
    }

    return """
        <html>
            <body>
                <table class="movie-parts-list">
                    $rows
                </table>
            </body>
        </html>
    """.trimIndent()
}

private data class SeasonEpisodeRow(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitle: String,
    val releaseDateRu: String,
    val episodeId: String,
)

private fun createFakeTmdbResolver(): TmdbPosterResolver {
    val fakeDao = object : TmdbPosterDao {
        override suspend fun getByDetailsUrl(detailsUrl: String) = null
        override suspend fun upsert(entity: com.kraat.lostfilmnewtv.data.db.TmdbPosterMappingEntity) {}
        override suspend fun deleteExpired(threshold: Long) {}
    }
    val fakeClient = FakeTmdbPosterClient()
    return TmdbPosterResolver(fakeClient, fakeDao)
}

private class FakeTmdbPosterClient : com.kraat.lostfilmnewtv.data.network.TmdbPosterClient(
    okHttpClient = okhttp3.OkHttpClient.Builder().build(),
    apiKey = "fake",
) {
    override suspend fun searchByTitle(query: String, year: Int?, type: com.kraat.lostfilmnewtv.data.model.TmdbMediaType) =
        emptyList<com.kraat.lostfilmnewtv.data.model.TmdbSearchResult>()

    override suspend fun getPosterAndBackdrop(tmdbId: Int, type: com.kraat.lostfilmnewtv.data.model.TmdbMediaType) =
        null
}
