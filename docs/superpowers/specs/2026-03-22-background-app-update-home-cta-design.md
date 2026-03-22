# Background App Update Home CTA Design

## Overview

This document extends [2026-03-21-app-update-settings-design.md](/D:/ai/tvbox1/new_lostfilm/docs/superpowers/specs/2026-03-21-app-update-settings-design.md).

The current update flow only checks for new APK releases inside settings. The new requirement is to make `Тихая проверка` work outside the settings screen and surface a visible install action directly on `Home` when a newer APK has already been found.

The design keeps the existing GitHub Releases source and Android handoff for installation. It adds background scheduling, a small persistent update-availability store, and a `Home` footer CTA that opens the APK installation flow immediately.

## Goals

- Run quiet update checks outside the settings screen.
- Keep background scheduling simple and predictable on Android TV devices.
- Surface a visible `Обновить` action on `Home` when a newer APK has been found.
- Keep the `Обновить` action visible until the app is actually updated or the saved update becomes definitively stale.
- Reuse the existing GitHub release check and APK install handoff.
- Preserve the current settings screen as the place for explicit update diagnostics and manual checks.

## Non-Goals

- No silent APK installation.
- No background APK download manager.
- No Android system notification for update availability.
- No changelog viewer or release-notes UI.
- No exact-timing guarantee for periodic work.
- No second update source beyond GitHub Releases.

## Product Decisions

- `Тихая проверка` schedules periodic background checks every `6` hours.
- `Тихая проверка` also requests an immediate one-time check when the app returns to the foreground.
- `Ручной` mode cancels background update work but does not clear an already-found update.
- A found update appears on `Home` as a bottom-right `Обновить` button in the approved `B` layout:
  - `Обновить` button
  - app version text on the same bottom-right row
- Pressing `Обновить` on `Home` opens the APK installation flow immediately through the existing launcher path.
- Transient check failures do not remove an already-found update from `Home`.
- The saved update is cleared only when:
  - the installed app version is equal to or newer than the saved latest version
  - or a later successful check proves the saved update is no longer valid

## High-Level Architecture

Add a focused background-update slice with these responsibilities:

- `AppUpdateBackgroundScheduler`
  - Owns `WorkManager` enqueue and cancel behavior for quiet update checks.
  - Schedules one unique periodic job and one unique immediate job.
  - Reads the saved update mode and only runs when `QUIET_CHECK` is active.

- `AppUpdateBackgroundWorker`
  - Thin `CoroutineWorker` bridge.
  - Follows the same bootstrap pattern already used by the home-channel worker.
  - Casts `applicationContext` to a small provider interface exposed by `LostFilmApplication`.
  - Delegates business logic to the coordinator.

- `AppUpdateCoordinator`
  - Owns the "check and persist" flow shared by settings and background work.
  - Calls the existing release client or repository.
  - Compares remote and installed versions.
  - Writes or clears the saved update availability state.
  - Exposes a single observable update-availability stream for UI consumers.

- `AppUpdateAvailabilityStore`
  - Persists the last known actionable update payload only.
  - Does not depend on `BuildConfig` or version comparison rules.
  - Stores only the minimum data needed for the `Home` CTA and install handoff.

- `HomeViewModel`
  - Observes the app-owned update-availability stream.
  - Maps saved update state into `Home` footer CTA visibility.
  - Owns transient `Home`-only feedback such as APK-launch failure copy.

This keeps boundaries clear:

- `AppUpdateRepository` remains the release-checking network layer.
- the coordinator owns persistence and business rules.
- `WorkManager` owns scheduling.
- `Home` renders a CTA from saved state instead of performing release checks directly.
- `Settings` remains the explicit user-facing maintenance surface.
- the store stays as a dumb persistence boundary without app-version logic.

## Background Scheduling Design

### Unique Work

The scheduler should use one stable periodic work name and one stable immediate work name, for example:

- `app-update-quiet-check`
- `app-update-quiet-check-immediate`

Scheduling rules:

- periodic work interval: `6 hours`
- network constraint: `CONNECTED`
- periodic work policy: `ExistingPeriodicWorkPolicy.UPDATE`
- immediate work policy: `ExistingWorkPolicy.KEEP`
- repeated scheduling calls must not stack duplicate workers
- switching away from `QUIET_CHECK` cancels both periodic and immediate update work

