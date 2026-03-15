# LostFilm Android TV Authentication Design

## Overview

This document defines the `v1` authentication design for the LostFilm Android TV application.

The application already supports anonymous browsing of new releases parsed directly from LostFilm. This design adds account sign-in through a QR-based flow and surfaces personal watched-state information without breaking anonymous use.

## Goals

- Add user authentication to the Android TV application.
- Use a TV-friendly QR login flow instead of manual credential entry on the TV.
- Keep anonymous browsing fully usable when the user is not signed in.
- Show personal watched-state data after a successful login.
- Support the full session lifecycle:
  - sign in
  - sign out
  - expired-session detection
  - re-login prompt
- Reuse the existing auth bridge as the pairing service.

## Non-Goals For V1

- No permanent proxying of LostFilm content traffic through the backend.
- No full personal profile UI.
- No account switching or multiple user profiles on one device.
- No requirement to restore an in-progress QR pairing after the app process is killed.
- No blocking of core content behind authentication.

## Product Decisions

- Authentication outcome for `v1`: session storage plus watched-state display.
- Login method: QR code on TV, sign-in on phone, one-time session handoff to TV.
- Session lifecycle scope: login, logout, expired-session detection, re-login prompt.
- User entry points:
  - a visible sign-in call to action on the home screen
  - a duplicated account section in settings
- Anonymous behavior:
  - the app continues to work normally without login
  - personal watched-state is hidden when there is no valid session

## Selected Technical Approach

### Chosen Approach

Use the backend auth bridge only for device pairing and one-time transfer of a valid LostFilm web session to the Android TV app. After login, the TV app stores the session locally and makes direct requests to LostFilm.

### Why This Approach

This keeps the TV login experience usable, avoids remote-control credential entry, and prevents the backend from becoming a permanent traffic proxy. It also lets LostFilm see the user's normal network path after login instead of a shared backend IP.

### Rejected Alternatives

- Permanent backend proxy for authenticated requests:
  - rejected because it increases operational risk, centralizes user traffic unnecessarily, and may create IP-related issues with LostFilm.
- TV-only username/password form:
  - rejected because it is poor TV UX and would likely be fragile if the web login flow changes.
- Manual cookie import as the primary method:
  - rejected because it is a poor end-user experience compared with QR sign-in.

## High-Level Architecture

The authenticated design adds a new auth layer beside the existing anonymous content pipeline rather than folding everything into the existing repository.

### Main Units

- `AuthRepository`
  - owns authentication state
  - starts pairing
  - polls pairing status
  - claims and stores the LostFilm session
  - clears session on logout
  - marks session expired when needed
- `AuthBridgeClient`
  - talks to the backend pairing API
- `SessionStore`
  - stores the LostFilm session securely on-device
- `AuthenticatedLostFilmHttpClient` or auth-aware `CookieJar`
  - attaches LostFilm cookies to authenticated requests
- `WatchStateRepository`
  - loads and stores personal watched-state
  - merges personal status with existing anonymous content data
- `AuthViewModel` and related settings/home state owners
  - drive QR login UI
  - expose login/logout/expired state to the screens

### Architectural Boundaries

- The backend is only responsible for:
  - creating pairings
  - hosting the phone-side pairing flow
  - exposing pairing status
  - one-time transfer of the LostFilm session payload
- The Android TV app is responsible for:
  - storing the session
  - making direct authenticated requests to LostFilm
  - reading watched-state
  - reacting to session expiry
- The existing content repository remains the primary source of anonymous release data.
- Personal watched-state is layered on top of anonymous content rather than replacing the base repository model.

## TV User Experience

## Entry Points

The user can start authentication from:

- a visible `Sign In` action on the home screen
- a duplicated account entry in settings

## Anonymous State

When there is no valid session:

- home and details continue to work
- the app shows content normally
- watched-state indicators are hidden
- the account area explains that personal status requires sign-in

## QR Login Flow

When the user chooses to sign in:

1. The TV opens a dedicated authentication screen.
2. The app creates a pairing with the backend.
3. The screen shows:
   - QR code
   - short user code
   - waiting state text
4. The app polls pairing status in the background.
5. On success:
   - the app claims the session
   - stores it locally
   - shows a brief success state
   - returns the user to the previous context

## Expired Pairing UX

If the QR pairing expires before completion:

- the screen shows an explicit expired state
- the user gets a clear `Try Again` or `Show New QR` action
- no global app state is broken

## Logged-In State

When login succeeds:

- home and details remain otherwise unchanged
- personal watched-state appears where supported
- settings shows that the account is connected
- settings provides a `Sign Out` action

## Expired Session UX

If the LostFilm session becomes invalid later:

- anonymous content remains available
- personal watched-state disappears
- the app shows a soft prompt such as `Session expired, sign in again`
- the same QR login flow is used for re-login

## Logout UX

When the user signs out:

- the app clears local session data
- clears personal watched-state cache
- immediately returns to anonymous mode

## Local Data Model

Authenticated data is intentionally separated from the anonymous content cache.

## Session Storage

The LostFilm web session is stored outside Room in a secure local store.

### Session Fields

- session cookies
- `issuedAt`
- `lastValidatedAt`
- `isExpired`
- optional stable account identifier if one can be derived safely

### Storage Rules

- store session data in encrypted local storage
- back storage with Android Keystore
- do not place raw cookies in Room tables with content data

