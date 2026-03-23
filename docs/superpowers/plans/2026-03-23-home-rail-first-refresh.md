# Home Rail-First Refresh Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh `Home` into a rail-first Android TV screen with a compact top utility row, a cohesive bottom stage, more deliberate card styling, and deterministic remote navigation between the rail and utility actions.

**Architecture:** Keep the data flow unchanged where possible: `HomeViewModel` continues to load and select releases, while `HomeScreen` is refactored into a clearer layout shell with dedicated header/stage UI pieces. `HomeRail` stays responsible for the release row, but it participates in explicit cross-zone focus wiring so `Up` and `Down` between the rail and utility row are deterministic instead of being left to incidental spatial focus behavior.

**Tech Stack:** Kotlin, Jetpack Compose for TV, Compose focus APIs, Material 3, Robolectric Compose tests, Android instrumented Compose tests, Gradle Android app module

---

**Spec Reference:** `docs/superpowers/specs/2026-03-23-home-rail-first-refresh-design.md`

**Execution Notes:** Follow @test-driven-development for all screen-behavior changes. Treat the rail as the primary interaction surface throughout implementation. Keep `HomeViewModel` and `AppNavGraph` unchanged unless compile or regression tests prove that a minimal contract adjustment is required.

## Planned File Structure

- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt`
  Responsibility: Own the refreshed `Home` composition, integrate the compact header, dominant rail shell, state panels, and the new bottom stage.
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeHeader.kt`
  Responsibility: Render the compact `Новые релизы` header plus unified utility actions (`Войти`/`Выйти`, `Настройки`, `Обновить`) with stable tags and shared styling.
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeBottomStage.kt`
  Responsibility: Render the non-focusable bottom stage for the currently selected release plus secondary status/service text such as app version and update status.
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeRail.kt`
  Responsibility: Preserve horizontal release-row behavior while accepting the explicit focus wiring needed for `Up` to the utility row and `Down` back to the selected card.
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/components/PosterCard.kt`
  Responsibility: Refresh the rail card style so focused state, watched badge, and season/episode metadata all match the new `Home` visual language.
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/theme/Color.kt`
  Responsibility: Add named `Home` color tokens for panels, borders, focus accent, and secondary text instead of relying on ad hoc inline colors.
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeUiState.kt`
  Responsibility: Keep the current state contract if possible; only adjust if the stage layout needs a minimal presentational field that cannot be derived inside `HomeScreen`.
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeScreenTest.kt`
  Responsibility: Lock the rail-first layout contract, top utility action placement, bottom-stage rendering, and removal of the detached footer assumptions.
- Modify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/HomeScreenTest.kt`
  Responsibility: Lock the real key-navigation contract: initial focus on rail, `Up` into utilities, `Down` back to the selected card, and stage updates when the focused card changes.
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModelTest.kt`
  Responsibility: Confirm the screen redesign does not break existing page loading, stale, paging, or watched-state behavior.
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt`
  Responsibility: Confirm the refreshed `Home` still exposes `Настройки` and `Обновить` correctly when composed through the real navigation graph.

## Chunk 1: Lock The Rail-First Contract In Tests

### Task 1: Rewrite `HomeScreenTest` around the refreshed layout contract

**Files:**
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeScreenTest.kt`

- [ ] **Step 1: Replace the old footer-position expectations with failing tests for the compact top utility row and cohesive bottom stage.**

Add or rewrite tests so they prove the new `Home` contract explicitly:

```kotlin
@Test
fun homeScreen_rendersUtilityActionsInTopHeader_withoutDetachedBottomFooter() {
    composeRule.setContent {
        LostFilmTheme {
            HomeScreen(
                state = seededState(),
                isAuthenticated = false,
                appVersionText = "0.1.0",
                savedAppUpdate = SavedAppUpdate(
                    latestVersion = "0.2.0",
                    apkUrl = "https://example.test/app.apk",
                ),
            )
        }
    }

    composeRule.onNodeWithTag("home-action-auth").assertExists()
    composeRule.onNodeWithTag("home-action-settings").assertExists()
    composeRule.onNodeWithTag("home-action-update").assertExists()
    composeRule.onNodeWithTag("home-bottom-stage").assertExists()
    composeRule.onNodeWithText("0.1.0").assertExists()
}
```

Add a selected-context regression:

```kotlin
@Test
fun homeScreen_bottomStage_showsSelectedReleaseContext() {
    composeRule.setContent {
        LostFilmTheme {
            HomeScreen(state = seededState())
        }
    }

    composeRule.onNodeWithTag("home-bottom-stage").assertExists()
    composeRule.onNodeWithText("9-1-1").assertExists()
    composeRule.onNodeWithText("Маменькин сынок").assertExists()
    composeRule.onNodeWithText("14.03.2026").assertExists()
}
```

Keep the existing season/episode card regression, but make sure it now lives alongside the refreshed stage/header assertions rather than the old footer geometry assumptions.

- [ ] **Step 2: Add a failing stale/paging-state layout regression that proves state surfaces now live inside the shared `Home` panel language instead of disconnected text blocks.**

Use tags or explicit text placement contracts, for example:

```kotlin
@Test
fun homeScreen_withPagingError_keepsRailVisible_andShowsInlineStatusPanel() {
    composeRule.setContent {
        LostFilmTheme {
            HomeScreen(
                state = seededState().copy(pagingErrorMessage = "Не удалось догрузить страницу"),
            )
        }
    }

    composeRule.onNodeWithTag(posterTag(firstDetailsUrl)).assertExists()
    composeRule.onNodeWithTag("home-paging-status").assertExists()
}
```

Do the same for stale state if a dedicated tag keeps the expectation clearer than pure text matching.

- [ ] **Step 3: Run the focused Robolectric `HomeScreenTest` class and verify RED.**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.home.HomeScreenTest"
```

Expected: FAIL because the current `Home` still uses the detached footer layout, has no unified top action tags, no stage container tag, and no new inline status surfaces.

### Task 2: Extend the Android `HomeScreenTest` to lock real remote navigation

**Files:**
- Modify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/HomeScreenTest.kt`

- [ ] **Step 1: Add a failing connected-focus test for `Up` from the rail into the utility row and `Down` back to the selected rail card.**

Extend the existing instrumented test class with a scenario like:

```kotlin
@Test
fun railFocus_movesUpToUtilityRow_andDownBackToSelectedCard() {
    composeRule.setContent {
        LostFilmTheme {
            var state by remember { mutableStateOf(seededState()) }
            HomeScreen(
                state = state,
                savedAppUpdate = SavedAppUpdate("0.2.0", "https://example.test/app.apk"),
                onItemFocused = { focusedKey ->
                    state = state.copy(
                        selectedItemKey = focusedKey,
                        selectedItem = state.items.find { it.detailsUrl == focusedKey },
                    )
                },
            )
        }
    }

    composeRule.onNodeWithTag(posterTag(firstDetailsUrl)).performSemanticsAction(SemanticsActions.RequestFocus)
    composeRule.onNodeWithTag(posterTag(firstDetailsUrl)).performKeyInput {
        keyDown(Key.DirectionUp)
        keyUp(Key.DirectionUp)
    }
    composeRule.onNodeWithTag("home-action-settings").assertIsFocused()

    composeRule.onNodeWithTag("home-action-settings").performKeyInput {
        keyDown(Key.DirectionDown)
        keyUp(Key.DirectionDown)
    }
    composeRule.onNodeWithTag(posterTag(firstDetailsUrl)).assertIsFocused()
}
```

- [ ] **Step 2: Add a stage-update regression proving horizontal card movement still updates the bottom stage.**

Reuse the existing two-item setup but assert the `bottom stage` content instead of the old plain bottom info assumptions:

```kotlin
composeRule.onNodeWithText("Необратимость").assertExists()
```

after moving focus right, while also checking that `home-bottom-stage` remains present.

- [ ] **Step 3: Run the connected `HomeScreen` instrumented class if a device/emulator is available and verify RED.**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.HomeScreenTest
```

Expected: FAIL because the current screen does not have explicit top-row focus tags or deterministic `Up`/`Down` behavior.

If no connected device or emulator is available, note that this RED step is blocked and continue with the rest of the plan, but keep the test code ready for later verification.

## Chunk 2: Build The Shared Home Visual Language

### Task 3: Add explicit `Home` theme tokens and refresh `PosterCard`

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/components/PosterCard.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeScreenTest.kt`

- [ ] **Step 1: Add named `Home` color tokens in `Color.kt` before changing the card visuals.**

Introduce tokens such as:

