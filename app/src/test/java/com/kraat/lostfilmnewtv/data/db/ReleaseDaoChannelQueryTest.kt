package com.kraat.lostfilmnewtv.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReleaseDaoChannelQueryTest {
    private lateinit var database: LostFilmDatabase
    private lateinit var dao: ReleaseDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LostFilmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.releaseDao()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun getLatestSummariesForChannel_returnsItemsInHomeOrderWithLimit() = runTest {
        dao.upsertSummaries(
            listOf(
                summary(pageNumber = 1, positionInPage = 0, detailsUrl = "https://example.com/1"),
                summary(pageNumber = 1, positionInPage = 1, detailsUrl = "https://example.com/2"),
                summary(pageNumber = 2, positionInPage = 0, detailsUrl = "https://example.com/3"),
            ),
        )

        val results = dao.getLatestSummariesForChannel(limit = 2)

        assertEquals(listOf("https://example.com/1", "https://example.com/2"), results.map { it.detailsUrl })
    }

    @Test
    fun getLatestUnwatchedSummariesForChannel_excludesWatchedRows() = runTest {
        dao.upsertSummaries(
            listOf(
                summary(detailsUrl = "https://example.com/watched", isWatched = true),
                summary(detailsUrl = "https://example.com/fresh", isWatched = false),
            ),
        )

        val results = dao.getLatestUnwatchedSummariesForChannel(limit = 10)

        assertEquals(listOf("https://example.com/fresh"), results.map { it.detailsUrl })
    }
}

private fun summary(
    detailsUrl: String,
    pageNumber: Int = 1,
    positionInPage: Int = 0,
    isWatched: Boolean = false,
): ReleaseSummaryEntity {
    return ReleaseSummaryEntity(
        detailsUrl = detailsUrl,
        kind = ReleaseKind.SERIES.name,
        titleRu = "Title $detailsUrl",
        episodeTitleRu = "Episode",
        seasonNumber = 1,
        episodeNumber = 1,
        releaseDateRu = "21.03.2026",
        posterUrl = "https://example.com/poster.jpg",
        pageNumber = pageNumber,
        positionInPage = positionInPage,
        fetchedAt = 1L,
        isWatched = isWatched,
    )
}
