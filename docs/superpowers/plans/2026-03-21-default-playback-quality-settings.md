# Default Playback Quality Settings Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an app-level default playback quality setting and replace the details-screen quality list with one `Смотреть` action that uses the saved preference with nearest-quality fallback.

**Architecture:** Introduce a small playback-preferences layer with an explicit quality enum and a lightweight `SharedPreferences` store. Wire the saved preference through `LostFilmApplication` and `AppNavGraph`, expose it in a dedicated `Настройки` screen, and resolve one playback row in `DetailsRoute` before rendering the details UI. Simplify the details stage model so the first screen knows only about the resolved primary action instead of a per-quality action rail.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, Android `SharedPreferences`, JUnit4, Robolectric, Compose UI tests, existing LostFilm/TorrServe integration

**Reference Spec:** `docs/superpowers/specs/2026-03-21-default-playback-quality-settings-design.md`

**Execution Notes:** Follow @test-driven-development for each behavior change and finish with @verification-before-completion before claiming success.

---

## File Map

- Create: `app/src/main/java/com/kraat/lostfilmnewtv/playback/PlaybackQualityPreference.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/playback/PlaybackPreferencesStore.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsPlaybackPreferenceResolver.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/playback/PlaybackPreferencesStoreTest.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsPlaybackPreferenceResolverTest.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreenTest.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppDestination.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsRoute.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt`
- Modify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt`
- Verify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/HomeScreenTest.kt`

Notes:
- Keep the details playback semantic tag stable for the resolved row: `torrent-torrserve-<rowId>`.
- Filter playback candidates in `DetailsRoute` down to TorrServe-supported rows only; unsupported rows must not surface as actions or fallback paths.
- Keep the default preference at `1080p` when nothing has been saved.
- Do not add a ViewModel for settings unless implementation hits a real state-management blocker; this is a small local preference flow.

## Chunk 1: Add Playback Preference Foundations

### Task 1: Add explicit quality buckets and a pure resolver for details rows

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/playback/PlaybackQualityPreference.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsPlaybackPreferenceResolver.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsPlaybackPreferenceResolverTest.kt`

- [ ] **Step 1: Write failing resolver tests first**

```kotlin
class DetailsPlaybackPreferenceResolverTest {
    @Test
    fun resolvePreferredTorrentRow_prefersExactMatch_beforeFallback() {
        val rows = listOf(
            row("r0", "1080p", "https://example.com/1080.torrent"),
            row("r1", "720p", "https://example.com/720.torrent"),
        )

        val result = resolvePreferredTorrentRow(PlaybackQualityPreference.Q720, rows)

        assertEquals("r1", result?.rowId)
    }

    @Test
    fun resolvePreferredTorrentRow_usesNearestAvailableQuality_andBreaksTiesUpward() {
        val rows = listOf(
            row("sd", "SD", "https://example.com/sd.torrent"),
            row("fullhd", "1080p", "https://example.com/1080.torrent"),
        )

        val result = resolvePreferredTorrentRow(PlaybackQualityPreference.Q720, rows)

        assertEquals("fullhd", result?.rowId)
    }

    @Test
    fun resolvePreferredTorrentRow_returnsFirstRow_whenOnlyUnknownLabelsExist() {
        val rows = listOf(
            row("unknown-0", "WEBRip", "https://example.com/webrip.torrent"),
            row("unknown-1", "Dubbed", "https://example.com/dubbed.torrent"),
        )

        val result = resolvePreferredTorrentRow(PlaybackQualityPreference.Q1080, rows)

        assertEquals("unknown-0", result?.rowId)
    }
}
```

- [ ] **Step 2: Run the resolver test to verify RED**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsPlaybackPreferenceResolverTest"`

Expected: FAIL with unresolved references for `PlaybackQualityPreference` and `resolvePreferredTorrentRow`.

- [ ] **Step 3: Implement the smallest quality model and resolver**

Use an explicit enum with stable storage values and an integer rank:

```kotlin
enum class PlaybackQualityPreference(
    val storageValue: String,
    val rank: Int,
) {
    Q480("480", 0),
    Q720("720", 1),
    Q1080("1080", 2);

    companion object {
        fun fromStorageValue(raw: String?): PlaybackQualityPreference =
            entries.firstOrNull { it.storageValue == raw } ?: Q1080
    }
}
```

In `DetailsPlaybackPreferenceResolver.kt`, keep the resolver pure:

```kotlin
fun resolvePreferredTorrentRow(
    preference: PlaybackQualityPreference,
    rows: List<DetailsTorrentRowUiModel>,
): DetailsTorrentRowUiModel? { ... }
```

Implementation rules:
- normalize labels into `Q1080`, `Q720`, `Q480`, or `null`
- map `MP4` to `Q720`
- choose exact match first
- otherwise choose nearest ranked match
- if distance ties, choose the higher-ranked match
- if all labels are unknown, return `rows.firstOrNull()`

- [ ] **Step 4: Re-run the resolver test to verify GREEN**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsPlaybackPreferenceResolverTest"`

Expected: PASS

- [ ] **Step 5: Commit the resolver slice**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/playback/PlaybackQualityPreference.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsPlaybackPreferenceResolver.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsPlaybackPreferenceResolverTest.kt
git commit -m "feat: add playback quality resolver"
```

### Task 2: Persist the chosen default quality locally

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/playback/PlaybackPreferencesStore.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/playback/PlaybackPreferencesStoreTest.kt`

- [ ] **Step 1: Write failing store tests**

```kotlin
@RunWith(RobolectricTestRunner::class)
class PlaybackPreferencesStoreTest {
    @Test
    fun readDefaultQuality_returns1080_whenNothingWasSaved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PlaybackPreferencesStore(context, prefsName = "playback-store-default")

        assertEquals(PlaybackQualityPreference.Q1080, store.readDefaultQuality())
    }

    @Test
    fun writeDefaultQuality_persistsSelectedValue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PlaybackPreferencesStore(context, prefsName = "playback-store-write")

        store.writeDefaultQuality(PlaybackQualityPreference.Q720)

        assertEquals(PlaybackQualityPreference.Q720, store.readDefaultQuality())
    }
}
```

- [ ] **Step 2: Run the store test to verify RED**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStoreTest"`

Expected: FAIL because `PlaybackPreferencesStore` does not exist yet.

- [ ] **Step 3: Implement the minimal `SharedPreferences` store**

```kotlin
class PlaybackPreferencesStore(
    context: Context,
    prefsName: String = "lostfilm_playback_prefs",
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun readDefaultQuality(): PlaybackQualityPreference =
        PlaybackQualityPreference.fromStorageValue(prefs.getString(KEY_DEFAULT_QUALITY, null))

    fun writeDefaultQuality(value: PlaybackQualityPreference) {
        prefs.edit().putString(KEY_DEFAULT_QUALITY, value.storageValue).apply()
    }
}
```

Keep the API synchronous; there is only one small local preference.

- [ ] **Step 4: Re-run the store test to verify GREEN**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStoreTest"`

Expected: PASS

- [ ] **Step 5: Commit the storage slice**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/playback/PlaybackPreferencesStore.kt app/src/test/java/com/kraat/lostfilmnewtv/playback/PlaybackPreferencesStoreTest.kt
git commit -m "feat: persist playback quality preference"
```

## Chunk 2: Add Settings UI And Wire It Into Navigation

### Task 3: Build a TV-friendly settings screen for the quality preference

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreenTest.kt`

- [ ] **Step 1: Write failing compose tests for the settings screen**

```kotlin
@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsScreen_marksCurrentQualityAndInvokesCallback() {
        val clicked = mutableListOf<PlaybackQualityPreference>()

        composeRule.setContent {
            SettingsScreen(
                selectedQuality = PlaybackQualityPreference.Q1080,
                onQualitySelected = { clicked += it },
            )
        }

        composeRule.onNodeWithTag("settings-quality-1080").assertIsSelected()
        composeRule.onNodeWithTag("settings-quality-720")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(PlaybackQualityPreference.Q720), clicked)
    }
}
```

- [ ] **Step 2: Run the settings-screen test to verify RED**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsScreenTest"`

Expected: FAIL because `SettingsScreen` does not exist yet.

- [ ] **Step 3: Implement the minimal settings screen**

Render:
- title `Настройки`
- section label `Качество по умолчанию`
- three vertically ordered options: `1080p`, `720p`, `480p / SD`

Use stable tags and selected semantics:

```kotlin
Modifier
    .testTag("settings-quality-1080")
    .semantics { selected = isSelected }
```

Keep system `Back` as the exit path unless UI work reveals a concrete need for an onscreen back control.

- [ ] **Step 4: Re-run the settings-screen test to verify GREEN**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsScreenTest"`

