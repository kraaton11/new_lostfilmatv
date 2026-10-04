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

    @Test
    fun resolve_usesRailYear_onSeasonCard_whenSeasonOutlivedThePremiere() = runTest {
        // «Братья»: год премьеры 2026, в ленте сезон от 2026-го. Одноимённых
        // сериалов в TMDB несколько, и без года появлялся более популярный,
        // но чужой. На карточке сезона год в ленте — год выхода сезона, и
        // ограничение по нему уместно, в отличие от строки эпизода.
        val fixture = fixture(
            slugResults = listOf(
                TmdbSearchResult(id = 66515, name = "Brothers", originalName = "Ang Probinsyano", popularity = 63.5, releaseYear = 2015),
                TmdbSearchResult(id = 250203, name = "Brothers", originalName = "Brothers", popularity = 41.1, releaseYear = 2026),
            ),
        )

        fixture.resolver.resolve(
            detailsUrl = "https://www.lostfilm.one/series/Brothers/season_1/",
            titleRu = "\u0411\u0440\u0430\u0442\u044c\u044f",
            releaseDateRu = "22.09.2026",
            kind = ReleaseKind.SERIES,
            originalReleaseYear = 2026,
        )

        assertEquals(listOf(250203), fixture.requestedIds)
    }

    @Test
    fun resolve_reusesSeriesMatch_forEpisode_soSeasonKeepsTheRightPoster() = runTest {
        // Эпизод пишется под ключом сезона. Его собственный поиск без года
        // выбирал однофамильца и перезаписывал сезонный маппинг, поэтому
        // карточка сезона показывала постер чужого сериала.
        val dao = StoringTmdbPosterDao()
        val searchQueries = mutableListOf<String>()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                searchQueries += query
                return listOf(
                    TmdbSearchResult(id = 250203, name = "Brothers", originalName = "Brothers", popularity = 41.1, releaseYear = 2026),
                    TmdbSearchResult(id = 66515, name = "Brothers", originalName = "Ang Probinsyano", popularity = 63.5, releaseYear = 2015),
                )
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls =
                TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/$tmdbId.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/original/$tmdbId-backdrop.jpg",
                )

            override suspend fun getSeasonImages(tmdbId: Int, seasonNumber: Int): TmdbImageUrls? = null
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        resolver.resolve(
            detailsUrl = "https://www.lostfilm.one/series/Brothers/",
            titleRu = "\u0411\u0440\u0430\u0442\u044c\u044f",
            releaseDateRu = "22.09.2026",
            kind = ReleaseKind.SERIES,
            originalReleaseYear = 2026,
        )
        val episode = resolver.resolve(
            detailsUrl = "https://www.lostfilm.one/series/Brothers/season_1/episode_3/",
            titleRu = "\u0411\u0440\u0430\u0442\u044c\u044f",
            releaseDateRu = "22.09.2026",
            kind = ReleaseKind.SERIES,
            originalReleaseYear = 2026,
        )

        assertEquals("https://image.tmdb.org/t/p/w780/250203.jpg", episode?.posterUrl)
        assertEquals(250203, dao.rows["https://www.lostfilm.one/series/Brothers/season_1/"]?.tmdbId)
        // Второй раз искать незачем: id сериала уже известен.
        assertEquals(1, searchQueries.size)
    }

    @Test
    fun resolve_dropsStaleSeasonMapping_whenEpisodeGotThereFirst() = runTest {
        // Порядок обогащения не гарантирован: эпизод может прийти раньше
        // сериала и записать под сезонным ключом однофамильца. Карточка
        // сериала обязана отбросить такую запись, чтобы сезон пересоздался
        // с верным постером.
        val dao = StoringTmdbPosterDao()
        val seasonKey = "https://www.lostfilm.one/series/Brothers/season_1/"
        dao.rows[seasonKey] = TmdbPosterMappingEntity.create(
            detailsUrl = seasonKey,
            tmdbId = 66515,
            tmdbType = "TV",
            posterUrl = "https://image.tmdb.org/t/p/w780/stale.jpg",
            backdropUrl = "",
        )
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> =
                listOf(
                    TmdbSearchResult(id = 250203, name = "Brothers", originalName = "Brothers", popularity = 41.1, releaseYear = 2026),
                    TmdbSearchResult(id = 66515, name = "Brothers", originalName = "Ang Probinsyano", popularity = 63.5, releaseYear = 2015),
                )

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls =
                TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/$tmdbId.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/original/$tmdbId-backdrop.jpg",
                )

            override suspend fun getSeasonImages(tmdbId: Int, seasonNumber: Int): TmdbImageUrls? = null
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        resolver.resolve(
            detailsUrl = "https://www.lostfilm.one/series/Brothers/",
            titleRu = "\u0411\u0440\u0430\u0442\u044c\u044f",
            releaseDateRu = "22.09.2026",
            kind = ReleaseKind.SERIES,
            originalReleaseYear = 2026,
        )

        assertEquals(250203, dao.rows["https://www.lostfilm.one/series/Brothers/"]?.tmdbId)
        assertNull("сезонный маппинг с однофамильцем должен быть отброшен", dao.rows[seasonKey])
    }

    @Test
    fun resolve_ignoresSeasonMapping_fromDb_whenSeriesIdDiffers() = runTest {
        // Сезонный маппинг в базе остался от прежнего запуска, когда сериал ещё
        // не был известен. Пока id расходится с корневым, кеш нельзя отдавать:
        // иначе постер чужого сериала держится до переустановки приложения.
        val dao = StoringTmdbPosterDao()
        dao.rows["https://www.lostfilm.one/series/Brothers/"] = TmdbPosterMappingEntity.create(
            detailsUrl = "https://www.lostfilm.one/series/Brothers/",
            tmdbId = 250203,
            tmdbType = "TV",
            posterUrl = "https://image.tmdb.org/t/p/w780/250203.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/250203-backdrop.jpg",
        )
        dao.rows["https://www.lostfilm.one/series/Brothers/season_1/"] = TmdbPosterMappingEntity.create(
            detailsUrl = "https://www.lostfilm.one/series/Brothers/season_1/",
            tmdbId = 66515,
            tmdbType = "TV",
            posterUrl = "https://image.tmdb.org/t/p/w780/stale.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/stale-backdrop.jpg",
        )
        val resolver = TmdbPosterResolverImpl(brothersClient(), dao)

        resolver.resolve(
            detailsUrl = "https://www.lostfilm.one/series/Brothers/season_1/",
            titleRu = "Братья",
            releaseDateRu = "22.09.2026",
            kind = ReleaseKind.SERIES,
            originalReleaseYear = 2026,
        )

        assertEquals(250203, dao.rows["https://www.lostfilm.one/series/Brothers/season_1/"]?.tmdbId)
    }

    @Test
    fun resolve_prefersSeriesId_whenItAppearsDuringEpisodeSearch() = runTest {
        // Гонка: эпизод начинает поиск, пока сериал ещё не записан, и дописывает
        // сезонный ключ уже после того, как сериал его вычистил. Повторная
        // сверка перед записью обязана поймать расхождение.
        val dao = StoringTmdbPosterDao()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                // Пока идёт поиск эпизода, обогащается карточка сериала.
                dao.rows["https://www.lostfilm.one/series/Brothers/"] = TmdbPosterMappingEntity.create(
                    detailsUrl = "https://www.lostfilm.one/series/Brothers/",
                    tmdbId = 250203,
                    tmdbType = "TV",
                    posterUrl = "https://image.tmdb.org/t/p/w780/250203.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/original/250203-backdrop.jpg",
                )
                return listOf(
                    TmdbSearchResult(id = 66515, name = "Brothers", originalName = "Ang Probinsyano", popularity = 63.5, releaseYear = 2015),
                )
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls =
                TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/$tmdbId.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/original/$tmdbId-backdrop.jpg",
                )

            override suspend fun getSeasonImages(tmdbId: Int, seasonNumber: Int): TmdbImageUrls? = null
        }

        TmdbPosterResolverImpl(client, dao).resolve(
            detailsUrl = "https://www.lostfilm.one/series/Brothers/season_1/episode_3/",
            titleRu = "Братья",
            releaseDateRu = "22.09.2026",
            kind = ReleaseKind.SERIES,
            originalReleaseYear = 2026,
        )

        assertEquals(250203, dao.rows["https://www.lostfilm.one/series/Brothers/season_1/"]?.tmdbId)
    }

    private fun brothersClient() = object : TmdbPosterClient(OkHttpClient(), "fake") {
        override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> =
            listOf(
                TmdbSearchResult(id = 250203, name = "Brothers", originalName = "Brothers", popularity = 41.1, releaseYear = 2026),
                TmdbSearchResult(id = 66515, name = "Brothers", originalName = "Ang Probinsyano", popularity = 63.5, releaseYear = 2015),
            )

        override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls =
            TmdbImageUrls(
                posterUrl = "https://image.tmdb.org/t/p/w780/$tmdbId.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/$tmdbId-backdrop.jpg",
            )

        override suspend fun getSeasonImages(tmdbId: Int, seasonNumber: Int): TmdbImageUrls? = null
    }

    /** Держит все записи, а не только последнюю: нужны и сериал, и сезон. */
    private class StoringTmdbPosterDao : TmdbPosterDao {
        val rows = mutableMapOf<String, TmdbPosterMappingEntity>()

        override suspend fun getByDetailsUrl(detailsUrl: String): TmdbPosterMappingEntity? = rows[detailsUrl]

        override suspend fun upsert(entity: TmdbPosterMappingEntity) {
            rows[entity.detailsUrl] = entity
        }

        override suspend fun deleteExpired(threshold: Long) = Unit

        override suspend fun deleteSeasonMappingsUnder(seriesPrefix: String) {
            rows.keys.filter { it.startsWith("$seriesPrefix/season_") }.forEach { rows.remove(it) }
        }

        override suspend fun deleteAll() {
            rows.clear()
        }
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

    override suspend fun deleteSeasonMappingsUnder(seriesPrefix: String) = Unit
                    override suspend fun deleteAll() = Unit
                },
            ),
            requestedIds = requestedIds,
        )
    }
}
