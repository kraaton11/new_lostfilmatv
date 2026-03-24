# Home Single Feed Mode Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current two-rail Home favorites presentation with one card-first Home screen that switches between `Новые релизы` and `Избранное` from header tabs, remembers the selected mode, and keeps Favorites-specific empty/login/error states without auto-falling back.

**Architecture:** Keep the repository contract for loading all-new and favorite releases unchanged, but move Home from a rail-collection model to an explicit mode model. Use a Kotlin `enum class` for the finite feed selector and `sealed interface` state models for mode-specific UI states, then let `HomeViewModel` own persistence, focus memory, and state transitions while Compose renders only the active mode plus fallback focus targets.

**Tech Stack:** Kotlin, Jetpack Compose for TV, SavedStateHandle, SharedPreferences via `PlaybackPreferencesStore`, Robolectric/JUnit unit tests, Android instrumented Compose tests

---

**Spec Reference:** `docs/superpowers/specs/2026-03-24-home-single-feed-mode-design.md`

**Context7 Notes:** Kotlin docs confirm that a finite selector should stay an `enum class`, while richer exhaustively-checked UI states fit `sealed interface` with `data object` and `data class` implementations. Use that split for `HomeFeedMode` and `HomeModeContentState` so `when` branches in `HomeViewModel` and Compose stay explicit and compiler-checked.

## Planned File Structure

- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeFeedMode.kt`
  Responsibility: Define the persisted Home mode enum and storage value mapping.
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeModeContentState.kt`
  Responsibility: Define sealed UI states for `AllNew` and `Favorites` feeds, including `Loading`, `Content`, `Empty`, `LoginRequired`, and `Error`.
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/playback/PlaybackPreferencesStore.kt`
  Responsibility: Persist the selected Home mode in SharedPreferences alongside the existing Favorites-tab visibility toggle.
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/playback/PlaybackPreferencesStoreTest.kt`
  Responsibility: Lock the new Home mode persistence and unknown-value fallback behavior.
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt`
  Responsibility: Change visible copy from `Полка Избранное` to `Вкладка Избранное`.
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeUiState.kt`
  Responsibility: Replace multi-rail-first fields with explicit selected mode, available modes, per-mode state, and remembered focus keys.
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModel.kt`
  Responsibility: Load and persist the selected mode, keep per-mode focus memory, eagerly load Favorites when available, and soft-fallback to `AllNew` only when the tab becomes unavailable.
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModelTest.kt`
  Responsibility: Prove mode selection, persisted startup, Favorites invalidation, runtime setting fallback, and per-mode focus restoration.
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeHeader.kt`
  Responsibility: Add the mode tabs to the header with deterministic focus movement to and from content.
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt`
  Responsibility: Render only the active feed, route initial focus to cards or fallback CTAs, and keep the bottom stage in sync with the active mode.
- Modify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/HomeScreenTest.kt`
  Responsibility: Lock the new header-tab focus behavior and in-place Favorites states.
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
  Responsibility: Read/write persisted Home mode, keep it stable across auth and details invalidation, and wire Home favorites visibility as tab availability instead of second-rail visibility.
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsRoute.kt`
  Responsibility: Surface the unchanged visibility setting semantics to Home as tab availability.

## Chunk 1: Persist and Model the New Home Mode

### Task 1: Write failing persistence tests for the selected Home mode

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeFeedMode.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/playback/PlaybackPreferencesStoreTest.kt`

- [ ] **Step 1: Add RED tests for selected Home mode persistence and fallback.**

Add cases that prove:
- default mode is `AllNew`
- writing `Favorites` is persisted
- unknown stored strings fall back safely to `AllNew`

- [ ] **Step 2: Run the focused preferences test and verify RED.**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStoreTest"
```

Expected: FAIL because the store has no selected-mode API yet.

- [ ] **Step 3: Implement `HomeFeedMode` and the new `PlaybackPreferencesStore` read/write methods with minimal code.**

- [ ] **Step 4: Re-run the preferences test and verify GREEN.**

- [ ] **Step 5: Commit the Home mode persistence slice.**

## Chunk 2: Lock ViewModel Behavior Before UI Changes

