package com.kraat.lostfilmnewtv.ui.settings

import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.playback.WatchedMarkingMode
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode

class SettingsActions(
    val onSectionSelected: (SettingsSection) -> Unit = {},
    val onSectionBack: () -> Unit = {},
    val onNavigateBack: () -> Unit = {},
    val onQualitySelected: (PlaybackQualityPreference) -> Unit,
    val onWatchedMarkingModeSelected: (WatchedMarkingMode) -> Unit = {},
    val onTorrServeLoginChanged: (String) -> Unit = {},
    val onTorrServePasswordChanged: (String) -> Unit = {},
    val onTorrServeBaseUrlChanged: (String) -> Unit = {},
    val onSaveTorrServeClick: () -> Unit = {},
    val onResetTorrServeClick: () -> Unit = {},
    val onCheckTorrServeClick: () -> Unit = {},
    val onCheckLostFilmHostClick: () -> Unit = {},
    val onSelectLostFilmMirror: (String) -> Unit = {},
    val onToggleLostFilmAutomaticMirror: () -> Unit = {},
    val onLostFilmHostChanged: (String) -> Unit = {},
    val onSaveLostFilmHostClick: () -> Unit = {},
    val onResetLostFilmHostClick: () -> Unit = {},
    val onAuthClick: () -> Unit = {},
    val onUpdateModeSelected: (UpdateCheckMode) -> Unit,
    val onChannelModeSelected: (AndroidTvChannelMode) -> Unit,
    val onHomeFavoritesRailVisibilitySelected: (Boolean) -> Unit = {},
    val onHomeMoviesVisibilitySelected: (Boolean) -> Unit = {},
    val onHomeSeriesVisibilitySelected: (Boolean) -> Unit = {},
    val onHomeMenuLabelsVisibilitySelected: (Boolean) -> Unit = {},
    val onCheckForUpdatesClick: () -> Unit,
    val onInstallUpdateClick: () -> Unit,
)
