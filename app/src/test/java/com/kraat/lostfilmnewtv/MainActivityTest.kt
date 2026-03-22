package com.kraat.lostfilmnewtv

import android.content.Intent
import com.kraat.lostfilmnewtv.data.auth.AuthCompletionResult
import com.kraat.lostfilmnewtv.data.auth.AuthRepositoryContract
import com.kraat.lostfilmnewtv.data.model.AuthState
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.PairingSession
import com.kraat.lostfilmnewtv.data.model.PairingStatus
import com.kraat.lostfilmnewtv.data.repository.DetailsResult
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundScheduler
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPreferences
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelProgram
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelProgramSource
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPublisher
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPublisherResult
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelSyncManager
import com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundScheduler as QuietAppUpdateBackgroundScheduler
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails

@RunWith(RobolectricTestRunner::class)
@Config(application = TestLostFilmApplication::class)
class MainActivityTest {
    @Before
    fun setUp() {
        TestLostFilmApplication.homeChannelWorkManager = mock(WorkManager::class.java)
        TestLostFilmApplication.appUpdateWorkManager = mock(WorkManager::class.java)
    }

    @After
    fun tearDown() {
        TestLostFilmApplication.homeChannelWorkManager = null
        TestLostFilmApplication.appUpdateWorkManager = null
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
        val workManager = checkNotNull(TestLostFilmApplication.homeChannelWorkManager)

        assertEquals(0, enqueueUniqueWorkCount(workManager))

        controller.pause().resume()

        assertEquals(1, enqueueUniqueWorkCount(workManager))
    }

    @Test
    fun returningToForeground_requestsImmediateQuietUpdateRefresh() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val workManager = checkNotNull(TestLostFilmApplication.appUpdateWorkManager)

        assertEquals(0, enqueueUniqueWorkCount(workManager))

        controller.pause().resume()

        assertEquals(1, enqueueUniqueWorkCount(workManager))
    }

    @Test
    fun bootCompletedBroadcast_requestsPeriodicAndImmediateChannelRefresh() {
        val application = ApplicationProvider.getApplicationContext<TestLostFilmApplication>()
        val workManager = checkNotNull(TestLostFilmApplication.homeChannelWorkManager)

        HomeChannelRefreshBootstrapReceiver().onReceive(
            application,
            Intent(Intent.ACTION_BOOT_COMPLETED),
        )

        assertEquals(1, enqueueUniquePeriodicWorkCount(workManager))
        assertEquals(1, enqueueUniqueWorkCount(workManager))
    }

    @Test
    fun packageReplacedBroadcast_requestsPeriodicAndImmediateChannelRefresh() {
        val application = ApplicationProvider.getApplicationContext<TestLostFilmApplication>()
        val workManager = checkNotNull(TestLostFilmApplication.homeChannelWorkManager)

        HomeChannelRefreshBootstrapReceiver().onReceive(
            application,
            Intent(Intent.ACTION_MY_PACKAGE_REPLACED),
        )

        assertEquals(1, enqueueUniquePeriodicWorkCount(workManager))
        assertEquals(1, enqueueUniqueWorkCount(workManager))
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

private fun enqueueUniqueWorkCount(workManager: WorkManager): Int {
    return mockingDetails(workManager).invocations.count {
        it.method.name == "enqueueUniqueWork"
    }
}

private fun enqueueUniquePeriodicWorkCount(workManager: WorkManager): Int {
    return mockingDetails(workManager).invocations.count {
        it.method.name == "enqueueUniquePeriodicWork"
    }
}

class TestLostFilmApplication : LostFilmApplication() {
    override val homeChannelBackgroundScheduler: HomeChannelBackgroundScheduler by lazy {
        HomeChannelBackgroundScheduler(
            readMode = { AndroidTvChannelMode.ALL_NEW },
            workManager = checkNotNull(homeChannelWorkManager) {
                "homeChannelWorkManager must be set before creating the application"
            },
        )
    }

    override val appUpdateBackgroundScheduler: QuietAppUpdateBackgroundScheduler by lazy {
        QuietAppUpdateBackgroundScheduler(
            readMode = { com.kraat.lostfilmnewtv.updates.UpdateCheckMode.QUIET_CHECK },
            workManager = checkNotNull(appUpdateWorkManager) {
                "appUpdateWorkManager must be set before creating the application"
            },
        )
    }

    override val authRepository: AuthRepositoryContract by lazy {
        object : AuthRepositoryContract {
            override suspend fun getAuthState(): AuthState = AuthState(isAuthenticated = false)

            override suspend fun startPairing(): PairingSession {
                return PairingSession(
                    pairingId = "pairing",
                    pairingSecret = "secret",
                    phoneVerifier = "verifier",
                    userCode = "code",
                    verificationUrl = "https://example.com",
                    status = PairingStatus.PENDING,
                    expiresIn = 60,
                )
            }

            override suspend fun pollPairingStatus(): PairingSession? = null

            override suspend fun claimAndPersistSession(): AuthCompletionResult {
                return AuthCompletionResult.VerificationFailed
            }

            override suspend fun logout() = Unit
        }
    }

    override val repository: LostFilmRepository by lazy {
        object : LostFilmRepository {
            override suspend fun loadPage(pageNumber: Int): PageState {
                return PageState.Content(
                    pageNumber = pageNumber,
                    items = emptyList(),
                    hasNextPage = false,
                    isStale = false,
                )
            }

            override suspend fun loadDetails(detailsUrl: String): DetailsResult {
                return DetailsResult.Error(detailsUrl = detailsUrl, message = "not needed")
            }

            override suspend fun markEpisodeWatched(detailsUrl: String, playEpisodeId: String): Boolean = false
        }
    }

    override val playbackPreferencesStore: PlaybackPreferencesStore by lazy {
        PlaybackPreferencesStore(this, prefsName = "main_activity_test_prefs")
    }

    override val homeChannelSyncManager: HomeChannelSyncManager by lazy {
        HomeChannelSyncManager(
            programSource = object : HomeChannelProgramSource {
                override suspend fun loadPrograms(mode: com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode, limit: Int): List<HomeChannelProgram> {
                    return emptyList()
                }
            },
            preferences = object : HomeChannelPreferences {
                override fun readMode() = com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode.ALL_NEW

                override fun readChannelId(): Long? = null

                override fun writeChannelId(channelId: Long) = Unit

                override fun clearChannelId() = Unit
            },
            publisher = object : HomeChannelPublisher {
                override suspend fun reconcile(
                    mode: com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode,
                    existingChannelId: Long?,
                    programs: List<HomeChannelProgram>,
                ): HomeChannelPublisherResult {
                    return HomeChannelPublisherResult(channelId = existingChannelId ?: 1L)
                }

                override suspend fun deleteChannel(channelId: Long) = Unit
            },
            onSyncFailure = {},
        )
    }

    companion object {
        @Volatile
        var homeChannelWorkManager: WorkManager? = null

        @Volatile
        var appUpdateWorkManager: WorkManager? = null
    }
}
