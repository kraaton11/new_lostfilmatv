package com.kraat.lostfilmnewtv.platform.torrserve

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TorrServeAvailabilityProbeTest {

    @Test
    fun available_whenTorrServeMainActivityResolves() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = TorrServeLauncher.TORRSERVE_PACKAGE
                name = TorrServeLauncher.TORRSERVE_MAIN_ACTIVITY
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).setClassName(
                TorrServeLauncher.TORRSERVE_PACKAGE,
                TorrServeLauncher.TORRSERVE_MAIN_ACTIVITY,
            ),
            resolveInfo,
        )

        val probe = TorrServeAvailabilityProbe(context)

        assertTrue(probe.isAvailable())
    }

    @Test
    fun unavailable_whenTorrServeMainActivityDoesNotResolve() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val probe = TorrServeAvailabilityProbe(context)

        assertFalse(probe.isAvailable())
    }
}
