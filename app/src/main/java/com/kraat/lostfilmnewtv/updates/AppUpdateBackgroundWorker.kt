package com.kraat.lostfilmnewtv.updates

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters

class AppUpdateBackgroundWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val provider = applicationContext as? AppUpdateBackgroundWorkerProvider
            ?: return Result.retry()
        return provider.appUpdateCoordinator.refreshSavedUpdateState().toWorkerResult()
    }
}

interface AppUpdateBackgroundWorkerProvider {
    val appUpdateCoordinator: AppUpdateCoordinator
}

internal fun AppUpdateRefreshResult.toWorkerResult(): ListenableWorker.Result {
    return when (this) {
        is AppUpdateRefreshResult.UpToDate,
        is AppUpdateRefreshResult.UpdateSaved,
        is AppUpdateRefreshResult.FailedKeptPrevious,
        is AppUpdateRefreshResult.FailedEmpty,
        -> ListenableWorker.Result.success()
    }
}
