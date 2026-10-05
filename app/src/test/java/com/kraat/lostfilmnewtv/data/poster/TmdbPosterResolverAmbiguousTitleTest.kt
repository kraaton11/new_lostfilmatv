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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Сопоставление только по русскому названию — это догадка. Название вроде
 * «Надежда» есть у десятка фильмов, а год на карточке бывает пустым, и прежний
 * код брал «самую популярную» среди одноимённых: карточка невышедшего фильма
 * 2026 года получала постер и рейтинг 10.0 от фильма 1955 года с одним голосом.
 * Плюс поиск TMDB отдаёт по 20 записей, а одноимённых названий бывает 71, так
 * что нужный фильм может лежать за пределами первой страницы.
 */
@RunWith(RobolectricTestRunner::class)
class TmdbPosterResolverAmbiguousTitleTest {

    /** Реальная выдача TMDB на «Надежда»: пять фильмов с оригинальным названием «Надежда». */
    private val nadezhdaCandidates = listOf(
        TmdbSearchResult(id = 604401, name = "Hope", originalName = "Надежда", popularity = 3.1, releaseYear = 2019, rating = "0.0"),
        TmdbSearchResult(id = 880358, name = "Nadezhda", originalName = "Надежда", popularity = 4.2, releaseYear = 1955, rating = "10.0"),
        TmdbSearchResult(id = 535238, name = "Nadezhda", originalName = "Надежда", popularity = 2.0, releaseYear = 1973, rating = "7.0"),
        TmdbSearchResult(id = 524928, name = "Nadezhda", originalName = "Надежда", popularity = 1.5, releaseYear = 2002, rating = "0.0"),
        TmdbSearchResult(id = 1272001, name = "Hope", originalName = "Надежда", popularity = 1.1, releaseYear = 2011, rating = "0.0"),
    )

    /**
     * Корейский фильм «호프» 2026 года — именно он лежит на lostfilm как «Надежда».
     * В выдаче TMDB title = «Надежда» (это локализованное имя), а
     * original_title = «호프», поэтому сверка только по original_title его бы пропустила.
     */
    private val hope2026 = listOf(
        TmdbSearchResult(id = 1058424, name = "Надежда", originalName = "호프", popularity = 50.0, releaseYear = 2026, rating = "7.3"),
    )

    @Test
    fun resolve_refusesToGuess_whenTitleIsAmbiguousAndYearIsUnknown() = runTest {
        val fixture = fixture(titlePages = listOf(nadezhdaCandidates))

        val result = fixture.resolver.resolve(
            detailsUrl = HOPEU,
            titleRu = "Надежда",
            kind = ReleaseKind.MOVIE,
        )

        assertNull(
            "Пять фильмов с названием «Надежда» и без года — угадывать нельзя, лучше без картинки",
            result,
        )
        assertEquals("TMDB дергаться не должен", emptyList<Int>(), fixture.requestedIds)
    }

    @Test
    fun resolve_pagesThroughResults_whenTheMatchIsBeyondTheFirstTwenty() = runTest {
        val fixture = fixture(titlePages = listOf(nadezhdaCandidates, hope2026))

        fixture.resolver.resolve(
            detailsUrl = HOPEU,
            titleRu = "Надежда",
            originalReleaseYear = 2026,
            kind = ReleaseKind.MOVIE,
        )

        assertEquals(listOf(1058424), fixture.requestedIds)
    }

    @Test
    fun resolve_stopsPaging_whenProxyIgnoresPageParam() = runTest {
        // Прокси может не пропускать page и вечно отдавать первую страницу.
        val samePage = List(6) { nadezhdaCandidates }
        val fixture = fixture(titlePages = samePage)

        val result = fixture.resolver.resolve(
            detailsUrl = HOPEU,
            titleRu = "Надежда",
            originalReleaseYear = 2026,
            kind = ReleaseKind.MOVIE,
        )

        assertNull("Бесконечное листание одинаковых страниц — не результат", result)
        assertTrue("Страниц должно быть мало, а не ${fixture.pageRequests.size}", fixture.pageRequests.size <= 3)
    }

    @Test
    fun resolve_usesTheYearToDisambiguate_whenYearIsKnown() = runTest {
        val fixture = fixture(titlePages = listOf(nadezhdaCandidates))

        fixture.resolver.resolve(
            detailsUrl = HOPEU,
            titleRu = "Надежда",
            originalReleaseYear = 2019,
            kind = ReleaseKind.MOVIE,
        )

        assertEquals(listOf(604401), fixture.requestedIds)
    }

