package com.kraat.lostfilmnewtv

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import com.kraat.lostfilmnewtv.di.AppModule
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundScheduler
import com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundScheduler
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Inject
import javax.inject.Singleton
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    // Инжектируем mock WorkManager-ы, зарегистрированные в MainActivityTestModule ниже
    @Inject lateinit var homeChannelWorkManager: @JvmSuppressWildcards WorkManager
    @Inject @HomeChannelWorkManager lateinit var homeChannelWM: WorkManager
    @Inject @AppUpdateWorkManager lateinit var appUpdateWM: WorkManager

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun onCreate_enablesImmersiveFullscreen() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(EXPECTED_FULLSCREEN_FLAGS, activity.window.decorView.systemUiVisibility)
    }

    @Test
    fun onWindowFocusChanged_reappliesImmersiveFullscreen() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        activity.onWindowFocusChanged(true)
        assertEquals(EXPECTED_FULLSCREEN_FLAGS, activity.window.decorView.systemUiVisibility)
    }

    @Test
    fun returningToForeground_requestsImmediateChannelRefresh() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertEquals(0, enqueueUniqueWorkCount(homeChannelWM))
        controller.pause().resume()
        assertEquals(1, enqueueUniqueWorkCount(homeChannelWM))
    }

    @Test
    fun returningToForeground_requestsImmediateQuietUpdateRefresh() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertEquals(0, enqueueUniqueWorkCount(appUpdateWM))
        controller.pause().resume()
        assertEquals(1, enqueueUniqueWorkCount(appUpdateWM))
    }

    @Test
    fun bootCompletedBroadcast_requestsPeriodicChannelRefresh() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        HomeChannelRefreshBootstrapReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(1, enqueueUniquePeriodicWorkCount(homeChannelWM))
        assertEquals(0, enqueueUniqueWorkCount(homeChannelWM))
    }

    @Test
    fun packageReplacedBroadcast_requestsPeriodicChannelRefresh() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        HomeChannelRefreshBootstrapReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertEquals(1, enqueueUniquePeriodicWorkCount(homeChannelWM))
        assertEquals(0, enqueueUniqueWorkCount(homeChannelWM))
    }

    private companion object {
        const val EXPECTED_FULLSCREEN_FLAGS =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}

private fun enqueueUniqueWorkCount(wm: WorkManager) =
    mockingDetails(wm).invocations.count { it.method.name == "enqueueUniqueWork" }

private fun enqueueUniquePeriodicWorkCount(wm: WorkManager) =
    mockingDetails(wm).invocations.count { it.method.name == "enqueueUniquePeriodicWork" }

// ── Квалификаторы для двух отдельных WorkManager mock-ов ──────────────────

@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HomeChannelWorkManager

@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppUpdateWorkManager

// ── Тестовый модуль: подменяет WorkManager в обоих scheduler-ах ───────────

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModule::class],
)
object MainActivityTestAppModule {

    private val homeWM: WorkManager = mock(WorkManager::class.java)
    private val appUpdateWM: WorkManager = mock(WorkManager::class.java)

    @Provides @Singleton @HomeChannelWorkManager
    fun provideHomeChannelWorkManager(): WorkManager = homeWM

    @Provides @Singleton @AppUpdateWorkManager
    fun provideAppUpdateWorkManager(): WorkManager = appUpdateWM

    @Provides @Singleton
    fun provideHomeChannelBackgroundScheduler(
        @HomeChannelWorkManager wm: WorkManager,
    ): HomeChannelBackgroundScheduler = HomeChannelBackgroundScheduler(
        readMode = { AndroidTvChannelMode.ALL_NEW },
        workManager = wm,
    )

    @Provides @Singleton
    fun provideAppUpdateBackgroundScheduler(
        @AppUpdateWorkManager wm: WorkManager,
    ): AppUpdateBackgroundScheduler = AppUpdateBackgroundScheduler(
        readMode = { UpdateCheckMode.QUIET_CHECK },
        workManager = wm,
    )

    // Все остальные провайды делегируем стандартному AppModule
    // через явный forward — или просто не replaces, а @InstallIn дополнительно.
    // Поскольку replaces = [AppModule::class], нужно переопределить всё что тест использует.
    // Остальные зависимости из AppModule продолжают работать через UnitTestDataModule и UnitTestNetworkModule.
}
