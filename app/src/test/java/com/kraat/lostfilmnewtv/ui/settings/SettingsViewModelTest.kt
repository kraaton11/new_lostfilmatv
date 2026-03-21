package com.kraat.lostfilmnewtv.ui.settings

import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.updates.AppUpdateInfo
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun manualMode_onScreenShown_doesNotAutoCheck() = runTest(dispatcher) {
        val updateChecker = FakeUpdateChecker(
            result = AppUpdateInfo.UpToDate(installedVersion = "1.0.0"),
        )
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
        val updateChecker = FakeUpdateChecker(
            result = AppUpdateInfo.UpToDate(installedVersion = "1.0.0"),
        )
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
        val updateChecker = FakeUpdateChecker(
            result = AppUpdateInfo.UpdateAvailable(
                installedVersion = "1.0.0",
                latestVersion = "1.1.0",
                apkUrl = "https://example.test/app-1.1.0.apk",
            ),
        )
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
        val updateChecker = FakeUpdateChecker(
            result = AppUpdateInfo.UpToDate(installedVersion = "1.0.0"),
        )
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
        private val result: AppUpdateInfo,
    ) {
        var callCount: Int = 0
            private set

        suspend operator fun invoke(): AppUpdateInfo {
            callCount += 1
            return result
        }
    }
}
