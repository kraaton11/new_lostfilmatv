# Home Single Feed Mode Design

## Goal

Replace the previously approved two-rail Home presentation with a single-mode Home screen that shows exactly one content feed at a time:
- `Новые релизы`
- `Избранное`

The selected mode must be user-controlled from the Home header, must be remembered, and must preserve TV-friendly focus behavior.

## Relationship To Existing Specs

This spec supersedes only the Home-surface parts of:
- `docs/superpowers/specs/2026-03-24-lostfilm-account-favorites-sync-design.md`

The `Details` favorite toggle, LostFilm sync contract, and favorite-feed data source remain unchanged. Only the Home presentation, state model, and settings meaning change in this document.

## Scope

Included:
- Home header tabs for `Новые релизы` and `Избранное`
- rendering only one Home feed at a time
- persistence of the selected Home mode
- per-mode focus restoration for Home cards
- Favorites-specific empty, login-required, and error states
- adapting the existing Home favorites visibility setting to control tab availability instead of a second rail
- ViewModel, Compose, and preference changes required to support the new behavior

Excluded:
- changes to the LostFilm favorite mutation flow on `Details`
- a standalone Favorites destination
- multiple visible Home rails at once
- Android TV launcher channel changes
- persistence of exact focused-card position across full app restarts

## Product Intent

Home should feel simpler than the earlier two-rail concept:
- the user chooses which feed they want right now
- the screen stays visually focused on one feed
- the active mode remains stable until the user changes it

This keeps the Home surface aligned with TV use:
- large cards stay the primary interaction target
- the header can expose mode switching without becoming the initial focus sink
- empty or unavailable Favorites should not silently dump the user back into `Новые релизы`

## Approved UX

## 1. Header layout

Home remains a single screen with one header.

Approved layout direction:
- keep the existing Home header structure
- add mode tabs in the header using the chosen visual direction from variant `A`
- keep service actions such as `Настройки`, `Войти/Выйти`, and app update CTA in the header

Behavior rules:
- the active mode tab is visually highlighted
- the tabs are always in the same position in the header
- the tabs do not receive initial focus when Home opens
- the `Избранное` tab is rendered only when the existing Home favorites visibility preference is enabled

## 2. Single visible feed

Exactly one feed is rendered below the header at a time.

Mode behavior:
- when `Новые релизы` is active, Home behaves like the current main feed
- when `Избранное` is active, Home shows only the account-backed favorite-release feed
- Home must not render both feeds together
- Home must not fall back to a hidden second rail or mixed-content layout

## 3. Focus behavior

The screen stays card-first even though mode switching lives in the header.

Approved rules:
- initial focus on Home goes directly to a card in the active mode when that mode has content cards
- `Up` from the top card moves focus to the active mode tab
- `Down` from a mode tab moves focus back to the remembered card for that mode when card content exists
- `Left` and `Right` on the mode tabs switch between modes
- service buttons remain reachable from the header, but they are not the default entry point when Home opens

Fallback focus rules for non-card states:
- if the active mode is `LoginRequired`, initial focus goes to the sign-in CTA
- if the active mode is `Error`, initial focus goes to the retry CTA
- if the active mode is `Loading` or `Empty`, initial focus remains on the active mode tab until content or another focusable action appears
- `Down` from the active mode tab follows the same fallback order when the mode has no cards

When restoring focus:
- each mode remembers its own last focused card during the current navigation session
- returning from `Details` restores both the active mode and the last focused card within that mode
- if the remembered card no longer exists after a refresh, focus falls back to the nearest valid card; if no safe nearest card can be resolved, use the first card in that mode

## 4. Persistence

Two different kinds of memory are required.

Persisted across app restarts:
- the selected Home mode

Remembered only for the current navigation/session lifetime:
- the last focused card for `Новые релизы`
- the last focused card for `Избранное`

The selected mode should be stored in preferences because the user explicitly asked for it to be remembered. Card focus should remain an in-memory or saved-state concern rather than a long-term preference because it is useful for navigation continuity, not for product identity.

