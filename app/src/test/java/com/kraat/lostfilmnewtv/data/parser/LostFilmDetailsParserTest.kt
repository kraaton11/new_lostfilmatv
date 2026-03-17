package com.kraat.lostfilmnewtv.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun parsesSeriesPlayEpisodeId() {
        val details = LostFilmDetailsParser().parseSeries(
            fixture("series-details.html"),
            "/series/9-1-1/season_9/episode_13/",
        )

        assertEquals("362009013", details.playEpisodeId)
    }

    @Test
    fun parsesTorrentRedirectLink() {
        val link = LostFilmDetailsParser().parseTorrentRedirect(
            fixture("torrent-redirect.html"),
        )

        assertEquals(
            "https://www.lostfilm.today/V/?c=1103&s=1&e=1&u=999999&h=fixturehash&n=1&newbie=&br=&ts=1773683822",
            link,
        )
    }
}
