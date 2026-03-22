# Home Season And Episode Card Overlay Design

## Overview

The `Home` screen currently shows season and episode metadata only in the bottom information panel under the poster rail.

The new requirement is to move `Сезон N, серия M` onto the poster card itself so the release rail communicates this information directly. The metadata should appear at the bottom of the card and should no longer be repeated in the lower info panel.

This design keeps the current `Home` rail structure, focus behavior, and card sizing intact.

## Scope

This design covers:
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/components/PosterCard.kt`
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/BottomInfoPanel.kt`
- `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/`
- `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/`

Included:
- rendering season and episode text on series poster cards
- placing that text at the bottom of the card as an overlay
- removing the duplicated season and episode row from the bottom info panel
- preserving existing movie behavior
- preserving existing watched badge, focus, and rail sizing behavior
- updating or adding UI tests for the new metadata placement

Excluded:
- changes to `HomeRail` sizing or spacing
- changes to release parsing or data models
- changes to movie metadata presentation
- new card metadata beyond season and episode
- redesign of the rest of the `Home` screen

## Product Intent

The season and episode number is useful at the moment the user scans the poster row, not only after they look down to the separate information panel. Moving this metadata onto the card makes episode context visible earlier and reduces the need to split attention between the focused rail item and the lower text block.

The lower panel should remain useful but less repetitive. It should keep the title, episode title when available, and release date, while the card itself becomes the owner of the compact season and episode label.

## Approved Concept

### Bottom overlay on series cards

The approved direction is:
- show `Сезон N, серия M` directly on the poster card
- position it at the bottom of the poster
- render it as a dark, semi-transparent overlay over the image
- keep it visible on the card, not only in focused state
- remove the same row from the lower information panel

This preserves the current card geometry and avoids reshaping the rail for a footer layout.

## Component Design

### 1. `PosterCard`

`PosterCard` becomes responsible for rendering season and episode metadata for series items.

Rendering rules:
- show the overlay only when:
  - `item.kind == ReleaseKind.SERIES`
  - `item.seasonNumber` is present
  - `item.episodeNumber` is present
- do not render the overlay for movies
- do not render the overlay for incomplete series metadata

Visual rules:
- anchor the overlay to the bottom edge of the card
- use a dark translucent background so the text remains readable against bright posters
- keep the text compact and TV-readable
- keep the existing watched badge in the top-right corner unchanged

The overlay should live inside the existing clipped card bounds so no size or border behavior changes are required.

### 2. `BottomInfoPanel`

`BottomInfoPanel` should stop rendering the season and episode row.

The panel should continue to show:
- release title
- episode title for series when available
- release date

This keeps the panel focused on descriptive context rather than repeating metadata already visible in the rail.

### 3. `HomeRail`

`HomeRail` should not change size, spacing, focus logic, or navigation wiring for this feature.

This is an intentionally local UI adjustment. The release row must continue to behave exactly as it does today from a remote-navigation perspective.

## State And Data Usage

No new state or data transformation is required.

The feature reuses existing `ReleaseSummary` fields:
- `kind`
- `seasonNumber`
- `episodeNumber`

Suggested formatting remains:
- `Сезон 9, серия 13`

Formatting should be created close to the UI that renders it so this feature stays local and does not introduce unnecessary model changes.

## Focus And Interaction

The overlay is informational only.

It must not:
- introduce a new focus target
- interfere with card click behavior
- change the current focused-card scaling or border treatment

The watched badge and the new season/episode overlay must coexist without overlap because they occupy opposite vertical edges of the card.

## Error Handling And Edge Cases

Handle these cases quietly:
- movie card: no overlay
- series card without season number: no overlay
- series card without episode number: no overlay
- empty or unexpected poster art: keep current image behavior and still allow overlay rendering if metadata exists

This feature should never block poster rendering or cause layout instability because of missing metadata.

## Testing Strategy

Implementation should add or update coverage for:

### 1. Series card metadata

UI tests for:
- a series item renders `Сезон N, серия M` on `Home`
- the season and episode text remains visible when the item is present in the rail

### 2. Bottom panel regression

UI tests for:
- the bottom info panel no longer duplicates `Сезон N, серия M`
- the lower panel still shows title, episode title, and release date

### 3. Non-series regression

UI tests for:
- movie cards do not render the season and episode overlay
- existing focus movement and selected-item updates still work

## Success Criteria

- Series poster cards on `Home` show `Сезон N, серия M` at the bottom of the card.
- The overlay is readable on top of poster art.
- The lower information panel no longer repeats the season and episode row.
- Movie cards remain unchanged.
- Card sizing, rail spacing, and TV remote navigation remain unchanged.

## Next Step

After the user reviews and approves this document, the next step is to write a focused implementation plan for the `Home` card season and episode overlay.
