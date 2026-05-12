package com.kraat.lostfilmnewtv.ui.settings

import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.playback.WatchedMarkingMode
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.updates.SavedAppUpdate
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode

data class SettingsUiState(
    val currentSection: SettingsSection = SettingsSection.PLAYBACK,
    val playbackQuality: PlaybackQualityPreference,
    val updateMode: UpdateCheckMode,
    val channelMode: AndroidTvChannelMode,
    val isHomeFavoritesRailEnabled: Boolean,
    val isHomeMenuLabelsEnabled: Boolean = true,
    val watchedMarkingMode: WatchedMarkingMode,
    val torrServeBaseUrl: String,
    val torrServeStatusText: String? = null,
    val isCheckingTorrServe: Boolean = false,
    val dataStatusText: String? = null,
    val isDataActionRunning: Boolean = false,
    val diagnosticResults: List<SettingsDiagnosticResult> = emptyList(),
    val diagnosticsStatusText: String? = null,
    val isRunningDiagnostics: Boolean = false,
    val installedVersionText: String,
    val savedAppUpdate: SavedAppUpdate? = null,
    val latestVersionText: String? = null,
    val statusText: String? = null,
    val isCheckingForUpdates: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val installDownloadProgress: Int? = null,
    val installUrl: String? = null,
)
