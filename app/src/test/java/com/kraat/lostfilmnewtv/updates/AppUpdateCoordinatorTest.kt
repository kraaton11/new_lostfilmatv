package com.kraat.lostfilmnewtv.updates

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppUpdateCoordinatorTest {
    @Test
    fun init_clearsSavedUpdate_whenInstalledVersionIsAlreadyCurrent() = runTest {
        val store = testStore("app-update-coordinator-init").apply {
            writeSavedUpdate(
                SavedAppUpdate(
                    latestVersion = "1.2.0",
                    apkUrl = "https://example.test/app-1.2.0.apk",
                ),
            )
        }

        val coordinator = AppUpdateCoordinator(
            installedVersion = "1.2.0",
            store = store,
            checkForUpdates = { error("checkForUpdates should not be called during init") },
        )

        assertNull(coordinator.savedUpdateState.value)
        assertNull(store.readSavedUpdate())
    }

    @Test
    fun refreshSavedUpdateState_savesAndEmitsUpdateAvailable() = runTest {
        val store = testStore("app-update-coordinator-save")
        val checker = FakeUpdateChecker().apply {
            enqueue(
                AppUpdateInfo.UpdateAvailable(
                    installedVersion = "1.0.0",
                    latestVersion = "1.2.0",
                    apkUrl = "https://example.test/app-1.2.0.apk",
                ),
            )
        }
        val coordinator = AppUpdateCoordinator(
            installedVersion = "1.0.0",
            store = store,
            checkForUpdates = checker::invoke,
        )

        val result = coordinator.refreshSavedUpdateState()

        assertEquals(
            AppUpdateRefreshResult.UpdateSaved(
                savedUpdate = SavedAppUpdate(
                    latestVersion = "1.2.0",
                    apkUrl = "https://example.test/app-1.2.0.apk",
                ),
            ),
            result,
        )
        assertEquals(
            SavedAppUpdate(
                latestVersion = "1.2.0",
                apkUrl = "https://example.test/app-1.2.0.apk",
            ),
            store.readSavedUpdate(),
        )
        assertEquals(store.readSavedUpdate(), coordinator.savedUpdateState.value)
    }

    @Test
    fun refreshSavedUpdateState_clearsSavedUpdate_whenRepositoryReturnsUpToDate() = runTest {
        val store = testStore("app-update-coordinator-clear")
        val checker = FakeUpdateChecker().apply {
            enqueue(
                AppUpdateInfo.UpdateAvailable(
                    installedVersion = "1.0.0",
                    latestVersion = "1.2.0",
                    apkUrl = "https://example.test/app-1.2.0.apk",
                ),
            )
            enqueue(
                AppUpdateInfo.UpToDate(installedVersion = "1.0.0"),
            )
        }
        val coordinator = AppUpdateCoordinator(
            installedVersion = "1.0.0",
            store = store,
            checkForUpdates = checker::invoke,
        )

        coordinator.refreshSavedUpdateState()
        val result = coordinator.refreshSavedUpdateState()

        assertEquals(
            AppUpdateRefreshResult.UpToDate(installedVersion = "1.0.0"),
            result,
        )
        assertNull(store.readSavedUpdate())
        assertNull(coordinator.savedUpdateState.value)
    }

    @Test
    fun refreshSavedUpdateState_keepsPreviousSavedUpdate_whenRepositoryReturnsError() = runTest {
        val store = testStore("app-update-coordinator-keep").apply {
            writeSavedUpdate(
                SavedAppUpdate(
                    latestVersion = "1.2.0",
                    apkUrl = "https://example.test/app-1.2.0.apk",
                ),
            )
        }
        val coordinator = AppUpdateCoordinator(
            installedVersion = "1.0.0",
            store = store,
            checkForUpdates = {
                AppUpdateInfo.Error(
                    installedVersion = "1.0.0",
                    message = "Не удалось проверить обновления.",
                )
            },
        )

        val result = coordinator.refreshSavedUpdateState()

        assertEquals(
            AppUpdateRefreshResult.FailedKeptPrevious(
                installedVersion = "1.0.0",
                message = "Не удалось проверить обновления.",
            ),
            result,
        )
        assertEquals(store.readSavedUpdate(), coordinator.savedUpdateState.value)
    }

    @Test
    fun refreshSavedUpdateState_returnsFailedEmpty_whenThereIsNoSavedUpdateToKeep() = runTest {
        val store = testStore("app-update-coordinator-empty")
        val coordinator = AppUpdateCoordinator(
            installedVersion = "1.0.0",
            store = store,
            checkForUpdates = {
                AppUpdateInfo.Error(
                    installedVersion = "1.0.0",
                    message = "Не удалось проверить обновления.",
                )
            },
        )

        val result = coordinator.refreshSavedUpdateState()

        assertEquals(
            AppUpdateRefreshResult.FailedEmpty(
                installedVersion = "1.0.0",
                message = "Не удалось проверить обновления.",
            ),
            result,
        )
        assertNull(store.readSavedUpdate())
        assertNull(coordinator.savedUpdateState.value)
    }

    private fun testStore(prefsName: String): AppUpdateAvailabilityStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteSharedPreferences(prefsName)
        return AppUpdateAvailabilityStore(context, prefsName = prefsName)
    }

    private class FakeUpdateChecker {
        private val results = ArrayDeque<AppUpdateInfo>()

        fun enqueue(result: AppUpdateInfo) {
            results += result
        }

        suspend operator fun invoke(): AppUpdateInfo {
            return checkNotNull(results.removeFirstOrNull()) {
                "Missing fake result"
            }
        }
    }
}
