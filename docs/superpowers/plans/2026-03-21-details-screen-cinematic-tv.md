# Cinematic TV Details Screen Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify the Android TV `Details` screen into the approved `Hero + Action Rail` layout so the first screen becomes readable at a glance while keeping playback action first and removing extra onscreen chrome.

**Architecture:** Keep `DetailsRoute` and the public `DetailsScreen` overloads stable. Slim the presentation model so it exposes only the hero strings and actions that the simplified first screen needs, then rebuild `DetailsScreen.kt` around two visible zones: a calm hero block and a compact right-side action rail. Remove the first-screen technical-sheet focus path instead of hiding it cosmetically.

**Tech Stack:** Kotlin, Jetpack Compose, Android TV focus APIs, Compose UI tests, existing LostFilm UI models

---

## Addendum: Episode Title In Hero

This follow-up slice keeps the current status-line behavior unchanged and adds only one new content element: an optional episode-title line for series. The source of truth is the cached summary metadata already stored for the same `detailsUrl`; do not introduce new navigation arguments or new network fetching for this.

## File Map

- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/model/ReleaseDetails.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryImpl.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
- Modify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt`
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryTest.kt`
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt`
- Reference spec: `docs/superpowers/specs/2026-03-21-details-screen-cinematic-tv-design.md`

Notes:
- Do not add new network requests or navigation arguments.
- Keep existing supported-row semantic tags such as `torrent-torrserve-<rowId>`.
- Do not keep separate onscreen `Назад` or `Открыть ссылку` controls on the first screen.
- The first-screen technical sheet and its focus model must be removed, not merely hidden behind alpha or size tricks.

## Chunk 1: Slim The Stage Model Contract

### Task 1: Replace dense first-screen presentation data with compact hero strings

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt`

- [ ] **Step 1: Write failing mapper tests for compact hero copy**

```kotlin
@Test
fun buildStageUi_exposesCompactHeroMetaAndStatusLines() {
    val ui = buildDetailsStageUi(
        state = DetailsUiState(details = seriesDetails()),
        isAuthenticated = true,
        torrentRows = listOf(
            DetailsTorrentRowUiModel("row-0", "1080p", "https://example.com/1080"),
        ),
        activeRowId = "row-0",
        activeTorrServeRowId = null,
        isTorrServeBusy = false,
    )

    assertEquals("Сезон 1, серия 5", ui.heroMetaLine)
    assertEquals("1080p • TorrServe • свежие данные", ui.heroStatusLine)
}

@Test
fun buildStageUi_usesSingleErrorStatusLine_forRowScopedTorrServeFallback() {
    val ui = buildDetailsStageUi(
        state = DetailsUiState(details = movieDetails(), showStaleBanner = true),
        isAuthenticated = false,
        torrentRows = listOf(
            DetailsTorrentRowUiModel("row-0", "WEBRip", "magnet:?xt=urn:btih:test", isTorrServeSupported = false),
        ),
        activeRowId = "row-0",
        activeTorrServeRowId = null,
        isTorrServeBusy = false,
        torrServeMessageText = "Не удалось открыть TorrServe",
    )

    assertEquals("21 марта 2026", ui.heroMetaLine)
    assertEquals("Не удалось открыть TorrServe", ui.heroStatusLine)
}
```

- [ ] **Step 2: Run the mapper tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsStageModelsTest"`

Expected: FAIL because the simplified properties and mapper path do not exist yet.

- [ ] **Step 3: Simplify the stage-ui model to only what the new first screen needs**

Update `DetailsStageUiModel` so it carries compact hero text instead of first-screen card/chip collections:

```kotlin
data class DetailsStageUiModel(
    val activeRowId: String?,
    val title: String,
    val heroMetaLine: String,
    val heroStatusLine: String,
    val primaryAction: DetailsStageActionUiModel,
    val qualityActions: List<DetailsStageActionUiModel>,
    val secondaryActions: List<DetailsStageActionUiModel>,
)
```

