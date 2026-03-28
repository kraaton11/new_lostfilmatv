# Android TV Home Channel Background Refresh Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add periodic background refresh for the Android TV launcher channel so cached page 1 is refreshed and the home-screen channel is republished even when the app is closed.

**Architecture:** Keep the current channel design intact and add a small `WorkManager` slice around it. A pure Kotlin background runner decides whether refresh should happen, a scheduler manages one unique periodic work request, and a thin worker bridges `WorkManager` to the runner through `LostFilmApplication`.

**Tech Stack:** Kotlin, Gradle Kotlin DSL, AndroidX WorkManager, existing Room cache, existing `LostFilmRepository`, existing `HomeChannelSyncManager`, JUnit 4, Robolectric, Mockito

---

**Spec Reference:** `docs/superpowers/specs/2026-03-21-android-tv-home-channel-design.md`

**Delta Spec Reference:** `docs/superpowers/specs/2026-03-21-android-tv-home-channel-background-refresh-design.md`

**Dependency Note:** Use `androidx.work:work-runtime-ktx` `2.11.1`, which is the current stable WorkManager release in the official AndroidX Work release notes and supports the modern `WorkManager.getInstance(context)` API.

## Planned File Structure

- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelBackgroundRefreshRunner.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelBackgroundScheduler.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelRefreshWorker.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsRoute.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModel.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelBackgroundRefreshRunnerTest.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelBackgroundSchedulerTest.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelRefreshWorkerTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/LostFilmApplicationTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModelTest.kt`

## Chunk 1: WorkManager Foundation

### Task 1: Add WorkManager runtime dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Confirm WorkManager is not already in the build**

Run:

```powershell
rg -n "work-runtime|androidx.work|workmanager" gradle/libs.versions.toml app/build.gradle.kts
```

Expected: no matches

- [ ] **Step 2: Add version-catalog and app-module entries**

Update `gradle/libs.versions.toml` with:

```toml
[versions]
work = "2.11.1"

[libraries]
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
```

Update `app/build.gradle.kts` with:

```kotlin
implementation(libs.androidx.work.runtime.ktx)
```

Keep the dependency surface minimal. Do not add `work-testing` unless implementation later proves it is truly needed.

- [ ] **Step 3: Verify the module still resolves and builds**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit the dependency foundation**

Run:

```powershell
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add workmanager runtime"
```

### Task 2: Implement the background refresh runner

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelBackgroundRefreshRunner.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelBackgroundRefreshRunnerTest.kt`

- [ ] **Step 1: Write failing runner tests for all outcomes**

Create `HomeChannelBackgroundRefreshRunnerTest.kt` covering:

```kotlin
@Test
fun disabledMode_skipsWithoutRefreshingOrSyncing() = runTest { }

@Test
fun missingSession_skipsWithoutRefreshingOrSyncing() = runTest { }

@Test
fun expiredSession_skipsWithoutRefreshingOrSyncing() = runTest { }

@Test
fun contentResult_refreshesPageOneAndSyncsChannel() = runTest { }

@Test
fun errorResult_returnsRetryableFailureWithoutSyncing() = runTest { }
```

Use lambda injection instead of new interfaces:

- `readMode: () -> AndroidTvChannelMode`
- `readSession: suspend () -> LostFilmSession?`
- `isSessionExpired: suspend () -> Boolean`
- `refreshFirstPage: suspend () -> PageState`
- `syncChannel: suspend () -> Unit`

- [ ] **Step 2: Run the runner tests to confirm failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundRefreshRunnerTest"
```

Expected: FAIL with missing runner types

- [ ] **Step 3: Implement the minimal runner and outcome enum**

Create `HomeChannelBackgroundRefreshRunner.kt` with:

```kotlin
enum class HomeChannelBackgroundRefreshOutcome {
    SKIPPED_DISABLED,
    SKIPPED_UNAUTHENTICATED,
    REFRESHED,
    FAILED_RETRYABLE,
}
```

Implementation rules:

- return `SKIPPED_DISABLED` for `AndroidTvChannelMode.DISABLED`
- return `SKIPPED_UNAUTHENTICATED` when session is missing or expired
- call `refreshFirstPage()` exactly once for active modes with a usable session
- treat `PageState.Content` as successful and call `syncChannel()`
- treat `PageState.Error` as `FAILED_RETRYABLE`
- do not add launcher-publication logic here; keep it delegated to `syncChannel()`

- [ ] **Step 4: Re-run the runner tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundRefreshRunnerTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the runner**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelBackgroundRefreshRunner.kt app/src/test/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelBackgroundRefreshRunnerTest.kt
git commit -m "feat: add background channel refresh runner"
```

## Chunk 2: Scheduling And Worker Bridge

