# HTTP Cache + Favorites Streaming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the anonymous OkHttp disk cache for public pages, and stream favorite releases to the UI as each series loads instead of waiting for the full fan-out.

**Architecture:** Two independent changes to the data layer. Fix 1 adds a second `LostFilmHttpClient` parameter to `LostFilmRepositoryImpl` (anonymous, cache-backed) and routes public-page fetches through it. Fix 2 converts `loadFavoriteReleases` from a `suspend fun` to a `Flow`-emitting function that emits partial results mid-fan-out via `channelFlow`.

**Tech Stack:** Kotlin coroutines (`channelFlow`, `Flow`), Hilt DI (`@AnonymousHttpClient` qualifier already exists in `NetworkModule.kt`), OkHttp disk cache (already configured in `NetworkModule.provideAnonymousLostFilmHttpClient`).

---

## Files Map

| File | Change |
|------|--------|
| `app/src/main/java/com/kraat/lostfilmnewtv/data/model/FavoriteModels.kt` | Add `Partial` variant to `FavoriteReleasesResult` |
| `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepository.kt` | Replace `suspend fun loadFavoriteReleases` with `fun observeFavoriteReleases`; keep `suspend fun loadFavoriteReleases` default impl as bridge for test fakes |
| `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryImpl.kt` | Add `anonymousHttpClient` constructor param; swap clients for public methods; convert `loadFavoriteReleases` to `observeFavoriteReleases` using `channelFlow` |
| `app/src/main/java/com/kraat/lostfilmnewtv/di/DataModule.kt` | Inject `@AnonymousHttpClient` into `provideLostFilmRepository` |
| `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModel.kt` | Replace single `repository.loadFavoriteReleases(pageNumber)` call with `repository.observeFavoriteReleases(pageNumber).collect { }`, handle `Partial` |
| `app/src/androidTest/java/com/kraat/lostfilmnewtv/di/TestDataModule.kt` | Replace `loadFavoriteReleases` override with `observeFavoriteReleases` in `TestFakeRepository` |
| `app/src/test/java/com/kraat/lostfilmnewtv/di/UnitTestModules.kt` | Replace `loadFavoriteReleases` override with `observeFavoriteReleases` in `UnitTestFakeRepository` |

---

## Task 1: Add `Partial` to `FavoriteReleasesResult`

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/model/FavoriteModels.kt`

- [ ] **Step 1: Add the `Partial` variant**

Open `FavoriteModels.kt`. The `FavoriteReleasesResult` sealed interface currently looks like:

```kotlin
sealed interface FavoriteReleasesResult {
    data class Success(
        val items: List<ReleaseSummary>,
        val pageNumber: Int = 1,
        val hasNextPage: Boolean = false,
        val favoriteSeriesCount: Int? = null,
    ) : FavoriteReleasesResult

    data class Unavailable(
        val message: String? = null,
    ) : FavoriteReleasesResult
}
```

Add `Partial` between `Success` and `Unavailable`:

```kotlin
sealed interface FavoriteReleasesResult {
    data class Success(
        val items: List<ReleaseSummary>,
        val pageNumber: Int = 1,
        val hasNextPage: Boolean = false,
        val favoriteSeriesCount: Int? = null,
    ) : FavoriteReleasesResult

    /**
     * Emitted during the fan-out phase (page 1 only) as each favorite series finishes loading.
     * Items are unenriched (no TMDB posters yet). Final enriched results arrive with [Success].
     */
    data class Partial(
        val items: List<ReleaseSummary>,
        val favoriteSeriesCount: Int,
    ) : FavoriteReleasesResult

    data class Unavailable(
        val message: String? = null,
    ) : FavoriteReleasesResult
}
```

- [ ] **Step 2: Build to verify no compile errors**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL (or only pre-existing errors unrelated to this file).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/data/model/FavoriteModels.kt
git commit -m "feat(model): add FavoriteReleasesResult.Partial for streaming favorites"
```

