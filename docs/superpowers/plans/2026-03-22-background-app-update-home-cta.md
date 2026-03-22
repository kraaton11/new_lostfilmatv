# Background App Update Home CTA Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add background quiet update checks plus a persistent `Обновить` CTA on `Home` that appears after a newer APK is found and launches the install handoff directly.

**Architecture:** Keep `AppUpdateRepository` as the GitHub release network layer and add a small update-state slice around it. A new `AppUpdateAvailabilityStore` persists the actionable APK payload, an `AppUpdateCoordinator` owns version comparison plus a shared `StateFlow`, and a `WorkManager` scheduler/worker pair handles quiet background checks. `Settings` and `Home` both consume the same coordinator-owned saved update state so found updates stay visible across screens and across transient network failures.

**Tech Stack:** Kotlin, AndroidX WorkManager, SharedPreferences, Coroutines/Flow, ViewModel, Compose for TV, Navigation Compose, JUnit 4, Robolectric, Mockito

---

**Base Spec Reference:** `docs/superpowers/specs/2026-03-21-app-update-settings-design.md`

**Delta Spec Reference:** `docs/superpowers/specs/2026-03-22-background-app-update-home-cta-design.md`

**Dependency Note:** Reuse the existing AndroidX WorkManager dependency surface already present in the project from the Android TV channel background refresh work. Do not add a second scheduling stack.

## Planned File Structure

- Create: `app/src/main/java/com/kraat/lostfilmnewtv/updates/SavedAppUpdate.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateAvailabilityStore.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateRefreshResult.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateCoordinator.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundScheduler.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundWorker.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/MainActivity.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsRoute.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsUiState.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeUiState.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateAvailabilityStoreTest.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateCoordinatorTest.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundSchedulerTest.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundWorkerTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/LostFilmApplicationTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/MainActivityTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModelTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModelTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeScreenTest.kt`

## Chunk 1: Saved Update State Foundation

### Task 1: Add the persisted saved-update model and store

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/updates/SavedAppUpdate.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateAvailabilityStore.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateAvailabilityStoreTest.kt`

- [ ] **Step 1: Write failing store tests for the persisted update payload**

Create `AppUpdateAvailabilityStoreTest.kt` covering:

```kotlin
@Test
fun readSavedUpdate_returnsNull_whenNothingWasSaved() { }

@Test
fun writeSavedUpdate_persistsLatestVersionAndApkUrl() { }

@Test
fun clearSavedUpdate_removesOnlySavedUpdatePayload() { }
```

Use a dedicated `prefsName` per test and assert on the store API only:

- `readSavedUpdate(): SavedAppUpdate?`
- `writeSavedUpdate(value: SavedAppUpdate)`
- `clearSavedUpdate()`

- [ ] **Step 2: Run the store tests to confirm the types do not exist yet**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.AppUpdateAvailabilityStoreTest"
```

Expected: FAIL with missing `SavedAppUpdate` and `AppUpdateAvailabilityStore` types

- [ ] **Step 3: Implement the minimal saved-update model and store**

Create:

```kotlin
data class SavedAppUpdate(
    val latestVersion: String,
    val apkUrl: String,
)
```

Create `AppUpdateAvailabilityStore.kt` backed by `SharedPreferences` with one responsibility: persist or clear the actionable update payload.

Implementation rules:

- keep this store dumb
- do not put version comparison or `BuildConfig` logic here
- do not move `UpdateCheckMode` persistence out of `PlaybackPreferencesStore`
- use separate keys for version and APK URL
- return `null` if either persisted field is missing or blank

- [ ] **Step 4: Re-run the store tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.AppUpdateAvailabilityStoreTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the saved-update store**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/updates/SavedAppUpdate.kt app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateAvailabilityStore.kt app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateAvailabilityStoreTest.kt
git commit -m "feat: add saved update availability store"
```

### Task 2: Implement the coordinator and shared observable update state

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateRefreshResult.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateCoordinator.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateCoordinatorTest.kt`

- [ ] **Step 1: Write failing coordinator tests for seeding, refresh, and error retention**

