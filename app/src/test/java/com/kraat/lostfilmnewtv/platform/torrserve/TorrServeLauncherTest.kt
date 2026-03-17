package com.kraat.lostfilmnewtv.platform.torrserve

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.ResolveInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TorrServeLauncherTest {

    @Test
    fun launch_returns_true_when_resolveActivity_is_not_null() = runBlocking {
        val context = Robolectric.buildActivity(android.app.Activity::class.java).get()
        val shadowPackageManager = org.robolectric.Shadows.shadowOf(context.packageManager)
        
        val resolveInfo = ResolveInfo()
        resolveInfo.activityInfo = android.content.pm.ActivityInfo()
        resolveInfo.activityInfo.packageName = TorrServeLauncher.TORRSERVE_PACKAGE
        resolveInfo.activityInfo.name = TorrServeLauncher.TORRSERVE_PLAY_ACTIVITY

        shadowPackageManager.addResolveInfoForIntent(
            Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com/torrent")).setClassName(
                TorrServeLauncher.TORRSERVE_PACKAGE,
                TorrServeLauncher.TORRSERVE_PLAY_ACTIVITY,
            ),
            resolveInfo
        )

        val launcher = TorrServeLauncher()
        val result = launcher.launch(context, "http://example.com/torrent")

        assertTrue(result)
    }

    @Test
    fun launch_returns_false_when_resolveActivity_is_null() = runBlocking {
        val context = Robolectric.buildActivity(android.app.Activity::class.java).get()

        val launcher = TorrServeLauncher()
        val result = launcher.launch(context, "http://example.com/torrent")

        assertFalse(result)
    }
}