Add one optional input for row-scoped message precedence:

```kotlin
fun buildDetailsStageUi(
    state: DetailsUiState,
    isAuthenticated: Boolean,
    torrentRows: List<DetailsTorrentRowUiModel>,
    activeRowId: String?,
    activeTorrServeRowId: String?,
    isTorrServeBusy: Boolean,
    torrServeMessageText: String? = null,
): DetailsStageUiModel
```

- [ ] **Step 4: Implement compact hero-line derivation**

Build only the copy the simplified screen needs:
- `heroMetaLine`: season/episode for series, release date for movies, or release date fallback
- `heroStatusLine`: one line only, with priority
  - explicit `torrServeMessageText`
  - `Открывается...`
  - `<quality> • TorrServe • свежие данные`
  - `<quality> • прямая ссылка`
  - `<quality> • TorrServe • данные из кэша`

Collapse first-screen secondary actions to `emptyList()`. Preserve the busy logic that disables all TorrServe-supported quality actions while keeping direct-link rows enabled when they are present.

- [ ] **Step 5: Re-run the mapper tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsStageModelsTest"`

Expected: PASS

- [ ] **Step 6: Commit the stage-model simplification**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt
git commit -m "refactor: simplify details stage model"
```

## Chunk 2: Rebuild The Screen Into Two Calm Zones

### Task 2: Remove the first-screen technical sheet and simplify the hero composition

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
- Modify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt`

- [ ] **Step 1: Write a failing screen test for the simplified first screen**

```kotlin
@Test
fun simplifiedDetails_hidesTechSheetAndShowsCompactStatusLine() {
    val rows = listOf(
        row("first", "1080p", "https://example.com/1.torrent", true),
        row("second", "720p", "https://example.com/2.torrent", true),
    )

    composeRule.setDetailsContent(
        state = DetailsUiState(details = detailsWithRows(rows)),
        torrentRows = rows,
    )

    composeRule.onNodeWithTag(torrServeTag("first")).assertIsDisplayed()
    composeRule.onNodeWithText("Сезон 9, серия 13").assertExists()
    composeRule.onNodeWithText("1080p • TorrServe • свежие данные").assertExists()
    composeRule.onNodeWithText("Сигнал релиза").assertDoesNotExist()
    composeRule.onNodeWithTag("details-tech-quality").assertDoesNotExist()
}
```

- [ ] **Step 2: Run the Android test compile step to verify the new expectation fails**

Run: `.\gradlew.bat :app:compileDebugAndroidTestKotlin`

Expected: PASS compilation, but the new test should fail until the layout is simplified.

- [ ] **Step 3: Remove the technical-sheet focus state from `DetailsScreen.kt`**

Delete first-screen dependencies on:
- `focusedTechCardId`
- `techRequesters`
- `TechSheet(...)`
- `TechCard(...)`
- `DetailPanel(...)`
- action-to-tech-card left navigation

The content state should only keep:
- hero strings from `stageUi`
- right action rail
- one compact inline status line

- [ ] **Step 4: Recompose the hero into a simpler readable block**

Update `DetailsScreen.kt` so the hero shows only:
- poster
- title
- one metadata line from `stageUi.heroMetaLine`
- one status line from `stageUi.heroStatusLine`

Keep the visual background and premium tone, but remove dense chip/stat rows. If `torrServeMessage` exists, it should appear through the single status line instead of a separate lower panel.

- [ ] **Step 5: Keep the action rail compact and stable**

The right rail should contain:
- active `Смотреть <quality>`
- remaining quality actions below

Preserve:
- `torrent-torrserve-<rowId>` for supported rows
- `torrent-open-<rowId>` for unsupported rows
- busy disabling behavior for TorrServe actions

- [ ] **Step 6: Re-run the targeted compile step**

Run: `.\gradlew.bat :app:compileDebugAndroidTestKotlin`

Expected: PASS