---

## Task 2: Update `LostFilmRepository` interface

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepository.kt`

- [ ] **Step 1: Replace `loadFavoriteReleases` with `observeFavoriteReleases`**

Add the import for `channelFlow` at the top of the file (next to existing flow imports):

```kotlin
import kotlinx.coroutines.flow.channelFlow
```

Then replace the interface method:

```kotlin
// Remove this:
suspend fun loadFavoriteReleases(pageNumber: Int = 1): FavoriteReleasesResult

// Add this:
fun observeFavoriteReleases(pageNumber: Int = 1): Flow<FavoriteReleasesResult> =
    channelFlow { send(FavoriteReleasesResult.Unavailable()) }
```

The default implementation returns a single `Unavailable` — the real implementation in `LostFilmRepositoryImpl` overrides this. Test fakes that don't override will return Unavailable, which is safe.

- [ ] **Step 2: Build to see which call sites break**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|Unresolved" | head -40
```

Expected: errors in `LostFilmRepositoryImpl.kt`, `HomeViewModel.kt`, `TestDataModule.kt`, `UnitTestModules.kt` — these are fixed in subsequent tasks.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepository.kt
git commit -m "feat(repository): replace loadFavoriteReleases with observeFavoriteReleases Flow"
```

---

## Task 3: Update test fakes

**Files:**
- Modify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/di/TestDataModule.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/di/UnitTestModules.kt`

- [ ] **Step 1: Update `TestFakeRepository` in `TestDataModule.kt`**

In `TestFakeRepository`, replace:

```kotlin
var favoriteReleasesResult: FavoriteReleasesResult = FavoriteReleasesResult.Unavailable()
// ...
override suspend fun loadFavoriteReleases(pageNumber: Int): FavoriteReleasesResult = favoriteReleasesResult
```

With:

```kotlin
var favoriteReleasesResult: FavoriteReleasesResult = FavoriteReleasesResult.Unavailable()
// ...
override fun observeFavoriteReleases(pageNumber: Int): Flow<FavoriteReleasesResult> =
    kotlinx.coroutines.flow.flowOf(favoriteReleasesResult)
```

Add the import at the top of the file:

```kotlin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
```

(`import kotlinx.coroutines.flow.Flow` may already be present — keep it, add `flowOf`.)

- [ ] **Step 2: Update `UnitTestFakeRepository` in `UnitTestModules.kt`**

In `UnitTestFakeRepository`, replace:

```kotlin
var favoriteReleasesResult: FavoriteReleasesResult = FavoriteReleasesResult.Unavailable()
// ...
override suspend fun loadFavoriteReleases(pageNumber: Int) = favoriteReleasesResult
```

With:

```kotlin
var favoriteReleasesResult: FavoriteReleasesResult = FavoriteReleasesResult.Unavailable()
// ...
override fun observeFavoriteReleases(pageNumber: Int): Flow<FavoriteReleasesResult> =
    kotlinx.coroutines.flow.flowOf(favoriteReleasesResult)
```

Add the import at the top of the file (if not already present):

```kotlin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
```

- [ ] **Step 3: Build to verify fakes compile**

```bash
./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin 2>&1 | grep -E "error:" | head -20
```

Expected: errors remain only in `LostFilmRepositoryImpl.kt` and `HomeViewModel.kt`.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/kraat/lostfilmnewtv/di/TestDataModule.kt \
        app/src/test/java/com/kraat/lostfilmnewtv/di/UnitTestModules.kt
git commit -m "test: update fakes to implement observeFavoriteReleases"
```

---

## Task 4: Add `anonymousHttpClient` to `LostFilmRepositoryImpl` + swap public methods (Fix 1)

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryImpl.kt`

- [ ] **Step 1: Add `anonymousHttpClient` constructor parameter**