## 5. Favorites-specific states

The `Избранное` mode must stay selected even when its content is unavailable. The app must not auto-switch back to `Новые релизы` just because Favorites has no content or needs login.

Required states for `Избранное` mode:
- `Loading`
- `Content`
- `LoginRequired`
- `Empty`
- `Error`

Meaning:
- `Loading`: the favorite-release feed is being loaded
- `Content`: at least one favorite release card is available
- `LoginRequired`: the Favorites mode is selected but there is no valid LostFilm session
- `Empty`: the session is valid, the feed loaded successfully, but there are no new releases from favorite series
- `Error`: loading Favorites failed for a non-auth reason and the user can retry

Required behavior:
- `LoginRequired` shows a short explanation and a clear CTA to sign in
- `Empty` explains that there are currently no new releases for favorite series
- `Error` keeps the user in Favorites mode and offers `Повторить`
- none of these states should force a mode switch back to `Новые релизы`

## 6. Settings meaning

The existing Home favorites visibility setting remains user-controlled, but its meaning changes.

Old meaning:
- whether a second Favorites rail appears below the all-new rail

New meaning:
- whether the `Избранное` mode is available as a Home tab

Rules:
- when the setting is off, only `Новые релизы` is available on Home
- when the setting is turned off while `Избранное` is currently selected, Home immediately and softly falls back to `Новые релизы`
- that fallback also becomes the newly persisted selected mode
- the visible copy in Settings should change from `Полка Избранное` to `Вкладка Избранное`
- the control copy should read like `Показывать вкладку Избранное на главном экране`

## Architecture And State Model

## 1. Home mode model

Home should move away from a multi-rail-first state shape and instead use an explicit feed mode.

Recommended model:
- `HomeFeedMode.AllNew`
- `HomeFeedMode.Favorites`

`HomeUiState` should expose:
- `selectedMode`
- `availableModes`
- mode-specific content state for `AllNew`
- mode-specific content state for `Favorites`
- per-mode remembered focus keys
- the currently resolved selected item and item key for the active mode

This makes the selected Home experience explicit instead of deriving it indirectly from which rails happen to be present.

## 2. Mode content state

Each mode should have its own content state instead of sharing one rail list and hiding parts of it.

Recommended direction:
- `HomeModeContentState.Loading`
- `HomeModeContentState.Content(items, selectedItemKey?)`
- `HomeModeContentState.Empty`
- `HomeModeContentState.LoginRequired`
- `HomeModeContentState.Error(message)`

Use the same state family for both modes where practical, while allowing Favorites to meaningfully use `LoginRequired` and `Empty`.

Boundary rule:
- the UI renders based on the active mode and that mode's state
- the ViewModel owns transitions between mode states
- repository results are mapped into these UI states before reaching Compose

## 3. Favorites availability contract

The existing setting and authentication state together determine whether Favorites mode can exist at all.

Availability rules:
- if the Home favorites setting is disabled, `Favorites` is removed from `availableModes`
- if the setting is enabled, `Favorites` remains available even when the content state is `LoginRequired`, `Empty`, or `Error`
- authentication failure affects Favorites content state, not mode existence

This distinction matters because the user asked to stay in a separate Favorites screen-state rather than dropping back to the general feed.

## 4. Focus memory model

Focus memory should be tracked per mode rather than per rail.

Recommended direction:
- keep a small in-memory or `SavedStateHandle` map from `HomeFeedMode` to `selectedItemKey`
- update the remembered key when a card gains focus
- resolve the selected item only from the active mode's current items

This lets Home restore:
- `Новые релизы` to the user's last card there
- `Избранное` to the user's last card there

without cross-mode focus contamination.

## Data Flow

## 1. Startup

On Home startup:
1. load the persisted selected mode from preferences
2. compute `availableModes` from settings
3. if the persisted mode is unavailable, fall back to `AllNew` and persist that fallback
4. start loading the all-new feed
5. prepare Favorites mode state only if Favorites mode is available
6. render the active mode only

