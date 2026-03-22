# Settings Two-Pane Layout Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the long stacked settings screen with a more compact two-pane Android TV layout: left-side section navigation and a right-side content panel with a richer updates view.

**Architecture:** Keep all real settings data and side effects in the existing `SettingsViewModel`/`SettingsRoute` flow, and make the layout change local to `SettingsScreen`. Add a small UI-only section-selection model remembered with `rememberSaveable`, render only one section at a time on the right, and preserve the existing callback and tag contract so business logic does not need to move.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Compose semantics/test tags, JUnit4, Robolectric Compose tests, existing settings/update state flow

**Reference Spec:** `docs/superpowers/specs/2026-03-22-settings-two-pane-layout-design.md`

**Execution Notes:** Follow @test-driven-development for the screen behavior changes. Keep `SettingsRoute.kt`, `SettingsUiState.kt`, and `SettingsViewModel.kt` unchanged unless compile or test failures prove a contract adjustment is necessary.

---

## Planned File Structure

- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreenTest.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsRoute.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModel.kt`
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModelTest.kt`

Notes:
- Keep the existing setting-option tags stable:
  - `settings-quality-*`
  - `settings-update-mode-*`
  - `settings-tv-channel-*`
- Add stable section tags for the left rail:
  - `settings-section-quality`
  - `settings-section-updates`
  - `settings-section-channel`
- Default the remembered active section to `Обновления` to match the approved “status-first” emphasis. If product feedback later prefers the first rail item by default, only the `rememberSaveable` initializer should need to change.

## Chunk 1: Lock The New Screen Contract In Tests

### Task 1: Rewrite `SettingsScreenTest` around the two-pane behavior

**Files:**
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreenTest.kt`

- [ ] **Step 1: Replace the old stacked-layout expectations with failing two-pane tests**

Update `SettingsScreenTest.kt` to cover the new contract explicitly:

```kotlin
@Test
fun settingsScreen_defaultsToUpdatesSection_andSwitchesVisiblePanel() {
    composeRule.setContent {
        LostFilmTheme {
            SettingsScreen(
                selectedQuality = PlaybackQualityPreference.Q1080,
                onQualitySelected = {},
                selectedUpdateMode = UpdateCheckMode.MANUAL,
                selectedChannelMode = AndroidTvChannelMode.ALL_NEW,
                installedVersionText = "0.1.0",
                latestVersionText = "0.2.0",
                statusText = "Доступно обновление",
                isCheckingForUpdates = false,
                installUrl = "https://example.test/app.apk",
                onUpdateModeSelected = {},
                onChannelModeSelected = {},
                onCheckForUpdatesClick = {},
                onInstallUpdateClick = {},
            )
        }
    }

    composeRule.onNodeWithTag("settings-section-updates").assertIsSelected()
    composeRule.onNodeWithText("Установлена версия: 0.1.0").assertExists()
    assertEquals(
        0,
        composeRule.onAllNodesWithTag("settings-quality-1080").fetchSemanticsNodes().size,
    )

    composeRule.onNodeWithTag("settings-section-quality")
        .performSemanticsAction(SemanticsActions.OnClick)

    composeRule.onNodeWithTag("settings-section-quality").assertIsSelected()
    composeRule.onNodeWithTag("settings-quality-1080").assertExists()
    assertEquals(
        0,
        composeRule.onAllNodesWithText("Установлена версия: 0.1.0").fetchSemanticsNodes().size,
    )
}
```

Add one focused updates-section regression:

```kotlin
@Test
fun settingsScreen_updatesSection_showsStatusHeader_andUpdateActions() {
    composeRule.setContent {
        LostFilmTheme {
            SettingsScreen(
                selectedQuality = PlaybackQualityPreference.Q1080,
                onQualitySelected = {},
                selectedUpdateMode = UpdateCheckMode.QUIET_CHECK,
                selectedChannelMode = AndroidTvChannelMode.ALL_NEW,
                installedVersionText = "0.1.0",
                latestVersionText = "0.2.0",
                statusText = "Доступно обновление",
                isCheckingForUpdates = false,
                installUrl = "https://example.test/app.apk",
                onUpdateModeSelected = {},
                onChannelModeSelected = {},
                onCheckForUpdatesClick = {},
                onInstallUpdateClick = {},
            )
        }
    }

    composeRule.onNodeWithText("Обновления").assertExists()
    composeRule.onNodeWithText("Установлена версия: 0.1.0").assertExists()
    composeRule.onNodeWithText("Последняя версия: 0.2.0").assertExists()
    composeRule.onNodeWithText("Доступно обновление").assertExists()
    composeRule.onNodeWithText("Проверить обновления").assertExists()
    composeRule.onNodeWithText("Скачать и установить").assertExists()
}
```

Keep one callback-oriented regression for the simple sections:

```kotlin
@Test
fun settingsScreen_qualityAndChannelSections_stillInvokeCallbacks() {
    val selectedQualities = mutableListOf<PlaybackQualityPreference>()
    val selectedChannels = mutableListOf<AndroidTvChannelMode>()

    composeRule.setContent { ... }

    composeRule.onNodeWithTag("settings-section-quality")
        .performSemanticsAction(SemanticsActions.OnClick)
    composeRule.onNodeWithTag("settings-quality-720")
        .performSemanticsAction(SemanticsActions.OnClick)

    composeRule.onNodeWithTag("settings-section-channel")
        .performSemanticsAction(SemanticsActions.OnClick)
    composeRule.onNodeWithTag("settings-tv-channel-unwatched")
        .performSemanticsAction(SemanticsActions.OnClick)

    assertEquals(listOf(PlaybackQualityPreference.Q720), selectedQualities)
    assertEquals(listOf(AndroidTvChannelMode.UNWATCHED), selectedChannels)
}
```

Remove or rewrite tests that only prove the old full-screen vertical scroll, because the new contract is sectioned rather than one long stacked page.

- [ ] **Step 2: Run the screen test to verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsScreenTest"
```

