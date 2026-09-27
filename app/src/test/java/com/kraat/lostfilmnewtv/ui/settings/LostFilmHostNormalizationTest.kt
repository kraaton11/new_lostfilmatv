package com.kraat.lostfilmnewtv.ui.settings

import com.kraat.lostfilmnewtv.data.parser.LostFilmBaseUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LostFilmHostNormalizationTest {

    @Test
    fun addsHttpsSchemeWhenMissing() {
        assertEquals("https://example.com", normalizeLostFilmHost("example.com"))
    }

    @Test
    fun trimsWhitespaceAndTrailingSlash() {
        assertEquals("https://example.com", normalizeLostFilmHost("  example.com/  "))
    }

    @Test
    fun dropsQueryAndFragment() {
        assertEquals("https://example.com", normalizeLostFilmHost("https://example.com/?a=1#x"))
    }

    @Test
    fun keepsPort() {
        assertEquals("https://example.com:8443", normalizeLostFilmHost("https://example.com:8443/"))
    }

    @Test
    fun acceptsFullHttpsUrl() {
        assertEquals("https://www.lostfilm.today", normalizeLostFilmHost("https://www.lostfilm.today"))
    }

    @Test
    fun rejectsBlank() {
        assertNull(normalizeLostFilmHost("   "))
        assertNull(normalizeLostFilmHost(""))
    }

    @Test
    fun rejectsNonHttpsScheme() {
        assertNull(normalizeLostFilmHost("http://example.com"))
    }

    @Test
    fun rejectsMalformed() {
        assertNull(normalizeLostFilmHost("http://"))
        assertNull(normalizeLostFilmHost("::::"))
    }

    @Test
    fun defaultBaseUrlIsLostFilm() {
        assertEquals("https://www.lostfilm.today", LostFilmBaseUrl.DEFAULT)
        assertEquals("https://www.lostfilm.today", LostFilmBaseUrl.get())
    }

    @Test
    fun holderNormalizesAndFallsBackToDefault() {
        try {
            LostFilmBaseUrl.set("https://mirror.example.com/")
            assertEquals("https://mirror.example.com", LostFilmBaseUrl.get())
            LostFilmBaseUrl.set("   ")
            assertEquals("https://www.lostfilm.today", LostFilmBaseUrl.get())
        } finally {
            LostFilmBaseUrl.set(LostFilmBaseUrl.DEFAULT)
        }
    }
}
