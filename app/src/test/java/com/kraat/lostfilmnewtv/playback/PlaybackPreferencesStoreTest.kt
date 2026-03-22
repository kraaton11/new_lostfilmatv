package com.kraat.lostfilmnewtv.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun writeDefaultQualityAndUpdateCheckMode_persistTogetherInSamePrefsFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-combined"
        context.deleteSharedPreferences(prefsName)

        val store = PlaybackPreferencesStore(context, prefsName = prefsName)
        store.writeDefaultQuality(PlaybackQualityPreference.Q720)
        store.writeUpdateCheckMode(UpdateCheckMode.QUIET_CHECK)

        val recreatedStore = PlaybackPreferencesStore(context, prefsName = prefsName)

        assertEquals(PlaybackQualityPreference.Q720, recreatedStore.readDefaultQuality())
        assertEquals(UpdateCheckMode.QUIET_CHECK, recreatedStore.readUpdateCheckMode())
    }

    @Test
    fun readAndroidTvChannelMode_returnsAllNew_whenNothingWasSaved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-tv-channel-default"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)

        assertEquals(AndroidTvChannelMode.ALL_NEW, store.readAndroidTvChannelMode())
    }

    @Test
    fun readAndroidTvChannelId_returnsNull_whenNothingWasSaved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-tv-channel-id-default"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)

        assertNull(store.readAndroidTvChannelId())
    }

    @Test
    fun writeAndroidTvChannelModeAndChannelId_persistTogether() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-tv-channel-write"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)

        store.writeAndroidTvChannelMode(AndroidTvChannelMode.UNWATCHED)
        store.writeAndroidTvChannelId(42L)

        val recreatedStore = PlaybackPreferencesStore(context, prefsName = prefsName)

        assertEquals(AndroidTvChannelMode.UNWATCHED, recreatedStore.readAndroidTvChannelMode())
        assertEquals(42L, recreatedStore.readAndroidTvChannelId())
    }

    @Test
    fun clearAndroidTvChannelId_removesOnlyStoredChannelId() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-tv-channel-clear"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)

        store.writeAndroidTvChannelMode(AndroidTvChannelMode.DISABLED)
        store.writeAndroidTvChannelId(99L)

        store.clearAndroidTvChannelId()

        assertEquals(AndroidTvChannelMode.DISABLED, store.readAndroidTvChannelMode())
        assertNull(store.readAndroidTvChannelId())
    }
}
