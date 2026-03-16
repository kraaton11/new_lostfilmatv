# LostFilm Android TV Authentication Design

## Overview

This document defines the revised `v1` authentication design for the LostFilm Android TV application.

The app already supports anonymous browsing of LostFilm releases. The new authenticated flow must keep that anonymous experience intact while adding a QR-based sign-in path that starts on TV, continues on a phone, and ends with the TV storing a valid LostFilm web session locally.

This revision replaces the earlier mixed WebView and phone-side challenge-bridge approach. The confirmed direction is now simpler:

- the TV shows a QR code and short code
- the phone opens a backend-hosted login page
- the user enters LostFilm login/password on the backend page
- the backend performs the LostFilm login server-side
- the backend captures the resulting LostFilm cookies
- the TV polls until the pairing is confirmed
- the TV claims the one-time session payload and stores it securely

Real-world validation showed that LostFilm may require CAPTCHA during server-side login. The `v1` flow therefore includes a backend-hosted challenge step, and the captcha image must be served through the backend pairing session so the displayed image matches the backend-side submit session.

## Goals

- Add QR-based authentication to the Android TV app.
- Keep anonymous browsing fully usable at all times.
- Let the backend perform LostFilm login after the user enters credentials on the phone.
- Transfer the authenticated LostFilm session to the TV through a one-time claim.
- Store LostFilm cookies securely in the app.
- Support login, logout, session expiry, and re-login.
- Leave ordinary authenticated content traffic direct from app to LostFilm after claim.

## Non-Goals For V1

- No permanent backend proxy for LostFilm browsing.
- No TV-side username/password entry.
- No manual cookie copy/paste.
- No multiple accounts per device.
- No restoration of an in-progress pairing after app process death.
- No broader account-management UI beyond login state and logout.

## Product Decisions

- Authentication entry points:
  - a visible sign-in action on the home screen
  - a duplicate account entry in settings
- Pairing UX on TV:
  - show QR code
  - show short pairing code
  - show waiting/pending/expired/success states
- Phone UX:
  - backend-hosted login page only
  - user enters LostFilm username and password on the backend page
  - backend never asks the TV to open a WebView for login
- Session destination:
  - cookies are claimed by the TV app and stored locally in encrypted storage
- Anonymous fallback:
  - release browsing keeps working when login fails, expires, or is absent

## Selected Technical Approach

### Chosen Approach

Use the existing auth bridge as a short-lived pairing and server-side login service.

The backend creates pairings, serves the phone login page, submits LostFilm credentials to `https://www.lostfilm.today/login`, captures the resulting cookies, holds them only until claim, and marks the pairing confirmed. The Android TV app polls the pairing, claims the session once confirmed, stores it in encrypted local storage, verifies that the claimed cookies work from the TV context, finalizes the claim, and only then treats the session as fully logged in for direct authenticated LostFilm requests.

### Why This Approach

This matches the desired UX best:

- TV input stays simple
- the phone is only used for credential entry
- cookies move to the TV automatically
- the backend stays out of the steady-state browsing path

It also removes the unstable TV WebView dependency that already proved fragile on the real device.

### Rejected Alternatives

- TV WebView login:
  - rejected because it is unstable on the target TV device and creates poor TV UX
- phone browser login to LostFilm directly plus manual cookie transfer:
  - rejected because the user wants automatic cookie handling
- permanent authenticated backend proxy:
  - rejected because it increases operational scope and risk unnecessarily

## High-Level Architecture

### Main Units

- `AuthRepository` in Android
  - starts pairing
  - polls pairing status
  - claims session payload
  - stores cookies securely
  - exposes login/logout/auth state to UI
- `AuthBridgeClient` in Android
  - calls pairing create/status/claim endpoints
- `SessionStore` in Android
  - stores LostFilm cookies in encrypted local storage
- auth-aware HTTP layer in Android
  - attaches stored cookies to LostFilm requests
  - updates stored cookies if LostFilm rotates them later
- `PairingService` in backend
  - creates pairings
  - tracks TTL and status
  - binds temporary login state and temporary session payload to pairing
- `LostFilmLoginClient` in backend
  - performs server-side LostFilm login
  - extracts cookies from the resulting session
  - validates that login succeeded before pairing confirmation
- backend phone flow routes/templates
  - render pairing page and login form
  - show success/expired/error states

### Responsibility Boundaries

- The backend is responsible for:
  - pairing lifecycle
  - phone-side credential collection page
  - server-side LostFilm login
  - temporary holding of resulting cookies
  - confirming pairings
  - one-time session claim
- The Android app is responsible for:
  - creating the pairing
  - showing QR and short code
  - polling for completion
  - claiming cookies
  - secure local storage
  - direct authenticated LostFilm requests after claim
- Anonymous repository/data flow remains the default browsing path.

## TV User Experience

### Anonymous State

When there is no valid session:

- home and details browsing continue to work
- watched-state indicators remain hidden
- the user can start login from home or settings