In the `LostFilmRepositoryImpl` class constructor, add the new parameter with a default so
existing test construction still compiles:

```kotlin
class LostFilmRepositoryImpl(
    private val httpClient: LostFilmHttpClient,
    private val anonymousHttpClient: LostFilmHttpClient = httpClient,  // <-- ADD THIS
    private val releaseDao: ReleaseDao,
    // ... rest unchanged
```

- [ ] **Step 2: Swap `loadPage` / `observeNewReleases` to use `anonymousHttpClient`**

Find (around line 133):
```kotlin
val html = httpClient.fetchNewPage(pageNumber)
```
Replace with:
```kotlin
val html = anonymousHttpClient.fetchNewPage(pageNumber)
```

- [ ] **Step 3: Swap `loadMovies`**

Find (around line 183):
```kotlin
val html = httpClient.fetchMoviesPage(pageNumber)
```
Replace with:
```kotlin
val html = anonymousHttpClient.fetchMoviesPage(pageNumber)
```

- [ ] **Step 4: Swap `loadSeriesCatalog`**

Find (around line 216):
```kotlin
val json = httpClient.fetchSeriesCatalogPage(pageNumber)
```
Replace with:
```kotlin
val json = anonymousHttpClient.fetchSeriesCatalogPage(pageNumber)
```

- [ ] **Step 5: Swap `loadSchedule`**

Find (around line 614):
```kotlin
val html = httpClient.fetchSchedulePage()
```
Replace with:
```kotlin
val html = anonymousHttpClient.fetchSchedulePage()
```

- [ ] **Step 6: Swap `schedulePosterUrlFromLostFilm`**

Find (around line 679):
```kotlin
val fetchedPosterUrl = detailsParser.parsePosterUrl(httpClient.fetchDetails(item.targetUrl))
```
Replace with:
```kotlin
val fetchedPosterUrl = detailsParser.parsePosterUrl(anonymousHttpClient.fetchDetails(item.targetUrl))
```

- [ ] **Step 7: Swap `loadSeriesOverview`**

Find the `loadSeriesOverview` method (around line 540–560). It calls `httpClient.fetchDetails(seriesRootUrl)`. The series overview is a public page (no auth gate). Replace:
```kotlin
val html = httpClient.fetchDetails(seriesRootUrl)
```
With:
```kotlin
val html = anonymousHttpClient.fetchDetails(seriesRootUrl)
```

- [ ] **Step 8: Swap `search`**

Find the `search` method (around line 570–610). It calls `httpClient.fetchDetails("$BASE_URL/search/?q=$encodedQuery")`. Replace:
```kotlin
val html = httpClient.fetchDetails("$BASE_URL/search/?q=$encodedQuery")
```
With:
```kotlin
val html = anonymousHttpClient.fetchDetails("$BASE_URL/search/?q=$encodedQuery")
```

- [ ] **Step 9: Build to verify**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:" | head -20
```

Expected: only remaining errors from `observeFavoriteReleases` not yet implemented.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryImpl.kt
git commit -m "feat(repository): route public page fetches through anonymousHttpClient"
```

---

## Task 5: Wire `@AnonymousHttpClient` in `DataModule`

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/di/DataModule.kt`

- [ ] **Step 1: Inject the anonymous client**

In `DataModule.kt`, update `provideLostFilmRepository`:

```kotlin
@Provides
@Singleton
fun provideLostFilmRepository(
    @AuthenticatedHttpClient httpClient: LostFilmHttpClient,
    @AnonymousHttpClient anonymousHttpClient: LostFilmHttpClient,   // <-- ADD
    releaseDao: ReleaseDao,
    tmdbResolver: TmdbPosterResolver,
    sessionStore: EncryptedSessionStore,
): LostFilmRepository = LostFilmRepositoryImpl(
    httpClient = httpClient,
    anonymousHttpClient = anonymousHttpClient,                       // <-- ADD
    releaseDao = releaseDao,
    listParser = LostFilmListParser(),
    detailsParser = LostFilmDetailsParser(),
    favoriteSeriesParser = LostFilmFavoriteSeriesParser(),
    seasonEpisodesParser = LostFilmSeasonEpisodesParser(),
    searchParser = LostFilmSearchParser(),
    scheduleParser = LostFilmScheduleParser(),
    tmdbResolver = tmdbResolver,
    hasAuthenticatedSession = {
        val session = sessionStore.read()
        session != null && !sessionStore.isExpired()
    },
)
```

The `@AnonymousHttpClient` qualifier is already defined in `NetworkModule.kt` and wired to `provideAnonymousLostFilmHttpClient` — no change needed there.

- [ ] **Step 2: Build to verify**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:" | head -20
```

