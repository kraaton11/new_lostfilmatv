# Favorite Releases Room Cache

**Date:** 2026-06-21
**Status:** Approved

## Problem

`observeFavoriteReleases` performs a network fan-out of 61+ HTTP requests for a user
with 20 favorite series (1 list fetch + S seasons pages + S watched-marks AJAX + up to
S fallback root pages + E "published today" checks). Results are cached in-memory with
a 2-minute TTL (`FavoriteReleasesCache`). On app restart or after TTL expiry the full
fan-out repeats, causing multi-second latency.

## Solution

Add a Room-persisted cache of the fan-out results. Use stale-while-revalidate: show
the cached list instantly on screen entry, then refresh in the background when stale.

## Components

### 1. `FavoriteReleaseCacheEntity` (new Room entity)

Separate table `favorite_release_cache`. Fields mirror `ReleaseSummaryEntity` minus
main-feed specifics. Key columns:

| Column | Type | Notes |
|--------|------|-------|
| `detailsUrl` | TEXT PK | Episode URL, same as `ReleaseSummaryEntity` |
| `kind` | TEXT | `ReleaseKind.name` |
| `titleRu` | TEXT | Series title |
| `episodeTitleRu` | TEXT? | Episode title |
| `seasonNumber` | INT? | |
| `episodeNumber` | INT? | |
| `releaseDateRu` | TEXT | dd.MM.yyyy |
| `posterUrl` | TEXT | LostFilm poster |
| `backdropUrl` | TEXT? | TMDB backdrop |
| `positionInList` | INT | Sort order in full list |
| `fetchedAt` | LONG | When the fan-out completed |
| `isWatched` | BOOL | |
| `episodeOverviewRu` | TEXT? | TMDB episode description |
| `episodeOverviewSource` | TEXT? | Source of overview |
| `seriesOverviewRu` | TEXT? | TMDB series description |
| `movieOverviewRu` | TEXT? | TMDB movie description |
| `tmdbRating` | TEXT? | TMDB rating |

Index on `fetchedAt` for cleanup queries.

### 2. `FavoriteReleaseCacheMetadataEntity` (new Room entity)

Table `favorite_release_cache_metadata`. Single row (PK = fixed sentinel `1`).

| Column | Type | Notes |
|--------|------|-------|
| `id` | INT PK | Always 1 |
| `fetchedAt` | LONG | When the fan-out completed |
| `favoriteSeriesCount` | INT | Number of favorite series |
| `itemCount` | INT | Total cached items |

### 3. DAO additions in `ReleaseDao`

```kotlin
@Query("SELECT * FROM favorite_release_cache ORDER BY positionInList ASC")
suspend fun getFavoriteReleasesCache(): List<FavoriteReleaseCacheEntity>

@Query("SELECT * FROM favorite_release_cache_metadata WHERE id = 1")
suspend fun getFavoriteReleaseCacheMetadata(): FavoriteReleaseCacheMetadataEntity?

@Query("DELETE FROM favorite_release_cache")
suspend fun deleteAllFavoriteReleasesCache()

@Query("DELETE FROM favorite_release_cache_metadata")
suspend fun deleteAllFavoriteReleasesCacheMetadata()

@Transaction
suspend fun replaceFavoriteReleasesCache(
    items: List<FavoriteReleaseCacheEntity>,
    metadata: FavoriteReleaseCacheMetadataEntity,
) {
    deleteAllFavoriteReleasesCache()
    deleteAllFavoriteReleasesCacheMetadata()
    upsertFavoriteReleasesCache(items)
    upsertFavoriteReleasesCacheMetadata(metadata)
}

@Upsert
suspend fun upsertFavoriteReleasesCache(items: List<FavoriteReleaseCacheEntity>)

@Upsert
suspend fun upsertFavoriteReleasesCacheMetadata(metadata: FavoriteReleaseCacheMetadataEntity)
```

### 4. Migration 19 -> 20

