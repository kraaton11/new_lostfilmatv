# LostFilm Android TV App Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Kotlin Android TV app that parses `https://www.lostfilm.today/new/`, shows a horizontal poster rail with Russian-only metadata, caches data in Room with a 6-hour freshness window and 7-day retention, and automates GitHub PR-to-release flow with minimal manual work.

**Architecture:** A Compose for TV app with `Home` and `Details` screens backed by a repository that uses OkHttp + Jsoup for parsing and Room for TTL-aware caching. GitHub Actions create and update pull requests from working branches, gate merges with required checks, auto-merge green PRs into `main`, and publish signed APK releases after merge.

**Tech Stack:** Kotlin, Gradle Kotlin DSL, Compose for TV, Navigation Compose, Coroutines/Flow, ViewModel, OkHttp, Jsoup, Room, Coil, JUnit 5 or JUnit 4, Turbine, MockWebServer, GitHub Actions

---

**Spec Reference:** `docs/superpowers/specs/2026-03-15-lostfilm-android-tv-design.md`

**Assumption:** Use package/application id `com.kraat.lostfilmnewtv`. If the user wants a different package later, change it consistently before starting Task 1.

## Planned File Structure

- Root: `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`
- App module: `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`
- App entry: `app/src/main/java/com/kraat/lostfilmnewtv/MainActivity.kt`, `app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt`
- Navigation: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppDestination.kt`, `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
- Design system: `app/src/main/java/com/kraat/lostfilmnewtv/ui/theme/Color.kt`, `app/src/main/java/com/kraat/lostfilmnewtv/ui/theme/Theme.kt`, `app/src/main/java/com/kraat/lostfilmnewtv/ui/components/PosterCard.kt`
- Models: `app/src/main/java/com/kraat/lostfilmnewtv/data/model/ReleaseKind.kt`, `ReleaseSummary.kt`, `ReleaseDetails.kt`, `PageState.kt`
- Network: `app/src/main/java/com/kraat/lostfilmnewtv/data/network/LostFilmHttpClient.kt`
- Parser: `app/src/main/java/com/kraat/lostfilmnewtv/data/parser/LostFilmListParser.kt`, `LostFilmDetailsParser.kt`, `ParserMappers.kt`
- Database: `app/src/main/java/com/kraat/lostfilmnewtv/data/db/LostFilmDatabase.kt`, `ReleaseSummaryEntity.kt`, `ReleaseDetailsEntity.kt`, `PageCacheMetadataEntity.kt`, `ReleaseDao.kt`
- Repository: `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepository.kt`, `LostFilmRepositoryImpl.kt`
- Home UI: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModel.kt`, `HomeUiState.kt`, `HomeScreen.kt`, `HomeRail.kt`, `BottomInfoPanel.kt`
- Details UI: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsViewModel.kt`, `DetailsUiState.kt`, `DetailsScreen.kt`
- Test fixtures: `app/src/test/resources/fixtures/new-page-1.html`, `new-page-2.html`, `series-details.html`, `movie-details.html`
- Unit tests: `app/src/test/java/com/kraat/lostfilmnewtv/data/parser/LostFilmListParserTest.kt`, `LostFilmDetailsParserTest.kt`, `app/src/test/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryTest.kt`, `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModelTest.kt`, `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsViewModelTest.kt`
- UI tests: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/HomeScreenTest.kt`, `DetailsScreenTest.kt`
- GitHub automation: `.github/workflows/open-pr.yml`, `.github/workflows/pull-request-checks.yml`, `.github/workflows/release.yml`
- GitHub docs: `docs/github-setup.md`

## Chunk 1: Foundation

### Task 1: Initialize the repository and Gradle foundation

**Files:**
- Create: `.gitignore`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradlew`
- Create: `gradlew.bat`

- [ ] **Step 1: Verify the workspace is not yet a runnable Gradle project**

Run:

```powershell
Test-Path .\gradlew.bat
```

Expected: `False`

- [ ] **Step 2: Write the root ignore/build files**

Write the root files with:

- Android/Gradle ignores in `.gitignore`
- plugin management and module include in `settings.gradle.kts`
- root plugin aliases in `build.gradle.kts`
- AndroidX and JVM flags in `gradle.properties`
- version catalog entries in `gradle/libs.versions.toml`

Version catalog should include at least:

```toml
[versions]
agp = "8.9.0"
kotlin = "2.1.20"
composeBom = "2026.03.00"
tvMaterial = "1.1.0"
room = "2.7.0"
okhttp = "5.1.0"
jsoup = "1.19.1"
coil = "2.8.0"
```

- [ ] **Step 3: Generate the Gradle wrapper**

Run:

```powershell
git init
gradle wrapper --gradle-version 8.13
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Verify the wrapper works**

Run:

```powershell
.\gradlew.bat help
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the bootstrap foundation**

Run:

```powershell
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle/libs.versions.toml gradle/wrapper gradlew gradlew.bat
git commit -m "chore: bootstrap gradle foundation"
```

### Task 2: Create the Android TV app shell

**Files:**
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/MainActivity.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/theme/Color.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/theme/Theme.kt`
- Test: `app/src/test/java/com/kraat/lostfilmnewtv/AppShellSmokeTest.kt`

- [ ] **Step 1: Write a failing app-shell smoke test**

Create:

```kotlin
class AppShellSmokeTest {
    @Test
    fun applicationId_matchesPlan() {
        assertEquals("com.kraat.lostfilmnewtv", BuildConfig.APPLICATION_ID)
    }
}
```

- [ ] **Step 2: Run the test to confirm the app module does not exist yet**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.AppShellSmokeTest"
```

Expected: FAIL with missing `app` module or missing `BuildConfig`

- [ ] **Step 3: Create the app module and Android TV manifest**

Implement:

- `applicationId = "com.kraat.lostfilmnewtv"`
- `minSdk = 26`
- `targetSdk = 35`
- Compose enabled
- TV launcher intent filter in `AndroidManifest.xml`
- `MainActivity` that sets a placeholder `LostFilmApp()` composable
- theme files needed for Android TV startup

`AndroidManifest.xml` should include:

```xml
<category android:name="android.intent.category.LEANBACK_LAUNCHER" />
```

- [ ] **Step 4: Re-run the smoke test and assemble debug**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.AppShellSmokeTest"
.\gradlew.bat :app:assembleDebug
```

Expected: both commands `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the Android shell**

Run:

```powershell
git add app settings.gradle.kts build.gradle.kts gradle.properties gradle/libs.versions.toml
git commit -m "feat: add android tv app shell"
```

### Task 3: Add app navigation and a verified empty home route

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppDestination.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt`
- Test: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/HomeScreenTest.kt`

- [ ] **Step 1: Write a failing UI test for the home title**

Create:

```kotlin
@Test
fun homeScreen_showsTitle() {
    composeRule.setContent { LostFilmApp() }
    composeRule.onNodeWithText("Новые релизы").assertExists()
}
```

- [ ] **Step 2: Run the UI test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.HomeScreenTest
```

Expected: FAIL because the title is not rendered yet

- [ ] **Step 3: Implement the app root and nav graph**

Implement:

- `Home` destination as start destination
- `Details/{detailsUrl}` destination placeholder
- `HomeScreen` with only the title text and empty-state placeholder

Use a simple root composable:

```kotlin
@Composable
fun LostFilmApp() {
    AppNavGraph()
}
```

- [ ] **Step 4: Re-run the UI test**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.HomeScreenTest
```

Expected: PASS

- [ ] **Step 5: Commit the navigation shell**

Run:

```powershell
git add app/src/main app/src/androidTest
git commit -m "feat: add app navigation shell"
```

## Chunk 2: Data Pipeline

### Task 4: Add HTML fixtures and list-parser tests

