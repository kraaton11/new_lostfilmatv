package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
