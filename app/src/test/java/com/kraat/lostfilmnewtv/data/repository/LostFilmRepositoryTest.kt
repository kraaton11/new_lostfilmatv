package com.kraat.lostfilmnewtv.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kraat.lostfilmnewtv.data.db.LostFilmDatabase
import com.kraat.lostfilmnewtv.data.db.PageCacheMetadataEntity
import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.db.ReleaseDetailsEntity
import com.kraat.lostfilmnewtv.data.db.ReleaseSummaryEntity
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.network.LostFilmHttpClient
import com.kraat.lostfilmnewtv.data.parser.LostFilmDetailsParser
import com.kraat.lostfilmnewtv.data.parser.LostFilmListParser
import com.kraat.lostfilmnewtv.data.parser.fixture
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun loadDetails_enrichesSeriesWithTorrentLinksFromSelectionPage() = runTest {
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            detailsHandler = { requestedUrl ->
                when {
                    requestedUrl.contains("/series/9-1-1/season_9/episode_13/") -> fixture("series-details.html")
                    requestedUrl == "https://www.lostfilm.today/V/?c=1103&s=1&e=1&u=999999&h=fixturehash&n=1&newbie=&br=&ts=1773683822" -> {
                        fixture("torrent-quality-page.html")
                    }
                    else -> error("Unexpected details request: $requestedUrl")
                }
            },
            torrentHandler = { episodeId ->
                assertEquals("362009013", episodeId)
                fixture("torrent-redirect.html")
            },
        )

        val result = repository.loadDetails("/series/9-1-1/season_9/episode_13/") as DetailsResult.Success

        assertEquals(
            listOf("SD", "1080", "MP4"),
            result.details.torrentLinks.map { it.label },
        )
        assertEquals(
            listOf(
                "https://n.tracktor.site/td.php?s=fixture-sd",
                "https://n.tracktor.site/td.php?s=fixture-1080",
                "https://n.tracktor.site/td.php?s=fixture-mp4",
            ),
            result.details.torrentLinks.map { it.url },
        )
    }

    @Test
    fun loadDetails_returnsCachedTorrentLinksWithoutCollapsingQualityChoices() = runTest {
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            detailsHandler = { requestedUrl ->
                when {
                    requestedUrl.contains("/series/9-1-1/season_9/episode_13/") -> fixture("series-details.html")
                    requestedUrl == "https://www.lostfilm.today/V/?c=1103&s=1&e=1&u=999999&h=fixturehash&n=1&newbie=&br=&ts=1773683822" -> {
                        fixture("torrent-quality-page.html")
                    }
                    else -> error("Unexpected details request: $requestedUrl")
                }
            },
            torrentHandler = { fixture("torrent-redirect.html") },
        )

        repository.loadDetails("/series/9-1-1/season_9/episode_13/")
        val cached = repository.loadDetails("/series/9-1-1/season_9/episode_13/") as DetailsResult.Success

        assertEquals(listOf("SD", "1080", "MP4"), cached.details.torrentLinks.map { it.label })
        assertEquals(
            listOf(
                "https://n.tracktor.site/td.php?s=fixture-sd",
                "https://n.tracktor.site/td.php?s=fixture-1080",
                "https://n.tracktor.site/td.php?s=fixture-mp4",
            ),
            cached.details.torrentLinks.map { it.url },
        )
    }

    @Test
    fun loadDetails_refreshesFreshCachedTorrentLinksAfterLogin() = runTest {
        val detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"
        seedDetailsWithoutTorrentLinks(detailsUrl = detailsUrl, fetchedAt = NOW - 1_000L)
        var detailsRequests = 0
        var torrentRequests = 0
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            detailsHandler = { requestedUrl ->
                detailsRequests += 1
                when (requestedUrl) {
                    "https://www.lostfilm.today/V/?c=1103&s=1&e=1&u=999999&h=fixturehash&n=1&newbie=&br=&ts=1773683822" -> {
                        fixture("torrent-quality-page.html")
                    }
                    else -> error("Fresh cached details should not refetch HTML: $requestedUrl")
                }
            },
            torrentHandler = { episodeId ->
                torrentRequests += 1
                assertEquals("362009013", episodeId)
                fixture("torrent-redirect.html")
            },
            isAuthenticated = true,
        )

        val result = repository.loadDetails(detailsUrl) as DetailsResult.Success

        assertEquals(1, detailsRequests)
        assertEquals(1, torrentRequests)
        assertEquals(listOf("SD", "1080", "MP4"), result.details.torrentLinks.map { it.label })
        assertEquals(
            listOf(
                "https://n.tracktor.site/td.php?s=fixture-sd",
                "https://n.tracktor.site/td.php?s=fixture-1080",
                "https://n.tracktor.site/td.php?s=fixture-mp4",
            ),
            releaseDao.getReleaseDetails(detailsUrl)?.toModel()?.torrentLinks?.map { it.url },
        )
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

    private suspend fun seedDetailsWithoutTorrentLinks(detailsUrl: String, fetchedAt: Long) {
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
        isAuthenticated: Boolean = false,
    ): LostFilmRepository {
        return LostFilmRepositoryImpl(
            httpClient = FakeLostFilmHttpClient(pageHandler, detailsHandler, torrentHandler),
            releaseDao = releaseDao,
            listParser = LostFilmListParser(),
            detailsParser = LostFilmDetailsParser(),
            hasAuthenticatedSession = { isAuthenticated },
            clock = { NOW },
        )
    }
}

private class FakeLostFilmHttpClient(
    private val pageHandler: suspend (Int) -> String,
    private val detailsHandler: suspend (String) -> String,
    private val torrentHandler: suspend (String) -> String,
) : LostFilmHttpClient {
    override suspend fun fetchNewPage(pageNumber: Int): String = pageHandler(pageNumber)

    override suspend fun fetchDetails(detailsUrl: String): String {
        return detailsHandler(detailsUrl)
    }

    override suspend fun fetchTorrentRedirect(playEpisodeId: String): String = torrentHandler(playEpisodeId)
}
