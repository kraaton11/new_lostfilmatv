package com.kraat.lostfilmnewtv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.updates.AppUpdateRefreshResult
import com.kraat.lostfilmnewtv.updates.SavedAppUpdate
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    installedVersion: String,
    initialPlaybackQuality: PlaybackQualityPreference,
    initialUpdateMode: UpdateCheckMode,
    initialChannelMode: AndroidTvChannelMode = AndroidTvChannelMode.ALL_NEW,
    initialHomeFavoritesRailEnabled: Boolean = false,
    private val persistPlaybackQuality: (PlaybackQualityPreference) -> Unit = {},
    private val persistUpdateMode: (UpdateCheckMode) -> Unit = {},
    private val persistChannelMode: (AndroidTvChannelMode) -> Unit = {},
    private val persistHomeFavoritesRailEnabled: (Boolean) -> Unit = {},
    private val onHomeFavoritesRailVisibilityChanged: (Boolean) -> Unit = {},
    savedUpdateState: StateFlow<SavedAppUpdate?>,
    private val refreshSavedUpdateState: suspend () -> AppUpdateRefreshResult,
    private val syncAppUpdateBackgroundSchedule: () -> Unit = {},
    private val syncAndroidTvChannelBackgroundSchedule: () -> Unit = {},
    private val syncAndroidTvChannel: suspend () -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val debounceIntervalMs: Long = DEFAULT_DEBOUNCE_INTERVAL_MS,
) : ViewModel() {
    private val initialSavedUpdate = savedUpdateState.value
    private var activeRefreshJob: Job? = null
    private var refreshRequestToken: Long = 0
    private var lastCheckTimestamp = 0L
    private val _uiState = MutableStateFlow(
        SettingsUiState(
            playbackQuality = initialPlaybackQuality,
            updateMode = initialUpdateMode,
            channelMode = initialChannelMode,
            isHomeFavoritesRailEnabled = initialHomeFavoritesRailEnabled,
            installedVersionText = installedVersion,
            savedAppUpdate = initialSavedUpdate,
            installUrl = initialSavedUpdate?.apkUrl,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            savedUpdateState.drop(1).collectLatest { savedUpdate ->
                _uiState.update { state ->
                    state.copy(
                        savedAppUpdate = savedUpdate,
                        installUrl = savedUpdate?.apkUrl,
                        isDownloadingUpdate = false,
                    )
                }
            }
        }
    }

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
        syncAppUpdateBackgroundSchedule()
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

    fun onHomeFavoritesRailVisibilitySelected(enabled: Boolean) {
        if (enabled == _uiState.value.isHomeFavoritesRailEnabled) {
            return
        }
        persistHomeFavoritesRailEnabled(enabled)
        onHomeFavoritesRailVisibilityChanged(enabled)
        _uiState.update { state ->
            state.copy(isHomeFavoritesRailEnabled = enabled)
        }
    }

    fun onCheckForUpdatesClick() {
        val now = System.currentTimeMillis()
        if (now - lastCheckTimestamp < debounceIntervalMs) {
            return
        }
        lastCheckTimestamp = now
        refreshUpdateInfo()
    }

    fun onInstallUpdateFailed() {
        _uiState.update { state ->
            state.copy(
                statusText = INSTALL_UPDATE_FAILED_MESSAGE,
                isDownloadingUpdate = false,
            )
        }
    }

    fun onInstallDownloadProgress(isDownloading: Boolean) {
        _uiState.update { state ->
            state.copy(isDownloadingUpdate = isDownloading)
        }
    }

    private fun refreshUpdateInfo() {
        val requestToken = refreshRequestToken + 1
        refreshRequestToken = requestToken
        activeRefreshJob?.cancel()
        _uiState.update { state ->
            state.copy(
                statusText = CHECKING_UPDATES_MESSAGE,
                isCheckingForUpdates = true,
                isDownloadingUpdate = false,
            )
        }
        activeRefreshJob = viewModelScope.launch(ioDispatcher) {
            val refreshResult = refreshSavedUpdateState()
            if (refreshRequestToken == requestToken) {
                _uiState.update { state ->
                    state.toCheckedState(refreshResult)
                }
            }
        }
    }
}

private fun SettingsUiState.toCheckedState(refreshResult: AppUpdateRefreshResult): SettingsUiState {
    return when (refreshResult) {
        is AppUpdateRefreshResult.UpToDate -> copy(
            installedVersionText = refreshResult.installedVersion,
            savedAppUpdate = null,
            latestVersionText = refreshResult.installedVersion,
            statusText = "Установлена последняя версия",
            isCheckingForUpdates = false,
            isDownloadingUpdate = false,
            installUrl = null,
        )

        is AppUpdateRefreshResult.UpdateSaved -> copy(
            savedAppUpdate = refreshResult.savedUpdate,
            latestVersionText = refreshResult.savedUpdate.latestVersion,
            statusText = "Доступно обновление",
            isCheckingForUpdates = false,
            isDownloadingUpdate = false,
            installUrl = refreshResult.savedUpdate.apkUrl,
        )

        is AppUpdateRefreshResult.FailedKeptPrevious -> copy(
            installedVersionText = refreshResult.installedVersion,
            statusText = refreshResult.message,
            isCheckingForUpdates = false,
            isDownloadingUpdate = false,
        )

        is AppUpdateRefreshResult.FailedEmpty -> copy(
            installedVersionText = refreshResult.installedVersion,
            savedAppUpdate = null,
            latestVersionText = null,
            statusText = refreshResult.message,
            isCheckingForUpdates = false,
            isDownloadingUpdate = false,
            installUrl = null,
        )
    }
}

private const val CHECKING_UPDATES_MESSAGE = "Проверяем обновления..."
private const val INSTALL_UPDATE_FAILED_MESSAGE = "Не удалось открыть обновление."
private const val DEFAULT_DEBOUNCE_INTERVAL_MS = 1000L
