package com.kraat.lostfilmnewtv.tvchannel

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters

class HomeChannelRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val provider = applicationContext as? HomeChannelBackgroundRefreshRunnerProvider
            ?: return Result.retry()
        return provider.homeChannelBackgroundRefreshRunner.run().toWorkerResult()
    }
}

interface HomeChannelBackgroundRefreshRunnerProvider {
    val homeChannelBackgroundRefreshRunner: HomeChannelBackgroundRefreshRunner
}

internal fun HomeChannelBackgroundRefreshOutcome.toWorkerResult(): ListenableWorker.Result {
    return when (this) {
        HomeChannelBackgroundRefreshOutcome.SKIPPED_DISABLED,
        HomeChannelBackgroundRefreshOutcome.SKIPPED_UNAUTHENTICATED,
        HomeChannelBackgroundRefreshOutcome.REFRESHED,
        -> ListenableWorker.Result.success()

        HomeChannelBackgroundRefreshOutcome.FAILED_RETRYABLE -> ListenableWorker.Result.retry()
    }
}
