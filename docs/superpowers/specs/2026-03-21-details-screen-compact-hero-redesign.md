# Details Screen Compact Hero Redesign

**Goal:** Remove the empty-feeling composition on the Android TV `Details` screen by collapsing the screen into one compact hero cluster and one minimal bottom information strip. The first screen should feel dense, calm, and immediately actionable from couch distance.

## Scope

This design covers:
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt`
- focused tests for the details screen UI and state mapping

Included:
- Replacing the current wide hero plus empty right-side composition with a compact two-part layout
- Keeping `Смотреть` as the single primary action
- Removing first-screen copy that does not help the immediate watch decision
- Adding a minimal non-focusable bottom information strip
- Updating details-state copy so it matches the simplified visual design

Excluded:
- Navigation changes
- Playback-quality resolution changes
- New metadata fetching
- Description or synopsis support
- Additional settings or actions on the details screen

## Product Intent

The current details screen no longer has multiple quality buttons, but it still spends too much space on composition rather than content. A single button now sits in a dedicated side area that makes the screen feel sparse on TV.

The new direction is denser and simpler:
- poster, title, subtitle, and `Смотреть` belong to one visual cluster
- the first screen should not explain technical implementation details
- the lower part of the screen should only gently close the composition, not introduce a second content deck

The user should understand the screen in one glance:
- what title they opened
- what the main action is
- one short line of useful context

## Design Direction

### Chosen concept: Compact hero + minimal bottom strip

The approved direction is:
- use the visual spirit of option `A` from brainstorming
- remove the separate right action column
- keep a compact hero block near the poster
- keep one thin information strip at the bottom

This replaces the current layout where the button area visually floats away from the main content.

### Why this direction

Compared with a larger lower info block or a more cinematic poster-dominant layout, this direction:
- removes the most obvious empty space
- keeps the screen TV-friendly and readable
- preserves a strong single action
- avoids inventing filler content while descriptions are still unavailable

## Layout Structure

### 1. Compact hero cluster

The visible first screen becomes one dense content cluster:
- poster on the left
- title and optional episode subtitle on the right
- `Смотреть` directly inside the same content group

The button must no longer live in its own far-right rail or detached column.

The cluster should read as one unit rather than three independent zones.

### 2. Reduced hero height

The hero area should become noticeably shorter than the current tall cinematic block.

The first screen should still feel premium, but not stretched. Background gradients and glow can remain, yet they must support the content instead of creating large empty fields.

### 3. Minimal bottom information strip

Below the hero, keep one narrow information strip spanning the width of the screen.

This strip is non-focusable and exists only to:
- finish the composition
- remove the feeling of empty space
- show one short line of useful context or one short playback status

It should not become a card deck, metadata sheet, or second hero.

## Content Rules

### 1. Keep only first-screen essentials

The hero should show:
- title
- series episode subtitle when available
- `Смотреть`

Optional contextual line placement may be used sparingly if needed, but the screen should stay visually restrained.

### 2. Remove non-essential first-screen copy

The first screen should not show:
- release date
- synopsis or description
- service-brand mentions such as `TorrServe`
- decorative filler text

This information either does not help the immediate watch decision or is not available yet.

### 3. Bottom strip content

The bottom strip carries one short line.

Series example:
- `Сезон 9 • Серия 13 • 1080p`

Movie example:
- `Фильм • 1080p`

If the release type already makes one fragment redundant, prefer the shortest readable version.

## Focus and Navigation Model

### Entry focus

Initial focus still lands on the single `Смотреть` action.

### Focusable elements

On first open, the screen should have one obvious focus target:
- `Смотреть`

The bottom information strip must not receive focus.

This keeps TV navigation simple and prevents the user from wandering into passive UI.

### Back behavior

System `Back` remains the exit path. No additional first-screen back chrome is introduced as part of this redesign.

## State Handling

### Normal available state

When playback is available:
- `Смотреть` is enabled
- the bottom strip shows short contextual info such as `Сезон 9 • Серия 13 • 1080p`

### No playable source

When no playable row is available:
- `Смотреть` remains in place but disabled
- the bottom strip shows a short neutral message such as `Видео недоступно`

### Busy state

When playback is being opened:
- `Смотреть` becomes temporarily disabled
- the bottom strip shows `Открывается...`

### Error state

When playback launch fails:
- the hero layout does not expand or add a new panel
- the bottom strip reuses the same space for the short error message

This keeps all transient states inside one stable layout.

## Visual Language

### Balance

The new screen should feel:
- compact
- premium
- high-contrast
- calm
- intentionally sparse, but not empty

### Poster and text proportions

The poster may grow slightly in perceived importance, but it should not dominate the screen.

The text block should stay visually close to the poster. The watch button should sit inside the text cluster, not off to the side.

### Bottom strip treatment

The bottom strip should be quiet:
- thin
- clean
- no card segmentation
- no heavy border treatment

It should read as a finishing line, not as a second panel.

## Implementation Implications

This redesign likely requires:
- restructuring `ContentState` in `DetailsScreen.kt` to remove the detached right-side action column
- tightening `HeroStage` spacing and dimensions
- moving short status/context output into a dedicated bottom strip
- simplifying state copy in `DetailsStageModels.kt` so first-screen text no longer mentions `TorrServe` or release date

The public screen contract should stay stable if possible. This is a layout and content-priority redesign, not a new screen flow.

## Testing Strategy

Implementation should verify:
- initial focus still lands on `Смотреть`
- the screen no longer shows `TorrServe` copy
- the screen no longer shows release-date copy on first open
- the bottom strip shows the short normal-state context for series and movies
- busy, disabled, and error states reuse the same bottom strip area
- the lower strip is not treated as an additional focus surface

Suggested validation:
- update focused UI tests for visible copy
- keep route/model tests aligned with the shorter status strings
- real-device TV spot check for density and readability

## Success Criteria

- The details screen no longer feels dominated by empty space
- Poster, title, subtitle, and `Смотреть` read as one compact hero cluster
- There is no detached right-side action rail
- The first screen does not show release date, description, or `TorrServe` branding
- The bottom of the screen is closed by one minimal information strip instead of a large content block
- TV navigation remains simple, with `Смотреть` as the immediate focus target
