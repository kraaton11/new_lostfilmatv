package com.kraat.lostfilmnewtv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.BuildConfig
import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundScheduler
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelSyncManager
import com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundScheduler
import com.kraat.lostfilmnewtv.updates.AppUpdateCoordinator
import com.kraat.lostfilmnewtv.updates.ReleaseApkLauncher
import com.kraat.lostfilmnewtv.updates.AppUpdateRefreshResult
import com.kraat.lostfilmnewtv.updates.SavedAppUpdate
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesStore: PlaybackPreferencesStore,
    private val appUpdateCoordinator: AppUpdateCoordinator,
    private val homeChannelSyncManager: HomeChannelSyncManager,
    private val homeChannelBackgroundScheduler: HomeChannelBackgroundScheduler,
    private val appUpdateBackgroundScheduler: AppUpdateBackgroundScheduler,
    private val releaseApkLauncher: ReleaseApkLauncher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    // Колбэк уведомляет HomeViewModel об изменении видимости рельса — прокидывается снаружи при навигации
    var onHomeFavoritesRailVisibilityChanged: (Boolean) -> Unit = {}

    private var debounceIntervalMs: Long = DEFAULT_DEBOUNCE_INTERVAL_MS

    private val savedUpdateState: StateFlow<SavedAppUpdate?> = appUpdateCoordinator.savedUpdateState

    private var activeRefreshJob: Job? = null
    private var refreshRequestToken: Long = 0
    private var lastCheckTimestamp = 0L

    private val initialSavedUpdate = savedUpdateState.value

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            playbackQuality = preferencesStore.readDefaultQuality(),
            updateMode = preferencesStore.readUpdateCheckMode(),
            channelMode = preferencesStore.readAndroidTvChannelMode(),
            isHomeFavoritesRailEnabled = preferencesStore.readHomeFavoritesRailEnabled(),
            installedVersionText = BuildConfig.VERSION_NAME,
            savedAppUpdate = initialSavedUpdate,
            installUrl = initialSavedUpdate?.apkUrl,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            savedUpdateState.drop(1).collectLatest { savedUpdate ->
                _uiState.update { it.copy(savedAppUpdate = savedUpdate, installUrl = savedUpdate?.apkUrl, isDownloadingUpdate = false) }
            }
        }
    }

    fun onScreenShown() {
        if (_uiState.value.updateMode == UpdateCheckMode.QUIET_CHECK) refreshUpdateInfo()
    }

    fun onPlaybackQualitySelected(quality: PlaybackQualityPreference) {
        preferencesStore.writeDefaultQuality(quality)
        _uiState.update { it.copy(playbackQuality = quality) }
    }

    fun onUpdateModeSelected(mode: UpdateCheckMode) {
        if (mode == _uiState.value.updateMode) return
        preferencesStore.writeUpdateCheckMode(mode)
        _uiState.update { it.copy(updateMode = mode) }
        appUpdateBackgroundScheduler.syncForCurrentMode()
        if (mode == UpdateCheckMode.QUIET_CHECK) refreshUpdateInfo()
    }

    fun onChannelModeSelected(mode: AndroidTvChannelMode) {
        if (mode == _uiState.value.channelMode) return
        preferencesStore.writeAndroidTvChannelMode(mode)
        _uiState.update { it.copy(channelMode = mode) }
        viewModelScope.launch(ioDispatcher) {
            homeChannelBackgroundScheduler.syncForCurrentMode()
            homeChannelSyncManager.syncNow()
        }
    }

    fun onHomeFavoritesRailVisibilitySelected(enabled: Boolean) {
        if (enabled == _uiState.value.isHomeFavoritesRailEnabled) return
        preferencesStore.writeHomeFavoritesRailEnabled(enabled)
        onHomeFavoritesRailVisibilityChanged(enabled)
        _uiState.update { it.copy(isHomeFavoritesRailEnabled = enabled) }
    }

    fun onCheckForUpdatesClick() {
        val now = System.currentTimeMillis()
        if (now - lastCheckTimestamp < debounceIntervalMs) return
        lastCheckTimestamp = now
        refreshUpdateInfo()
    }

    fun onInstallUpdateFailed() {
        _uiState.update { it.copy(statusText = INSTALL_UPDATE_FAILED_MESSAGE, isDownloadingUpdate = false) }
    }

    fun onInstallDownloadProgress(isDownloading: Boolean) {
        _uiState.update { it.copy(isDownloadingUpdate = isDownloading) }
    }

    fun installUpdate(context: android.content.Context, apkUrl: String) {
        viewModelScope.launch(ioDispatcher) {
            onInstallDownloadProgress(true)
            val launched = releaseApkLauncher.launch(
                context = context,
                apkUrl = apkUrl,
                onDownloadingChange = { onInstallDownloadProgress(it) },
            )
            if (!launched) onInstallUpdateFailed()
        }
    }

    private fun refreshUpdateInfo() {
        val requestToken = ++refreshRequestToken
        activeRefreshJob?.cancel()
        _uiState.update { it.copy(statusText = CHECKING_UPDATES_MESSAGE, isCheckingForUpdates = true, isDownloadingUpdate = false) }
        activeRefreshJob = viewModelScope.launch(ioDispatcher) {
            val result = appUpdateCoordinator.refreshSavedUpdateState()
            if (refreshRequestToken == requestToken) {
                _uiState.update { it.toCheckedState(result) }
            }
        }
    }

    constructor(
        preferencesStore: PlaybackPreferencesStore,
        appUpdateCoordinator: AppUpdateCoordinator,
        homeChannelSyncManager: HomeChannelSyncManager,
        homeChannelBackgroundScheduler: HomeChannelBackgroundScheduler,
        appUpdateBackgroundScheduler: AppUpdateBackgroundScheduler,
        releaseApkLauncher: ReleaseApkLauncher,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        debounceIntervalMs: Long,
    ) : this(
        preferencesStore = preferencesStore,
        appUpdateCoordinator = appUpdateCoordinator,
        homeChannelSyncManager = homeChannelSyncManager,
        homeChannelBackgroundScheduler = homeChannelBackgroundScheduler,
        appUpdateBackgroundScheduler = appUpdateBackgroundScheduler,
        releaseApkLauncher = releaseApkLauncher,
        ioDispatcher = ioDispatcher,
    ) {
        this.debounceIntervalMs = debounceIntervalMs
    }
}