### Task 3: Implement the unique periodic scheduler

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelBackgroundScheduler.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelBackgroundSchedulerTest.kt`

- [ ] **Step 1: Write failing scheduler tests**

Create tests for:

```kotlin
@Test
fun activeMode_enqueuesUniquePeriodicWork() { }

@Test
fun disabledMode_cancelsUniquePeriodicWork() { }

@Test
fun scheduledWork_usesConnectedNetworkConstraintAndSixHourInterval() { }
```

Inject `WorkManager` directly and use Mockito to verify:

- `enqueueUniquePeriodicWork(...)` is called once for active modes
- `cancelUniqueWork(...)` is called for `DISABLED`
- unique work name is stable
- policy is explicit
- `PeriodicWorkRequest` uses `NetworkType.CONNECTED`
- interval equals `Duration.ofHours(6)` or the equivalent millis constant

- [ ] **Step 2: Run the scheduler tests to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundSchedulerTest"
```

Expected: FAIL with missing scheduler type

- [ ] **Step 3: Implement the scheduler**

Create `HomeChannelBackgroundScheduler.kt` with one focused responsibility:

```kotlin
class HomeChannelBackgroundScheduler(
    private val readMode: () -> AndroidTvChannelMode,
    private val workManager: WorkManager,
) {
    fun syncForCurrentMode() { ... }
}
```

Implementation requirements:

- use one constant work name such as `android-tv-home-channel-refresh`
- for active modes, enqueue `PeriodicWorkRequestBuilder<HomeChannelRefreshWorker>(6, TimeUnit.HOURS)`
- set `Constraints(requiredNetworkType = NetworkType.CONNECTED)`
- use unique periodic work so repeated startup/settings calls do not duplicate work
- for `DISABLED`, cancel the unique work

- [ ] **Step 4: Re-run the scheduler tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundSchedulerTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the scheduler**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelBackgroundScheduler.kt app/src/test/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelBackgroundSchedulerTest.kt
git commit -m "feat: add background channel scheduler"
```

### Task 4: Implement the worker bridge and result mapping

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelRefreshWorker.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelRefreshWorkerTest.kt`

- [ ] **Step 1: Write failing worker-mapping tests**

Create `HomeChannelRefreshWorkerTest.kt` that verifies:

```kotlin
@Test
fun skippedDisabled_mapsToSuccess() { }

@Test
fun skippedUnauthenticated_mapsToSuccess() { }

@Test
fun refreshed_mapsToSuccess() { }

@Test
fun retryableFailure_mapsToRetry() { }
```

Keep these tests focused on outcome mapping. Do not introduce instrumentation or `work-testing` unless a real blocker appears.

- [ ] **Step 2: Run the worker tests to confirm failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.tvchannel.HomeChannelRefreshWorkerTest"
```

Expected: FAIL because the worker and mapping helper do not exist yet

- [ ] **Step 3: Implement the worker**

Create `HomeChannelRefreshWorker.kt` as a thin `CoroutineWorker`:

```kotlin
class HomeChannelRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result { ... }
}
```

Implementation rules:

- resolve `applicationContext as LostFilmApplication`
- call `application.homeChannelBackgroundRefreshRunner.run()`
- convert the returned outcome using one small internal mapper:

```kotlin
internal fun HomeChannelBackgroundRefreshOutcome.toWorkerResult(): ListenableWorker.Result
```

- keep all business decisions out of the worker

- [ ] **Step 4: Re-run the worker tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.tvchannel.HomeChannelRefreshWorkerTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the worker bridge**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelRefreshWorker.kt app/src/test/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelRefreshWorkerTest.kt
git commit -m "feat: add background channel worker"
```

## Chunk 3: Application Wiring And Regression Coverage

### Task 5: Wire application dependencies and startup scheduling

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/LostFilmApplicationTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt`

- [ ] **Step 1: Extend the startup tests before touching implementation**

Add to `LostFilmApplicationTest.kt`:

```kotlin
@Test
fun homeChannelBackgroundScheduler_isExposedAsStableApplicationDependency() { }

@Test
fun homeChannelBackgroundRefreshRunner_isExposedAsStableApplicationDependency() { }
```

Add to `AppNavGraphTorrServeTest.kt` a new startup regression that blocks page loading with a `CompletableDeferred<PageState>`, records scheduler calls through a fake application dependency, and asserts startup scheduling happens exactly once before the deferred page result is released:

```kotlin
@Test
fun startup_composition_schedules_background_channel_refresh_once() { }
```

Use a recording fake scheduler in `TestLostFilmApplication` and assert one startup scheduling call before home content finishes loading.

- [ ] **Step 2: Run the startup-focused tests and verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.LostFilmApplicationTest" --tests "com.kraat.lostfilmnewtv.navigation.AppNavGraphTorrServeTest"
```

