package com.kraat.lostfilmnewtv.updates

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {

    @Test
    fun checkForUpdate_returnsUpdateAvailable_whenLatestVersionDiffers() = runTest {
        val repository = AppUpdateRepository(
            installedVersion = "1.0.0",
            releaseClient = fakeClient(
                GitHubRelease(
                    version = "1.1.0",
                    apkUrl = "https://example.test/app-1.1.0.apk",
                ),
            ),
        )

        val updateInfo = repository.checkForUpdate()

        assertEquals(
            AppUpdateInfo.UpdateAvailable(
                installedVersion = "1.0.0",
                latestVersion = "1.1.0",
                apkUrl = "https://example.test/app-1.1.0.apk",
            ),
            updateInfo,
        )
    }

    @Test
    fun checkForUpdate_returnsUpToDate_whenVersionsMatch() = runTest {
        val repository = AppUpdateRepository(
            installedVersion = "1.1.0",
            releaseClient = fakeClient(
                GitHubRelease(
                    version = "1.1.0",
                    apkUrl = "https://example.test/app-1.1.0.apk",
                ),
            ),
        )

        val updateInfo = repository.checkForUpdate()

        assertEquals(AppUpdateInfo.UpToDate(installedVersion = "1.1.0"), updateInfo)
    }

    @Test
    fun checkForUpdate_returnsError_whenReleaseHasNoApkAsset() = runTest {
        val repository = AppUpdateRepository(
            installedVersion = "1.0.0",
            releaseClient = fakeClient(
                GitHubRelease(
                    version = "1.1.0",
                    apkUrl = null,
                ),
            ),
        )

        val updateInfo = repository.checkForUpdate()

        assertTrue(updateInfo is AppUpdateInfo.Error)
        assertEquals("1.0.0", (updateInfo as AppUpdateInfo.Error).installedVersion)
        assertEquals("Latest release does not contain an APK asset.", updateInfo.message)
    }

    private fun fakeClient(release: GitHubRelease): GitHubReleaseClient =
        object : GitHubReleaseClient(httpClient = okhttp3.OkHttpClient()) {
            override suspend fun fetchLatestRelease(): GitHubRelease = release
        }
}
