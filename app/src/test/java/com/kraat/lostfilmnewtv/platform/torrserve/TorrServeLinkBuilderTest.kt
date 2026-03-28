package com.kraat.lostfilmnewtv.platform.torrserve

import org.junit.Test
import org.junit.Assert.*

class TorrServeLinkBuilderTest {
    private val config = TorrServeConfig()
    private val builder = TorrServeLinkBuilder(config)

    @Test
    fun supports_httpHttpsAndMagnet_caseInsensitively() {
        assertTrue(builder.supportsSource("https://example.com/torrent.torrent"))
        assertTrue(builder.supportsSource("HTTP://example.com/torrent.torrent"))
        assertTrue(builder.supportsSource("magnet:?xt=urn:btih:1234567890"))
    }

    @Test
    fun rejects_blankMalformedAndUnsupportedSources() {
        assertFalse(builder.supportsSource(" "))
        assertFalse(builder.supportsSource("http://"))
        assertFalse(builder.supportsSource("https:///foo"))
        assertFalse(builder.supportsSource("magnet:"))
        assertFalse(builder.supportsSource("magnet:?"))
        assertFalse(builder.supportsSource("ftp://example.com/torrent.torrent"))
    }

    @Test
    fun build_encodesHttpSourceAndUsesExactM3uFlag() {
        val result = builder.build("https://example.com/torrent.torrent")
        val expected = "https://example.com/torrent.torrent"
        assertEquals(expected, result)
    }

    @Test
    fun build_trimsWhitespaceWithoutRewritingSupportedSource() {
        val result = builder.build("  magnet:?xt=urn:btih:1234567890  ")

        assertEquals("magnet:?xt=urn:btih:1234567890", result)
    }

    @Test
    fun build_returnsNull_forUnsupportedInputsThatSupportsSourceRejects() {
        assertNull(builder.build(" "))
        assertNull(builder.build("http://"))
        assertNull(builder.build("https:///foo"))
        assertNull(builder.build("magnet:"))
        assertNull(builder.build("magnet:?"))
        assertNull(builder.build("ftp://example.com/torrent.torrent"))
    }
}
