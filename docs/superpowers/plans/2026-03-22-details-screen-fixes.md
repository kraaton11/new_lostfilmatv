# Details Screen Fixes Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the details screen loading and stale-data states, remove dead details UI code, clean up route scope handling, and move details colors into theme.

**Architecture:** Keep the existing details feature shape intact: `DetailsViewModel` remains the source of loading and stale flags, `DetailsScreen` owns the new rendering states, and `DetailsStageModels` continues to derive only the hero/bottom-strip UI needed by the screen. Cleanup work stays local to the details feature and theme files to avoid unrelated refactors.

**Tech Stack:** Kotlin, Jetpack Compose, Robolectric Compose tests, Gradle Android app module

---

### Task 1: Add explicit details loading and stale-data UI

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreenStateTest.kt`

- [ ] Step 1: Write a failing Compose test proving `DetailsUiState(isLoading = true, details = null)` renders a loading indicator instead of content.
- [ ] Step 2: Run the new test class and verify the loading test fails for the expected reason.
- [ ] Step 3: Add `LoadingState()` and route the `when` branch to it only when loading without details.
- [ ] Step 4: Add a failing Compose test proving `showStaleBanner = true` renders a visible informational banner while content remains visible.
- [ ] Step 5: Run the new test class and verify the stale-banner test fails for the expected reason.
- [ ] Step 6: Render the stale banner inside `ContentState` and keep background-refresh behavior unchanged when `details != null`.
- [ ] Step 7: Run `DetailsScreenStateTest` and confirm both tests pass.

### Task 2: Remove dead details stage and torrent model code

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsTorrentModels.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsTorrentModelsTest.kt`

- [ ] Step 1: Update stage model tests so they no longer expect `heroMetaLine` or `heroStatusLine`.
- [ ] Step 2: Run the affected unit tests and verify they fail because the legacy fields still exist in code/tests.
- [ ] Step 3: Remove `heroMetaLine`, `heroStatusLine`, and `buildHeroMetaLine`, plus the dead hero-meta rendering block in `HeroStage`.
- [ ] Step 4: Remove `qualityStatusText` and delete its now-obsolete tests.
- [ ] Step 5: Run `DetailsStageModelsTest` and `DetailsTorrentModelsTest` and confirm they pass.

### Task 3: Fix route scope handling and move details colors into theme

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsRoute.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/theme/Color.kt`
- Test: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt`

- [ ] Step 1: Replace the custom remembered `CoroutineScope` with `rememberCoroutineScope()` and remove the manual scope cancellation while keeping `inFlightJob` cleanup.
- [ ] Step 2: Move named details colors from `DetailsScreen.kt` into the theme color file and update imports/usages.
- [ ] Step 3: Run `DetailsRouteTest` plus the new details screen state tests and confirm the refactor stays green.

### Task 4: Verify the touched details feature end to end

**Files:**
- Test: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreenStateTest.kt`
- Test: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt`
- Test: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt`
- Test: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsTorrentModelsTest.kt`

- [ ] Step 1: Run the focused details unit test command covering route, screen state, stage models, and torrent models.
- [ ] Step 2: If compilation or expectation failures remain, fix them and rerun until green.
- [ ] Step 3: Summarize the final behavior changes and any residual verification gaps.