### Task 2: Write failing `HomeViewModel` tests for mode selection, fallback, and per-mode focus memory

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeModeContentState.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeUiState.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModelTest.kt`

- [ ] **Step 1: Add RED tests for persisted startup into `Favorites` and eager Favorites loading.**

Lock:
- startup from persisted `Favorites`
- eager Favorites load when the tab is available
- `LoginRequired`, `Empty`, and `Error` staying in `Favorites` instead of switching away

- [ ] **Step 2: Add RED tests for runtime tab availability changes and per-mode remembered focus.**

Lock:
- disabling Favorites while selected soft-falls back to `AllNew`
- mode switch preserves remembered card per mode
- return from details invalidation refreshes Favorites without changing selected mode

- [ ] **Step 3: Run the focused ViewModel tests and verify RED.**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.home.HomeViewModelTest"
```

Expected: FAIL because the current ViewModel is still rail-based.

### Task 3: Implement the Home mode state machine in `HomeViewModel`

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeUiState.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModel.kt`

- [ ] **Step 1: Introduce explicit selected/available mode state and per-mode remembered focus keys.**

- [ ] **Step 2: Map repository results into sealed mode states with minimal branching.**

- [ ] **Step 3: Add mode-selection and mode-availability handlers to `HomeViewModel`.**

- [ ] **Step 4: Re-run the focused ViewModel tests and verify GREEN.**

- [ ] **Step 5: Commit the ViewModel state-machine slice.**

## Chunk 3: Update Header/UI and Lock Focus Navigation

### Task 4: Write failing UI tests for header tabs and fallback focus targets

**Files:**
- Modify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/HomeScreenTest.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Replace the old multi-rail Home test expectations with RED tests for the new mode tabs.**

Lock:
- initial focus lands on the first card in the active mode
- `Up` from the top card focuses the active tab
- `Left/Right` on the tab switches mode
- `Down` returns to the remembered card

- [ ] **Step 2: Add RED tests for Favorites `LoginRequired` and `Error` fallback focus targets if covered by instrumented UI tests.**

- [ ] **Step 3: Add a small settings UI expectation that copy now says `Вкладка Избранное`.**

- [ ] **Step 4: Run the focused UI tests and verify RED.**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsScreenTest"
adb -s emulator-5554 shell am instrument -w -e class com.kraat.lostfilmnewtv.ui.HomeScreenTest com.kraat.lostfilmnewtv.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: FAIL because the current UI still renders the second rail.

### Task 5: Implement the header tabs and single-feed Home rendering

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeHeader.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Add header tabs with deterministic focus requesters and tags.**

- [ ] **Step 2: Render only the active mode's content or fallback state surface.**

- [ ] **Step 3: Keep bottom-stage content and service-action focus behavior intact.**

- [ ] **Step 4: Update Settings copy from rail wording to tab wording.**

- [ ] **Step 5: Re-run the focused UI tests and verify GREEN.**

- [ ] **Step 6: Commit the Home UI slice.**

## Chunk 4: Wire Navigation, Refresh Triggers, and Final Verification

### Task 6: Integrate persisted mode and runtime availability in navigation

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsRoute.kt`

- [ ] **Step 1: Thread the persisted Home mode and Favorites-tab visibility through the Home ViewModel factory.**

- [ ] **Step 2: Keep watched and Favorites invalidation behavior working with the new mode-based Home state.**

- [ ] **Step 3: Re-run focused nav/home tests and fix any state restoration gaps.**

### Task 7: Run final verification for the full single-mode Home flow

**Files:**
- Modify: none unless verification reveals a defect

- [ ] **Step 1: Run the full targeted unit-test suite for preferences, Home, and Settings.**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStoreTest" --tests "com.kraat.lostfilmnewtv.ui.home.HomeViewModelTest" --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsViewModelTest" --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsScreenTest"
```

- [ ] **Step 2: Run the instrumented Home screen test on the emulator.**

Run:

```powershell
adb -s emulator-5554 shell am instrument -w -e class com.kraat.lostfilmnewtv.ui.HomeScreenTest com.kraat.lostfilmnewtv.test/androidx.test.runner.AndroidJUnitRunner
```

- [ ] **Step 3: Run a debug build to catch any compile-only regressions.**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

- [ ] **Step 4: Commit the integrated single-mode Home implementation.**