Create `AppUpdateCoordinatorTest.kt` covering:

```kotlin
@Test
fun init_clearsSavedUpdate_whenInstalledVersionIsAlreadyCurrent() = runTest { }

@Test
fun refreshSavedUpdateState_savesAndEmitsUpdateAvailable() = runTest { }

@Test
fun refreshSavedUpdateState_clearsSavedUpdate_whenRepositoryReturnsUpToDate() = runTest { }

@Test
fun refreshSavedUpdateState_keepsPreviousSavedUpdate_whenRepositoryReturnsError() = runTest { }

@Test
fun refreshSavedUpdateState_returnsFailedEmpty_whenThereIsNoSavedUpdateToKeep() = runTest { }
```

Use fakes for:

- `checkForUpdates: suspend () -> AppUpdateInfo`
- `AppUpdateAvailabilityStore`

Assert both:

- returned refresh result
- exposed observable saved state

- [ ] **Step 2: Run the coordinator tests to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.AppUpdateCoordinatorTest"
```

Expected: FAIL with missing coordinator and refresh-result types

- [ ] **Step 3: Implement the coordinator and refresh result contract**

Create a small result type in `AppUpdateRefreshResult.kt`, for example:

```kotlin
sealed interface AppUpdateRefreshResult {
    data class UpToDate(val installedVersion: String) : AppUpdateRefreshResult
    data class UpdateSaved(val savedUpdate: SavedAppUpdate) : AppUpdateRefreshResult
    data class FailedKeptPrevious(val installedVersion: String, val message: String) : AppUpdateRefreshResult
    data class FailedEmpty(val installedVersion: String, val message: String) : AppUpdateRefreshResult
}
```

Create `AppUpdateCoordinator.kt` with:

```kotlin
class AppUpdateCoordinator(
    private val installedVersion: String,
    private val store: AppUpdateAvailabilityStore,
    private val checkForUpdates: suspend () -> AppUpdateInfo,
)
```

Implementation rules:

- seed a private `MutableStateFlow<SavedAppUpdate?>` from persisted store data on construction
- if the seeded saved version is less than or equal to the installed version, clear it before exposing state
- expose `val savedUpdateState: StateFlow<SavedAppUpdate?>`
- on `UpdateAvailable`, write the payload to the store and emit it
- on `UpToDate`, clear stale saved update data and emit `null`
- on `Error`, keep previously saved update data if one exists
- do not mutate `AppUpdateRepository`
- keep version comparison inside the coordinator

- [ ] **Step 4: Re-run the coordinator tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.AppUpdateCoordinatorTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the coordinator foundation**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateRefreshResult.kt app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateCoordinator.kt app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateCoordinatorTest.kt
git commit -m "feat: add app update coordinator"
```

## Chunk 2: Background Scheduling And Lifecycle Wiring

### Task 3: Implement the quiet-update background scheduler

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundScheduler.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundSchedulerTest.kt`

- [ ] **Step 1: Write failing scheduler tests for periodic and immediate quiet checks**

Create tests for:

```kotlin
@Test
fun quietMode_syncForCurrentMode_enqueuesUniquePeriodicWork() { }

@Test
fun manualMode_syncForCurrentMode_cancelsPeriodicAndImmediateWork() { }

@Test
fun quietMode_requestImmediateRefresh_enqueuesUniqueOneTimeWork() { }

@Test
fun scheduledPeriodicWork_usesConnectedNetworkAndSixHourInterval() { }
```

Inject:

- `readMode: () -> UpdateCheckMode`
- `WorkManager`

Verify:

- periodic policy is `ExistingPeriodicWorkPolicy.UPDATE`
- immediate policy is `ExistingWorkPolicy.KEEP`
- unique work names are stable
- `NetworkType.CONNECTED` is required

- [ ] **Step 2: Run the scheduler tests to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundSchedulerTest"
```

Expected: FAIL with missing scheduler type

- [ ] **Step 3: Implement the scheduler**

Create `AppUpdateBackgroundScheduler.kt`:

```kotlin
class AppUpdateBackgroundScheduler(
    private val readMode: () -> UpdateCheckMode,
    private val workManager: WorkManager,
) {
    fun syncForCurrentMode() { ... }
    fun requestImmediateRefresh() { ... }
}
```

Implementation rules:

- use one periodic work name such as `app-update-quiet-check`
- use one immediate work name such as `app-update-quiet-check-immediate`
- schedule periodic work only for `QUIET_CHECK`
- cancel both work names for `MANUAL`
- reuse the project’s existing WorkManager style from the TV channel scheduler

- [ ] **Step 4: Re-run the scheduler tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundSchedulerTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the update scheduler**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundScheduler.kt app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundSchedulerTest.kt
git commit -m "feat: add quiet update scheduler"
```

### Task 4: Add the background worker and application-owned dependencies

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundWorker.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundWorkerTest.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/LostFilmApplicationTest.kt`

- [ ] **Step 1: Write failing worker and application dependency tests**

Create `AppUpdateBackgroundWorkerTest.kt` covering:

```kotlin
@Test
fun updateSaved_mapsToSuccess() { }

@Test
fun upToDate_mapsToSuccess() { }

@Test
fun failedKeptPrevious_mapsToSuccess() { }

@Test
fun failedEmpty_mapsToSuccess() { }
```

Extend `LostFilmApplicationTest.kt` with:

```kotlin
@Test
fun appUpdateCoordinator_isExposedAsStableApplicationDependency() { }

@Test
fun appUpdateBackgroundScheduler_isExposedAsStableApplicationDependency() { }
```

- [ ] **Step 2: Run the worker and application tests to confirm failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundWorkerTest" --tests "com.kraat.lostfilmnewtv.LostFilmApplicationTest"
```

Expected: FAIL with missing worker/provider/result-mapping/coordinator/scheduler symbols

- [ ] **Step 3: Implement the worker bridge and application wiring**

Create `AppUpdateBackgroundWorker.kt` with:

```kotlin
class AppUpdateBackgroundWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params)
```

Add a small provider interface in the same file:

```kotlin
interface AppUpdateBackgroundWorkerProvider {
    val appUpdateCoordinator: AppUpdateCoordinator
}
```

Add an internal mapping helper in the same file:

```kotlin
internal fun AppUpdateRefreshResult.toWorkerResult(): ListenableWorker.Result = ...
```

Implementation rules:

- cast `applicationContext` to `AppUpdateBackgroundWorkerProvider`
- call `appUpdateCoordinator.refreshSavedUpdateState()`
- return `Result.success()` for all coordinator-managed refresh outcomes through `toWorkerResult()`
- return `Result.retry()` only when worker bootstrap itself is broken, such as a missing provider

Modify `LostFilmApplication.kt` to:

- implement `AppUpdateBackgroundWorkerProvider`
- expose stable lazy instances for:
  - `AppUpdateAvailabilityStore`
  - `AppUpdateCoordinator`
  - `AppUpdateBackgroundScheduler`
- build the coordinator from `BuildConfig.VERSION_NAME`, the new store, and `appUpdateRepository::checkForUpdate`

- [ ] **Step 4: Re-run the worker and application tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundWorkerTest" --tests "com.kraat.lostfilmnewtv.LostFilmApplicationTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the worker bridge**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundWorker.kt app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt app/src/test/java/com/kraat/lostfilmnewtv/updates/AppUpdateBackgroundWorkerTest.kt app/src/test/java/com/kraat/lostfilmnewtv/LostFilmApplicationTest.kt
git commit -m "feat: add background app update worker"
```

### Task 5: Wire quiet-update scheduling into startup and foreground resume

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/MainActivity.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/MainActivityTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt`

- [ ] **Step 1: Extend the lifecycle tests with failing update-scheduler assertions**

Add tests for:

```kotlin
@Test
fun returningToForeground_requestsImmediateQuietUpdateRefresh() { }

@Test
fun startup_composition_schedules_background_update_refresh_once() { }
```

Use a dedicated `WorkManager` mock for update scheduling in the same style already used for the Android TV channel scheduler tests.

- [ ] **Step 2: Run the targeted lifecycle tests to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.MainActivityTest" --tests "com.kraat.lostfilmnewtv.navigation.AppNavGraphTorrServeTest"
```

