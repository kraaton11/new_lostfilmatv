# Settings Theme And TV Navigation Design

## Goal

Bring the settings screen into the same visual system as the refreshed TV surfaces and make remote navigation feel explicit, stable, and easy to follow on Android TV.

The redesign should keep the existing three settings groups and business behavior, but upgrade the screen from a functional two-pane form into a cohesive TV settings hub.

## Scope

This design covers:
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/`
- shared settings-related theme tokens in `app/src/main/java/com/kraat/lostfilmnewtv/ui/theme/`
- focused settings tests in `app/src/test/java/com/kraat/lostfilmnewtv/ui/settings/`
- settings-related navigation regression tests that exercise the screen through `AppNavGraph`

Included:
- visual unification of settings with the `Home` surface language
- a refined two-zone layout based on the approved `A` direction from brainstorming
- explicit D-pad focus routing between the left rail and right content panel
- per-section focus memory inside the settings UI
- overview/header treatment for every settings section
- a reusable TV-style settings surface/button component for selected and focused states
- focused test coverage for navigation, section summaries, and update-state rendering

Excluded:
- new settings categories beyond `Качество`, `Обновления`, and `Канал Android TV`
- new business logic for updates, playback quality, or Android TV channel sync
- changes to persistence keys or store behavior
- app-wide theming changes outside the settings screen and shared theme tokens it consumes
- a migration to `androidx.tv.material3` or a broader component-library rewrite

## Product Intent

The current settings screen already uses a two-pane layout, but it still feels visually separate from the newer TV screens:
- it relies on local hardcoded colors instead of shared theme tokens
- selected and focused states are not clearly separated
- focus routing depends too much on default Compose behavior
- each section feels more like a list of buttons than a TV settings scene

The new screen should feel like part of the same product as `Home`:
- dark layered panels
- gold selected state
- blue focus treatment
- stronger section summaries
- deterministic remote navigation

The user should understand where they are, what the current value is, and where `Left`, `Right`, `Up`, and `Down` will go next.

## Approved Direction

### Chosen concept: Variant A with TV-safe focus flow

The approved direction is the `A` layout explored during brainstorming:
- a compact left rail for sections
- a large right panel for the active section
- a summary card at the top of the right panel
- larger, more intentional controls below the summary

The visual and interaction adjustments are:
- keep the two-zone composition from the current implementation
- restyle it with the same palette family already used on `Home`
- make right-panel navigation mostly vertical even when options are visually dense
- remember the last focused control within each section

The result should feel denser and more premium than the current settings screen without turning it into a dashboard-style focus maze.

## Layout Structure

### 1. Left section rail

The left pane remains a stable vertical rail with the three existing sections:
- `Качество`
- `Обновления`
- `Канал Android TV`

Requirements:
- the rail stays visible and does not scroll
- one section is always active
- each rail item shows a compact secondary summary of its current value or state
- the rail width stays close to the current split, roughly 28-30% of the screen width

Example summaries:
- `Качество`: `1080p`
- `Обновления`: `Доступно обновление` or `Последняя версия`
- `Канал Android TV`: `Все новые релизы`

This gives the user more context before moving into the right panel and makes the left side useful rather than purely navigational.

### 2. Right content panel

The right pane should always follow the same internal structure, regardless of section:
1. overview card
2. optional helper text
3. control list or compact control grid

The overview card is mandatory for all three sections so the screen reads as one system instead of three unrelated layouts.

The panel may scroll internally, but the target is that common TV viewport sizes can see the overview plus the section controls without frequent scrolling.

### 3. Section-specific content

#### Playback quality

The quality section should contain:
- overview card describing that the value affects default playback choice
- currently selected quality highlighted in the summary
- three large option controls:
  - `1080p`
  - `720p`
  - `480p / SD`

No extra technical detail is needed. This remains a fast preference picker.

#### Updates

The updates section remains the most informative section, but uses the same structure as the others.

The overview card should show:
- installed version
- latest version
- current update status

Below it:
- update-mode choices
- `Проверить обновления`
- `Скачать и установить` when an APK is available

The actions should not jump around as update state changes. Loading, success, failure, and available-update states should all reuse the same overview surface.

#### Android TV channel

The Android TV channel section should contain:
- overview card describing how the setting affects the Android TV home channel
- current mode summary
- three large option controls:
  - `Все новые релизы`
  - `Только непросмотренные`
  - `Не показывать`

This section should stay direct and avoid over-explaining.

## Visual Language

### Shared palette and surface system

Settings should stop using local hardcoded colors for major states and instead align with the existing named TV palette:
- `BackgroundPrimary`
- `HomePanelSurface`
- `HomePanelSurfaceStrong`
- `HomePanelBorder`
- `HomeAccentGold`
- `HomeAccentBlue`
- `HomeTextSecondary`
- `HomeTextMuted`

If the current tokens are not quite enough, add clearly named settings-compatible tokens in the theme layer rather than scattering fresh literals through `SettingsScreen`.

### Focused vs selected

Focused and selected states must be visually distinct:
- `selected`: persistent meaning, rendered with the gold active treatment
- `focused`: transient remote position, rendered with a blue border, stronger surface, and subtle scale

Rules:
- a selected but unfocused item remains clearly selected
- a focused but unselected item does not look active or saved
- a focused and selected item combines both signals without ambiguity

This distinction is especially important on TV, where users navigate first and confirm second.

### Reusable settings TV control

Instead of styling each `Button` ad hoc, introduce a shared settings-specific TV control primitive that owns:
- shape
- minimum touch/focus size
- border logic
- selected styling
- focused styling
- disabled styling
- optional subtitle/value support when needed

This component should be used by:
- rail items
- quality options
- update-mode options
- channel-mode options
- action buttons where the same surface language fits

This keeps settings internally consistent and makes future sections cheaper to add.

## Focus And Navigation Model

### Context from Android docs

Official Compose guidance reviewed through Context7 supports:
- `FocusRequester` for explicit focus targets
- `focusProperties` for overriding two-dimensional traversal
- `focusGroup()` for predictable focus movement within grouped controls

The redesign should adopt those patterns instead of relying on default traversal heuristics.

### Entry and zone transitions

Default focus behavior:
- initial focus lands on the active item in the left rail
- `Right` from the active rail item moves to the first focusable control in the current section
- `Left` from the first column or first control in the right panel returns to the active rail item

The left rail and the right panel should each behave like coherent focus zones.

### Right-panel focus flow

The right panel should feel mostly vertical from a remote perspective, even if some controls are arranged more densely.

Requirements:
- the overview card is informational and non-focusable
- the first actionable control is the topmost option under the overview
- controls inside a section are grouped with `focusGroup()` where it improves predictability
- if a section uses two visual columns, traversal still follows an intentional top-to-bottom route rather than arbitrary nearest-neighbor jumps

This keeps the visual density of variant `A` without inheriting the navigation complexity of a grid dashboard.

### Focus memory

The screen should remember focus in two ways:
- active section in the left rail
- last focused actionable item inside each section

Expected behavior:
- moving from rail to content restores the last focused control for that section when possible
- switching to a different section focuses that section's remembered control, or falls back to the first action
- returning from right panel to left rail preserves which section is active

This memory belongs in `SettingsScreen` UI state, not in `SettingsViewModel`, because it is presentation state rather than app data.

## State Presentation

### Stable overview card

Each section overview card should remain layout-stable as state changes.

For updates this is especially important. The same card should carry:
- checking state
- up-to-date state
- update available state
- failure state
- download-in-progress state

The card may update text, emphasis, and supporting lines, but actions below it should not shift into a different layout family.

### Disabled and loading behavior

When update actions are unavailable:
- the action remains visible when appropriate
- disabled styling stays inside the same component family
- loading labels such as `Проверяем...` or `Скачивание…` remain readable without collapsing spacing

Errors should appear as stable user-facing text inside the overview card, not as temporary detached banners.

## Architecture And Responsibilities

### `SettingsScreen`

Owns:
- overall layout composition
- current section selection UI state
- focus requesters and focus routing
- per-section focus memory
- rendering section summaries and overview cards

It should not absorb business logic currently owned by the view model.

### `SettingsViewModel`

Keeps owning:
- persisted values
- update-check state
- install/download state
- update-related status text

The redesign should consume the existing state model as much as possible and only extend it if a section-summary string or explicit overview metadata cannot be reasonably derived in UI code.

### Theme layer

Owns:
- shared TV colors and any additional named settings tokens
- reusable styling helpers for the new settings TV control

The theme layer should make it obvious that settings is part of the same product family as `Home`.

## Testing Strategy

Implementation should follow TDD and add failing tests before production changes.

### 1. Section summaries and layout

UI tests should verify:
- the rail renders all three sections
- the default active section remains correct
- each rail item exposes its current summary text
- the active section renders an overview card plus section controls

### 2. Focus behavior

UI tests should verify:
- initial focus lands on the active rail item
- moving into the right panel targets the expected first or remembered control
- moving left from the right panel returns to the active rail item
- switching sections preserves or restores per-section focus memory

If some focus assertions are easier in `androidTest` than Robolectric Compose tests, the implementation plan may split coverage accordingly.

### 3. Update-state rendering

UI tests should verify:
- installed version, latest version, and status appear in the overview card
- loading state keeps the action visible but disabled
- install action appears only when `installUrl` is available
- failure and download states render inside the stable overview surface

### 4. Selected-state regressions

UI tests should continue to verify:
- selected quality remains exposed as selected
- selected update mode remains exposed as selected
- selected Android TV channel mode remains exposed as selected

Existing tags should be preserved where practical to avoid needless test churn.

## Risks And Mitigations

### Risk: the screen becomes visually richer but harder to navigate

Mitigation:
- keep the overview card non-focusable
- keep the right panel mostly vertical in traversal
- explicitly wire `focusProperties` for cross-zone transitions

### Risk: theme unification becomes token sprawl

Mitigation:
- prefer existing `Home` tokens first
- only add new named tokens when multiple settings surfaces genuinely need them
- do not replace a few literals with many near-duplicate theme values

### Risk: focus memory fights recomposition or loading updates

Mitigation:
- keep memory keyed by stable section and control identifiers
- fall back gracefully to the first actionable item when the previous target is absent
- add regression tests around section switches and update-state transitions

### Risk: update states cause layout jumping

Mitigation:
- keep status messaging inside the overview card
- preserve action order and position across update states
- avoid conditional restructuring of the panel

## Success Criteria

- Settings visually match the app's newer TV surfaces instead of using isolated local colors.
- The left rail and right content panel behave as explicit focus zones.
- Focused and selected states are both clear and distinct.
- Each section has a stable overview card and more intentional content hierarchy.
- Remote navigation feels predictable when moving between rail and content.
- Existing settings behavior stays intact while the screen becomes easier to use on Android TV.

## Next Step

After the user reviews and approves this document, the next step is to write a focused implementation plan for the settings theme and TV navigation refresh.
