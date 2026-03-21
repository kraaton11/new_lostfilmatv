package com.kraat.lostfilmnewtv.updates

import android.content.Context
import android.content.ContextWrapper
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPackageManager
import org.mockito.Mockito.mock

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
        val context = FailingContext(Robolectric.buildActivity(android.app.Activity::class.java).get())

        val launcher = ReleaseApkLauncher(Dispatchers.Unconfined)
        val result = launcher.launch(context, "https://example.test/releases/lostfilm-tv.apk")

        assertFalse(result)
    }

    @Test
    fun launch_startsApkIntent_evenWhenPackageVisibilityHidesResolvers() = runBlocking {
        val context = RecordingContext(Robolectric.buildActivity(android.app.Activity::class.java).get())

        val launcher = ReleaseApkLauncher(Dispatchers.Unconfined)
        val result = launcher.launch(context, "https://example.test/releases/lostfilm-tv.apk")

        assertTrue(result)
        assertEquals(Intent.ACTION_VIEW, context.startedIntent?.action)
        assertEquals(
            "https://example.test/releases/lostfilm-tv.apk",
            context.startedIntent?.data.toString(),
        )
        assertTrue(context.startedIntent?.flags?.and(Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        private val packageManager = mock(PackageManager::class.java)
        var startedIntent: Intent? = null

        override fun getPackageManager(): PackageManager = packageManager

        override fun startActivity(intent: Intent?) {
            startedIntent = intent
        }
    }

    private class FailingContext(base: Context) : ContextWrapper(base) {
        private val packageManager = mock(PackageManager::class.java)

        override fun getPackageManager(): PackageManager = packageManager

        override fun startActivity(intent: Intent?) {
            throw ActivityNotFoundException()
        }
    }
}