private fun SettingsUiState.toCheckedState(result: AppUpdateRefreshResult): SettingsUiState = when (result) {
    is AppUpdateRefreshResult.UpToDate -> copy(
        installedVersionText = result.installedVersion, savedAppUpdate = null,
        latestVersionText = result.installedVersion, statusText = "Установлена последняя версия",
        isCheckingForUpdates = false, isDownloadingUpdate = false, installUrl = null,
    )
    is AppUpdateRefreshResult.UpdateSaved -> copy(
        savedAppUpdate = result.savedUpdate.copy(manuallyChecked = true),
        latestVersionText = result.savedUpdate.latestVersion, statusText = "Доступно обновление",
        isCheckingForUpdates = false, isDownloadingUpdate = false, installUrl = result.savedUpdate.apkUrl,
    )
    is AppUpdateRefreshResult.FailedKeptPrevious -> copy(
        installedVersionText = result.installedVersion, statusText = result.message,
        isCheckingForUpdates = false, isDownloadingUpdate = false,
    )
    is AppUpdateRefreshResult.FailedEmpty -> copy(
        installedVersionText = result.installedVersion, savedAppUpdate = null,
        latestVersionText = null, statusText = result.message,
        isCheckingForUpdates = false, isDownloadingUpdate = false, installUrl = null,
    )
}

private const val CHECKING_UPDATES_MESSAGE = "Проверяем обновления..."
private const val INSTALL_UPDATE_FAILED_MESSAGE = "Не удалось открыть обновление."
private const val DEFAULT_DEBOUNCE_INTERVAL_MS = 1000L