Expected: FAIL because the current screen still renders all sections at once, has no left rail section tags, and does not conditionally swap the right panel.

- [ ] **Step 3: Commit the test contract once it is written**

```powershell
git add app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreenTest.kt
git commit -m "test: define two-pane settings screen contract"
```

## Chunk 2: Implement The Two-Pane Composable

### Task 2: Add section selection and render only one panel at a time

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Implement a small UI-local section model inside `SettingsScreen.kt`**

Add a private enum near the screen:

```kotlin
private enum class SettingsSection(
    val railTitle: String,
    val panelTitle: String,
    val tag: String,
) {
    QUALITY("Качество", "Качество по умолчанию", "settings-section-quality"),
    UPDATES("Обновления", "Обновления", "settings-section-updates"),
    CHANNEL("Канал Android TV", "Канал Android TV", "settings-section-channel"),
}
```

Remember the active section with `rememberSaveable` using the enum name:

```kotlin
var selectedSectionName by rememberSaveable { mutableStateOf(SettingsSection.UPDATES.name) }
val selectedSection = SettingsSection.valueOf(selectedSectionName)
```

Keep this state in the composable only. Do not add it to `SettingsUiState` or `SettingsViewModel`.

- [ ] **Step 2: Replace the root stacked `Column` with a split `Row`**

Reshape the screen to:

```kotlin
Row(
    modifier = Modifier
        .fillMaxSize()
        .background(BackgroundPrimary)
        .padding(horizontal = 48.dp, vertical = 32.dp),
    horizontalArrangement = Arrangement.spacedBy(24.dp),
) {
    SettingsSectionRail(...)
    SettingsContentPanel(...)
}
```

Implementation requirements:
- left rail width roughly `280.dp` or `0.30f` of the available width
- right panel fills the remaining width
- keep the screen title `Настройки`
- move scrolling to the right content panel only, not the whole screen
- expose selected semantics on rail items the same way option buttons already do

