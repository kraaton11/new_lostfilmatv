# Series Guide Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `Гид по сериям` action to series details, load the LostFilm `/seasons` guide on a dedicated TV screen, and navigate from any guide row into the existing episode details flow.

**Architecture:** Keep guide loading isolated from the current details screen. Extend the existing `/seasons` parser and repository with a dedicated guide contract, then add a new Navigation Compose destination plus a focused `ui/guide` package that follows the same `SavedStateHandle` + `collectAsStateWithLifecycle` route pattern already used elsewhere in the app. Based on AndroidX guidance surfaced via Context7, keep the route argument as an encoded `String`, define the destination with `composable(..., arguments = listOf(navArgument(...)))`, and collect `StateFlow` with `collectAsStateWithLifecycle()` from the route composable.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, AndroidX Lifecycle Compose, Coroutines, Jsoup, OkHttp, Robolectric, JUnit

---

**Spec Reference:** `docs/superpowers/specs/2026-03-25-series-guide-design.md`

## Planned File Structure

- Data models:
  - Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/model/SeriesGuide.kt`
- Repository contracts and implementation:
  - Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepository.kt`
  - Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryImpl.kt`
- Parser:
  - Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/parser/LostFilmSeasonEpisodesParser.kt`
- Navigation:
  - Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppDestination.kt`
  - Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
- Details-screen entry point:
  - Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsRoute.kt`
  - Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
  - Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt`
- Guide UI:
  - Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/guide/SeriesGuideUiState.kt`
  - Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/guide/SeriesGuideViewModel.kt`
  - Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/guide/SeriesGuideRoute.kt`
  - Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/guide/SeriesGuideScreen.kt`
- Fixtures and tests:
  - Create: `app/src/test/resources/fixtures/series-guide-ted-seasons.html`
  - Modify: `app/src/test/java/com/kraat/lostfilmnewtv/data/parser/LostFilmSeasonEpisodesParserTest.kt`
  - Modify: `app/src/test/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryTest.kt`
  - Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt`
  - Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt`
  - Create: `app/src/test/java/com/kraat/lostfilmnewtv/ui/guide/SeriesGuideRouteTest.kt`
  - Modify: `app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt`
- Repository test doubles that must compile after the interface change:
  - Modify: `app/src/test/java/com/kraat/lostfilmnewtv/MainActivityTest.kt`
  - Modify: `app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt`
  - Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModelTest.kt`
  - Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt`
  - Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsViewModelTest.kt`

## Chunk 1: Parse And Load Guide Data

### Task 1: Add fixture-backed parser coverage for the LostFilm guide page

**Files:**
- Create: `app/src/test/resources/fixtures/series-guide-ted-seasons.html`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/data/parser/LostFilmSeasonEpisodesParserTest.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/parser/LostFilmSeasonEpisodesParser.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/model/SeriesGuide.kt`

- [ ] **Step 1: Write the failing parser tests**

Add tests that lock down the guide shape from a real `/series/Ted/seasons` fixture:

```kotlin
@Test
fun parseGuide_groupsEpisodesBySeason_andExtractsGuideRows() {
    val guide = parser.parseGuide(
        html = fixture("series-guide-ted-seasons.html"),
        watchedEpisodeIds = emptySet(),
    )

    assertEquals(listOf(2, 1), guide.map { it.seasonNumber })
    assertEquals(8, guide.first().episodes.first().episodeNumber)
    assertEquals("Левые новости", guide.first().episodes.first().episodeTitleRu)
    assertEquals(
        "https://www.lostfilm.today/series/Ted/season_2/episode_8/",
        guide.first().episodes.first().detailsUrl,
    )
}

@Test
fun parseGuide_marksEpisodeWatched_whenEpisodeIdIsPresentInWatchedSet() {
    val guide = parser.parseGuide(
        html = fixture("series-guide-ted-seasons.html"),
        watchedEpisodeIds = setOf("1072001003"),
    )

    assertEquals(true, guide.last().episodes.first { it.episodeNumber == 3 }.isWatched)
}
```

- [ ] **Step 2: Run the parser test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.parser.LostFilmSeasonEpisodesParserTest"
```

Expected:
- FAIL because `parseGuide` and the guide models do not exist yet

- [ ] **Step 3: Write the minimal guide models and parser implementation**

Add a focused model file and a new parser entry point rather than overloading the favorites-only API:

```kotlin
data class SeriesGuideSeason(
    val seasonNumber: Int,
    val episodes: List<SeriesGuideEpisode>,
)

data class SeriesGuideEpisode(
    val detailsUrl: String,
    val episodeId: String?,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitleRu: String?,
    val releaseDateRu: String,
    val isWatched: Boolean,
)

fun parseGuide(
    html: String,
    watchedEpisodeIds: Set<String> = emptySet(),
): List<SeriesGuideSeason> {
    // Parse guide rows, extract episode URL + metadata, then group by season
}
```

Implementation notes:
- reuse the existing `goTo(...)` extraction
- keep ordering exactly as it appears in the page
- skip rows without a valid details URL or release date
- prefer parser-level grouping so repository logic stays thin

- [ ] **Step 4: Run the parser test to verify it passes**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.parser.LostFilmSeasonEpisodesParserTest"
```

Expected:
- PASS

- [ ] **Step 5: Commit the parser slice**

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/data/model/SeriesGuide.kt app/src/main/java/com/kraat/lostfilmnewtv/data/parser/LostFilmSeasonEpisodesParser.kt app/src/test/java/com/kraat/lostfilmnewtv/data/parser/LostFilmSeasonEpisodesParserTest.kt app/src/test/resources/fixtures/series-guide-ted-seasons.html
git commit -m "feat: parse lostfilm series guide rows"
```

### Task 2: Add repository contract, URL normalization, and guide loading

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepository.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryImpl.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/MainActivityTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModelTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsViewModelTest.kt`

- [ ] **Step 1: Write the failing repository tests**

Add tests that verify URL normalization, guide loading, and error handling:

```kotlin
@Test
fun loadSeriesGuide_normalizesEpisodeUrl_toSeriesSeasonsUrl() = runTest {
    val requests = mutableListOf<String>()
    val repository = createRepository(
        pageHandler = { fixture("new-page-1.html") },
        detailsHandler = { requestedUrl ->
            requests += requestedUrl
            when (requestedUrl) {
                "https://www.lostfilm.today/series/Ted/" -> fixture("series-details.html")
                "https://www.lostfilm.today/series/Ted/seasons" -> fixture("series-guide-ted-seasons.html")
                else -> error("Unexpected details request: $requestedUrl")
            }
        },
        watchedEpisodeMarksHandler = { """["1072001003"]""" },
        isAuthenticated = true,
    )

    val result = repository.loadSeriesGuide("https://www.lostfilm.today/series/Ted/season_2/episode_8/")

    assertTrue(result is SeriesGuideResult.Success)
    assertEquals(
        listOf(
            "https://www.lostfilm.today/series/Ted/",
            "https://www.lostfilm.today/series/Ted/seasons",
        ),
        requests,
    )
}
```

Also add one `SeriesGuideResult.Error` case for an `IOException` on `/seasons`.

- [ ] **Step 2: Run the repository test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.repository.LostFilmRepositoryTest"
```

Expected:
- FAIL because `loadSeriesGuide` and `SeriesGuideResult` are not implemented yet

- [ ] **Step 3: Implement the minimal repository contract and guide-loading path**

Keep the contract next to the existing `DetailsResult` pattern:

```kotlin
sealed interface SeriesGuideResult {
    data class Success(val guide: SeriesGuide) : SeriesGuideResult
    data class Error(val message: String) : SeriesGuideResult
}

interface LostFilmRepository {
    suspend fun loadSeriesGuide(detailsUrl: String): SeriesGuideResult
}
```

Implement in `LostFilmRepositoryImpl`:

```kotlin
override suspend fun loadSeriesGuide(detailsUrl: String): SeriesGuideResult {
    val normalizedDetailsUrl = resolveUrl(detailsUrl)
    val seriesRootUrl = seriesRootUrl(normalizedDetailsUrl)
    val seasonsUrl = "${seriesRootUrl.trimEnd('/')}/seasons"

    return try {
        val seriesHtml = httpClient.fetchDetails(seriesRootUrl)
        val seasonsHtml = httpClient.fetchDetails(seasonsUrl)
        val watchedIds = loadGuideWatchedIds(seriesRootUrl, seasonsUrl, seasonsHtml)
        val seasons = seasonEpisodesParser.parseGuide(seasonsHtml, watchedIds)
        val guideDocument = Jsoup.parse(seasonsHtml, BASE_URL)
        SeriesGuideResult.Success(
            guide = SeriesGuide(
                seriesTitleRu = guideDocument.selectFirst("h1")?.text().orEmpty().ifBlank { extractSlugTitle(seriesRootUrl) },
                posterUrl = guideDocument.selectFirst(".main_poster img")?.absUrl("src"),
                selectedEpisodeDetailsUrl = normalizedDetailsUrl,
                seasons = seasons,
            ),
        )
    } catch (exception: IOException) {
        SeriesGuideResult.Error(exception.message ?: "Не удалось загрузить гид по сериям")
    }
}
```

