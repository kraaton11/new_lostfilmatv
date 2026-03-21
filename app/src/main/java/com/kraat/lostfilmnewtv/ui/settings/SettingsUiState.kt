package com.kraat.lostfilmnewtv.ui.settings

import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode

data class SettingsUiState(
    val playbackQuality: PlaybackQualityPreference,
    val updateMode: UpdateCheckMode,
    val installedVersionText: String,
    val latestVersionText: String? = null,
    val statusText: String? = null,
    val isCheckingForUpdates: Boolean = false,
    val installUrl: String? = null,
)