Use a dedicated helper for the rail so the branching stays readable:

```kotlin
@Composable
private fun SettingsSectionRail(
    selectedSection: SettingsSection,
    onSectionSelected: (SettingsSection) -> Unit,
)
```

- [ ] **Step 3: Render the active section content conditionally**

Inside the right panel, switch on `selectedSection`:

```kotlin
when (selectedSection) {
    SettingsSection.QUALITY -> QualitySectionContent(...)
    SettingsSection.UPDATES -> UpdatesSectionContent(...)
    SettingsSection.CHANNEL -> ChannelSectionContent(...)
}
```

Implementation rules:
- `QualitySectionContent` renders only the quality title and the three quality buttons
- `ChannelSectionContent` renders only the channel title and the three channel buttons
- keep existing tags/selected semantics for those option buttons
- when a section is inactive, its controls should not be composed

This is what makes the screen materially more compact.

- [ ] **Step 4: Re-run the screen test to verify GREEN**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsScreenTest"
```

Expected: PASS

- [ ] **Step 5: Commit the two-pane layout foundation**

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt
git commit -m "feat: add two-pane settings layout"
```

### Task 3: Give `Обновления` the richer header while keeping the other sections simple

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Introduce a compact updates summary card**

Add an `UpdatesStatusCard` helper above the update-mode buttons:

```kotlin
@Composable
private fun UpdatesStatusCard(
    installedVersionText: String,
    latestVersionText: String?,
    statusText: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF16293C), RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Обновления", ...)
        SettingsValueRow("Установлена версия: $installedVersionText")
        SettingsValueRow("Последняя версия: ${latestVersionText ?: "-"}")
        statusText?.let(::SettingsValueRow)
    }
}
```

The right panel for updates should then render in this order:
1. summary card
2. mode buttons
3. `Проверить обновления`
4. optional `Скачать и установить`

- [ ] **Step 2: Keep the simple sections deliberately lighter**

Refactor the quality/channel branches so they use a shared helper:

```kotlin
@Composable
private fun SettingsOptionsSection(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
)
```

Rules:
- no summary card for quality or channel
- tighter spacing than the updates section
- preserve the gold selected-state styling already used today
- do not introduce extra explanatory text unless it genuinely helps focus

- [ ] **Step 3: Re-run the screen test suite after the UI polish**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsScreenTest"
```

Expected: PASS with the richer updates section and the simpler quality/channel sections still behaving identically.

- [ ] **Step 4: Commit the updates emphasis pass**

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt
git commit -m "feat: emphasize updates in settings pane"
```

## Chunk 3: Regression And Handoff Verification

### Task 4: Prove the layout redesign did not change settings behavior

**Files:**
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsRoute.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModel.kt`
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsViewModelTest.kt`
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreenTest.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Run the focused settings unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsScreenTest" --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsViewModelTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Compile the app module to catch Compose signature regressions**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Review the final diff**

Run:

```powershell
git status --short
git diff -- app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreenTest.kt
```

Expected:
- only the intended settings UI files are modified for this feature
- `SettingsRoute.kt` and `SettingsViewModel.kt` remain unchanged unless a compile/test regression forced a small contract fix

- [ ] **Step 4: Final commit**

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreenTest.kt
git commit -m "feat: compact settings with two-pane layout"
```

## Execution Notes

- Do not reintroduce full-screen root scrolling just to make tests easier. The right-side content panel is the only place that should scroll if a future settings section grows.
- Preserve current button labels and callbacks so `SettingsRoute.kt` and `SettingsViewModel.kt` can remain unchanged.
- Prefer small private helpers inside `SettingsScreen.kt` over creating extra files for one-off UI pieces.
- If a focus-navigation issue appears during implementation, solve it locally in the composable with focus properties instead of promoting section selection into the view model.
