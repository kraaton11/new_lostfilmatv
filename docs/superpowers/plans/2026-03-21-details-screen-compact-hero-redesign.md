# Details Screen Compact Hero Redesign Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Android TV `Details` screen into the approved compact-hero layout so the first screen stops feeling empty, keeps `Смотреть` as the only focus target, and uses one minimal bottom strip for context or status.

**Architecture:** Keep the route-level playback flow intact and focus the redesign inside the details presentation layer. Slim `DetailsStageModels.kt` so it emits short hero and bottom-strip strings without release date or `TorrServe` branding, then restructure `DetailsScreen.kt` around one compact poster-plus-text cluster and one non-focusable bottom strip. Update UI tests to lock down the denser composition and the reused bottom-strip state area.

**Tech Stack:** Kotlin, Jetpack Compose, Android TV focus APIs, JUnit4, Robolectric, Compose UI tests, existing LostFilm details/playback models

**Reference Spec:** `docs/superpowers/specs/2026-03-21-details-screen-compact-hero-redesign.md`

**Execution Notes:** Follow @test-driven-development for each behavior change and finish with @verification-before-completion before claiming success. Preserve existing route contracts and semantic playback tags unless a test proves they must change.

---

## File Map

- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt`
- Modify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt`
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt`

Notes:
- Do not reintroduce extra playback actions or a detached right-side action rail.
- Do not show release date, description, or `TorrServe` copy on the first screen.
- Keep the single `Смотреть` action tag stable: `torrent-torrserve-<rowId>` when a playable row exists, otherwise `details-primary-action`.
- The bottom strip is display-only; it must not become a focus target.

## Chunk 1: Simplify The Details Copy Model

### Task 1: Replace the old status phrasing with compact hero and bottom-strip strings

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt`

- [ ] **Step 1: Write failing stage-model tests for the approved compact copy**

Add or update tests to capture the new strings:

```kotlin
@Test
fun buildStageUi_usesSeriesMetaInBottomStrip_withoutTorrServeBranding() {
    val ui = buildDetailsStageUi(
        state = DetailsUiState(details = seriesDetails()),
        isAuthenticated = true,
        availableTorrentRowsCount = 1,
        playbackRow = DetailsTorrentRowUiModel("row-0", "1080p", "https://example.com/1080"),
        activeTorrServeRowId = null,
        isTorrServeBusy = false,
    )

    assertEquals("Сезон 1 • Серия 5 • 1080p", ui.bottomInfoLine)
    assertEquals("", ui.heroMetaLine)
}

@Test
fun buildStageUi_usesMovieMetaInBottomStrip_withoutReleaseDate() {
    val ui = buildDetailsStageUi(
        state = DetailsUiState(details = movieDetails()),
        isAuthenticated = true,
        availableTorrentRowsCount = 1,
        playbackRow = DetailsTorrentRowUiModel("row-0", "1080p", "https://example.com/1080"),
        activeTorrServeRowId = null,
        isTorrServeBusy = false,
    )

    assertEquals("Фильм • 1080p", ui.bottomInfoLine)
    assertEquals("", ui.heroMetaLine)
}

@Test
fun buildStageUi_reusesBottomStripForBusyAndErrorStates() {
    val busyUi = buildDetailsStageUi(
        state = DetailsUiState(details = seriesDetails()),
        isAuthenticated = true,
        availableTorrentRowsCount = 1,
        playbackRow = DetailsTorrentRowUiModel("row-0", "1080p", "https://example.com/1080"),
        activeTorrServeRowId = "row-0",
        isTorrServeBusy = true,
    )
    val errorUi = buildDetailsStageUi(
        state = DetailsUiState(details = seriesDetails()),
        isAuthenticated = true,
        availableTorrentRowsCount = 1,
        playbackRow = DetailsTorrentRowUiModel("row-0", "1080p", "https://example.com/1080"),
        activeTorrServeRowId = null,
        isTorrServeBusy = false,
        torrServeMessageText = "Не удалось открыть видео",
    )

    assertEquals("Открывается...", busyUi.bottomInfoLine)
    assertEquals("Не удалось открыть видео", errorUi.bottomInfoLine)
}
```

