package com.kraat.lostfilmnewtv.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackPreferencesStoreTest {
    @Test
    fun readDefaultQuality_returns1080_whenNothingWasSaved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-default"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)

        assertEquals(PlaybackQualityPreference.Q1080, store.readDefaultQuality())
    }

    @Test
    fun writeDefaultQuality_persistsSelectedValue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-write"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)

        store.writeDefaultQuality(PlaybackQualityPreference.Q720)

        assertEquals(PlaybackQualityPreference.Q720, store.readDefaultQuality())
    }

    @Test
    fun readUpdateCheckMode_returnsManual_whenNothingWasSaved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-update-default"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)

        assertEquals(UpdateCheckMode.MANUAL, store.readUpdateCheckMode())
    }

    @Test
    fun writeUpdateCheckMode_persistsQuietCheck() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-update-write"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)

        store.writeUpdateCheckMode(UpdateCheckMode.QUIET_CHECK)

        assertEquals(UpdateCheckMode.QUIET_CHECK, store.readUpdateCheckMode())
    }
}
