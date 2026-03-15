# LostFilm Android TV Authentication Design

## Overview

This document defines the revised `v1` authentication design for the LostFilm Android TV application.

The application already supports anonymous browsing of new releases parsed directly from LostFilm. This design adds account sign-in through a QR-based flow and surfaces personal watched-state information without breaking anonymous use.

The original `v1` concept assumed the backend could exchange LostFilm credentials for a stable session without mandatory extra steps. Real-world validation showed that LostFilm login currently includes CAPTCHA and dynamic login markup, so the backend must act as a short-lived phone-side login bridge instead of a simple credential exchange endpoint.

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
- Reuse the existing auth bridge as the pairing service and short-lived login bridge.

## Non-Goals For V1

- No permanent proxying of LostFilm content traffic through the backend.
- No full personal profile UI.
- No account switching or multiple user profiles on one device.
- No requirement to restore an in-progress QR pairing after the app process is killed.
- No blocking of core content behind authentication.
- No browser extension or separate helper app requirement for `v1`.

## Product Decisions

- Authentication outcome for `v1`: session storage plus watched-state display.
- Login method: QR code on TV, phone-side login flow on the auth bridge, one-time session handoff to TV.
- Session lifecycle scope: login, logout, expired-session detection, re-login prompt.
- User entry points:
  - a visible sign-in call to action on the home screen
  - a duplicated account section in settings
- Anonymous behavior:
  - the app continues to work normally without login
  - personal watched-state is hidden when there is no valid session
- Phone-side login behavior:
  - the user signs in on an auth bridge page, not on the TV
  - the auth bridge may prompt for CAPTCHA or other LostFilm-required fields on the phone
  - successful phone-side completion automatically confirms the pairing for the TV

## Selected Technical Approach

### Chosen Approach

Use the existing auth bridge backend for device pairing, phone-side LostFilm login orchestration, CAPTCHA handling, and one-time transfer of a valid LostFilm web session to the Android TV app. After login, the TV app stores the session locally and makes direct requests to LostFilm.

### Why This Approach

This keeps the TV login experience usable, preserves the QR entry point, and allows a human to complete LostFilm's real browser login requirements on the phone while still handing off the resulting session to the TV automatically. It also keeps the backend out of the steady-state content path after the session is claimed.

### Rejected Alternatives

- Permanent backend proxy for authenticated requests:
  - rejected because it increases operational risk, centralizes user traffic unnecessarily, and may create IP-related issues with LostFilm.
- TV-only username/password form:
  - rejected because it is poor TV UX and would likely be fragile if the web login flow changes.
- Manual cookie import as the primary method:
  - rejected because it is worse UX than a phone-side login bridge and should remain only a fallback idea if the bridge proves impossible.
- Simple backend credential exchange without CAPTCHA handling:
  - rejected because real-world validation showed that LostFilm login currently involves CAPTCHA and dynamic form behavior that cannot be ignored.

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
- `LostFilmLoginBridgeClient` on the backend
  - fetches LostFilm login pages
  - submits LostFilm login and CAPTCHA forms server-side
  - extracts confirmed session cookies from the backend-side LostFilm session

### Architectural Boundaries

- The backend is responsible for:
  - creating pairings
  - hosting the phone-side pairing and login bridge flow
  - maintaining short-lived LostFilm login state during the phone flow
  - submitting LostFilm login and CAPTCHA forms server-side
  - exposing pairing status
  - one-time transfer of the LostFilm session payload
- The Android TV app is responsible for:
  - storing the session
  - making direct authenticated requests to LostFilm after claim
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
4. The user scans the QR code or opens the verification URL on the phone.
5. The phone opens an auth bridge page for the pairing.
6. The auth bridge page walks the user through the current LostFilm login requirements, including CAPTCHA if required.
7. The backend polls nothing on the phone side; instead, it advances the pairing state as the phone-side flow succeeds or fails.
8. The TV app polls pairing status in the background.
9. On success:
   - the backend marks the pairing `confirmed`
   - the app claims the session
   - stores it locally
   - shows a brief success state
   - returns the user to the previous context

## Phone-Side Flow UX

The phone-side page is served by the auth bridge and should be optimized for the current LostFilm login flow.

- The page explains that the user is signing in to connect the TV device.
- The page can show:
  - username/password fields
  - CAPTCHA image and input
  - hidden-field dependent follow-up steps if LostFilm requires them
- On success, the page shows a clear confirmation such as `Device connected, return to your TV`.
- On invalid credentials or CAPTCHA failure, the page stays on the phone and lets the user retry without breaking the TV waiting state.

## Expired Pairing UX

If the QR pairing expires before completion:

- the TV screen shows an explicit expired state
- the user gets a clear `Try Again` or `Show New QR` action
- the phone-side page also shows an expired state if opened after expiry
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

