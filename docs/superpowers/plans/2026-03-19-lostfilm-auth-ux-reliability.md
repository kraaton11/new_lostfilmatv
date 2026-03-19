# LostFilm Auth UX And Reliability Refresh Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing LostFilm QR sign-in flow clearer on TV and more reliable by verifying claimed sessions on-device before reporting success.

**Architecture:** Keep the existing QR pairing contract and backend flow, but tighten the Android side. Add a small authenticated-session verifier, return structured completion results from `AuthRepository`, move TV status updates into an explicit auth UI state machine, and simplify `AuthScreen` around one clear QR flow plus a `Get new code` recovery path.

**Tech Stack:** Kotlin, Jetpack Compose for TV, Coroutines, OkHttp, AndroidX Security Crypto, JUnit4, MockWebServer, Compose Android UI tests

---

**Spec Reference:** `docs/superpowers/specs/2026-03-19-lostfilm-auth-ux-reliability-design.md`

## Planned File Structure

- `app/src/main/java/com/kraat/lostfilmnewtv/data/network/LostFilmSessionVerifier.kt`
  - New focused unit that performs a lightweight authenticated probe against LostFilm and decides whether the claimed session is really usable from the TV context.
- `app/src/main/java/com/kraat/lostfilmnewtv/data/network/LostFilmHttpClient.kt`
  - Keep the cookie-aware HTTP client stable; only touch if the verifier should reuse shared request helpers.
- `app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt`
  - Wire the new verifier into `AuthRepository`.
- `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/AuthCompletionResult.kt`
  - New small result model that converts raw claim/verify failures into UI-safe outcomes.
- `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/AuthRepository.kt`
  - Own pairing completion, bounded retries, claim/finalize/release behavior, and mapping of failures into `AuthCompletionResult`.
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthUiState.kt`
  - New explicit UI state model for idle, waiting, verifying, expired, recoverable-error, and authenticated states.
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthViewModel.kt`
  - Drive polling and state transitions in a user-meaningful way instead of using loose boolean flags.
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthScreen.kt`
  - Render the approved simple QR flow, status line, and `Get new code` recovery UI.
- `app/src/test/java/com/kraat/lostfilmnewtv/data/network/LostFilmSessionVerifierTest.kt`
  - New verifier-focused unit tests with `MockWebServer`.
- `app/src/test/java/com/kraat/lostfilmnewtv/data/auth/AuthRepositoryTest.kt`
  - New repository tests for finalize/release/retry behavior.
- `app/src/test/java/com/kraat/lostfilmnewtv/ui/auth/AuthViewModelTest.kt`
  - New state-machine tests for pending/in-progress/confirmed/expired/error transitions.
- `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/AuthScreenTest.kt`
  - New focused UI test for the simplified screen copy and recovery CTA.

Keep unchanged unless implementation proves it is necessary:

- `backend/auth_bridge/backend/src/auth_bridge/templates/login.html`
- `backend/auth_bridge/backend/src/auth_bridge/templates/challenge.html`
- `backend/auth_bridge/backend/tests/test_phone_flow.py`

## Chunk 1: Session Verification And Repository Contract

### Task 1: Add a TV-side session verifier with focused tests

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/network/LostFilmSessionVerifier.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/data/network/LostFilmSessionVerifierTest.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/data/network/LostFilmHttpClient.kt`

- [ ] **Step 1: Write the failing verifier tests**

```kotlin
@Test
fun verify_returnsAuthenticated_whenProbeIncludesCookiesAndAuthenticatedMarker() = runTest {
    // Arrange MockWebServer to return authenticated HTML without login form marker.
}

@Test
fun verify_returnsUnauthenticated_whenProbeLooksAnonymous() = runTest {
    // Arrange HTML that still contains the anonymous login marker.
}
```

- [ ] **Step 2: Run the targeted verifier test file and confirm it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.network.LostFilmSessionVerifierTest"
```

Expected: FAIL because `LostFilmSessionVerifier` does not exist yet.

- [ ] **Step 3: Implement the minimal verifier**

Suggested shape:

```kotlin
interface LostFilmSessionVerifier {
    suspend fun verify(session: LostFilmSession): Boolean
}
```

Implementation notes:
- Use the same TV user-agent style as the existing network layer.
- Probe `https://www.lostfilm.today/`.
- Send the claimed cookies in the `Cookie` header.
- Treat the response as authenticated only when it does not contain `id="lf-login-form"` and does contain a stable authenticated marker locked by the test fixture.
- Keep the file focused on verification only; do not fold pairing logic into it.

