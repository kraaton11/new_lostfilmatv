package com.kraat.lostfilmnewtv.tvchannel

import androidx.work.ListenableWorker
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeChannelRefreshWorkerTest {
    @Test
    fun skippedDisabled_mapsToSuccess() {
        assertEquals(
            ListenableWorker.Result.success(),
            HomeChannelBackgroundRefreshOutcome.SKIPPED_DISABLED.toWorkerResult(),
        )
    }

    @Test
    fun skippedUnauthenticated_mapsToSuccess() {
        assertEquals(
            ListenableWorker.Result.success(),
            HomeChannelBackgroundRefreshOutcome.SKIPPED_UNAUTHENTICATED.toWorkerResult(),
        )
    }

    @Test
    fun skippedRecentlyRefreshed_mapsToSuccess() {
        assertEquals(
            ListenableWorker.Result.success(),
            HomeChannelBackgroundRefreshOutcome.SKIPPED_RECENTLY_REFRESHED.toWorkerResult(),
        )
    }

    @Test
    fun refreshed_mapsToSuccess() {
        assertEquals(
            ListenableWorker.Result.success(),
            HomeChannelBackgroundRefreshOutcome.REFRESHED.toWorkerResult(),
        )
    }

    @Test
    fun retryableFailure_mapsToRetry() {
        assertEquals(
            ListenableWorker.Result.retry(),
            HomeChannelBackgroundRefreshOutcome.FAILED_RETRYABLE.toWorkerResult(),
        )
    }
}
