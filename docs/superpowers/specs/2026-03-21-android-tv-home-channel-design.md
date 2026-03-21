# Android TV Home Channel Design

## Overview

This document defines the design for adding a system Android TV home screen channel to the existing LostFilm Android TV application.

The feature publishes one Android TV preview channel for the app and keeps its cards synchronized with locally cached release data. The default channel mode shows all new releases. The settings screen also allows switching the channel to show only unwatched releases or disabling the channel entirely.

This design extends the current application. It does not replace the existing in-app home rail.

## Goals

- Publish a system Android TV channel on the launcher home screen.
- Show preview cards for LostFilm releases already known to the app.
- Default the channel content to all new releases.
- Add a settings option that controls what the channel shows.
- Support at least these channel modes:
  - all new releases
  - unwatched releases
  - disabled
- Open the matching release details screen when a user clicks a channel card.
- Reuse the app's existing cache and watched-state data instead of building a second data source.

## Non-Goals

- No second launcher channel for alternate filters.
- No WorkManager or periodic background sync in `v1`.
- No separate backend or channel-specific network fetch.
- No "continue watching" row in this iteration.
- No launcher-specific customizations beyond the standard Android TV preview channel APIs.

## Product Decisions

- Launcher integration: one preview channel named `LostFilm`.
- Default mode: `All New Releases`.
- User control lives in the existing `Settings` screen.
- Settings options:
  - `Все новые релизы`
  - `Только непросмотренные`
  - `Не показывать`
- Clicking a card opens the app directly on the corresponding details screen.
- Empty channel content is allowed when no releases match the selected mode.
- Disabling the channel removes the published system channel and clears the stored channel id.

## User Experience

### Initial Behavior

- When the app has cached releases and the channel mode is active, the app publishes a single launcher channel.
- On first channel creation, the app requests that Android TV offer the channel for browsing on the home screen.
- The app should not repeatedly re-prompt during normal syncs. Re-prompting is acceptable when the user explicitly re-enables the channel after disabling it.

### Settings Behavior

- The existing `Settings` screen gains a new group titled `Канал Android TV`.
- The settings group uses the same full-width selection-button pattern already used for playback quality and update mode.
- Selecting a mode persists immediately.
- After selection:
  - `All New Releases` republishes the channel using all cached releases.
  - `Unwatched` republishes the channel using only releases with `isWatched = false`.
  - `Disabled` deletes the system channel and clears persisted channel metadata.

### Card Behavior

- Channel cards use the release poster as preview art.
- For series:
  - title: Russian series title
  - description: Russian episode title when present, otherwise season and episode text
- For movies:
  - title: Russian movie title
  - description: `Ru` release date
- Clicking a card launches `MainActivity` and routes to the release details screen for that `detailsUrl`.

## Channel Modes

Introduce a dedicated enum for launcher content selection:

- `ALL_NEW`
- `UNWATCHED`
- `DISABLED`

This enum is separate from playback and update settings because it controls launcher integration rather than in-app playback or maintenance behavior.

## High-Level Architecture

Add a focused `tvchannel` package with these responsibilities:

- `HomeChannelContentRepository`
  - Reads channel-ready release summaries from local storage.
  - Applies the selected mode filter.
  - Limits the result set to a fixed maximum, recommended `30`.

- `HomeChannelPublisher`
  - Creates, updates, and deletes the Android TV preview channel.
  - Upserts preview programs using stable ids derived from `detailsUrl`.
  - Requests channel browsable approval when appropriate.

- `HomeChannelSyncManager`
  - Coordinates settings + local content + publisher operations.
  - Serves as the single entry point for "sync the launcher channel now".

### Supporting Boundaries

- `PlaybackPreferencesStore` expands to also store:
  - selected channel mode
  - last known system `channelId`
- The Room-backed cache remains the source of truth for channel content.
- Existing app features remain owners of refresh and watched-state changes; they only notify the sync manager after successful state changes.

This keeps responsibilities clear:

- repository/database layers own release data
- settings own persisted user preference
- `tvchannel` owns launcher publication
- navigation owns how launcher clicks open details

## Data Source And Selection Rules

### Source Of Truth

Channel content is built from `release_summaries` already cached in Room.

The launcher channel must not trigger its own HTML fetches in `v1`. This avoids duplicated parsing and keeps launcher content aligned with what the app itself currently knows.

### Ordering

- Programs follow the same ordering already used by the app home rail:
  - `pageNumber ASC`
  - `positionInPage ASC`
- The sync manager takes the first `30` matching items after filtering.

### Filtering

- `ALL_NEW`: all cached releases in normal order
- `UNWATCHED`: only cached releases where `isWatched = false`
- `DISABLED`: publish nothing and remove the system channel

### Stable Program Identity

Each preview program uses `detailsUrl` as its stable internal identity.

This avoids duplicate programs across syncs and provides a natural join key for updates and cleanup.

## System Integration Design

### Android TV APIs

Use the standard Android TV preview channel APIs through `androidx.tvprovider`.

The Android-specific implementation should be isolated behind a small boundary so the orchestration logic can be unit-tested without a real `ContentResolver`.

