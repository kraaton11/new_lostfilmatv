# LostFilm QR Browser Proxy Authentication Design

**Goal:** Keep QR-based sign-in from the TV, but replace the fragile raw HTTP LostFilm login with a real browser-based phone flow that the backend can safely bind to a TV pairing and finalize automatically.

This design supersedes the server-side credential-submit model chosen in [2026-03-15-lostfilm-auth-design.md](D:/ai/tvbox1/new_lostfilm/docs/superpowers/specs/2026-03-15-lostfilm-auth-design.md) for the phone-side login step. The TV pairing model, automatic completion model, and Android-side session verification from [2026-03-19-lostfilm-auth-ux-reliability-design.md](D:/ai/tvbox1/new_lostfilm/docs/superpowers/specs/2026-03-19-lostfilm-auth-ux-reliability-design.md) remain valid.

## Scope

Included:
- Keep TV sign-in initiated by QR code
- Keep automatic completion on TV after phone-side success
- Change phone-side auth from backend credential submit to browser-based LostFilm proxy flow
- Introduce wildcard subdomain routing on `*.auth.bazuka.pp.ua`
- Keep pairing, claim, finalize, and release semantics as the TV handoff contract
- Preserve Android-side post-claim verification before reporting success

Excluded:
- TV-side username/password entry
- Manual cookie export/import
- Direct QR links to LostFilm without backend pairing context
- Permanent backend proxy for all TV browsing after login
- Multi-account device support
- Replacing the existing TV pairing UI with a different interaction model

## User-Validated Decisions

The user approved these product choices during brainstorming:

- QR sign-in must remain the primary auth entry flow.
- After scanning the QR code, the phone may open `auth.bazuka.pp.ua` first instead of opening LostFilm directly.
- The best architecture is a backend-owned browser flow, not a direct LostFilm QR target.
- Automatic TV completion remains required. The user should not manually confirm on the TV.
- Wildcard subdomains under `*.auth.bazuka.pp.ua` are acceptable and should be treated as a required prerequisite.
- The preferred architecture is a wildcard proxy flow, not a path-prefix proxy and not a remote browser session.

## Current Problem

The existing backend login path is no longer reliable enough for real sign-in:

- `POST https://www.lostfilm.today/ajaxik.users.php` may return apparent success while still yielding only a partial cookie set
- subsequent requests such as `/my` may still render an anonymous redirect shim instead of a real authenticated page
- a real browser session may surface CAPTCHA or anti-bot behavior that the raw backend flow does not complete correctly

This means the current architecture can produce false positives at the LostFilm login endpoint while still failing the real session handoff to the TV.

The root cause is architectural, not a small verifier bug: the backend is trying to synthesize a browser login result from a raw HTTP flow that LostFilm does not reliably treat as a fully authenticated browser session.

## Selected Approach

### Chosen Approach

Use a pairing-bound wildcard browser proxy flow:

1. TV creates a pairing as it does today.
2. Backend returns a verification URL on a unique wildcard subdomain such as `https://<phone_verifier>.auth.bazuka.pp.ua/`.
3. The phone opens that subdomain.
4. The backend proxies the LostFilm browser flow through that subdomain.
5. The phone completes login in a real browser context, including any JavaScript-, redirect-, or CAPTCHA-driven steps that LostFilm requires.
6. The backend detects a genuinely authenticated upstream LostFilm session, stores the final LostFilm cookie jar for that pairing, and marks the pairing `CONFIRMED`.
7. The TV continues polling, claims the final LostFilm session, verifies it locally, finalizes the claim, and completes sign-in automatically.

### Why This Approach

This preserves the required UX while moving the hard part into the correct place:

- TV UX stays simple and unchanged at a high level
- the phone still uses QR
- the phone uses a real browser flow instead of a fragile synthetic login request
- backend still owns pairing and session transfer to the TV
- Android can keep the current secure storage and verification model

This is the smallest architecture change that solves the actual failure mode.

## Rejected Alternatives

### Direct LostFilm URL In QR

Rejected because the QR target must stay bound to a specific TV pairing. LostFilm does not provide an OAuth-style callback or pairing primitive we can rely on to reconnect the phone result to the correct TV session automatically.

### Path-Prefix Proxy On A Single Domain

Rejected because LostFilm uses root-relative paths, redirects, and frontend behavior that make path-prefix rewriting much more fragile than host-based proxying.

### Remote Headless Browser Session On The Server

Rejected as the default design because it is infrastructure-heavy, harder to scale, and adds more moving parts than needed if a real phone browser can complete the flow through a wildcard proxy.

