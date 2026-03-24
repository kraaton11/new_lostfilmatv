package com.kraat.lostfilmnewtv.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LostFilmFavoriteReleasesParserTest {
    @Test
    fun parse_filtersMalformedCards_butKeepsValidFavoriteEpisodes() {
        val items = LostFilmFavoriteReleasesParser().parse(
            html = fixture("favorite-releases.html"),
            fetchedAt = 1_773_576_000_000L,
        )

        assertEquals(
            listOf(
                "https://www.lostfilm.today/series/example_show/season_4/episode_7/",
                "https://www.lostfilm.today/series/another_show/season_1/episode_2/",
            ),
            items.map { it.detailsUrl },
        )
        assertEquals(listOf("Пример шоу", "Другой сериал"), items.map { it.titleRu })
        assertEquals(listOf("15.03.2026", "14.03.2026"), items.map { it.releaseDateRu })
        assertTrue(items.all { it.posterUrl.isNotBlank() })
    }

    @Test
    fun parse_returnsEmptyList_forRedirectWrapperDocument() {
        val items = LostFilmFavoriteReleasesParser().parse(
            html = """
                <html>
                    <head>
                        <script type="text/javascript">
                            top.location.replace("/");
                        </script>
                    </head>
                    <body></body>
                </html>
            """.trimIndent(),
            fetchedAt = 1_773_576_000_000L,
        )

        assertTrue(items.isEmpty())
    }
}