### Pairing Statuses

The backend may expose more detail than the TV strictly needs, but the flow should at least distinguish:

- `pending`
- `awaiting_phone_login`
- `awaiting_phone_challenge`
- `confirmed`
- `expired`
- `failed`

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

### Required TV Calls

- `POST /api/pairings`
  - create pairing
  - receive `pairingId`, `userCode`, `verificationUrl`, `expiresIn`, `pollInterval`
- `GET /api/pairings/{pairingId}`
  - poll status
  - expect states such as `pending`, `awaiting_phone_login`, `awaiting_phone_challenge`, `confirmed`, `expired`, `failed`
- `POST /api/pairings/{pairingId}/claim`
  - claim a one-time session payload after pairing confirmation

### Phone-Side Flow

The phone-side browser flow is also hosted by the auth bridge.

The exact route shape may evolve, but the backend must support these responsibilities:

- open a pairing-specific page from the QR verification URL
- display the current LostFilm login step for that pairing
- submit username/password and any required hidden fields to LostFilm
- surface CAPTCHA or other challenge steps to the user on the phone
- retain short-lived LostFilm login session state between steps
- confirm the pairing only after valid LostFilm session cookies are acquired

### Session Handoff

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

## Phone Login Bridge Errors

If LostFilm rejects the phone-side submission because credentials are invalid:

- the phone-side page shows an inline retryable error
- the TV stays in a waiting state until the pairing expires or succeeds

If LostFilm requires CAPTCHA or another challenge step:

- the phone-side page surfaces the challenge
- the backend retains the short-lived login state needed to continue the flow
- the TV can show a more specific waiting message if the pairing status exposes this detail

If the backend cannot acquire session cookies after an apparent login success:

- the pairing is not confirmed
- the phone-side flow shows a recoverable failure or retry path
- anonymous TV browsing continues unchanged

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
- Keep backend-side LostFilm login state short-lived and pairing-scoped.
- Do not turn the backend into a permanent content proxy for authenticated browsing.
- Logout must remove locally stored session material and personal watched-state cache.

## Testing Strategy

## Unit Tests

Add tests for:

- `AuthRepository`
- pairing state transitions
- session expiry transitions
- logout behavior
- `SessionStore`
- backend pairing service
- backend phone-side login bridge client

## Login Bridge Tests

Add fixture-based or mocked tests for LostFilm login behavior that cover:

- login page fetch and hidden field extraction
- CAPTCHA-required flow detection
- successful credential plus CAPTCHA submit
- extraction of required session cookies from the backend-side LostFilm session

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
- phone-side login bridge opens from the QR URL
- real LostFilm login and CAPTCHA completion confirms the pairing
- watched-state appears after returning to TV
- logout returns the app to anonymous mode
- expired-session relogin path works

## Mandatory Early Validation Checkpoint

Before starting Android auth implementation beyond backend pairing and login-bridge foundations, perform a real-world validation of the phone-side flow:

1. open a real `/pair/{code}` flow from the backend
2. log in with a disposable LostFilm test account
3. complete any required CAPTCHA or challenge on the phone
4. verify the pairing reaches `confirmed`
5. verify `claim` returns a valid session payload

If this checkpoint fails because the backend cannot stably complete the LostFilm login bridge flow, stop implementation and revise the design again before continuing.

## Risks And Mitigations

### LostFilm Login Markup Drift

Risk:

- login fields, hidden values, or CAPTCHA behavior may change

Mitigation:

- isolate login bridge parsing and submission logic
- add focused fixtures and mocked flow coverage
- keep the phone-side bridge short-lived and easy to revise

### Session Fragility

Risk:

- LostFilm session behavior may change

Mitigation:

- keep session storage isolated
- detect invalid sessions softly
- preserve anonymous mode

### Scope Expansion

Risk:

- auth work may balloon into a full account feature set or a permanent proxy design

Mitigation:

- keep `v1` limited to sign-in, sign-out, expiry handling, and watched-state
- keep the backend limited to pairing, short-lived login bridging, and one-time session transfer

## Open Assumptions Locked For V1

- Anonymous mode remains fully supported.
- There is only one active account per device.
- QR login through the auth bridge is the only supported sign-in path in `v1`.
- The existing auth bridge remains the only backend component needed for `v1`.
- The backend performs pairing, short-lived phone-side login bridging, and one-time session transfer only.
- Watched-state is the only personal feature required for `v1`.
- In-progress pairing recovery after process death is out of scope.
- The phone-side login bridge can obtain a valid LostFilm session when a human completes required CAPTCHA or challenge steps.

## Next Step

After the user approves this revised design document, the next step is to rewrite the implementation plan for the backend login bridge plus Android auth flow before writing more production code.
