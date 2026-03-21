package com.kraat.lostfilmnewtv.updates

import java.util.concurrent.CancellationException

class AppUpdateRepository(
    private val installedVersion: String,
    private val releaseClient: GitHubReleaseClient,
) {
    suspend fun checkForUpdate(): AppUpdateInfo =
        try {
            val latestRelease = releaseClient.fetchLatestRelease()
            when {
                latestRelease.apkUrl.isNullOrBlank() -> AppUpdateInfo.Error(
                    installedVersion = installedVersion,
                    message = "Latest release does not contain an APK asset.",
                )

                latestRelease.version == installedVersion -> AppUpdateInfo.UpToDate(
                    installedVersion = installedVersion,
                )

                else -> AppUpdateInfo.UpdateAvailable(
                    installedVersion = installedVersion,
                    latestVersion = latestRelease.version,
                    apkUrl = latestRelease.apkUrl,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppUpdateInfo.Error(
                installedVersion = installedVersion,
                message = error.message ?: "Failed to check for app updates.",
            )
        }
}
