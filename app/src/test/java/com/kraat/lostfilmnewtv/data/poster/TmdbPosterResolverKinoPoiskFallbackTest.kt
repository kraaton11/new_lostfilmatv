package com.kraat.lostfilmnewtv.data.poster

import com.kraat.lostfilmnewtv.data.db.TmdbPosterDao
import com.kraat.lostfilmnewtv.data.db.TmdbPosterMappingEntity
import com.kraat.lostfilmnewtv.data.model.KinoPoiskFilmDetails
import com.kraat.lostfilmnewtv.data.model.KinoPoiskSearchResult
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.TmdbEpisodeOverview
import com.kraat.lostfilmnewtv.data.model.TmdbEpisodeOverviewSource
import com.kraat.lostfilmnewtv.data.model.TmdbImageUrls
import com.kraat.lostfilmnewtv.data.model.TmdbMediaType
import com.kraat.lostfilmnewtv.data.model.TmdbSearchResult
import com.kraat.lostfilmnewtv.data.network.KINOPOISK_FILM_TYPES
import com.kraat.lostfilmnewtv.data.network.KinoPoiskClient
import com.kraat.lostfilmnewtv.data.network.TmdbPosterClient
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Кинопоиск и TMDB используют разные пространства идентификаторов. Фолбэк на
 * Кинопоиск кладёт его filmId в колонку tmdbId, и любой последующий запрос
 * описания уходит в TMDB с чужим id — то есть в 404. Здесь регрессия
 * воспроизводится end-to-end: первый resolve падает в фолбэк и записывает
 * маппинг, второй resolve берёт маппинг из кеша и не должен дёргать TMDB.
 */
@RunWith(RobolectricTestRunner::class)
class TmdbPosterResolverKinoPoiskFallbackTest {
    private val kpFilmId = 8_042_172
    private val detailsUrl = "https://www.lostfilm.today/series/The_Lowdown/season_1/episode_2/"

    @Test
    fun resolve_doesNotRequestTmdbOverviews_whenCachedMappingCameFromKinoPoisk() = runTest {
        val dao = RecordingTmdbPosterDao()
        var overviewCalls = 0
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(
                query: String,
                year: Int?,
                type: TmdbMediaType,
            ): List<TmdbSearchResult> = emptyList()

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls? {
                error("TMDB не нашёл сериал, картинки сюда попасть не должны")
            }

            override suspend fun getEpisodeOverview(
                tmdbId: Int,
                seasonNumber: Int,
                episodeNumber: Int,
            ): TmdbEpisodeOverview? {
                overviewCalls++
                return TmdbEpisodeOverview("Описание серии из TMDB.", TmdbEpisodeOverviewSource.TMDB_RU)
            }

            override suspend fun getSeriesOverviewRu(tmdbId: Int): String? {
                overviewCalls++
                return "Описание сериала из TMDB."
            }

            override suspend fun getMovieOverviewRu(tmdbId: Int): String? {
                overviewCalls++
                return "Описание фильма из TMDB."
            }
        }
        val resolver = TmdbPosterResolverImpl(
            tmdbClient = client,
            tmdbDao = dao,
            kinoPoiskClient = kinoPoiskFallback(),
        )

        val first = resolver.resolve(
            detailsUrl = detailsUrl,
            titleRu = "Подноготная",
            releaseDateRu = "10.09.2026",
            kind = ReleaseKind.SERIES,
        )

        assertNotNull("Фолбэк на Кинопоиск должен отдать постер", first?.posterUrl)
        assertEquals(kpFilmId, dao.upserted?.tmdbId)
        assertEquals(0, overviewCalls)

        val second = resolver.resolve(
            detailsUrl = detailsUrl,
            titleRu = "Подноготная",
            releaseDateRu = "10.09.2026",
            kind = ReleaseKind.SERIES,
        )