Implementation notes:
- extract and reuse a dedicated `seriesRootUrl(...)` helper instead of duplicating regexes in multiple branches
- fetch the series root page first for title/poster and watched markup already present there
- keep first-version behavior network-only; do not touch Room

- [ ] **Step 4: Update every `LostFilmRepository` test double to satisfy the new interface**

Add a minimal stub everywhere an anonymous repository or fake exists:

```kotlin
override suspend fun loadSeriesGuide(detailsUrl: String): SeriesGuideResult {
    return SeriesGuideResult.Error("not needed")
}
```

Files to touch in this step:
- `app/src/test/java/com/kraat/lostfilmnewtv/MainActivityTest.kt`
- `app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt`
- `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModelTest.kt`
- `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt`
- `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsViewModelTest.kt`

- [ ] **Step 5: Run repository and affected compile-sensitive tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.repository.LostFilmRepositoryTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsViewModelTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsRouteTest"
```

Expected:
- PASS

- [ ] **Step 6: Commit the repository slice**

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepository.kt app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryImpl.kt app/src/test/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryTest.kt app/src/test/java/com/kraat/lostfilmnewtv/MainActivityTest.kt app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModelTest.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsViewModelTest.kt
git commit -m "feat: load lostfilm series guide data"
```

## Chunk 2: Surface The Guide Entry From Details

### Task 3: Add a guide action to the details-stage model and route

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsRoute.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt`

- [ ] **Step 1: Write the failing details-screen tests**

Add one model test and one route test:

```kotlin
@Test
fun buildStageUi_addsGuideAction_forSeriesDetails() {
    val ui = buildDetailsStageUi(
        state = DetailsUiState(details = seriesDetails()),
        isAuthenticated = true,
        availableTorrentRowsCount = 1,
        playbackRow = DetailsTorrentRowUiModel("row-0", "1080p", "https://example.com/1080", true),
        activeTorrServeRowId = null,
        isTorrServeBusy = false,
    )

    assertEquals(
        true,
        ui.secondaryActions.any { it.actionType == DetailsStageActionType.OPEN_SERIES_GUIDE },
    )
}
```

```kotlin
@Test
fun route_callsOnOpenSeriesGuide_whenGuideActionIsClicked() {
    var openedGuideUrl: String? = null

    composeRule.setContent {
        DetailsRoute(
            detailsUrl = detailsUrl,
            repository = RouteFakeDetailsRepository.success(detailsUrl),
            actionHandler = succeedingActionHandler(),
            linkBuilder = TorrServeLinkBuilder(TorrServeConfig()),
            onOpenSeriesGuide = { openedGuideUrl = it },
        )
    }

    composeRule.waitForNodeWithTag("details-series-guide-action")
    composeRule.onNodeWithTag("details-series-guide-action")
        .performSemanticsAction(SemanticsActions.OnClick)

    assertEquals(detailsUrl, openedGuideUrl)
}
```

- [ ] **Step 2: Run the details tests to verify they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsStageModelsTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsRouteTest"
```

Expected:
- FAIL because the guide action type, semantics tag, and callback do not exist yet

- [ ] **Step 3: Implement the minimal details-screen changes**

Update the stage model and screen to support more than one secondary action instead of special-casing favorite as the only option:

```kotlin
enum class DetailsStageActionType {
    OPEN_TORRSERVE,
    TOGGLE_FAVORITE,
    OPEN_SERIES_GUIDE,
    NONE,
}
```

```kotlin
val guideAction = details
    ?.takeIf { it.kind == ReleaseKind.SERIES }
    ?.let {
        DetailsStageActionUiModel(
            actionId = "series-guide",
            rowId = null,
            label = "Гид по сериям",
            subtitle = "Все сезоны и серии",
            actionType = DetailsStageActionType.OPEN_SERIES_GUIDE,
            enabled = true,
        )
    }

secondaryActions = listOfNotNull(favoriteAction, guideAction)
```

In `DetailsScreen.kt`:
- render every `secondaryAction`, not just the favorite button
- route clicks by `action.actionType`
- keep primary focus behavior intact, with primary `down` pointing to the first secondary action
- chain secondary-action `up` and `down` focus requesters so the D-pad path stays deterministic

In `DetailsRoute.kt`:
- add `onOpenSeriesGuide: (String) -> Unit = {}`
- pass `detailsUrl` when the guide action is clicked