Expected: only remaining error from `observeFavoriteReleases` not yet implemented in `LostFilmRepositoryImpl`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/di/DataModule.kt
git commit -m "feat(di): wire AnonymousHttpClient into LostFilmRepository"
```

---

## Task 6: Implement `observeFavoriteReleases` with `channelFlow` streaming (Fix 2)

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryImpl.kt`

- [ ] **Step 1: Add missing imports**

At the top of `LostFilmRepositoryImpl.kt`, add:

```kotlin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicReference
```

(`Flow` and `flow` are likely already imported — add `channelFlow` and `AtomicReference`.)

- [ ] **Step 2: Replace `loadFavoriteReleases` with `observeFavoriteReleases`**

Find the existing `loadFavoriteReleases` method (lines 844–893). Replace it entirely with:

```kotlin
override fun observeFavoriteReleases(pageNumber: Int): Flow<FavoriteReleasesResult> = channelFlow {
    if (!hasAuthenticatedSession()) {
        send(FavoriteReleasesResult.Unavailable("Войдите в LostFilm"))
        return@channelFlow
    }

    val normalizedPageNumber = pageNumber.coerceAtLeast(1)

    try {
        // Pages > 1 read from in-memory cache — no streaming needed, emit Success directly.
        val cached = favoriteReleasesCacheMutex.withLock { favoriteReleasesCache }
        val canReuseCache = normalizedPageNumber > 1 &&
            cached != null &&
            (clock() - cached.fetchedAt) < FAVORITE_RELEASES_CACHE_TTL_MS

        val (allItems, favoriteSeriesCount) = if (canReuseCache) {
            cached!!.allItems to cached.favoriteSeriesCount
        } else {
            // Page 1: run fan-out with streaming partial results.
            val fetched = fetchAllFavoriteReleasesStreaming(
                onPartial = { partialItems, total ->
                    // Emit unenriched partial results so the UI can show items immediately.
                    send(FavoriteReleasesResult.Partial(items = partialItems, favoriteSeriesCount = total))
                },
            ) ?: run {
                send(FavoriteReleasesResult.Unavailable())
                return@channelFlow
            }
            favoriteReleasesCacheMutex.withLock {
                favoriteReleasesCache = FavoriteReleasesCache(
                    allItems = fetched.allItems,
                    favoriteSeriesCount = fetched.favoriteSeriesCount,
                    fetchedAt = clock(),
                )
            }
            fetched.allItems to fetched.favoriteSeriesCount
        }

        val pageOffset = (normalizedPageNumber - 1) * FAVORITE_RELEASES_PAGE_SIZE
        val items = allItems
            .drop(pageOffset)
            .take(FAVORITE_RELEASES_PAGE_SIZE)
            .mapIndexed { index, item -> item.copy(positionInPage = pageOffset + index) }

        val enrichedItems = enrichSummaries(items)
            .mapIndexed { index, item -> item.copy(positionInPage = pageOffset + index) }

        send(FavoriteReleasesResult.Success(
            items = enrichedItems,
            pageNumber = normalizedPageNumber,
            hasNextPage = allItems.size > pageOffset + FAVORITE_RELEASES_PAGE_SIZE,
            favoriteSeriesCount = favoriteSeriesCount,
        ))
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: IOException) {
        send(FavoriteReleasesResult.Unavailable())
    }
}
```

