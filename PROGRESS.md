## Goal
- Fix season-specific TMDB poster/overview for anthology series with rate limiting and backend wildcard proxy routing

## Constraints & Preferences
- ADB device at `192.168.2.209:5555` (Amlogic-based Android TV box)
- App package: `com.kraat.lostfilmnewtv`, main activity: `.MainActivity`
- KinoPoisk API key proxied via backend at `https://auth.bazuka.pp.ua/api/`
- Backend proxy at `https://auth.bazuka.pp.ua/api/` (FastAPI on server1 Docker)
- Server1: `144.24.180.76` (Oracle x86_64, SSH user `ubuntu`)
- Docker compose project: `lostfilm-auth-bridge`, container: `auth-backend`
- All TMDB/KP requests go through backend proxy — app never talks to external APIs directly
- Server1 shell is fish — use `bash -c '...'` for complex commands

## Progress
### Done
- **Root cause found:** `tmdbCacheKey` strips season/episode from URL → all seasons of same series share cache → wrong poster/overview
- **Backend KP proxy deployed** on server1 (container healthy, KP search/details verified working)
- **TmdbPosterClient — added `getSeasonImages(tmdbId, seasonNumber)`** — calls `/tv/{id}/season/{N}/images` with ru→en→null fallback
- **TmdbPosterClient — added `getSeasonOverviewRu(tmdbId, seasonNumber)`** — calls `/tv/{id}/season/{N}?language=ru-RU`
- **`tmdbCacheKey` made season-aware** — now uses `/series/<slug>/season_N/` key when season present, `/series/<slug>/` for base URL
- **`performSearch` tries season images** — calls `getSeasonImages` first, merges with series images via `mergeSeasonAndSeriesImages`
- **`resolveSeriesOverview` tries season overview** — calls `getSeasonOverviewRu`, falls back to `getSeriesOverviewRu`
- **Cached paths no longer extra API calls** — Room stores per-season data via season-aware cacheKey; `seasonOverviewCache` memory LRU stores overviews; `seasonOverviewNegativeCache` (24h TTL) prevents retries on 404
- **In-memory cache path optimized** — when `seriesOverviewRu`/`movieOverviewRu` already in cached `TmdbImageUrls`, skip `resolveOverviews` re-fetch; only get episode overview if null
- **`resolveSeriesOverview` rate-limited** — `seasonOverviewMutex` with 150ms minimum gap to avoid TMDB 429
- **Global rate limiter in `TmdbPosterClient`** — companion object mutex enforces 300ms minimum between ALL TMDB API calls; 0 HTTP 429 observed in logs after deploy
- **`fetchImages`/`fetchOverview` made `suspend`** — to support `rateLimit()` call inside them
- **Backend wildcard proxy routing fixed** — reverted from broken `app.mount()` approach to `app.include_router()` with proper prefixes; wildcard proxy returns 404 for known API paths (`api/`, `health`, `pair/`) as safety net
- **Reverted `""` to `"/"` route path change** in health, pairings, and translation routers (caused 404 for clients without trailing slash)
- All 110 backend unit tests pass
- All 472 Android unit tests pass

### In Progress
- (none)

### Blocked
- (none)

## Key Decisions
- Month-long `images` TTL for season images (same as series) since season posters rarely change
- 24h negative cache for season overview 404s — avoids retries; `resolveSeriesOverview` falls through to series-level overview
- 300ms global rate limiter in `TmdbPosterClient` companion object — serializes all TMDB requests to stay under 4 req/s TMDB limit; acceptable cold-start latency (~36s for 30 series × ~4 calls each)
- `app.include_router()` with `prefix` parameter (not `APIRouter(prefix=...)`) for consistent route registration
- Wildcard proxy returns 404 for known API/health/pair paths as safety net (even though they should be caught by dedicated routers first)

## Next Steps
1. Verify on device that Террор S3 now shows correct poster and overview
2. Push feature branch and create PR

## Critical Context
- TMDB ID 75191 (Террор season 3) episode overviews return 404 even from direct TMDB API — not a proxy problem
- KP filmId 738318 has Террор S3 episode data but `synopsis=null` for most episodes
- `logcat -c` unreliable on this device — use `-d` with grep
- Backend container maps `127.0.0.1:3001` → `8000` inside container
- `app.mount()` approach failed with `TypeError: 'NoneType' object is not callable` due to uvicorn ASGI2 middleware compatibility issue

## Relevant Files
- `app/src/main/java/com/kraat/lostfilmnewtv/data/network/TmdbPosterClient.kt` — `getSeasonImages()`, `getSeasonOverviewRu()`, `rateLimit()` companion object, suspend `fetchImages`/`fetchOverview`
- `app/src/main/java/com/kraat/lostfilmnewtv/data/poster/TmdbPosterResolver.kt` — season-aware `tmdbCacheKey`, season overview with rate limit + negative cache, in-memory cache shortcut, `seasonOverviewMutex`
- `backend/auth_bridge/backend/src/auth_bridge/main.py` — `include_router` with proper prefixes, removed `_mount_fastapi_sub_app`
- `backend/auth_bridge/backend/src/auth_bridge/api/wildcard_proxy.py` — bypass for `/api/`, `/health`, `/pair/` paths
- `backend/auth_bridge/backend/src/auth_bridge/api/health.py` — route `""` (empty path, not `"/"`)
- `backend/auth_bridge/backend/src/auth_bridge/api/pairings.py` — route `""` (empty path, not `"/"`)
- `backend/auth_bridge/backend/src/auth_bridge/api/translation.py` — route `""` (empty path, not `"/"`)
- `backend/auth_bridge/backend/src/auth_bridge/services/tmdb_proxy_service.py` — `_SEASON_IMAGES_RE`, `_SEASON_DETAILS_RE` allowlist regex
- `/home/ubuntu/lostfilm-auth-bridge/` — deploy directory on server1
