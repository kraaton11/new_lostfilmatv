# Design: Anonymous HTTP Cache + Favorites Streaming

**Date:** 2026-06-21  
**Scope:** Two independent fixes to the data layer

---

## Fix 1: Anonymous HTTP Cache (unused `@AnonymousHttpClient`)

### Problem

`DataModule.provideLostFilmRepository` always injects `@AuthenticatedHttpClient`. The
`@AnonymousHttpClient` binding (with a 10 MB OkHttp disk cache) is created by Hilt but wired to
nothing, so public pages are fetched fresh on every request.

### Solution

Give `LostFilmRepositoryImpl` two clients: one for public pages, one for authenticated operations.

**`DataModule.kt`** — add `@AnonymousHttpClient anonymousHttpClient: LostFilmHttpClient` parameter
to `provideLostFilmRepository` and pass it through.

**`LostFilmRepositoryImpl` constructor** — add `private val anonymousHttpClient: LostFilmHttpClient`.

**Methods using `anonymousHttpClient`** (pages that carry no account-specific content):
- `loadPage` / `observeNewReleases` → `anonymousHttpClient.fetchNewPage()`
- `loadMovies` → `anonymousHttpClient.fetchMoviesPage()`
- `loadSeriesCatalog` → `anonymousHttpClient.fetchSeriesCatalogPage()`
- `loadSchedule` → `anonymousHttpClient.fetchSchedulePage()`
- `loadSeriesOverview` → `anonymousHttpClient.fetchDetails(seriesRootUrl)` (series overview page is public)
- `search` → `anonymousHttpClient.fetchDetails(searchUrl)` (search results are public)
- `schedulePosterUrlFromLostFilm` → `anonymousHttpClient.fetchDetails(item.targetUrl)`

**Methods keeping `httpClient` (authenticated):**
- `loadDetails`, `loadDetailsPreview`, `refreshDetailsExtras`
- `loadSeriesGuide` (fetches seasons + watched marks — session-dependent)
- `loadFavoriteReleases` / `fetchAllFavoriteReleases` / `loadFavoriteSeries`
- `setEpisodeWatched`, `setFavorite`, `loadWatchedState`
- `fetchFavoriteMetadataPage`, `refreshFavoriteMetadataIfNeeded`

**Side-effect removed:** The anonymous client never calls `sessionStore.markExpired()` because it
has no session. The `lostFilmResponseLooksAnonymous` check inside `executeLostFilm` is driven by the
session store being present — `OkHttpLostFilmHttpClient(sessionStore = null, ...)` already skips
this check. No code change needed in the HTTP client itself.

**Test modules:** `TestNetworkModule` / `TestDataModule` inject fakes — both clients can be the
same fake. Constructor default `anonymousHttpClient = httpClient` added so existing tests that
construct `LostFilmRepositoryImpl` directly compile without changes.

---

## Fix 2: Favorites Streaming via Flow

### Problem

`fetchAllFavoriteReleases` makes O(N×2 + M) HTTP calls (N = favorite series, M = today-check
candidates) and only returns after all of them complete. The first item appears on screen only when
the entire fan-out finishes — typically several seconds for large accounts.

### Solution

Convert `loadFavoriteReleases` from a `suspend fun` to a `Flow`-emitting function that streams
partial results as each series is processed.

#### Model change — `FavoriteReleasesResult`

Add a new sealed variant to `FavoriteModels.kt`:

```kotlin
data class Partial(
    val items: List<ReleaseSummary>,       // accumulated so far (all series processed up to now)
    val favoriteSeriesCount: Int,
) : FavoriteReleasesResult
```

`Success` and `Unavailable` remain unchanged.

#### `LostFilmRepository` interface change

```kotlin
// Remove:
suspend fun loadFavoriteReleases(pageNumber: Int = 1): FavoriteReleasesResult

// Add:
fun observeFavoriteReleases(pageNumber: Int = 1): Flow<FavoriteReleasesResult>
```

The default implementation in the interface returns `flow { emit(FavoriteReleasesResult.Unavailable()) }`.