**Files:**
- Create: `app/src/test/resources/fixtures/new-page-1.html`
- Create: `app/src/test/resources/fixtures/new-page-2.html`
- Create: `app/src/test/resources/fixtures/series-details.html`
- Create: `app/src/test/resources/fixtures/movie-details.html`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/data/parser/LostFilmListParserTest.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/data/parser/FixtureLoader.kt`

- [ ] **Step 1: Save real HTML fixtures from LostFilm**

Run:

```powershell
curl.exe -L -A "Mozilla/5.0" "https://www.lostfilm.today/new/" -o "app/src/test/resources/fixtures/new-page-1.html"
curl.exe -L -A "Mozilla/5.0" "https://www.lostfilm.today/new/page_2" -o "app/src/test/resources/fixtures/new-page-2.html"
curl.exe -L -A "Mozilla/5.0" "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/" -o "app/src/test/resources/fixtures/series-details.html"
curl.exe -L -A "Mozilla/5.0" "https://www.lostfilm.today/movies/Irreversible" -o "app/src/test/resources/fixtures/movie-details.html"
```

Expected: four HTML files created under `fixtures`

- [ ] **Step 2: Write failing list-parser tests**

Create tests that assert:

```kotlin
@Test
fun parses_series_row_withRussianOnlyFields() {
    val html = fixture("new-page-1.html")
    val results = LostFilmListParser().parse(html, pageNumber = 1)

    val first = results.first()
    assertEquals(ReleaseKind.SERIES, first.kind)
    assertEquals("9-1-1", first.titleRu)
    assertEquals("Маменькин сынок", first.episodeTitleRu)
    assertEquals(9, first.seasonNumber)
    assertEquals(13, first.episodeNumber)
    assertEquals("14.03.2026", first.releaseDateRu)
}
```

Also add a movie test that asserts `seasonNumber` and `episodeNumber` are `null`.

- [ ] **Step 3: Run the list-parser tests and confirm they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.parser.LostFilmListParserTest"
```

Expected: FAIL because parser/model classes do not exist yet

- [ ] **Step 4: Add the parser-facing model classes**

Create:

- `ReleaseKind.kt`
- `ReleaseSummary.kt`
- `PageState.kt`

Keep fields aligned with the spec and Russian-only UI contract.

- [ ] **Step 5: Commit fixtures and failing tests**

Run:

```powershell
git add app/src/test/resources/fixtures app/src/test/java/com/kraat/lostfilmnewtv/data/parser app/src/main/java/com/kraat/lostfilmnewtv/data/model
git commit -m "test: add lostfilm list parser fixtures"
```

### Task 5: Implement the list parser and details parser

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/parser/LostFilmListParser.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/parser/LostFilmDetailsParser.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/model/ReleaseDetails.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/data/parser/LostFilmDetailsParserTest.kt`

- [ ] **Step 1: Write failing details-parser tests**

Create tests that assert:

```kotlin
@Test
fun parses_series_details_withRuDateOnly() {
    val details = LostFilmDetailsParser().parseSeries(fixture("series-details.html"), "/series/9-1-1/season_9/episode_13/")
    assertEquals("9-1-1", details.titleRu)
    assertEquals(9, details.seasonNumber)
    assertEquals(13, details.episodeNumber)
    assertEquals("14 марта 2026", details.releaseDateRu)
}
```

Also add a movie details test that asserts `seasonNumber == null`.

- [ ] **Step 2: Run the parser test suite and verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.parser.*"
```

Expected: FAIL because parser implementations are still missing

- [ ] **Step 3: Implement the Jsoup parsers**

Implementation requirements:

- Parse list rows from `.serials-list .row`
- Use `.name-ru` for Russian title
- Use only `.details-pane .alpha` fields
- Resolve relative URLs against `https://www.lostfilm.today`
- Parse movie rows from overlay text `Фильм`
- Parse details pages using `h1.title-ru`, poster, and `Ru` release date

Example helper:

```kotlin
private fun Element.absoluteAttr(name: String): String =
    URI(BASE_URL).resolve(attr(name)).toString()
```

- [ ] **Step 4: Re-run the parser tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.parser.*"
```

Expected: PASS

- [ ] **Step 5: Commit the parser implementation**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/data/parser app/src/main/java/com/kraat/lostfilmnewtv/data/model app/src/test/java/com/kraat/lostfilmnewtv/data/parser
git commit -m "feat: implement lostfilm html parsers"
```

