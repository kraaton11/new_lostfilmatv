package com.kraat.lostfilmnewtv.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LostFilmSeasonEpisodesParserTest {
    private val parser = LostFilmSeasonEpisodesParser()

    @Test
    fun parseSerialId_extractsSeriesIdFromSeasonPageScript() {
        val html = """
            <html>
                <head>
                    <script>
                        var serial_id = '1072';
                    </script>
                </head>
                <body></body>
            </html>
        """.trimIndent()

        assertEquals("1072", parser.parseSerialId(html))
    }

    @Test
    fun parse_marksEpisodeWatchedWhenAjaxMarksContainEpisodeId() {
        val watchedIds = parser.parseWatchedEpisodeIds("""["1072001002","1072001001"]""")

        assertEquals(setOf("1072001002", "1072001001"), watchedIds)
    }

    @Test
    fun parseWatchedEpisodeIdsFromPage_extractsCheckedGuideEntries() {
        val html = """
            <html>
                <body>
                    <table class="movie-parts-list">
                        <tr>
                            <td class="alpha">
                                <div class="haveseen-btn checked" title="Серия просмотрена" data-episode="1072001003"></div>
                            </td>
                        </tr>
                        <tr>
                            <td class="alpha">
                                <div class="haveseen-btn checked" title="Серия просмотрена" data-episode="1072001002"></div>
                            </td>
                        </tr>
                        <tr>
                            <td class="alpha">
                                <div class="haveseen-btn" title="Пометить серию как просмотренную" data-episode="1072001001"></div>
                            </td>
                        </tr>
                    </table>
                </body>
            </html>
        """.trimIndent()

        val watchedIds = parser.parseWatchedEpisodeIdsFromPage(html)

        assertEquals(setOf("1072001003", "1072001002"), watchedIds)
    }

    @Test
    fun parseGuide_groupsEpisodesBySeason_andExtractsGuideRows() {
        val guide = parser.parseGuide(
            html = fixture("series-guide-ted-seasons.html"),
            watchedEpisodeIds = emptySet(),
        )

        assertEquals(listOf(2, 1), guide.map { it.seasonNumber })
        assertEquals(listOf(8, 7), guide.first().episodes.map { it.episodeNumber })
        assertEquals("Левые новости", guide.first().episodes.first().episodeTitleRu)
        assertEquals(
            "https://www.lostfilm.today/series/Ted/season_2/episode_8/",
            guide.first().episodes.first().detailsUrl,
        )
        assertEquals("24.03.2026", guide.first().episodes.first().releaseDateRu)
        assertFalse(guide.first().episodes.first().isWatched)
    }

    @Test
    fun parse_extractsEpisodeTitlesForFavorites_whenGammaCellUsesNestedDiv() {
        val items = parser.parse(
            html = fixture("series-guide-ted-seasons.html"),
            series = FavoriteSeriesRef(
                titleRu = "Третий лишний",
                posterUrl = "https://www.lostfilm.today/Static/Images/810/Posters/image.jpg",
                seriesUrl = "https://www.lostfilm.today/series/Ted",
            ),
            fetchedAt = 1_773_576_000_000L,
        )

        assertEquals(4, items.size)
        assertEquals("Левые новости", items.first { it.episodeNumber == 8 }.episodeTitleRu)
        assertEquals("Сьюзен мотает срок", items.first { it.episodeNumber == 7 }.episodeTitleRu)
    }

    @Test
    fun parseGuide_marksEpisodeWatched_whenEpisodeIdPresentInWatchedSetOrCheckedInPage() {
        val guide = parser.parseGuide(
            html = fixture("series-guide-ted-seasons.html"),
            watchedEpisodeIds = setOf("810002007"),
        )

        assertTrue(guide.first().episodes.first { it.episodeNumber == 7 }.isWatched)
        assertTrue(guide.last().episodes.first { it.episodeNumber == 3 }.isWatched)
        assertFalse(guide.last().episodes.first { it.episodeNumber == 1 }.isWatched)
    }
}