        assertEquals(
            "Маппинг Кинопоиска нельзя использовать как tmdbId: описание пришло бы в 404",
            0,
            overviewCalls,
        )
        assertEquals(
            "Описание Кинопоиска должно сохраниться — терять его нельзя",
            "Описание из Кинопоиска.",
            second?.seriesOverviewRu,
        )
    }

    @Test
    fun resolve_stillRequestsOverviews_whenCachedMappingCameFromTmdb() = runTest {
        val dao = RecordingTmdbPosterDao()
        var overviewCalls = 0
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(
                query: String,
                year: Int?,
                type: TmdbMediaType,
            ): List<TmdbSearchResult> = emptyList()

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls? = null

            override suspend fun getSeriesOverviewRu(tmdbId: Int): String? {
                overviewCalls++
                return "Описание сериала из TMDB."
            }

            override suspend fun getEpisodeOverview(
                tmdbId: Int,
                seasonNumber: Int,
                episodeNumber: Int,
            ): TmdbEpisodeOverview? {
                overviewCalls++
                return TmdbEpisodeOverview("Описание серии из TMDB.", TmdbEpisodeOverviewSource.TMDB_RU)
            }
        }
        val tmdbMapping = TmdbPosterMappingEntity.create(
            detailsUrl = "/series/The_Lowdown/season_1/",
            tmdbId = 273_247,
            tmdbType = "TV",
            posterUrl = "https://image.tmdb.org/t/p/w780/poster.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w1280/backdrop.jpg",
            fetchedAt = System.currentTimeMillis(),
        )
        val resolver = TmdbPosterResolverImpl(
            tmdbClient = client,
            tmdbDao = FixedTmdbPosterDao(tmdbMapping),
            kinoPoiskClient = kinoPoiskFallback(),
        )

        val result = resolver.resolve(
            detailsUrl = detailsUrl,
            titleRu = "Подноготная",
            releaseDateRu = "10.09.2026",
            kind = ReleaseKind.SERIES,
        )

        assertTrue("Маппинг TMDB обязан отдавать описание", overviewCalls > 0)
        assertEquals("Описание сериала из TMDB.", result?.seriesOverviewRu)
    }

    @Test
    fun resolve_asksKinoPoiskForFilmsOnly_whenMatchingAMovie() = runTest {
        val requestedTypes = mutableListOf<Set<String>>()
        val resolver = TmdbPosterResolverImpl(
            tmdbClient = emptyTmdbPosterClient(),
            tmdbDao = FixedTmdbPosterDao(null),
            kinoPoiskClient = object : KinoPoiskClient(OkHttpClient(), "http://localhost") {
                override suspend fun searchByKeyword(
                    query: String,
                    acceptedTypes: Set<String>,
                    expectedYear: String?,
                    expectedNameEn: String?,
                ): KinoPoiskSearchResult {
                    requestedTypes += acceptedTypes
                    return KinoPoiskSearchResult(
                        filmId = 2544,
                        nameRu = "В поисках галактики",
                        nameEn = "Galaxy Quest",
                        type = "FILM",
                        year = "1999",
                        rating = "8.0",
                        posterUrl = "https://st.kp.yandex.net/images/film_big/2544.jpg",
                    )
                }
            },
        )

        resolver.resolve(
            detailsUrl = "/movies/В_поисках_галактики/",
            titleRu = "В поисках галактики",
            releaseDateRu = "01.01.2026",
            kind = ReleaseKind.MOVIE,
        )

        assertTrue(
            "Для фильма КП должен искать только среди фильмов, а не сериалов: $requestedTypes",
            requestedTypes.isNotEmpty() && requestedTypes.all { it == KINOPOISK_FILM_TYPES },
        )
    }

    private fun emptyTmdbPosterClient() = object : TmdbPosterClient(OkHttpClient(), "http://localhost") {
        override suspend fun searchByTitle(
            query: String,
            year: Int?,
            type: TmdbMediaType,
        ): List<TmdbSearchResult> = emptyList()

        override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls? = null

        override suspend fun getSeriesOverviewRu(tmdbId: Int): String? = null

        override suspend fun getEpisodeOverview(
            tmdbId: Int,
            seasonNumber: Int,
            episodeNumber: Int,
        ): TmdbEpisodeOverview? = null
    }

    private fun kinoPoiskFallback() = object : KinoPoiskClient(OkHttpClient(), "http://localhost") {
        override suspend fun searchByKeyword(
            query: String,
            acceptedTypes: Set<String>,
            expectedYear: String?,
            expectedNameEn: String?,
        ): KinoPoiskSearchResult = KinoPoiskSearchResult(
            filmId = kpFilmId,
            nameRu = "Подноготная",
            nameEn = "The Lowdown",
            type = "series",
            year = "2025",
            rating = "6.9",
            posterUrl = "https://st.kp.yandex.net/images/film_big/$kpFilmId.jpg",
        )

        override suspend fun getFilmDetails(filmId: Int): KinoPoiskFilmDetails = KinoPoiskFilmDetails(
            posterUrl = "https://st.kp.yandex.net/images/film_big/$kpFilmId.jpg",
            coverUrl = null,
            description = "Описание из Кинопоиска.",
            shortDescription = null,
            ratingKinopoisk = 6.9,
            ratingImdb = null,
        )
    }
}

private open class FixedTmdbPosterDao(
    private val cached: TmdbPosterMappingEntity? = null,
) : TmdbPosterDao {
    var upserted: TmdbPosterMappingEntity? = null
        protected set

    override suspend fun getByDetailsUrl(detailsUrl: String): TmdbPosterMappingEntity? = cached

    override suspend fun upsert(entity: TmdbPosterMappingEntity) {
        upserted = entity
    }

    override suspend fun deleteExpired(threshold: Long) = Unit

    override suspend fun deleteAll() = Unit
}

private class RecordingTmdbPosterDao : FixedTmdbPosterDao(cached = null) {
    val stored: TmdbPosterMappingEntity?
        get() = upserted
}