### Channel Lifecycle

When syncing an active mode:

1. Read the persisted `channelId`, if any.
2. Verify that the channel still exists.
3. If it does not exist, create a new preview channel and persist the new `channelId`.
4. Publish or update preview programs for the selected content.
5. Remove programs that no longer belong in the selected mode.
6. If the channel was newly created, request browsable approval from Android TV.

When syncing `DISABLED`:

1. Read the persisted `channelId`.
2. If present, delete the channel.
3. Clear the persisted `channelId`.

### Channel Metadata

- Channel title: `LostFilm`
- Channel description:
  - `Новые релизы LostFilm` for `ALL_NEW`
  - `Непросмотренные релизы LostFilm` for `UNWATCHED`
- Channel logo can reuse the app icon in `v1`.

### Program Launch Intents

Each preview program stores an explicit launch intent for `MainActivity` with the selected `detailsUrl`.

The app does not need an external deep-link scheme for this feature. Instead:

- `MainActivity` reads the startup intent
- the app passes an initial details target into navigation
- `AppNavGraph` opens `Details` on first composition when that target is present

This approach fits the existing single-activity Compose navigation setup and keeps launcher integration internal to the app.

## Sync Triggers

The launcher channel should be synchronized at these moments:

- app startup
- after a successful release-list refresh
- after the user changes the channel mode in settings
- after a release is marked watched successfully

These triggers are sufficient for `v1` because they cover the normal moments when relevant data changes inside the app.

No periodic background job is required in this version.

## Settings Integration

### Preferences

Extend the existing preferences store with:

- `KEY_ANDROID_TV_CHANNEL_MODE`
- `KEY_ANDROID_TV_CHANNEL_ID`

Suggested defaults:

- mode: `ALL_NEW`
- channel id: absent

### Settings UI

Add a new group below existing settings sections:

- group title: `Канал Android TV`
- button tags:
  - `settings-tv-channel-all-new`
  - `settings-tv-channel-unwatched`
  - `settings-tv-channel-disabled`

The new group should follow the existing selection button treatment so it feels native to the current settings screen.

### Settings ViewModel

Extend `SettingsUiState` and `SettingsViewModel` to:

- expose the selected channel mode
- persist changes immediately
- invoke channel sync after a mode change

The sync callback should live outside the composable screen so UI code remains declarative.

## Repository And Database Impact

The main HTML repository design does not change.

Add only the minimum new database access needed for the launcher:

- query top cached summaries in home order
- query top cached unwatched summaries in home order

These queries belong close to `ReleaseDao` because they are pure local read models over existing cached data.

## Error Handling

The feature must fail safely and quietly.

### Unsupported Or Unavailable TV Provider

If preview channel publication is unavailable on the device or launcher:

- do not crash
- keep the selected setting value
- log the failure for diagnostics if logging exists
- skip channel publication for that sync attempt

### Missing Data

If there are no cached releases matching the selected mode:

- keep the channel published if the mode is active
- publish zero programs
- do not invent placeholders

### Invalid Stored Channel Id

If the stored `channelId` no longer resolves:

- treat it as stale
- create a fresh channel
- overwrite the stored id

### Publication Errors

If program publication fails mid-sync:

- do not crash the app
- leave the previous published state in place when possible
- allow the next normal sync trigger to try again

## Testing Strategy

### Unit Tests

- channel mode persistence in the preferences store
- content selection for:
  - `ALL_NEW`
  - `UNWATCHED`
  - `DISABLED`
- sync manager behavior for:
  - creating a missing channel
  - reusing an existing channel
  - clearing a disabled channel
  - removing obsolete programs after a mode switch
- launch-intent mapping from program click to `detailsUrl`
- `SettingsViewModel` mode changes triggering persistence and sync

### UI Tests

- settings screen renders the `Канал Android TV` group
- selecting each mode updates the selected button state

### Integration-Focused Verification

- app startup sync does not break normal app launch
- successful list refresh republishes channel content
- marking an episode watched updates the unwatched channel content on the next sync

## Risks And Mitigations

### Launcher Variability

Risk:

- Different Android TV launchers may behave differently with preview channels.

Mitigation:

- Use the standard `androidx.tvprovider` APIs only.
- Keep Android-framework calls isolated in one publisher implementation.

### Data Drift Between App And Launcher

Risk:

- Launcher content can become stale if it depends on a separate fetch path.

Mitigation:

- Build channel content strictly from the existing Room cache.
- Sync only when app-owned state changes.

### Duplicated Or Stale Programs

Risk:

- Repeated publication may leave duplicate or outdated cards.

Mitigation:

- Use `detailsUrl` as stable internal identity.
- Remove programs that are no longer in the current selected result set.

## Open Assumptions Locked For This Feature

- The app remains a single-activity Compose Android TV app.
- `release_summaries` already contain the watched flag needed for `UNWATCHED`.
- The launcher channel is allowed to be empty when no items match.
- The existing settings screen remains the correct place for launcher preferences.
- `v1` does not require automatic periodic background refresh.

## Next Step

After the user reviews and approves this document, the next step is to write a focused implementation plan for the Android TV home channel feature.
