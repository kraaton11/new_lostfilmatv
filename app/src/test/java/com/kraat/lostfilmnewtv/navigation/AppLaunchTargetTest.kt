package com.kraat.lostfilmnewtv.navigation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppLaunchTargetTest {
    @Test
    fun createDetailsIntent_roundTripsDetailsUrl() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"

        val intent = AppLaunchTarget.createDetailsIntent(context, detailsUrl)

        assertEquals(detailsUrl, AppLaunchTarget.parseDetailsUrl(intent))
    }
}