- [ ] **Step 3: Add `fetchAllFavoriteReleasesStreaming`**

After the existing `fetchAllFavoriteReleases` method (around line 907), add the new streaming variant. This method is structurally identical to `fetchAllFavoriteReleases` but calls `onPartial` after each series finishes:

```kotlin
/**
 * Streaming variant of [fetchAllFavoriteReleases]. After each favorite series finishes loading,
 * calls [onPartial] with the accumulated items so far so callers can emit intermediate results.
 * Returns null only when nothing could be loaded at all (every request failed).
 */
private suspend fun fetchAllFavoriteReleasesStreaming(
    onPartial: suspend (items: List<ReleaseSummary>, favoriteSeriesCount: Int) -> Unit,
): AllFavoriteReleasesFetch? {
    val favoritesHtml = httpClient.fetchAccountPage(favoriteSeriesRoute)
    val favoriteSeries = favoriteSeriesParser.parse(favoritesHtml)
    if (favoriteSeries.isEmpty()) {
        return AllFavoriteReleasesFetch(allItems = emptyList(), favoriteSeriesCount = 0)
    }

    val fetchedAt = clock()
    val today = currentFavoriteReleaseDate()
    val totalSeriesCount = favoriteSeries.size

    val favoriteSeriesSemaphore = Semaphore(FAVORITE_SERIES_LOAD_CONCURRENCY)
    data class SeriesLoadResult(
        val items: List<ReleaseSummary>,
        val loaded: Boolean,
    )

    // Shared accumulator — updated from concurrent coroutines, so use a mutex-protected list.
    val accumulatedMutex = Mutex()
    var accumulatedItems: List<ReleaseSummary> = emptyList()

    val seriesResults = coroutineScope {
        favoriteSeries.map { series ->
            async {
                favoriteSeriesSemaphore.withPermit {
                    val seasonsUrl = "${series.seriesUrl.trimEnd('/')}/seasons"
                    val seasonsHtml = try {
                        httpClient.fetchDetails(seasonsUrl)
                    } catch (_: IOException) {
                        return@withPermit SeriesLoadResult(emptyList(), loaded = false)
                    }
                    val watchedEpisodeIdsFromMarks = seasonEpisodesParser.parseSerialId(seasonsHtml)
                        ?.let { serialId ->
                            try {
                                seasonEpisodesParser.parseWatchedEpisodeIds(
                                    httpClient.fetchSeasonWatchedEpisodeMarks(
                                        refererUrl = seasonsUrl,
                                        serialId = serialId,
                                    ),
                                )
                            } catch (_: IOException) {
                                emptySet()
                            }
                        }
                        .orEmpty()
                    val watchedEpisodeIdsFromSeriesRoot = if (watchedEpisodeIdsFromMarks.isEmpty()) {
                        try {
                            seasonEpisodesParser.parseWatchedEpisodeIdsFromPage(
                                httpClient.fetchDetails(series.seriesUrl),
                            )
                        } catch (_: IOException) {
                            emptySet()
                        }
                    } else {
                        emptySet()
                    }
                    val watchedEpisodeIds = watchedEpisodeIdsFromSeriesRoot + watchedEpisodeIdsFromMarks
                    val seriesItems = withContext(Dispatchers.Default) {
                        seasonEpisodesParser.parse(
                            html = seasonsHtml,
                            series = series,
                            fetchedAt = fetchedAt,
                            watchedEpisodeIds = watchedEpisodeIds,
                            maxEpisodesPerSeason = FAVORITE_RELEASES_MAX_EPISODES_PER_SEASON,
                            maxSeasons = FAVORITE_RELEASES_MAX_SEASONS_PER_SERIES,
                        )
                    }

                    // Accumulate and emit partial result after this series finishes.
                    if (seriesItems.isNotEmpty()) {
                        val snapshot = accumulatedMutex.withLock {
                            accumulatedItems = (accumulatedItems + seriesItems).distinctBy { it.detailsUrl }
                            accumulatedItems
                        }
                        onPartial(snapshot, totalSeriesCount)
                    }

                    SeriesLoadResult(items = seriesItems, loaded = true)
                }
            }
        }.awaitAll()
    }

    val loadedAnySeasonPage = seriesResults.any { it.loaded }
    val rawItems = seriesResults
        .flatMap { it.items }
        .distinctBy { it.detailsUrl }

    val publishCheckSemaphore = Semaphore(FAVORITE_PUBLISH_CHECK_CONCURRENCY)
    val allItems = coroutineScope {
        rawItems.map { item ->
            async {
                publishCheckSemaphore.withPermit {
                    val releaseDate = parseFavoriteReleaseDate(item.releaseDateRu) ?: return@withPermit null
                    when {
                        releaseDate.isBefore(today) -> item
                        releaseDate.isAfter(today) -> null
                        isFavoriteReleasePublishedToday(item.detailsUrl) -> item
                        else -> null
                    }
                }
            }
        }.awaitAll()
    }
        .filterNotNull()
        .sortedByDescending { parseFavoriteReleaseDate(it.releaseDateRu) ?: LocalDate.MIN }
        .mapIndexed { index, item -> item.copy(positionInPage = index) }

    if (allItems.isEmpty() && !loadedAnySeasonPage) {
        return null
    }

    return AllFavoriteReleasesFetch(allItems = allItems, favoriteSeriesCount = favoriteSeries.size)
}
```

