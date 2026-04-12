package com.kraat.lostfilmnewtv.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Operation
import androidx.work.WorkManager
import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundScheduler
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPreferences
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelProgram
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelProgramSource
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPublisher
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPublisherResult
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelSyncManager
import com.kraat.lostfilmnewtv.updates.AppUpdateAvailabilityStore
import com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundScheduler
import com.kraat.lostfilmnewtv.updates.AppUpdateCoordinator
import com.kraat.lostfilmnewtv.updates.AppUpdateInfo
import com.kraat.lostfilmnewtv.updates.AppUpdateRefreshResult
import com.kraat.lostfilmnewtv.updates.ReleaseApkLauncher
import com.kraat.lostfilmnewtv.updates.SavedAppUpdate
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun manualMode_onScreenShown_doesNotRefreshButStillShowsSavedUpdate() = runTest(dispatcher) {
        val savedUpdateState = MutableStateFlow<SavedAppUpdate?>(
            SavedAppUpdate(
                latestVersion = "1.1.0",
                apkUrl = "https://example.test/app-1.1.0.apk",
            ),
        )
        val refresher = FakeUpdateRefresher()
        val viewModel = createViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.MANUAL,
            savedUpdateState = savedUpdateState,
            refreshSavedUpdateState = refresher::invoke,
            ioDispatcher = dispatcher,
        )

        advanceUntilIdle()
        viewModel.onScreenShown()
        advanceUntilIdle()

        assertEquals(0, refresher.callCount)
        assertEquals(savedUpdateState.value, viewModel.uiState.value.savedAppUpdate)
        assertEquals("https://example.test/app-1.1.0.apk", viewModel.uiState.value.installUrl)
    }

    @Test
    fun quietMode_onScreenShown_runsAutoCheck() = runTest(dispatcher) {
        val refresher = FakeUpdateRefresher().apply {
            enqueue(AppUpdateRefreshResult.UpToDate(installedVersion = "1.0.0"))
        }
        val viewModel = createViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.QUIET_CHECK,
            savedUpdateState = MutableStateFlow(null),
            refreshSavedUpdateState = refresher::invoke,
            ioDispatcher = dispatcher,
        )

        viewModel.onScreenShown()
        advanceUntilIdle()

        assertEquals(1, refresher.callCount)
        assertEquals("1.0.0", viewModel.uiState.value.installedVersionText)
        assertEquals("1.0.0", viewModel.uiState.value.latestVersionText)
        assertEquals("Установлена последняя версия", viewModel.uiState.value.statusText)
    }

    @Test
    fun onCheckForUpdatesClick_emitsUpdateAvailableState() = runTest(dispatcher) {
        val refresher = FakeUpdateRefresher().apply {
            enqueue(
                AppUpdateRefreshResult.UpdateSaved(
                    savedUpdate = SavedAppUpdate(
                        latestVersion = "1.1.0",
                        apkUrl = "https://example.test/app-1.1.0.apk",
                    ),
                ),
            )
        }
        val viewModel = createViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.MANUAL,
            savedUpdateState = MutableStateFlow(null),
            refreshSavedUpdateState = refresher::invoke,
            ioDispatcher = dispatcher,
        )

        viewModel.onCheckForUpdatesClick()
        advanceUntilIdle()

        assertEquals(1, refresher.callCount)
        assertEquals("1.1.0", viewModel.uiState.value.latestVersionText)
        assertEquals("Доступно обновление", viewModel.uiState.value.statusText)
        assertEquals("https://example.test/app-1.1.0.apk", viewModel.uiState.value.installUrl)
    }

    @Test
    fun onCheckForUpdatesClick_showsLoadingState_andKeepsSavedInstallActionWhenAlreadyPresent() = runTest(dispatcher) {
        val savedUpdate = SavedAppUpdate(
            latestVersion = "1.1.0",
            apkUrl = "https://example.test/app-1.1.0.apk",
        )
        val refresher = FakeUpdateRefresher()
        refresher.enqueueDeferred()
        val viewModel = createViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.MANUAL,
            savedUpdateState = MutableStateFlow(savedUpdate),
            refreshSavedUpdateState = refresher::invoke,
            ioDispatcher = dispatcher,
        )

        advanceUntilIdle()
        viewModel.onCheckForUpdatesClick()
        runCurrent()

        assertEquals(1, refresher.callCount)
        assertEquals("Проверяем обновления...", viewModel.uiState.value.statusText)
        assertEquals(true, viewModel.uiState.value.isCheckingForUpdates)
        assertEquals(savedUpdate, viewModel.uiState.value.savedAppUpdate)
        assertEquals(savedUpdate.apkUrl, viewModel.uiState.value.installUrl)
    }

    @Test
    fun onUpdateModeSelected_persistsMode_triggersRefreshWhenQuiet_andSyncsBackgroundSchedule() = runTest(dispatcher) {
        val persistedModes = mutableListOf<UpdateCheckMode>()
        var scheduleCalls = 0
        val refresher = FakeUpdateRefresher().apply {
            enqueue(AppUpdateRefreshResult.UpToDate(installedVersion = "1.0.0"))
        }
        val viewModel = createViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.MANUAL,
            persistUpdateMode = { persistedModes += it },
            savedUpdateState = MutableStateFlow(null),
            refreshSavedUpdateState = refresher::invoke,
            syncAppUpdateBackgroundSchedule = { scheduleCalls += 1 },
            ioDispatcher = dispatcher,
        )

        viewModel.onUpdateModeSelected(UpdateCheckMode.QUIET_CHECK)
        advanceUntilIdle()

        assertEquals(UpdateCheckMode.QUIET_CHECK, checkNotNull(lastSettingsPreferencesStore).readUpdateCheckMode())
        assertEquals(UpdateCheckMode.QUIET_CHECK, viewModel.uiState.value.updateMode)
        assertEquals(1, refresher.callCount)
        assertEquals(1, lastAppUpdateScheduleCallCount)
    }

    @Test
    fun onUpdateModeSelected_sameMode_doesNotPersistAgainTriggerRefreshOrReschedule() = runTest(dispatcher) {
        val persistedModes = mutableListOf<UpdateCheckMode>()
        var scheduleCalls = 0
        val refresher = FakeUpdateRefresher()
        val viewModel = createViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.QUIET_CHECK,
            persistUpdateMode = { persistedModes += it },
            savedUpdateState = MutableStateFlow(null),
            refreshSavedUpdateState = refresher::invoke,
            syncAppUpdateBackgroundSchedule = { scheduleCalls += 1 },
            ioDispatcher = dispatcher,
        )

        viewModel.onUpdateModeSelected(UpdateCheckMode.QUIET_CHECK)
        advanceUntilIdle()

        assertEquals(UpdateCheckMode.QUIET_CHECK, checkNotNull(lastSettingsPreferencesStore).readUpdateCheckMode())
        assertEquals(0, refresher.callCount)
        assertEquals(0, lastAppUpdateScheduleCallCount)
        assertEquals(UpdateCheckMode.QUIET_CHECK, viewModel.uiState.value.updateMode)
    }

    @Test
    fun onUpdateModeSelected_switchingBackToManual_persistsAndReschedulesWithoutRefresh() = runTest(dispatcher) {
        val persistedModes = mutableListOf<UpdateCheckMode>()
        var scheduleCalls = 0
        val refresher = FakeUpdateRefresher()
        val viewModel = createViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.QUIET_CHECK,
            persistUpdateMode = { persistedModes += it },
            savedUpdateState = MutableStateFlow(null),
            refreshSavedUpdateState = refresher::invoke,
            syncAppUpdateBackgroundSchedule = { scheduleCalls += 1 },
            ioDispatcher = dispatcher,
        )

        viewModel.onUpdateModeSelected(UpdateCheckMode.MANUAL)
        advanceUntilIdle()

        assertEquals(UpdateCheckMode.MANUAL, checkNotNull(lastSettingsPreferencesStore).readUpdateCheckMode())
        assertEquals(UpdateCheckMode.MANUAL, viewModel.uiState.value.updateMode)
        assertEquals(0, refresher.callCount)
        assertEquals(2, lastAppUpdateScheduleCallCount)
    }

    @Test
    fun refreshError_keepsExistingInstallUrl_whenSavedUpdateWasAlreadyPresent() = runTest(dispatcher) {
        val savedUpdate = SavedAppUpdate(
            latestVersion = "1.1.0",
            apkUrl = "https://example.test/app-1.1.0.apk",
        )
        val refresher = FakeUpdateRefresher().apply {
            enqueue(
                AppUpdateRefreshResult.FailedKeptPrevious(
                    installedVersion = "1.0.0",
                    message = "Не удалось проверить обновления.",
                ),
            )
        }
        val viewModel = createViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.MANUAL,
            savedUpdateState = MutableStateFlow(savedUpdate),
            refreshSavedUpdateState = refresher::invoke,
            ioDispatcher = dispatcher,
        )

        advanceUntilIdle()
        viewModel.onCheckForUpdatesClick()
        advanceUntilIdle()

        assertEquals("Не удалось проверить обновления.", viewModel.uiState.value.statusText)
        assertEquals(savedUpdate, viewModel.uiState.value.savedAppUpdate)
        assertEquals(savedUpdate.apkUrl, viewModel.uiState.value.installUrl)
    }

    @Test
    fun onHomeFavoritesRailVisibilitySelected_persistsState_andNotifiesCaller() = runTest(dispatcher) {
        val persistedValues = mutableListOf<Boolean>()
        val notifiedValues = mutableListOf<Boolean>()
        val viewModel = createViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.MANUAL,
            initialHomeFavoritesRailEnabled = false,
            persistHomeFavoritesRailEnabled = { persistedValues += it },
            onHomeFavoritesRailVisibilityChanged = { notifiedValues += it },
            savedUpdateState = MutableStateFlow(null),
            refreshSavedUpdateState = FakeUpdateRefresher()::invoke,
            ioDispatcher = dispatcher,
        )

        viewModel.onHomeFavoritesRailVisibilitySelected(true)
        advanceUntilIdle()

        assertEquals(true, checkNotNull(lastSettingsPreferencesStore).readHomeFavoritesRailEnabled())
        assertEquals(listOf(true), notifiedValues)
        assertEquals(true, viewModel.uiState.value.isHomeFavoritesRailEnabled)
    }

    @Test
    fun overlappingRefreshes_doNotLetOlderResultOverwriteLatestState() = runTest(dispatcher) {
        val refresher = FakeUpdateRefresher()
        val firstResult = refresher.enqueueDeferred()
        val secondResult = refresher.enqueueDeferred()
        val viewModel = createViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.MANUAL,
            savedUpdateState = MutableStateFlow(null),
            refreshSavedUpdateState = refresher::invoke,
            ioDispatcher = dispatcher,
            debounceIntervalMs = 0,
        )

        viewModel.onCheckForUpdatesClick()
        runCurrent()
        viewModel.onCheckForUpdatesClick()
        runCurrent()

        secondResult.complete(
            AppUpdateRefreshResult.UpdateSaved(
                savedUpdate = SavedAppUpdate(
                    latestVersion = "1.2.0",
                    apkUrl = "https://example.test/app-1.2.0.apk",
                ),
            ),
        )
        advanceUntilIdle()

        firstResult.complete(
            AppUpdateRefreshResult.FailedEmpty(
                installedVersion = "1.0.0",
                message = "stale result",
            ),
        )
        advanceUntilIdle()

        assertEquals(2, refresher.callCount)
        assertEquals("1.2.0", viewModel.uiState.value.latestVersionText)
        assertEquals("Доступно обновление", viewModel.uiState.value.statusText)
        assertEquals("https://example.test/app-1.2.0.apk", viewModel.uiState.value.installUrl)
    }

    @Test
    fun onChannelModeSelected_activeMode_persistsSyncsAndSchedules() = runTest(dispatcher) {
        val persistedModes = mutableListOf<AndroidTvChannelMode>()
        var syncCalls = 0
        var scheduleCalls = 0
        val viewModel = createViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.MANUAL,
            initialChannelMode = AndroidTvChannelMode.ALL_NEW,
            persistChannelMode = { persistedModes += it },
            savedUpdateState = MutableStateFlow(null),
            refreshSavedUpdateState = { AppUpdateRefreshResult.UpToDate(installedVersion = "1.0.0") },
            syncAndroidTvChannelBackgroundSchedule = { scheduleCalls += 1 },
            syncAndroidTvChannel = { syncCalls += 1 },
            ioDispatcher = dispatcher,
        )

        viewModel.onChannelModeSelected(AndroidTvChannelMode.UNWATCHED)
        advanceUntilIdle()

        assertEquals(AndroidTvChannelMode.UNWATCHED, checkNotNull(lastSettingsPreferencesStore).readAndroidTvChannelMode())
        assertEquals(AndroidTvChannelMode.UNWATCHED, viewModel.uiState.value.channelMode)
        assertEquals(1, lastHomeChannelScheduleCallCount)
        assertEquals(1, lastHomeChannelSyncCallCount)
    }

    @Test
    fun onChannelModeSelected_disabledMode_persistsSyncsAndCancelsSchedule() = runTest(dispatcher) {
        val persistedModes = mutableListOf<AndroidTvChannelMode>()
        var syncCalls = 0
        var scheduleCalls = 0
        val viewModel = createViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.MANUAL,
            initialChannelMode = AndroidTvChannelMode.ALL_NEW,
            persistChannelMode = { persistedModes += it },
            savedUpdateState = MutableStateFlow(null),
            refreshSavedUpdateState = { AppUpdateRefreshResult.UpToDate(installedVersion = "1.0.0") },
            syncAndroidTvChannelBackgroundSchedule = { scheduleCalls += 1 },
            syncAndroidTvChannel = { syncCalls += 1 },
            ioDispatcher = dispatcher,
        )

        viewModel.onChannelModeSelected(AndroidTvChannelMode.DISABLED)
        advanceUntilIdle()

        assertEquals(AndroidTvChannelMode.DISABLED, checkNotNull(lastSettingsPreferencesStore).readAndroidTvChannelMode())
        assertEquals(AndroidTvChannelMode.DISABLED, viewModel.uiState.value.channelMode)
        assertEquals(1, lastHomeChannelScheduleCallCount)
        assertEquals(1, lastHomeChannelSyncCallCount)
    }

    @Test
    fun onChannelModeSelected_sameMode_doesNotPersistSyncOrScheduleAgain() = runTest(dispatcher) {
        val persistedModes = mutableListOf<AndroidTvChannelMode>()
        var syncCalls = 0
        var scheduleCalls = 0
        val viewModel = createViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.MANUAL,
            initialChannelMode = AndroidTvChannelMode.DISABLED,
            persistChannelMode = { persistedModes += it },
            savedUpdateState = MutableStateFlow(null),
            refreshSavedUpdateState = { AppUpdateRefreshResult.UpToDate(installedVersion = "1.0.0") },
            syncAndroidTvChannelBackgroundSchedule = { scheduleCalls += 1 },
            syncAndroidTvChannel = { syncCalls += 1 },
            ioDispatcher = dispatcher,
        )

        viewModel.onChannelModeSelected(AndroidTvChannelMode.DISABLED)
        advanceUntilIdle()

        assertEquals(AndroidTvChannelMode.DISABLED, checkNotNull(lastSettingsPreferencesStore).readAndroidTvChannelMode())
        assertEquals(AndroidTvChannelMode.DISABLED, viewModel.uiState.value.channelMode)
        assertEquals(0, lastHomeChannelScheduleCallCount)
        assertEquals(0, lastHomeChannelSyncCallCount)
    }

    @Test
    fun onPlaybackQualitySelected_persistsQualityAndUpdatesState() = runTest(dispatcher) {
        val persistedQualities = mutableListOf<PlaybackQualityPreference>()
        val viewModel = createViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.MANUAL,
            persistPlaybackQuality = { persistedQualities += it },
            savedUpdateState = MutableStateFlow(null),
            refreshSavedUpdateState = { AppUpdateRefreshResult.UpToDate(installedVersion = "1.0.0") },
            ioDispatcher = dispatcher,
        )

        viewModel.onPlaybackQualitySelected(PlaybackQualityPreference.Q720)

        assertEquals(PlaybackQualityPreference.Q720, checkNotNull(lastSettingsPreferencesStore).readDefaultQuality())
        assertEquals(PlaybackQualityPreference.Q720, viewModel.uiState.value.playbackQuality)
    }

    @Test
    fun onInstallUpdateFailed_showsUserFacingErrorMessage() = runTest(dispatcher) {
        val viewModel = createViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.MANUAL,
            savedUpdateState = MutableStateFlow(null),
            refreshSavedUpdateState = { AppUpdateRefreshResult.UpToDate(installedVersion = "1.0.0") },
            ioDispatcher = dispatcher,
        )

        viewModel.onInstallUpdateFailed()

        assertEquals("Не удалось открыть обновление.", viewModel.uiState.value.statusText)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun createViewModel(
        installedVersion: String,
        initialPlaybackQuality: PlaybackQualityPreference,
        initialUpdateMode: UpdateCheckMode,
        initialChannelMode: AndroidTvChannelMode = AndroidTvChannelMode.ALL_NEW,
        initialHomeFavoritesRailEnabled: Boolean = false,
        persistUpdateMode: (UpdateCheckMode) -> Unit = {},
        persistHomeFavoritesRailEnabled: (Boolean) -> Unit = {},
        onHomeFavoritesRailVisibilityChanged: (Boolean) -> Unit = {},
        persistChannelMode: (AndroidTvChannelMode) -> Unit = {},
        persistPlaybackQuality: (PlaybackQualityPreference) -> Unit = {},
        savedUpdateState: MutableStateFlow<SavedAppUpdate?> = MutableStateFlow(null),
        refreshSavedUpdateState: suspend () -> AppUpdateRefreshResult,
        syncAppUpdateBackgroundSchedule: () -> Unit = {},
        syncAndroidTvChannelBackgroundSchedule: () -> Unit = {},
        syncAndroidTvChannel: () -> Unit = {},
        ioDispatcher: CoroutineDispatcher,
        debounceIntervalMs: Long = 1000L,
    ): SettingsViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "settings-view-model-test-${settingsPrefsCounter++}"
        context.deleteSharedPreferences(prefsName)
        val preferencesStore = PlaybackPreferencesStore(context, prefsName = prefsName).also {
            it.writeDefaultQuality(initialPlaybackQuality)
            it.writeUpdateCheckMode(initialUpdateMode)
            it.writeAndroidTvChannelMode(initialChannelMode)
            it.writeHomeFavoritesRailEnabled(initialHomeFavoritesRailEnabled)
        }
        lastSettingsPreferencesStore = preferencesStore

        val savedUpdatePrefsName = "settings-view-model-updates-${settingsUpdatePrefsCounter++}"
        context.deleteSharedPreferences(savedUpdatePrefsName)
        val updateStore = AppUpdateAvailabilityStore(context, prefsName = savedUpdatePrefsName)
        savedUpdateState.value?.let(updateStore::writeSavedUpdate)

        lastAppUpdateScheduleCallCount = 0
        val appUpdateWorkManager = recordingWorkManager(
            onInvocation = {
                lastAppUpdateScheduleCallCount += 1
                syncAppUpdateBackgroundSchedule()
            },
        )
        val appUpdateBackgroundScheduler = AppUpdateBackgroundScheduler(
            readMode = preferencesStore::readUpdateCheckMode,
            workManager = appUpdateWorkManager,
        )

        lastHomeChannelScheduleCallCount = 0
        val homeChannelWorkManager = recordingWorkManager(
            onInvocation = {
                lastHomeChannelScheduleCallCount += 1
                syncAndroidTvChannelBackgroundSchedule()
            },
        )
        val homeChannelBackgroundScheduler = HomeChannelBackgroundScheduler(
            readMode = preferencesStore::readAndroidTvChannelMode,
            workManager = homeChannelWorkManager,
        )

        lastHomeChannelSyncCallCount = 0
        val homeChannelSyncManager = HomeChannelSyncManager(
            programSource = object : HomeChannelProgramSource {
                override suspend fun loadPrograms(mode: AndroidTvChannelMode, limit: Int): List<HomeChannelProgram> {
                    return emptyList()
                }
            },
            preferences = object : HomeChannelPreferences {
                private var channelId: Long? = 1L

                override fun readMode(): AndroidTvChannelMode = preferencesStore.readAndroidTvChannelMode()

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
                ): HomeChannelPublisherResult {
                    lastHomeChannelSyncCallCount += 1
                    syncAndroidTvChannel()
                    return HomeChannelPublisherResult(channelId = existingChannelId ?: 1L)
                }

                override suspend fun deleteChannel(channelId: Long) {
                    lastHomeChannelSyncCallCount += 1
                    syncAndroidTvChannel()
                }
            },
        )

        val appUpdateCoordinator = AppUpdateCoordinator(
            installedVersion = installedVersion,
            store = updateStore,
            checkForUpdates = {
                when (val result = refreshSavedUpdateState()) {
                    is AppUpdateRefreshResult.UpToDate -> AppUpdateInfo.UpToDate(result.installedVersion)
                    is AppUpdateRefreshResult.UpdateSaved -> AppUpdateInfo.UpdateAvailable(
                        installedVersion = installedVersion,
                        latestVersion = result.savedUpdate.latestVersion,
                        apkUrl = result.savedUpdate.apkUrl,
                    )
                    is AppUpdateRefreshResult.FailedKeptPrevious -> AppUpdateInfo.Error(
                        installedVersion = result.installedVersion,
                        message = result.message,
                    )
                    is AppUpdateRefreshResult.FailedEmpty -> AppUpdateInfo.Error(
                        installedVersion = result.installedVersion,
                        message = result.message,
                    )
                }
            },
        )

        val releaseApkLauncher = object : ReleaseApkLauncher(OkHttpClient()) {
            override suspend fun launch(
                context: Context,
                apkUrl: String,
                onDownloadingChange: (Boolean) -> Unit,
                onDownloadProgress: (Int) -> Unit,
            ): Boolean = true
        }

        return SettingsViewModel(
            preferencesStore = preferencesStore,
            appUpdateCoordinator = appUpdateCoordinator,
            homeChannelSyncManager = homeChannelSyncManager,
            homeChannelBackgroundScheduler = homeChannelBackgroundScheduler,
            appUpdateBackgroundScheduler = appUpdateBackgroundScheduler,
            releaseApkLauncher = releaseApkLauncher,
            ioDispatcher = ioDispatcher,
            debounceIntervalMs = debounceIntervalMs,
        ).also { viewModel ->
            viewModel.onHomeFavoritesRailVisibilityChanged = { enabled ->
                persistHomeFavoritesRailEnabled(enabled)
                onHomeFavoritesRailVisibilityChanged(enabled)
            }
        }
    }

    private class FakeUpdateRefresher {
        private val deferredResults = ArrayDeque<CompletableDeferred<AppUpdateRefreshResult>>()
        var callCount: Int = 0
            private set

        fun enqueue(result: AppUpdateRefreshResult) {
            deferredResults += CompletableDeferred(result)
        }

        fun enqueueDeferred(): CompletableDeferred<AppUpdateRefreshResult> {
            return CompletableDeferred<AppUpdateRefreshResult>().also { deferredResults += it }
        }

        suspend operator fun invoke(): AppUpdateRefreshResult {
            callCount += 1
            return checkNotNull(deferredResults.removeFirstOrNull()) {
                "Missing fake refresh result for call $callCount"
            }.await()
        }
    }
}

private var settingsPrefsCounter = 0
private var settingsUpdatePrefsCounter = 0
private var lastSettingsPreferencesStore: PlaybackPreferencesStore? = null
private var lastAppUpdateScheduleCallCount = 0
private var lastHomeChannelScheduleCallCount = 0
private var lastHomeChannelSyncCallCount = 0

private fun recordingWorkManager(onInvocation: () -> Unit): WorkManager {
    val operation = mock(Operation::class.java)
    return mock(WorkManager::class.java) { invocation ->
        when (invocation.method.name) {
            "cancelUniqueWork",
            "enqueueUniquePeriodicWork",
            "enqueueUniqueWork",
            -> {
                onInvocation()
                operation
            }
            "toString" -> "recordingWorkManager"
            "hashCode" -> System.identityHashCode(invocation.mock)
            "equals" -> invocation.arguments.firstOrNull() === invocation.mock
            else -> null
        }
    }
}
