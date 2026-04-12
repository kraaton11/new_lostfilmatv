package com.kraat.lostfilmnewtv

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import com.kraat.lostfilmnewtv.di.AppModule
import com.kraat.lostfilmnewtv.di.UnitTestAppOverrides
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeActionHandler
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeAvailabilityChecker
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeConfig
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeLinkBuilder
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeUrlLauncher
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundRefreshRunner
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPreferences
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelProgram
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelProgramSource
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPublisher
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPublisherResult
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundScheduler
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelSyncManager
import com.kraat.lostfilmnewtv.updates.AppUpdateAvailabilityStore
import com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundScheduler
import com.kraat.lostfilmnewtv.updates.AppUpdateCoordinator
import com.kraat.lostfilmnewtv.updates.AppUpdateInfo
import com.kraat.lostfilmnewtv.updates.ReleaseApkLauncher
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.reset
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
        reset(homeChannelWorkManager, homeChannelWM, appUpdateWM)
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

    @Provides
    @Singleton
    fun provideWorkManager(@HomeChannelWorkManager wm: WorkManager): WorkManager = wm

    @Provides
    @Singleton
    fun providePlaybackPreferencesStore(@ApplicationContext context: Context): PlaybackPreferencesStore {
        return UnitTestAppOverrides.playbackPreferencesStore ?: PlaybackPreferencesStore(
            context,
            prefsName = "unit-test-playback-prefs",
        ).also {
            it.writeAndroidTvChannelMode(AndroidTvChannelMode.ALL_NEW)
            it.writeUpdateCheckMode(UpdateCheckMode.QUIET_CHECK)
        }
    }

    @Provides
    @Singleton
    fun provideTorrServeConfig(): TorrServeConfig = TorrServeConfig()

    @Provides
    @Singleton
    fun provideTorrServeLinkBuilder(config: TorrServeConfig): TorrServeLinkBuilder =
        UnitTestAppOverrides.torrServeLinkBuilder ?: TorrServeLinkBuilder(config)

    @Provides
    @Singleton
    fun provideTorrServeActionHandler(linkBuilder: TorrServeLinkBuilder): TorrServeActionHandler =
        UnitTestAppOverrides.torrServeActionHandler ?: TorrServeActionHandler(
            builder = linkBuilder,
            probe = TorrServeAvailabilityChecker { false },
            launcher = TorrServeUrlLauncher { _, _, _, _ -> false },
        )

    @Provides
    @Singleton
    fun provideHomeChannelSyncManager(
        playbackPreferencesStore: PlaybackPreferencesStore,
    ): HomeChannelSyncManager = UnitTestAppOverrides.homeChannelSyncManager ?: HomeChannelSyncManager(
        programSource = object : HomeChannelProgramSource {
            override suspend fun loadPrograms(mode: AndroidTvChannelMode, limit: Int): List<HomeChannelProgram> = emptyList()
        },
        preferences = object : HomeChannelPreferences {
            private var channelId: Long? = null

            override fun readMode(): AndroidTvChannelMode = playbackPreferencesStore.readAndroidTvChannelMode()

            override fun readChannelId(): Long? = channelId

            override fun writeChannelId(channelId: Long) {
                this.channelId = channelId
            }

            override fun clearChannelId() {
                channelId = null
            }
        },
        publisher = object : HomeChannelPublisher {
            override suspend fun reconcile(
                mode: AndroidTvChannelMode,
                existingChannelId: Long?,
                programs: List<HomeChannelProgram>,
            ): HomeChannelPublisherResult = HomeChannelPublisherResult(channelId = existingChannelId ?: 1L)

            override suspend fun deleteChannel(channelId: Long) = Unit
        },
    )

    @Provides @Singleton
    fun provideHomeChannelBackgroundScheduler(
        playbackPreferencesStore: PlaybackPreferencesStore,
        @HomeChannelWorkManager wm: WorkManager,
    ): HomeChannelBackgroundScheduler = UnitTestAppOverrides.homeChannelBackgroundScheduler
        ?: HomeChannelBackgroundScheduler(
            readMode = playbackPreferencesStore::readAndroidTvChannelMode,
            workManager = wm,
        )

    @Provides
    @Singleton
    fun provideHomeChannelBackgroundRefreshRunner(
        playbackPreferencesStore: PlaybackPreferencesStore,
        repository: LostFilmRepository,
        homeChannelSyncManager: HomeChannelSyncManager,
    ): HomeChannelBackgroundRefreshRunner = HomeChannelBackgroundRefreshRunner(
        readMode = playbackPreferencesStore::readAndroidTvChannelMode,
        readSession = { null },
        isSessionExpired = { false },
        refreshFirstPage = { repository.loadPage(pageNumber = 1) },
        syncChannel = homeChannelSyncManager::syncNow,
        readFirstPageFetchedAt = { null },
    )

    @Provides
    @Singleton
    fun provideAppUpdateCoordinator(
        @ApplicationContext context: Context,
    ): AppUpdateCoordinator = UnitTestAppOverrides.appUpdateCoordinator ?: AppUpdateCoordinator(
        installedVersion = "0.1.0",
        store = AppUpdateAvailabilityStore(context, prefsName = "unit-test-app-updates"),
        checkForUpdates = {
            val overrideRepository = UnitTestAppOverrides.appUpdateRepository
            if (overrideRepository != null) {
                overrideRepository.checkForUpdate()
            } else {
                AppUpdateInfo.UpToDate(installedVersion = "0.1.0")
            }
        },
    )

    @Provides @Singleton
    fun provideAppUpdateBackgroundScheduler(
        playbackPreferencesStore: PlaybackPreferencesStore,
        @AppUpdateWorkManager wm: WorkManager,
    ): AppUpdateBackgroundScheduler = UnitTestAppOverrides.appUpdateBackgroundScheduler ?: AppUpdateBackgroundScheduler(
        readMode = playbackPreferencesStore::readUpdateCheckMode,
        workManager = wm,
    )

    @Provides
    @Singleton
    fun provideReleaseApkLauncher(): ReleaseApkLauncher =
        UnitTestAppOverrides.releaseApkLauncher ?: object : ReleaseApkLauncher(OkHttpClient()) {
            override suspend fun launch(
                context: Context,
                apkUrl: String,
                onDownloadingChange: (Boolean) -> Unit,
                onDownloadProgress: (Int) -> Unit,
            ): Boolean = true
        }
}
