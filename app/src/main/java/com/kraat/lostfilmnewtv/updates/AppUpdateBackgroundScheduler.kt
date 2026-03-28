package com.kraat.lostfilmnewtv.updates

import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class AppUpdateBackgroundScheduler(
    private val readMode: () -> UpdateCheckMode,
    private val workManager: WorkManager,
) {
    fun syncForCurrentMode() {
        try {
            when (readMode()) {
                UpdateCheckMode.MANUAL -> {
                    workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                    workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
                }

                UpdateCheckMode.QUIET_CHECK -> {
                    workManager.enqueueUniquePeriodicWork(
                        UNIQUE_WORK_NAME,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        PeriodicWorkRequestBuilder<AppUpdateBackgroundWorker>(
                            REFRESH_INTERVAL_HOURS,
                            TimeUnit.HOURS,
                        )
                            .setConstraints(
                                Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                    .build(),
                            )
                            .build(),
                    )
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Failed to sync update schedule", error)
        }
    }

    fun requestImmediateRefresh() {
        try {
            when (readMode()) {
                UpdateCheckMode.MANUAL -> {
                    workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
                }

                UpdateCheckMode.QUIET_CHECK -> {
                    workManager.enqueueUniqueWork(
                        IMMEDIATE_WORK_NAME,
                        ExistingWorkPolicy.KEEP,
                        OneTimeWorkRequestBuilder<AppUpdateBackgroundWorker>()
                            .setConstraints(
                                Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                    .build(),
                            )
                            .build(),
                    )
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Failed to request immediate refresh", error)
        }
    }

    companion object {
        internal const val UNIQUE_WORK_NAME = "app-update-quiet-check"
        internal const val IMMEDIATE_WORK_NAME = "app-update-quiet-check-immediate"
        private const val REFRESH_INTERVAL_HOURS = 6L
        private const val TAG = "AppUpdateScheduler"
    }
}
