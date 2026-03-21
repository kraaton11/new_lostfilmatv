# Default Playback Quality Settings Design

**Goal:** Simplify playback on Android TV details screens by replacing the per-quality action list with a single `Смотреть` action that uses a configurable default quality. The app should prefer `1080p` out of the box and fall back to the nearest available quality automatically when the preferred one is missing.

## Scope

This design covers:
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/`
- `app/src/main/java/com/kraat/lostfilmnewtv/navigation/`
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/`
- new playback-preferences classes under `app/src/main/java/com/kraat/lostfilmnewtv/`

Included:
- Single `Смотреть` action on the details screen
- User-configurable default playback quality in a dedicated settings screen
- Local persistence for the chosen default quality
- Automatic nearest-quality fallback when the preferred quality is unavailable
- Test coverage for quality resolution, settings persistence, and details-screen behavior

Excluded:
- Changes to LostFilm parsing and torrent-link fetching
- Changes to TorrServe integration behavior
- Multi-profile or cloud-synced settings
- Advanced playback options beyond default quality

## Product Intent

The current details screen asks the user to choose quality every time. On TV this adds friction and visual noise to the main action area. The new direction is action-first:
- one clear `Смотреть` button on details
- one place in the app to set playback preference
- automatic fallback so playback still works even when the preferred quality is missing

The user should not need to think about technical variants during routine browsing. Quality becomes a preference, not a repeated decision.

## Chosen Concept

### Dedicated settings + one playback action

The approved direction is:
- add a dedicated `Настройки` screen
- let the user choose a default quality there
- show only one `Смотреть` action on details
- resolve the actual torrent row before playback using the stored preference

This keeps the details screen calm while still leaving quality under user control.

## Navigation and UX

### 1. Home entry point

The home screen gains a `Настройки` button near the existing auth action so settings stay discoverable without introducing extra chrome inside details.

### 2. Settings screen

The new settings screen contains a single focused choice group for default playback quality:
- `1080p`
- `720p`
- `480p / SD`

Default for a fresh install:
- `1080p`

The screen should be TV-friendly:
- clear focused state
- simple vertical navigation
- immediate feedback for the selected option

### 3. Details screen

The details screen keeps the current simplified cinematic layout, but the right action area changes from a list of quality buttons to one action:
- `Смотреть`

That action always opens the row resolved from the saved preference and the available torrent rows for the current release.

The hero status line should reflect the actual chosen row so the user can see what will open, for example:
- `1080p • TorrServe • свежие данные`
- `720p • прямая ссылка`

If there are no playable rows, the primary action remains disabled.

## Quality Resolution Model

### Preference model

Represent default quality as a small explicit domain model rather than free-form strings:
- `Q1080`
- `Q720`
- `Q480`

This keeps storage, tests, and fallback logic stable even if source labels vary.

### Label normalization

Torrent row labels should be normalized into comparable quality buckets where possible:
- `1080`, `1080p`, similar full-HD labels -> `Q1080`
- `720`, `720p`, `MP4`, similar HD labels -> `Q720`
- `480`, `480p`, `SD` -> `Q480`

If a label cannot be mapped reliably, treat it as unknown instead of guessing aggressively.

### Fallback rules

Resolution order:
1. Try exact match for the saved preference
2. If missing, choose the nearest known available quality
3. If distance is tied, prefer the higher quality
4. If only unknown labels are available, choose the first available row

Examples:
- preferred `1080p`, available `720p` and `480p` -> choose `720p`
- preferred `720p`, available `1080p` and `480p` -> choose `1080p`
- preferred `480p`, available `720p` and `1080p` -> choose `720p`
- preferred `1080p`, available only `WEBRip` -> choose `WEBRip`

This guarantees the single action stays useful without requiring a manual fallback flow.

## State and Data Handling

### Local persistence

Store the default quality locally in lightweight app preferences. A small `SharedPreferences`-backed store is sufficient because:
- only one simple setting is needed
- no reactive multi-process or migration-heavy setup is required
- app startup wiring stays small

The store should expose:
- read current preference with `1080p` fallback
- write a new preference from settings

### App wiring

`LostFilmApplication` should own one playback-preferences store instance and provide it to:
- navigation for the settings screen
- details route for quality resolution

### Details route behavior

`DetailsRoute` should resolve a single active row from:
- saved default quality
- currently loaded torrent rows

This keeps the selection logic out of the composable layout and makes it easy to test independently.

Recommended helper:
- `resolvePreferredTorrentRow(preference, rows)`

The details screen should then render one action based on the resolved row, keeping focus and click behavior simple.

## Focus and TV Interaction

### Settings

Focus moves vertically through:
- back/navigation affordance if present
- quality options

The currently saved value should be visually marked even when not focused.

### Details

Initial focus still lands on the single `Смотреть` action.

Removing the vertical quality list simplifies the action area:
- no per-quality focus transitions
- no need to highlight alternate rows on focus
- one deterministic playback action

## Testing Strategy

Implementation should add or update coverage for:

### 1. Quality resolution logic

Unit tests for:
- exact preference match
- fallback to nearest lower quality
- fallback to nearest higher quality
- tie-breaking toward higher quality
- unknown-label fallback to first row
- empty rows returning no playable action

### 2. Preference storage

Unit tests for:
- default value is `1080p`
- saving and reading each supported quality

### 3. Settings UI

UI or composable tests for:
- current preference is shown as selected
- changing selection updates persisted value
- focus order stays TV-friendly

### 4. Details behavior

Route/screen tests for:
- details render only one primary watch action
- the chosen row matches the stored preference or nearest fallback
- TorrServe/direct-link behavior still follows the resolved row type
- hero status line reflects the resolved quality

## Success Criteria

- The details screen shows one clear `Смотреть` action instead of a per-quality list
- A fresh install defaults to `1080p`
- Users can change the default quality in a dedicated settings screen
- Playback automatically falls back to the nearest available quality when needed
- Existing TorrServe and direct-link behavior remains intact for the resolved row
- The TV flow becomes simpler without reducing playback reliability
