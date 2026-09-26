package com.kraat.lostfilmnewtv.data.network

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Кинопоиск отдаёт десятки films с одинаковым русским названием: фильм, сериал
 * и переводы. Раньше выбор был жёстко «приоритет TV_SERIES» без оглядки на то,
 * фильм мы ищем или сериал, — из-за чего карточка фильма получала постер и
 * описание одноимённого сериала.
 */
@RunWith(RobolectricTestRunner::class)
class KinoPoiskClientSearchTest {

    @Test
    fun searchByKeyword_picksTheFilm_whenTheSameTitleAlsoHasASeries() = runTest {
        val client = clientReturning(
            films = listOf(
                kpFilm(2544, type = "FILM", year = "1999", nameRu = "В поисках галактики", nameEn = "Galaxy Quest"),
                kpFilm(1044280, type = "FILM", year = "2023", nameRu = "Стражи Галактики. Часть 3", nameEn = "Guardians of the Galaxy Vol. 3"),
                kpFilm(1379545, type = "TV_SERIES", year = "2022", nameRu = "Звездный путь: Странные новые миры", nameEn = "Star Trek: Strange New Worlds"),
                kpFilm(153013, type = "TV_SERIES", year = "2004", nameRu = "Звёздный крейсер «Галактика»", nameEn = "Battlestar Galactica"),
            ),
        )

        val result = client.searchByKeyword(
            query = "В поисках галактики",
            acceptedTypes = KINOPOISK_FILM_TYPES,
        )

        requireNotNull(result)
        assertEquals("Фильм должен побеждать одноимённый сериал", 2544, result.filmId)
        assertEquals("Galaxy Quest", result.nameEn)
    }

    @Test
    fun searchByKeyword_picksTheSeries_whenWeLookForASeries() = runTest {
        val client = clientReturning(
            films = listOf(
                kpFilm(340467, type = "TV_SERIES", year = "1972", nameRu = "Шестое чувство", nameEn = "The Sixth Sense"),
                kpFilm(395, type = "FILM", year = "1999", nameRu = "Шестое чувство", nameEn = "The Sixth Sense"),
            ),
        )

        val result = client.searchByKeyword(
            query = "Шестое чувство",
            acceptedTypes = KINOPOISK_SERIES_TYPES,
        )

        requireNotNull(result)
        assertEquals(340467, result.filmId)
    }

    @Test
    fun searchByKeyword_prefersTheRequestedYear_whenSeveralFilmsShareTheTitle() = runTest {
        val client = clientReturning(
            films = listOf(
                kpFilm(133930, type = "FILM", year = "1929", nameRu = "Шестое чувство", nameEn = "El sexto sentido"),
                kpFilm(455743, type = "FILM", year = "1935", nameRu = "Шестое чувство", nameEn = ""),
                kpFilm(395, type = "FILM", year = "1999", nameRu = "Шестое чувство", nameEn = "The Sixth Sense"),
            ),
        )

        val result = client.searchByKeyword(
            query = "Шестое чувство",
            acceptedTypes = KINOPOISK_FILM_TYPES,
            expectedYear = "1999",
        )

        requireNotNull(result)
        assertEquals(395, result.filmId)
    }

    @Test
    fun searchByKeyword_prefersTheMatchingEnglishName_whenRussianTitleIsGeneric() = runTest {
        val client = clientReturning(
            films = listOf(
                kpFilm(5333558, type = "FILM", year = "2024", nameRu = "Пираты галактики Барракуда", nameEn = ""),
                kpFilm(2544, type = "FILM", year = "1999", nameRu = "В поисках галактики", nameEn = "Galaxy Quest"),
            ),
        )

        val result = client.searchByKeyword(
            query = "В поисках галактики",
            acceptedTypes = KINOPOISK_FILM_TYPES,
            expectedNameEn = "Galaxy Quest",
        )

        requireNotNull(result)
        assertEquals(2544, result.filmId)
    }

    @Test
    fun searchByKeyword_returnsNull_whenNoResultHasTheRequestedType() = runTest {
        val client = clientReturning(
            films = listOf(
                kpFilm(1379545, type = "TV_SERIES", year = "2022", nameRu = "В поисках галактики", nameEn = "Star Trek"),
            ),
        )

        val result = client.searchByKeyword(
            query = "В поисках галактики",
            acceptedTypes = KINOPOISK_FILM_TYPES,
        )

        assertEquals("Одноимённого фильма нет — лучше пусто, чем чужой сериал", null, result)
    }

    @Test
    fun searchByKeyword_stillReturnsFirstMatch_whenNoConstraintsGiven() = runTest {
        val client = clientReturning(
            films = listOf(
                kpFilm(1, type = "FILM", year = "2001", nameRu = "А", nameEn = "A"),
                kpFilm(2, type = "FILM", year = "2002", nameRu = "Б", nameEn = "B"),
            ),
        )

        assertEquals(1, client.searchByKeyword(query = "А")?.filmId)
    }

    private fun kpFilm(id: Int, type: String, year: String, nameRu: String, nameEn: String) =
        """{"filmId":$id,"type":"$type","year":"$year","nameRu":"$nameRu","nameEn":"$nameEn",
           "rating":"7.1","posterUrl":"https://kinopoiskapiunofficial.tech/images/posters/kp/$id.jpg"}"""

    private fun clientReturning(films: List<String>) = KinoPoiskClient(
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"pagesCount":1,"films":[${films.joinToString(",")}]}""".toResponseBody())
                    .build()
            })
            .build(),
        baseUrl = "https://kp.example.test",
    )
}