- [ ] **Step 2: Run the stage-model test to verify RED**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsStageModelsTest"`

Expected: FAIL because `bottomInfoLine` and the new copy rules do not exist yet.

- [ ] **Step 3: Slim `DetailsStageUiModel` to the strings the new screen actually needs**

Update the model so it carries:

```kotlin
data class DetailsStageUiModel(
    val activeRowId: String?,
    val title: String,
    val heroMetaLine: String,
    val heroEpisodeTitle: String,
    val bottomInfoLine: String,
    val primaryAction: DetailsStageActionUiModel,
    val secondaryActions: List<DetailsStageActionUiModel>,
)
```

Implementation rules:
- `heroMetaLine` becomes optional and should stay empty by default for the compact redesign
- `heroEpisodeTitle` stays available for series only
- `bottomInfoLine` becomes the single source for normal context, busy state, disabled state, and error text
- remove `TorrServe` wording from normal-state text
- never use release date in normal-state text

- [ ] **Step 4: Implement bottom-strip copy rules**

In `DetailsStageModels.kt`, derive strings with this priority:
- explicit `torrServeMessageText`
- `Открывается...`
- if `playbackRow == null`, `Видео недоступно`
- otherwise:
  - series: `Сезон X • Серия Y • <quality>`
  - movie: `Фильм • <quality>`

Keep the existing single-action model:
- label: `Смотреть`
- subtitle may stay short and action-oriented, but it must not mention `TorrServe`

- [ ] **Step 5: Re-run the stage-model test to verify GREEN**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsStageModelsTest"`

Expected: PASS

- [ ] **Step 6: Commit the copy-model slice**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt
git commit -m "refactor: simplify details stage copy"
```

## Chunk 2: Rebuild The Screen Into A Compact Hero

### Task 2: Remove the detached action column and compose one dense hero cluster

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
- Modify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt`

- [ ] **Step 1: Write failing UI expectations for the compact layout**

Update or add Android UI tests like:

```kotlin
@Test
fun detailsScreen_hidesReleaseDateAndTorrServeBranding_onFirstScreen() {
    val playbackRow = row("preferred", "1080p", "https://example.com/1080.torrent", true)

    composeRule.setDetailsContent(
        state = DetailsUiState(details = detailsWithRows(listOf(playbackRow))),
        availableTorrentRowsCount = 1,
        playbackRow = playbackRow,
    )

    assertTrue(composeRule.onAllNodesWithText("14 марта 2026").fetchSemanticsNodes().isEmpty())
    assertTrue(composeRule.onAllNodesWithText("TorrServe").fetchSemanticsNodes().isEmpty())
}

@Test
fun detailsScreen_showsMinimalBottomStrip_forSeries() {
    val playbackRow = row("preferred", "1080p", "https://example.com/1080.torrent", true)

    composeRule.setDetailsContent(
        state = DetailsUiState(details = detailsWithRows(listOf(playbackRow))),
        availableTorrentRowsCount = 1,
        playbackRow = playbackRow,
    )

    composeRule.onNodeWithText("Сезон 9 • Серия 13 • 1080p").assertExists()
}

@Test
fun detailsScreen_reusesBottomStrip_forDisabledState() {
    composeRule.setDetailsContent(
        state = DetailsUiState(details = seriesDetails().copy(torrentLinks = emptyList())),
        availableTorrentRowsCount = 0,
        playbackRow = null,
    )

    composeRule.onNodeWithTag("details-primary-action").assertIsNotEnabled()
    composeRule.onNodeWithText("Видео недоступно").assertExists()
}
```

