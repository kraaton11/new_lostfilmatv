package com.kraat.lostfilmnewtv

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LostFilmApplicationTest {
    @Test
    fun authBridgeBaseUrl_usesProductionAuthDomain() {
        val application = LostFilmApplication()

        assertEquals("https://auth.bazuka.pp.ua", application.authBridgeBaseUrl)
    }

    @Test
    fun homeChannelSyncManager_isExposedAsStableApplicationDependency() {
        val application = ApplicationProvider.getApplicationContext<LostFilmApplication>()

        assertSame(application.homeChannelSyncManager, application.homeChannelSyncManager)
    }
}
