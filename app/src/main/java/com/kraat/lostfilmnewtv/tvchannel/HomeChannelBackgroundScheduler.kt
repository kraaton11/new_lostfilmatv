package com.kraat.lostfilmnewtv.tvchannel

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class HomeChannelBackgroundScheduler(
    private val readMode: () -> AndroidTvChannelMode,
    private val workManager: WorkManager,
) {
    fun syncForCurrentMode() {
        when (readMode()) {
            AndroidTvChannelMode.DISABLED -> {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            }

            AndroidTvChannelMode.ALL_NEW,
            AndroidTvChannelMode.UNWATCHED,
            -> {
                workManager.enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<HomeChannelRefreshWorker>(REFRESH_INTERVAL_HOURS, TimeUnit.HOURS)
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build(),
                        )
                        .build(),
                )
            }
        }
    }

    companion object {
        internal const val UNIQUE_WORK_NAME = "android-tv-home-channel-refresh"
        private const val REFRESH_INTERVAL_HOURS = 6L
    }
}