### QR Login Flow

1. The user selects `Sign In` on the TV.
2. The app calls `POST /api/pairings`.
3. The auth screen shows:
   - QR code for the backend verification URL
   - short user code
   - waiting state text
4. The user scans the QR with the phone.
5. The phone opens the backend page for this pairing.
6. The user enters LostFilm login/password on the backend page.
7. The backend logs in to LostFilm server-side.
8. If login succeeds immediately, or after the phone user completes any required CAPTCHA challenge, the backend stores cookies temporarily and marks the pairing `confirmed`.
9. The TV app sees `confirmed`, calls `POST /api/pairings/{pairingId}/claim`, receives the leased session payload, stores it locally, verifies it with the agreed authenticated probe, calls `finalize`, and only then transitions to logged-in state.

### Phone Login Page UX

The phone page must be backend-hosted and pairing-specific.

- It explains that the user is connecting the TV device.
- It shows username/password fields.
- If LostFilm requires CAPTCHA, it shows a backend-proxied captcha image and a challenge form.
- It shows retryable inline errors for invalid credentials.
- On success it shows `Device connected. Return to your TV.`
- If pairing expires before completion, it shows an expired page and never confirms the pairing.

### Logged-In State

When login succeeds:

- the app returns to normal browsing
- authenticated cookies are active for LostFilm requests
- watched-state becomes available where supported
- settings exposes a sign-out action

### Logout And Expiry

- Logout clears stored cookies and personal state only.
- Anonymous cached content remains available.
- If cookies become invalid later, the app clears auth state softly and prompts for re-login.

## Pairing Contract

### Pairing Fields

- `pairingId`
- `pairingSecret`
- `phoneVerifier`
- `userCode`
- `verificationUrl`
- `expiresIn`
- `pollInterval`
- `status`
- optional `retryable`
- optional `failureReason`

### TV-Facing Status Model

The public TV-facing status model is intentionally simple:

- `pending`
- `in_progress`
- `confirmed`
- `expired`
- `failed`

The backend may keep more detailed internal state, but Android should not depend on challenge-specific statuses.

### Pairing Identity And Binding Rules

- `pairingId` is not sufficient authorization for TV poll/claim calls.
- `POST /api/pairings` must return a high-entropy `pairingSecret` for the TV.
- `GET /api/pairings/{pairingId}` and `POST /api/pairings/{pairingId}/claim` must require the TV-held `pairingSecret`.
- QR URLs must contain an unguessable phone-side verifier token.
- the QR code must encode a verifier-bound backend URL such as `/pair/{phoneVerifier}`.
- `userCode` exists only as a manual fallback for the human, not as a security boundary.
- if manual code entry is supported, it must resolve to the verifier-bound pairing before any credential form is shown.

### Claim Semantics

- `claim` returns the session payload only after `confirmed`
- claim must be idempotent for the same pairing during a short lease window so transport loss does not destroy the session handoff
- after `claim`, the pairing enters a leased handoff state until Android finalizes success or releases failure
- leased handoff TTL for `v1` should be short, for example 60 seconds
- after secure local save plus successful authenticated verification, Android finalizes the claim and backend-side temporary cookies are deleted
- if save or verification fails, Android releases the leased payload and discards it locally
- if Android never calls `finalize` or `release` before lease expiry, the backend must delete the temporary cookies and move the pairing to a retryable failure or expired state according to remaining pairing TTL
- once finalized, later claims must be rejected

### Pairing Concurrency Rules

- the app treats one pairing as active at a time
- `POST /api/pairings` must include or derive a TV auth-attempt binding key for the current app/device auth session
- starting a newer pairing invalidates or supersedes any older unclaimed pairing for that same TV auth-attempt binding key
- backend confirmation and claim transitions must be atomic
- a late success from an older superseded pairing must not overwrite the currently active pairing

## Session Payload And Storage

### Session Payload Fields

The backend returns enough cookie data for faithful replay in Android:

- cookie name
- cookie value
- cookie domain
- cookie path
- expiry or max-age when available
- `secure`
- `httpOnly`
- `sameSite` when available
- host-only vs domain cookie semantics when distinguishable
- optional stable account identifier if available safely

### Android Storage Rules

- store the session in encrypted local storage backed by Android Keystore
- do not store raw cookies in Room content tables
- keep session storage separate from anonymous content cache
- support later cookie refresh if authenticated LostFilm responses update cookies

## Network And API Design

### Android Calls

- `POST /api/pairings`
  - returns `pairingId`, `pairingSecret`, `phoneVerifier`, `userCode`, `verificationUrl`, `expiresIn`, `pollInterval`, `status`
- `GET /api/pairings/{pairingId}`
  - requires `pairingSecret`
  - returns at least `pairingId`, `status`, `expiresIn`
  - may also return `retryable` and `failureReason`
- `POST /api/pairings/{pairingId}/claim`
  - requires `pairingSecret`
  - returns leased `SessionPayload`
