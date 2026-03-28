# Android TV Home Channel Background Refresh Design

## Overview

This document extends the existing Android TV launcher channel design in [2026-03-21-android-tv-home-channel-design.md](/D:/ai/tvbox1/new_lostfilm/.worktrees/android-tv-home-channel/docs/superpowers/specs/2026-03-21-android-tv-home-channel-design.md).

The new requirement is to refresh launcher channel content even when the app is not open. The background flow should refresh the cached first page of new releases and then republish the Android TV home channel from local storage.

The design keeps the current channel architecture intact. It adds periodic scheduling around the existing repository and `HomeChannelSyncManager` instead of introducing a second channel-specific fetch path.

## Goals

- Refresh Android TV launcher channel content while the app is closed.
- Use the app's existing `LostFilmRepository` and Room cache as the only data pipeline.
- Respect the user's channel setting and stop background work when the channel is disabled.
- Limit background refresh cost to the first page of new releases.
- Keep failure handling quiet and safe for a TV device.

## Non-Goals

- No exact-timing guarantee for every 6-hour run.
- No `AlarmManager`, `BOOT_COMPLETED` receiver, or launcher-specific background hacks.
- No multi-page background crawl in `v1`.
- No user-visible notifications about background refresh status.
- No background re-authentication flow when the LostFilm session is missing or expired.

## Product Decisions

- Background scheduling uses `WorkManager`.
- The app schedules one unique periodic worker every 6 hours.
- The worker requires `NetworkType.CONNECTED`.
- Background refresh is enabled only when the Android TV channel mode is active:
  - `ALL_NEW`
  - `UNWATCHED`
- Background refresh is cancelled when the user selects `DISABLED`.
- Each background run refreshes only page `1` through the existing `LostFilmRepository.loadPage(1)` API.
- After the page refresh completes, the worker republishes the launcher channel through the existing `HomeChannelSyncManager`.
- Android may batch periodic work. "Every 6 hours" means regular periodic scheduling, not a hard real-time promise.

## High-Level Architecture

Add a small background-refresh slice with these responsibilities:

- `HomeChannelBackgroundScheduler`
  - Owns `WorkManager` enqueue and cancel logic.
  - Uses one stable unique work name for the launcher channel refresh job.
  - Provides clear entry points such as `ensureScheduled()` and `cancel()`.

- `HomeChannelBackgroundRefreshRunner`
  - Contains the background business flow without `WorkManager` APIs.
  - Reads channel mode and session state.
  - Refreshes the first page of releases when allowed.
  - Invokes `homeChannelSyncManager.syncNow()` after the refresh attempt.
  - Returns a small outcome enum so the worker can map outcomes to `Result.success()` or `Result.retry()`.

- `HomeChannelRefreshWorker`
  - Thin `CoroutineWorker` bridge.
  - Pulls dependencies from `applicationContext as LostFilmApplication`.
  - Delegates the real work to `HomeChannelBackgroundRefreshRunner`.

This keeps responsibilities clear:

- `WorkManager` handles persistence and system scheduling.
- the runner owns background refresh decisions.
- `LostFilmRepository` remains the only owner of release-list fetching and cache writes.
- `HomeChannelSyncManager` remains the only owner of launcher publication.

## Scheduling Design

### Unique Periodic Work

The scheduler registers one periodic job, for example:

- work name: `android-tv-home-channel-refresh`
- interval: `6 hours`
- constraint: connected network

The scheduler must never create duplicate periodic jobs. Repeated scheduling calls should update or preserve the existing unique work instead of stacking additional requests.

### Scheduling Triggers

The scheduler should run at these moments:

- application startup
- after the user changes channel mode in settings

Behavior by mode:

- `ALL_NEW` or `UNWATCHED`
  - ensure the periodic worker is scheduled
  - keep the existing immediate in-app sync behavior
- `DISABLED`
  - cancel the periodic worker
  - keep the existing immediate channel deletion behavior

No separate boot receiver is needed. `WorkManager` already persists scheduled periodic work across process death, reboot, and app update.

## Background Data Flow

Each worker run follows this sequence:

1. Read the selected Android TV channel mode.
2. If the mode is `DISABLED`, finish successfully without network work.
3. Check whether a LostFilm session exists and is still usable for background refresh.
4. If the session is missing or expired, finish successfully and keep the previously published channel state unchanged.
5. Call `repository.loadPage(1)` to refresh the first page of new releases into Room.
6. If page refresh returns usable content, call `homeChannelSyncManager.syncNow()`.
7. Finish with a worker result that reflects whether the run should be retried immediately.

### Why Page 1 Only

The launcher channel default is "new releases", and the top row only needs the freshest slice of content.

Refreshing only page `1`:

- satisfies the feature requirement
- reuses the repository code already exercised by the home screen
- avoids turning periodic work into a heavy multi-page crawler
- keeps network and CPU cost predictable on TV boxes

## Outcome And Retry Policy

The background runner should return one of these outcomes:

- `SKIPPED_DISABLED`
- `SKIPPED_UNAUTHENTICATED`
- `REFRESHED`
- `FAILED_RETRYABLE`

Worker mapping:

- `SKIPPED_DISABLED` -> `Result.success()`
- `SKIPPED_UNAUTHENTICATED` -> `Result.success()`
- `REFRESHED` -> `Result.success()`
- `FAILED_RETRYABLE` -> `Result.retry()`

### Retry Guidance

Immediate retry should be reserved for truly retryable failures such as:

- an uncaught transient repository failure that leaves no usable page result
- unexpected worker-level infrastructure failures

The worker should not aggressively retry when the app already has usable cached page data or when the user simply needs to sign in again. The periodic 6-hour cadence already provides the next normal refresh opportunity, and avoiding retry storms is better for TV devices.

## Authentication Behavior

Background refresh must be non-interactive.

If the app has no saved LostFilm session or the session is marked expired:

- do not launch auth UI
- do not clear the existing launcher channel automatically
- do not schedule extra immediate retries
- leave the last published launcher state in place until the user authenticates again

This keeps background work quiet and avoids surprising launcher regressions when auth expires.

## Integration Points

### LostFilmApplication

Extend `LostFilmApplication` with a lazily created `HomeChannelBackgroundScheduler` and the dependencies needed by the background runner:

- `repository`
- `playbackPreferencesStore`
- `homeChannelSyncManager`
- `sessionStore`

App startup should call `homeChannelBackgroundScheduler.ensureScheduled()` alongside the existing startup sync flow.

### Settings Flow

The existing settings flow already persists channel mode and triggers immediate channel sync.

Extend it so that mode changes also drive background scheduling:

- active mode -> ensure periodic work is scheduled
- disabled mode -> cancel periodic work

This keeps scheduling ownership outside the composable UI and matches the existing "settings changes trigger side effects through higher layers" pattern.

### Existing Channel Sync Flow

`HomeChannelSyncManager` remains the publication entry point. The background design does not create a second publisher path.

If implementation needs stronger outcome reporting for background refresh, add the smallest possible return type around the sync boundary. UI callers can continue ignoring the returned value.

## Error Handling

### Network Failure

If the worker cannot refresh page `1` and there is no usable result path for the run:

- return `Result.retry()`
- leave the previous channel state untouched

### Cached Fallback Content

If the repository falls back to existing cached page content:

- allow the runner to republish the channel from Room
- finish successfully unless there is a stronger signal that the whole run should retry immediately

This keeps the channel stable and avoids overreacting to transient connectivity problems.

### Channel Publication Failure

If launcher publication fails during the worker run:

- do not crash the app process
- log the failure for diagnostics
- treat it as retryable only if the implementation can detect that the sync did not complete

### Unsupported TV Provider

If the device launcher or `TvProvider` does not support the channel APIs:

- background work should finish safely
- the user's setting remains persisted
- periodic work may remain scheduled, but each run should fail quietly without user-visible disruption

## Testing Strategy

### Unit Tests

- `HomeChannelBackgroundScheduler`
  - schedules unique periodic work for active modes
  - does not duplicate work on repeated startup scheduling
  - cancels periodic work for `DISABLED`

- `HomeChannelBackgroundRefreshRunner`
  - returns `SKIPPED_DISABLED` when the channel is off
  - returns `SKIPPED_UNAUTHENTICATED` when the session is missing or expired
  - refreshes page `1` and calls `syncNow()` for active modes
  - returns `FAILED_RETRYABLE` for retryable repository failures

- worker mapping
  - converts runner outcomes into the expected `WorkManager` `Result`

### Regression Tests

- existing settings tests verify that channel mode changes now also affect scheduling
- existing startup tests verify the app still schedules background work without breaking launch
- existing channel sync tests remain the source of truth for publisher behavior

## Risks And Mitigations

### Over-Scheduling

Risk:

- repeated app starts or settings changes could enqueue duplicate workers

Mitigation:

- centralize scheduling in one scheduler class
- use unique periodic work only

### Hidden Coupling Between Worker And UI Flows

Risk:

- background refresh logic could drift away from the app's in-foreground refresh path

Mitigation:

- reuse `LostFilmRepository.loadPage(1)`
- reuse `HomeChannelSyncManager`
- avoid a second fetch or publish pipeline

### Auth Expiration

Risk:

- periodic background refresh silently stops producing fresh data when auth expires

Mitigation:

- skip safely without deleting the last channel state
- rely on the existing in-app auth flow when the user next opens the app

## Open Assumptions Locked For This Feature

- The first page of `/new/` is sufficient to keep the launcher channel fresh in `v1`.
- Background refresh should be silent and non-interactive.
- The existing session store can reliably signal whether background refresh should be attempted.
- The current immediate in-app sync triggers remain in place even after periodic work is added.

## Next Step

After the user reviews and approves this document, the next step is to write a focused implementation plan for background Android TV channel refresh.
