package com.kraat.lostfilmnewtv.ui.settings

import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.updates.AppUpdateInfo
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun manualMode_onScreenShown_doesNotAutoCheck() = runTest(dispatcher) {
        val updateChecker = FakeUpdateChecker().apply {
            enqueue(AppUpdateInfo.UpToDate(installedVersion = "1.0.0"))
        }
        val viewModel = SettingsViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.MANUAL,
            checkForUpdates = updateChecker::invoke,
            ioDispatcher = dispatcher,
        )

        viewModel.onScreenShown()
        advanceUntilIdle()

        assertEquals(0, updateChecker.callCount)
        assertNull(viewModel.uiState.value.latestVersionText)
        assertNull(viewModel.uiState.value.installUrl)
    }

    @Test
    fun quietMode_onScreenShown_runsAutoCheck() = runTest(dispatcher) {
        val updateChecker = FakeUpdateChecker().apply {
            enqueue(AppUpdateInfo.UpToDate(installedVersion = "1.0.0"))
        }
        val viewModel = SettingsViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.QUIET_CHECK,
            checkForUpdates = updateChecker::invoke,
            ioDispatcher = dispatcher,
        )

        viewModel.onScreenShown()
        advanceUntilIdle()

        assertEquals(1, updateChecker.callCount)
        assertEquals("1.0.0", viewModel.uiState.value.installedVersionText)
        assertEquals("Установлена последняя версия", viewModel.uiState.value.statusText)
    }

    @Test
    fun onCheckForUpdatesClick_emitsUpdateAvailableState() = runTest(dispatcher) {
        val updateChecker = FakeUpdateChecker().apply {
            enqueue(AppUpdateInfo.UpdateAvailable(
                installedVersion = "1.0.0",
                latestVersion = "1.1.0",
                apkUrl = "https://example.test/app-1.1.0.apk",
            ))
        }
        val viewModel = SettingsViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.MANUAL,
            checkForUpdates = updateChecker::invoke,
            ioDispatcher = dispatcher,
        )

        viewModel.onCheckForUpdatesClick()
        advanceUntilIdle()

        assertEquals(1, updateChecker.callCount)
        assertEquals("1.1.0", viewModel.uiState.value.latestVersionText)
        assertEquals("Доступно обновление", viewModel.uiState.value.statusText)
        assertEquals("https://example.test/app-1.1.0.apk", viewModel.uiState.value.installUrl)
    }

    @Test
    fun onUpdateModeSelected_persistsMode_andTriggersCheckWhenQuiet() = runTest(dispatcher) {
        val persistedModes = mutableListOf<UpdateCheckMode>()
        val updateChecker = FakeUpdateChecker()
        updateChecker.enqueue(AppUpdateInfo.UpToDate(installedVersion = "1.0.0"))
        val viewModel = SettingsViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.MANUAL,
            persistUpdateMode = { persistedModes += it },
            checkForUpdates = updateChecker::invoke,
            ioDispatcher = dispatcher,
        )

        viewModel.onUpdateModeSelected(UpdateCheckMode.QUIET_CHECK)
        advanceUntilIdle()

        assertEquals(listOf(UpdateCheckMode.QUIET_CHECK), persistedModes)
        assertEquals(UpdateCheckMode.QUIET_CHECK, viewModel.uiState.value.updateMode)
        assertEquals(1, updateChecker.callCount)
    }

    @Test
    fun onUpdateModeSelected_sameMode_doesNotPersistAgainOrTriggerCheck() = runTest(dispatcher) {
        val persistedModes = mutableListOf<UpdateCheckMode>()
        val updateChecker = FakeUpdateChecker()
        val viewModel = SettingsViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.QUIET_CHECK,
            persistUpdateMode = { persistedModes += it },
            checkForUpdates = updateChecker::invoke,
            ioDispatcher = dispatcher,
        )

        viewModel.onUpdateModeSelected(UpdateCheckMode.QUIET_CHECK)
        advanceUntilIdle()

        assertEquals(emptyList<UpdateCheckMode>(), persistedModes)
        assertEquals(0, updateChecker.callCount)
        assertEquals(UpdateCheckMode.QUIET_CHECK, viewModel.uiState.value.updateMode)
    }

    @Test
    fun overlappingChecks_doNotLetOlderResultOverwriteLatestState() = runTest(dispatcher) {
        val updateChecker = FakeUpdateChecker()
        val firstResult = updateChecker.enqueueDeferred()
        val secondResult = updateChecker.enqueueDeferred()
        val viewModel = SettingsViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.MANUAL,
            checkForUpdates = updateChecker::invoke,
            ioDispatcher = dispatcher,
        )

        viewModel.onCheckForUpdatesClick()
        runCurrent()
        viewModel.onCheckForUpdatesClick()
        runCurrent()

        secondResult.complete(
            AppUpdateInfo.UpdateAvailable(
                installedVersion = "1.0.0",
                latestVersion = "1.2.0",
                apkUrl = "https://example.test/app-1.2.0.apk",
            ),
        )
        advanceUntilIdle()

        firstResult.complete(
            AppUpdateInfo.Error(
                installedVersion = "1.0.0",
                message = "stale result",
            ),
        )
        advanceUntilIdle()

        assertEquals(2, updateChecker.callCount)
        assertEquals("1.2.0", viewModel.uiState.value.latestVersionText)
        assertEquals("Доступно обновление", viewModel.uiState.value.statusText)
        assertEquals("https://example.test/app-1.2.0.apk", viewModel.uiState.value.installUrl)
    }

    @Test
    fun onPlaybackQualitySelected_persistsQualityAndUpdatesState() = runTest(dispatcher) {
        val persistedQualities = mutableListOf<PlaybackQualityPreference>()
        val viewModel = SettingsViewModel(
            installedVersion = "1.0.0",
            initialPlaybackQuality = PlaybackQualityPreference.Q1080,
            initialUpdateMode = UpdateCheckMode.MANUAL,
            persistPlaybackQuality = { persistedQualities += it },
            checkForUpdates = { AppUpdateInfo.UpToDate(installedVersion = "1.0.0") },
            ioDispatcher = dispatcher,
        )

        viewModel.onPlaybackQualitySelected(PlaybackQualityPreference.Q720)

        assertEquals(listOf(PlaybackQualityPreference.Q720), persistedQualities)
        assertEquals(PlaybackQualityPreference.Q720, viewModel.uiState.value.playbackQuality)
    }

    private class FakeUpdateChecker(
    ) {
        private val deferredResults = ArrayDeque<CompletableDeferred<AppUpdateInfo>>()
        var callCount: Int = 0
            private set

        fun enqueue(result: AppUpdateInfo) {
            deferredResults += CompletableDeferred(result)
        }

        fun enqueueDeferred(): CompletableDeferred<AppUpdateInfo> {
            return CompletableDeferred<AppUpdateInfo>().also { deferredResults += it }
        }

        suspend operator fun invoke(): AppUpdateInfo {
            callCount += 1
            return checkNotNull(deferredResults.removeFirstOrNull()) {
                "Missing fake result for update check $callCount"
            }.await()
        }
    }
}
