package com.kraat.lostfilmnewtv.updates

import android.content.Context

class AppUpdateAvailabilityStore(
    context: Context,
    prefsName: String = DEFAULT_PREFS_NAME,
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun readSavedUpdate(): SavedAppUpdate? {
        val latestVersion = prefs.getString(KEY_LATEST_VERSION, null).orEmpty()
        val apkUrl = prefs.getString(KEY_APK_URL, null).orEmpty()
        if (latestVersion.isBlank() || apkUrl.isBlank()) {
            return null
        }

        return SavedAppUpdate(
            latestVersion = latestVersion,
            apkUrl = apkUrl,
        )
    }

    fun writeSavedUpdate(value: SavedAppUpdate) {
        prefs.edit()
            .putString(KEY_LATEST_VERSION, value.latestVersion)
            .putString(KEY_APK_URL, value.apkUrl)
            .apply()
    }

    fun clearSavedUpdate() {
        prefs.edit()
            .remove(KEY_LATEST_VERSION)
            .remove(KEY_APK_URL)
            .apply()
    }

    private companion object {
        const val DEFAULT_PREFS_NAME = "lostfilm_app_update_prefs"
        const val KEY_LATEST_VERSION = "saved_update_latest_version"
        const val KEY_APK_URL = "saved_update_apk_url"
    }
}
