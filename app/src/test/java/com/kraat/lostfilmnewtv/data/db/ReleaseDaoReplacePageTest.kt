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

/**
 * Постеры и описания набираются прогрессивным обогащением уже после того, как
 * страница разобрана и записана. Перезапись страницы их затирала, и карточки,
 * чьё обогащение успело записаться, оставались без картинки — в ленте это
 * выглядело как «загрузились только первые 15». Парсер страницы artwork не
 * приносит вовсе, поэтому перезапись обязана сохранять уже накопленное.
 */
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
    fun replacePage_keepsArtworkEnrichedBeforeRefetch() = runTest {
        dao.replacePage(pageNumber = 1, summaries = listOf(parsed("a"), parsed("b")), metadata = metadata(1))

        // Прогрессивное обогащение записало постер первому элементу.
        dao.updateSummaryArtwork(
            detailsUrl = "a",
            posterUrl = "https://image.tmdb.org/poster-a.jpg",
            backdropUrl = "https://image.tmdb.org/backdrop-a.jpg",
            episodeOverviewRu = "Описание серии",
            episodeOverviewSource = "TMDB_RU",
            seriesOverviewRu = null,
            movieOverviewRu = null,
            tmdbRating = "8.1",
        )

        // Лента перезагрузила страницу: парсер снова принёс её без artwork.
        dao.replacePage(pageNumber = 1, summaries = listOf(parsed("a"), parsed("b")), metadata = metadata(1))

        val stored = dao.getSummary("a")!!
        assertEquals("https://image.tmdb.org/poster-a.jpg", stored.posterUrl)
        assertEquals("https://image.tmdb.org/backdrop-a.jpg", stored.backdropUrl)
        assertEquals("Описание серии", stored.episodeOverviewRu)
        assertEquals("8.1", stored.tmdbRating)
    }

    @Test
    fun replacePage_keepsSeriesAndMovieArtwork() = runTest {
        dao.replacePage(pageNumber = 1, summaries = listOf(parsed("s")), metadata = metadata(1))
        dao.updateSummaryArtwork(
            detailsUrl = "s",
            posterUrl = "https://image.tmdb.org/poster-s.jpg",
            backdropUrl = null,
            episodeOverviewRu = null,
            episodeOverviewSource = null,
            seriesOverviewRu = "Описание сериала",
            movieOverviewRu = null,
            tmdbRating = null,
        )

        dao.replacePage(pageNumber = 1, summaries = listOf(parsed("s")), metadata = metadata(1))

        assertEquals("https://image.tmdb.org/poster-s.jpg", dao.getSummary("s")?.posterUrl)
        assertEquals("Описание сериала", dao.getSummary("s")?.seriesOverviewRu)
    }

    @Test
    fun replacePage_stillAppliesParsedFieldsAndWatchedState() = runTest {
        dao.replacePage(pageNumber = 1, summaries = listOf(parsed("a")), metadata = metadata(1))
        dao.updateSummaryArtwork(
            detailsUrl = "a",
            posterUrl = "https://image.tmdb.org/poster-a.jpg",
            backdropUrl = null,
            episodeOverviewRu = null,
            episodeOverviewSource = null,
            seriesOverviewRu = null,
            movieOverviewRu = null,
            tmdbRating = null,
        )

        val refreshed = parsed("a").copy(titleRu = "Новое название", releaseDateRu = "02.02.2026", isWatched = true)
        dao.replacePage(pageNumber = 1, summaries = listOf(refreshed), metadata = metadata(1))

        val stored = dao.getSummary("a")!!
        assertEquals("Новое название", stored.titleRu)
        assertEquals("02.02.2026", stored.releaseDateRu)
        assertEquals(true, stored.isWatched)
        assertEquals("https://image.tmdb.org/poster-a.jpg", stored.posterUrl)
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
