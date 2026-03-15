# LostFilm Android TV Design

## Overview

This document defines the `v1` design for an Android TV application written in Kotlin that parses new releases from `https://www.lostfilm.today/new/`.

The application is a greenfield Android TV project. The data source is parsed directly on device. The UI is Russian-only.

## Goals

- Build an Android TV application for browsing new LostFilm releases.
- Show all entries from `/new/`, including series episodes and movies.
- Use a TV-friendly home screen with a horizontal poster rail.
- Open a minimal details screen for the selected release.
- Support page-by-page loading of `/new/page_N`.
- Cache data locally in a database with a limited storage lifetime.
- Fall back to cached data when the site is unavailable.

## Non-Goals For V1

- No backend service.
- No user authentication.
- No comments, ratings, photos, or gallery UI.
- No English titles in the UI.
- No English release dates in the UI.
- No search, filtering, favorites, or watched-state sync.
- No routine manual GitHub release work after initial repository setup.

## Product Decisions

- Platform: Android TV.
- Language: Kotlin.
- UI toolkit: Compose for TV.
- Home layout: horizontal poster rail.
- Focused item info: bottom info panel, matching the approved "variant B" direction.
- Details screen fields: poster, Russian title, season/episode for series, and `Ru` release date only.
- Movie details screen: poster, Russian title, and `Ru` release date only.
- Data source: direct parsing of `https://www.lostfilm.today/new/` on device.
- Refresh behavior: automatic refresh when opening the home screen.
- Pagination: full pagination through `/new/page_N`.
- Cache policy:
  - Fresh window: 6 hours.
  - Retention window: 7 days.
  - Records older than 7 days are deleted.

## Selected Technical Approach

### Chosen Stack

- Kotlin
- Compose for TV
- Navigation Compose
- OkHttp
- Jsoup
- Room
- Kotlin coroutines and Flow
- AndroidX ViewModel

### Why This Approach

Compose for TV is the chosen UI direction because the project is a new Android TV application and the user wants a custom poster-first interface. The app still stays simple in `v1`: one main browsing screen, one details screen, one parser pipeline, and a local database-backed cache with TTL behavior.

## High-Level Architecture

The application is split into focused layers:

- `data/network`: downloads HTML pages from LostFilm.
- `data/parser`: converts HTML into strongly typed models.
- `data/db`: persists summaries, page ordering, details, and cache metadata.
- `data/repository`: combines network, parsing, TTL logic, and fallback behavior.
- `ui/home`: renders the horizontal poster rail and bottom info panel.
- `ui/details`: renders the minimal details screen.
- `navigation`: owns screen routing and restore behavior.

### Main Flow

1. The app opens `Home`.
2. `Home` requests page 1 from the repository.
3. The repository tries network first.
4. On success, parsed results are written to Room and emitted to the UI.
5. On failure, the repository serves the newest cached page data that is still within the 7-day retention window.
6. When the user moves near the end of the loaded rail, the next page is requested.
7. Pressing `OK` on a poster opens `Details`.
8. `Details` loads from cache first if available, then refreshes from network when needed.

## UI Design

## Home Screen

The home screen is a single horizontal poster rail optimized for DPAD navigation.

### Visible Elements

- Screen title
- One horizontal rail of posters
- A bottom information panel for the currently focused item
- Loading placeholders when data is not ready
- A compact stale-data banner when fallback cache is shown
- A focused retry action only when neither network nor valid cache is available

### Bottom Information Panel

The bottom panel updates whenever focus changes.

For series episodes it shows:

- Russian series title
- Russian episode title if present
- Season and episode numbers
- `Ru` release date

For movies it shows:

- Russian movie title
- `Ru` release date

It does not show:

- English titles
- English release dates
- Ratings
- Comment counts

### Home Focus Behavior

- Left and right DPAD move focus through posters.
- Focused poster gets a stronger visual treatment.
- The bottom panel always reflects the focused item.
- Pressing `OK` opens the details screen.
- Returning with `Back` restores the previously focused poster and scroll position.

### Home Loading Behavior

- On first open, the screen loads automatically.
- While loading the first page, poster skeletons are shown.
- While loading the next page, a loading card appears at the end of the rail.
- If a request fails but retained cache exists, cached content is shown with a stale-data notice.
- If neither network nor retained cache exists, a full-screen error state with a `Retry` button is shown.

## Details Screen

The details screen is intentionally minimal for `v1`.

### Series Details

- Poster
- Russian series title
- Season number
- Episode number
- `Ru` release date

### Movie Details

- Poster
- Russian movie title
- `Ru` release date

### Details Exclusions

