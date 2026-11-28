package com.kraat.lostfilmnewtv.ui.settings

import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.playback.WatchedMarkingMode
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode

sealed class SettingsFocusTarget {
    data class PlaybackQuality(val quality: PlaybackQualityPreference) : SettingsFocusTarget()
    data object TorrServeBaseUrl : SettingsFocusTarget()
    data object TorrServeSave : SettingsFocusTarget()
    data object TorrServeCheck : SettingsFocusTarget()
    data object TorrServeReset : SettingsFocusTarget()
    data object LostFilmHost : SettingsFocusTarget()
    data object LostFilmHostSave : SettingsFocusTarget()
    data class LostFilmMirror(val url: String) : SettingsFocusTarget()
    data object LostFilmAutomaticMirror : SettingsFocusTarget()
    data object LostFilmHostCheck : SettingsFocusTarget()
    data object LostFilmHostReset : SettingsFocusTarget()
    data object HomeFavoritesToggle : SettingsFocusTarget()
    data object HomeMoviesToggle : SettingsFocusTarget()
    data object HomeSeriesToggle : SettingsFocusTarget()
    data object HomeMenuLabelsToggle : SettingsFocusTarget()
    data class UpdateChannel(val mode: UpdateCheckMode) : SettingsFocusTarget()
    data object CheckForUpdates : SettingsFocusTarget()
    data object InstallUpdate : SettingsFocusTarget()
    data class ChannelMode(val mode: AndroidTvChannelMode) : SettingsFocusTarget()
    data class WatchedMarking(val mode: WatchedMarkingMode) : SettingsFocusTarget()
    data object AccountAuth : SettingsFocusTarget()
    data object AboutGitHubLink : SettingsFocusTarget()
    data object AboutTelegramLink : SettingsFocusTarget()
}

fun SettingsFocusTarget.toTag(): String = when (this) {
    is SettingsFocusTarget.PlaybackQuality -> "settings-quality-${quality.storageValue}"
    SettingsFocusTarget.TorrServeBaseUrl -> "settings-torrserve-base-url"
    SettingsFocusTarget.TorrServeSave -> "settings-torrserve-save"
    SettingsFocusTarget.TorrServeCheck -> "settings-torrserve-check"
    SettingsFocusTarget.TorrServeReset -> "settings-torrserve-reset"
    SettingsFocusTarget.LostFilmHost -> "settings-lostfilm-host"
    SettingsFocusTarget.LostFilmHostSave -> "settings-lostfilm-host-save"
    is SettingsFocusTarget.LostFilmMirror -> "settings-lostfilm-mirror-${url.substringAfter("://")}"
    SettingsFocusTarget.LostFilmAutomaticMirror -> "settings-lostfilm-automatic-mirror"
    SettingsFocusTarget.LostFilmHostCheck -> "settings-lostfilm-host-check"
    SettingsFocusTarget.LostFilmHostReset -> "settings-lostfilm-host-reset"
    SettingsFocusTarget.HomeFavoritesToggle -> "settings-home-favorites-toggle"
    SettingsFocusTarget.HomeMoviesToggle -> "settings-home-movies-toggle"
    SettingsFocusTarget.HomeSeriesToggle -> "settings-home-series-toggle"
    SettingsFocusTarget.HomeMenuLabelsToggle -> "settings-home-menu-labels-toggle"
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
    is SettingsFocusTarget.WatchedMarking -> "settings-watched-marking-${mode.storageValue.replace('_', '-')}"
    SettingsFocusTarget.AccountAuth -> "settings-account-auth-action"
    SettingsFocusTarget.AboutGitHubLink -> "settings-about-github-link"
    SettingsFocusTarget.AboutTelegramLink -> "settings-about-telegram-link"
}