- [ ] **Step 4: Run the details tests to verify they pass**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsStageModelsTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsRouteTest"
```

Expected:
- PASS

- [ ] **Step 5: Commit the details entry-point slice**

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsRoute.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModelsTest.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsRouteTest.kt
git commit -m "feat: add series guide action to details screen"
```

## Chunk 3: Build The Guide Screen And Navigation Flow

### Task 4: Create the guide route, view-model, and screen states

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/guide/SeriesGuideUiState.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/guide/SeriesGuideViewModel.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/guide/SeriesGuideRoute.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/guide/SeriesGuideScreen.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/ui/guide/SeriesGuideRouteTest.kt`

- [ ] **Step 1: Write the failing guide-route tests**

Cover the state transitions and click contract:

```kotlin
@Test
fun route_showsGuideContent_andMarksCurrentEpisodeSelected() {
    composeRule.setContent {
        SeriesGuideRoute(
            detailsUrl = currentEpisodeUrl,
            repository = FakeSeriesGuideRepository.success(currentEpisodeUrl),
            onOpenDetails = {},
        )
    }

    composeRule.waitForNodeWithText("Третий лишний")
    composeRule.onNodeWithTag("series-guide-row-$currentEpisodeUrl").assertIsSelected()
}

@Test
fun route_showsRetryState_whenGuideLoadFails() {
    composeRule.setContent {
        SeriesGuideRoute(
            detailsUrl = currentEpisodeUrl,
            repository = FakeSeriesGuideRepository.error("offline"),
            onOpenDetails = {},
        )
    }

    composeRule.waitForNodeWithText("offline")
    composeRule.onNodeWithText("Повторить").assertExists()
}

@Test
fun route_callsOnOpenDetails_whenEpisodeRowIsClicked() {
    var openedUrl: String? = null

    composeRule.setContent {
        SeriesGuideRoute(
            detailsUrl = currentEpisodeUrl,
            repository = FakeSeriesGuideRepository.success(currentEpisodeUrl),
            onOpenDetails = { openedUrl = it },
        )
    }

    composeRule.waitForNodeWithTag("series-guide-row-https://www.lostfilm.today/series/Ted/season_2/episode_7/")
    composeRule.onNodeWithTag("series-guide-row-https://www.lostfilm.today/series/Ted/season_2/episode_7/")
        .performSemanticsAction(SemanticsActions.OnClick)

    assertEquals("https://www.lostfilm.today/series/Ted/season_2/episode_7/", openedUrl)
}
```

- [ ] **Step 2: Run the guide-route test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.guide.SeriesGuideRouteTest"
```

Expected:
- FAIL because the guide route, UI state, and screen do not exist yet

- [ ] **Step 3: Implement the minimal guide route and screen**

Follow the existing details/auth route pattern and the AndroidX/Context7 lifecycle guidance:

```kotlin
data class SeriesGuideUiState(
    val title: String = "",
    val seasons: List<SeriesGuideSeason> = emptyList(),
    val selectedEpisodeDetailsUrl: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
```

```kotlin
@Composable
fun SeriesGuideRoute(
    detailsUrl: String,
    repository: LostFilmRepository,
    onOpenDetails: (String) -> Unit,
) {
    val viewModel: SeriesGuideViewModel = viewModel(
        key = "series-guide:$detailsUrl",
        factory = seriesGuideViewModelFactory(repository, detailsUrl),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(detailsUrl) {
        viewModel.onStart()
    }

    SeriesGuideScreen(
        state = state,
        onRetry = viewModel::onRetry,
        onEpisodeClick = onOpenDetails,
    )
}
```

Screen responsibilities:
- loading panel
- error panel with `Повторить`
- empty panel with `Список серий пока недоступен`
- season sections and episode rows
- `selected = true` semantics on the current episode row
- optional one-shot focus request to the selected row after first composition if it exists

- [ ] **Step 4: Run the guide-route test to verify it passes**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.guide.SeriesGuideRouteTest"
```

Expected:
- PASS

- [ ] **Step 5: Commit the guide-screen slice**

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/guide/SeriesGuideUiState.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/guide/SeriesGuideViewModel.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/guide/SeriesGuideRoute.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/guide/SeriesGuideScreen.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/guide/SeriesGuideRouteTest.kt
git commit -m "feat: add series guide screen"
```

