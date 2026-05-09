package com.kraat.lostfilmnewtv.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
    initialSection: String? = null,
    isAuthenticated: Boolean = false,
    onAuthClick: () -> Unit = {},
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onScreenShown() }
    LaunchedEffect(initialSection) {
        if (initialSection != null) viewModel.onDeepLinkSection(initialSection)
    }

    SettingsScreen(
        currentSection = state.value.currentSection,
        onSectionSelected = viewModel::onSectionSelected,
        onSectionBack = viewModel::onSectionBack,
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
                viewModel.installUpdate(url)
            }
        },
    )
}
