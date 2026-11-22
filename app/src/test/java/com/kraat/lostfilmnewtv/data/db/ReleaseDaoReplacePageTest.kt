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
class ReleaseDaoReplacePageTest {
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
        if (::database.isInitialized) database.close()
    }

    @Test
    fun replacePage_replacesLegacyArtworkAndAppliesWatchedState() = runTest {
        val cached = parsed("a").copy(
            posterUrl = "https://image.tmdb.org/poster.jpg",
            backdropUrl = "https://auth.bazuka.pp.ua/api/tmdb/t/p/w780/legacy.jpg",
            tmdbRating = "8.1",
        )
        dao.replacePage(pageNumber = 1, summaries = listOf(cached), metadata = metadata(1))

        val refreshed = parsed("a").copy(
            titleRu = "Новое название",
            releaseDateRu = "02.02.2026",
            isWatched = true,
            posterUrl = "/Static/Images/362/Posters/image_s9.jpg",
        )
        dao.replacePage(pageNumber = 1, summaries = listOf(refreshed), metadata = metadata(1))

        assertEquals(refreshed, dao.getSummary("a"))
    }

    @Test
    fun replacePage_dropsRowsThatLeftThePage() = runTest {
        dao.replacePage(pageNumber = 1, summaries = listOf(parsed("a"), parsed("b")), metadata = metadata(1))

        dao.replacePage(pageNumber = 1, summaries = listOf(parsed("a")), metadata = metadata(1))

        assertEquals(1, dao.getSummariesUpToPage(1).size)
    }

    private fun parsed(id: String) = ReleaseSummaryEntity(
        detailsUrl = id,
        kind = ReleaseKind.SERIES.name,
        titleRu = "Название $id",
        episodeTitleRu = null,
        seasonNumber = 1,
        episodeNumber = 1,
        releaseDateRu = "01.01.2026",
        posterUrl = "",
        pageNumber = 1,
        positionInPage = 0,
        fetchedAt = 1L,
        isWatched = false,
    )

    private fun metadata(page: Int) = PageCacheMetadataEntity(
        pageNumber = page,
        fetchedAt = 1L,
        itemCount = 1,
        hasNextPage = false,
    )
}
