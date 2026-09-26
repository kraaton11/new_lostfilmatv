package com.kraat.lostfilmnewtv.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val DETAILS_URL = "https://www.lostfilm.tv/series/casino/season_1/episode_1/"
private const val SEED_FETCHED_AT = 1_773_576_000_000L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReleaseDaoArtworkUpdateTest {
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
    fun updateSummaryArtwork_writesAllArtworkColumns() = runTest {
        releaseDao.upsertSummaries(listOf(seedEntity()))

        val rowsUpdated = releaseDao.updateSummaryArtwork(
            detailsUrl = DETAILS_URL,
            posterUrl = "https://image.tmdb.org/t/p/w780/poster.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w1280/backdrop.jpg",
            episodeOverviewRu = "Эпизод идёт",
            episodeOverviewSource = "TMDB_RU",
            seriesOverviewRu = "Обзор сериала",
            movieOverviewRu = null,
            tmdbRating = "8.4",
        )

        assertEquals(1, rowsUpdated)
        val stored = releaseDao.getSummary(DETAILS_URL)
        requireNotNull(stored)
        assertEquals("https://image.tmdb.org/t/p/w780/poster.jpg", stored.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w1280/backdrop.jpg", stored.backdropUrl)
        assertEquals("Эпизод идёт", stored.episodeOverviewRu)
        assertEquals("TMDB_RU", stored.episodeOverviewSource)
        assertEquals("Обзор сериала", stored.seriesOverviewRu)
        assertNull(stored.movieOverviewRu)
        assertEquals("8.4", stored.tmdbRating)
    }

    @Test
    fun updateSummaryArtwork_keepsWatchedFlagAndOrderingColumns() = runTest {
        releaseDao.upsertSummaries(listOf(seedEntity(isWatched = true)))

        releaseDao.updateSummaryArtwork(
            detailsUrl = DETAILS_URL,
            posterUrl = "https://image.tmdb.org/t/p/w780/poster.jpg",
            backdropUrl = null,
            episodeOverviewRu = null,
            episodeOverviewSource = null,
            seriesOverviewRu = null,
            movieOverviewRu = null,
            tmdbRating = null,
        )

        val stored = releaseDao.getSummary(DETAILS_URL)
        requireNotNull(stored)
        assertTrue("isWatched не должен сбрасываться точечным UPDATE", stored.isWatched)
        assertEquals(3, stored.positionInPage)
        assertEquals(2, stored.pageNumber)
        assertEquals(SEED_FETCHED_AT, stored.fetchedAt)
        assertEquals("Казино", stored.titleRu)
    }

    @Test
    fun updateSummaryArtwork_returnsZero_whenDetailsUrlIsUnknown() = runTest {
        releaseDao.upsertSummaries(listOf(seedEntity()))

        val rowsUpdated = releaseDao.updateSummaryArtwork(
            detailsUrl = "https://www.lostfilm.tv/series/unknown/",
            posterUrl = "https://image.tmdb.org/t/p/w780/poster.jpg",
            backdropUrl = null,
            episodeOverviewRu = null,
            episodeOverviewSource = null,
            seriesOverviewRu = null,
            movieOverviewRu = null,
            tmdbRating = null,
        )

        assertEquals(0, rowsUpdated)
    }

    private fun seedEntity(isWatched: Boolean = false) = ReleaseSummaryEntity(
        detailsUrl = DETAILS_URL,
        kind = "SERIES",
        titleRu = "Казино",
        episodeTitleRu = null,
        seasonNumber = 1,
        episodeNumber = 1,
        releaseDateRu = "14 марта 2026",
        posterUrl = "",
        pageNumber = 2,
        positionInPage = 3,
        fetchedAt = SEED_FETCHED_AT,
        isWatched = isWatched,
    )
}
