package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LostFilmListParserTest {
    @Test
    fun parses_series_row_withRussianOnlyFields() {
        val html = fixture("new-page-1.html")

        val results = LostFilmListParser().parse(html, pageNumber = 1)
        val firstSeries = results.first { it.kind == ReleaseKind.SERIES && it.titleRu == "9-1-1" }

        assertEquals(ReleaseKind.SERIES, firstSeries.kind)
        assertEquals("9-1-1", firstSeries.titleRu)
        assertEquals("Маменькин сынок", firstSeries.episodeTitleRu)
        assertEquals(9, firstSeries.seasonNumber)
        assertEquals(13, firstSeries.episodeNumber)
        assertEquals("14.03.2026", firstSeries.releaseDateRu)
    }

    @Test
    fun parses_movie_row_withoutSeasonOrEpisodeNumbers() {
        val html = fixture("new-page-1.html")

        val results = LostFilmListParser().parse(html, pageNumber = 1)
        val movie = results.first { it.kind == ReleaseKind.MOVIE && it.titleRu == "Необратимость" }

        assertEquals(ReleaseKind.MOVIE, movie.kind)
        assertEquals("Необратимость", movie.titleRu)
        assertNull(movie.episodeTitleRu)
        assertNull(movie.seasonNumber)
        assertNull(movie.episodeNumber)
        assertEquals("13.03.2026", movie.releaseDateRu)
    }

    @Test
    fun parsesWatchedStateFromHaveSeenButton() {
        val html = fixture("new-page-1.html")
            .replaceFirst(
                "class=\"haveseen-btn\"",
                "class=\"haveseen-btn checked\"",
            )

        val results = LostFilmListParser().parse(html, pageNumber = 1)
        val watchedSeries = results.first { it.kind == ReleaseKind.SERIES && it.titleRu == "9-1-1" }
        val unwatchedMovie = results.first { it.kind == ReleaseKind.MOVIE && it.titleRu == "Необратимость" }

        assertTrue(watchedSeries.isWatched)
        assertFalse(unwatchedMovie.isWatched)
    }

    @Test
    fun parsesWatchMarkersFromListRows() {
        val html = fixture("new-page-1.html")

        val markers = LostFilmListParser().parseWatchMarkers(html)
        val seriesMarker = markers.first { it.detailsUrl.endsWith("/series/9-1-1/season_9/episode_13/") }
        val movieMarker = markers.first { it.detailsUrl.endsWith("/movies/Irreversible") }

        assertEquals("362009013", seriesMarker.episodeId)
        assertEquals("362", seriesMarker.serialId)
        assertEquals("1080001001", movieMarker.episodeId)
        assertEquals("1080", movieMarker.serialId)
    }

    @Test
    fun fallsBackToEnglishEpisodeTitle_whenRussianTitleMissingInListRow() {
        val html = """
            <div class="serials-list">
                <div class="row">
                    <a href="/series/The_Testaments/season_1/episode_1/" style="text-decoration:none;display:block">
                        <div class="picture-box">
                            <div class="overlay">
                                <div class="left-part">1 сезон 1 серия</div>
                            </div>
                            <img src="/Static/Images/1093/Posters/image_s1.jpg" class="thumb" />
                        </div>
                        <div class="body">
                            <div class="name-ru">Заветы</div>
                            <div class="details-pane">
                                <div class="alpha"></div>
                                <div class="beta">Precious Flowers</div>
                                <div class="alpha">Дата выхода Ru: 11.04.2026</div>
                                <div class="beta">Дата выхода Eng: 08.04.2026</div>
                            </div>
                        </div>
                    </a>
                </div>
            </div>
        """.trimIndent()

        val item = LostFilmListParser().parse(html, pageNumber = 1).single()

        assertEquals("Заветы", item.titleRu)
        assertEquals("Precious Flowers", item.episodeTitleRu)
        assertEquals("11.04.2026", item.releaseDateRu)
    }
}
