package com.kraat.lostfilmnewtv.data.poster

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kraat.lostfilmnewtv.data.db.LostFilmDatabase
import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.db.ReleaseSummaryEntity
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.model.TmdbImageUrls
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val FIRST_URL = "https://www.lostfilm.tv/series/first/season_1/episode_1/"
private const val SECOND_URL = "https://www.lostfilm.tv/series/second/season_1/episode_1/"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TmdbEnrichmentServiceTest {
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
    fun enrichProgressively_emitsItemAndPersistsArtwork_whenResolverFindsMatch() = runTest {
        releaseDao.upsertSummaries(listOf(entity(FIRST_URL), entity(SECOND_URL)))
        val service = createService { detailsUrl ->
            TmdbImageUrls(
                posterUrl = "https://image.tmdb.org/t/p/w780/${detailsUrl.hashCode()}.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w1280/${detailsUrl.hashCode()}.jpg",
                rating = "7.7",
            )
        }

        val emitted = service.enrichProgressively(listOf(summary(FIRST_URL), summary(SECOND_URL))).toList()

        assertEquals(listOf(FIRST_URL, SECOND_URL), emitted.map { it.detailsUrl })
        assertTrue(emitted.all { it.posterUrl.isNotBlank() })
        assertEquals("7.7", emitted.first().tmdbRating)

        val stored = releaseDao.getSummary(FIRST_URL)
        requireNotNull(stored)
        assertEquals(emitted.first().posterUrl, stored.posterUrl)
        assertEquals(emitted.first().backdropUrl, stored.backdropUrl)
        assertEquals("7.7", stored.tmdbRating)
    }

    @Test
    fun enrichProgressively_emitsNothing_whenResolverReturnsNothing() = runTest {
        releaseDao.upsertSummaries(listOf(entity(FIRST_URL)))
        val service = createService { null }

        val emitted = service.enrichProgressively(listOf(summary(FIRST_URL))).toList()

        assertTrue("Пустой результат не должен приводить к эмиссии", emitted.isEmpty())
    }

    @Test
    fun enrichProgressively_skipsItemsThatAlreadyHaveCompleteArtwork() = runTest {
        val resolvedUrls = mutableListOf<String>()
        val service = createService { detailsUrl ->
            resolvedUrls += detailsUrl
            TmdbImageUrls(posterUrl = "p", backdropUrl = "b")
        }
        val complete = summary(FIRST_URL).copy(
            posterUrl = "https://image.tmdb.org/t/p/w780/old.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w1280/old.jpg",
        )

        val emitted = service.enrichProgressively(listOf(complete)).toList()

        assertTrue(emitted.isEmpty())
        assertTrue("Резолвер не должен вызываться для готовых карточек", resolvedUrls.isEmpty())
    }

    @Test
    fun enrichProgressively_emitsNothing_whenItemListIsEmpty() = runTest {
        val service = createService { error("резолвер не должен вызываться") }

        val emitted = service.enrichProgressively(emptyList()).toList()

        assertTrue(emitted.isEmpty())
    }

    private fun createService(resolve: (String) -> TmdbImageUrls?) = TmdbEnrichmentServiceImpl(
        tmdbResolver = object : TmdbPosterResolver {
            override suspend fun resolve(
                detailsUrl: String,
                titleRu: String,
                releaseDateRu: String,
                kind: ReleaseKind,
                originalReleaseYear: Int?,
            ): TmdbImageUrls? = resolve(detailsUrl)
        },
        releaseDao = releaseDao,
    )

    private fun summary(detailsUrl: String) = ReleaseSummary(
        id = detailsUrl,
        kind = ReleaseKind.SERIES,
        titleRu = "Казино",
        episodeTitleRu = null,
        seasonNumber = 1,
        episodeNumber = 1,
        releaseDateRu = "14 марта 2026",
        posterUrl = "",
        detailsUrl = detailsUrl,
        pageNumber = 1,
        positionInPage = 1,
        fetchedAt = 0L,
    )

    private fun entity(detailsUrl: String) = ReleaseSummaryEntity.fromModel(summary(detailsUrl))
}
