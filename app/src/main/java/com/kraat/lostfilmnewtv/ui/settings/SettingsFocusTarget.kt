package com.kraat.lostfilmnewtv.ui.settings

import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.playback.WatchedMarkingMode
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode

sealed class SettingsFocusTarget {
    data class PlaybackQuality(val quality: PlaybackQualityPreference) : SettingsFocusTarget()
    data object HomeFavoritesShow : SettingsFocusTarget()
    data object HomeFavoritesHide : SettingsFocusTarget()
    data class UpdateChannel(val mode: UpdateCheckMode) : SettingsFocusTarget()
    data object CheckForUpdates : SettingsFocusTarget()
    data object InstallUpdate : SettingsFocusTarget()
    data class ChannelMode(val mode: AndroidTvChannelMode) : SettingsFocusTarget()
    data class WatchedMarking(val mode: WatchedMarkingMode) : SettingsFocusTarget()
    data object AccountAuth : SettingsFocusTarget()
}

fun SettingsFocusTarget.toTag(): String = when (this) {
    is SettingsFocusTarget.PlaybackQuality -> "settings-quality-${quality.storageValue}"
    SettingsFocusTarget.HomeFavoritesShow -> "settings-home-favorites-show"
    SettingsFocusTarget.HomeFavoritesHide -> "settings-home-favorites-hide"
    is SettingsFocusTarget.UpdateChannel -> when (mode) {
        UpdateCheckMode.MANUAL -> "settings-update-mode-manual"
        UpdateCheckMode.QUIET_CHECK -> "settings-update-mode-quiet"
    }
    SettingsFocusTarget.CheckForUpdates -> "settings-action-check-updates"
    SettingsFocusTarget.InstallUpdate -> "settings-install-update"
    is SettingsFocusTarget.ChannelMode -> when (mode) {
        AndroidTvChannelMode.ALL_NEW -> "settings-tv-channel-all-new"
        AndroidTvChannelMode.UNWATCHED -> "settings-tv-channel-unwatched"
        AndroidTvChannelMode.DISABLED -> "settings-tv-channel-disabled"
    }
    is SettingsFocusTarget.WatchedMarking -> when (mode) {
        WatchedMarkingMode.AUTO -> "settings-watched-marking-auto"
        WatchedMarkingMode.DISABLED -> "settings-watched-marking-disabled"
    }
    SettingsFocusTarget.AccountAuth -> "settings-account-auth-action"
}