- `POST /api/pairings/{pairingId}/finalize`
  - requires `pairingSecret`
  - confirms durable local save and successful authenticated verification
- `POST /api/pairings/{pairingId}/release`
  - requires `pairingSecret`
  - abandons leased payload when save or verification fails

### Polling Contract

- `expiresIn` means remaining TTL in seconds, not original lifetime
- `confirmed`, `expired`, and non-retryable `failed` are terminal states
- temporary network errors do not change pairing state
- polling after a deleted or unknown pairing should return a stable error contract rather than ambiguous partial payloads

### Phone-Side Backend Calls

The backend-hosted phone flow should support:

- `GET /pair/{phoneVerifier}` or `GET /pair/{phoneVerifier}/login`
  - open verifier-bound pairing page
- `POST /pair/{phoneVerifier}/login`
  - accept LostFilm username/password
  - execute server-side login
  - confirm pairing on success
  - return inline errors on failure

No phone-side cookie extraction, manual cookie submit, or Android-JS bridge is part of the chosen design.

### Backend Login Form Security

- backend login form posts must be bound to the pairing/verifier token
- form handling must include CSRF protection
- login attempts must be rate-limited per pairing and per source IP
- backend must not leak whether a pairing exists beyond the allowed phone flow
- backend must not log raw credentials or raw cookies

## Error Handling

### Invalid Credentials

- backend phone page shows inline retryable error
- pairing remains active until expiry or success
- TV stays in waiting state

### Pairing Expiry

- TV shows explicit expired state with retry option
- phone page also shows expired state
- no anonymous functionality is affected

### Backend Unavailable

- login cannot start or complete
- anonymous browsing remains available
- existing valid locally stored sessions keep working until they expire

### Invalid Or Non-Portable Cookies

If backend-collected cookies do not work from the TV/app context:

- the app must clear the claimed session
- the app returns to anonymous mode
- the spec treats this as a critical validation failure for this design
- the app must verify the claimed session with an immediate lightweight authenticated LostFilm request before presenting final logged-in success

### Post-Claim Verification Contract

- after `claim`, Android remains in a non-final `verifying` state
- Android must issue a lightweight authenticated LostFilm request before showing final success
- the default `v1` verification probe is `GET https://www.lostfilm.today/` using the claimed cookies
- authenticated success means the response does not contain the anonymous login form marker `id="lf-login-form"` and does contain an authenticated-only account/logout marker locked by fixtures during implementation
- if that probe fails, Android must discard the claimed session, call backend release, and return to anonymous mode
- only after that probe succeeds may Android finalize the claim and transition UI to logged-in success

## Security And Privacy

- credential entry page must be served only over trusted transport in production
- backend must not log usernames, passwords, or raw cookies
- credentials must not be persisted beyond request scope
- backend-side temporary cookies must be pairing-scoped and short-lived
- logout must remove locally stored auth material
- anonymous browsing must remain available even when auth fails

## Testing Strategy

### Backend Tests

Add or revise tests for:

- pairing create/poll/confirm/claim lifecycle
- invalid-credential retry behavior
- expired pairing behavior
- one-time claim semantics
- cleanup of temporary backend-side cookies after claim/expiry/failure
- LostFilm login client success/failure parsing and cookie extraction

### Android Tests

Add or revise tests for:

- create pairing and display QR/code
- poll until confirmed
- claim and persist cookies
- logout clears auth state without breaking anonymous browsing
- invalid/expired session falls back to anonymous mode

### Manual Validation

The following checkpoint remains mandatory before further Android auth expansion:

1. create a real pairing from TV/app
2. open the backend login page from the phone QR URL
3. submit real LostFilm credentials through the backend page
4. verify backend reaches `confirmed`
5. verify TV/app successfully claims cookies
6. verify those claimed cookies actually work for authenticated LostFilm requests from the TV device context

If step 6 fails, this design must be revised again because backend-side cookies would not be portable to the app.

## Risks And Mitigations

### LostFilm Login Flow Drift

Risk:

- LostFilm may change its login markup or flow

Mitigation:

- isolate login parsing/submission in `LostFilmLoginClient`
- keep backend tests fixture-based and focused

### Cookie Portability Risk

Risk:

- cookies acquired server-side may not be usable from the TV device context

Mitigation:

- keep explicit real-device validation checkpoint
- do not treat backend cookie extraction alone as success

### Scope Drift

Risk:

- auth work expands into a full account platform

Mitigation:

- keep `v1` limited to login, logout, expiry handling, and watched-state support

## Locked Assumptions For This Revision

- LostFilm login for the target account flow does not require CAPTCHA.
- QR + phone-side backend page is the only supported login path in `v1`.
- Backend login is performed server-side after phone credential entry.
- Claimed cookies are stored locally in the Android app.
- Anonymous mode remains fully supported.
- One active account per device is sufficient.

## Next Step

After the user reviews and approves this revised spec, the next step is to rewrite the implementation plan around the server-side backend login flow and then implement it.
