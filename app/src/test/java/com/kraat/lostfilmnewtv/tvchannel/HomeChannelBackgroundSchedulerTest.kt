package com.kraat.lostfilmnewtv.tvchannel

import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails

class HomeChannelBackgroundSchedulerTest {
    @Test
    fun activeMode_enqueuesUniqueOneTimeRefreshWork() {
        val workManager = mock(WorkManager::class.java)
        val scheduler = HomeChannelBackgroundScheduler(
            readMode = { AndroidTvChannelMode.ALL_NEW },
            workManager = workManager,
        )

        scheduler.requestImmediateRefresh()

        val invocation = mockingDetails(workManager).invocations.single {
            it.method.name == "enqueueUniqueWork"
        }

        assertEquals(HomeChannelBackgroundScheduler.IMMEDIATE_WORK_NAME, invocation.arguments[0])
        assertEquals(ExistingWorkPolicy.KEEP, invocation.arguments[1])
        assertEquals(
            HomeChannelRefreshWorker::class.java.name,
            (invocation.arguments[2] as OneTimeWorkRequest).workSpec.workerClassName,
        )
    }

    @Test
    fun disabledMode_cancelsImmediateRefreshWork() {
        val workManager = mock(WorkManager::class.java)
        val scheduler = HomeChannelBackgroundScheduler(
            readMode = { AndroidTvChannelMode.DISABLED },
            workManager = workManager,
        )

        scheduler.requestImmediateRefresh()

        val invocation = mockingDetails(workManager).invocations.single {
            it.method.name == "cancelUniqueWork"
        }

        assertEquals(HomeChannelBackgroundScheduler.IMMEDIATE_WORK_NAME, invocation.arguments[0])
    }

    @Test
    fun activeMode_enqueuesUniquePeriodicWork() {
        val workManager = mock(WorkManager::class.java)
        val scheduler = HomeChannelBackgroundScheduler(
            readMode = { AndroidTvChannelMode.ALL_NEW },
            workManager = workManager,
        )

        scheduler.syncForCurrentMode()

        val invocation = mockingDetails(workManager).invocations.single {
            it.method.name == "enqueueUniquePeriodicWork"
        }

        assertEquals(HomeChannelBackgroundScheduler.UNIQUE_WORK_NAME, invocation.arguments[0])
        assertEquals(ExistingPeriodicWorkPolicy.UPDATE, invocation.arguments[1])
        assertEquals(
            HomeChannelRefreshWorker::class.java.name,
            (invocation.arguments[2] as PeriodicWorkRequest).workSpec.workerClassName,
        )
    }

    @Test
    fun disabledMode_cancelsUniquePeriodicWork() {
        val workManager = mock(WorkManager::class.java)
        val scheduler = HomeChannelBackgroundScheduler(
            readMode = { AndroidTvChannelMode.DISABLED },
            workManager = workManager,
        )

        scheduler.syncForCurrentMode()

        val invocation = mockingDetails(workManager).invocations.single {
            it.method.name == "cancelUniqueWork"
        }

        assertEquals(HomeChannelBackgroundScheduler.UNIQUE_WORK_NAME, invocation.arguments[0])
    }

    @Test
    fun scheduledWork_usesConnectedNetworkConstraintAndSixHourInterval() {
        val workManager = mock(WorkManager::class.java)
        val scheduler = HomeChannelBackgroundScheduler(
            readMode = { AndroidTvChannelMode.UNWATCHED },
            workManager = workManager,
        )

        scheduler.syncForCurrentMode()

        val request = mockingDetails(workManager).invocations.single {
            it.method.name == "enqueueUniquePeriodicWork"
        }.arguments[2] as PeriodicWorkRequest
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(TimeUnit.HOURS.toMillis(6), request.workSpec.intervalDuration)
        assertEquals(HomeChannelRefreshWorker::class.java.name, request.workSpec.workerClassName)
    }
}