Expected: PASS

- [ ] **Step 5: Commit the settings-screen slice**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreenTest.kt
git commit -m "feat: add playback quality settings screen"
```

### Task 4: Wire the settings screen and saved preference through the app shell

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppDestination.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt`
- Verify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/HomeScreenTest.kt`

- [ ] **Step 1: Add a failing navigation/integration test**

Add one focused test to `AppNavGraphTorrServeTest.kt`:

```kotlin
@Test
fun home_settingsSelection_changesDefaultQuality_usedByDetails() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = PlaybackPreferencesStore(context, prefsName = "app-nav-playback-settings")
    TestLostFilmApplication.playbackPreferencesStoreOverride = store

    composeRule.setContent { LostFilmTheme { AppNavGraph() } }

    composeRule.waitForText("Новые релизы")
    composeRule.onNodeWithText("Настройки").performSemanticsAction(SemanticsActions.OnClick)
    composeRule.waitForText("Качество по умолчанию")
    composeRule.onNodeWithTag("settings-quality-720").performSemanticsAction(SemanticsActions.OnClick)

    composeRule.activity.onBackPressedDispatcher.onBackPressed()
    composeRule.waitForTag(posterTag(TEST_SUMMARY.detailsUrl))
    composeRule.onNodeWithTag(posterTag(TEST_SUMMARY.detailsUrl))
        .performSemanticsAction(SemanticsActions.OnClick)

    composeRule.waitForTag(routeTorrServeTag(TEST_SUMMARY.detailsUrl, 1))
}
```

Update `TEST_DETAILS` in this file to include at least `1080p` and `720p` rows so the saved preference can change which single action appears.

- [ ] **Step 2: Run the nav/integration test to verify RED**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.navigation.AppNavGraphTorrServeTest"`

Expected: FAIL because the app has no settings destination, no playback store in `LostFilmApplication`, and no home-screen settings entry point yet.

- [ ] **Step 3: Implement the app-shell wiring**

In `LostFilmApplication.kt`, add:

```kotlin
open val playbackPreferencesStore: PlaybackPreferencesStore by lazy {
    PlaybackPreferencesStore(this)
}
```

In `AppDestination.kt`, add:

```kotlin
data object Settings : AppDestination {
    override val route: String = "settings"
}
```

In `HomeScreen.kt`:
- add `onSettingsClick: () -> Unit = {}`
- render a `Настройки` button next to auth

In `AppNavGraph.kt`:
- read `application.playbackPreferencesStore`
- seed `preferredPlaybackQuality` from `readDefaultQuality()`
- keep it in compose state
- navigate to `AppDestination.Settings.route`
- render `SettingsScreen`
- on selection, write to store and update local state
- pass `preferredPlaybackQuality` into `DetailsRoute`

In `AppNavGraphTorrServeTest.kt`:
- extend `TestLostFilmApplication` with `playbackPreferencesStoreOverride`
- clear that override in `tearDown()`

- [ ] **Step 4: Re-run the nav/integration test to verify GREEN**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.navigation.AppNavGraphTorrServeTest"`

Expected: PASS

- [ ] **Step 5: Compile-check existing home-screen instrumentation tests**

Run: `.\gradlew.bat :app:compileDebugAndroidTestKotlin`

Expected: PASS, including `HomeScreenTest.kt` after the new `onSettingsClick` parameter and extra button.

- [ ] **Step 6: Commit the app-shell wiring**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppDestination.kt app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt
git commit -m "feat: wire playback settings into app navigation"
```

## Chunk 3: Replace The Details Quality List With One Resolved Watch Action

### Task 5: Resolve one playback row in `DetailsRoute` and simplify the stage model

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsRoute.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt`

- [ ] **Step 1: Write failing unit tests for the single-action details flow**

In `DetailsStageModelsTest.kt`, replace the per-quality expectation with a single resolved action:

```kotlin
@Test
fun buildStageUi_usesResolvedPlaybackRow_asPrimaryWatchAction() {
    val playbackRow = DetailsTorrentRowUiModel(
        rowId = "row-1",
        label = "720p",
        url = "https://example.com/720",
        isTorrServeSupported = true,
    )

    val ui = buildDetailsStageUi(
        state = DetailsUiState(details = seriesDetails()),
        isAuthenticated = true,
        availableTorrentRowsCount = 2,
        playbackRow = playbackRow,
        activeTorrServeRowId = null,
        isTorrServeBusy = false,
    )

    assertEquals("Смотреть", ui.primaryAction.label)
    assertEquals("row-1", ui.primaryAction.rowId)
    assertEquals("720p • TorrServe", ui.primaryAction.subtitle)
    assertEquals("720p • TorrServe • свежие данные", ui.heroStatusLine)
}
```