Favorites loading policy:
- if Favorites mode is available, the app loads it eagerly in parallel with the all-new feed
- eager loading is required so that persisted startup into `Favorites` does not incur an avoidable second loading turn after Home is already visible

## 2. Mode switching

When the user switches tabs:
1. update `selectedMode`
2. persist the new selected mode immediately
3. keep the previous mode's remembered card intact
4. restore the new mode's remembered card if possible
5. if the new mode has no remembered valid card, focus the first card when content exists

Switching to Favorites must not require leaving Home or navigating to a different route.

## 3. Return from Details

When returning from `Details`:
- keep the selected Home mode unchanged
- restore the remembered card for that mode
- if Favorites was invalidated by a follow or unfollow action, refresh Favorites content without changing modes
- if the refreshed item set no longer contains the previously remembered card, resolve a fallback card deterministically

## 4. Setting changes

When the Favorites visibility setting changes while Home is alive:
- turning it on adds the `Избранное` tab and prepares Favorites state
- turning it off removes the `Избранное` tab
- if `Favorites` was selected at that moment, switch to `AllNew`, persist the fallback, and place focus on the resolved all-new card

## Error Handling

## 1. All-new mode

`Новые релизы` keeps its current loading, paging, stale-cache, and retry behavior.

This redesign must not weaken:
- paging
- stale banner behavior
- full-screen error handling for the primary feed

## 2. Favorites mode

Favorites-specific failures must stay local to Favorites mode.

Rules:
- authentication absence maps to `LoginRequired`
- an empty successful feed maps to `Empty`
- network or parse failure maps to `Error`
- retry from Favorites re-runs only the Favorites load
- Favorites errors must not block all-new paging or all-new content rendering

## 3. Lost remembered focus

If a remembered key becomes invalid:
- first try to keep focus within the same mode on a nearby surviving item
- otherwise use the first available item in that mode
- if the mode has no items because it is in `Empty`, `LoginRequired`, or `Error`, focus should remain in the mode-level state surface until the user navigates elsewhere

## Testing Strategy

## 1. Preference tests

Add coverage for:
- persisting and reading the selected Home mode
- preserving the existing favorites-visibility preference semantics after the meaning change
- falling back safely when stored values are unknown or missing

## 2. ViewModel tests

Add coverage for:
- Home start with persisted `AllNew`
- Home start with persisted `Favorites`
- fallback to `AllNew` when Favorites mode is unavailable
- preserving mode selection across content refresh
- restoring remembered card per mode
- keeping Favorites selected for `LoginRequired`, `Empty`, and `Error`
- updating only Favorites after favorite invalidation from `Details`
- switching away from Favorites when the setting is disabled during runtime

## 3. Compose and focus tests

Add coverage for:
- initial focus landing on a card, not on a header tab or service button
- `Up` from top card focusing the active tab
- `Down` from active tab restoring the remembered card
- `Left` and `Right` on tabs switching modes
- header service actions remaining reachable without hijacking default focus
- Favorites empty/login/error states rendering in place

## 4. Emulator smoke coverage

Smoke-check on Android TV should confirm:
- mode selection survives app restart
- returning from `Details` restores mode and card
- disabling Favorites removes the tab and soft-falls back to `Новые релизы`
- Favorites can show content, login-required, and empty/error states without breaking Home

## Acceptance Criteria

The redesign is complete when all of the following are true:
- Home shows a header tab selector for `Новые релизы` and `Избранное`
- only one feed is visible at a time
- the selected mode is remembered across app restart
- Home opens with focus on a card in the active mode when cards exist, otherwise on the active mode's first valid fallback target
- returning from `Details` restores the same mode and card when possible
- Favorites stays selected for login-required, empty, and error states
- disabling Favorites removes the tab and safely falls back to `Новые релизы`
- the previous two-rail Home presentation is no longer reachable in the new flow
