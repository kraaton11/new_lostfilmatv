package com.kraat.lostfilmnewtv

import androidx.work.WorkManager
import androidx.test.core.app.ApplicationProvider
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundRefreshRunner
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.Mockito.mock

@RunWith(RobolectricTestRunner::class)
@Config(application = TestLostFilmApplicationForBackgroundDeps::class)
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

    @Test
    fun homeChannelBackgroundScheduler_isExposedAsStableApplicationDependency() {
        val application = ApplicationProvider.getApplicationContext<LostFilmApplication>()

        assertSame(application.homeChannelBackgroundScheduler, application.homeChannelBackgroundScheduler)
    }

    @Test
    fun homeChannelBackgroundRefreshRunner_isExposedAsStableApplicationDependency() {
        val application = ApplicationProvider.getApplicationContext<LostFilmApplication>()

        assertSame(application.homeChannelBackgroundRefreshRunner, application.homeChannelBackgroundRefreshRunner)
    }
}

class TestLostFilmApplicationForBackgroundDeps : LostFilmApplication() {
    override val homeChannelBackgroundRefreshRunner: HomeChannelBackgroundRefreshRunner by lazy {
        HomeChannelBackgroundRefreshRunner(
            readMode = { AndroidTvChannelMode.ALL_NEW },
            readSession = { null },
            isSessionExpired = { false },
            refreshFirstPage = { error("refreshFirstPage should not run in this test") },
            syncChannel = {},
        )
    }

    override val homeChannelBackgroundScheduler: HomeChannelBackgroundScheduler by lazy {
        HomeChannelBackgroundScheduler(
            readMode = { AndroidTvChannelMode.ALL_NEW },
            workManager = mock(WorkManager::class.java),
        )
    }
}
