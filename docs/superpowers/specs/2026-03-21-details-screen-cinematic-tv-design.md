# Cinematic TV Details Screen Design

**Goal:** Redesign the Android TV `Details` screen into a more cinematic, premium-feeling experience while keeping TV navigation simple and action-first. The first focus must lead directly to playback, and technical release data must be visible without leaving the primary screen.

## Scope

This design covers the visual and interaction model for the `Details` screen in:
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`

Included:
- New cinematic hero composition for the details screen
- TV-first focus model with immediate access to `Смотреть`
- Right-side vertical action stack
- Bottom technical information sheet with focusable cards
- Quality-dependent technical data presentation
- Compact service-state messaging that does not break the cinematic layout

Excluded:
- Repository, parser, or network-layer changes
- Final implementation details for playback integration
- New navigation destinations
- Expanded story/description-first layouts

## Product Intent

The screen should feel new, premium, and visually authored rather than like a small iteration on the current utility-style layout.

At the same time, this is not a poster-only concept. The screen is optimized for actual Android TV use:
- first focus goes to playback
- focus movement stays deterministic
- secondary actions remain available but do not compete with playback
- technical release data is prioritized over synopsis text on the first screen

## Design Direction

### Chosen concept: Floating Stage

The approved direction is a centered, poster-led composition:
- a large cinematic hero stage in the middle
- a floating poster card as the visual anchor
- title and key release metadata grouped around the hero
- a right-side vertical action stack
- a bottom information sheet for technical data

This creates a streaming-platform feel without sacrificing TV usability.

### Why this direction

Compared with a more conventional TV information grid, the Floating Stage layout:
- feels substantially more premium and memorable
- makes the title and artwork feel event-like
- preserves a clear place for playback actions
- gives technical data a dedicated surface rather than hiding it in small metadata text

## Layout Structure

### 1. Top metadata strip

The top area remains light and compact:
- app/context eyebrow
- small metadata chips such as type, season/episode, runtime, or HDR marker
- subtle control hinting only when needed in mock/demo contexts

This strip should not visually dominate the hero.

### 2. Center hero stage

The center of the screen holds the dramatic composition:
- floating poster card
- large title
- short supporting line
- a compact row of high-value stats such as source, dub, cache freshness, or rating

The hero should read clearly from a distance and avoid dense text.

### 3. Right action stack

The right side contains a vertical stack of actions with strong focus visibility.

Primary order:
- `Смотреть 1080p`
- alternative quality actions such as `Смотреть 720p` and `Смотреть 4K`
- secondary action such as `Трейлер`

The active playback option is visually emphasized. The focused item receives the strongest focus ring and lift effect.

Current functional compatibility should be preserved:
- the existing raw/open-link path should remain available somewhere in the secondary action model
- it does not need to compete visually with the primary playback path
- it can live in a lower-priority slot or compact overflow treatment as long as the capability is not removed

### 4. Bottom technical sheet

The bottom sheet is not a synopsis panel. It is a release-signal panel.

It contains:
- a compact section header
- current active quality badge
- a horizontal row of focusable technical cards
- a lower detail area that explains the currently focused card and mirrors action state

This sheet is meant to answer practical questions immediately:
- what quality is this
- what source is it
- what size is it
- what audio is it
- is TorrServe ready

## Focus and Navigation Model

### Entry focus

On screen open, focus lands on the primary playback action:
- `Смотреть 1080p` by default, or the best configured preferred quality if product logic later supports that

### Primary focus routes

Navigation should remain simple:
- `Up/Down` moves only inside the right action stack
- `Left` from the action stack moves into the technical card row
- `Left/Right` moves across technical cards
- `Up` from technical cards returns to the action stack

This creates a stable two-zone focus model:
- action zone
- technical data zone

The screen should avoid deep nested focus paths on first implementation.

## Content Strategy

### First-screen priority

The first screen prioritizes:
- playback actions
- quality choice
- technical release data
- service readiness

### De-prioritized content

Long descriptions, cast lists, or extended synopsis text should not dominate the first screen.

They may appear later as:
- secondary overlays
- expanded sections
- future drill-down surfaces

But they should not replace the technical sheet on the initial layout.

## Quality-aware behavior

Changing the selected quality should update the entire technical context, not just the button label.

Examples of affected technical cards:
- resolution and codec
- audio profile
- source type
- file size
- TorrServe readiness or warm-up state

This gives the quality controls real informational value and makes the screen feel responsive and purposeful.

## State Handling

### Playback and service states

Service feedback must stay lightweight and cinematic-safe.

Preferred treatment:
- compact status pills
- small toast-style feedback near the action area
- subtle active-state styling on the currently launching action

Avoid:
- oversized warning banners that split the composition
- full-width utility panels unless the state is critical

### Error and stale-data handling

The existing app behavior for stale or failed data can remain, but presentation should be adapted to fit the new composition:
- stale state becomes a restrained inline/pill treatment where possible
- actionable errors still remain readable and recoverable
- critical errors can still fall back to a dedicated error screen if necessary

## Visual Language

### Tone

The screen should feel:
- cinematic
- premium
- calm
- high-contrast
- authored rather than generic

### Styling cues

Recommended visual cues:
- dark atmospheric background with controlled glow
- large centered typography
- premium gold accent for the primary playback path
- cool blue accent for technical/focus surfaces
- soft glass or translucent surfaces for overlays
- clear, TV-legible focus rings and scale changes

### What to avoid

Avoid:
- plain utility rows as the main composition
- flat white cards on dark background
- overly dense metadata text blocks
- multiple competing accent colors
- tiny badges that are unreadable from couch distance

## Implementation Implications

This design implies UI work primarily in `DetailsScreen.kt`, with possible supporting extraction into focused UI helpers if the file becomes too dense.

Likely responsibilities:
- hero stage container
- action-stack composable
- technical card row composable
- compact state/toast presentation

The public screen contract should remain stable unless implementation reveals a strong reason to extend the UI model.

## Testing Strategy

Implementation should verify:
- initial focus lands on the primary playback action
- action-stack focus order is deterministic
- left/right movement across technical cards is deterministic
- quality changes update the visible technical data
- service or launch state remains visible without obscuring the layout
- the screen remains readable on real Android TV dimensions

Suggested validation:
- focused UI tests for navigation behavior
- screenshot/manual verification for key states
- debug/demo previews where possible for multiple quality profiles

## Success Criteria

- The details screen feels distinctly new and premium rather than like a small style refresh
- Playback is still the clearest first action on Android TV
- Technical release data is immediately available on the first screen
- Focus movement is simple enough to understand without training
- Secondary actions remain present without competing with `Смотреть`
- Status and service feedback fits the cinematic layout instead of breaking it