- [ ] **Step 2: Compile Android test sources to lock the new expectations**

Run: `.\gradlew.bat :app:compileDebugAndroidTestKotlin`

Expected: PASS compilation before the layout work, with runtime assertions still failing until implementation is updated.

- [ ] **Step 3: Restructure `ContentState` around two visual zones**

In `DetailsScreen.kt`:
- remove the detached right-side `Column` used only for the single button
- keep one main hero container that holds poster plus text/action cluster
- reduce the fixed hero height from the current oversized cinematic block to a noticeably tighter value
- place `StageButton` inside the text column under title and episode subtitle

Keep:
- background gradients
- poster image treatment
- semantic playback tags

Do not add:
- new focus targets
- bottom cards
- filler description text

- [ ] **Step 4: Add a dedicated non-focusable bottom-strip composable**

Add a small display-only strip below the hero, fed from `stageUi.bottomInfoLine`.

Guidelines:
- full-width or near-full-width
- quiet styling
- one line only
- reused for normal, busy, disabled, and error states
- no `.focusable()`, `Button`, or focus requesters

- [ ] **Step 5: Remove obsolete first-screen copy from the hero**

The hero should show only:
- title
- optional episode title
- `Смотреть`
- optional `heroMetaLine` only if the compact layout still needs it after implementation review

It must not show:
- release date
- `TorrServe`
- description placeholders

- [ ] **Step 6: Re-run the focused Android UI tests**

Run: `.\gradlew.bat :app:compileDebugAndroidTestKotlin`

Expected: PASS

If device testing is available during execution, later validate these UI changes through the focused instrumentation suite from Chunk 4.

- [ ] **Step 7: Commit the screen-layout slice**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt
git commit -m "feat: compact details hero layout"
```

## Chunk 3: Lock Down State Handling And Regression Coverage

### Task 3: Verify route compatibility and the stable single-action flow

**Files:**
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt`
- Modify if needed: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt`

- [ ] **Step 1: Run route-level tests against the redesigned presentation**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsRouteTest"`

Expected: PASS

- [ ] **Step 2: If a route test depends on removed first-screen text, update only the assertion, not the route contract**

Allowed adjustments:
- assert the button tag still exists
- assert busy/error behavior still maps into one visible message
- keep `preferredPlaybackQuality` and watched-marking behavior untouched

Do not expand route churn unless a test proves the new copy contract requires it.

- [ ] **Step 3: Re-run stage-model and route tests together**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsStageModelsTest" --tests "com.kraat.lostfilmnewtv.ui.details.DetailsRouteTest"`

Expected: PASS

- [ ] **Step 4: Commit any needed route-test adjustments**

```bash
git add app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt
git commit -m "test: align details route coverage with compact hero"
```

## Chunk 4: Final Verification

### Task 4: Prove the redesign works before handoff

**Files:**
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt`
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt`
- Verify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt`

- [ ] **Step 1: Run the focused details unit tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsStageModelsTest" --tests "com.kraat.lostfilmnewtv.ui.details.DetailsRouteTest"`

Expected: PASS

- [ ] **Step 2: Run the full unit suite**

Run: `.\gradlew.bat :app:testDebugUnitTest`

Expected: PASS

- [ ] **Step 3: Compile Android test sources and assemble the app**

Run: `.\gradlew.bat :app:compileDebugAndroidTestKotlin :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: If a device is available, install and run the focused details instrumentation suite**

Run:

```bash
.\gradlew.bat :app:installDebug :app:installDebugAndroidTest
adb -s 192.168.2.246:5555 shell am instrument -w -e class com.kraat.lostfilmnewtv.ui.DetailsScreenTest com.kraat.lostfilmnewtv.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: `OK`

If no device is available, explicitly report that final verification stopped at unit tests plus Android-test compilation.

- [ ] **Step 5: Final commit**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt
git commit -m "feat: redesign compact details hero"
```