In `DetailsRouteTest.kt`, add two behavior checks:

```kotlin
@Test
fun route_usesPreferredQuality_whenMultipleRowsAreAvailable() {
    composeRule.setContent {
        DetailsRoute(
            detailsUrl = detailsUrl,
            repository = repositoryWithRows("1080p", "720p"),
            preferredPlaybackQuality = PlaybackQualityPreference.Q720,
            actionHandler = succeedingActionHandler(),
            linkBuilder = TorrServeLinkBuilder(TorrServeConfig()),
            onBack = {},
        )
    }

    composeRule.waitForNodeWithTag(torrServeButtonTag("$detailsUrl#1"))
    composeRule.onNodeWithTag(torrServeButtonTag("$detailsUrl#1")).assertExists()
}

@Test
fun route_ignoresUnsupportedRows_andDisablesPlaybackWhenNothingSupportedRemains() {
    composeRule.setContent {
        DetailsRoute(
            detailsUrl = detailsUrl,
            repository = repositoryWithSingleRow(label = "1080p", url = "ftp://example.com/file.torrent"),
            preferredPlaybackQuality = PlaybackQualityPreference.Q1080,
            actionHandler = succeedingActionHandler(),
            linkBuilder = TorrServeLinkBuilder(TorrServeConfig()),
            onBack = {},
        )
    }

    composeRule.waitForNodeWithTag("details-primary-action")
    composeRule.onNodeWithTag("details-primary-action").assertIsNotEnabled()
}
```

- [ ] **Step 2: Run the stage-model and route tests to verify RED**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsStageModelsTest" --tests "com.kraat.lostfilmnewtv.ui.details.DetailsRouteTest"`

Expected: FAIL because the stage model still assumes a list of quality actions and the route/screen contracts still expose direct-link-specific behavior.

- [ ] **Step 3: Implement the route/model simplification**

In `DetailsRoute.kt`:
- add `preferredPlaybackQuality: PlaybackQualityPreference = PlaybackQualityPreference.Q1080`
- build torrent rows, then keep only `linkBuilder.supportsSource(link.url)` results for playback selection
- compute `playbackRow` with `resolvePreferredTorrentRow(preferredPlaybackQuality, supportedTorrentRows)`
- keep the existing `TorrServe` busy/message flow, but scope it to the resolved row only
- remove `openExternalLink`/`onOpenLink` plumbing from the route API

In `DetailsStageModels.kt`:
- remove `qualityActions` from `DetailsStageUiModel`
- change the builder contract to accept `availableTorrentRowsCount` and `playbackRow`
- produce one `primaryAction`
- use `label = "Смотреть"`
- use subtitle text derived from the resolved row, for example `1080p • TorrServe`
- keep `qualityStatusText(...)` only for empty-state copy when no row is available
- delete `DetailsStageActionType.OPEN_LINK` and any direct-link-specific branches

- [ ] **Step 4: Re-run the stage-model and route tests to verify GREEN**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsStageModelsTest" --tests "com.kraat.lostfilmnewtv.ui.details.DetailsRouteTest"`

Expected: PASS

- [ ] **Step 5: Commit the route/model slice**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsRoute.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt
git commit -m "feat: resolve single playback action on details"
```

### Task 6: Update the details screen to render only one watch button

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
- Modify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt`

- [ ] **Step 1: Rewrite the screen expectations around one action**

Update `DetailsScreenTest.kt` so the important assertions become:

```kotlin
@Test
fun detailsScreen_showsSingleResolvedWatchAction() {
    val playbackRow = row("preferred", "720p", "https://example.com/720.torrent", true)

    composeRule.setDetailsContent(
        state = DetailsUiState(details = detailsWithRows(listOf(playbackRow))),
        availableTorrentRowsCount = 2,
        playbackRow = playbackRow,
    )

    composeRule.onNodeWithText("Смотреть").assertExists()
    composeRule.onNodeWithTag(torrServeTag("preferred")).assertIsDisplayed()
    assertTrue(composeRule.onAllNodesWithTag(torrServeTag("other")).fetchSemanticsNodes().isEmpty())
}

@Test
fun busyState_disablesSingleWatchButton_andShowsStatusFeedback() {
    val playbackRow = row("preferred", "1080p", "https://example.com/1080.torrent", true)

    composeRule.setDetailsContent(
        state = DetailsUiState(details = detailsWithRows(listOf(playbackRow))),
        availableTorrentRowsCount = 1,
        playbackRow = playbackRow,
        torrServeMessage = TorrServeMessage(rowId = "preferred", text = "Не удалось открыть TorrServe"),
        activeTorrServeRowId = "preferred",
        isTorrServeBusy = true,
    )

    composeRule.onNodeWithTag(torrServeTag("preferred")).assertIsNotEnabled()
    composeRule.onNodeWithText("Не удалось открыть TorrServe").assertExists()
}
```

Remove or rewrite tests that assume:
- a long per-quality vertical rail
- alternate quality focus traversal
- any `torrent-open-*` tag or direct-link playback button

- [ ] **Step 2: Compile the Android test sources before implementation**

Run: `.\gradlew.bat :app:compileDebugAndroidTestKotlin`

Expected: FAIL or compile against outdated helper signatures until `DetailsScreen.kt` and the test helper are updated.

- [ ] **Step 3: Implement the single-action screen**

In `DetailsScreen.kt`:
- replace the per-quality loop with one `StageButton`
- take `availableTorrentRowsCount` and `playbackRow` instead of the full quality list
- remove the multi-row focus-requester list
- keep the existing visual style and hero block
- preserve semantic tags through `primaryActionTag(stageUi.primaryAction)`
- remove the `onOpenLink` callback and any direct-link-specific button rendering

Keep behavior:
- clicking the single button opens TorrServe for the resolved supported row only
- the status line shows the actual resolved row quality
- the button disables when there is no supported row or when the resolved TorrServe row is currently busy

- [ ] **Step 4: Re-run the Android test compile step**

Run: `.\gradlew.bat :app:compileDebugAndroidTestKotlin`

Expected: PASS

- [ ] **Step 5: Commit the details-screen slice**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt
git commit -m "feat: show single watch button on details"
```

## Chunk 4: Verify The Full Playback-Preference Flow

### Task 7: Prove the end-to-end behavior before handoff

**Files:**
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/playback/PlaybackQualityPreference.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/playback/PlaybackPreferencesStore.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsRoute.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsPlaybackPreferenceResolverTest.kt`
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/playback/PlaybackPreferencesStoreTest.kt`
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreenTest.kt`
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt`
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt`
- Verify: `app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt`
- Verify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt`

- [ ] **Step 1: Run the focused new unit tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsPlaybackPreferenceResolverTest" --tests "com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStoreTest" --tests "com.kraat.lostfilmnewtv.ui.settings.SettingsScreenTest"`

Expected: PASS

- [ ] **Step 2: Run the updated route/nav tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsStageModelsTest" --tests "com.kraat.lostfilmnewtv.ui.details.DetailsRouteTest" --tests "com.kraat.lostfilmnewtv.navigation.AppNavGraphTorrServeTest"`

Expected: PASS

- [ ] **Step 3: Run the full app unit suite**

Run: `.\gradlew.bat :app:testDebugUnitTest`

Expected: PASS

- [ ] **Step 4: Compile Android test sources and assemble the app**

Run: `.\gradlew.bat :app:compileDebugAndroidTestKotlin :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: If a device is available, run the focused details instrumentation suite**

Run:

```bash
.\gradlew.bat :app:installDebug :app:installDebugAndroidTest
adb -s 192.168.2.246:5555 shell am instrument -w -e class com.kraat.lostfilmnewtv.ui.DetailsScreenTest com.kraat.lostfilmnewtv.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: `OK`

If device instrumentation is unavailable, explicitly document that only unit/Robolectric coverage and Android-test compilation were completed.

- [ ] **Step 6: Final commit**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/playback/PlaybackQualityPreference.kt app/src/main/java/com/kraat/lostfilmnewtv/playback/PlaybackPreferencesStore.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsPlaybackPreferenceResolver.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppDestination.kt app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsRoute.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt app/src/test/java/com/kraat/lostfilmnewtv/playback/PlaybackPreferencesStoreTest.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsPlaybackPreferenceResolverTest.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreenTest.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt
git commit -m "feat: add default playback quality setting"
```
