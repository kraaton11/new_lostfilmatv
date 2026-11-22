package com.kraat.lostfilmnewtv.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import org.jsoup.Jsoup

/**
 * Proves that the request builders follow the configured host: `resolveUrl` is the single
 * entry point used across parsers/repositories to turn relative paths into absolute URLs.
 */
class LostFilmBaseUrlWiringTest {

    @Test
    fun baseUrl_defaultsToLostFilm() {
        LostFilmBaseUrl.set(LostFilmBaseUrl.DEFAULT)
        assertEquals("https://www.lostfilm.today", BASE_URL)
    }

    @Test
    fun resolveUrl_usesConfiguredHost() {
        try {
            LostFilmBaseUrl.set("https://mirror.example.com/")

            assertEquals("https://mirror.example.com", BASE_URL)
            assertEquals("https://mirror.example.com/new/", resolveUrl("/new/"))
            assertEquals("https://mirror.example.com/series/x/", resolveUrl("/series/x/"))
            assertEquals(
                "https://mirror.example.com/absolute",
                resolveUrl("https://mirror.example.com/absolute"),
            )
        } finally {
            LostFilmBaseUrl.set(LostFilmBaseUrl.DEFAULT)
        }
    }

    @Test
    fun cachedTitleLinksFollowSelectedMirror() {
        val previous = BASE_URL
        try {
            LostFilmBaseUrl.set("https://www.lostfilm.download")

            assertEquals(
                "https://www.lostfilm.download/series/x/?section=1#episode",
                resolveUrl("https://www.lostfilm.today/series/x/?section=1#episode"),
            )
            val link = Jsoup.parse("<a href='//lostfilm.tv/movies/x/'>x</a>")
                .selectFirst("a")
            assertEquals("https://www.lostfilm.download/movies/x/", link.absoluteUrl("href"))
            assertEquals("https://www.lostfilm.today/V/?id=1", resolveUrl("https://www.lostfilm.today/V/?id=1"))
            assertEquals("https://external.example/movies/x/", resolveUrl("https://external.example/movies/x/"))
        } finally {
            LostFilmBaseUrl.set(previous)
        }
    }
}
