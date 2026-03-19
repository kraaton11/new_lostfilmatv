# LostFilm Auth UX And Reliability Refresh Design

**Goal:** Improve the existing LostFilm QR sign-in flow so it feels clear from the TV, recovers cleanly from failure, and only reports success after the TV has verified that the claimed session actually works.

This design supplements the existing auth baseline in `docs/superpowers/specs/2026-03-15-lostfilm-auth-design.md`. It does not replace the QR-based pairing model or server-side login architecture already chosen.

## Scope

Included:
- Simplified TV auth screen UX for the current QR + code flow
- Clearer waiting, verifying, expired, and recoverable-error states
- Stronger Android-side verification before treating sign-in as successful
- Limited backend phone-page copy improvements where they support the chosen UX
- Focused unit/UI tests around auth flow state handling and session verification

Excluded:
- Replacing the QR pairing model
- Adding TV-side username/password entry
- Adding a manual `I finished on my phone` confirmation button
- Introducing a multi-screen TV auth wizard
- Redesigning the backend pairing protocol beyond what is needed for clearer results

## User-Validated Decisions

The user approved these product choices during brainstorming:

- The TV entry screen should follow the simpler visual direction similar to option A.
- The biggest UX pain is on the QR/code screen and in failure states.
- After a failure, the primary recovery action should be `Get new code`.
- TV should complete sign-in automatically after phone-side success.
- CAPTCHA should not be mentioned proactively on the initial TV screen.
- CAPTCHA can be referenced only when it is relevant to an actual failure or retry hint.

## Current Problems

The current implementation is close to functional, but it has two gaps that make the experience feel unreliable:

- The TV auth screen gives too little guidance once the QR and code appear.
- The success path is too optimistic: Android currently treats the presence of `lf_session` as enough proof that sign-in succeeded, even before verifying the session from the TV context.

This creates a bad combination: users feel under-guided while also being exposed to false-positive success if the claimed cookies do not actually work on-device.

## Design Decisions

### Keep the TV screen visually simple

The main TV auth screen should remain lightweight and readable from a distance:

- large QR code
- large short code
- three concise steps
- one short status line beneath the steps

We intentionally do not add status chips, advanced diagnostics, or a busy layout. The approved direction is clarity over density.

### Keep completion automatic

There will be no explicit `I signed in on my phone` button. The TV already polls the pairing, and the experience should feel automatic:

- the user scans the QR
- completes login on the phone
- returns attention to the TV
- the TV finishes by itself

This reduces ambiguity and keeps the flow consistent with the pairing model already in place.

### Move complexity into state handling, not screen density

The UI can stay simple only if the underlying state machine becomes more explicit. Instead of loosely combining `isLoading`, `isPolling`, and `error`, the auth flow should move through a small set of user-meaningful states.

Recommended TV-facing states:

- `Idle`
- `CreatingCode`
- `WaitingForPhoneOpen`
- `WaitingForPhoneLogin`
- `ClaimingSession`
- `VerifyingSession`
- `Authenticated`
- `Expired`
- `RecoverableError`

The UI does not need to show those enum names, but it should render distinct text and actions from them.

State mapping should stay straightforward:

- backend `PENDING` -> `WaitingForPhoneOpen`
- backend `IN_PROGRESS` -> `WaitingForPhoneLogin`
- backend `CONFIRMED` -> `ClaimingSession`, then `VerifyingSession`
- backend `EXPIRED` -> `Expired`
- backend `FAILED` -> `RecoverableError`

### Only show success after real TV-side verification

This is the key reliability change.

After the pairing becomes `confirmed`, Android should:

1. claim the temporary session payload
2. store it securely
3. perform an authenticated probe to LostFilm from the TV/app context
4. finalize the claim only if that probe succeeds

If the probe fails:

- clear the local session
- release the backend claim
- return the UI to a recoverable failure state
- show a human-friendly message with `Get new code`

The UI should show `Checking sign-in...` during this phase instead of immediately transitioning to success.

### Default failure recovery is a new code

When the flow cannot be completed cleanly, the TV should not ask the user to reason about stale pairing state. The default recovery is:

- explain the problem briefly in human terms
- make `Get new code` the primary CTA
- keep `Back` as a secondary action

`Get new code` should discard the current pairing attempt in-place and immediately start a fresh pairing request, without forcing the user through an extra intermediate screen.

This applies to:

- expired pairing
- failed session verification
- backend claim issues after confirmation
- repeated temporary network failures during the final handoff

### Mention CAPTCHA only when relevant

The initial QR screen should not warn about CAPTCHA in advance. That would add anxiety to the main path.

If the sign-in attempt fails and the phone-side challenge may have been part of the problem, the retry copy may mention it briefly, for example:

`If LostFilm showed a CAPTCHA on your phone, finish it there and then try again with a new code.`

## TV UX Structure

### Initial State

Show a single primary action:

- `Start sign-in`

### Active QR State

Show:

- page title
- large QR code
- large short code
- three steps:
  1. Open the QR on your phone
  2. Sign in to LostFilm
  3. Return to the TV, this screen will update automatically
- one short dynamic status line

Recommended status copy:

- `Open the link on your phone`
- `Continue sign-in on your phone`
- `Checking sign-in...`

### Success State

The screen can briefly show a simple success message, but only after the verification probe succeeds. It should not be reachable directly from pairing confirmation alone.

### Expired Or Recoverable Error State

Replace the QR block with a compact error state:

- short message
- primary button `Get new code`
- secondary button `Back`

Representative messages:

- `This sign-in code expired. Get a new code to try again.`
- `The TV could not confirm your sign-in. Get a new code and try again.`
- `There was a network problem while finishing sign-in. Get a new code and try again.`

## Reliability Design

### Android-side verification probe

`AuthRepository.claimAndPersistSession()` should stop using cookie presence as its success condition.

Instead, add a lightweight authenticated probe through the same cookie-aware HTTP layer used by the app. The default probe remains the LostFilm root page, using the stored claimed cookies.

Successful verification means:

- the request completes successfully
- the response no longer looks anonymous
- the response contains an authenticated-only marker agreed by fixtures/tests

Only then may the repository finalize the claim and report success.

### Retry strategy for temporary failures

Short-lived network or transport failures during polling, claim, or verification should not instantly collapse the flow.

Use a small bounded retry policy for temporary failures in the final handoff path:

- retry a few times
- use short delays
- stop retrying once the failure looks terminal

If retries are exhausted, transition to `RecoverableError`.

### Normalize raw failures into UI-safe outcomes

The UI should not display backend codes like `lease_expired` or `session_invalid` directly.

Repository-level error mapping should convert raw outcomes into a small set of user-safe results such as:

- expired
- network problem
- sign-in could not be confirmed
- generic retryable failure

This keeps the screen copy simple while preserving enough structure for tests.

## File-Level Change Boundaries

Modify:

- `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthScreen.kt`
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthViewModel.kt`
- `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/AuthRepository.kt`

Likely add or refactor:

- a small auth-flow UI state model in the auth UI package
- a lightweight authenticated verification helper or method in the network layer

Optional copy-only adjustments:

- `backend/auth_bridge/backend/src/auth_bridge/templates/login.html`
- `backend/auth_bridge/backend/src/auth_bridge/templates/challenge.html`

Do not change:

- pairing creation semantics
- the QR-based phone flow entry model
- anonymous browsing behavior

## Testing Strategy

Add or update tests for:

- `AuthRepository`
  - successful claim + successful verification probe
  - successful claim + failed verification probe -> local clear + backend release
  - temporary finalization-path failure with bounded retries
- `AuthViewModel`
  - pending/in-progress statuses map to the intended user-facing states
  - confirmed pairing transitions through `Checking sign-in...`
  - expired/failure states expose `Get new code`
- authenticated network verification
  - the verification request sends claimed cookies
- auth UI
  - error state renders a human-readable message and the primary recovery CTA

## Success Criteria

- The QR sign-in screen is understandable without extra explanation.
- The TV never reports sign-in success before on-device verification succeeds.
- Expired and failed flows recover through a single obvious primary action: `Get new code`.
- The TV flow stays automatic after phone-side completion.
- CAPTCHA is not mentioned on the initial screen, but failure copy can still guide the user when needed.
