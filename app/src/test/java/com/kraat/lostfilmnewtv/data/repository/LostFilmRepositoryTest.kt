package com.kraat.lostfilmnewtv.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kraat.lostfilmnewtv.data.db.LostFilmDatabase
import com.kraat.lostfilmnewtv.data.db.PageCacheMetadataEntity
import com.kraat.lostfilmnewtv.data.db.ReleaseDao
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

    private fun createRepository(
        pageHandler: suspend (Int) -> String,
    ): LostFilmRepository {
        return LostFilmRepositoryImpl(
            httpClient = FakeLostFilmHttpClient(pageHandler),
            releaseDao = releaseDao,
            listParser = LostFilmListParser(),
            detailsParser = LostFilmDetailsParser(),
            clock = { NOW },
        )
    }
}

private class FakeLostFilmHttpClient(
    private val pageHandler: suspend (Int) -> String,
) : LostFilmHttpClient {
    override suspend fun fetchNewPage(pageNumber: Int): String = pageHandler(pageNumber)

    override suspend fun fetchDetails(detailsUrl: String): String {
        error("Details loading is not used in these tests")
    }
}
