package com.kraat.lostfilmnewtv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.updates.AppUpdateInfo
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    installedVersion: String,
    initialPlaybackQuality: PlaybackQualityPreference,
    initialUpdateMode: UpdateCheckMode,
    initialChannelMode: AndroidTvChannelMode = AndroidTvChannelMode.ALL_NEW,
    private val persistPlaybackQuality: (PlaybackQualityPreference) -> Unit = {},
    private val persistUpdateMode: (UpdateCheckMode) -> Unit = {},
    private val persistChannelMode: (AndroidTvChannelMode) -> Unit = {},
    private val syncAndroidTvChannelBackgroundSchedule: () -> Unit = {},
    private val syncAndroidTvChannel: suspend () -> Unit = {},
    private val checkForUpdates: suspend () -> AppUpdateInfo,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private var activeRefreshJob: Job? = null
    private var refreshRequestToken: Long = 0
    private val _uiState = MutableStateFlow(
        SettingsUiState(
            playbackQuality = initialPlaybackQuality,
            updateMode = initialUpdateMode,
            channelMode = initialChannelMode,
            installedVersionText = installedVersion,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onScreenShown() {
        if (_uiState.value.updateMode == UpdateCheckMode.QUIET_CHECK) {
            refreshUpdateInfo()
        }
    }

    fun onPlaybackQualitySelected(quality: PlaybackQualityPreference) {
        persistPlaybackQuality(quality)
        _uiState.update { state ->
            state.copy(playbackQuality = quality)
        }
    }

    fun onUpdateModeSelected(mode: UpdateCheckMode) {
        if (mode == _uiState.value.updateMode) {
            return
        }
        persistUpdateMode(mode)
        _uiState.update { state ->
            state.copy(updateMode = mode)
        }
        if (mode == UpdateCheckMode.QUIET_CHECK) {
            refreshUpdateInfo()
        }
    }

    fun onChannelModeSelected(mode: AndroidTvChannelMode) {
        if (mode == _uiState.value.channelMode) {
            return
        }
        persistChannelMode(mode)
        _uiState.update { state ->
            state.copy(channelMode = mode)
        }
        viewModelScope.launch(ioDispatcher) {
            syncAndroidTvChannelBackgroundSchedule()
            syncAndroidTvChannel()
        }
    }

    fun onCheckForUpdatesClick() {
        refreshUpdateInfo()
    }

    fun onInstallUpdateFailed() {
        _uiState.update { state ->
            state.copy(statusText = INSTALL_UPDATE_FAILED_MESSAGE)
        }
    }

    private fun refreshUpdateInfo() {
        val requestToken = refreshRequestToken + 1
        refreshRequestToken = requestToken
        activeRefreshJob?.cancel()
        _uiState.update { state ->
            state.copy(
                latestVersionText = null,
                statusText = CHECKING_UPDATES_MESSAGE,
                isCheckingForUpdates = true,
                installUrl = null,
            )
        }
        activeRefreshJob = viewModelScope.launch(ioDispatcher) {
            val updateInfo = checkForUpdates()
            if (refreshRequestToken == requestToken) {
                _uiState.update { state ->
                    state.toCheckedState(updateInfo)
                }
            }
        }
    }
}

private fun SettingsUiState.toCheckedState(updateInfo: AppUpdateInfo): SettingsUiState {
    return when (updateInfo) {
        is AppUpdateInfo.UpToDate -> copy(
            installedVersionText = updateInfo.installedVersion,
            latestVersionText = updateInfo.installedVersion,
            statusText = "Установлена последняя версия",
            isCheckingForUpdates = false,
            installUrl = null,
        )

        is AppUpdateInfo.UpdateAvailable -> copy(
            installedVersionText = updateInfo.installedVersion,
            latestVersionText = updateInfo.latestVersion,
            statusText = "Доступно обновление",
            isCheckingForUpdates = false,
            installUrl = updateInfo.apkUrl,
        )

        is AppUpdateInfo.Error -> copy(
            installedVersionText = updateInfo.installedVersion,
            latestVersionText = null,
            statusText = updateInfo.message,
            isCheckingForUpdates = false,
            installUrl = null,
        )
    }
}

private const val CHECKING_UPDATES_MESSAGE = "Проверяем обновления..."
private const val INSTALL_UPDATE_FAILED_MESSAGE = "Не удалось открыть обновление."