```kotlin
val HomePanelSurface = Color(0xCC0B1520)
val HomePanelSurfaceStrong = Color(0xE6142432)
val HomePanelBorder = Color(0x26FFFFFF)
val HomePanelBorderFocus = Color(0x66F2C46E)
val HomeAccentGold = Color(0xFFF2C46E)
val HomeAccentGoldGlow = Color(0xFFFFE0A8)
val HomeTextSecondary = Color(0xFFCCDAE6)
val HomeTextMuted = Color(0xFF8FA7BB)
val HomeStatusError = Color(0xFFE07060)
```

Prefer reusing or promoting existing details tokens only if the names remain semantically correct for `Home`; do not keep `Home` styling dependent on `Details*` names.

- [ ] **Step 2: Write the minimal card styling update in `PosterCard.kt` using the new tokens.**

Refresh the focused and unfocused card treatment so it is more intentional:

```kotlin
val borderColor = if (isFocused) HomeAccentGoldGlow else HomePanelBorder
val overlayColor = if (isFocused) HomePanelSurfaceStrong else HomePanelSurface
```

Apply the same style family to:
- focused outline / glow
- season/episode overlay
- watched badge background

Do not change card size, card metadata rules, or the rail’s horizontal semantics in this task.

- [ ] **Step 3: Re-run the Robolectric `HomeScreenTest` class and verify the refreshed card visuals compile cleanly while the larger layout contract remains red.**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.home.HomeScreenTest"
```

Expected: still FAIL overall because the layout/header/stage contract is not implemented yet, but the compilation should succeed and existing poster metadata assertions should remain green.

### Task 4: Replace the old lower info block with a cohesive bottom stage and compact header

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeHeader.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeBottomStage.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/BottomInfoPanel.kt`

- [ ] **Step 1: Create `HomeHeader.kt` with stable action tags and a unified top-row action treatment.**

Add a dedicated header composable such as:

```kotlin
@Composable
fun HomeHeader(
    isAuthenticated: Boolean,
    hasSavedUpdate: Boolean,
    onSettingsClick: () -> Unit,
    onAuthClick: () -> Unit,
    onInstallUpdateClick: () -> Unit,
    settingsFocusRequester: FocusRequester,
    authFocusRequester: FocusRequester,
    updateFocusRequester: FocusRequester?,
    downTarget: FocusRequester,
)
```

Tag the actions explicitly:
- `home-action-settings`
- `home-action-auth`
- `home-action-update`

Use one shared action style rather than raw default `Button` treatment.

- [ ] **Step 2: Create `HomeBottomStage.kt` and move selected-release context into a single stage surface.**

Add a composable contract like:

```kotlin
@Composable
fun HomeBottomStage(
    item: ReleaseSummary?,
    appVersionText: String,
    appUpdateStatusText: String?,
)
```

Requirements:
- wrap the whole stage in `Modifier.testTag("home-bottom-stage")`
- render selected title, episode title, and date
- include a compact secondary block for app version and update status so the detached footer can disappear
- stay non-focusable

At this step, leave `BottomInfoPanel.kt` either:
- as a delegating wrapper over `HomeBottomStage`
- or replace its internal implementation with the stage panel while keeping the file temporarily

Do not delete the file yet if keeping it as a thin compatibility wrapper makes the diff easier to stage.

- [ ] **Step 3: Recompose `HomeScreen.kt` around the new header + rail + stage shell and restyle the transient status panels.**

Reshape the screen to:
- compact header at the top
- dominant rail in the middle
- stage below
- no detached bottom-right footer

Render stale, paging, and update-related copy inside styled inline panels with stable tags such as:
- `home-stale-status`
- `home-paging-status`

Only keep full-screen error when `state.items.isEmpty()`.

- [ ] **Step 4: Re-run `HomeScreenTest` and verify GREEN for the refreshed layout contract.**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.home.HomeScreenTest"
```

Expected: PASS for the new top-row action, bottom-stage, and integrated-status expectations.

- [ ] **Step 5: Commit the refreshed `Home` shell.**

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeHeader.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeBottomStage.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/home/BottomInfoPanel.kt
git commit -m "feat: add rail-first home layout shell"
```

## Chunk 3: Make Focus Navigation Deterministic

### Task 5: Wire explicit `Up`/`Down` focus movement between the rail and top utility actions

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeRail.kt`
- Modify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/HomeScreenTest.kt`

