package com.kraat.lostfilmnewtv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.BuildConfig
import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import com.kraat.lostfilmnewtv.data.parser.LostFilmBaseUrl
import com.kraat.lostfilmnewtv.data.network.LostFilmMirrorChecker
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.playback.WatchedMarkingMode
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundScheduler
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelSyncManager
import com.kraat.lostfilmnewtv.ui.home.HomeFeedMode
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesStore: PlaybackPreferencesStore,
    private val appUpdateCoordinator: AppUpdateCoordinator,
    private val homeChannelSyncManager: HomeChannelSyncManager,
    private val homeChannelBackgroundScheduler: HomeChannelBackgroundScheduler,
    private val appUpdateBackgroundScheduler: AppUpdateBackgroundScheduler,
    private val releaseApkLauncher: ReleaseApkLauncher,
    private val torrServeEndpointChecker: TorrServeEndpointChecker,
    private val lostFilmMirrorChecker: LostFilmMirrorChecker,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private var debounceIntervalMs: Long = DEFAULT_DEBOUNCE_INTERVAL_MS

    private val savedUpdateState: StateFlow<SavedAppUpdate?> = appUpdateCoordinator.savedUpdateState

    private var activeRefreshJob: Job? = null
    private var installJob: Job? = null
    private var torrServeCheckJob: Job? = null
    private var lostFilmCheckJob: Job? = null
    private var refreshRequestToken: Long = 0
    private var lastCheckTimestamp = 0L

    private val initialSavedUpdate = savedUpdateState.value

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            playbackQuality = preferencesStore.readDefaultQuality(),
            updateMode = preferencesStore.readUpdateCheckMode(),
            channelMode = preferencesStore.readAndroidTvChannelMode(),
            isHomeFavoritesRailEnabled = preferencesStore.readHomeFavoritesRailEnabled(),
            isHomeMoviesEnabled = preferencesStore.readHomeMoviesEnabled(),
            isHomeSeriesEnabled = preferencesStore.readHomeSeriesEnabled(),
            isHomeMenuLabelsEnabled = preferencesStore.readHomeMenuLabelsEnabled(),
            watchedMarkingMode = preferencesStore.readWatchedMarkingMode(),
            torrServeBaseUrl = preferencesStore.readTorrServeBaseUrl(),
            torrServeLogin = preferencesStore.readTorrServeLogin(),
            torrServePassword = preferencesStore.readTorrServePassword(),
            lostFilmHost = preferencesStore.readLostFilmBaseUrl(),
            isLostFilmAutomaticMirror = preferencesStore.readLostFilmAutomaticMirror(),
            installedVersionText = BuildConfig.VERSION_NAME,
            savedAppUpdate = initialSavedUpdate,
            installUrl = initialSavedUpdate?.apkUrl,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _railVisibilityEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val railVisibilityEvents: SharedFlow<Boolean> = _railVisibilityEvents.asSharedFlow()
    private val _homeModeVisibilityEvents = MutableSharedFlow<Pair<HomeFeedMode, Boolean>>(extraBufferCapacity = 4)
    val homeModeVisibilityEvents: SharedFlow<Pair<HomeFeedMode, Boolean>> = _homeModeVisibilityEvents.asSharedFlow()
    private val _homeMenuLabelsVisibilityEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val homeMenuLabelsVisibilityEvents: SharedFlow<Boolean> = _homeMenuLabelsVisibilityEvents.asSharedFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            savedUpdateState.drop(1).collectLatest { savedUpdate ->
                _uiState.update {
                    it.copy(
                        savedAppUpdate = savedUpdate,
                        installUrl = savedUpdate?.apkUrl,
                        isDownloadingUpdate = false,
                        installDownloadProgress = null,
                    )
                }
            }
        }
    }

    fun onScreenShown() {
        if (_uiState.value.torrServeStatusText == null) onCheckTorrServeClick()
        if (_uiState.value.updateMode == UpdateCheckMode.QUIET_CHECK) refreshUpdateInfo()
    }

    fun onSectionSelected(section: SettingsSection) {
        _uiState.update { it.copy(currentSection = section) }
    }

    fun onSectionBack() {
        _uiState.update { it.copy(currentSection = SettingsSection.PLAYBACK) }
    }

    fun onDeepLinkSection(sectionName: String) {
        val section = SettingsSection.entries.firstOrNull { it.name == sectionName }
            ?: if (sectionName == "QUALITY") SettingsSection.PLAYBACK else return
        _uiState.update { it.copy(currentSection = section) }
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
        _railVisibilityEvents.tryEmit(enabled)
        _homeModeVisibilityEvents.tryEmit(HomeFeedMode.Favorites to enabled)
        _uiState.update { it.copy(isHomeFavoritesRailEnabled = enabled) }
    }

    fun onHomeMoviesVisibilitySelected(enabled: Boolean) {
        if (enabled == _uiState.value.isHomeMoviesEnabled) return
        preferencesStore.writeHomeMoviesEnabled(enabled)
        _homeModeVisibilityEvents.tryEmit(HomeFeedMode.Movies to enabled)
        _uiState.update { it.copy(isHomeMoviesEnabled = enabled) }
    }

    fun onHomeSeriesVisibilitySelected(enabled: Boolean) {
        if (enabled == _uiState.value.isHomeSeriesEnabled) return
        preferencesStore.writeHomeSeriesEnabled(enabled)
        _homeModeVisibilityEvents.tryEmit(HomeFeedMode.Series to enabled)
        _uiState.update { it.copy(isHomeSeriesEnabled = enabled) }
    }

    fun onHomeMenuLabelsVisibilitySelected(enabled: Boolean) {
        if (enabled == _uiState.value.isHomeMenuLabelsEnabled) return
        preferencesStore.writeHomeMenuLabelsEnabled(enabled)
        _homeMenuLabelsVisibilityEvents.tryEmit(enabled)
        _uiState.update { it.copy(isHomeMenuLabelsEnabled = enabled) }
    }

    fun onWatchedMarkingModeSelected(mode: WatchedMarkingMode) {
        if (mode == _uiState.value.watchedMarkingMode) return
        preferencesStore.writeWatchedMarkingMode(mode)
        _uiState.update { it.copy(watchedMarkingMode = mode) }
    }

    fun onTorrServeBaseUrlChanged(value: String) = editTorrServe { it.copy(torrServeBaseUrl = value) }

    fun onTorrServeLoginChanged(value: String) = editTorrServe { it.copy(torrServeLogin = value) }

    fun onTorrServePasswordChanged(value: String) = editTorrServe { it.copy(torrServePassword = value) }

    private fun editTorrServe(transform: (SettingsUiState) -> SettingsUiState) {
        torrServeCheckJob?.cancel()
        _uiState.update {
            transform(it).copy(torrServeStatusText = null, isCheckingTorrServe = false, isTorrServeConnected = false)
        }
    }

    fun onSaveTorrServeBaseUrlClick() {
        val settings = _uiState.value
        val normalized = normalizeTorrServeBaseUrl(settings.torrServeBaseUrl)
        if (normalized == null) {
            _uiState.update { it.copy(torrServeStatusText = "Неверный адрес TorrServe") }
            return
        }
        preferencesStore.writeTorrServeBaseUrl(normalized, settings.torrServeLogin, settings.torrServePassword)
        editTorrServe { it.copy(torrServeBaseUrl = normalized) }
        onCheckTorrServeClick()
    }

    fun onResetTorrServeBaseUrlClick() {
        preferencesStore.resetTorrServeBaseUrl()
        editTorrServe {
            it.copy(torrServeBaseUrl = preferencesStore.readTorrServeBaseUrl(), torrServeLogin = "", torrServePassword = "")
        }
        onCheckTorrServeClick()
    }

    fun onLostFilmHostChanged(value: String) {
        lostFilmCheckJob?.cancel()
        _uiState.update {
            it.copy(lostFilmHost = value, lostFilmHostStatusText = null, isCheckingLostFilmHost = false)
        }
    }

    fun onSelectLostFilmMirror(url: String) {
        onLostFilmHostChanged(url)
        onSaveLostFilmHostClick()
    }

    fun onToggleLostFilmAutomaticMirror() {
        val enabled = !_uiState.value.isLostFilmAutomaticMirror
        preferencesStore.writeLostFilmAutomaticMirror(enabled)
        LostFilmBaseUrl.setAutomatic(enabled)
        _uiState.update { it.copy(isLostFilmAutomaticMirror = enabled) }
    }

    fun onCheckLostFilmHostClick() {
        if (lostFilmCheckJob?.isActive == true) return
        val address = normalizeLostFilmHost(_uiState.value.lostFilmHost)
        if (address == null) {
            _uiState.update { it.copy(lostFilmHostStatusText = "Неверный адрес сервера") }
            return
        }
        _uiState.update { it.copy(isCheckingLostFilmHost = true, lostFilmHostStatusText = null) }
        lostFilmCheckJob = viewModelScope.launch {
            val available = withContext(ioDispatcher) { lostFilmMirrorChecker.isAvailable(address) }
            _uiState.update {
                it.copy(
                    isCheckingLostFilmHost = false,
                    lostFilmHostStatusText = if (available) "Сервер доступен" else "Сервер недоступен",
                )
            }
        }
    }

    fun onSaveLostFilmHostClick() {
        val normalized = normalizeLostFilmHost(_uiState.value.lostFilmHost)
        if (normalized == null) {
            _uiState.update { it.copy(lostFilmHostStatusText = "Неверный адрес сервера") }
            return
        }
        lostFilmCheckJob?.cancel()
        preferencesStore.writeLostFilmBaseUrl(normalized)
        val previousBaseUrl = LostFilmBaseUrl.get()
        LostFilmBaseUrl.set(normalized)
        if (LostFilmBaseUrl.get() != previousBaseUrl && _uiState.value.channelMode != AndroidTvChannelMode.DISABLED) {
            viewModelScope.launch(ioDispatcher) { homeChannelSyncManager.syncNow() }
        }
        _uiState.update {
            it.copy(
                lostFilmHost = normalized,
                isCheckingLostFilmHost = false,
                lostFilmHostStatusText = "Адрес сохранен",
            )
        }
    }

    fun onResetLostFilmHostClick() {
        lostFilmCheckJob?.cancel()
        preferencesStore.resetLostFilmBaseUrl()
        val restored = preferencesStore.readLostFilmBaseUrl()
        val previousBaseUrl = LostFilmBaseUrl.get()
        LostFilmBaseUrl.set(restored)
        if (LostFilmBaseUrl.get() != previousBaseUrl && _uiState.value.channelMode != AndroidTvChannelMode.DISABLED) {
            viewModelScope.launch(ioDispatcher) { homeChannelSyncManager.syncNow() }
        }
        _uiState.update {
            it.copy(
                lostFilmHost = restored,
                isCheckingLostFilmHost = false,
                lostFilmHostStatusText = "Адрес сброшен",
            )
        }
    }

    fun onCheckTorrServeClick() {
        if (torrServeCheckJob?.isActive == true) return
        val settings = _uiState.value
        _uiState.update { it.copy(isCheckingTorrServe = true, isTorrServeConnected = false, torrServeStatusText = "Проверяем TorrServe…") }
        torrServeCheckJob = viewModelScope.launch {
            val result = runCatching {
                withContext(ioDispatcher) {
                    torrServeEndpointChecker.check(settings.torrServeBaseUrl, settings.torrServeLogin, settings.torrServePassword)
                }
            }.getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                null
            }
            _uiState.update {
                val message = when {
                    result == null -> "Не удалось проверить TorrServe"
                    result.normalizedBaseUrl == null -> "Неверный адрес TorrServe"
                    result.isEndpointReachable -> "Подключён"
                    result.isAuthorizationRejected -> "Проверьте логин и пароль"
                    else -> "Сервер по указанному адресу не ответил"
                }
                it.copy(
                    isTorrServeConnected = result?.isEndpointReachable == true,
                    torrServeStatusText = message,
                    isCheckingTorrServe = false,
                )
            }
        }
    }

    fun onCheckForUpdatesClick() {
        val now = System.currentTimeMillis()
        if (now - lastCheckTimestamp < debounceIntervalMs) return
        lastCheckTimestamp = now
        refreshUpdateInfo()
    }

    fun onInstallUpdateFailed() {
        _uiState.update {
            it.copy(
                statusText = INSTALL_UPDATE_FAILED_MESSAGE,
                isDownloadingUpdate = false,
                installDownloadProgress = null,
            )
        }
    }

    fun onInstallDownloadProgress(isDownloading: Boolean) {
        _uiState.update {
            it.copy(
                isDownloadingUpdate = isDownloading,
                installDownloadProgress = if (isDownloading) it.installDownloadProgress else null,
                statusText = if (isDownloading) DOWNLOADING_UPDATE_MESSAGE else it.statusText,
            )
        }
    }

    private fun onInstallDownloadProgress(progress: Int) {
        _uiState.update {
            it.copy(
                isDownloadingUpdate = true,
                installDownloadProgress = progress.coerceIn(0, 100),
                statusText = "$DOWNLOADING_UPDATE_MESSAGE ${progress.coerceIn(0, 100)}%",
            )
        }
    }

    fun installUpdate(apkUrl: String) {
        if (installJob?.isActive == true) return
        _uiState.update {
            it.copy(
                statusText = "$DOWNLOADING_UPDATE_MESSAGE 0%",
                isDownloadingUpdate = true,
                installDownloadProgress = 0,
            )
        }
        installJob = viewModelScope.launch(ioDispatcher) {
            try {
                onInstallDownloadProgress(true)
                val launched = releaseApkLauncher.launch(
                    apkUrl = apkUrl,
                    onDownloadingChange = { onInstallDownloadProgress(it) },
                    onDownloadProgress = { onInstallDownloadProgress(it) },
                )
                if (launched) {
                    _uiState.update {
                        it.copy(
                            statusText = INSTALLER_OPENED_MESSAGE,
                            isDownloadingUpdate = false,
                            installDownloadProgress = null,
                        )
                    }
                } else {
                    onInstallUpdateFailed()
                }
            } finally {
                installJob = null
            }
        }
    }

    private fun refreshUpdateInfo() {
        val requestToken = ++refreshRequestToken
        activeRefreshJob?.cancel()
        _uiState.update {
            it.copy(
                statusText = CHECKING_UPDATES_MESSAGE,
                isCheckingForUpdates = true,
                isDownloadingUpdate = false,
                installDownloadProgress = null,
            )
        }
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
        torrServeEndpointChecker: TorrServeEndpointChecker,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        debounceIntervalMs: Long,
        lostFilmMirrorChecker: LostFilmMirrorChecker = LostFilmMirrorChecker(okhttp3.OkHttpClient()),
    ) : this(
        preferencesStore = preferencesStore,
        appUpdateCoordinator = appUpdateCoordinator,
        homeChannelSyncManager = homeChannelSyncManager,
        homeChannelBackgroundScheduler = homeChannelBackgroundScheduler,
        appUpdateBackgroundScheduler = appUpdateBackgroundScheduler,
        releaseApkLauncher = releaseApkLauncher,
        torrServeEndpointChecker = torrServeEndpointChecker,
        lostFilmMirrorChecker = lostFilmMirrorChecker,
        ioDispatcher = ioDispatcher,
    ) {
        this.debounceIntervalMs = debounceIntervalMs
    }
}

