package com.kraat.lostfilmnewtv.ui.settings

import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.playback.WatchedMarkingMode
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.ui.theme.AppTheme
import com.kraat.lostfilmnewtv.updates.SavedAppUpdate
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode

data class SettingsUiState(
    val currentSection: SettingsSection = SettingsSection.PLAYBACK,
    val playbackQuality: PlaybackQualityPreference,
    val updateMode: UpdateCheckMode,
    val channelMode: AndroidTvChannelMode,
    val isHomeFavoritesRailEnabled: Boolean,
    val isHomeMoviesEnabled: Boolean = true,
    val isHomeSeriesEnabled: Boolean = true,
    val isHomeMenuLabelsEnabled: Boolean = true,
    val watchedMarkingMode: WatchedMarkingMode,
    val torrServeBaseUrl: String,
    val torrServeLogin: String = "",
    val torrServePassword: String = "",
    val isTorrServeClientAvailable: Boolean? = null,
    val isTorrServeConnected: Boolean = false,
    val lostFilmHost: String = "",
    val isCheckingLostFilmHost: Boolean = false,
    val isLostFilmAutomaticMirror: Boolean = false,
    val lostFilmHostStatusText: String? = null,
    val torrServeStatusText: String? = null,
    val isCheckingTorrServe: Boolean = false,
    val installedVersionText: String,
    val savedAppUpdate: SavedAppUpdate? = null,
    val latestVersionText: String? = null,
    val statusText: String? = null,
    val isCheckingForUpdates: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val installDownloadProgress: Int? = null,
    val installUrl: String? = null,
    val appTheme: AppTheme = AppTheme.CLASSIC,
)
