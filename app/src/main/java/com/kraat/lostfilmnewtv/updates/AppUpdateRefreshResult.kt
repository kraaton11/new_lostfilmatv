package com.kraat.lostfilmnewtv.updates

sealed interface AppUpdateRefreshResult {
    data class UpToDate(val installedVersion: String) : AppUpdateRefreshResult

    data class UpdateSaved(val savedUpdate: SavedAppUpdate) : AppUpdateRefreshResult

    data class FailedKeptPrevious(
        val installedVersion: String,
        val message: String,
    ) : AppUpdateRefreshResult

    data class FailedEmpty(
        val installedVersion: String,
        val message: String,
    ) : AppUpdateRefreshResult
}
