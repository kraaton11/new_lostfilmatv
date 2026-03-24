package com.kraat.lostfilmnewtv.updates

import androidx.work.ListenableWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateBackgroundWorkerTest {
    @Test
    fun updateSaved_mapsToSuccess() {
        assertEquals(
            ListenableWorker.Result.success(),
            AppUpdateRefreshResult.UpdateSaved(
                savedUpdate = SavedAppUpdate(
                    latestVersion = "1.2.0",
                    apkUrl = "https://example.test/app-1.2.0.apk",
                ),
            ).toWorkerResult(),
        )
    }

    @Test
    fun upToDate_mapsToSuccess() {
        assertEquals(
            ListenableWorker.Result.success(),
            AppUpdateRefreshResult.UpToDate(
                installedVersion = "1.0.0",
            ).toWorkerResult(),
        )
    }

    @Test
    fun failedKeptPrevious_mapsToSuccess() {
        assertEquals(
            ListenableWorker.Result.success(),
            AppUpdateRefreshResult.FailedKeptPrevious(
                installedVersion = "1.0.0",
                message = "Не удалось проверить обновления.",
            ).toWorkerResult(),
        )
    }

    @Test
    fun failedEmpty_mapsToSuccess_whenNonTransientError() {
        assertEquals(
            ListenableWorker.Result.success(),
            AppUpdateRefreshResult.FailedEmpty(
                installedVersion = "1.0.0",
                message = "Не удалось проверить обновления.",
            ).toWorkerResult(),
        )
    }

    @Test
    fun failedEmpty_mapsToRetry_whenNetworkError() {
        val result = AppUpdateRefreshResult.FailedEmpty(
            installedVersion = "1.0.0",
            message = "Нет подключения к сети.",
        ).toWorkerResult()

        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun failedEmpty_mapsToRetry_whenTimeoutError() {
        val result = AppUpdateRefreshResult.FailedEmpty(
            installedVersion = "1.0.0",
            message = "Превышено время ожидания соединения.",
        ).toWorkerResult()

        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun failedEmpty_mapsToRetry_whenRateLimitError() {
        val result = AppUpdateRefreshResult.FailedEmpty(
            installedVersion = "1.0.0",
            message = "Превышен лимит запросов. Попробуйте позже.",
        ).toWorkerResult()

        assertTrue(result is ListenableWorker.Result.Retry)
    }
}
