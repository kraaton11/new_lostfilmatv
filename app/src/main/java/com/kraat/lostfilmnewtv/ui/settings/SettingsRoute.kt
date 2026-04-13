package com.kraat.lostfilmnewtv.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
    isAuthenticated: Boolean = false,
    onAuthClick: () -> Unit = {},
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.onScreenShown() }

    SettingsScreen(
        selectedQuality = state.value.playbackQuality,
        onQualitySelected = viewModel::onPlaybackQualitySelected,
        selectedUpdateMode = state.value.updateMode,
        selectedChannelMode = state.value.channelMode,
        isHomeFavoritesRailEnabled = state.value.isHomeFavoritesRailEnabled,
        selectedWatchedMarkingMode = state.value.watchedMarkingMode,
        onWatchedMarkingModeSelected = viewModel::onWatchedMarkingModeSelected,
        isAuthenticated = isAuthenticated,
        onAuthClick = onAuthClick,
        installedVersionText = state.value.installedVersionText,
        latestVersionText = state.value.savedAppUpdate?.latestVersion ?: state.value.latestVersionText,
        statusText = state.value.statusText,
        isCheckingForUpdates = state.value.isCheckingForUpdates,
        isDownloadingUpdate = state.value.isDownloadingUpdate,
        installUrl = state.value.installUrl,
        onUpdateModeSelected = viewModel::onUpdateModeSelected,
        onChannelModeSelected = viewModel::onChannelModeSelected,
        onHomeFavoritesRailVisibilitySelected = viewModel::onHomeFavoritesRailVisibilitySelected,
        onCheckForUpdatesClick = viewModel::onCheckForUpdatesClick,
        onInstallUpdateClick = {
            state.value.installUrl?.let { url ->
                scope.launch { viewModel.installUpdate(context, url) }
            }
        },
    )
}