Expected: FAIL because update background scheduling is not yet wired

- [ ] **Step 3: Add startup and resume scheduling hooks**

Modify `AppNavGraph.kt` so the startup `LaunchedEffect(application)` also calls:

```kotlin
application.appUpdateBackgroundScheduler.syncForCurrentMode()
```

Modify `MainActivity.kt` so the existing post-first-resume block also calls:

```kotlin
(application as? LostFilmApplication)
    ?.appUpdateBackgroundScheduler
    ?.requestImmediateRefresh()
```

Keep the current TV channel refresh behavior intact. This task adds update scheduling beside it, not instead of it.

- [ ] **Step 4: Re-run the targeted lifecycle tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.MainActivityTest" --tests "com.kraat.lostfilmnewtv.navigation.AppNavGraphTorrServeTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit lifecycle scheduling**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt app/src/main/java/com/kraat/lostfilmnewtv/MainActivity.kt app/src/test/java/com/kraat/lostfilmnewtv/MainActivityTest.kt app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt
git commit -m "feat: wire quiet update scheduling"
```

## Chunk 3: Settings Shared-State Integration

### Task 6: Move settings update checks onto the shared coordinator

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsRoute.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsUiState.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Add failing settings tests for shared saved state and background scheduler sync**

Extend `SettingsViewModelTest.kt` with tests for:

```kotlin
@Test
fun manualMode_onScreenShown_doesNotRefreshButStillShowsSavedUpdate() = runTest { }

@Test
fun quietMode_onScreenShown_refreshesSavedUpdateState() = runTest { }

@Test
fun onUpdateModeSelected_syncsBackgroundSchedule_forQuietAndManualModes() = runTest { }

@Test
fun refreshError_keepsExistingInstallUrl_whenSavedUpdateWasAlreadyPresent() = runTest { }
```

Drive the view model with:

- `savedUpdateState: StateFlow<SavedAppUpdate?>`
- `refreshSavedUpdateState: suspend () -> AppUpdateRefreshResult`
- `syncAppUpdateBackgroundSchedule: () -> Unit`

- [ ] **Step 2: Run the settings tests to confirm failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsViewModelTest"
```

Expected: FAIL because settings still depend on one-shot repository checks only

- [ ] **Step 3: Refactor settings route and view model onto the coordinator**

Update `SettingsRoute.kt` so it receives `AppUpdateCoordinator` instead of `AppUpdateRepository`.

Update the `SettingsViewModel` constructor so it accepts:

```kotlin
private val savedUpdateState: StateFlow<SavedAppUpdate?>,
private val refreshSavedUpdateState: suspend () -> AppUpdateRefreshResult,
private val syncAppUpdateBackgroundSchedule: () -> Unit,
```

Implementation rules:

- collect `savedUpdateState` inside the view model and map it into `latestVersionText` and `installUrl`
- keep `statusText` as transient view-model state for check progress and launch failure copy
- `onScreenShown()` refreshes only in `QUIET_CHECK`
- `onUpdateModeSelected()` persists the mode, triggers `syncAppUpdateBackgroundSchedule()`, and refreshes immediately only when switching into `QUIET_CHECK`
- `MANUAL` mode must keep any previously saved actionable update visible

- [ ] **Step 4: Re-run the settings tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsViewModelTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the settings integration**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsRoute.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsUiState.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModel.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModelTest.kt
git commit -m "feat: share saved update state with settings"
```

## Chunk 4: Home CTA And Navigation Handoff

### Task 7: Extend Home state to observe the saved update CTA and footer errors

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeUiState.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModel.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModelTest.kt`

- [ ] **Step 1: Add failing HomeViewModel tests for update CTA state and launch-failure feedback**

Extend `HomeViewModelTest.kt` with:

```kotlin
@Test
fun savedUpdateState_addsFooterUpdateCta_withoutChangingContentSelection() = runTest { }

@Test
fun clearedSavedUpdateState_removesFooterUpdateCta() = runTest { }

@Test
fun onInstallUpdateFailed_setsFooterErrorWithoutRemovingSavedUpdate() = runTest { }
```

Use a controllable `MutableStateFlow<SavedAppUpdate?>` and assert that the existing page-loading behavior still passes unchanged.

- [ ] **Step 2: Run the HomeViewModel tests to confirm failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.home.HomeViewModelTest"
```

Expected: FAIL because `HomeViewModel` does not yet observe update state or expose footer error text

- [ ] **Step 3: Implement Home update-state observation**

Modify `HomeUiState.kt` to add the minimal footer fields, for example:

```kotlin
val savedAppUpdate: SavedAppUpdate? = null,
val footerErrorText: String? = null,
```

Modify `HomeViewModel.kt` to accept:

```kotlin
private val savedUpdateState: StateFlow<SavedAppUpdate?> = MutableStateFlow(null),
```

Implementation rules:

- collect the saved update flow in the view model and copy it into `HomeUiState`
- keep initial poster focus behavior untouched
- add `onInstallUpdateFailed()` that sets `footerErrorText = "Не удалось открыть обновление."`
- do not put `Context` or install-launching logic inside the view model

- [ ] **Step 4: Re-run the HomeViewModel tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.home.HomeViewModelTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the Home view-model state**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeUiState.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModel.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModelTest.kt
git commit -m "feat: add saved update state to home view model"
```

### Task 8: Add the Home footer CTA row and navigation install handoff

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeScreenTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt`

- [ ] **Step 1: Add failing UI and nav-graph tests for the Home update CTA**

Extend `HomeScreenTest.kt` with:

```kotlin
@Test
fun homeScreen_withSavedUpdate_showsUpdateButtonBesideVersionInBottomRightFooter() { }
```

Extend `AppNavGraphTorrServeTest.kt` with:

```kotlin
@Test
fun home_savedUpdate_launchesInstallDirectlyFromHomeFooter() { }

@Test
fun home_installFailure_showsFooterErrorAndKeepsUpdateButton() { }
```

Use a seeded `AppUpdateCoordinator` or test override that exposes a non-null `savedUpdateState`.

- [ ] **Step 2: Run the Home UI and nav-graph tests to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.home.HomeScreenTest" --tests "com.kraat.lostfilmnewtv.navigation.AppNavGraphTorrServeTest"
```

Expected: FAIL because `Home` does not yet render or handle the update CTA

- [ ] **Step 3: Implement the Home footer CTA row and direct install launch**

Modify `HomeScreen.kt` so the bottom-right footer uses the approved `B` layout:

- when `savedAppUpdate == null`, render only the version text
- when `savedAppUpdate != null`, render:
  - `Button("Обновить")`
  - version text on the same row
- keep the footer aligned to `Alignment.BottomEnd`

Add a stable test tag such as:

```kotlin
Modifier.testTag("home-update-button")
```

Modify `AppNavGraph.kt` so the `Home` route:

- builds `HomeViewModel` with `savedUpdateState = application.appUpdateCoordinator.savedUpdateState`
- launches `application.releaseApkLauncher.launch(context, apkUrl)` directly from the footer button
- calls `homeViewModel.onInstallUpdateFailed()` when launch returns `false`

- [ ] **Step 4: Re-run the Home UI and nav-graph tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.home.HomeScreenTest" --tests "com.kraat.lostfilmnewtv.navigation.AppNavGraphTorrServeTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Run the full targeted regression suite**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.updates.*" --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsViewModelTest" --tests "com.kraat.lostfilmnewtv.ui.home.HomeViewModelTest" --tests "com.kraat.lostfilmnewtv.ui.home.HomeScreenTest" --tests "com.kraat.lostfilmnewtv.navigation.AppNavGraphTorrServeTest" --tests "com.kraat.lostfilmnewtv.MainActivityTest" --tests "com.kraat.lostfilmnewtv.LostFilmApplicationTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit the Home CTA integration**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeScreenTest.kt app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt
git commit -m "feat: add home update install cta"
```
