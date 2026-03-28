package com.kraat.lostfilmnewtv.updates

import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
    fun checkForUpdate_returnsUpToDate_whenInstalledVersionIsNewerThanLatestRelease() = runTest {
        val repository = AppUpdateRepository(
            installedVersion = "v2026.03.22.101",
            releaseClient = fakeClient(
                GitHubRelease(
                    version = "v2026.03.21.100",
                    apkUrl = "https://example.test/app-v2026.03.21.100.apk",
                ),
            ),
        )

        val updateInfo = repository.checkForUpdate()

        assertEquals(
            AppUpdateInfo.UpToDate(installedVersion = "v2026.03.22.101"),
            updateInfo,
        )
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
        assertEquals("Не удалось найти APK для обновления.", updateInfo.message)
    }

    @Test
    fun checkForUpdate_returnsUserFacingError_whenReleaseFetchFails() = runTest {
        val repository = AppUpdateRepository(
            installedVersion = "1.0.0",
            releaseClient = object : GitHubReleaseClient(httpClient = okhttp3.OkHttpClient()) {
                override suspend fun fetchLatestRelease(): GitHubRelease {
                    throw java.io.IOException("HTTP 500 from releases API")
                }
            },
        )

        val updateInfo = repository.checkForUpdate()

        assertTrue(updateInfo is AppUpdateInfo.Error)
        assertEquals("1.0.0", (updateInfo as AppUpdateInfo.Error).installedVersion)
        assertEquals("Не удалось проверить обновления.", updateInfo.message)
    }

    @Test
    fun checkForUpdate_rethrowsCancellationException() = runTest {
        val repository = AppUpdateRepository(
            installedVersion = "1.0.0",
            releaseClient = object : GitHubReleaseClient(httpClient = okhttp3.OkHttpClient()) {
                override suspend fun fetchLatestRelease(): GitHubRelease {
                    throw CancellationException("cancelled")
                }
            },
        )

        try {
            repository.checkForUpdate()
            fail("Expected CancellationException")
        } catch (error: CancellationException) {
            assertEquals("cancelled", error.message)
        }
    }

    private fun fakeClient(release: GitHubRelease): GitHubReleaseClient =
        object : GitHubReleaseClient(httpClient = okhttp3.OkHttpClient()) {
            override suspend fun fetchLatestRelease(): GitHubRelease = release
        }
}
