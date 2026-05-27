package com.kraat.lostfilmnewtv.platform.torrserve

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    fun launch_grants_read_permission_for_content_torrent_uri() = runBlocking {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).get()
        val shadowPackageManager = org.robolectric.Shadows.shadowOf(activity.packageManager)
        val componentName = ComponentName(
            TorrServeLauncher.TORRSERVE_PACKAGE,
            TorrServeLauncher.TORRSERVE_PLAY_ACTIVITY,
        )
        shadowPackageManager.addActivityIfNotPresent(componentName)
        shadowPackageManager.addIntentFilterForActivity(
            componentName,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addDataScheme("content")
                addDataType(TorrServeLauncher.TORRENT_MIME_TYPE)
            },
        )
        val uri = "content://com.kraat.lostfilmnewtv.fileprovider/torrents/test.torrent"

        val launcher = TorrServeLauncher(Dispatchers.Unconfined)
        val result = launcher.launch(activity, uri, "Test Title", "")

        assertTrue(result)
        val startedIntent = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
        assertEquals(Uri.parse(uri), startedIntent.data)
        assertEquals(TorrServeLauncher.TORRENT_MIME_TYPE, startedIntent.type)
        assertTrue(startedIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun launch_returns_false_when_resolveActivity_is_null() = runBlocking {
        val context = Robolectric.buildActivity(android.app.Activity::class.java).get()

        val launcher = TorrServeLauncher(Dispatchers.Unconfined)
        val result = launcher.launch(context, "http://example.com/torrent", "Test Title", "")

        assertFalse(result)
    }
}
