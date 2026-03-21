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
                    message = "Не удалось найти APK для обновления.",
                )

                latestRelease.version.isNewerThan(installedVersion).not() -> AppUpdateInfo.UpToDate(
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
                message = "Не удалось проверить обновления.",
            )
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
