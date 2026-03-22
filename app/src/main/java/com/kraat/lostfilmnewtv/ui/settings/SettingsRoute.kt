package com.kraat.lostfilmnewtv.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kraat.lostfilmnewtv.BuildConfig
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import com.kraat.lostfilmnewtv.updates.AppUpdateCoordinator
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(
    playbackPreferencesStore: PlaybackPreferencesStore,
    appUpdateCoordinator: AppUpdateCoordinator,
    onPlaybackQualityChanged: (PlaybackQualityPreference) -> Unit = {},
    syncAppUpdateBackgroundSchedule: () -> Unit = {},
    syncAndroidTvChannelBackgroundSchedule: () -> Unit = {},
    syncAndroidTvChannel: suspend () -> Unit = {},
    installedVersion: String = BuildConfig.VERSION_NAME,
    openInstallApk: suspend (Context, String) -> Boolean = { _, _ -> false },
) {
    val settingsViewModel: SettingsViewModel = viewModel(
        key = "settings",
        factory = settingsViewModelFactory(
            playbackPreferencesStore = playbackPreferencesStore,
            appUpdateCoordinator = appUpdateCoordinator,
            installedVersion = installedVersion,
            syncAppUpdateBackgroundSchedule = syncAppUpdateBackgroundSchedule,
            syncAndroidTvChannelBackgroundSchedule = syncAndroidTvChannelBackgroundSchedule,
            syncAndroidTvChannel = syncAndroidTvChannel,
        ),
    )
    val state = settingsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        settingsViewModel.onScreenShown()
    }

    SettingsScreen(
        selectedQuality = state.value.playbackQuality,
        onQualitySelected = { quality ->
            settingsViewModel.onPlaybackQualitySelected(quality)
            onPlaybackQualityChanged(quality)
        },
        selectedUpdateMode = state.value.updateMode,
        selectedChannelMode = state.value.channelMode,
        installedVersionText = state.value.installedVersionText,
        latestVersionText = state.value.savedAppUpdate?.latestVersion ?: state.value.latestVersionText,
        statusText = state.value.statusText,
        isCheckingForUpdates = state.value.isCheckingForUpdates,
        installUrl = state.value.installUrl,
        onUpdateModeSelected = settingsViewModel::onUpdateModeSelected,
        onChannelModeSelected = settingsViewModel::onChannelModeSelected,
        onCheckForUpdatesClick = settingsViewModel::onCheckForUpdatesClick,
        onInstallUpdateClick = {
            state.value.installUrl?.let { installUrl ->
                scope.launch {
                    val opened = openInstallApk(context, installUrl)
                    if (!opened) {
                        settingsViewModel.onInstallUpdateFailed()
                    }
                }
            }
        },
    )
}

private fun settingsViewModelFactory(
    playbackPreferencesStore: PlaybackPreferencesStore,
    appUpdateCoordinator: AppUpdateCoordinator,
    installedVersion: String,
    syncAppUpdateBackgroundSchedule: () -> Unit,
    syncAndroidTvChannelBackgroundSchedule: () -> Unit,
    syncAndroidTvChannel: suspend () -> Unit,
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(
                installedVersion = installedVersion,
                initialPlaybackQuality = playbackPreferencesStore.readDefaultQuality(),
                initialUpdateMode = playbackPreferencesStore.readUpdateCheckMode(),
                initialChannelMode = playbackPreferencesStore.readAndroidTvChannelMode(),
                persistPlaybackQuality = playbackPreferencesStore::writeDefaultQuality,
                persistUpdateMode = playbackPreferencesStore::writeUpdateCheckMode,
                persistChannelMode = playbackPreferencesStore::writeAndroidTvChannelMode,
                savedUpdateState = appUpdateCoordinator.savedUpdateState,
                refreshSavedUpdateState = appUpdateCoordinator::refreshSavedUpdateState,
                syncAppUpdateBackgroundSchedule = syncAppUpdateBackgroundSchedule,
                syncAndroidTvChannelBackgroundSchedule = syncAndroidTvChannelBackgroundSchedule,
                syncAndroidTvChannel = syncAndroidTvChannel,
            ) as T
        }
    }
}
