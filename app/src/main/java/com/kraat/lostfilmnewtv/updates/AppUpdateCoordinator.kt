package com.kraat.lostfilmnewtv.updates

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppUpdateCoordinator(
    private val installedVersion: String,
    private val store: AppUpdateAvailabilityStore,
    private val checkForUpdates: suspend () -> AppUpdateInfo,
) {
    private val _savedUpdateState = MutableStateFlow(seedSavedUpdate())
    val savedUpdateState: StateFlow<SavedAppUpdate?> = _savedUpdateState.asStateFlow()

    suspend fun refreshSavedUpdateState(): AppUpdateRefreshResult {
        return when (val updateInfo = checkForUpdates()) {
            is AppUpdateInfo.UpToDate -> {
                store.clearSavedUpdate()
                _savedUpdateState.value = null
                AppUpdateRefreshResult.UpToDate(installedVersion = updateInfo.installedVersion)
            }

            is AppUpdateInfo.UpdateAvailable -> {
                val savedUpdate = SavedAppUpdate(
                    latestVersion = updateInfo.latestVersion,
                    apkUrl = updateInfo.apkUrl,
                )
                store.writeSavedUpdate(savedUpdate)
                _savedUpdateState.value = savedUpdate
                AppUpdateRefreshResult.UpdateSaved(savedUpdate = savedUpdate)
            }

            is AppUpdateInfo.Error -> {
                val previousSavedUpdate = _savedUpdateState.value
                if (previousSavedUpdate != null) {
                    AppUpdateRefreshResult.FailedKeptPrevious(
                        installedVersion = updateInfo.installedVersion,
                        message = updateInfo.message,
                    )
                } else {
                    AppUpdateRefreshResult.FailedEmpty(
                        installedVersion = updateInfo.installedVersion,
                        message = updateInfo.message,
                    )
                }
            }
        }
    }

    private fun seedSavedUpdate(): SavedAppUpdate? {
        val savedUpdate = store.readSavedUpdate() ?: return null
        if (!VersionComparator.isNewerThan(savedUpdate.latestVersion, installedVersion)) {
            store.clearSavedUpdate()
            return null
        }

        return savedUpdate
    }
}
