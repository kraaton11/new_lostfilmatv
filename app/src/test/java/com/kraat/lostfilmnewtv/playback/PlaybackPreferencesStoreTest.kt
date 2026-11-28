package com.kraat.lostfilmnewtv.playback

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.kraat.lostfilmnewtv.data.model.ReleaseWatchFilter
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.ui.home.HomeFeedMode
import com.kraat.lostfilmnewtv.ui.theme.AppTheme
import com.kraat.lostfilmnewtv.ui.theme.ThemeController
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackPreferencesStoreTest {
    @Test
    fun themeControllerStartsWithSavedThemeAndPersistsSelection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "theme_test_${System.nanoTime()}"
        val store = PlaybackPreferencesStore(context, prefsName)
        assertEquals(AppTheme.CLASSIC, ThemeController(store).theme.value)

        store.writeAppTheme(AppTheme.AMOLED)
        val controller = ThemeController(store)
        assertEquals(AppTheme.AMOLED, controller.theme.value)

        controller.selectTheme(AppTheme.BLUE)
        assertEquals(AppTheme.BLUE, controller.theme.value)
        assertEquals(AppTheme.BLUE, store.readAppTheme())
        val key = stringPreferencesKey("app_theme")
        val persisted = PlaybackPreferencesDataStore.get(context, prefsName).data
        val savedTheme = withTimeoutOrNull(5_000) {
            while (persisted.first()[key] != AppTheme.BLUE.storageValue) delay(10)
            persisted.first()[key]
        }
        assertEquals(AppTheme.BLUE.storageValue, savedTheme)
        assertEquals(AppTheme.CLASSIC, AppTheme.fromStorageValue("unknown"))
    }

    @Test
    fun askAfterReturnModePersistsWithItsOwnStorageValue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-watched-mode"
        val store = PlaybackPreferencesStore(context, prefsName)

        store.writeWatchedMarkingMode(WatchedMarkingMode.fromStorageValue("ask_after_return"))

        assertEquals("ask_after_return", PlaybackPreferencesStore(context, prefsName).readWatchedMarkingMode().storageValue)
    }

    @Test
    fun firstReadMigratesValuesAndRemovesLegacyEntries() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-migration"
        val legacy = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        legacy.edit()
            .putString("default_quality", PlaybackQualityPreference.Q720.storageValue)
            .putBoolean("home_movies_enabled", false)
            .putLong("android_tv_channel_id", 42L)
            .putString("torrserve_login", "legacy-user")
            .putString("release_filter_${HomeFeedMode.AllNew.storageValue}", "invalid-filter")
            .commit()

        val store = PlaybackPreferencesStore(context, prefsName)
        assertEquals(PlaybackQualityPreference.Q720, store.readDefaultQuality())
        assertEquals(false, store.readHomeMoviesEnabled())
        assertEquals(42L, store.readAndroidTvChannelId())
        assertEquals("legacy-user", store.readTorrServeLogin())
        assertEquals(ReleaseWatchFilter.All, store.readReleaseFilter(HomeFeedMode.AllNew))
        assertEquals(emptySet<String>(), legacy.all.keys)
    }

    @Test
    fun writesAndResetsPersistTogetherInDataStore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-transaction"
        val store = PlaybackPreferencesStore(context, prefsName)
        val persisted = PlaybackPreferencesDataStore.get(context, prefsName).data
        // Read a fresh snapshot each time. An observer can miss a write between
        // DataStore's initial read and its subscription to further updates.
        val snapshots = flow {
            while (true) {
                emit(persisted.first())
                delay(10)
            }
        }
        val endpoint = stringPreferencesKey("torrserve_base_url")
        val login = stringPreferencesKey("torrserve_login")
        val password = stringPreferencesKey("torrserve_password")
        val quality = stringPreferencesKey("default_quality")

        store.writeTorrServeBaseUrl("http://example.test:8090", "user", "password")
        store.writeDefaultQuality(PlaybackQualityPreference.Q720)

        val saved = withTimeoutOrNull(5_000) {
            snapshots.first { it[quality] == PlaybackQualityPreference.Q720.storageValue }
        } ?: error("DataStore did not persist TorrServe settings and playback quality")
        assertEquals("http://example.test:8090", saved[endpoint])
        assertEquals("user", saved[login])
        assertEquals("password", saved[password])

        store.resetTorrServeBaseUrl()
        val reset = withTimeoutOrNull(5_000) {
            snapshots.first { it[endpoint] == null }
        } ?: error("DataStore did not persist the TorrServe reset")
        assertNull(reset[login])
        assertNull(reset[password])
        assertEquals(PlaybackQualityPreference.Q720.storageValue, reset[quality])
    }

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
    fun readUpdateCheckMode_returnsQuietCheck_whenNothingWasSaved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-update-default"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)

        assertEquals(UpdateCheckMode.QUIET_CHECK, store.readUpdateCheckMode())
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

    @Test
    fun readAndroidTvFavoritesChannelId_returnsNull_whenNothingWasSaved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-tv-favorites-channel-id-default"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)

        assertNull(store.readAndroidTvFavoritesChannelId())
    }

    @Test
    fun writeAndClearAndroidTvFavoritesChannelId_persistIndependently() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-tv-favorites-channel-id-write"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)

        store.writeAndroidTvChannelId(42L)
        store.writeAndroidTvFavoritesChannelId(77L)
        assertEquals(42L, store.readAndroidTvChannelId())
        assertEquals(77L, store.readAndroidTvFavoritesChannelId())

        store.clearAndroidTvFavoritesChannelId()

        assertEquals(42L, store.readAndroidTvChannelId())
        assertNull(store.readAndroidTvFavoritesChannelId())
    }

    @Test
    fun readHomeFavoritesRailEnabled_returnsTrue_whenNothingWasSaved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-home-favorites-default"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)

        assertEquals(true, store.readHomeFavoritesRailEnabled())
    }

    @Test
    fun writeHomeFavoritesRailEnabled_persistsSelectedValue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-home-favorites-write"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)

        store.writeHomeFavoritesRailEnabled(true)

        assertEquals(true, store.readHomeFavoritesRailEnabled())
    }

    @Test
    fun homeModeVisibilityPreferences_defaultToVisible_andPersistSelectedValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-home-mode-visibility"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)

        assertEquals(true, store.readHomeFavoriteSeriesEnabled())
        assertEquals(true, store.readHomeMoviesEnabled())
        assertEquals(true, store.readHomeSeriesEnabled())

        store.writeHomeFavoriteSeriesEnabled(false)
        store.writeHomeMoviesEnabled(false)
        store.writeHomeSeriesEnabled(false)

        assertEquals(false, store.readHomeFavoriteSeriesEnabled())
        assertEquals(false, store.readHomeMoviesEnabled())
        assertEquals(false, store.readHomeSeriesEnabled())
    }

    @Test
    fun readHomeSelectedFeedMode_returnsAllNew_whenNothingWasSaved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-home-feed-mode-default"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)

        assertEquals(HomeFeedMode.AllNew, store.readHomeSelectedFeedMode())
    }

    @Test
    fun writeHomeSelectedFeedMode_persistsFavorites() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-home-feed-mode-write"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)

        store.writeHomeSelectedFeedMode(HomeFeedMode.Favorites)

        assertEquals(HomeFeedMode.Favorites, store.readHomeSelectedFeedMode())
    }

    @Test
    fun readHomeSelectedFeedMode_fallsBackToAllNew_whenStoredValueIsUnknown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "playback-store-home-feed-mode-unknown"
        context.deleteSharedPreferences(prefsName)
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putString("home_selected_feed_mode", "unexpected")
            .apply()
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)

        assertEquals(HomeFeedMode.AllNew, store.readHomeSelectedFeedMode())
    }
}