```sql
CREATE TABLE IF NOT EXISTS `favorite_release_cache` (
    `detailsUrl` TEXT NOT NULL,
    `kind` TEXT NOT NULL,
    `titleRu` TEXT NOT NULL,
    `episodeTitleRu` TEXT,
    `seasonNumber` INTEGER,
    `episodeNumber` INTEGER,
    `releaseDateRu` TEXT NOT NULL,
    `posterUrl` TEXT NOT NULL,
    `backdropUrl` TEXT,
    `positionInList` INTEGER NOT NULL,
    `fetchedAt` INTEGER NOT NULL,
    `isWatched` INTEGER NOT NULL,
    `episodeOverviewRu` TEXT,
    `episodeOverviewSource` TEXT,
    `seriesOverviewRu` TEXT,
    `movieOverviewRu` TEXT,
    `tmdbRating` TEXT,
    PRIMARY KEY(`detailsUrl`)
);
CREATE INDEX IF NOT EXISTS `index_favorite_release_cache_fetchedAt`
    ON `favorite_release_cache` (`fetchedAt`);

CREATE TABLE IF NOT EXISTS `favorite_release_cache_metadata` (
    `id` INTEGER NOT NULL,
    `fetchedAt` INTEGER NOT NULL,
    `favoriteSeriesCount` INTEGER NOT NULL,
    `itemCount` INTEGER NOT NULL,
    PRIMARY KEY(`id`)
);
```

### 5. Changes to `LostFilmRepositoryImpl.observeFavoriteReleases`

New constant:
```
FAVORITE_RELEASES_ROOM_FRESH_MS = 15 * 60 * 1000L  // 15 minutes
```

Updated flow for page 1:

```
1. Read Room cache (getFavoriteReleaseCacheMetadata + getFavoriteReleasesCache)
2. If Room cache exists:
   a. Convert to ReleaseSummary list
   b. Paginate the cached list, run enrichSummaries on the page slice, emit as Success
   c. If cache is fresh (< FAVORITE_RELEASES_ROOM_FRESH_MS) -> return
3. Run fan-out (fetchAllFavoriteReleasesStreaming) as before
   - Partial emissions continue working as-is during fan-out
4. On fan-out success:
   a. Run enrichSummaries on the full result
   b. Save enriched items to Room via replaceFavoriteReleasesCache
   c. Update in-memory cache (existing behavior)
   d. Paginate and emit final Success
```

For pages > 1, the existing in-memory cache path is unchanged. The in-memory cache
is populated from the Room cache or from a fresh fan-out, so pagination always works
from memory.

### 6. Cache invalidation

`invalidateFavoriteReleasesCache()` is extended to also clear Room:

```kotlin
private suspend fun invalidateFavoriteReleasesCache() {
    favoriteReleasesCacheMutex.withLock { favoriteReleasesCache = null }
    releaseDao.deleteAllFavoriteReleasesCache()
    releaseDao.deleteAllFavoriteReleasesCacheMetadata()
}
```

This method is already called from:
- `setFavorite()` -- user toggled favorite on a series
- After successful fan-out implicitly replaces old data

Additionally, `setEpisodeWatched()` should call `invalidateFavoriteReleasesCache()`
to reflect watched-state changes in the favorites list.

Cleanup: `deleteExpiredData` is extended to also delete expired favorite cache rows.

### 7. `LostFilmDatabase` changes

- Add `FavoriteReleaseCacheEntity` and `FavoriteReleaseCacheMetadataEntity` to
  `@Database(entities = [...])`.
- Bump version from 19 to 20.
- Add `MIGRATION_19_20` to `ALL_MIGRATIONS`.

## What does NOT change

- `fetchAllFavoriteReleasesStreaming` -- fan-out logic stays the same.
- `FavoriteReleasesResult.Partial` -- streaming partial results during fan-out.
- Concurrency limits (`FAVORITE_SERIES_LOAD_CONCURRENCY`, etc.).
- `LostFilmFavoriteSeriesParser`, `LostFilmSeasonEpisodesParser` -- parsers untouched.
- Existing unit tests for parsers.

## Testing

1. Unit test: `observeFavoriteReleases` returns Room-cached data without network
   calls when cache is fresh (< 15 min).
2. Unit test: Room cache is written after successful fan-out.
3. Unit test: `invalidateFavoriteReleasesCache` clears both in-memory and Room cache.
4. Unit test: `setEpisodeWatched` invalidates favorite releases cache.
5. Existing parser tests remain green.
6. `./gradlew testDebugUnitTest lint assembleDebug` passes.
