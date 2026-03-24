package com.kraat.lostfilmnewtv.updates

import android.util.Log
import java.io.IOException
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
        } catch (error: RateLimitException) {
            Log.w(TAG, "GitHub API rate limit exceeded", error)
            AppUpdateInfo.Error(
                installedVersion = installedVersion,
                message = "Превышен лимит запросов. Попробуйте позже.",
            )
        } catch (error: java.net.SocketTimeoutException) {
            Log.w(TAG, "Update check timed out", error)
            AppUpdateInfo.Error(
                installedVersion = installedVersion,
                message = "Превышено время ожидания соединения.",
            )
        } catch (error: java.net.UnknownHostException) {
            Log.w(TAG, "No network connectivity", error)
            AppUpdateInfo.Error(
                installedVersion = installedVersion,
                message = "Нет подключения к сети.",
            )
        } catch (error: IOException) {
            Log.e(TAG, "Update check failed with IO error", error)
            AppUpdateInfo.Error(
                installedVersion = installedVersion,
                message = "Не удалось проверить обновления.",
            )
        } catch (error: Exception) {
            Log.e(TAG, "Unexpected error during update check", error)
            AppUpdateInfo.Error(
                installedVersion = installedVersion,
                message = "Не удалось проверить обновления.",
            )
        }

    private companion object {
        const val TAG = "AppUpdateRepository"
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