- [ ] **Step 4: Build to verify only HomeViewModel error remains**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:" | head -20
```

Expected: one error about `loadFavoriteReleases` call site in `HomeViewModel.kt`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryImpl.kt
git commit -m "feat(repository): implement observeFavoriteReleases with channelFlow streaming"
```

---

## Task 7: Update `HomeViewModel` to collect `observeFavoriteReleases`

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModel.kt`

- [ ] **Step 1: Replace the `loadFavoriteReleases` call with `collect`**

Find the `loadFavoriteReleases` private function in `HomeViewModel.kt`. The core of the function is:

```kotlin
favoriteLoadJob = viewModelScope.launch(ioDispatcher) {
    val result = repository.loadFavoriteReleases(pageNumber)
    if (favoriteRequestToken != requestToken) return@launch
    _uiState.update { state ->
        when (result) {
            is FavoriteReleasesResult.Success -> { ... }
            is FavoriteReleasesResult.Unavailable -> { ... }
        }
    }
}
```

Replace the job body with a `collect` call:

```kotlin
favoriteLoadJob = viewModelScope.launch(ioDispatcher) {
    try {
        repository.observeFavoriteReleases(pageNumber).collect { result ->
            if (favoriteRequestToken != requestToken) return@collect
            _uiState.update { state ->
                when (result) {
                    is FavoriteReleasesResult.Partial -> {
                        val updatedItems = if (isPagingRequest) {
                            (state.favoriteItems + result.items).distinctBy { it.detailsUrl }
                        } else {
                            result.items
                        }
                        val favoriteState = if (updatedItems.isEmpty()) {
                            state.favoritesModeState // keep Loading while streaming
                        } else {
                            HomeModeContentState.Content(updatedItems)
                        }
                        state.copy(
                            favoriteItems = updatedItems,
                            favoritesModeState = favoriteState,
                            isFavoritesPaging = false,
                        ).resolveSelection()
                    }
                    is FavoriteReleasesResult.Success -> {
                        val updatedItems = if (isPagingRequest) {
                            (state.favoriteItems + result.items).distinctBy { it.detailsUrl }
                        } else {
                            result.items
                        }
                        val favoriteState = if (updatedItems.isEmpty()) {
                            HomeModeContentState.Empty
                        } else {
                            HomeModeContentState.Content(updatedItems)
                        }
                        state.copy(
                            favoriteItems = updatedItems,
                            favoritesModeState = favoriteState,
                            favoriteSeriesCount = result.favoriteSeriesCount,
                            isFavoritesPaging = false,
                            favoritesPagingErrorMessage = null,
                            favoritesNextPage = result.pageNumber + 1,
                            favoritesHasNextPage = result.hasNextPage,
                        ).resolveSelection()
                    }
                    is FavoriteReleasesResult.Unavailable -> {
                        if (retainVisibleItemsOnFailure) {
                            return@collect _uiState.update { s ->
                                s.copy(isFavoritesPaging = false).resolveSelection()
                            }
                        }
                        if (isPagingRequest && state.favoriteItems.isNotEmpty()) {
                            return@collect _uiState.update { s ->
                                s.copy(
                                    isFavoritesPaging = false,
                                    favoritesPagingErrorMessage = result.message ?: "Не удалось загрузить избранное",
                                ).resolveSelection()
                            }
                        }
                        val favoriteState = when {
                            result.message.isNullOrBlank() -> HomeModeContentState.Error("Не удалось загрузить избранное")
                            result.message.contains("Войдите", ignoreCase = true) -> HomeModeContentState.LoginRequired(result.message)
                            else -> HomeModeContentState.Error(result.message)
                        }
                        state.copy(
                            favoriteItems = emptyList(),
                            favoritesModeState = favoriteState,
                            favoriteSeriesCount = null,
                            isFavoritesPaging = false,
                            favoritesPagingErrorMessage = null,
                        ).resolveSelection()
                    }
                }
            }
        }
    } catch (exception: CancellationException) {
        throw exception
    }
}
```

Also add the import at the top of the file if not already present:

```kotlin
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
```

(This import should already exist — verify it's there.)

- [ ] **Step 2: Full build**

```bash
./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin 2>&1 | grep -E "error:" | head -20
```

Expected: no errors.

- [ ] **Step 3: Run unit tests**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModel.kt
git commit -m "feat(ui): collect observeFavoriteReleases Flow, handle Partial for streaming UX"
```