    @Test
    fun resolve_acceptsTitleOnlyMatch_whenOnlyOneFilmCarriesThatName() = runTest {
        val fixture = fixture(
            titlePages = listOf(
                listOf(
                    TmdbSearchResult(id = 111, name = "The Hope Factory", originalName = "Комбинат «Надежда»", popularity = 9.0, releaseYear = 2014),
                    TmdbSearchResult(id = 880358, name = "Nadezhda", originalName = "Надежда", popularity = 4.2, releaseYear = 1955, rating = "10.0"),
                ),
            ),
        )

        fixture.resolver.resolve(
            detailsUrl = HOPEU,
            titleRu = "Надежда",
            kind = ReleaseKind.MOVIE,
        )

        assertEquals(listOf(880358), fixture.requestedIds)
    }

    @Test
    fun resolve_acceptsTitleOnlyMatch_whenSearchReturnedASingleResult() = runTest {
        val fixture = fixture(
            titlePages = listOf(
                listOf(
                    TmdbSearchResult(id = 816, name = "Остин Пауэрс: Человек-загадка", originalName = "Austin Powers: International Man of Mystery", popularity = 12.0, releaseYear = 1997),
                ),
            ),
        )

        fixture.resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/movies/Austin_Powers_International_Man_ofMystery",
            titleRu = "Остин Пауэрс: Человек-загадка международного масштаба",
            kind = ReleaseKind.MOVIE,
        )

        assertEquals(listOf(816), fixture.requestedIds)
    }

    @Test
    fun resolve_stillTrustsTheSlug_whenManyFilmsShareTheTitle() = runTest {
        val fixture = fixture(
            slugResults = listOf(
                TmdbSearchResult(id = 745, name = "The Sixth Sense", originalName = "The Sixth Sense", popularity = 30.0, releaseYear = 1999),
            ),
            titlePages = listOf(nadezhdaCandidates),
        )

        fixture.resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/movies/The_Sixth_Sense",
            titleRu = "Шестое чувство",
            kind = ReleaseKind.MOVIE,
        )

        assertEquals(listOf(745), fixture.requestedIds)
    }

    @Test
    fun resolve_asksTmdbForRussianTitles_whenSearchingByRussianTitle() = runTest {
        // Без language=title в выдаче приходит английский: у корейского «хоф»
        // title = Hope, original_title = хоф, и русского «Надежда» в результатах
        // нет вовсе — сверять не с чем. По slug наоборот нужен оригинальный
        // язык, там идёт сравнение с английским slug.
        val fixture = fixture(titlePages = listOf(hope2026))

        fixture.resolver.resolve(
            detailsUrl = HOPEU,
            titleRu = "Надежда",
            originalReleaseYear = 2026,
            kind = ReleaseKind.MOVIE,
        )

        assertEquals("ru-RU", fixture.languages["Надежда"])
        assertNull("slug ищется в оригинальном языке", fixture.languages["Hopeu"])
    }

    private class Fixture(
        val resolver: TmdbPosterResolverImpl,
        val requestedIds: MutableList<Int>,
        val pageRequests: MutableList<Int>,
        val languages: MutableMap<String, String?> = mutableMapOf(),
    )

    private fun fixture(
        slugResults: List<TmdbSearchResult> = emptyList(),
        titlePages: List<List<TmdbSearchResult>>,
    ): Fixture {
        val requestedIds = mutableListOf<Int>()
        val pageRequests = mutableListOf<Int>()
        val languages = mutableMapOf<String, String?>()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            // Slug из URL — латиница, русское название — кириллица.
            override suspend fun searchByTitle(
                query: String,
                year: Int?,
                type: TmdbMediaType,
                page: Int,
                language: String?,
            ): List<TmdbSearchResult> {
                pageRequests += page
                languages[query] = language
                val pages = if (query.all { it.code < 128 }) listOf(slugResults) else titlePages
                return pages.getOrElse(page - 1) { emptyList() }
            }

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

    override suspend fun deleteSeasonMappingsUnder(seriesPrefix: String) = Unit
                    override suspend fun deleteAll() = Unit
                },
            ),
            requestedIds = requestedIds,
            pageRequests = pageRequests,
            languages = languages,
        )
    }

    private companion object {
        const val HOPEU = "https://www.lostfilm.today/movies/Hopeu"
    }
}
