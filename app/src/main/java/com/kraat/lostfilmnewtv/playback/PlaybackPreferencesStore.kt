package com.kraat.lostfilmnewtv.playback

import android.content.Context

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

    private companion object {
        const val DEFAULT_PREFS_NAME = "lostfilm_playback_prefs"
        const val KEY_DEFAULT_QUALITY = "default_quality"
    }
}
