package com.kraat.lostfilmnewtv.playback

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackPreferencesStoreLostFilmHostTest {

    @Test
    fun returnsDefaultWhenUnset() {
        val store = PlaybackPreferencesStore(
            ApplicationProvider.getApplicationContext(),
            "test_lostfilm_host_prefs_unset",
        )
        assertEquals("https://www.lostfilm.today", store.readLostFilmBaseUrl())
    }

    @Test
    fun persistsWrittenValueAndResets() {
        val store = PlaybackPreferencesStore(
            ApplicationProvider.getApplicationContext(),
            "test_lostfilm_host_prefs_persist",
        )
        store.writeLostFilmBaseUrl("https://mirror.example.com")
        assertEquals("https://mirror.example.com", store.readLostFilmBaseUrl())

        store.resetLostFilmBaseUrl()
        assertEquals("https://www.lostfilm.today", store.readLostFilmBaseUrl())
    }
}
