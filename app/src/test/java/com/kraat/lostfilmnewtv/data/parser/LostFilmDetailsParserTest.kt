package com.kraat.lostfilmnewtv.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LostFilmDetailsParserTest {
    @Test
    fun parses_series_details_withRuDateOnly() {
        val details = LostFilmDetailsParser().parseSeries(
            fixture("series-details.html"),
            "/series/9-1-1/season_9/episode_13/",
        )

        assertEquals("9-1-1", details.titleRu)
        assertEquals(9, details.seasonNumber)
        assertEquals(13, details.episodeNumber)
        assertEquals("14 марта 2026", details.releaseDateRu)
    }

    @Test
    fun parses_movie_details_withoutSeasonOrEpisode() {
        val details = LostFilmDetailsParser().parseMovie(
            fixture("movie-details.html"),
            "/movies/Irreversible",
        )

        assertEquals("Необратимость", details.titleRu)
        assertNull(details.seasonNumber)
        assertNull(details.episodeNumber)
        assertEquals("13 марта 2026", details.releaseDateRu)
    }
}
