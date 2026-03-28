package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.FavoriteTargetKind
import org.junit.Assert.assertFalse
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

    @Test
    fun parsesSeriesPlayEpisodeId() {
        val details = LostFilmDetailsParser().parseSeries(
            fixture("series-details.html"),
            "/series/9-1-1/season_9/episode_13/",
        )

        assertEquals("362009013", details.playEpisodeId)
    }

    @Test
    fun parsesAjaxSessionTokenFromAuthenticatedUserData() {
        val html = """
            <html>
                <body>
                    <script type="text/javascript">
                        let UserData = {"id":42,"session":"ajax-session-token","auto_seen":"1"};
                    </script>
                </body>
            </html>
        """.trimIndent()

        val token = LostFilmDetailsParser().parseAjaxSessionToken(html)

        assertEquals("ajax-session-token", token)
    }

    @Test
    fun parsesAjaxSessionTokenFromSequentialUserDataAssignments() {
        val html = """
            <html>
                <body>
                    <script type="text/javascript">
                        UserData = {};
                        UserData.id = 7431747;
                        UserData.level = 1;
                        UserData.session = 'ajax-session-token';
                        UserData.auto_seen = '';
                    </script>
                </body>
            </html>
        """.trimIndent()

        val token = LostFilmDetailsParser().parseAjaxSessionToken(html)

        assertEquals("ajax-session-token", token)
    }

    @Test
    fun parsesSeriesFavoriteMetadata_fromFollowSerialAndOffButton() {
        val html = """
            <html>
                <body>
                    <div class="favorites-btn2" title="Добавить сериал в избранное" onClick="FollowSerial(915, false)">
                        <div class="icon"></div>добавить в избранное
                    </div>
                </body>
            </html>
        """.trimIndent()

        val favorite = LostFilmDetailsParser().parseFavoriteMetadata(html)

        assertEquals(915, favorite?.targetId)
        assertEquals(FavoriteTargetKind.SERIES, favorite?.targetKind)
        assertEquals(false, favorite?.isFavorite)
    }

    @Test
    fun parsesMovieFavoriteMetadata_fromRootOnButton() {
        val html = """
            <html>
                <body>
                    <div class="favorites-btn" title="Фильм в избранном" onClick="FollowSerial(1080, true)">
                        <div class="icon"></div>убрать из избранного
                    </div>
                </body>
            </html>
        """.trimIndent()

        val favorite = LostFilmDetailsParser().parseFavoriteMetadata(html)

        assertEquals(1080, favorite?.targetId)
        assertEquals(FavoriteTargetKind.MOVIE, favorite?.targetKind)
        assertEquals(true, favorite?.isFavorite)
    }

    @Test
    fun parsesFavoriteMetadata_fromSeriesRootButtonType_whenButtonLooksActiveButIsOff() {
        val html = """
            <html>
                <body>
                    <div class="favorites-btn2 active" title="Добавить сериал в избранное" onClick="FollowSerial(42, false)">
                        <div class="icon"></div>добавить в избранное
                    </div>
                </body>
            </html>
        """.trimIndent()

        val favorite = LostFilmDetailsParser().parseFavoriteMetadata(html)

        assertEquals(42, favorite?.targetId)
        assertEquals(FavoriteTargetKind.SERIES, favorite?.targetKind)
        assertEquals(false, favorite?.isFavorite)
    }

    @Test
    fun parsesFavoriteMetadata_fromSeriesRootOnButton() {
        val html = """
            <html>
                <body>
                    <div class="favorites-btn" title="Сериал в избранном" onClick="FollowSerial(42, false)">
                        <div class="icon"></div>убрать из избранного
                    </div>
                </body>
            </html>
        """.trimIndent()

        val favorite = LostFilmDetailsParser().parseFavoriteMetadata(html)

        assertEquals(42, favorite?.targetId)
        assertEquals(FavoriteTargetKind.SERIES, favorite?.targetKind)
        assertEquals(true, favorite?.isFavorite)
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

    @Test
    fun parsesTorrentLinksWithQualityLabels() {
        val links = LostFilmDetailsParser().parseTorrentLinks(
            fixture("torrent-links.html"),
        )

        assertEquals(
            listOf(
                "1080p",
                "720p",
                "480p",
            ),
            links.map { it.label },
        )
        assertEquals(3, links.size)
        assertFalse(links.any { !it.url.startsWith("https://www.lostfilm.today/V/?") })
    }

    @Test
    fun parsesTorrentOptionsFromVPage() {
        val links = LostFilmDetailsParser().parseTorrentLinks(
            fixture("torrent-options-page.html"),
        )

        assertEquals(listOf("SD", "1080p", "720p"), links.map { it.label })
        assertEquals(
            listOf(
                "https://n.tracktor.site/td.php?s=sd-token",
                "https://n.tracktor.site/td.php?s=fullhd-token",
                "https://n.tracktor.site/td.php?s=mp4-token",
            ),
            links.map { it.url },
        )
    }

    @Test
    fun parsesRedirectPageAsGenericVariantForExpansion() {
        val links = LostFilmDetailsParser().parseTorrentLinks(
            fixture("torrent-redirect.html"),
        )

        assertEquals(listOf("Вариант 1"), links.map { it.label })
        assertEquals(
            listOf("https://www.lostfilm.today/V/?c=1103&s=1&e=1&u=999999&h=fixturehash&n=1&newbie=&br=&ts=1773683822"),
            links.map { it.url },
        )
    }
}