### Worker Bootstrap

`AppUpdateBackgroundWorker` should follow the same construction pattern already used in the project for `WorkManager` workers:

- no custom `WorkerFactory` is required for this feature
- the worker reads a provider from `applicationContext as AppUpdateBackgroundWorkerProvider`
- `LostFilmApplication` implements that provider and exposes the coordinator dependency needed by the worker

This keeps worker wiring aligned with existing project patterns and prevents duplicate dependency bootstrapping paths.

### Scheduling Triggers

The scheduler should run at these moments:

- app startup
- when the user switches update mode in settings
- when `MainActivity` returns to the foreground after the first resume

Behavior by mode:

- `QUIET_CHECK`
  - ensure the periodic worker is scheduled
  - request an immediate worker on foreground return
- `MANUAL`
  - cancel background update work
  - keep any already-saved update CTA state intact

Android may batch periodic work. "Every 6 hours" is regular periodic scheduling, not a real-time promise.

## Saved Update State

### Persisted Fields

Persist only the minimum actionable payload:

- `latestVersion`
- `apkUrl`

The store does not decide whether a saved update is stale for the current install. It only reads, writes, and clears persisted payload.

### Persistence Rules

The coordinator owns version comparison and stale-data clearing rules.

When a check returns `UpdateAvailable`:

- save `latestVersion`
- save `apkUrl`
- mark the update as actionable for the current install

When a check returns `UpToDate`:

- clear any saved update whose version is less than or equal to the installed version

When a check returns `Error`:

- do not clear previously saved actionable update data
- leave the last known `Home` CTA available

When the app starts or when the coordinator seeds its observable state from persisted data:

- the coordinator compares the installed version against the saved `latestVersion`
- if the installed version is already equal to or newer than the saved `latestVersion`, the coordinator clears the saved update and exposes no CTA

This locks in the user-approved rule that a found update remains visible until the app is actually updated, even if the next quiet check fails.

## Check And Persist Flow

The coordinator should expose one shared operation such as `refreshSavedUpdateState()`:

1. Run the existing GitHub release check.
2. Map the result into persistence actions.
3. Return a compact outcome for UI or worker callers.

Suggested outcomes:

- `UP_TO_DATE`
- `UPDATE_SAVED`
- `FAILED_KEPT_PREVIOUS`
- `FAILED_EMPTY`

The exact enum name is flexible. The important requirement is that callers can distinguish:

- a fresh update was found
- no update is needed
- the check failed but an older saved CTA is still valid
- the check failed and there is nothing actionable to show

### Observable UI Contract

The coordinator should expose one observable source of truth for actionable update availability, for example a `StateFlow<SavedAppUpdate?>`.

Rules:

- `Home` and `Settings` both consume the same observable update state
- background workers update the store through the coordinator
- when the app process is alive, coordinator writes must update the observable state immediately
- when the app process is recreated, the observable state is seeded from persisted store data only after the coordinator resolves stale saved updates against the installed app version

This guarantees that a background-found update can appear on `Home` without requiring the user to visit settings first.

## Home Screen UX

### Footer Layout

The approved `Home` footer keeps the current version text in the bottom-right area and adds the `Обновить` button on the same row when a saved update exists.

Behavior:

- no saved update:
  - show only the app version text, as today
- saved update exists:
  - show `Обновить` and the version text in the same bottom-right row

The footer should remain secondary UI, not a takeover banner.

### Focus Behavior

The new button must be focusable with the TV remote, but it must not steal initial focus from the release rail.

That means:

- initial `Home` focus remains on the poster row
- the CTA becomes reachable through normal directional navigation
- existing poster focus and details navigation behavior remains unchanged

### Action Behavior

Pressing `Обновить` on `Home` should:

- use the saved `apkUrl`
- call the same `ReleaseApkLauncher` already used by settings
- open the Android APK install flow immediately

This path should not navigate to settings first.

### Failure Feedback

If APK install handoff cannot be opened:

- keep the `Home` CTA visible
- show a short user-facing failure message such as `Не удалось открыть обновление.`
- avoid modal interruption unless Android itself requires it

The transient failure message should not live in the persistent update store. It belongs to `HomeViewModel` or an equivalent `Home`-scoped state holder so the saved CTA state and one-off UI feedback stay separate.

## Settings Integration

The settings screen remains the owner of:

- update mode selection
- manual `Проверить обновления`
- detailed status text for checking results

Required integration changes:

- switching update mode must also drive the background scheduler
- settings checks should use the same coordinator so manual and background logic do not drift apart
- if a saved update already exists, settings should reuse that same observable state for install availability instead of requiring the user to rediscover it

This keeps one source of truth for update availability while preserving settings as the maintenance screen.

## App Wiring

### LostFilmApplication

`LostFilmApplication` should own and expose:

- `AppUpdateAvailabilityStore`
- `AppUpdateCoordinator`
- `AppUpdateBackgroundScheduler`
- any worker-facing provider needed by the background worker

### MainActivity

`MainActivity` should request an immediate quiet update check on foreground return in the same spirit as the existing home-channel refresh trigger.

The first resume after launch should stay quiet, matching the current pattern used for background channel refresh.

### Navigation And View Models

`AppNavGraph` and `HomeViewModel` should receive enough app-owned state to render the `Home` CTA without placing update network calls inside `HomeScreen`.

The preferred shape is:

- app-owned saved update state observed by higher layers
- `HomeScreen` stays mostly presentational
- `HomeViewModel` coordinates `Home`-specific action feedback if needed
- `HomeUiState` should contain both:
  - persistent CTA visibility and APK target derived from observable update state
  - transient footer error text for failed install handoff

## Error Handling

Cases to handle:

- no network connectivity
- GitHub request failure
- malformed release payload
- release without APK asset
- install intent cannot be opened

Rules:

- keep background work quiet
- never remove an already-actionable `Home` CTA because of a transient check failure
- keep settings usable in every error case
- avoid raw exception text in UI
- avoid duplicate update CTA state owners

## Testing Strategy

Implementation should add or update coverage for:

### 1. Saved update state

Unit tests for:

- saving update availability from a successful check
- keeping saved availability after a retryable check failure
- clearing saved availability when the installed version catches up
- ignoring stale saved data on read

### 2. Background scheduling

Unit tests for:

- `QUIET_CHECK` schedules unique periodic work
- `MANUAL` cancels update work
- foreground return requests immediate one-time work only when quiet mode is active
- periodic requests use `CONNECTED` network and `6` hour interval

### 3. Worker and coordinator behavior

Unit tests for:

- background work persists `UpdateAvailable`
- `UpToDate` clears stale saved update state
- `Error` keeps previously saved actionable update state
- worker result mapping stays non-crashing and predictable

### 4. Home UI

UI tests for:

- version text remains in the bottom-right footer when no update exists
- saved update state adds the `Обновить` button in the approved footer row
- the button does not replace or duplicate the version label
- pressing `Обновить` launches the saved APK URL
- launch failure shows a short user-facing error while keeping the CTA available

### 5. Settings and navigation integration

Route or nav-graph tests for:

- quiet mode persists and schedules background update work
- returning to the app requests immediate quiet check work
- saved update state is visible on `Home` after app wiring initializes
- settings and `Home` use the same saved update source

## Risks And Mitigations

### Duplicate Background Patterns

Risk:

- update checks and home-channel refresh could diverge into inconsistent scheduling styles

Mitigation:

- mirror the existing scheduler and worker structure where practical
- keep update-specific logic in separate classes with similarly narrow responsibilities

### Over-Clearing Saved Updates

Risk:

- a transient GitHub failure could accidentally remove the `Home` CTA

Mitigation:

- clear saved state only on successful evidence that the app is up to date or the saved update is stale

### Footer Clutter

Risk:

- the `Home` footer could become noisy or steal attention from the release rail

Mitigation:

- keep the CTA small and bottom-right
- preserve initial focus on content cards
- avoid adding extra explanatory copy in the footer

## Success Criteria

- `Тихая проверка` performs update checks outside settings.
- Quiet mode schedules periodic checks every 6 hours.
- Returning the app to the foreground requests an immediate quiet check.
- A found update persists across transient check failures.
- `Home` shows an `Обновить` CTA in the approved bottom-right row when a saved update exists.
- Pressing `Обновить` opens the APK install handoff directly.
- `Ручной` mode stops future background checks without hiding an already-found update.
- The CTA disappears automatically after the app is actually updated.

## Next Step

After the user reviews and approves this document, the next step is to write a focused implementation plan for background quiet update checks and the `Home` install CTA.
