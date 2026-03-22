# Settings Two-Pane Layout Design

**Goal:** Make the settings screen more compact on Android TV by replacing the long vertical stack with a two-pane layout: a left navigation rail for sections and a right content panel for the selected section. The updates section should get a richer status-and-actions treatment, while simpler settings stay lightweight.

## Scope

This design covers:
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/`
- `app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/`

Included:
- replacing the single scrolling settings column with a split layout
- adding section navigation on the left side
- rendering only one settings section at a time on the right side
- giving `Обновления` a compact status header plus actions
- keeping `Качество` and `Канал Android TV` simpler and denser than updates
- preserving existing settings callbacks, persistence, and update-check behavior
- test coverage for section switching, selected-state rendering, and update actions

Excluded:
- new settings categories beyond the three existing sections
- changes to update-check business logic
- changes to playback-quality persistence behavior
- changes to Android TV channel background-sync behavior
- redesign of app-wide navigation outside the settings screen

## Product Intent

The current screen is functionally correct but grows tall because all groups are rendered one after another, with the updates block taking the most space. On TV this creates unnecessary scrolling and weakens focus guidance.

The new direction is:
- let the user choose a section once on the left
- show only the relevant controls on the right
- make updates feel like an actionable control center rather than a long form
- keep simpler preferences fast to scan and change

The screen should feel more like a TV settings hub and less like a mobile-style stacked form.

## Approved Concept

### Two-pane settings hub

The approved direction is:
- left side: vertical section list
- right side: content for the currently selected section
- richer status header only for `Обновления`
- simpler lists for `Качество` and `Канал Android TV`

This keeps the heavy section informative without forcing every section to carry the same visual weight.

## Layout Structure

### 1. Left navigation rail

The left pane should contain the three existing sections in a fixed vertical list:
- `Качество`
- `Обновления`
- `Канал Android TV`

Behavior:
- one section is always selected
- focused and selected states remain visually distinct
- the rail stays narrow and stable, roughly 28-30% of screen width
- changing the selected section updates the right panel immediately

The rail should behave like a tab list conceptually, but use TV-friendly full-width buttons rather than tiny traditional tabs.

### 2. Right content panel

The right pane should occupy the remaining width and render only the active section. This removes the need to keep all groups mounted as one long document and sharply reduces vertical scrolling in the common case.

The right panel should contain:
- a section title
- optional helper copy when useful
- controls for the active section only

The panel may scroll internally if needed, but the target design is to keep each section compact enough that scrolling is rare on standard TV resolutions.

## Section Designs

### 1. Playback quality

The `Качество` section should stay simple:
- section title on the right
- dense list or compact grid of the three quality options
- clear selected state for the saved quality

No extra status card is needed here. This section is a straightforward preference picker, so adding a summary header would increase noise without adding useful information.

### 2. Updates

The `Обновления` section should become the visual anchor of the screen.

Right-panel structure:
- compact status header at the top
- update-mode choices below the header
- update action buttons at the bottom of the section

The status header should include:
- installed version
- latest version
- current update status text

This header should read like a quick snapshot so the user understands update state before moving through buttons.

Below the header:
- mode buttons: `Проверять вручную`, `Проверять тихо`
- actions: `Проверить обновления`
- optional action: `Скачать и установить` when an APK is available

The updates section should feel denser and more intentional than the current tall block, but still preserve the same behavior and messages already implemented in `SettingsViewModel`.

### 3. Android TV channel

The `Канал Android TV` section should mirror the simpler quality layout:
- section title
- compact list of the three available modes
- clear selected-state styling

No status header is needed. The user only needs to pick one mode, so the UI should stay direct.

## Focus and TV Navigation

### Deterministic two-step navigation

The interaction model should become:
1. choose a section in the left rail
2. move right into that section's controls

This gives the user a consistent mental model and reduces accidental long-range jumps through unrelated controls.

Recommended focus behavior:
- initial focus lands on the selected left-rail item
- pressing right moves focus into the first control of the active section
- pressing left from the right panel returns focus to the selected left-rail item

The selected section itself should be remembered across recompositions of the screen instance so focus does not reset unexpectedly while the user interacts with update state.

## UI State Ownership

### Section selection is UI-local

The active section does not need persistence in app settings. It is a transient presentation concern, so it should live in UI state rather than in `SettingsViewModel`.

Recommended model:
- introduce a small `SettingsSection` enum for the three sections
- keep the currently selected section in `rememberSaveable` inside `SettingsScreen`

This keeps the view model focused on real settings data and avoids mixing navigation chrome with business state.

## Visual Direction

The current visual language should be preserved:
- dark background
- gold highlight for selected content
- blue-toned neutral surfaces for inactive controls

Key changes:
- stronger left-right hierarchy
- tighter spacing on the right for simple sections
- one compact information card for updates

The result should feel more structured, not more decorative.

## Accessibility and Semantics

The redesigned screen should keep semantics useful for testing and accessibility:
- selected rail item exposed as selected
- selected option buttons remain exposed as selected
- update actions keep stable labels

Existing test tags for quality, update mode, and channel mode should be preserved where practical so tests do not need unnecessary rewrites.

## Failure Handling

This redesign should not change how failures are communicated. Update errors, loading text, and install failures should continue to appear inside the updates section using the existing state messages from the view model.

The only layout-specific requirement is that the updates status header must remain stable when state changes between:
- checking
- up to date
- update available
- failure

The section should not jump or rearrange actions drastically between states.

## Testing Strategy

Implementation should add or update coverage for:

### 1. Section navigation

UI tests for:
- the left rail renders all three sections
- one section is selected by default
- clicking a rail item swaps the visible right-panel content

### 2. Updates panel rendering

UI tests for:
- installed version, latest version, and status text render in the updates panel
- check button remains enabled/disabled according to current loading state
- install button appears only when `installUrl` is available

### 3. Simpler sections

UI tests for:
- quality options render only when the quality section is active
- Android TV channel options render only when the channel section is active
- selected states for those options remain visible in the redesigned panel

### 4. Regression protection

Keep or adapt tests covering:
- update callbacks
- quality-selection callback
- channel-mode callback
- compact-screen accessibility for the updates actions

## Success Criteria

- The settings screen no longer renders all three groups as one long vertical stack
- Users can switch sections from a left-side navigation rail
- The right side shows only the selected section
- The updates section shows versions and status in a compact header above its controls
- Quality and Android TV channel settings remain simpler than updates
- Focus navigation feels predictable with a remote
- Existing settings behavior stays intact while the screen becomes more compact