### Task 6: Add Room schema and repository TTL tests

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/db/ReleaseSummaryEntity.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/db/ReleaseDetailsEntity.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/db/PageCacheMetadataEntity.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/db/ReleaseDao.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/db/LostFilmDatabase.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryTest.kt`

- [ ] **Step 1: Write failing repository tests for freshness and retention**

Create tests that assert:

```kotlin
@Test
fun staleCacheWithinRetention_isReturnedWithStaleFlag() = runTest {
    // seed page 1 fetched 8 hours ago
    // network throws IOException
    // repository returns cached items and marks them stale
}

@Test
fun expiredCacheOlderThanSevenDays_isDeletedAndNotReturned() = runTest {
    // seed page 1 fetched 8 days ago
    // repository returns error and removes expired rows
}
```

- [ ] **Step 2: Run the repository tests and confirm they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.repository.LostFilmRepositoryTest"
```

Expected: FAIL because Room and repository code do not exist yet

- [ ] **Step 3: Implement the Room entities and DAO**

Schema requirements:

- `release_summaries` keyed by `detailsUrl`
- `release_details` keyed by `detailsUrl`
- `page_cache_metadata` keyed by `pageNumber`
- queries for page fetch, stale cleanup, and page replacement

Add indices for:

- `pageNumber`
- `fetchedAt`

- [ ] **Step 4: Re-run repository tests to ensure they still fail only on missing repository implementation**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.repository.LostFilmRepositoryTest"
```

Expected: FAIL on missing repository behavior, not schema compilation

- [ ] **Step 5: Commit the Room foundation**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/data/db app/src/test/java/com/kraat/lostfilmnewtv/data/repository
git commit -m "feat: add room cache schema"
```

### Task 7: Implement network and repository logic

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/network/LostFilmHttpClient.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepository.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryImpl.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/parser/ParserMappers.kt`

- [ ] **Step 1: Add one more failing test for page append behavior**

Extend `LostFilmRepositoryTest.kt` with:

```kotlin
@Test
fun loadsNextPage_andAppendsWithoutLosingExistingItems() = runTest {
    // seed successful responses for page 1 and page 2
    // assert page 2 items are appended in order
}
```

- [ ] **Step 2: Run repository tests and verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.repository.LostFilmRepositoryTest"
```

Expected: FAIL

- [ ] **Step 3: Implement the repository**

Implementation requirements:

- Network-first load for page 1
- Fallback to retained cache within 7 days
- Freshness flag for data older than 6 hours
- Append next page without duplicates
- Cleanup expired rows on startup, refresh, and details access
- Details lookup by `detailsUrl`

Use interfaces like:

```kotlin
interface LostFilmRepository {
    suspend fun loadPage(pageNumber: Int): PageState
    suspend fun loadDetails(detailsUrl: String): DetailsResult
}
```

- [ ] **Step 4: Run parser and repository tests together**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.parser.*" --tests "com.kraat.lostfilmnewtv.data.repository.*"
```

Expected: PASS

- [ ] **Step 5: Commit the repository layer**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/data/network app/src/main/java/com/kraat/lostfilmnewtv/data/repository app/src/main/java/com/kraat/lostfilmnewtv/data/parser
git commit -m "feat: add repository and ttl cache logic"
```

## Chunk 3: TV UI

### Task 8: Implement the HomeViewModel with paging state

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeUiState.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModel.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModelTest.kt`

- [ ] **Step 1: Write failing HomeViewModel tests**

Create tests for:

```kotlin
@Test
fun onStart_loadsFirstPageAutomatically() = runTest { }

@Test
fun onEndReached_loadsNextPageOnce() = runTest { }

@Test
fun staleCache_setsStaleBanner() = runTest { }
```

- [ ] **Step 2: Run the HomeViewModel tests and verify they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.home.HomeViewModelTest"
```

Expected: FAIL

- [ ] **Step 3: Implement the HomeViewModel**

Behavior:

- trigger automatic initial load
- expose list items, selected item, stale banner, loading state, paging state, and full-screen error state
- guard duplicate paging requests
- persist and restore focused item key

