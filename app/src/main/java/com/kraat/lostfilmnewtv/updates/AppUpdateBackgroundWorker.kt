package com.kraat.lostfilmnewtv.updates

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AppUpdateBackgroundWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: AppUpdateCoordinator,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        coordinator.refreshSavedUpdateState().toWorkerResult()
}

interface AppUpdateBackgroundWorkerProvider {
    val appUpdateCoordinator: AppUpdateCoordinator
}

internal fun AppUpdateRefreshResult.toWorkerResult(): ListenableWorker.Result =
    when (this) {
        is AppUpdateRefreshResult.UpToDate,
        is AppUpdateRefreshResult.UpdateSaved,
        is AppUpdateRefreshResult.FailedKeptPrevious,
        -> ListenableWorker.Result.success()
        is AppUpdateRefreshResult.FailedEmpty ->
            if (isTransientError(message)) ListenableWorker.Result.retry()
            else ListenableWorker.Result.success()
    }

private fun isTransientError(message: String): Boolean {
    val keywords = listOf("сети", "подключению", "таймаут", "ожидания", "лимит", "timeout", "network", "connection")
    return keywords.any { message.contains(it, ignoreCase = true) }
}
