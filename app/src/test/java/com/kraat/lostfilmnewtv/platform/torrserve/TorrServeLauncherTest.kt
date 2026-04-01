package com.kraat.lostfilmnewtv.platform.torrserve

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPackageManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TorrServeLauncherTest {

    @Before
    fun resetPackageManagerState() {
        ShadowPackageManager.reset()
    }

    @Test
    fun launch_returns_true_when_resolveActivity_is_not_null() = runBlocking {
        val context = Robolectric.buildActivity(android.app.Activity::class.java).get()
        val shadowPackageManager = org.robolectric.Shadows.shadowOf(context.packageManager)
        val componentName = ComponentName(
            TorrServeLauncher.TORRSERVE_PACKAGE,
            TorrServeLauncher.TORRSERVE_PLAY_ACTIVITY,
        )
        shadowPackageManager.addActivityIfNotPresent(componentName)
        shadowPackageManager.addIntentFilterForActivity(
            componentName,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addDataScheme(Uri.parse("http://example.com/torrent").scheme)
            },
        )

        val launcher = TorrServeLauncher(Dispatchers.Unconfined)
        val result = launcher.launch(context, "http://example.com/torrent", "Test Title", "")

        assertTrue(result)
    }

    @Test
    fun launch_returns_false_when_resolveActivity_is_null() = runBlocking {
        val context = Robolectric.buildActivity(android.app.Activity::class.java).get()

        val launcher = TorrServeLauncher(Dispatchers.Unconfined)
        val result = launcher.launch(context, "http://example.com/torrent", "Test Title", "")

        assertFalse(result)
    }
}