- No English text
- No ratings
- No comments
- No photos
- No cast
- No trailers

## Data Model

The app uses separate models for list summaries, page ordering, and detailed page data.

### Release Kind

- `SERIES`
- `MOVIE`

### Release Summary

Used by the home screen rail and bottom panel.

Fields:

- `id`: stable internal identifier
- `kind`: series or movie
- `titleRu`
- `episodeTitleRu`: nullable
- `seasonNumber`: nullable
- `episodeNumber`: nullable
- `releaseDateRu`
- `posterUrl`
- `detailsUrl`
- `pageNumber`
- `positionInPage`
- `fetchedAt`

### Release Details

Used by the details screen.

Fields:

- `detailsUrl`
- `kind`
- `titleRu`
- `seasonNumber`: nullable
- `episodeNumber`: nullable
- `releaseDateRu`
- `posterUrl`
- `fetchedAt`

### Page Cache Metadata

Needed for page-based pagination and TTL checks.

Fields:

- `pageNumber`
- `fetchedAt`
- `itemCount`

## Database Design

Room stores cached summaries and details. The database is also responsible for limiting storage lifetime.

### Tables

- `release_summaries`
- `release_details`
- `page_cache_metadata`

### Retention Rules

- Records younger than 6 hours are considered fresh.
- Records older than 6 hours but younger than 7 days may still be shown as fallback cache.
- Records older than 7 days are deleted.

### Cleanup Moments

Cleanup runs:

- On app startup
- After a successful refresh
- Before or during details load

### Freshness Semantics

- Fresh data: show normally
- Retained but stale data: show with a "data may be outdated" banner
- Expired data: delete and do not use

## Parsing Design

Parsing is HTML-based and uses Jsoup.

## List Parsing

Source pages:

- `https://www.lostfilm.today/new/`
- `https://www.lostfilm.today/new/page_N`

### Summary Parsing Rules

Each release is parsed from `.serials-list .row`.

From the list HTML:

- Russian title comes from `.name-ru`
- Russian episode title comes from `.details-pane .alpha`
- `Ru` release date comes from the `.alpha` line containing `Дата выхода Ru`
- Poster URL comes from `.picture-box img.thumb`
- Details URL comes from the main row link
- Season and episode are parsed from `.overlay .left-part` for series rows
- Movies are identified when `.overlay .left-part` contains `Фильм`

### Data Normalization

- Relative URLs are resolved against `https://www.lostfilm.today`
- Whitespace is trimmed
- Empty episode title values are converted to `null`
- Movie season and episode values are stored as `null`
- English fields are ignored entirely

### Pagination Parsing

Page `N` is loaded only when needed.

The repository should track:

- current loaded page
- whether a next page exists
- whether a page is already loading

Pagination end is determined in this order:

1. Parse the paginator and use the highest available page number when it exists.
2. If paginator data is missing, treat an empty parsed page as the end.
3. If the page returns only duplicates of already stored entries, stop paging for the current session and surface a non-blocking paging error for diagnostics.

## Details Parsing

Details source is the release page itself.

For series pages, parse:

- `h1.title-ru`
- poster image
- `Ru` release date
- season and episode from breadcrumbs or URL if needed

For movie pages, parse:

- `h1.title-ru`
- poster image
- `Ru` release date

Only Russian-facing fields are extracted for UI use.

## Repository Behavior

The repository owns all decisions about network, database, fallback cache, and paging.

### Home Refresh

When `Home` opens:

1. Try network for page 1.
2. If success:
   - parse page 1
   - replace cached page 1 entries
   - update page metadata
   - delete expired data
   - emit fresh content
3. If failure:
   - read retained cache for page 1
   - if available, emit cache with stale flag
   - if unavailable, emit error state

### Pagination

When the user reaches the end threshold of the current poster rail:

1. Determine the next page number.
2. Avoid duplicate in-flight requests.
3. Try network for the next page.
4. On success, append parsed results to the database and UI stream.
5. On failure, try retained cache for that page.
6. If neither exists, keep already loaded pages and surface a non-blocking paging error state.

### Details Loading

When `Details` opens:

1. Try to read cached details for the selected `detailsUrl`.
2. If cache is fresh, show it immediately.
3. If cache is stale but retained, show it with stale indication while refreshing if needed.
4. If network succeeds, replace cached details.
5. If network fails and no retained details exist, show an error state with retry.

## Error Handling

The app must degrade predictably when LostFilm is unavailable or markup changes.

### Network Errors

- Timeouts
- No connectivity
- HTTP errors

Result:

- Fall back to retained cache when possible
- Show explicit user-facing stale or error state

### Parse Errors