- [ ] **Step 4: Wire the verifier into application composition and rerun the tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.network.LostFilmSessionVerifierTest"
```

Expected: PASS

- [ ] **Step 5: Commit the verifier slice**

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/data/network/LostFilmSessionVerifier.kt app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt app/src/test/java/com/kraat/lostfilmnewtv/data/network/LostFilmSessionVerifierTest.kt
git commit -m "test: add lostfilm session verifier"
```

### Task 2: Make `AuthRepository` return structured completion results

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/AuthCompletionResult.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/data/auth/AuthRepositoryTest.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/AuthRepository.kt`

- [ ] **Step 1: Write failing repository tests for finalize, release, and retry**

```kotlin
@Test
fun claimAndPersistSession_finalizes_whenVerificationSucceeds() = runTest { /* ... */ }

@Test
fun claimAndPersistSession_releasesAndClears_whenVerificationFails() = runTest { /* ... */ }

@Test
fun claimAndPersistSession_returnsRetryableFailure_afterTransientProbeErrors() = runTest { /* ... */ }
```

- [ ] **Step 2: Run the targeted repository tests and confirm they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.auth.AuthRepositoryTest"
```

Expected: FAIL because the result model and verifier-driven completion path do not exist yet.

- [ ] **Step 3: Implement the repository contract**

Suggested result shape:

```kotlin
sealed interface AuthCompletionResult {
    data object Authenticated : AuthCompletionResult
    data object Expired : AuthCompletionResult
    data object NetworkError : AuthCompletionResult
    data object VerificationFailed : AuthCompletionResult
    data class RecoverableFailure(val hint: String? = null) : AuthCompletionResult
}
```

Implementation notes:
- Inject `LostFilmSessionVerifier`.
- Keep `startPairing()` and `pollPairingStatus()` available.
- Change `claimAndPersistSession()` to return `AuthCompletionResult` instead of `Boolean`.
- On successful claim + verification:
  - save session
  - finalize claim
  - return `Authenticated`
- On failed verification:
  - clear session
  - release claim
  - return `VerificationFailed`
- On transient errors:
  - retry a few times with short delay
  - stop once the failure is clearly terminal
- Keep logout behavior unchanged except for clearing any in-memory pairing attempt.

- [ ] **Step 4: Rerun the repository tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.auth.AuthRepositoryTest"
```

Expected: PASS

- [ ] **Step 5: Commit the repository slice**

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/data/auth/AuthCompletionResult.kt app/src/main/java/com/kraat/lostfilmnewtv/data/auth/AuthRepository.kt app/src/test/java/com/kraat/lostfilmnewtv/data/auth/AuthRepositoryTest.kt
git commit -m "feat: verify claimed auth sessions on tv"
```

## Chunk 2: Auth ViewModel State Machine

### Task 3: Replace boolean auth flags with explicit UI states

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthUiState.kt`
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/ui/auth/AuthViewModelTest.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthViewModel.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/data/model/AuthModels.kt`

- [ ] **Step 1: Write failing ViewModel tests for the approved flow**

```kotlin
@Test
fun startAuth_setsWaitingForPhoneOpen_afterPairingCreated() = runTest(dispatcher) { /* ... */ }

@Test
fun pollingMovesFromPendingToInProgressToVerifyingBeforeSuccess() = runTest(dispatcher) { /* ... */ }

@Test
fun expiredPairing_exposesGetNewCodeRecoveryState() = runTest(dispatcher) { /* ... */ }
```

- [ ] **Step 2: Run the targeted ViewModel tests and confirm they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.auth.AuthViewModelTest"
```

Expected: FAIL because `AuthViewModel` still exposes `isLoading/isPolling/error`.

- [ ] **Step 3: Implement the explicit state machine**

Implementation notes:
- Move `AuthUiState` out of `AuthViewModel.kt` into its own file, matching the existing `HomeUiState` / `DetailsUiState` pattern.
- Represent the screen with explicit phases such as:
  - `Idle`
  - `CreatingCode`
  - `WaitingForPhoneOpen`
  - `WaitingForPhoneLogin`
  - `ClaimingSession`
  - `VerifyingSession`
  - `Authenticated`
  - `Expired`
  - `RecoverableError`
- Let the ViewModel own the polling loop so it can update the status line when backend status changes from `PENDING` to `IN_PROGRESS`.
- When the user presses `Get new code`, discard the current attempt and immediately call `startPairing()` again.
- Keep `logout()` behavior intact.

- [ ] **Step 4: Rerun the ViewModel tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.auth.AuthViewModelTest"
```