## Pairing State

Active QR pairing state may be held as transient screen state for `v1`.

### Pairing Fields

- `pairingId`
- `userCode`
- `verificationUrl`
- `expiresAt`
- `pollIntervalSeconds`
- `status`

For `v1`, in-progress pairing restoration after app process death is not required.

## Watched-State Storage

Personal watched-state is stored in a dedicated table and never merged into the anonymous content tables at rest.

### Watched-State Fields

- `detailsUrl` or equivalent stable release key
- `accountHash`
- `watchState`
  - `UNKNOWN`
  - `WATCHED`
  - `UNWATCHED`
- `fetchedAt`

### Data Separation Rules

- anonymous release summaries and details remain intact when login state changes
- logout clears personal watched-state only
- base content cache survives logout and session expiry

## Network And API Design

## Backend Pairing API Usage

The Android TV app uses the backend only during login.

### Required Calls

- `POST /api/pairings`
  - create pairing
  - receive `pairingId`, `userCode`, `verificationUrl`, `expiresIn`, `pollInterval`
- `GET /api/pairings/{pairingId}`
  - poll status
  - expect states such as `pending`, `confirmed`, `expired`
- `POST /api/pairings/{pairingId}/claim`
  - claim a one-time session payload after pairing confirmation

## Session Handoff

After `claim`, the backend returns a session payload containing the LostFilm cookies required for authenticated requests.

The TV app then:

1. stores the session securely
2. activates authenticated request handling
3. stops using the backend for ordinary content traffic

## Authenticated LostFilm Access

After login, the TV app talks directly to LostFilm.

This direct flow is used for:

- release pages
- details pages
- watched-state extraction

The backend is not part of ordinary content browsing after the session is claimed.

## Watched-State Retrieval

The app reads personal watched-state from LostFilm pages or other authenticated responses available to the signed-in session.

If the required personal markup is absent where it should exist, the app treats the session as suspect and validates expiry behavior through repository rules.

## Repository Integration

The existing anonymous repository remains responsible for release content.

The personal layer should:

- fetch watched-state separately
- merge it into UI-facing models
- avoid mutating anonymous cache tables as a side effect of login

## Error Handling

## Pairing Errors

If the backend pairing flow fails to start:

- the login screen shows a retryable error
- the rest of the app remains usable anonymously

If polling fails temporarily:

- the login screen remains on the same page
- the user sees a clear status and retry path

## Session Expiry Detection

The app should mark the session expired when authenticated requests consistently behave like anonymous requests or when expected personal data disappears in a context where it should exist.

When this happens:

- clear the active session state
- stop showing watched-state
- keep anonymous content visible
- prompt for re-login

## Parse Failures In Personal Data

If personal watched-state cannot be parsed:

- do not break the home or details screens
- fall back to `UNKNOWN` watched-state
- avoid inventing values

## Backend Unavailability

If the auth bridge is down:

- login cannot start or complete
- anonymous browsing still works
- existing valid local sessions remain usable until they expire

## Security And Privacy

- Store LostFilm session data encrypted on-device.
- Do not persist raw cookies in Room content tables.
- Treat the backend session handoff as one-time and short-lived.
- The backend should not become a permanent content proxy for authenticated browsing.
- Logout must remove locally stored session material and personal watched-state cache.

## Testing Strategy

## Unit Tests

Add tests for:

- `AuthRepository`
- pairing state transitions
- session expiry transitions
- logout behavior
- `SessionStore`

## Parser Tests

Add fixture-based tests for authenticated HTML that contains watched-state markers.

Verify:

- watched-state extraction
- `WATCHED` mapping
- `UNWATCHED` mapping
- graceful fallback to `UNKNOWN`

## Repository Integration Tests

Verify:

- merge of anonymous content and watched-state
- expired-session fallback
- logout cleanup
- anonymous behavior when no session exists

## Android UI Tests

Verify:

- home sign-in action opens the auth screen
- QR screen states: waiting, expired, success, error
- successful login returns the user to content
- watched-state becomes visible after login
- logout hides watched-state
- expired-session prompt appears without breaking content browsing

## Manual Smoke Tests

On a real device or emulator, verify:

- QR sign-in start
- login completion from phone
- watched-state appears after returning to TV
- logout returns the app to anonymous mode
- expired-session relogin path works

## Risks And Mitigations

### LostFilm Markup Drift

Risk:

- personal watched-state selectors may change

Mitigation:

- isolate parsing
- use fixture coverage
- degrade to `UNKNOWN` or re-login prompt instead of breaking content

### Session Fragility

Risk:

- LostFilm session behavior may change

Mitigation:

- keep session storage isolated
- detect invalid sessions softly
- preserve anonymous mode

### Scope Expansion

Risk:

- auth work may balloon into a full account feature set

Mitigation:

- keep `v1` limited to sign-in, sign-out, expiry handling, and watched-state
- defer profile and multi-account features

## Open Assumptions Locked For V1

- Anonymous mode remains fully supported.
- There is only one active account per device.
- QR login through the auth bridge is the only supported sign-in path in `v1`.
- The backend performs pairing and one-time session transfer only.
- Watched-state is the only personal feature required for `v1`.
- In-progress pairing recovery after process death is out of scope.

## Next Step

After the user approves this design document, the next step is to create a detailed implementation plan for the Android client and backend changes before writing production code.
