# App Update Settings Design

**Goal:** Add a TV-friendly updates section to the settings screen so users can see the installed version, check whether a newer APK exists, choose between manual checks and quiet automatic checks, and jump into the standard Android APK installation flow.

## Scope

This design covers:
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/`
- `app/src/main/java/com/kraat/lostfilmnewtv/navigation/`
- `app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt`
- lightweight local settings storage for update mode
- release metadata fetch from GitHub Releases

Included:
- showing the installed app version in settings
- checking the latest published GitHub release
- showing whether an update is available
- exposing two update modes: manual and quiet check
- showing a direct APK installation action when a newer release exists
- clear status and error messaging for update checks
- test coverage for state mapping, persistence, and settings behavior

Excluded:
- fully silent APK installation
- background APK download service
- changelog rendering
- delta patches or in-app binary diff updates
- non-GitHub release sources

## Product Intent

The settings screen already exists and is the right place for app-level preferences that do not belong in the browsing flow. Updates should feel visible and trustworthy without turning the TV app into a software-center UI.

The new direction is:
- let the user always see which version is installed
- let the user know whether a newer APK exists
- keep update behavior simple and explicit
- avoid promising a true silent install that normal Android TV devices cannot provide

The app should help the user discover updates, but Android should still own the final installation confirmation.

## Chosen Concept

### Settings-owned update block

The approved direction is:
- add a dedicated `Обновления` section to the existing settings screen
- keep `GitHub Releases` as the only update source
- support two modes:
  - `Ручной`
  - `Тихая проверка`
- treat `Тихая проверка` as automatic background checking, not silent installation

This matches the current release pipeline and avoids adding extra backend infrastructure.

## Update Source

### GitHub Releases

The app should read update metadata from the repository's latest GitHub release because the current CI already publishes signed APK assets there.

The update client should:
- request the latest release payload
- extract the release version identifier
- find the APK asset URL
- compare the remote version against the installed version from `BuildConfig`

If the release payload contains multiple assets, choose the first asset that is clearly an APK. If no APK asset is present, treat the release as unavailable for installation and surface a check failure state instead of a broken install action.

### Version comparison

The app should compare:
- installed version from `BuildConfig.VERSION_NAME`
- latest version from the release tag or release name, depending on which field the workflow guarantees

Comparison does not need full semver support. It only needs a stable equality/inequality decision that works with the current release naming convention produced by `.github/workflows/release.yml`.

## Settings UX

### 1. Update mode choice

The settings screen should add a new choice group labeled `Режим обновления` with two large TV-friendly buttons:
- `Ручной`
- `Тихая проверка`

Behavior:
- `Ручной`: the app checks for updates only when the user presses `Проверить обновления`
- `Тихая проверка`: the app automatically checks for updates when the settings screen opens

This mode only changes when checks happen. It does not change installation behavior.

### 2. Update information

The `Обновления` section should show:
- `Установлена версия`
- `Последняя версия`
- a one-line status message

Suggested status copy:
- `Проверяем обновления...`
- `Установлена актуальная версия`
- `Доступна версия X`
- `Не удалось проверить обновления`

### 3. Actions

The section should expose:
- `Проверить обновления`
- `Скачать и установить` when a newer APK is available

The install button must not appear when:
- the latest version is unknown
- the current version is already up to date
- the release exists but has no usable APK asset

## Installation Flow

### Standard Android install handoff

When the user presses `Скачать и установить`, the app should open the direct APK asset URL and hand off to the standard Android installation flow.

This design intentionally does not include:
- silent install APIs
- background installer ownership
- device-owner provisioning requirements

If the device blocks installation from unknown sources, the app should not hide that reality. Instead, it should keep the settings layout stable and show a short explanatory error or hint that installation permissions may be required.

## State Model

### Update states

The UI should model update checks explicitly, for example:
- idle
- checking
- up-to-date
- update-available
- error

`update-available` should include:
- latest version label
- direct APK URL

`error` should include a user-facing message appropriate for settings UI, not a raw exception dump.

### Settings ownership

`SettingsScreen.kt` should remain mostly a rendering layer. Update check logic should live in a small state holder or `SettingsViewModel` so that:
- automatic checks are easy to trigger once on entry
- button clicks do not mix networking into UI code
- state mapping stays unit-testable

## Local Persistence

### Update mode storage

Persist the chosen update mode alongside the existing playback preference storage rather than introducing a separate storage stack.

The store should support:
- reading the saved mode with a default
- writing a new mode immediately when the user changes it

Recommended default:
- `Ручной`

This keeps first-run behavior predictable and avoids unexpected network activity until the user opts into quiet checks.

## App Wiring

`LostFilmApplication` should own the update-related dependencies needed by navigation and settings, such as:
- update settings storage
- release metadata client or repository

`AppNavGraph.kt` should pass the stored update mode and update actions into the settings screen in the same spirit as the existing playback settings wiring. If the update block grows beyond a few callbacks, prefer moving settings state ownership into a dedicated view model instead of expanding nav-graph local state indefinitely.

## Failure Handling

The update flow should fail softly.

Cases to handle:
- no network connectivity
- GitHub request failure
- malformed release payload
- release without APK asset
- install intent cannot be opened

In all of these cases:
- keep the settings screen usable
- preserve the selected update mode
- show a short readable status message
- avoid modal error flows unless Android itself requires one

## Testing Strategy

Implementation should add or update coverage for:

### 1. Update mode persistence

Unit tests for:
- default mode is `Ручной`
- saving and reading both supported modes

### 2. Release parsing and state mapping

Unit tests for:
- latest release with APK asset becomes `update-available`
- matching version becomes `up-to-date`
- missing APK asset becomes an error or unavailable state
- malformed payload becomes an error state

### 3. Settings UI

UI tests for:
- installed version is rendered
- selected update mode is visibly marked
- switching update mode invokes persistence
- manual mode does not auto-check on first composition
- quiet mode triggers an automatic check on first composition
- install button appears only when a newer APK exists

### 4. Navigation wiring

Route or nav-graph tests for:
- opening settings still works from home
- changing update mode persists to application-owned settings storage
- quiet mode uses the update-check path when the settings screen is entered

## Success Criteria

- The settings screen shows the installed app version
- Users can choose between manual checks and quiet automatic checks
- The app can determine whether a newer GitHub release APK exists
- A newer release exposes a `Скачать и установить` action
- Installation still goes through the standard Android APK flow
- The UI never promises fully silent installation on unsupported devices
- Settings behavior is covered by focused tests