- [ ] **Step 7: Commit the layout simplification**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt
git commit -m "feat: simplify details screen layout"
```

## Chunk 3: Lock Down The Vertical TV Focus Model

### Task 3: Update UI tests to match the simplified navigation and preserved behavior

**Files:**
- Modify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt`
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt`

- [ ] **Step 1: Replace tech-sheet-era assertions with rail-era assertions**

Update tests that currently assume:
- `Сигнал релиза`
- `details-tech-*` nodes
- left movement into a lower technical zone

Replace them with assertions for:
- metadata line under the title
- one compact status line
- only vertical movement in the action rail
- no onscreen `Назад` or `Открыть ссылку`

- [ ] **Step 2: Add a focused test for the simplified focus path**

```kotlin
@Test
fun actionRail_upFromFirstAction_movesToBack_withoutLeftSideZone() {
    val rows = listOf(
        row("first", "1080p", "https://example.com/1.torrent", true),
        row("second", "720p", "https://example.com/2.torrent", true),
    )

    composeRule.setDetailsContent(
        state = DetailsUiState(details = detailsWithRows(rows)),
        torrentRows = rows,
    )

    composeRule.onNodeWithTag(torrServeTag("first"))
        .performSemanticsAction(SemanticsActions.RequestFocus)
    composeRule.pressKey(torrServeTag("first"), Key.DirectionUp)
    composeRule.onNodeWithText("Назад").assertIsFocused()
}
```

- [ ] **Step 3: Keep a test for the compact busy/error status line**

```kotlin
@Test
fun busyState_showsOneCompactStatusLine_andKeepsDirectLinkAvailable() {
    val rows = listOf(
        row("active", "1080p", "https://example.com/active.torrent", true),
        row("fallback", "WEBRip", "magnet:?xt=urn:btih:fallback", false),
    )

    composeRule.setDetailsContent(
        state = DetailsUiState(details = detailsWithRows(rows)),
        torrentRows = rows,
        torrServeMessage = TorrServeMessage(rowId = "active", text = "Не удалось открыть TorrServe"),
        activeTorrServeRowId = "active",
        isTorrServeBusy = true,
    )

    composeRule.onNodeWithText("Не удалось открыть TorrServe").assertExists()
    composeRule.onNodeWithTag(openTag("fallback")).assertIsEnabled()
}
```

- [ ] **Step 4: Verify route-level compatibility stays green**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsRouteTest"`

Expected: PASS

If route tests fail because a supported-row semantic tag disappeared, restore the old tag instead of broadening route churn.

- [ ] **Step 5: Re-run the Android test compile step**

Run: `.\gradlew.bat :app:compileDebugAndroidTestKotlin`

Expected: PASS

- [ ] **Step 6: Commit the simplified UI coverage**

```bash
git add app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt
git commit -m "test: cover simplified details focus behavior"
```

## Chunk 4: Final Verification

### Task 4: Prove the simplified details screen works before handoff

**Files:**
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt`
- Verify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt`
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt`

- [ ] **Step 1: Run the stage-model tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsStageModelsTest"`

Expected: PASS

- [ ] **Step 2: Run the route/unit verification**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsRouteTest"`

Expected: PASS

- [ ] **Step 3: Run the full unit test suite**

Run: `.\gradlew.bat :app:testDebugUnitTest`

Expected: PASS

- [ ] **Step 4: Compile and assemble the Android app**

Run: `.\gradlew.bat :app:compileDebugAndroidTestKotlin :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: If a device is available, install and run the focused details instrumentation suite**

Run:

```bash
.\gradlew.bat :app:installDebug :app:installDebugAndroidTest
adb -s 192.168.2.246:5555 shell am instrument -w -e class com.kraat.lostfilmnewtv.ui.DetailsScreenTest com.kraat.lostfilmnewtv.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: `OK`

If `connectedDebugAndroidTest` still fails with the known UTP `Invalid file path` issue, document that explicitly and rely on the direct `adb` instrumentation result as the device-level check.

- [ ] **Step 6: Final commit**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt
git commit -m "feat: simplify cinematic details screen"
```