Expected: FAIL because the new application properties and scheduler override do not exist yet

- [ ] **Step 3: Implement the application wiring**

Update `LostFilmApplication.kt` to expose:

- `homeChannelBackgroundRefreshRunner`
- `homeChannelBackgroundScheduler`

Suggested construction:

```kotlin
open val homeChannelBackgroundRefreshRunner: HomeChannelBackgroundRefreshRunner by lazy { ... }

open val homeChannelBackgroundScheduler: HomeChannelBackgroundScheduler by lazy {
    HomeChannelBackgroundScheduler(
        readMode = playbackPreferencesStore::readAndroidTvChannelMode,
        workManager = WorkManager.getInstance(applicationContext),
    )
}
```

Update `AppNavGraph.kt` startup effect to:

1. synchronize scheduler with current mode
2. keep the existing immediate `homeChannelSyncManager.syncNow()`

Also extend `TestLostFilmApplication` in `AppNavGraphTorrServeTest.kt` with background-scheduler and runner overrides rather than hard-coding production dependencies in tests.

- [ ] **Step 4: Re-run the startup-focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.LostFilmApplicationTest" --tests "com.kraat.lostfilmnewtv.navigation.AppNavGraphTorrServeTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the startup wiring**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt app/src/test/java/com/kraat/lostfilmnewtv/LostFilmApplicationTest.kt app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt
git commit -m "feat: wire startup background channel refresh"
```

### Task 6: Wire settings-driven scheduling updates

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsRoute.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Extend settings tests with scheduling expectations**

Add tests like:

```kotlin
@Test
fun onChannelModeSelected_activeMode_persistsSyncsAndSchedules() = runTest { }

@Test
fun onChannelModeSelected_disabledMode_persistsSyncsAndCancelsSchedule() = runTest { }
```

Track three independent effects:

- persisted mode
- immediate channel sync
- scheduler refresh call count

Also keep the existing same-mode guard test unchanged so it still proves there is no duplicate sync or scheduling work.

- [ ] **Step 2: Run the settings tests to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsViewModelTest"
```

Expected: FAIL because the new scheduling callback is not wired yet

- [ ] **Step 3: Implement the minimal settings wiring**

Update `SettingsViewModel.kt` constructor with one additional callback:

```kotlin
private val syncAndroidTvChannelBackgroundSchedule: () -> Unit = {}
```

Update `onChannelModeSelected(...)` so, after persisting the new mode and updating state, the background side effects happen in order inside the existing coroutine:

1. `syncAndroidTvChannelBackgroundSchedule()`
2. `syncAndroidTvChannel()`

Update `SettingsRoute.kt` and its factory to pass:

```kotlin
syncAndroidTvChannelBackgroundSchedule = application.homeChannelBackgroundScheduler::syncForCurrentMode
```

Keep the composable screen declarative. Do not move WorkManager calls into `SettingsScreen.kt`.

- [ ] **Step 4: Re-run the settings tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsViewModelTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the settings wiring**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsRoute.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModel.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModelTest.kt
git commit -m "feat: reschedule background channel refresh from settings"
```

### Task 7: Run the focused verification suite

**Files:**
- No new files

- [ ] **Step 1: Run the targeted background-refresh tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundRefreshRunnerTest" --tests "com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundSchedulerTest" --tests "com.kraat.lostfilmnewtv.tvchannel.HomeChannelRefreshWorkerTest" --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsViewModelTest" --tests "com.kraat.lostfilmnewtv.LostFilmApplicationTest" --tests "com.kraat.lostfilmnewtv.navigation.AppNavGraphTorrServeTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Re-run the existing launcher-channel regression suite**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.tvchannel.*" --tests "com.kraat.lostfilmnewtv.navigation.AppLaunchTargetTest" --tests "com.kraat.lostfilmnewtv.ui.details.DetailsRouteTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Assemble the app**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Review the worktree before handoff**

Run:

```powershell
git status --short
```

Expected: only the intended tracked files plus the pre-existing untracked `tmp/` verification folder

- [ ] **Step 5: Commit the completed implementation**

Run:

```powershell
git add app gradle/libs.versions.toml
git commit -m "feat: refresh android tv channel in background"
```

## Execution Notes

- Follow the tasks in order because later wiring assumes the runner, scheduler, and worker already exist.
- Keep the worker thin. If implementation pressure starts pushing business logic into `HomeChannelRefreshWorker`, move it back into the runner instead.
- Reuse the existing lambda-heavy test style already present in `SettingsViewModelTest.kt` and `AppNavGraphTorrServeTest.kt`.
- Do not add a new repository API for background refresh. Use `LostFilmRepository.loadPage(1)` directly.
- Do not add receivers, notifications, or re-auth flows in this iteration.
