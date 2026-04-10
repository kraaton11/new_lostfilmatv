package com.kraat.lostfilmnewtv.updates

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails

class AppUpdateBackgroundSchedulerTest {
    @Test
    fun quietMode_syncForCurrentMode_enqueuesUniquePeriodicWork() {
        val workManager = mock(WorkManager::class.java)
        val scheduler = AppUpdateBackgroundScheduler(
            readMode = { UpdateCheckMode.QUIET_CHECK },
            workManager = workManager,
        )

        scheduler.syncForCurrentMode()

        val invocation = mockingDetails(workManager).invocations.single {
            it.method.name == "enqueueUniquePeriodicWork"
        }

        assertEquals(AppUpdateBackgroundScheduler.UNIQUE_WORK_NAME, invocation.arguments[0])
        assertEquals(ExistingPeriodicWorkPolicy.UPDATE, invocation.arguments[1])
        assertEquals(
            AppUpdateBackgroundWorker::class.java.name,
            (invocation.arguments[2] as PeriodicWorkRequest).workSpec.workerClassName,
        )
    }

    @Test
    fun manualMode_syncForCurrentMode_cancelsPeriodicAndImmediateWork() {
        val workManager = mock(WorkManager::class.java)
        val scheduler = AppUpdateBackgroundScheduler(
            readMode = { UpdateCheckMode.MANUAL },
            workManager = workManager,
        )

        scheduler.syncForCurrentMode()

        val canceledNames = mockingDetails(workManager).invocations
            .filter { it.method.name == "cancelUniqueWork" }
            .map { it.arguments[0] }

        assertEquals(
            listOf(
                AppUpdateBackgroundScheduler.UNIQUE_WORK_NAME,
                AppUpdateBackgroundScheduler.IMMEDIATE_WORK_NAME,
            ),
            canceledNames,
        )
    }

    @Test
    fun quietMode_requestImmediateRefresh_enqueuesUniqueOneTimeWork() {
        val workManager = mock(WorkManager::class.java)
        val scheduler = AppUpdateBackgroundScheduler(
            readMode = { UpdateCheckMode.QUIET_CHECK },
            workManager = workManager,
        )

        scheduler.requestImmediateRefresh()

        val invocation = mockingDetails(workManager).invocations.single {
            it.method.name == "enqueueUniqueWork"
        }

        assertEquals(AppUpdateBackgroundScheduler.IMMEDIATE_WORK_NAME, invocation.arguments[0])
        assertEquals(ExistingWorkPolicy.KEEP, invocation.arguments[1])
        assertEquals(
            AppUpdateBackgroundWorker::class.java.name,
            (invocation.arguments[2] as OneTimeWorkRequest).workSpec.workerClassName,
        )
    }

    @Test
    fun scheduledPeriodicWork_usesConnectedNetworkAndSixHourInterval() {
        val workManager = mock(WorkManager::class.java)
        val scheduler = AppUpdateBackgroundScheduler(
            readMode = { UpdateCheckMode.QUIET_CHECK },
            workManager = workManager,
        )

        scheduler.syncForCurrentMode()

        val request = mockingDetails(workManager).invocations.single {
            it.method.name == "enqueueUniquePeriodicWork"
        }.arguments[2] as PeriodicWorkRequest

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(6L * 60L * 60L * 1000L, request.workSpec.intervalDuration)
        assertEquals(AppUpdateBackgroundWorker::class.java.name, request.workSpec.workerClassName)
    }
}