## High-Level Architecture

### Main Units

#### Pairing API

Responsible for:

- creating pairings
- returning TV poll metadata
- generating the wildcard verification URL
- storing status transitions
- handling claim, finalize, and release

#### Wildcard Router

Responsible for:

- accepting requests on `*.auth.bazuka.pp.ua`
- extracting `phone_verifier` from the host
- resolving the request to the correct pairing
- rejecting expired, unknown, or mismatched subdomains

#### Phone Proxy Session Store

Responsible for:

- holding the upstream LostFilm cookie jar server-side for each active pairing
- storing per-pairing phone flow state
- keeping any transient anti-bot or challenge state between requests
- expiring and cleaning all temporary phone-side auth state by TTL

Important design choice:

- the phone browser does not need to hold real `lostfilm.today` auth cookies
- the backend holds the upstream LostFilm cookie jar server-side and injects it on proxied requests
- the phone browser only stays inside the backend-owned wildcard subdomain

This avoids cross-domain cookie portability problems during the phone flow.

#### LostFilm Browser Proxy

Responsible for:

- forwarding phone browser requests to LostFilm
- attaching the server-side upstream cookie jar for that pairing
- capturing upstream `Set-Cookie` headers into the per-pairing cookie jar
- rewriting redirects and absolute URLs that would otherwise leave the wildcard subdomain
- proxying HTML, forms, XHR, and static assets needed for the login flow

#### Auth Detector

Responsible for:

- deciding when the upstream LostFilm session is genuinely authenticated
- using multiple signals instead of only trusting one login endpoint response
- preventing `CONFIRMED` status until the browser flow is truly logged in

#### TV Claim Handoff

Responsible for:

- freezing the final upstream LostFilm cookie jar once auth is confirmed
- returning those cookies through the existing claim endpoint
- deleting temporary backend-side phone auth state after finalize or expiry

### Responsibility Boundaries

- Backend owns pairing, wildcard routing, proxying, upstream cookie capture, auth detection, and temporary session holding.
- Phone browser owns interactive login, JavaScript execution, CAPTCHA solving, and user-visible browser behavior.
- Android TV owns QR display, polling, claiming, secure storage, local session verification, and logout.
- The TV app does not become a general proxy client. After claim, ordinary authenticated browsing still goes directly to LostFilm.

## Request And Data Flow

### TV Flow

1. TV calls `POST /api/pairings`.
2. Backend creates a pairing and returns:
   - `pairingId`
   - `pairingSecret`
   - `userCode`
   - `phoneVerifier`
   - `verificationUrl = https://<phone_verifier>.auth.bazuka.pp.ua/`
   - `status`
   - `expiresIn`
   - `pollInterval`
3. TV renders the QR code and short code.
4. TV polls pairing status until it becomes terminal or confirmed.
5. When status becomes `CONFIRMED`, TV claims the final LostFilm session.
6. TV verifies the claimed cookies locally.
7. TV finalizes the claim only after verification succeeds.

### Phone Flow

1. Phone opens `https://<phone_verifier>.auth.bazuka.pp.ua/`.
2. Backend resolves `phone_verifier` and loads the pairing-bound phone flow.
3. Backend either shows a short shell page and redirects into the proxied LostFilm login flow, or lands directly on the proxied flow while keeping the user inside the wildcard subdomain.
4. Every phone request routes through the backend proxy.
5. Backend injects the pairing-scoped upstream LostFilm cookie jar.
6. Backend captures upstream cookie updates and reuses them on later proxied requests.
7. Once the detector sees a genuinely logged-in upstream session, pairing status becomes `CONFIRMED`.
8. Phone sees a success page such as `Device connected. Return to your TV.`

### Auth Detection Rules

The backend must not treat `ajaxik.users.php` success as sufficient proof.

Recommended confirm conditions:

- an upstream LostFilm cookie jar exists and contains the required auth cookie(s)
- a proxied follow-up request reaches a page that is known to require authentication
- that page does not match the anonymous redirect shim
- that page contains a stable authenticated marker agreed by tests and fixtures

Until those conditions hold, pairing remains `IN_PROGRESS`.

## Wildcard And Proxy Requirements

### Infrastructure Prerequisites

- wildcard DNS record for `*.auth.bazuka.pp.ua`
- wildcard TLS certificate for `*.auth.bazuka.pp.ua`
- edge routing that forwards wildcard hosts to the auth backend

### Host Model

