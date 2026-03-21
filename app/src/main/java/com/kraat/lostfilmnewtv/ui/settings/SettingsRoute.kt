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
import com.kraat.lostfilmnewtv.updates.AppUpdateRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(
    playbackPreferencesStore: PlaybackPreferencesStore,
    appUpdateRepository: AppUpdateRepository,
    onPlaybackQualityChanged: (PlaybackQualityPreference) -> Unit = {},
    installedVersion: String = BuildConfig.VERSION_NAME,
    openInstallApk: suspend (Context, String) -> Boolean = { _, _ -> false },
) {
    val settingsViewModel: SettingsViewModel = viewModel(
        key = "settings",
        factory = settingsViewModelFactory(
            playbackPreferencesStore = playbackPreferencesStore,
            appUpdateRepository = appUpdateRepository,
            installedVersion = installedVersion,
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
        installedVersionText = state.value.installedVersionText,
        latestVersionText = state.value.latestVersionText,
        statusText = state.value.statusText,
        installUrl = state.value.installUrl,
        onUpdateModeSelected = settingsViewModel::onUpdateModeSelected,
        onCheckForUpdatesClick = settingsViewModel::onCheckForUpdatesClick,
        onInstallUpdateClick = {
            state.value.installUrl?.let { installUrl ->
                scope.launch {
                    openInstallApk(context, installUrl)
                }
            }
        },
    )
}

private fun settingsViewModelFactory(
    playbackPreferencesStore: PlaybackPreferencesStore,
    appUpdateRepository: AppUpdateRepository,
    installedVersion: String,
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(
                installedVersion = installedVersion,
                initialPlaybackQuality = playbackPreferencesStore.readDefaultQuality(),
                initialUpdateMode = playbackPreferencesStore.readUpdateCheckMode(),
                persistPlaybackQuality = playbackPreferencesStore::writeDefaultQuality,
                persistUpdateMode = playbackPreferencesStore::writeUpdateCheckMode,
                checkForUpdates = appUpdateRepository::checkForUpdate,
            ) as T
        }
    }
}
