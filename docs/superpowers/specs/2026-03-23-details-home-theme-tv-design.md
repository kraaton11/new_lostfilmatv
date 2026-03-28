# Details Home Theme TV Design

## Goal

Bring the Android TV `Details` screen into the same visual and interaction system as the refreshed `Home` screen while keeping `Details` more cinematic and fully watch-first.

The redesign should make the first screen readable in one glance from couch distance:
- what title is open
- what episode or kind it is
- what the primary watch action is
- what short playback or service state currently applies

## Scope

This design covers:
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsStageModels.kt`
- shared theme tokens in `app/src/main/java/com/kraat/lostfilmnewtv/ui/theme/`
- focused details tests in `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/`
- details UI tests in `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt`

Included:
- visual unification of `Details` with the refreshed `Home` surface language
- a simpler three-layer details composition with a cinematic hero and a `Home`-style bottom stage
- explicit Android TV focus behavior for the primary watch action
- unified stale, loading, disabled, and error presentation
- pruning duplicated first-screen text so the hero stays compact

Excluded:
- new navigation destinations
- playback-quality selection UI beyond the single resolved primary action
- synopsis or long-description support
- new metadata fetching or repository changes
- changes to playback business logic or TorrServe integration rules

## Product Intent

The current `Details` screen already moved toward a calmer cinematic layout, but it still feels like a separate product surface:
- it uses a duplicated `Details` token set that largely mirrors `Home`
- state messaging lives in its own layout treatment instead of the shared TV status language
- the visual rhythm does not match the `Home` pattern of quiet top, clear primary content, and stable lower stage

The new direction should make `Details` feel like the second half of the same Android TV experience:
- `Home` is the browsing surface
- `Details` is the confirmation-and-watch surface

The goal is not to copy `Home` literally. The goal is to reuse the same visual grammar and remote-navigation logic so the user never has to re-learn the screen.

## Approved Direction

### Chosen concept: Home-aligned cinematic details

The approved direction is to keep `Details` watch-first and quiet at the top while restructuring it around the same composition principles as `Home`:
- layered atmospheric background
- one focused upper content block
- one wide bottom stage surface
- restrained status panels
- deterministic D-pad focus

This preserves a stronger hero than `Home`, but removes the feeling that `Details` belongs to a different theme family.

### Why this direction

Compared with a light visual restyle only, this direction:
- creates a more obvious family resemblance with `Home`
- improves couch-distance readability through a clearer top-to-bottom scan
- simplifies TV navigation by reducing focusable zones
- gives service and playback status one stable place to live

Compared with copying `Home` too literally, it still leaves `Details` with:
- a larger poster-driven hero
- quieter chrome
- a more cinematic first impression

## Layout Structure

### 1. Quiet top edge

The top of the screen remains intentionally quiet:
- no settings or auth buttons
- no inline back button
- no detached utility controls

System `Back` remains the only first-screen exit path.

This keeps `Details` fully focused on the watch decision instead of reintroducing `Home`-style service actions in the top row.

### 2. Layered background

The screen should use the same general palette family already established on `Home`:
- dark blue-to-charcoal vertical gradient
- poster image in the background at restrained opacity
- soft horizontal and vertical overlays for readability

The result should feel related to `Home`, not like a separate color story.

The background should support legibility first. It must not create empty-looking space or overpower text.

### 3. Upper hero block

The upper block contains the identity and action cluster:
- poster on the left
- title on the right
- optional episode title below the main title for series
- one compact metadata line
- primary `Смотреть` button

This block should stay visually dense enough to feel intentional, but not crowded.

Recommended metadata examples:
- `Сезон 9 • Серия 13`
- `Фильм`

Release date does not belong in the hero block.

### 4. Bottom stage

The lower part of the screen becomes a wide stage surface in the same family as `HomeBottomStage`:
- rounded shared TV surface treatment
- shared border language
- similar spacing rhythm
- two information columns when content warrants it

The bottom stage becomes the stable home for short playback and service context.

This stage replaces the current dedicated bottom strip treatment and avoids scattering status text between multiple custom surfaces.

## Content Strategy

### 1. Hero content stays short

The hero should answer only:
- what this title is
- what episode or media kind it is
- where to press to start playback

Hero content should include:
- title
- optional episode title
- one metadata line
- primary action

Hero content should not include:
- release date
- `TorrServe` branding
- service explanations
- repeated quality and status copy that also appears in the bottom stage

### 2. Bottom stage owns short context

The bottom stage carries the short contextual line that helps the immediate watch decision.

Primary examples:
- `1080p • TorrServe`
- `Открывается...`
- `Видео недоступно`
- `Не удалось открыть TorrServe`

Secondary examples for the right side when helpful:
- release date
- stale-data hint
- short service context

The stage should not turn into a technical fact sheet. It remains compact and readable.

### 3. Avoid duplication

The redesign should explicitly avoid repeating the same information in both hero and stage.

Recommended split:
- hero: identity and primary action
- bottom stage: short playback or service state

If a line already appears in the bottom stage, the hero should not repeat it unless the duplication clearly improves readability.

## Focus And Navigation Model

### Context from official Compose guidance

Official Compose focus guidance reviewed through Context7 supports:
- `FocusRequester` for explicit initial focus
- `focusProperties` for overriding two-dimensional traversal
- explicit directional wiring for D-pad and keyboard navigation

The redesign should use those patterns instead of relying on default nearest-neighbor focus search in a sparse TV layout.

### Entry focus

On screen open, focus lands on the primary `Смотреть` action:
- enabled playback: focus the primary watch button
- unavailable playback: focus the same disabled primary action surface if it remains focusable in current implementation, or the nearest stable fallback if the component family does not support disabled focus

The user should never land on a decorative hero element first.

### Focus zones

The first-screen focus model should stay intentionally small:
- primary action in the upper hero block
- no focus on poster, title, banners, or passive status text
- no focus on the bottom stage in the initial implementation

This makes the screen predictable and keeps the remote interaction centered on `Смотреть`.

### Directional rules

Recommended routing:
- `up` from the primary button stays on the primary button
- `left` and `right` from the primary button do not jump into decorative space
- `down` only leaves the button if a future focusable action is added below

If the bottom stage remains informational, the first version should effectively behave as a single-action screen with explicit no-op directional exits where needed.

### Back behavior

System `Back` returns to the previous destination and should preserve the focused poster on `Home`, matching existing navigation expectations.

## State Presentation

### Loading

Loading should remain a dedicated centered state, but it should visually match the shared TV surface language:
- centered progress indicator
- optional panel treatment aligned with `HomeCenteredPanel` styling
- no extra decorative copy

### Error

Critical screen-level errors should still fall back to a dedicated full-screen error state with:
- one readable message
- one `Повторить` action
- calm shared-surface styling

This should feel like the `Home` error treatment instead of a one-off details error layout.

### Stale data

Stale-data messaging should use the same status-panel family already used on `Home`.

The stale state belongs near the upper content block, not in an unrelated bespoke banner style.

### Playback busy, unavailable, and launch failure

Transient playback states should not create new layout regions.

Instead:
- `Открывается...` lives in the bottom stage
- `Видео недоступно` lives in the bottom stage
- launch errors such as `Не удалось открыть TorrServe` live in the bottom stage

This keeps the layout stable while content changes.

## Visual Language

### Shared token direction

The redesign should reduce duplicated token ownership between `Home` and `Details`.

Preferred direction:
- reuse shared TV tokens where the value is already identical
- only keep `Details`-specific tokens where the screen truly needs a unique role

The intent is not a risky full theme rewrite, but the screen should stop carrying a parallel near-copy of the `Home` palette when the visual system is the same.

### Surface treatment

Surfaces should feel consistent across `Home`, `Settings`, and `Details`:
- dark layered fills
- soft white borders at low opacity
- gold as the primary persistent action color
- blue as the focus accent
- readable secondary text in the same family

### Hero balance

`Details` should remain slightly more cinematic than `Home`, but not more complex.

That means:
- larger poster emphasis than `Home`
- more atmospheric background than `Home`
- no extra chips, decks, or utility chrome

The screen should feel premium because of composition and restraint, not because of more UI.

## Architecture And Responsibilities

### `DetailsScreen`

Owns:
- overall composition
- hero layout
- bottom stage placement
- focus requesters and directional focus rules
- visual presentation of loading, error, and stale states

### `DetailsStageModels`

Owns:
- hero metadata string
- bottom stage status string
- resolved primary action model

The stage model should be simplified so the hero and bottom stage each receive the right level of content without duplication.

### Theme layer

Owns:
- shared TV color and surface tokens used by `Home`, `Settings`, and `Details`
- any narrowly scoped additional token needed for the more cinematic details background

The theme layer should make it obvious that all three screens belong to one TV product.

## Testing Strategy

Implementation should follow TDD and update tests before production changes.

### 1. Layout and content hierarchy

Tests should verify:
- the primary watch action is visible and remains the initial focus target
- the screen renders a bottom stage surface in the new shared visual structure
- the hero shows title, optional episode title, and compact metadata
- release date is not shown in the hero
- status and playback context live in the bottom stage instead of a separate strip

### 2. Focus behavior

Tests should verify:
- initial focus lands on `Смотреть`
- no upper utility actions are rendered on `Details`
- `Back` still restores the previously focused poster on `Home`
- directional navigation does not escape into passive elements

If some directional assertions are easier in `androidTest`, coverage may be split between local and instrumentation tests.

### 3. State handling

Tests should verify:
- loading state remains visible without partial content
- stale-data messaging appears in the shared status-panel style
- busy state disables or updates the primary watch action as expected while using the bottom stage for status
- no-playback state keeps the screen stable and readable
- launch failure state reuses the same bottom stage area

### 4. Layout safety for TV viewports

Tests should verify:
- the bottom stage stays above the bottom edge in TV-sized viewports
- long titles or episode names do not visually overflow the stage or hero
- the hero-to-stage spacing remains readable in common Android TV resolutions

## Risks And Mitigations

### Risk: the screen becomes too much like `Home`

Mitigation:
- keep the quiet top edge unique to `Details`
- preserve a larger poster-driven hero
- avoid adding `Home` service actions or list-style affordances

### Risk: shared token work becomes broader than necessary

Mitigation:
- consolidate only the obviously duplicated tokens first
- keep any remaining details-only background accents narrowly scoped
- avoid expanding this task into a full app-wide theme migration

### Risk: focus behavior regresses when the layout is simplified

Mitigation:
- explicitly wire `FocusRequester` and `focusProperties`
- keep the number of focus targets minimal
- preserve and extend existing focus regression tests

### Risk: state messaging becomes too compressed

Mitigation:
- keep the hero short and allow the bottom stage to carry brief status lines
- prefer one stable readable line over multiple competing panels
- keep critical screen-level failures as full-screen error states

## Success Criteria

- `Details` clearly feels like part of the same visual system as `Home`.
- The screen remains watch-first and keeps a quiet top edge without utility chrome.
- The hero answers identity and action, while the bottom stage owns short playback and service context.
- Android TV focus behavior is explicit and predictable.
- Loading, stale, unavailable, and launch-failure states reuse a stable presentation model.
- The screen reads comfortably from couch distance without duplicated text or scattered status surfaces.

## Next Step

After the user reviews and approves this document, the next step is to write a focused implementation plan for the `Details` home-theme TV refresh.