- [ ] **Step 4: Re-run the HomeViewModel tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.home.HomeViewModelTest"
```

Expected: PASS

- [ ] **Step 5: Commit the home state layer**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/home app/src/test/java/com/kraat/lostfilmnewtv/ui/home
git commit -m "feat: add home viewmodel paging state"
```

### Task 9: Build the TV home screen rail and bottom info panel

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/components/PosterCard.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeRail.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/BottomInfoPanel.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt`
- Modify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/HomeScreenTest.kt`

- [ ] **Step 1: Extend the UI test with focus-driven assertions**

Add assertions like:

```kotlin
@Test
fun movingFocus_updatesBottomInfoPanel() {
    // seed UI with two releases
    // move DPAD right
    // assert bottom panel shows the newly focused Russian title
}
```

- [ ] **Step 2: Run the home UI tests to confirm failure**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.HomeScreenTest
```

Expected: FAIL because the rail and panel are not implemented

- [ ] **Step 3: Implement the poster rail and info panel**

Implementation requirements:

- horizontal lazy row for posters
- focused card scale/glow effect
- bottom panel shows Russian title, optional Russian episode title, season/episode, and `Ru` date
- movies hide season/episode
- paging loader card at the end
- stale banner above content when needed

Use `Coil` for poster loading and keep the focus effect lightweight.

- [ ] **Step 4: Re-run the home UI tests**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.HomeScreenTest
```

Expected: PASS

- [ ] **Step 5: Commit the home TV UI**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/components app/src/main/java/com/kraat/lostfilmnewtv/ui/home app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/HomeScreenTest.kt
git commit -m "feat: add tv poster rail home screen"
```

### Task 10: Implement details loading, screen UI, and navigation restore

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsUiState.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsViewModel.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsViewModelTest.kt`
- Create: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`

- [ ] **Step 1: Write failing details tests**

Add:

```kotlin
@Test
fun seriesDetails_showSeasonEpisodeAndRuDate() = runTest { }

@Test
fun movieDetails_hideSeasonEpisode() = runTest { }

@Test
fun backFromDetails_restoresFocusedPoster() {
    // navigate from home to details and back
}
```

- [ ] **Step 2: Run details tests and verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsViewModelTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.DetailsScreenTest
```

Expected: FAIL

- [ ] **Step 3: Implement the details screen and restore behavior**

Implementation requirements:

- load cached details first
- refresh from network when needed
- show poster, Russian title, `Ru` date
- show season/episode only for series
- retain home focus key and list position across back navigation

- [ ] **Step 4: Re-run the details tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsViewModelTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.DetailsScreenTest
```

Expected: PASS

- [ ] **Step 5: Commit the details flow**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/details app/src/main/java/com/kraat/lostfilmnewtv/navigation app/src/test/java/com/kraat/lostfilmnewtv/ui/details app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt
git commit -m "feat: add details screen and navigation restore"
```

## Chunk 4: GitHub Automation And Release

### Task 11: Add pull-request checks workflow

**Files:**
- Create: `.github/workflows/pull-request-checks.yml`
- Create: `docs/github-setup.md`

- [ ] **Step 1: Write the GitHub workflow first and verify it is absent**

Run:

```powershell
Test-Path .github\workflows\pull-request-checks.yml
```

Expected: `False`

- [ ] **Step 2: Create the PR checks workflow**

The workflow must run on `pull_request` for `main` and execute:

```yaml
- uses: actions/checkout@v4
- uses: actions/setup-java@v4
- uses: gradle/actions/setup-gradle@v4
- run: ./gradlew testDebugUnitTest lint assembleDebug
```

Also add `docs/github-setup.md` with one-time settings for:

- branch protection
- Actions permissions
- required checks

- [ ] **Step 3: Validate the workflow locally**

Run:

```powershell
Get-Content .github/workflows/pull-request-checks.yml
```

Expected: workflow includes `pull_request`, `testDebugUnitTest`, `lint`, and `assembleDebug`

- [ ] **Step 4: Run the local equivalent once**

