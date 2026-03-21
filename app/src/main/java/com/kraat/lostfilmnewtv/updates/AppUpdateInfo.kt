package com.kraat.lostfilmnewtv.updates

sealed interface AppUpdateInfo {
    data class UpToDate(val installedVersion: String) : AppUpdateInfo

    data class UpdateAvailable(
        val installedVersion: String,
        val latestVersion: String,
        val apkUrl: String,
    ) : AppUpdateInfo

    data class Error(
        val installedVersion: String,
        val message: String,
    ) : AppUpdateInfo
}