private fun SettingsUiState.toCheckedState(result: AppUpdateRefreshResult): SettingsUiState = when (result) {
    is AppUpdateRefreshResult.UpToDate -> copy(
        installedVersionText = result.installedVersion, savedAppUpdate = null,
        latestVersionText = result.installedVersion, statusText = "Установлена последняя версия",
        isCheckingForUpdates = false, isDownloadingUpdate = false, installDownloadProgress = null, installUrl = null,
    )
    is AppUpdateRefreshResult.UpdateSaved -> copy(
        savedAppUpdate = result.savedUpdate.copy(manuallyChecked = true),
        latestVersionText = result.savedUpdate.latestVersion, statusText = "Доступно обновление",
        isCheckingForUpdates = false, isDownloadingUpdate = false, installDownloadProgress = null, installUrl = result.savedUpdate.apkUrl,
    )
    is AppUpdateRefreshResult.FailedKeptPrevious -> copy(
        installedVersionText = result.installedVersion, statusText = result.message,
        isCheckingForUpdates = false, isDownloadingUpdate = false, installDownloadProgress = null,
    )
    is AppUpdateRefreshResult.FailedEmpty -> copy(
        installedVersionText = result.installedVersion, savedAppUpdate = null,
        latestVersionText = null, statusText = result.message,
        isCheckingForUpdates = false, isDownloadingUpdate = false, installDownloadProgress = null, installUrl = null,
    )
}

private const val CHECKING_UPDATES_MESSAGE = "Проверяем обновления..."
private const val DOWNLOADING_UPDATE_MESSAGE = "Скачивание обновления..."
private const val INSTALLER_OPENED_MESSAGE = "Открыт установщик обновления"
private const val INSTALL_UPDATE_FAILED_MESSAGE = "Не удалось открыть обновление."
private const val DEFAULT_DEBOUNCE_INTERVAL_MS = 1000L