---

## Task 8: Full CI verification

- [ ] **Step 1: Run all checks**

```bash
./gradlew testDebugUnitTest lint assembleDebug 2>&1 | tail -40
```

Expected: BUILD SUCCESSFUL. If lint flags issues in modified files, fix them before proceeding.

- [ ] **Step 2: If lint fails, fix and re-run**

Common lint issues after this change:
- Unused import warnings → remove stale imports.
- `when` expression not exhaustive → add `else -> {}` or missing branch.

- [ ] **Step 3: Final commit if any lint fixes were needed**

```bash
git add -A
git commit -m "fix(lint): address lint warnings after favorites streaming"
```

---

## Self-Review Notes

- `fetchAllFavoriteReleases` (original, non-streaming) is kept in place — it is still referenced
  by the now-removed `loadFavoriteReleases`. Since that suspend fun is gone, `fetchAllFavoriteReleases`
  becomes dead code. **Remove it** in Task 6 Step 3 or leave it for a follow-up cleanup commit.
  The plan above keeps it to minimize diff scope; delete it if the build confirms no references.
- `onPartial` in `fetchAllFavoriteReleasesStreaming` is a `suspend` lambda to allow `send()` inside
  `channelFlow`. The `accumulatedMutex` is needed because multiple `async` coroutines inside
  `coroutineScope` run concurrently and all update `accumulatedItems`.
- `return@collect` inside `_uiState.update { }` doesn't compile — the `Unavailable` branch uses
  a separate `_uiState.update` call and early return. Verify the lambda nesting is correct when
  implementing Task 7.
