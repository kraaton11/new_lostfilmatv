package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.LostFilmSearchItem
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import org.junit.Assert.assertEquals
import org.junit.Test

class LostFilmSearchParserTest {
    private val parser = LostFilmSearchParser()

    @Test
    fun parse_returnsSeriesAndMovies_andIgnoresPeopleRows() {
        val result = parser.parse(fixture("search-dragon.html"))

        assertEquals(
            listOf(
                LostFilmSearchItem(
                    titleRu = "Дом дракона",
                    titleEn = "House of the Dragon",
                    subtitle = "Статус: Идет • Год выхода: 2022 • Канал: HBO",
                    posterUrl = "https://www.lostfilm.today/Static/Images/676/Posters/image.jpg",
                    targetUrl = "https://www.lostfilm.today/series/House_of_the_Dragon",
                    kind = ReleaseKind.SERIES,
                ),
                LostFilmSearchItem(
                    titleRu = "Путь дракона",
                    titleEn = "The Way of the Dragon",
                    subtitle = "Год выхода: 1972 • Жанр: Боевик",
                    posterUrl = "https://www.lostfilm.today/Static/Images/1112/Posters/image.jpg",
                    targetUrl = "https://www.lostfilm.today/movies/The_Way_of_the_Dragon",
                    kind = ReleaseKind.MOVIE,
                ),
            ),
            result,
        )
    }
}
