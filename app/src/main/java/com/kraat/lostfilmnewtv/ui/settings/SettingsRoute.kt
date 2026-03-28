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
    isAuthenticated: Boolean = false,
    onAuthClick: () -> Unit = {},
    onPlaybackQualityChanged: (PlaybackQualityPreference) -> Unit = {},
    onHomeFavoritesRailVisibilityChanged: (Boolean) -> Unit = {},
    syncAppUpdateBackgroundSchedule: () -> Unit = {},
    syncAndroidTvChannelBackgroundSchedule: () -> Unit = {},
    syncAndroidTvChannel: suspend () -> Unit = {},
    installedVersion: String = BuildConfig.VERSION_NAME,
    openInstallApk: suspend (Context, String, (Boolean) -> Unit) -> Boolean = { _, _, _ -> false },
) {
    val settingsViewModel: SettingsViewModel = viewModel(
        key = "settings",
        factory = settingsViewModelFactory(
            playbackPreferencesStore = playbackPreferencesStore,
            appUpdateCoordinator = appUpdateCoordinator,
            installedVersion = installedVersion,
            onHomeFavoritesRailVisibilityChanged = onHomeFavoritesRailVisibilityChanged,
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
        isHomeFavoritesRailEnabled = state.value.isHomeFavoritesRailEnabled,
        isAuthenticated = isAuthenticated,
        onAuthClick = onAuthClick,
        installedVersionText = state.value.installedVersionText,
        latestVersionText = state.value.savedAppUpdate?.latestVersion ?: state.value.latestVersionText,
        statusText = state.value.statusText,
        isCheckingForUpdates = state.value.isCheckingForUpdates,
        isDownloadingUpdate = state.value.isDownloadingUpdate,
        installUrl = state.value.installUrl,
        onUpdateModeSelected = settingsViewModel::onUpdateModeSelected,
        onChannelModeSelected = settingsViewModel::onChannelModeSelected,
        onHomeFavoritesRailVisibilitySelected = settingsViewModel::onHomeFavoritesRailVisibilitySelected,
        onCheckForUpdatesClick = settingsViewModel::onCheckForUpdatesClick,
        onInstallUpdateClick = {
            state.value.installUrl?.let { installUrl ->
                scope.launch {
                    val opened = openInstallApk(context, installUrl) { downloading ->
                        settingsViewModel.onInstallDownloadProgress(downloading)
                    }
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
    onHomeFavoritesRailVisibilityChanged: (Boolean) -> Unit,
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
                initialHomeFavoritesRailEnabled = playbackPreferencesStore.readHomeFavoritesRailEnabled(),
                persistPlaybackQuality = playbackPreferencesStore::writeDefaultQuality,
                persistUpdateMode = playbackPreferencesStore::writeUpdateCheckMode,
                persistChannelMode = playbackPreferencesStore::writeAndroidTvChannelMode,
                persistHomeFavoritesRailEnabled = playbackPreferencesStore::writeHomeFavoritesRailEnabled,
                onHomeFavoritesRailVisibilityChanged = onHomeFavoritesRailVisibilityChanged,
                savedUpdateState = appUpdateCoordinator.savedUpdateState,
                refreshSavedUpdateState = appUpdateCoordinator::refreshSavedUpdateState,
                syncAppUpdateBackgroundSchedule = syncAppUpdateBackgroundSchedule,
                syncAndroidTvChannelBackgroundSchedule = syncAndroidTvChannelBackgroundSchedule,
                syncAndroidTvChannel = syncAndroidTvChannel,
            ) as T
        }
    }
}
