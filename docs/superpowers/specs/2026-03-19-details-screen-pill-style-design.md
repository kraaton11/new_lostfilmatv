# Details Screen Pill Style Design

**Goal:** Adapt the current details screen to visually match the pill-style torrent-quality section from the provided `DetailsScreen(1).kt`, while preserving the current screen contracts and behavior already wired into navigation and buildable app flow.

## Scope

This change is limited to the details screen UI in `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`.

Included:
- Pill-style visual treatment for the torrent quality section
- Updated local palette and focus treatment used by that section
- Preservation of both `DetailsScreen` entry points, including the compatibility overload used by navigation
- Preservation of both actions: opening the raw link and opening through TorrServe

Excluded:
- Navigation changes
- ViewModel or state contract changes
- Repository or parsing changes
- Full replacement of the current screen with the downloaded file

## Design Decisions

### Keep the current public API

The current file has two `DetailsScreen` overloads:
- a compatibility overload used by navigation
- a richer overload used by the enhanced details UI

These overloads remain in place so we do not regress the app flow or reintroduce the earlier compile issue.

### Transfer style, not behavior removal

The downloaded file visually simplifies the quality section into horizontal pills and effectively stops using `onOpenLink`. We will only borrow its styling cues:
- compact horizontal pill presentation
- focus-driven borders and scaling
- lower-profile section heading
- TorrServe-centric visual emphasis

We will not remove the raw-link action. The resulting UI should still expose both supported actions.

### Use the existing data model

The current `DetailsTorrentRowUiModel` and `TorrServeMessage` models remain the source of truth. No new screen-level state is needed. Existing row ids, busy state, and message plumbing stay unchanged.

## UI Structure

The details screen keeps its current top hero layout and stale banner behavior.

Only the torrent section changes:
- Section heading becomes more compact and style-oriented
- Each torrent item uses a tighter, pill-influenced row presentation
- `Open link` remains available
- `TorrServe` keeps its active/busy styling
- Focus transitions remain deterministic for TV navigation

## Testing Strategy

- Rebuild `:app:assembleDebug`
- Run the existing focused details UI model test to ensure the compatibility mapping still works

## Success Criteria

- The details screen visually reflects the pill-style direction from `DetailsScreen(1).kt`
- The app still builds without changing navigation call sites
- The raw link and TorrServe actions are both still reachable
- No repository, parser, or state-layer files need to change