- [ ] **Step 1: Move the cross-zone focus wiring into `HomeScreen` and pass the necessary requesters into `HomeRail`.**

Use explicit `FocusRequester`s so the layout does not rely on incidental spatial search:

```kotlin
val settingsRequester = remember { FocusRequester() }
val authRequester = remember { FocusRequester() }
val updateRequester = remember { FocusRequester() }
val cardFocusRequesters = remember(itemKeys) { itemKeys.associateWith { FocusRequester() } }
val currentCardRequester = cardFocusRequesters[focusedItemKey] ?: cardFocusRequesters[firstItemKey]
```

The top row should use `focusProperties` to point `down` at `currentCardRequester`.
The rail cards should use `focusProperties` so `up` targets the highest-priority visible utility action:
- `home-action-update` when present
- otherwise auth action
- otherwise settings action

- [ ] **Step 2: Refactor `HomeRail.kt` to accept externally managed card requesters while preserving horizontal row behavior.**

Adjust the signature to something like:

```kotlin
fun HomeRail(
    items: List<ReleaseSummary>,
    focusedItemKey: String?,
    cardFocusRequesters: Map<String, FocusRequester>,
    topActionRequester: FocusRequester?,
    ...
)
```

Implementation rules:
- preserve `posterTag(detailsUrl)` semantics
- keep `LaunchedEffect(items, focusedItemKey)` focus restore behavior
- keep `onEndReached()` logic unchanged
- only add the minimal `focusProperties` / requester usage needed for deterministic cross-zone navigation

- [ ] **Step 3: Run the connected Android `HomeScreenTest` class and verify GREEN for the new remote-navigation contract.**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.HomeScreenTest
```

Expected: PASS, specifically for:
- initial rail focus
- `Up` to the utility row
- `Down` back to the selected rail card
- stage updates after horizontal movement

If no device or emulator is available, explicitly record the connected-focus verification as a remaining gap and continue with unit-level verification.

- [ ] **Step 4: Commit the deterministic focus navigation pass.**

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeRail.kt app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/HomeScreenTest.kt
git commit -m "feat: improve home focus navigation"
```

## Chunk 4: Regression And Integration Verification

### Task 6: Verify the refreshed `Home` through view-model and nav-graph regressions

**Files:**
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModelTest.kt`
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeScreenTest.kt`
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`

- [ ] **Step 1: Run the focused home and navigation unit/regression suite.**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.home.HomeScreenTest" --tests "com.kraat.lostfilmnewtv.ui.home.HomeViewModelTest" --tests "com.kraat.lostfilmnewtv.navigation.AppNavGraphTorrServeTest"
```

Expected: `BUILD SUCCESSFUL`

Use failures to decide whether a minimal route contract adjustment is required. If the redesign keeps button labels and callbacks stable, `AppNavGraph.kt` should not need behavior changes.

- [ ] **Step 2: Build the app module to catch Compose signature or theme-token regressions.**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Review the final `Home` diff and verify only intended files changed.**

Run:

```powershell
git status --short
git diff -- app/src/main/java/com/kraat/lostfilmnewtv/ui/home app/src/main/java/com/kraat/lostfilmnewtv/ui/components/PosterCard.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/theme/Color.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/home app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/HomeScreenTest.kt
```

Expected:
- the redesign stays local to `Home`, card styling, and theme tokens
- `HomeViewModel.kt` and `AppNavGraph.kt` remain unchanged unless tests forced a minimal contract fix

- [ ] **Step 4: Final commit for the rail-first refresh.**

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/home app/src/main/java/com/kraat/lostfilmnewtv/ui/components/PosterCard.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/theme/Color.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/home app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/HomeScreenTest.kt
git commit -m "feat: refresh home with rail-first layout"
```

## Execution Notes

- Do not let the stage become focusable just because it contains more information. The approved design is explicitly rail-first with a read-only stage.
- Keep the header compact. If a visual tweak makes the utility row louder than the rail, revert it.
- Preserve the existing `Настройки`, auth, and update callbacks so the nav graph can keep working without route churn.
- Prefer focused new helper files (`HomeHeader.kt`, `HomeBottomStage.kt`) over growing `HomeScreen.kt` into another monolith.
- If connected Android verification is blocked by device availability, state that clearly in the execution handoff instead of implying the focus contract was fully proven.