Run:

```powershell
.\gradlew.bat testDebugUnitTest lint assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the checks workflow**

Run:

```powershell
git add .github/workflows/pull-request-checks.yml docs/github-setup.md
git commit -m "ci: add pull request checks workflow"
```

### Task 12: Automate PR creation and auto-merge

**Files:**
- Create: `.github/workflows/open-pr.yml`
- Modify: `docs/github-setup.md`

- [ ] **Step 1: Write the branch-to-PR workflow**

Create a workflow triggered on `push` to branches except `main`.

Use `gh` commands similar to:

```yaml
- name: Find or create PR
  run: |
    PR_NUMBER=$(gh pr list --head "$BRANCH" --base main --json number --jq '.[0].number')
    if [ -z "$PR_NUMBER" ]; then
      gh pr create --base main --head "$BRANCH" --title "$PR_TITLE" --body "$PR_BODY"
    fi
```

- [ ] **Step 2: Enable auto-merge in the same workflow**

Add a step similar to:

```yaml
- name: Enable auto-merge
  run: gh pr merge "$PR_NUMBER" --auto --squash
```

Grant workflow permissions:

- `contents: write`
- `pull-requests: write`

- [ ] **Step 3: Document the one-time repo settings**

Update `docs/github-setup.md` with:

- enable auto-merge
- allow Actions to create and approve PRs if needed
- set `pull-request-checks` as required

- [ ] **Step 4: Validate workflow shape**

Run:

```powershell
Get-Content .github/workflows/open-pr.yml
```

Expected: contains `gh pr create` and `gh pr merge --auto`

- [ ] **Step 5: Commit the PR automation**

Run:

```powershell
git add .github/workflows/open-pr.yml docs/github-setup.md
git commit -m "ci: automate pull request creation and auto-merge"
```

### Task 13: Add signed release workflow

**Files:**
- Create: `.github/workflows/release.yml`
- Modify: `app/build.gradle.kts`
- Modify: `docs/github-setup.md`

- [ ] **Step 1: Add signing hooks to the app module**

Update `app/build.gradle.kts` so release signing can read:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Use environment lookups guarded so local debug builds still work without secrets.

- [ ] **Step 2: Create the release workflow**

The workflow must:

- run on `push` to `main`
- decode the keystore from secrets
- build `assembleRelease`
- generate version metadata
- create a `GitHub Release`
- upload the signed APK asset

Use steps like:

```yaml
- run: ./gradlew assembleRelease
- uses: softprops/action-gh-release@v2
  with:
    files: app/build/outputs/apk/release/*.apk
```

- [ ] **Step 3: Update one-time setup documentation**

Add the exact secrets list and formatting instructions to `docs/github-setup.md`.

- [ ] **Step 4: Validate local release build still works without secrets in debug mode**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the release automation**

Run:

```powershell
git add .github/workflows/release.yml app/build.gradle.kts docs/github-setup.md
git commit -m "ci: add signed github release workflow"
```

## Chunk 5: Final Verification

### Task 14: Verify the full app and automation surface

**Files:**
- Create: `README.md`

- [ ] **Step 1: Add a minimal project README**

Document:

- what the app does
- how to run debug build
- where the spec and plan live
- where GitHub setup instructions live

- [ ] **Step 2: Run the full local verification suite**

Run:

```powershell
.\gradlew.bat testDebugUnitTest lint assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run the targeted connected UI tests**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Expected: all home/details UI tests PASS

- [ ] **Step 4: Review the git status before handoff**

Run:

```powershell
git status --short
```

Expected: only intended tracked changes are present

- [ ] **Step 5: Commit the verification pass**

Run:

```powershell
git add README.md
git commit -m "docs: add project readme and verification notes"
```

## Execution Notes

- Implement the plan in order.
- Keep each task small and self-contained.
- Do not skip the failing-test step when a task introduces behavior.
- When Android instrumentation is unavailable in the environment, note the gap explicitly and still run all available unit and build checks.
- If GitHub repository settings cannot be changed from the local environment, complete every file-based automation step and leave only the documented one-time setup for the user.
