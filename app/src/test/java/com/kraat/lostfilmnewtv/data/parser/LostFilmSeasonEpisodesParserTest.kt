package com.kraat.lostfilmnewtv.data.parser

import org.junit.Assert.assertEquals
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
}
