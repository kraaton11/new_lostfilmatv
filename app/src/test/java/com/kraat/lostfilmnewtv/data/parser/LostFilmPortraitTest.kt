package com.kraat.lostfilmnewtv.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LostFilmPortraitTest {
    private val mirror = "https://www.lostfilm.download"

    @Test
    fun upgradesSeasonThumbnailsOnTheirMirror() {
        for (name in listOf("image_s10", "icon_s10", "t_shmoster_s10")) {
            assertEquals(
                "$mirror/Static/Images/362/Posters/shmoster_s10.jpg",
                lostFilmPortraitUrl("$mirror/Static/Images/362/Posters/$name.jpg", mirror),
            )
        }
    }

    @Test
    fun ignoresFilmImagesAndUnrelatedHosts() {
        assertNull(lostFilmPortraitUrl("$mirror/Static/Images/1080/Posters/poster.jpg", mirror))
        assertNull(lostFilmPortraitUrl("https://example.com/Static/Images/362/Posters/image_s10.jpg", mirror))
    }
}
