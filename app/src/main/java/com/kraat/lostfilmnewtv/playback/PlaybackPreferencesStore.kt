package com.kraat.lostfilmnewtv.playback

import android.content.Context
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.ui.home.HomeFeedMode
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode
import com.kraat.lostfilmnewtv.playback.WatchedMarkingMode

class PlaybackPreferencesStore(
    context: Context,
    prefsName: String = DEFAULT_PREFS_NAME,
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun readDefaultQuality(): PlaybackQualityPreference {
        return PlaybackQualityPreference.fromStorageValue(
            prefs.getString(KEY_DEFAULT_QUALITY, null),
        )
    }

    fun writeDefaultQuality(value: PlaybackQualityPreference) {
        prefs.edit()
            .putString(KEY_DEFAULT_QUALITY, value.storageValue)
            .apply()
    }

    fun readUpdateCheckMode(): UpdateCheckMode {
        return UpdateCheckMode.fromStorageValue(
            prefs.getString(KEY_UPDATE_CHECK_MODE, null),
        )
    }

    fun writeUpdateCheckMode(value: UpdateCheckMode) {
        prefs.edit()
            .putString(KEY_UPDATE_CHECK_MODE, value.storageValue)
            .apply()
    }

    fun readAndroidTvChannelMode(): AndroidTvChannelMode {
        return AndroidTvChannelMode.fromStorageValue(
            prefs.getString(KEY_ANDROID_TV_CHANNEL_MODE, null),
        )
    }

    fun writeAndroidTvChannelMode(value: AndroidTvChannelMode) {
        prefs.edit()
            .putString(KEY_ANDROID_TV_CHANNEL_MODE, value.storageValue)
            .apply()
    }

    fun readAndroidTvChannelId(): Long? {
        return if (prefs.contains(KEY_ANDROID_TV_CHANNEL_ID)) {
            prefs.getLong(KEY_ANDROID_TV_CHANNEL_ID, 0L)
        } else {
            null
        }
    }

    fun writeAndroidTvChannelId(value: Long) {
        prefs.edit()
            .putLong(KEY_ANDROID_TV_CHANNEL_ID, value)
            .apply()
    }

    fun clearAndroidTvChannelId() {
        prefs.edit()
            .remove(KEY_ANDROID_TV_CHANNEL_ID)
            .apply()
    }

    fun readHomeFavoritesRailEnabled(): Boolean {
        return prefs.getBoolean(KEY_HOME_FAVORITES_RAIL_ENABLED, false)
    }

    fun writeHomeFavoritesRailEnabled(value: Boolean) {
        prefs.edit()
            .putBoolean(KEY_HOME_FAVORITES_RAIL_ENABLED, value)
            .apply()
    }

    fun readWatchedMarkingMode(): WatchedMarkingMode {
        return WatchedMarkingMode.fromStorageValue(
            prefs.getString(KEY_WATCHED_MARKING_MODE, null),
        )
    }

    fun writeWatchedMarkingMode(value: WatchedMarkingMode) {
        prefs.edit()
            .putString(KEY_WATCHED_MARKING_MODE, value.storageValue)
            .apply()
    }

    fun readHomeSelectedFeedMode(): HomeFeedMode {
        return HomeFeedMode.fromStorageValue(
            prefs.getString(KEY_HOME_SELECTED_FEED_MODE, null),
        )
    }

    fun writeHomeSelectedFeedMode(value: HomeFeedMode) {
        prefs.edit()
            .putString(KEY_HOME_SELECTED_FEED_MODE, value.storageValue)
            .apply()
    }

    private companion object {
        const val DEFAULT_PREFS_NAME = "lostfilm_playback_prefs"
        const val KEY_DEFAULT_QUALITY = "default_quality"
        const val KEY_UPDATE_CHECK_MODE = "update_check_mode"
        const val KEY_ANDROID_TV_CHANNEL_MODE = "android_tv_channel_mode"
        const val KEY_ANDROID_TV_CHANNEL_ID = "android_tv_channel_id"
        const val KEY_HOME_FAVORITES_RAIL_ENABLED = "home_favorites_rail_enabled"
        const val KEY_HOME_SELECTED_FEED_MODE = "home_selected_feed_mode"
        const val KEY_WATCHED_MARKING_MODE = "watched_marking_mode"
    }
}
