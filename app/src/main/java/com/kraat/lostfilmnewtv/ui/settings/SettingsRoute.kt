package com.kraat.lostfilmnewtv.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kraat.lostfilmnewtv.data.parser.LostFilmBaseUrl

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
    initialSection: String? = null,
    isAuthenticated: Boolean = false,
    onAuthClick: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onScreenShown() }
    LaunchedEffect(initialSection) {
        if (initialSection != null) viewModel.onDeepLinkSection(initialSection)
    }

    SettingsScreen(
        currentSection = state.value.currentSection,
        selectedQuality = state.value.playbackQuality,
        selectedUpdateMode = state.value.updateMode,
        selectedChannelMode = state.value.channelMode,
        selectedTheme = state.value.appTheme,
        isHomeFavoritesRailEnabled = state.value.isHomeFavoritesRailEnabled,
        isHomeMoviesEnabled = state.value.isHomeMoviesEnabled,
        isHomeSeriesEnabled = state.value.isHomeSeriesEnabled,
        isHomeMenuLabelsEnabled = state.value.isHomeMenuLabelsEnabled,
        selectedWatchedMarkingMode = state.value.watchedMarkingMode,
        torrServeBaseUrl = state.value.torrServeBaseUrl,
        torrServeLogin = state.value.torrServeLogin,
        torrServePassword = state.value.torrServePassword,
        isTorrServeConnected = state.value.isTorrServeConnected,
        isTorrServeClientAvailable = state.value.isTorrServeClientAvailable,
        lostFilmHost = state.value.lostFilmHost,
        activeLostFilmHost = LostFilmBaseUrl.url.collectAsStateWithLifecycle().value,
        isCheckingLostFilmHost = state.value.isCheckingLostFilmHost,
        isLostFilmAutomaticMirror = state.value.isLostFilmAutomaticMirror,
        lostFilmHostStatusText = state.value.lostFilmHostStatusText,
        torrServeStatusText = state.value.torrServeStatusText,
        isCheckingTorrServe = state.value.isCheckingTorrServe,
        isAuthenticated = isAuthenticated,
        installedVersionText = state.value.installedVersionText,
        latestVersionText = state.value.savedAppUpdate?.latestVersion ?: state.value.latestVersionText,
        statusText = state.value.statusText,
        isCheckingForUpdates = state.value.isCheckingForUpdates,
        isDownloadingUpdate = state.value.isDownloadingUpdate,
        installDownloadProgress = state.value.installDownloadProgress,
        installUrl = state.value.installUrl,
        actions = SettingsActions(
            onSectionSelected = viewModel::onSectionSelected,
            onSectionBack = viewModel::onSectionBack,
            onNavigateBack = onNavigateBack,
            onQualitySelected = viewModel::onPlaybackQualitySelected,
            onWatchedMarkingModeSelected = viewModel::onWatchedMarkingModeSelected,
            onTorrServeLoginChanged = viewModel::onTorrServeLoginChanged,
            onTorrServePasswordChanged = viewModel::onTorrServePasswordChanged,
            onCheckLostFilmHostClick = viewModel::onCheckLostFilmHostClick,
            onSelectLostFilmMirror = viewModel::onSelectLostFilmMirror,
            onToggleLostFilmAutomaticMirror = viewModel::onToggleLostFilmAutomaticMirror,
            onLostFilmHostChanged = viewModel::onLostFilmHostChanged,
            onSaveLostFilmHostClick = viewModel::onSaveLostFilmHostClick,
            onResetLostFilmHostClick = viewModel::onResetLostFilmHostClick,
            onTorrServeBaseUrlChanged = viewModel::onTorrServeBaseUrlChanged,
            onSaveTorrServeClick = viewModel::onSaveTorrServeBaseUrlClick,
            onResetTorrServeClick = viewModel::onResetTorrServeBaseUrlClick,
            onCheckTorrServeClick = viewModel::onCheckTorrServeClick,
            onAuthClick = onAuthClick,
            onUpdateModeSelected = viewModel::onUpdateModeSelected,
            onChannelModeSelected = viewModel::onChannelModeSelected,
            onThemeSelected = viewModel::onThemeSelected,
            onHomeFavoritesRailVisibilitySelected = viewModel::onHomeFavoritesRailVisibilitySelected,
            onHomeMoviesVisibilitySelected = viewModel::onHomeMoviesVisibilitySelected,
            onHomeSeriesVisibilitySelected = viewModel::onHomeSeriesVisibilitySelected,
            onHomeMenuLabelsVisibilitySelected = viewModel::onHomeMenuLabelsVisibilitySelected,
            onCheckForUpdatesClick = viewModel::onCheckForUpdatesClick,
            onInstallUpdateClick = {
                state.value.installUrl?.let { url ->
                    viewModel.installUpdate(url)
                }
            },
        ),
    )
}
