package com.kraat.lostfilmnewtv.tvchannel

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class HomeChannelRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val runner: HomeChannelBackgroundRefreshRunner,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        runner.run().toWorkerResult()
}

interface HomeChannelBackgroundRefreshRunnerProvider {
    val homeChannelBackgroundRefreshRunner: HomeChannelBackgroundRefreshRunner
}

internal fun HomeChannelBackgroundRefreshOutcome.toWorkerResult(): ListenableWorker.Result =
    when (this) {
        HomeChannelBackgroundRefreshOutcome.SKIPPED_DISABLED,
        HomeChannelBackgroundRefreshOutcome.SKIPPED_UNAUTHENTICATED,
        HomeChannelBackgroundRefreshOutcome.SKIPPED_RECENTLY_REFRESHED,
        HomeChannelBackgroundRefreshOutcome.REFRESHED,
        -> ListenableWorker.Result.success()
        HomeChannelBackgroundRefreshOutcome.FAILED_RETRYABLE -> ListenableWorker.Result.retry()
    }
