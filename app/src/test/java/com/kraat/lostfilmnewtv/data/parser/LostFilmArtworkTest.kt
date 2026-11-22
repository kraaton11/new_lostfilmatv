package com.kraat.lostfilmnewtv.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LostFilmArtworkTest {
    private val posterPath = "/Static/Images/362/Posters/shmoster_s10.jpg"

    @Test
    fun normalizesAbsoluteArtworkFromKnownMirrorsToPaths() {
        assertEquals(posterPath, lostFilmArtworkPath("https://www.lostfilm.tv$posterPath"))
        assertEquals(posterPath, lostFilmArtworkPath("//static.lostfilm.today$posterPath"))
        assertEquals(posterPath, lostFilmArtworkPath("https://www.lostfilm.download$posterPath"))
    }

    @Test
    fun resolvesCachedArtworkAgainstEachSelectedMirror() {
        assertEquals(
            "https://www.lostfilm.download$posterPath",
            lostFilmArtworkUrl(posterPath, "https://www.lostfilm.download"),
        )
        assertEquals(
            "https://my-mirror.example.org$posterPath",
            lostFilmArtworkUrl(
                "https://www.my-mirror.example.org$posterPath",
                "https://my-mirror.example.org",
            ),
        )
    }

    @Test
    fun rejectsLegacyAndUnrelatedImageUrls() {
        assertNull(lostFilmArtworkUrl("https://image.tmdb.org/t/p/w500/poster.jpg"))
        assertNull(lostFilmArtworkUrl("https://proxy.example.org/api/tmdb/image/t/p/w500/poster.jpg"))
        assertNull(lostFilmArtworkUrl("https://lostfilm.today.other.example$posterPath"))
        assertNull(lostFilmArtworkUrl("/Static/Images/../../api/tmdb/poster.jpg"))
    }
}
