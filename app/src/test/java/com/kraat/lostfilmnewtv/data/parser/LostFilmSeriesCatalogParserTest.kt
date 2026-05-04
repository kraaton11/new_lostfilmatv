package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LostFilmSeriesCatalogParserTest {
    @Test
    fun parsesSeriesCatalogRows() {
        val html = fixture("series-catalog.html")

        val results = LostFilmSeriesCatalogParser().parse(html, fetchedAt = 42L)

        assertEquals(2, results.size)

        val first = results.first()
        assertEquals(ReleaseKind.SERIES, first.kind)
        assertEquals("Паук-Нуар", first.titleRu)
        assertEquals("Spider-Noir", first.episodeTitleRu)
        assertEquals("https://www.lostfilm.today/series/Spider_Noir", first.detailsUrl)
        assertEquals("https://www.lostfilm.today/Static/Images/1095/Posters/image.jpg", first.posterUrl)
        assertEquals("Скоро", first.availabilityLabel)
        assertEquals("2026", first.releaseDateRu)
        assertEquals(2026, first.originalReleaseYear)
        assertNull(first.seasonNumber)
        assertNull(first.episodeNumber)
        assertEquals(42L, first.fetchedAt)
    }

    @Test
    fun parsesSeriesCatalogSearchJsonRows() {
        val json = """
            {
              "result": "ok",
              "data": [
                {
                  "title": "Паук-Нуар",
                  "title_orig": "Spider-Noir",
                  "date": "2026",
                  "link": "/series/Spider_Noir",
                  "has_image": true,
                  "img": "/Static/Images/1095/Posters/image.jpg",
                  "not_aired": true
                }
              ]
            }
        """.trimIndent()

        val item = LostFilmSeriesCatalogParser()
            .parseSearchJson(json = json, pageNumber = 2, fetchedAt = 43L)
            .single()

        assertEquals(ReleaseKind.SERIES, item.kind)
        assertEquals("Паук-Нуар", item.titleRu)
        assertEquals("Spider-Noir", item.episodeTitleRu)
        assertEquals("https://www.lostfilm.today/series/Spider_Noir", item.detailsUrl)
        assertEquals("https://www.lostfilm.today/Static/Images/1095/Posters/image.jpg", item.posterUrl)
        assertEquals("Скоро", item.availabilityLabel)
        assertEquals("2026", item.releaseDateRu)
        assertEquals(2, item.pageNumber)
        assertEquals(43L, item.fetchedAt)
    }
}
