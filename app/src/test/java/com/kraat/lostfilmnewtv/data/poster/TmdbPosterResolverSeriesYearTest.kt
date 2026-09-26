package com.kraat.lostfilmnewtv.data.poster

import com.kraat.lostfilmnewtv.data.db.TmdbPosterDao
import com.kraat.lostfilmnewtv.data.db.TmdbPosterMappingEntity
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.TmdbImageUrls
import com.kraat.lostfilmnewtv.data.model.TmdbMediaType
import com.kraat.lostfilmnewtv.data.model.TmdbSearchResult
import com.kraat.lostfilmnewtv.data.network.TmdbPosterClient
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Для сериалов год подборщик брал только из slug, а в ленте он лежит строкой
 * «Год выхода» рядом с названием. Из-за этого «Остров сокровищ» 2026 года
 * получал японский сериал 1978-го, а «К востоку от Эдема» — фильм 2008 года.
 *
 * Год эпизода при этом использовать нельзя: у долгоиграющего сериала в ленте
 * стоит год серии, а не год премьеры, и ограничение по нему отсекло бы верный
 * матч.
 */
@RunWith(RobolectricTestRunner::class)
class TmdbPosterResolverSeriesYearTest {

    @Test
    fun resolve_picksTheSeriesRailYear_whenSlugCarriesNoYear() = runTest {
        val fixture = fixture(
            slugResults = listOf(
                TmdbSearchResult(id = 23956, name = "Treasure Island", originalName = "トレジャーアイランド", popularity = 30.0, releaseYear = 1978),
                TmdbSearchResult(id = 555111, name = "Treasure Island", originalName = "Treasure Island", popularity = 5.0, releaseYear = 2026),
            ),
        )

        fixture.resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/Treasure_Island",
            titleRu = "Остров сокровищ",
            releaseDateRu = "Скоро",
            kind = ReleaseKind.SERIES,
            originalReleaseYear = 2026,
        )

        assertEquals(listOf(555111), fixture.requestedIds)
    }

    @Test
    fun resolve_refusesSeriesFromAnotherDecade_whenRailYearContradictsTmdb() = runTest {
        val fixture = fixture(
            slugResults = listOf(
                TmdbSearchResult(id = 23956, name = "Treasure Island", originalName = "トレジャーアイランド", popularity = 12.0, releaseYear = 1978),
            ),
        )

        val result = fixture.resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/Treasure_Island",
            titleRu = "Остров сокровищ",
            releaseDateRu = "Скоро",
            kind = ReleaseKind.SERIES,
            originalReleaseYear = 2026,
        )

        assertNull("Сериал 1978 года не может быть «Островом сокровищ» 2026-го", result)
    }

    @Test
    fun resolve_keepsLongRunningSeries_whenYearComesFromAnEpisode() = runTest {
        // Медленные кони: шестой сезон вышел в 2026-м, а сериал стартовал в 2022-м.
        // Год в ленте на строке эпизода — это год серии, не год премьеры.
        val fixture = fixture(
            slugResults = listOf(
                TmdbSearchResult(id = 95480, name = "Slow Horses", originalName = "Slow Horses", popularity = 40.0, releaseYear = 2022),
            ),
        )

        fixture.resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/Slow_Horses/season_6/episode_2/",
            titleRu = "Медленные кони",
            releaseDateRu = "26.09.2026",
            kind = ReleaseKind.SERIES,
            originalReleaseYear = 2026,
        )

        assertEquals(listOf(95480), fixture.requestedIds)
    }

    @Test
    fun resolve_stillUsesYearFromSlug_whenRailHasNoYear() = runTest {
        val fixture = fixture(
            slugResults = listOf(
                TmdbSearchResult(id = 111, name = "Old Show", originalName = "Old Show", popularity = 20.0, releaseYear = 2019),
                TmdbSearchResult(id = 222, name = "New Show", originalName = "New Show", popularity = 5.0, releaseYear = 2024),
            ),
        )

        fixture.resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/Some_Show_2024",
            titleRu = "Какой-то сериал",
            releaseDateRu = "01.01.2024",
            kind = ReleaseKind.SERIES,
        )

        assertEquals(listOf(222), fixture.requestedIds)
    }

    private class Fixture(val resolver: TmdbPosterResolverImpl, val requestedIds: MutableList<Int>)

    private fun fixture(slugResults: List<TmdbSearchResult>): Fixture {
        val requestedIds = mutableListOf<Int>()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(
                query: String,
                year: Int?,
                type: TmdbMediaType,
                page: Int,
                language: String?,
            ): List<TmdbSearchResult> = if (query.all { it.code < 128 }) slugResults else emptyList()

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls {
                requestedIds += tmdbId
                return TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/$tmdbId.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/original/$tmdbId.jpg",
                )
            }
        }
        return Fixture(
            resolver = TmdbPosterResolverImpl(
                tmdbClient = client,
                tmdbDao = object : TmdbPosterDao {
                    override suspend fun getByDetailsUrl(detailsUrl: String): TmdbPosterMappingEntity? = null
                    override suspend fun upsert(entity: TmdbPosterMappingEntity) = Unit
                    override suspend fun deleteExpired(threshold: Long) = Unit
                    override suspend fun deleteAll() = Unit
                },
            ),
            requestedIds = requestedIds,
        )
    }
}
