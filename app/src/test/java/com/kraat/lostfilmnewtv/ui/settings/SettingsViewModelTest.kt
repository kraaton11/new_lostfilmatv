package com.kraat.lostfilmnewtv.ui.settings

import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.updates.AppUpdateRefreshResult
import com.kraat.lostfilmnewtv.updates.SavedAppUpdate
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun manualMode_onScreenShown_doesNotRefreshButStillShowsSavedUpdate() = runTest(dispatcher) {
        val savedUpdateState = MutableStateFlow(
            SavedAppUpdate(
                latestVersion = "1.1.0",
                apkUrl = "https://example.test/app-1.1.0.apk",
            ),
        )
        val refresher = FakeUpdateRefresher()
        val viewModel = SettingsViewModel(
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
        val viewModel = SettingsViewModel(
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
        val viewModel = SettingsViewModel(
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
        val viewModel = SettingsViewModel(
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
        val viewModel = SettingsViewModel(
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

        assertEquals(listOf(UpdateCheckMode.QUIET_CHECK), persistedModes)
        assertEquals(UpdateCheckMode.QUIET_CHECK, viewModel.uiState.value.updateMode)
        assertEquals(1, refresher.callCount)
        assertEquals(1, scheduleCalls)
    }

    @Test
    fun onUpdateModeSelected_sameMode_doesNotPersistAgainTriggerRefreshOrReschedule() = runTest(dispatcher) {
        val persistedModes = mutableListOf<UpdateCheckMode>()
        var scheduleCalls = 0
        val refresher = FakeUpdateRefresher()
        val viewModel = SettingsViewModel(
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

        assertEquals(emptyList<UpdateCheckMode>(), persistedModes)
        assertEquals(0, refresher.callCount)
        assertEquals(0, scheduleCalls)
        assertEquals(UpdateCheckMode.QUIET_CHECK, viewModel.uiState.value.updateMode)
    }

    @Test
    fun onUpdateModeSelected_switchingBackToManual_persistsAndReschedulesWithoutRefresh() = runTest(dispatcher) {
        val persistedModes = mutableListOf<UpdateCheckMode>()
        var scheduleCalls = 0
        val refresher = FakeUpdateRefresher()
        val viewModel = SettingsViewModel(
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

        assertEquals(listOf(UpdateCheckMode.MANUAL), persistedModes)
        assertEquals(UpdateCheckMode.MANUAL, viewModel.uiState.value.updateMode)
        assertEquals(0, refresher.callCount)
        assertEquals(1, scheduleCalls)
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
        val viewModel = SettingsViewModel(
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
        val viewModel = SettingsViewModel(
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

        assertEquals(listOf(true), persistedValues)
        assertEquals(listOf(true), notifiedValues)
        assertEquals(true, viewModel.uiState.value.isHomeFavoritesRailEnabled)
    }

    @Test
    fun overlappingRefreshes_doNotLetOlderResultOverwriteLatestState() = runTest(dispatcher) {
        val refresher = FakeUpdateRefresher()
        val firstResult = refresher.enqueueDeferred()
        val secondResult = refresher.enqueueDeferred()
        val viewModel = SettingsViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.MANUAL,
            savedUpdateState = MutableStateFlow(null),
            refreshSavedUpdateState = refresher::invoke,
            ioDispatcher = dispatcher,
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
        val viewModel = SettingsViewModel(
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

        assertEquals(listOf(AndroidTvChannelMode.UNWATCHED), persistedModes)
        assertEquals(AndroidTvChannelMode.UNWATCHED, viewModel.uiState.value.channelMode)
        assertEquals(1, scheduleCalls)
        assertEquals(1, syncCalls)
    }

    @Test
    fun onChannelModeSelected_disabledMode_persistsSyncsAndCancelsSchedule() = runTest(dispatcher) {
        val persistedModes = mutableListOf<AndroidTvChannelMode>()
        var syncCalls = 0
        var scheduleCalls = 0
        val viewModel = SettingsViewModel(
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

        assertEquals(listOf(AndroidTvChannelMode.DISABLED), persistedModes)
        assertEquals(AndroidTvChannelMode.DISABLED, viewModel.uiState.value.channelMode)
        assertEquals(1, scheduleCalls)
        assertEquals(1, syncCalls)
    }

    @Test
    fun onChannelModeSelected_sameMode_doesNotPersistSyncOrScheduleAgain() = runTest(dispatcher) {
        val persistedModes = mutableListOf<AndroidTvChannelMode>()
        var syncCalls = 0
        var scheduleCalls = 0
        val viewModel = SettingsViewModel(
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

        assertEquals(emptyList<AndroidTvChannelMode>(), persistedModes)
        assertEquals(AndroidTvChannelMode.DISABLED, viewModel.uiState.value.channelMode)
        assertEquals(0, scheduleCalls)
        assertEquals(0, syncCalls)
    }

    @Test
    fun onPlaybackQualitySelected_persistsQualityAndUpdatesState() = runTest(dispatcher) {
        val persistedQualities = mutableListOf<PlaybackQualityPreference>()
        val viewModel = SettingsViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.MANUAL,
            persistPlaybackQuality = { persistedQualities += it },
            savedUpdateState = MutableStateFlow(null),
            refreshSavedUpdateState = { AppUpdateRefreshResult.UpToDate(installedVersion = "1.0.0") },
            ioDispatcher = dispatcher,
        )

        viewModel.onPlaybackQualitySelected(PlaybackQualityPreference.Q720)

        assertEquals(listOf(PlaybackQualityPreference.Q720), persistedQualities)
        assertEquals(PlaybackQualityPreference.Q720, viewModel.uiState.value.playbackQuality)
    }

    @Test
    fun onInstallUpdateFailed_showsUserFacingErrorMessage() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(
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
