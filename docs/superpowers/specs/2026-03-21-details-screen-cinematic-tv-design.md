# Cinematic TV Details Screen Design

**Goal:** Redesign the Android TV `Details` screen into a more cinematic but much more readable experience. The first screen must feel calm, action-first, and easy to parse from couch distance. Playback remains the primary action, while technical data stops competing for attention on the first screen.

## Scope

This design covers the visual and interaction model for the `Details` screen in:
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`

Included:
- Simpler cinematic hero composition for the details screen
- TV-first focus model with immediate access to `Смотреть`
- Short right-side action rail
- Compact first-screen status treatment
- Reduced metadata density for readability

Excluded:
- New network/API work or navigation changes
- New navigation destinations
- Description-first or synopsis-first layouts
- Full technical data sheet on the first screen

Addendum, 2026-03-21:
- The simplified hero may show one extra line for series episode title when that text already exists in cached summary metadata for the same `detailsUrl`.
- This does not change the status-line contract and does not add new first-screen actions.

## Product Intent

The previous direction proved too busy. The new design optimizes first for readability:
- fewer simultaneous blocks
- clearer visual hierarchy
- less text on the first screen
- stronger separation between `hero` content and `actions`

This is still a premium TV details screen, but it should now read in one glance instead of asking the user to scan multiple information surfaces.

## Design Direction

### Chosen concept: Hero + Action Rail

The approved direction is a two-zone composition:
- one calm hero zone for poster, title, and a single metadata line
- one compact right-side action rail for playback and quality selection

The bottom technical sheet is removed from the first screen.

### Why this direction

Compared with the previous `Floating Stage + info sheet` direction, this approach:
- removes one full competing information block
- makes the screen readable faster
- keeps the first action obvious
- preserves the premium feel without visual overload

## Layout Structure

### 1. Minimal top edge

The top edge becomes intentionally quiet:
- no focusable chrome above the hero
- no inline `Назад` button
- system `Back` on the remote remains the only exit control

Avoid multiple chips, badges, or stacked metadata in this zone.

### 2. Hero zone

The left or center-left side holds the content identity:
- poster
- title
- one short metadata line
- optional episode-title line for series
- one short status line

The hero should not include dense chip rows or a secondary information deck.

Recommended metadata line examples:
- `Сезон 9, серия 13`
- `14 марта 2026`
- `Сериал`

Recommended episode-title examples:
- `Маменькин сынок`
- `Pilot`

Recommended status line examples:
- `1080p • TorrServe • кэш свежий`
- `720p • прямая ссылка`
- `Ошибка TorrServe`

This line replaces the need for a separate technical information surface on first open.

### 3. Right action rail

The right side contains a short vertical action rail with strong focus clarity.

Primary order:
- active `Смотреть`
- alternative quality actions below

The rail should remain visually lighter than before:
- fewer decorative layers
- tighter spacing
- short labels
- one clear primary state

### 4. Removed from first screen

The following elements are removed from the initial visible layout:
- bottom technical sheet
- focusable technical cards
- secondary explanatory panel for the focused card
- extra status panels that duplicate the action state

If richer technical details are still needed later, they should appear in:
- a secondary overlay
- an expanded state
- a later interaction step

They should not be present by default on first load.

## Focus and Navigation Model

### Entry focus

On screen open, focus lands on the primary playback action:
- `Смотреть 1080p` by default, or the first available preferred quality

### Primary focus routes

Navigation should become simpler than the previous two-zone model:
- `Up/Down` moves within the right action rail
- `Up` from the first action stays on the first action
- no first-screen left/right dependency on a lower technical zone

This creates a very predictable focus model:
- vertical action rail
- system `Back` to leave the screen

The hero remains informational and visually stable, not part of the initial focus loop.

## Content Strategy

### First-screen priority

The first screen now answers only:
- what this title is
- what the main watch action is
- what current playback/status context applies

### De-prioritized content

The following content moves out of the first-screen priority layer:
- full technical card breakdown
- long explanation blocks
- multiple concurrent badges
- auxiliary information that does not help the immediate watch decision

## State Handling

### Playback and service states

State feedback should appear as one compact line close to the active action or hero metadata.

Preferred treatment:
- short inline status text
- one restrained error line when needed
- subtle disabled state for unavailable TorrServe actions

Avoid:
- large warning panels
- dedicated lower response surfaces
- repeated status messaging in multiple places

### Error and stale-data handling

The existing app behavior for stale or failed data can remain, but presentation should stay compact:
- stale state becomes one short inline indicator
- TorrServe issues become one readable status line
- critical errors can still fall back to a dedicated error screen

## Visual Language

### Tone

The screen should feel:
- cinematic
- calm
- sparse
- high-contrast
- easy to scan

### Styling cues

Recommended visual cues:
- dark atmospheric background
- one strong poster focal point
- large readable title
- restrained accent usage
- one dominant primary action color
- minimal decorative chrome around secondary information

### What to avoid

Avoid:
- multiple concurrent information surfaces
- wide lower panels on first screen
- dense chip clusters
- duplicated status text
- any layout that feels like four widgets competing for attention

## Implementation Implications

This revision simplifies `DetailsScreen.kt`:
- remove the first-screen technical sheet
- remove technical-card focus handling from the main path
- collapse visible metadata into one short line plus one status line
- keep the action rail and its existing playback compatibility

The public screen contract should remain stable if possible.

## Testing Strategy

Implementation should verify:
- initial focus lands on the primary playback action
- action-rail focus order is deterministic
- there is no onscreen `Назад` or `Открыть ссылку` control
- the compact status line updates for busy/error/fallback states
- the simplified screen still preserves direct-link rows and TorrServe behavior when provided

Suggested validation:
- focused UI tests for action-rail navigation
- screenshot/manual verification for readability
- real-device spot checks for couch-distance legibility

## Success Criteria

- The first screen is readable in one glance
- There are only two dominant visible zones: hero and actions
- Playback remains the clearest first action on Android TV
- Technical or service context is still available, but only as compact inline text
- The screen feels calmer and less crowded than the previous cinematic version
