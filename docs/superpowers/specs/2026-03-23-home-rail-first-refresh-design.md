# Home Rail-First Refresh Design

## Goal

Refresh the Android TV `Home` screen so navigation feels more predictable with a remote and the whole screen reads as one cohesive visual system.

The release rail must remain the primary experience. The redesign should improve how users reach utility actions such as `Войти`, `Настройки`, and `Обновить`, while keeping the first interaction centered on browsing releases.

## Scope

This design covers:
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/`
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/components/PosterCard.kt`
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/theme/`
- focused `Home` UI tests in:
  - `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/`
  - `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/`

Included:
- medium-scope layout refresh for `Home`
- improved remote navigation between the rail and top utility actions
- a unified visual language for rail, cards, utility actions, and stage panels
- a redesigned lower information area as a single `bottom stage`
- aligned styling for loading, stale, paging, and update-status states
- focused regression coverage for `Home` interaction and rendering

Excluded:
- new data sources or repository behavior
- changes to release parsing or models
- a complete app-wide redesign outside `Home`
- a multi-rail or multi-row content system
- turning `Home` into a hero-first landing screen
- adding new destinations or deep-link flows

## Product Intent

The current `Home` works, but it feels assembled from separate pieces:
- a simple title and action row
- a poster rail
- a lower info block
- footer update/version UI

This makes the screen feel flatter and less intentional than the newer design work elsewhere in the app. It also makes utility actions feel visually detached from the content flow.

The refreshed `Home` should feel like a TV-first content surface:
- the rail stays dominant
- the selected item gets stronger contextual support
- utility actions become easier to reach and more visually integrated
- temporary states stop looking like special-case UI

## Approved Direction

### Chosen concept: Rail-first with bottom stage

The approved direction is:
- keep the release rail as the dominant visual and interaction layer
- keep initial focus on the rail
- reduce the top area to a compact header plus a unified utility-action row
- replace the current lower information area with a single cohesive `bottom stage`
- avoid a large cinematic hero that competes with the rail

The chosen visual reference is the rail-dominant `B` layout explored during brainstorming:
- dominant rail in the main body
- compact top utility area
- cohesive bottom stage for selected-release context

## Layout Structure

### 1. Compact top header

The top of `Home` becomes a compact structured header.

It should contain:
- screen title `Новые релизы`
- utility actions on the right:
  - `Войти` or `Выйти`
  - `Настройки`
  - `Обновить` when an update is available

The top header must remain visually secondary to the rail. It exists to:
- orient the user
- expose quick actions
- keep service actions easy to reach with one directional move

It must not become a large hero or banner zone.

### 2. Dominant release rail

The rail remains the center of the `Home` experience.

Requirements:
- it is the largest and most visually important block on screen
- initial focus lands on the current or first available poster card
- horizontal navigation remains the primary browsing action
- focus and selection behavior remain stable across recompositions

The rail should feel more premium than it does today, but its role does not change:
- browse releases
- move horizontally
- update selection context below

### 3. Bottom stage

The lower information area becomes a single, unified `bottom stage`.

It replaces the feel of a plain text block with a purposeful panel that contains selected-release context in one visual unit.

Suggested content:
- title
- episode title for series when available
- release date
- short status or metadata line when useful
- compact secondary service information if needed

The `bottom stage` is intentionally contextual rather than interactive. It should update from the focused card but should not become a separate focus maze in the default flow.

### 4. Footer integration

The existing app version and update-related messaging should no longer feel detached from the rest of the screen.

They should either:
- be absorbed into the compact top utility/header language
- or be visually aligned with the bottom stage and utility styling

The important rule is consistency:
- no isolated mini-UI in a different visual language
- no random-looking bottom-right cluster

## Focus and Navigation Model

### Entry focus

Initial focus must always remain content-first:
- first available poster card in the rail
- or the previously selected card when that state is restored

This preserves the primary TV browsing flow.

### Primary directional rules

Focus rules should become explicit and deterministic:

- `Left/Right` on the rail:
  - move across poster cards
- `Up` from a rail card:
  - move into the top utility row
- `Down` from the top utility row:
  - return to the currently selected rail card

This gives utility actions a fast path without stealing initial focus from content.

### Bottom stage behavior

The `bottom stage` should remain non-focusable in the default design.

Reasoning:
- it provides context, not actions
- it should not create an extra navigation layer
- it should not compete with the top utility row or the rail

This keeps `Home` easy to traverse with a remote:
- rail for browsing
- top row for utility actions
- bottom stage for read-only context

## Visual Language

### Chosen style: Calm TV panel system

All major `Home` surfaces should use one shared visual system:
- soft large radii
- dark translucent or dark layered panels
- one consistent border language
- one accent color for focus and active state
- restrained secondary text and status styling

The screen should feel like one product surface rather than stacked default components.

### Rail and card treatment

`PosterCard` should stay the visual anchor of the rail, but styling becomes more deliberate:
- focused card gets stronger emphasis than a simple subtle outline
- selected/focused state should visually relate to the same accent system used elsewhere on `Home`
- non-focused cards stay calm and slightly recessed
- existing season/episode and watched badges should adopt the same visual language as the rest of the screen

### Utility action treatment

Top-row actions should no longer look like generic standalone buttons.

They should share the same shape and styling principles as the rest of `Home`:
- unified corner radius
- consistent vertical sizing
- one visual treatment for neutral actions
- one accent treatment for the primary/actionable item such as `Обновить`

### Bottom stage treatment

The stage should feel like a deliberate panel, not just text under the rail.

It should use:
- the same panel background family as utility surfaces
- stronger spacing and hierarchy
- one clear title treatment
- one clear secondary-text treatment
- subtle status styling that does not overpower the title

## State Presentation

The screen should stop switching into unrelated visual styles for transient states.

### Loading

Loading should use the same `Home` surface language:
- loading indicator inside a styled panel or stage-aligned surface
- not a visually disconnected default placeholder

### Stale state

Stale state should be compact and integrated:
- not a loud banner unless content is otherwise unavailable
- aligned with the overall panel/status system

### Paging error

Paging errors should remain actionable but visually consistent:
- compact inline surface
- same typography and spacing rules as the rest of the screen

### Full-screen error

Full-screen error remains valid only when there is no content to show.

If content is available:
- errors should stay compact
- the rail should remain visible
- the screen should avoid flipping into a fully separate error aesthetic

### Update status

Quiet update state and available-update UI must share the same style family as the rest of `Home`.

This is especially important because the redesign explicitly makes utility actions easier to access and more visible.

## File and Component Responsibilities

### `HomeScreen`

Owns:
- overall layout composition
- top header and utility row placement
- coordination of rail + bottom stage + transient screen states

### `HomeRail`

Owns:
- rail layout
- card focus transitions
- end-reached behavior
- deterministic movement within the release row

It should not grow into a second screen-layout owner.

### `PosterCard`

Owns:
- poster presentation
- selected/focused card styling
- in-card metadata badges such as season/episode and watched state

### `BottomInfoPanel`

Likely evolves into a more stage-like component or gets replaced by a renamed stage-focused component.

Its responsibility remains:
- render selected-release context
- stay visually aligned with `Home`
- remain non-focusable in the approved design

### Theme layer

`Home` should stop relying on ad hoc local colors where practical.

Add or promote named theme colors/tokens for:
- `Home` panel background(s)
- `Home` focus accent
- `Home` border states
- `Home` secondary and muted text
- utility/action states

## Testing Strategy

Implementation should add or update coverage for:

### 1. Focus and navigation

UI tests for:
- initial focus remains on the rail
- `Up` from the focused card reaches the utility row
- `Down` from a utility action returns to the selected rail card
- horizontal rail movement still updates selected context

### 2. Stage updates

UI tests for:
- the bottom stage updates when the focused card changes
- the bottom stage remains informational and does not introduce extra focus targets

### 3. Utility actions

UI tests for:
- `Настройки`, auth action, and update action render in the unified top row
- update CTA still appears only when actionable update state exists

### 4. State consistency

UI tests for:
- stale state remains visible without replacing content
- paging error remains visible and actionable while content stays onscreen
- full-screen error still appears only when there is no content

### 5. Visual regression intent

Even if screenshot coverage is not added immediately, implementation should be reviewed against these visual goals:
- rail remains the dominant zone
- top row feels compact and secondary
- bottom stage reads as one cohesive panel
- loading and error states match the same style family

## Risks and Mitigations

### Risk: top utility row steals too much attention

Mitigation:
- keep it compact
- preserve rail as initial focus
- avoid oversized buttons or banner treatment

### Risk: bottom stage becomes another navigation zone

Mitigation:
- keep it non-focusable
- make it contextual only
- limit it to summary information

### Risk: visual refresh becomes a full-screen rebuild

Mitigation:
- preserve the single-rail architecture
- avoid introducing hero-first layout behavior
- keep changes local to `Home`, card styling, and theme tokens

### Risk: inconsistent transient states remain

Mitigation:
- explicitly redesign loading, stale, paging, and update surfaces as part of the same `Home` refresh
- do not treat them as postscript fixes

## Success Criteria

- `Home` remains clearly rail-first.
- Initial focus lands on the rail, not on utility actions.
- Utility actions become easier to reach and visually integrated into the screen.
- The lower information area reads as a cohesive `bottom stage`, not a plain text block.
- Cards, actions, stage, and transient states all share one visual language.
- `Home` feels stylistically closer to the rest of the app’s newer TV surfaces without becoming a full hero-screen redesign.

## Next Step

After the user reviews and approves this document, the next step is to write a focused implementation plan for the `Home` rail-first refresh.