### Task 5: Wire Navigation Compose so the new guide screen is reachable end-to-end

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppDestination.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt`

- [ ] **Step 1: Write the failing navigation integration test**

Add a focused test that proves the whole path:

```kotlin
@Test
fun detailsGuideAction_opensGuide_andGuideRowNavigatesToEpisodeDetails() {
    TestLostFilmApplication.repositoryOverride = FakeAppNavGraphRepository().apply {
        guideResult = SeriesGuideResult.Success(testGuide(selectedEpisodeUrl = TEST_SUMMARY.detailsUrl))
        detailsByUrl["https://www.lostfilm.today/series/Ted/season_2/episode_7/"] =
            DetailsResult.Success(TEST_GUIDE_DETAILS, false)
    }

    composeRule.setContent {
        LostFilmTheme { AppNavGraph(initialDetailsUrl = TEST_SUMMARY.detailsUrl) }
    }

    composeRule.waitForTag("details-series-guide-action")
    composeRule.onNodeWithTag("details-series-guide-action")
        .performSemanticsAction(SemanticsActions.OnClick)

    composeRule.waitForText("Третий лишний")
    composeRule.onNodeWithTag("series-guide-row-https://www.lostfilm.today/series/Ted/season_2/episode_7/")
        .performSemanticsAction(SemanticsActions.OnClick)

    composeRule.waitForText(TEST_GUIDE_DETAILS.titleRu)
}
```

- [ ] **Step 2: Run the navigation test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.navigation.AppNavGraphTorrServeTest"
```

Expected:
- FAIL because `AppDestination.SeriesGuide` and the nav wiring do not exist yet

- [ ] **Step 3: Implement the minimal navigation changes**

In `AppDestination.kt`, add a route with encoded `detailsUrl`, matching the existing string-argument style:

```kotlin
data object SeriesGuide : AppDestination {
    const val detailsUrlArg: String = "detailsUrl"
    override val route: String = "series-guide/{$detailsUrlArg}"

    fun createRoute(detailsUrl: String): String = "series-guide/${Uri.encode(detailsUrl)}"
}
```

In `AppNavGraph.kt`:
- add a `composable(AppDestination.SeriesGuide.route, arguments = listOf(navArgument(...)))`
- decode the route argument with `Uri.decode(...)`
- pass `repository = application.repository`
- call `SeriesGuideRoute(...)`
- navigate to `AppDestination.SeriesGuide.createRoute(detailsUrl)` from `DetailsRoute.onOpenSeriesGuide`
- navigate from guide rows to `AppDestination.Details.createRoute(episodeDetailsUrl)`

This matches the Navigation Compose patterns surfaced in Context7:
- string route
- `composable(route, arguments = ...)`
- decoded argument from `NavBackStackEntry`

- [ ] **Step 4: Run the navigation test to verify it passes**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.navigation.AppNavGraphTorrServeTest"
```

Expected:
- PASS

- [ ] **Step 5: Commit the navigation slice**

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppDestination.kt app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt app/src/test/java/com/kraat/lostfilmnewtv/navigation/AppNavGraphTorrServeTest.kt
git commit -m "feat: wire series guide navigation flow"
```

## Chunk 4: Final Verification

### Task 6: Run targeted verification and a debug build

**Files:**
- Verify: `docs/superpowers/specs/2026-03-25-series-guide-design.md`
- Verify: `docs/superpowers/plans/2026-03-25-series-guide.md`
- Verify: all changed app files above

- [ ] **Step 1: Run parser coverage**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.parser.LostFilmSeasonEpisodesParserTest"
```

Expected:
- PASS

- [ ] **Step 2: Run repository coverage**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.repository.LostFilmRepositoryTest"
```

Expected:
- PASS

- [ ] **Step 3: Run details-screen regression coverage**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsStageModelsTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsRouteTest"
```

Expected:
- PASS

- [ ] **Step 4: Run guide-screen and navigation coverage**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.guide.SeriesGuideRouteTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.navigation.AppNavGraphTorrServeTest"
```

Expected:
- PASS

- [ ] **Step 5: Build the Android app**

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected:
- PASS

- [ ] **Step 6: Do one manual TV flow check**

Checklist:

1. Open a series details screen
2. Verify `Гид по сериям` appears as the second action
3. Open the guide
4. Confirm the current episode row is selected or receives initial focus
5. Open a different episode from the guide
6. Confirm the existing details screen opens for that episode
7. Back out and confirm normal navigation still works

- [ ] **Step 7: Commit the full feature if requested**

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv app/src/test/java/com/kraat/lostfilmnewtv app/src/test/resources/fixtures/series-guide-ted-seasons.html docs/superpowers/specs/2026-03-25-series-guide-design.md docs/superpowers/plans/2026-03-25-series-guide.md
git commit -m "feat: add lostfilm series guide screen"
```
