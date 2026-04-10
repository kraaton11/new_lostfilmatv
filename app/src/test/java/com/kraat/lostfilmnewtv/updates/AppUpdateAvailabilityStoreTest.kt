package com.kraat.lostfilmnewtv.updates

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppUpdateAvailabilityStoreTest {
    @Test
    fun readSavedUpdate_returnsNull_whenNothingWasSaved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "app-update-store-default"
        context.deleteSharedPreferences(prefsName)
        val store = AppUpdateAvailabilityStore(context, prefsName = prefsName)

        assertNull(store.readSavedUpdate())
    }

    @Test
    fun writeSavedUpdate_persistsLatestVersionAndApkUrl() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "app-update-store-write"
        context.deleteSharedPreferences(prefsName)
        val store = AppUpdateAvailabilityStore(context, prefsName = prefsName)

        store.writeSavedUpdate(
            SavedAppUpdate(
                latestVersion = "1.2.0",
                apkUrl = "https://example.test/app-1.2.0.apk",
            ),
        )

        assertEquals(
            SavedAppUpdate(
                latestVersion = "1.2.0",
                apkUrl = "https://example.test/app-1.2.0.apk",
            ),
            store.readSavedUpdate(),
        )
    }

    @Test
    fun clearSavedUpdate_removesOnlySavedUpdatePayload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "app-update-store-clear"
        context.deleteSharedPreferences(prefsName)
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("unrelated_key", "keep-me")
            .apply()
        val store = AppUpdateAvailabilityStore(context, prefsName = prefsName)

        store.writeSavedUpdate(
            SavedAppUpdate(
                latestVersion = "1.2.0",
                apkUrl = "https://example.test/app-1.2.0.apk",
            ),
        )

        store.clearSavedUpdate()

        assertNull(store.readSavedUpdate())
        assertEquals("keep-me", prefs.getString("unrelated_key", null))
    }
}
