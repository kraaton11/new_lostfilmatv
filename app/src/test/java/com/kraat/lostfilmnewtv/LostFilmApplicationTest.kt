package com.kraat.lostfilmnewtv

import androidx.test.core.app.ApplicationProvider
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundRefreshRunner
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundScheduler
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelSyncManager
import com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundScheduler
import com.kraat.lostfilmnewtv.updates.AppUpdateCoordinator
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Проверяет, что все зависимости из SingletonComponent предоставляются
 * как настоящие синглтоны — один и тот же экземпляр при каждом обращении.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LostFilmApplicationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var homeChannelSyncManager1: HomeChannelSyncManager
    @Inject lateinit var homeChannelBackgroundScheduler1: HomeChannelBackgroundScheduler
    @Inject lateinit var homeChannelBackgroundRefreshRunner1: HomeChannelBackgroundRefreshRunner
    @Inject lateinit var appUpdateCoordinator1: AppUpdateCoordinator
    @Inject lateinit var appUpdateBackgroundScheduler1: AppUpdateBackgroundScheduler

    // Второй набор для проверки того же инстанса
    @Inject lateinit var homeChannelSyncManager2: HomeChannelSyncManager
    @Inject lateinit var homeChannelBackgroundScheduler2: HomeChannelBackgroundScheduler
    @Inject lateinit var homeChannelBackgroundRefreshRunner2: HomeChannelBackgroundRefreshRunner
    @Inject lateinit var appUpdateCoordinator2: AppUpdateCoordinator
    @Inject lateinit var appUpdateBackgroundScheduler2: AppUpdateBackgroundScheduler

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun homeChannelSyncManager_isStableSingleton() {
        assertSame(homeChannelSyncManager1, homeChannelSyncManager2)
    }

    @Test
    fun homeChannelBackgroundScheduler_isStableSingleton() {
        assertSame(homeChannelBackgroundScheduler1, homeChannelBackgroundScheduler2)
    }

    @Test
    fun homeChannelBackgroundRefreshRunner_isStableSingleton() {
        assertSame(homeChannelBackgroundRefreshRunner1, homeChannelBackgroundRefreshRunner2)
    }

    @Test
    fun appUpdateCoordinator_isStableSingleton() {
        assertSame(appUpdateCoordinator1, appUpdateCoordinator2)
    }

    @Test
    fun appUpdateBackgroundScheduler_isStableSingleton() {
        assertSame(appUpdateBackgroundScheduler1, appUpdateBackgroundScheduler2)
    }
}
