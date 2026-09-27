package com.kraat.lostfilmnewtv.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