- one host per pairing, derived from `phone_verifier`
- the host itself is the pairing identity for the phone flow
- the backend must reject requests whose host does not map to an active pairing

### URL Rewriting

The proxy must preserve browser usability by handling:

- `Location` headers that point to `lostfilm.today`
- absolute `href`, `src`, or form `action` attributes when necessary
- JavaScript-triggered navigations that rely on same-host paths
- root-relative upstream paths

Host-based proxying keeps root-relative paths much simpler than a path-prefix design.

## Session Handling

### Temporary Backend Session State

For each active pairing, backend stores:

- upstream LostFilm cookie jar
- current phone flow state
- confirmation status
- timestamps and TTL metadata

This state is short-lived and must be deleted on:

- finalize
- explicit release
- pairing expiry
- cleanup of abandoned phone flows

### Claimed TV Session Payload

The claimed payload should continue to include:

- cookie name
- cookie value
- cookie domain
- cookie path
- expiry/max-age when available
- secure/httpOnly/sameSite metadata when available

Those cookies are the upstream LostFilm cookies, not proxy-domain cookies.

## Error Handling

### Pairing Expired

- wildcard phone host shows expired page
- TV shows expired state with `Get new code`
- no auth confirmation may occur after expiry

### Phone Flow Fails Or Remains Anonymous

- pairing stays `IN_PROGRESS` while retry is still possible
- phone browser continues on the proxied flow
- backend does not promote the pairing to `CONFIRMED`

### TV Claim Verification Fails

Keep the current Android-side protection:

- TV discards the claimed session
- TV releases the claim
- TV returns to recoverable error state

This remains necessary even with the better phone flow.

## Security And Privacy

- backend must not log raw LostFilm credentials or raw auth cookies
- pairing subdomains must be unguessable because they are derived from `phone_verifier`
- all temporary phone auth state must be TTL-bound and pairing-scoped
- auth confirmation must be atomic with session freeze to avoid race conditions
- wildcard routing must not leak pairing existence details beyond allowed phone entry points
- phone success pages must avoid exposing sensitive account details unnecessarily

## File-Level Change Boundaries

### Backend

Modify:

- `backend/auth_bridge/backend/src/auth_bridge/config.py`
- `backend/auth_bridge/backend/src/auth_bridge/services/pairing_service.py`
- `backend/auth_bridge/backend/src/auth_bridge/api/phone_flow.py`
- `backend/auth_bridge/backend/src/auth_bridge/main.py`

Likely add:

- wildcard host parsing/router support
- a proxy-focused service such as `lostfilm_proxy_service.py`
- a pairing-scoped upstream cookie/session store service
- an auth detection helper focused on proxied pages
- new phone shell/success/error templates as needed

### Infrastructure

Modify:

- Caddy or edge routing config for wildcard subdomains
- deploy documentation and `.env` examples for wildcard base domain support

### Android

Likely minimal changes:

- keep existing QR screen and pairing polling flow
- continue using `verificationUrl` from backend
- keep post-claim local verification and finalize/release logic

Android should not need a new login architecture if backend preserves the pairing contract.

## Testing Strategy

### Backend Automated Tests

Add or update tests for:

- verification URL generation with wildcard subdomains
- wildcard host resolution to the correct pairing
- proxy cookie jar capture and reuse across multiple proxied requests
- redirect and absolute URL rewriting where required
- auth detector refusing false-positive login endpoint success
- auth detector confirming only after a real authenticated proxied page
- claim/finalize/release cleanup semantics after confirmation

### Android Automated Tests

Retain or extend tests for:

- pairing create/poll/claim flow
- release on failed local verification
- recoverable error UX after claim verification failure

### Manual Validation

Required real-device flow:

1. TV generates QR code
2. phone opens wildcard subdomain
3. user logs in through proxied LostFilm browser flow
4. backend reaches `CONFIRMED`
5. TV claims cookies
6. TV verifies the claimed session successfully
7. TV completes sign-in automatically

## Success Criteria

- QR remains the only required TV auth entry method
- phone login completes in a real browser flow, not a raw backend credential submit shortcut
- backend no longer confirms pairing based only on a login endpoint response
- TV continues to complete sign-in automatically after phone-side success
- Android-side verification remains the final guard before reporting success
- the architecture works with LostFilm CAPTCHA or anti-bot steps as long as they can be completed in a normal phone browser

## Next Step

After the user reviews and approves this spec document, the next step is to write a concrete implementation plan for the wildcard browser proxy flow and then implement backend and infrastructure changes before touching the TV flow further.