If expected markup is missing or malformed:

- treat the page as failed
- keep previously retained data if available
- surface a retryable error

### Partial Data

If a field cannot be extracted:

- keep required fields strict
- use `null` only for optional fields such as episode title or season/episode for movies
- do not invent missing values

## GitHub Automation

The repository workflow is designed so that normal day-to-day work requires minimal GitHub interaction after an initial one-time setup.

### Workflow Goals

- Automatically create and update pull requests from working branches
- Automatically run required checks on every pull request
- Automatically merge green pull requests into `main`
- Automatically build and publish a signed release APK after merge
- Keep manual GitHub interaction limited to initial repository and secrets setup

### Branch Strategy

- Development happens in working branches
- `main` is the protected integration branch
- Pull requests target `main`
- Direct pushes to `main` are not part of the normal workflow

### Pull Request Automation

When code is pushed to a working branch:

1. A GitHub Actions workflow checks whether an open pull request to `main` already exists.
2. If no pull request exists, the workflow creates one automatically.
3. If a pull request already exists, new commits simply update that pull request.

This keeps the repository in pull-request flow without requiring manual PR creation.

### Required Pull Request Checks

Each pull request must automatically run:

- project build
- unit tests
- parser fixture tests
- lint or static analysis checks

These checks are required status checks for merging into `main`.

### Protected Main Branch

`main` is protected with branch rules that require:

- pull requests for changes
- required status checks to pass
- auto-merge to be enabled

The design assumes merge is not performed manually in the normal path.

### Auto-Merge

Once a pull request is green and all required checks pass, GitHub auto-merge completes the merge into `main` automatically.

This is the default happy path and is intended to minimize user involvement.

### Release Automation

After merge into `main`:

1. A release workflow builds the release APK.
2. The APK is signed using GitHub repository secrets.
3. The workflow creates a `GitHub Release`.
4. The signed APK is attached to that release.

The release process should not require a manual tag or manual upload for normal `v1` operation.

### Versioning Automation

Release version metadata should be generated automatically so the user does not need to manually edit release identifiers before each build.

The exact versioning mechanism is an implementation detail, but it must support:

- monotonically increasing `versionCode`
- deterministic release naming

### One-Time Manual Setup

A one-time manual setup is acceptable to unlock the automated flow. This setup includes:

- creating the GitHub repository if it does not already exist
- adding signing secrets for the release APK
- enabling GitHub Actions write permissions needed for pull request automation
- enabling auto-merge in the repository settings
- configuring branch protection for `main`

After this setup, the intended daily workflow is: push code to a working branch and let GitHub handle the rest.

## Testing Strategy

Testing is required before implementation is considered complete.

### Parser Tests

Use saved HTML fixtures for:

- `/new/`
- `/new/page_2`
- a series details page
- a movie details page

Verify:

- Russian title parsing
- episode title parsing
- movie detection
- season/episode extraction
- `Ru` date extraction
- poster URL resolution

### Repository Tests

Verify:

- fresh cache behavior within 6 hours
- stale-but-retained behavior after 6 hours
- expiration and deletion after 7 days
- page append logic
- network failure fallback

### ViewModel Tests

Verify:

- automatic first-page load
- next-page loading
- stale banner state
- home error state
- details error state
- state restoration hooks

### UI Smoke Tests

Verify:

- DPAD navigation across the poster rail
- bottom panel updates on focus change
- `OK` opens details
- `Back` restores the previous position

## Risks And Mitigations

### Site Markup Changes

Risk:

- LostFilm HTML may change and break selectors.

Mitigation:

- keep parser code isolated
- cover selectors with fixture-based tests
- fail gracefully into cache or retry states

### Weak TV Hardware

Risk:

- heavy image loading or complex focus behavior may perform poorly.

Mitigation:

- keep the home layout simple
- use one primary rail in `v1`
- prefer lightweight focus effects

### Deep Pagination

Risk:

- repeated page loads may stress the parser or create duplicate data.

Mitigation:

- page-based metadata
- unique constraints on cached entries
- in-flight request guards

## Open Assumptions Locked For V1

- The application is TV-only.
- The data source remains `https://www.lostfilm.today/new/`.
- The UI remains Russian-only.
- `Ru` release date is the only release date shown to the user.
- Cache freshness and retention stay at 6 hours and 7 days.
- Home screen remains a single horizontal poster rail with a bottom info panel.
- GitHub remains the primary remote hosting and CI/CD platform.
- A one-time secrets setup is acceptable in exchange for near-hands-off day-to-day GitHub usage.

## Next Step

After the user approves this design document, the next step is to create a detailed implementation plan before touching production code.
