package com.kraat.lostfilmnewtv.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kraat.lostfilmnewtv.BuildConfig
import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import com.kraat.lostfilmnewtv.updates.AppUpdateRepository

@Composable
fun SettingsRoute(
    playbackPreferencesStore: PlaybackPreferencesStore,
    appUpdateRepository: AppUpdateRepository,
    installedVersion: String = BuildConfig.VERSION_NAME,
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

    LaunchedEffect(Unit) {
        settingsViewModel.onScreenShown()
    }

    SettingsScreen(
        selectedQuality = state.value.playbackQuality,
        onQualitySelected = settingsViewModel::onPlaybackQualitySelected,
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
