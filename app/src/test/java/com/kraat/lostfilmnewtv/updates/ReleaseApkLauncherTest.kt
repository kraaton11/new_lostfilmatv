package com.kraat.lostfilmnewtv.updates

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPackageManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReleaseApkLauncherTest {

    @Before
    fun resetPackageManagerState() {
        ShadowPackageManager.reset()
    }

    @Test
    fun launch_returnsTrue_whenViewIntentResolves() = runBlocking {
        val context = Robolectric.buildActivity(android.app.Activity::class.java).get()
        val shadowPackageManager = org.robolectric.Shadows.shadowOf(context.packageManager)
        val componentName = ComponentName(
            "com.example.installer",
            "com.example.installer.InstallActivity",
        )
        shadowPackageManager.addActivityIfNotPresent(componentName)
        shadowPackageManager.addIntentFilterForActivity(
            componentName,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addDataScheme(Uri.parse("https://example.test/releases/lostfilm-tv.apk").scheme)
            },
        )

        val launcher = ReleaseApkLauncher(Dispatchers.Unconfined)
        val result = launcher.launch(context, "https://example.test/releases/lostfilm-tv.apk")

        assertTrue(result)
    }

    @Test
    fun launch_returnsFalse_whenNoActivityCanHandleApkUrl() = runBlocking {
        val context = Robolectric.buildActivity(android.app.Activity::class.java).get()

        val launcher = ReleaseApkLauncher(Dispatchers.Unconfined)
        val result = launcher.launch(context, "https://example.test/releases/lostfilm-tv.apk")

        assertFalse(result)
    }
}