#### `LostFilmRepositoryImpl` changes

Rename current `loadFavoriteReleases` → `observeFavoriteReleases`, returning `Flow<FavoriteReleasesResult>` via `flow { }`.

**Page > 1 (cache path):** No streaming needed — the full list is already in memory. Emit a single
`Success` directly from the cache slice. Behavior unchanged.

**Page 1 (network path):** Call a new `fetchAllFavoriteReleasesStreaming` that:
1. Fetches the favorites list page (1 HTTP call).
2. If empty → emit `Success(emptyList, 0)` and return.
3. Launches N parallel coroutines bounded by `Semaphore(FAVORITE_SERIES_LOAD_CONCURRENCY)`.
4. Uses `channelFlow { }` so each coroutine can `send()` a partial result as soon as one series
   finishes, without waiting for the others.
5. After each series completes, accumulates its episodes into a shared `AtomicReference<List<ReleaseSummary>>`
   and emits `Partial(accumulated, totalSeriesCount)`.
6. After all series coroutines finish, runs the today-check fan-out (unchanged logic), sorts,
   stores in `favoriteReleasesCache`, then emits final `Success`.

The `fetchAllFavoriteReleases` private method is preserved for backward-compat with the cache
population on page 1 final result. `fetchAllFavoriteReleasesStreaming` is an extraction that adds
the interim `send()` calls.

#### `HomeViewModel` changes

Replace:
```kotlin
val result = repository.loadFavoriteReleases(pageNumber)
```

With:
```kotlin
repository.observeFavoriteReleases(pageNumber).collect { result -> ... }
```

Handle new `FavoriteReleasesResult.Partial` in the `when` block the same as `Success` but without
updating `hasNextPage` or `favoriteSeriesCount` (those are only set on `Success`):

```kotlin
is FavoriteReleasesResult.Partial -> {
    val updatedItems = if (isPagingRequest) {
        (state.favoriteItems + result.items).distinctBy { it.detailsUrl }
    } else {
        result.items
    }
    state.copy(
        favoriteItems = updatedItems,
        favoritesModeState = if (updatedItems.isEmpty()) HomeModeContentState.Loading
                             else HomeModeContentState.Content(updatedItems),
    ).resolveSelection()
}
```

The `favoriteLoadJob` already cancels the coroutine on re-entry — `collect` inside the job is
cancelled correctly.

#### `Partial` emission timing

`Partial` is emitted only for page 1 (network fan-out). Pages > 1 emit `Success` directly. This
keeps paging semantics simple and avoids duplicate UI thrash during scroll.

#### TMDB enrichment of partial results

`enrichSummaries` is called per-page slice in `Success` (unchanged). For `Partial`, TMDB enrichment
is **not** called — items in `Partial` are unenriched. When `Success` arrives for page 1, the full
slice is enriched as before. This keeps the streaming path simple and avoids concurrent TMDB calls
racing with the main enrichment.

---

## What is NOT changed

- `loadFavoriteSeries` — different code path, unrelated.
- Any Room / DB logic.
- The `FavoriteReleasesCache` in-memory TTL logic.
- Error handling semantics — `Unavailable` is still emitted on auth failure or IOException.
- Test fakes that implement `LostFilmRepository` — need to add `observeFavoriteReleases` override.

---

## Files to change

| File | Change |
|------|--------|
| `data/model/FavoriteModels.kt` | Add `Partial` variant |
| `data/repository/LostFilmRepository.kt` | Replace `suspend fun loadFavoriteReleases` with `fun observeFavoriteReleases` |
| `data/repository/LostFilmRepositoryImpl.kt` | Add `anonymousHttpClient` param; swap clients; convert `loadFavoriteReleases` to `observeFavoriteReleases` with `channelFlow` |
| `di/DataModule.kt` | Inject `@AnonymousHttpClient` into `provideLostFilmRepository` |
| `ui/home/HomeViewModel.kt` | Switch `loadFavoriteReleases` to collect `observeFavoriteReleases`, handle `Partial` |
| Test fakes implementing `LostFilmRepository` | Add `observeFavoriteReleases` override |