Expected: PASS

- [ ] **Step 5: Commit the ViewModel slice**

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthUiState.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthViewModel.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/auth/AuthViewModelTest.kt
git commit -m "feat: add explicit auth flow states"
```

## Chunk 3: Simplified TV Auth Screen

### Task 4: Rebuild `AuthScreen` around the approved simple QR flow

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthScreen.kt`
- Create: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/AuthScreenTest.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`

- [ ] **Step 1: Write the failing auth screen UI tests**

Cover these behaviors:
- initial state shows `Начать вход`
- active pairing shows the three approved steps
- verifying state shows `Проверяем вход...`
- recoverable error shows `Получить новый код`

Suggested assertions:

```kotlin
composeRule.onNodeWithText("Начать вход").assertIsDisplayed()
composeRule.onNodeWithText("1. Откройте QR на телефоне").assertIsDisplayed()
composeRule.onNodeWithText("Получить новый код").assertIsDisplayed()
```

- [ ] **Step 2: Run the focused auth screen instrumentation test and confirm it fails**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AuthScreenTest
```

Expected: FAIL because the current screen still uses the older instruction copy and generic error UI.

- [ ] **Step 3: Implement the minimal screen refresh**

Implementation notes:
- Keep the screen layout simple and center-weighted.
- Preserve the existing QR rendering helper.
- Replace the old four-line instruction text with the approved three-step guidance:
  1. `Откройте QR на телефоне`
  2. `Войдите в LostFilm`
  3. `Вернитесь к телевизору, экран обновится сам`
- Keep a single short dynamic status line under the steps.
- Do not mention CAPTCHA on the initial QR screen.
- In expired/error states, replace the QR block with:
  - short human-readable text
  - primary `Получить новый код`
  - secondary `Назад`

- [ ] **Step 4: Rerun the auth screen test**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AuthScreenTest
```

Expected: PASS

- [ ] **Step 5: Commit the UI slice**

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthScreen.kt app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/AuthScreenTest.kt
git commit -m "feat: simplify lostfilm auth tv screen"
```

## Chunk 4: Final Verification

### Task 5: Run focused verification and a debug build

**Files:**
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/AuthRepository.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthUiState.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthViewModel.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthScreen.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/data/network/LostFilmSessionVerifier.kt`

- [ ] **Step 1: Run the new auth-focused unit tests together**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.network.LostFilmSessionVerifierTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.auth.AuthRepositoryTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.auth.AuthViewModelTest"
```

Expected: PASS

- [ ] **Step 2: Run the existing auth-related regression tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.auth.QrCodeGeneratorTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.network.AuthenticatedLostFilmHttpClientTest"
```

Expected: PASS

- [ ] **Step 3: Run the focused auth screen instrumentation test**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AuthScreenTest
```

Expected: PASS when a connected device/emulator is available

- [ ] **Step 4: Build the debug APK**

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: PASS

- [ ] **Step 5: Do one manual TV flow sanity check**

Checklist:

1. Start sign-in from TV.
2. Confirm the QR screen shows the three-step copy and no CAPTCHA warning.
3. Scan the QR and observe TV status move from waiting-for-open to waiting-for-login.
4. Complete phone login.
5. Confirm the TV shows `Проверяем вход...` before success.
6. Force one failed attempt if possible and confirm the primary recovery action is `Получить новый код`.

- [ ] **Step 6: Create the final implementation commit**

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt app/src/main/java/com/kraat/lostfilmnewtv/data/auth app/src/main/java/com/kraat/lostfilmnewtv/data/network app/src/main/java/com/kraat/lostfilmnewtv/ui/auth app/src/test/java/com/kraat/lostfilmnewtv/data/auth/AuthRepositoryTest.kt app/src/test/java/com/kraat/lostfilmnewtv/data/network/LostFilmSessionVerifierTest.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/auth/AuthViewModelTest.kt app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/AuthScreenTest.kt docs/superpowers/specs/2026-03-19-lostfilm-auth-ux-reliability-design.md docs/superpowers/plans/2026-03-19-lostfilm-auth-ux-reliability.md
git commit -m "feat: improve lostfilm auth ux and reliability"
```

## Notes

- Keep this change Android-focused unless the phone-side copy proves actively misleading during implementation.
- Do not widen the scope into a new pairing protocol or a multi-step TV wizard.
- Prefer small commits after each task so regression points stay easy to isolate.
