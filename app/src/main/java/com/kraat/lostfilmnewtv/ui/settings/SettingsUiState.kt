package com.kraat.lostfilmnewtv.ui.settings

import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.updates.SavedAppUpdate
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode

data class SettingsUiState(
    val playbackQuality: PlaybackQualityPreference,
    val updateMode: UpdateCheckMode,
    val channelMode: AndroidTvChannelMode,
    val isHomeFavoritesRailEnabled: Boolean,
    val installedVersionText: String,
    val savedAppUpdate: SavedAppUpdate? = null,
    val latestVersionText: String? = null,
    val statusText: String? = null,
    val isCheckingForUpdates: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val installUrl: String? = null,
)
