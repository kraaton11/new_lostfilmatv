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
        if (!savedUpdate.latestVersion.isNewerThan(installedVersion)) {
            store.clearSavedUpdate()
            return null
        }

        return savedUpdate
    }
}

private fun String.isNewerThan(other: String): Boolean {
    val thisParts = versionParts()
    val otherParts = other.versionParts()
    val maxSize = maxOf(thisParts.size, otherParts.size)
    for (index in 0 until maxSize) {
        val thisPart = thisParts.getOrElse(index) { 0 }
        val otherPart = otherParts.getOrElse(index) { 0 }
        if (thisPart != otherPart) {
            return thisPart > otherPart
        }
    }
    return false
}

private fun String.versionParts(): List<Int> =
    Regex("""\d+""")
        .findAll(this)
        .map { match -> match.value.toInt() }
        .toList()
