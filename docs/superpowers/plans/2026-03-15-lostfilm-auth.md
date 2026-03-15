# LostFilm Android TV Authentication Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add QR-based authentication to the Android TV app using the existing auth bridge for pairing plus short-lived phone-side LostFilm login bridging, then store the claimed LostFilm session securely on-device and surface watched-state without breaking anonymous browsing.

**Architecture:** The auth bridge remains the only backend component. It creates pairings, hosts the phone-side login bridge, handles LostFilm CAPTCHA or other challenge steps during login, and performs a one-time session handoff to the TV. After claim, the Android app stores the session locally and continues to fetch LostFilm pages directly while keeping anonymous browsing available when auth is absent or broken.

**Tech Stack:** Kotlin, Gradle Kotlin DSL, Compose for TV, Navigation Compose, Coroutines/Flow, ViewModel, OkHttp, Room, AndroidX Security Crypto, FastAPI, Jinja2, BeautifulSoup4, httpx, unittest, Docker Compose, GHCR

---

**Spec Reference:** `docs/superpowers/specs/2026-03-15-lostfilm-auth-design.md`

**Critical Assumption:** The existing auth bridge can complete a real LostFilm phone-side login bridge flow when a human solves CAPTCHA or other required challenges on the phone, and the backend can retain the resulting LostFilm session cookies for one-time claim. If this assumption fails during Chunk 1 Task 4 verification, stop implementation and revise the design again before continuing.

**Runtime Target:** Keep anonymous browsing working at every checkpoint. No task should leave the app in a state where authentication failures block the current release browsing flow.

## Planned File Structure

- Backend dependencies/config: `backend/auth_bridge/backend/pyproject.toml`, `backend/auth_bridge/backend/src/auth_bridge/config.py`
- Backend application wiring: `backend/auth_bridge/backend/src/auth_bridge/main.py`
- Backend APIs: `backend/auth_bridge/backend/src/auth_bridge/api/pairings.py`, `backend/auth_bridge/backend/src/auth_bridge/api/phone_flow.py`
- Backend schemas: `backend/auth_bridge/backend/src/auth_bridge/schemas/pairing.py`, `backend/auth_bridge/backend/src/auth_bridge/schemas/session_payload.py`
- Backend services: `backend/auth_bridge/backend/src/auth_bridge/services/pairing_store.py`, `backend/auth_bridge/backend/src/auth_bridge/services/pairing_service.py`, `backend/auth_bridge/backend/src/auth_bridge/services/lostfilm_login_client.py`
- Backend templates: `backend/auth_bridge/backend/src/auth_bridge/templates/pair.html`, `login.html`, `challenge.html`, `success.html`, `expired.html`, `error.html`
- Backend fixtures/tests: `backend/auth_bridge/backend/tests/fixtures/lostfilm-login-page.html`, `lostfilm-challenge-page.html`, `backend/auth_bridge/backend/tests/test_pairings.py`, `test_phone_flow.py`, `test_lostfilm_login_client.py`
- Android build config: `app/build.gradle.kts`, `gradle/libs.versions.toml`
- Android application wiring: `app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt`
- Navigation: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppDestination.kt`, `AppNavGraph.kt`
- Existing content models to extend: `app/src/main/java/com/kraat/lostfilmnewtv/data/model/ReleaseSummary.kt`, `ReleaseDetails.kt`
- New auth models: `app/src/main/java/com/kraat/lostfilmnewtv/data/model/AuthState.kt`, `PairingSession.kt`, `LostFilmSession.kt`, `WatchState.kt`
- Auth networking: `app/src/main/java/com/kraat/lostfilmnewtv/data/network/AuthBridgeClient.kt`, `AuthBridgeModels.kt`, `LostFilmHttpClient.kt`
- Auth/session storage: `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/SessionStore.kt`, `EncryptedSessionStore.kt`, `LostFilmCookieJar.kt`, `AuthRepository.kt`, `AuthRepositoryImpl.kt`
- Personal cache: `app/src/main/java/com/kraat/lostfilmnewtv/data/db/WatchStateEntity.kt`, `LostFilmDatabase.kt`, `ReleaseDao.kt`
- Personal parsing: `app/src/main/java/com/kraat/lostfilmnewtv/data/parser/WatchedStateParser.kt`, `LostFilmDetailsParser.kt`, `LostFilmListParser.kt`, `ParserMappers.kt`
- Repository integration: `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepository.kt`, `LostFilmRepositoryImpl.kt`
- Auth UI: `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthUiState.kt`, `AuthViewModel.kt`, `AuthScreen.kt`
- Debug auth helpers: `app/src/debug/java/com/kraat/lostfilmnewtv/debug/AuthDebugActions.kt`
- Settings UI: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt`
- Home/details UI updates: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeUiState.kt`, `HomeViewModel.kt`, `HomeScreen.kt`, `BottomInfoPanel.kt`, `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsUiState.kt`, `DetailsScreen.kt`
- Android tests: `app/src/test/java/com/kraat/lostfilmnewtv/data/auth/AuthRepositoryTest.kt`, `SessionStoreTest.kt`, `app/src/test/java/com/kraat/lostfilmnewtv/data/parser/WatchedStateParserTest.kt`, `app/src/test/java/com/kraat/lostfilmnewtv/data/repository/PersonalStateMergeTest.kt`, `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/AnonymousBrowsingSmokeTest.kt`, `AuthScreenTest.kt`, `HomeScreenTest.kt`, `DetailsScreenTest.kt`, `SettingsScreenTest.kt`
- Auth fixtures: `app/src/test/resources/fixtures/authenticated-new-page.html`, `authenticated-series-details.html`
- Operational docs: `docs/auth-bridge-ops.md`, optional `README.md`

## Chunk 0: Anonymous Browsing Baseline

### Task 0: Add a real-wiring anonymous browsing smoke test before auth work begins

**Files:**
- Create: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/AnonymousBrowsingSmokeTest.kt`

- [ ] **Step 1: Write the failing anonymous browsing smoke tests**

Add tests for:

```kotlin
@Test fun appLaunch_showsAnonymousHomeWithRealWiring() { }
@Test fun anonymousBrowse_opensDetailsWithoutSession() { }
```

- [ ] **Step 2: Run the smoke tests and confirm failure**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AnonymousBrowsingSmokeTest
```

Expected: FAIL because the real-wiring harness or assertions are not implemented correctly yet.

- [ ] **Step 3: Implement the minimal instrumentation smoke harness**

Implement only the test harness needed to launch the real app wiring and verify anonymous home and details browsing. Do not add auth production code in this task.

- [ ] **Step 4: Re-run the smoke tests**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AnonymousBrowsingSmokeTest
```

Expected: PASS

- [ ] **Step 5: Commit the anonymous browsing baseline**

Run:

```powershell
git add app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/AnonymousBrowsingSmokeTest.kt
git commit -m "test: add anonymous browsing smoke coverage"
```

## Chunk 1: Auth Bridge Pairing And Phone-Side Login Bridge

### Task 1: Finalize pairing API contract and in-memory pairing lifecycle

**Files:**
- Modify: `backend/auth_bridge/backend/src/auth_bridge/config.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/schemas/pairing.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/schemas/session_payload.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/services/pairing_store.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/services/pairing_service.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/api/pairings.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/main.py`
- Test: `backend/auth_bridge/backend/tests/test_pairings.py`

- [ ] **Step 1: Write the failing backend tests for create/poll/claim lifecycle**

Add tests for:

```python
def test_create_pairing_returns_code_and_polling_metadata(self) -> None: ...
def test_poll_pairing_returns_pending_until_confirmed(self) -> None: ...
def test_claim_requires_confirmed_pairing_and_is_one_time(self) -> None: ...
```

- [ ] **Step 2: Run backend tests to confirm the pairing API fails before implementation is complete**

Run:

```powershell
Push-Location backend/auth_bridge/backend
python -m unittest discover -s tests -p "test_*.py"
Pop-Location
```

Expected: FAIL in `test_pairings.py` until the lifecycle contract is implemented.

- [ ] **Step 3: Implement the minimal pairing lifecycle**

Implement:

- TTL settings in `config.py`
- request/response schemas for create, status, and claim
- in-memory TTL store keyed by `pairingId`
- `create_pairing`, `get_status`, `confirm_pairing`, `claim_session`
- `POST /api/pairings`
- `GET /api/pairings/{pairing_id}`
- `POST /api/pairings/{pairing_id}/claim`

Keep the store process-local for `v1`. The app can tolerate in-flight pairing loss on backend restart.

- [ ] **Step 4: Re-run backend tests and verify the pairing lifecycle passes**

Run:

```powershell
Push-Location backend/auth_bridge/backend
python -m unittest discover -s tests -p "test_*.py"
Pop-Location
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AnonymousBrowsingSmokeTest
```

Expected: PASS for `test_health.py`, `test_pairings.py`, and the anonymous browsing smoke test.

- [ ] **Step 5: Commit the pairing API foundation**

Run:

```powershell
git add backend/auth_bridge/backend/src/auth_bridge backend/auth_bridge/backend/tests
git commit -m "feat: add auth bridge pairing lifecycle"
```

### Task 2: Add phone-side pairing page, pairing statuses, and transient phone-flow state

**Files:**
- Modify: `backend/auth_bridge/backend/pyproject.toml`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/services/pairing_store.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/services/pairing_service.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/api/phone_flow.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/templates/pair.html`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/templates/login.html`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/templates/expired.html`
- Create: `backend/auth_bridge/backend/src/auth_bridge/templates/error.html`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/main.py`
- Test: `backend/auth_bridge/backend/tests/test_phone_flow.py`

- [ ] **Step 1: Write the failing tests for the pairing page and phone-side status transitions**

Add tests for:

```python
def test_pair_page_for_pending_code_shows_phone_login_entry(self) -> None: ...
def test_opening_pair_page_moves_pairing_to_awaiting_phone_login(self) -> None: ...
def test_expired_pair_shows_expired_page(self) -> None: ...
```

- [ ] **Step 2: Run backend tests and confirm the new phone-flow tests fail**

Run:

```powershell
Push-Location backend/auth_bridge/backend
python -m unittest discover -s tests -p "test_*.py"
Pop-Location
```

Expected: FAIL with missing phone-flow routes, templates, or status transitions.

- [ ] **Step 3: Implement the minimal phone-side pairing page and statuses**

Implement:

- pairing statuses that can represent at least `pending`, `awaiting_phone_login`, `awaiting_phone_challenge`, `confirmed`, `expired`, `failed`
- `GET /pair/{user_code}`
- template rendering for pending and expired states
- backend-side transition from `pending` to `awaiting_phone_login` when the phone flow is opened

Do not implement real LostFilm submission yet. This task is only the pairing page and state scaffolding.

- [ ] **Step 4: Re-run backend tests and verify the phone-flow scaffolding passes**

Run:

```powershell
Push-Location backend/auth_bridge/backend
python -m unittest discover -s tests -p "test_*.py"
Pop-Location
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AnonymousBrowsingSmokeTest
```

Expected: PASS for `test_pairings.py`, the new scaffolding assertions in `test_phone_flow.py`, and the anonymous browsing smoke test.

- [ ] **Step 5: Commit the phone-side pairing scaffold**

Run:

```powershell
git add backend/auth_bridge/backend/pyproject.toml backend/auth_bridge/backend/src/auth_bridge backend/auth_bridge/backend/tests
git commit -m "feat: add phone-side pairing flow scaffold"
```

### Task 3: Build the LostFilm login bridge client with hidden-field and CAPTCHA handling

**Files:**
- Modify: `backend/auth_bridge/backend/pyproject.toml`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/config.py`
- Create: `backend/auth_bridge/backend/tests/fixtures/lostfilm-login-page.html`
- Create: `backend/auth_bridge/backend/tests/fixtures/lostfilm-challenge-page.html`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/services/lostfilm_login_client.py`
- Test: `backend/auth_bridge/backend/tests/test_lostfilm_login_client.py`

- [ ] **Step 1: Save sanitized login-flow fixtures for the LostFilm login bridge**

Create fixtures that cover:

- a login page with required hidden fields
- a challenge page with CAPTCHA image and submit form

Manually scrub any user-identifying or secret values before committing them.

- [ ] **Step 2: Write the failing tests for the login bridge client**

Add tests for:

```python
def test_fetch_login_step_extracts_hidden_fields_and_form_action(self) -> None: ...
def test_submit_credentials_detects_captcha_challenge(self) -> None: ...
def test_complete_challenge_extracts_required_session_cookies(self) -> None: ...
```

Use `httpx.MockTransport` or `unittest.mock` so the red-green cycle does not depend on real LostFilm availability.

- [ ] **Step 3: Run the login-bridge tests and confirm failure**

Run:

```powershell
Push-Location backend/auth_bridge/backend
python -m unittest discover -s tests -p "test_*.py"
Pop-Location
```

Expected: FAIL because the login bridge client does not yet parse the LostFilm flow.

- [ ] **Step 4: Implement the minimal LostFilm login bridge client**

Implement:

- a typed representation of the current LostFilm login step
- fetch of the LostFilm login page
- extraction of hidden fields and form action
- detection of CAPTCHA or other challenge requirements
- follow-up submit that returns session cookies only after full success

Keep the bridge logic isolated in `lostfilm_login_client.py`. Do not leak LostFilm-specific parsing into route handlers.

- [ ] **Step 5: Re-run backend tests and verify the login bridge passes under mocked responses**

Run:

```powershell
Push-Location backend/auth_bridge/backend
python -m unittest discover -s tests -p "test_*.py"
Pop-Location
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AnonymousBrowsingSmokeTest
```

Expected: PASS for `test_lostfilm_login_client.py`, the previously green backend tests, and the anonymous browsing smoke test.

- [ ] **Step 6: Commit the LostFilm login bridge client**

Run:

```powershell
git add backend/auth_bridge/backend/pyproject.toml backend/auth_bridge/backend/src/auth_bridge backend/auth_bridge/backend/tests
git commit -m "feat: add lostfilm login bridge client"
```

### Task 4: Wire the end-to-end phone-side login bridge and perform the mandatory early validation checkpoint

**Files:**
- Modify: `backend/auth_bridge/backend/src/auth_bridge/api/phone_flow.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/services/pairing_service.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/services/pairing_store.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/main.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/templates/login.html`
- Create: `backend/auth_bridge/backend/src/auth_bridge/templates/challenge.html`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/templates/success.html`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/templates/error.html`
- Test: `backend/auth_bridge/backend/tests/test_phone_flow.py`
- Doc reference: `docs/auth-bridge-ops.md`

- [ ] **Step 1: Write the failing end-to-end phone-flow tests**

Add tests for:

```python
def test_submit_credentials_moves_pairing_to_awaiting_phone_challenge(self) -> None: ...
def test_submit_challenge_confirms_pairing_and_stores_session_payload(self) -> None: ...
def test_failed_phone_step_keeps_tv_pairing_retryable(self) -> None: ...
```

- [ ] **Step 2: Run backend tests and verify the new phone-flow tests fail**

Run:

```powershell
Push-Location backend/auth_bridge/backend
python -m unittest discover -s tests -p "test_*.py"
Pop-Location
```

Expected: FAIL with missing route wiring, missing challenge handling, or missing status transitions.

- [ ] **Step 3: Implement pairing-scoped login state and the credential submit handler**

Implement:

- pairing-scoped short-lived LostFilm login state on the backend
- `POST` handler for username and password submission
- transition from `awaiting_phone_login` to `awaiting_phone_challenge` or direct success when appropriate

- [ ] **Step 4: Re-run the targeted phone-flow tests and verify partial progress**

Run:

```powershell
Push-Location backend/auth_bridge/backend
python -m unittest discover -s tests -p "test_phone_flow.py"
Pop-Location
```

Expected: challenge-related tests may still fail, but credential-submit flow should now reach the expected intermediate state.

- [ ] **Step 5: Implement challenge submit, success and error pages, and final session handoff state**

Implement:

- `POST` handler for challenge submission
- page rendering for challenge, success, expired, and recoverable error states
- transition to `confirmed` only after valid LostFilm session cookies are captured
- one-time session payload storage for later TV `claim`

Do not add any steady-state content proxying.

- [ ] **Step 6: Re-run the backend suite and verify all backend tests pass**

Run:

```powershell
Push-Location backend/auth_bridge/backend
python -m unittest discover -s tests -p "test_*.py"
Pop-Location
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AnonymousBrowsingSmokeTest
```

Expected: PASS for the backend suite and the anonymous browsing smoke test.

- [ ] **Step 7: Perform the mandatory real-world validation checkpoint before any Android auth work continues**

Use `docs/auth-bridge-ops.md` for the existing environment and server details instead of rediscovering setup.

Run the current backend build on a phone-reachable URL in one terminal:

```powershell
$lanIp = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '169.254*' -and $_.InterfaceAlias -notmatch 'Loopback' } | Select-Object -First 1 -ExpandProperty IPAddress)
Push-Location backend/auth_bridge/backend
$env:PYTHONPATH = "src"
$env:AUTH_BRIDGE_PUBLIC_BASE_URL = "http://$lanIp:8000"
python -m uvicorn auth_bridge.main:app --host 0.0.0.0 --port 8000
Pop-Location
```

Then verify the reachable health endpoint from another terminal:

```powershell
$lanIp = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '169.254*' -and $_.InterfaceAlias -notmatch 'Loopback' } | Select-Object -First 1 -ExpandProperty IPAddress)
curl.exe "http://$lanIp:8000/health/live"
```

Expected: `{"status":"ok"}`

Then verify the real phone flow with concrete pairing and claim calls:

```powershell
$lanIp = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '169.254*' -and $_.InterfaceAlias -notmatch 'Loopback' } | Select-Object -First 1 -ExpandProperty IPAddress)
$baseUrl = "http://$lanIp:8000"
$pairing = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/pairings"
$pairing | ConvertTo-Json -Depth 5
# Open $pairing.verificationUrl on the phone and complete the LostFilm flow there.
$status = $null
do {
    Start-Sleep -Seconds 3
    $status = Invoke-RestMethod -Uri "$baseUrl/api/pairings/$($pairing.pairingId)"
    $status | ConvertTo-Json -Depth 5
} while ($status.status -eq "pending" -or $status.status -eq "awaiting_phone_login" -or $status.status -eq "awaiting_phone_challenge")
Invoke-RestMethod -Method Post -Uri "$baseUrl/api/pairings/$($pairing.pairingId)/claim" | ConvertTo-Json -Depth 6
```

Expected before claim:

- the pairing JSON includes the current `pairingId`, `userCode`, and `verificationUrl`
- the polling loop eventually reports `status: confirmed`
- `POST /api/pairings/{pairingId}/claim` returns a session payload with LostFilm cookies

If the phone cannot reach the dev machine on the local network, stop here and make the current backend build reachable from the phone before continuing. Do not proceed to Android auth work without a phone-reachable real-world checkpoint.

Also manually verify the browser part of the flow:

1. start a real pairing and open `/pair/{code}` on the phone
2. sign in with a disposable LostFilm test account
3. complete any required CAPTCHA or challenge step
4. verify the phone page reaches a clear success state
5. verify the pairing status reaches `confirmed`
6. verify `POST /api/pairings/{pairingId}/claim` returns a valid session payload

If CAPTCHA or other challenge requirements still prevent the backend from reliably obtaining a valid LostFilm session, stop here and revise the design again before continuing.

- [ ] **Step 8: Commit the working phone-side login bridge only after the real-world checkpoint passes**

Run:

```powershell
git add backend/auth_bridge/backend/src/auth_bridge backend/auth_bridge/backend/tests docs/auth-bridge-ops.md
git commit -m "feat: add lostfilm phone-side login bridge"
```

## Chunk 2: Android Auth Foundation

### Task 5: Add auth models and secure session storage

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/model/AuthState.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/model/PairingSession.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/model/LostFilmSession.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/model/WatchState.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/SessionStore.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/EncryptedSessionStore.kt`
- Test: `app/src/test/java/com/kraat/lostfilmnewtv/data/auth/SessionStoreTest.kt`
- Test: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/AnonymousBrowsingSmokeTest.kt`

- [ ] **Step 1: Write failing tests for secure session persistence**

Add tests for:

```kotlin
@Test fun saveAndReadSession_roundTripsCookies() = runTest { }
@Test fun clearSession_removesStoredAuthData() = runTest { }
@Test fun expiredMarker_isPersistedSeparatelyFromCookies() = runTest { }
```

- [ ] **Step 2: Run the new session-store tests to confirm the auth layer is missing**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.auth.SessionStoreTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AnonymousBrowsingSmokeTest
```

Expected: FAIL with missing auth model or storage classes.

- [ ] **Step 3: Add auth dependencies and implement encrypted session storage**

Implement:

- AndroidX Security Crypto dependency in the version catalog and app module
- serializable auth models
- `SessionStore` interface
- `EncryptedSessionStore` backed by `EncryptedSharedPreferences` or equivalent Keystore-backed storage

Expose operations:

```kotlin
interface SessionStore {
    suspend fun read(): LostFilmSession?
    suspend fun save(session: LostFilmSession)
    suspend fun markExpired()
    suspend fun clear()
}
```

- [ ] **Step 4: Re-run the session-store tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.auth.SessionStoreTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AnonymousBrowsingSmokeTest
```

Expected: PASS for secure session-store tests and the anonymous real-wiring smoke test.

- [ ] **Step 5: Commit the secure session-storage layer**

Run:

```powershell
git add app/build.gradle.kts gradle/libs.versions.toml app/src/main/java/com/kraat/lostfilmnewtv/data/model app/src/main/java/com/kraat/lostfilmnewtv/data/auth app/src/test/java/com/kraat/lostfilmnewtv/data/auth app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/AnonymousBrowsingSmokeTest.kt
git commit -m "feat: add secure auth session storage"
```

### Task 6: Add auth bridge client, cookie jar, repository, and application wiring

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/network/AuthBridgeModels.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/network/AuthBridgeClient.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/LostFilmCookieJar.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/AuthRepository.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/AuthRepositoryImpl.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/network/LostFilmHttpClient.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt`
- Test: `app/src/test/java/com/kraat/lostfilmnewtv/data/auth/AuthRepositoryTest.kt`
- Test: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/AnonymousBrowsingSmokeTest.kt`

- [ ] **Step 1: Write failing tests for pairing lifecycle, richer statuses, and logout**

Add tests for:

```kotlin
@Test fun startLogin_createsPairingAndExposesQrState() = runTest { }
@Test fun pollingAwaitingPhoneChallenge_updatesPairingStatusWithoutClaim() = runTest { }
@Test fun pollingConfirmedPairing_claimsAndStoresSession() = runTest { }
@Test fun logout_clearsSessionAndReturnsAnonymousState() = runTest { }
```

Mock the auth bridge client and session store.

- [ ] **Step 2: Run the auth repository tests to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.auth.AuthRepositoryTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AnonymousBrowsingSmokeTest
```

Expected: FAIL because the repository, client wiring, or anonymous real-wiring smoke path does not exist yet.

- [ ] **Step 3: Implement the auth bridge client and repository wiring**

Implement:

- auth bridge API DTOs with support for pairing statuses from the revised spec
- `AuthBridgeClient` with `createPairing`, `getPairingStatus`, `claimSession`
- `LostFilmCookieJar` that reads from `SessionStore`
- `AuthRepositoryImpl`
- `BuildConfig` field or equivalent constant for the auth bridge base URL
- `LostFilmApplication` wiring for:
  - session store
  - auth repository
  - authenticated LostFilm HTTP client sharing the cookie jar

Keep `LostFilmHttpClient` stable for content consumers where possible.

- [ ] **Step 4: Re-run the auth repository tests and existing build checks**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.auth.AuthRepositoryTest"
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AnonymousBrowsingSmokeTest
```

Expected:

- auth repository tests pass
- `:app:assembleDebug` is `BUILD SUCCESSFUL`
- the real-wiring anonymous browsing smoke test still passes

- [ ] **Step 5: Commit the Android auth foundation and DI wiring**

Run:

```powershell
git add app/build.gradle.kts app/src/main/java/com/kraat/lostfilmnewtv/data/network app/src/main/java/com/kraat/lostfilmnewtv/data/auth app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt app/src/test/java/com/kraat/lostfilmnewtv/data/auth app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/AnonymousBrowsingSmokeTest.kt
git commit -m "feat: add auth bridge client and session wiring"
```

## Chunk 3: Personal Watched-State Data Layer

### Task 7: Capture authenticated fixtures and implement the watched-state parser

**Files:**
- Create: `app/src/test/resources/fixtures/authenticated-new-page.html`
- Create: `app/src/test/resources/fixtures/authenticated-series-details.html`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/parser/WatchedStateParser.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/parser/LostFilmListParser.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/parser/LostFilmDetailsParser.kt`
- Test: `app/src/test/java/com/kraat/lostfilmnewtv/data/parser/WatchedStateParserTest.kt`

- [ ] **Step 1: Save sanitized authenticated HTML fixtures that show watched-state**

Use a temporary cookie file exported from a real authenticated LostFilm session and save fixtures:

```powershell
curl.exe -L -A "Mozilla/5.0" -b ".\tmp\lostfilm-cookies.txt" "https://www.lostfilm.today/new/" -o "app/src/test/resources/fixtures/authenticated-new-page.html"
curl.exe -L -A "Mozilla/5.0" -b ".\tmp\lostfilm-cookies.txt" "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/" -o "app/src/test/resources/fixtures/authenticated-series-details.html"
```

Then manually scrub secrets or user-identifying values before committing the fixtures.

- [ ] **Step 2: Write failing watched-state parser tests**

Add tests that assert watched-state is parsed from the authenticated fixtures:

```kotlin
@Test fun parsesWatchedStateFromAuthenticatedListFixture() { }
@Test fun parsesWatchedStateFromAuthenticatedDetailsFixture() { }
@Test fun missingMarkupFallsBackToUnknown() { }
```

- [ ] **Step 3: Run parser tests and confirm failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.parser.WatchedStateParserTest"
```

Expected: FAIL because the parser does not exist yet.

- [ ] **Step 4: Implement the watched-state parser and parser integration points**

Implement:

- `WatchedStateParser`
- extensions to list and details parsing so watched-state can be surfaced in parsed models without breaking anonymous fixtures
- `UNKNOWN` fallback when the expected authenticated markup is absent

- [ ] **Step 5: Re-run the parser tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.parser.*"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AnonymousBrowsingSmokeTest
```

Expected: PASS for the new watched-state parser tests, the existing anonymous parser coverage, and the real-wiring anonymous browsing smoke test.

- [ ] **Step 6: Commit the watched-state parser**

Run:

```powershell
git add app/src/test/resources/fixtures/authenticated-* app/src/main/java/com/kraat/lostfilmnewtv/data/parser app/src/test/java/com/kraat/lostfilmnewtv/data/parser
git commit -m "feat: parse authenticated watched state"
```

### Task 8: Add personal watched-state persistence and repository merge logic

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/db/WatchStateEntity.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/db/LostFilmDatabase.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/db/ReleaseDao.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/model/ReleaseSummary.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/model/ReleaseDetails.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/parser/ParserMappers.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepository.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryImpl.kt`
- Test: `app/src/test/java/com/kraat/lostfilmnewtv/data/repository/PersonalStateMergeTest.kt`

- [ ] **Step 1: Write failing repository tests for personal-state merge and cleanup**

Add tests for:

```kotlin
@Test fun authenticatedLoad_mergesWatchedStateIntoReleaseSummary() = runTest { }
@Test fun logoutClearsPersonalState_withoutDeletingAnonymousCache() = runTest { }
@Test fun repeatedAnonymousEquivalentAuthenticatedResponses_markSessionExpiredAndFallsBackToAnonymous() = runTest { }
```

- [ ] **Step 2: Run repository tests and verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.repository.PersonalStateMergeTest"
```

Expected: FAIL with missing watched-state schema or repository behavior.

- [ ] **Step 3: Implement watched-state schema and repository integration**

Implement:

- `WatchStateEntity`
- DAO methods to read, upsert, and clear watched-state by account
- merge logic in the repository so UI models receive personal watched-state when a valid session exists
- session-expiry detection only for strong expiry signals such as repeated anonymous-equivalent authenticated responses, while ordinary parse misses still fall back to `UNKNOWN`

Do not change the semantics of the existing anonymous cache cleanup rules.

- [ ] **Step 4: Re-run parser and repository tests together**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.parser.*" --tests "com.kraat.lostfilmnewtv.data.repository.*"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AnonymousBrowsingSmokeTest
```

Expected: PASS for parser tests, repository tests, and the real-wiring anonymous browsing smoke test.

- [ ] **Step 5: Commit the personal watched-state data layer**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/data/db app/src/main/java/com/kraat/lostfilmnewtv/data/model app/src/main/java/com/kraat/lostfilmnewtv/data/repository app/src/main/java/com/kraat/lostfilmnewtv/data/parser app/src/test/java/com/kraat/lostfilmnewtv/data/repository
git commit -m "feat: merge personal watched state into content"
```

## Chunk 4: TV Authentication And Settings UI

### Task 9: Add navigation, QR auth screen, and auth view model

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthUiState.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthViewModel.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthScreen.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/QrCodeBitmapFactory.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppDestination.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/AuthScreenTest.kt`

- [ ] **Step 1: Write failing UI tests for the auth screen states**

Add tests for:

```kotlin
@Test fun authScreen_showsQrAndCodeWhenPairingStarts() { }
@Test fun authScreen_showsPhoneChallengeWaitingMessage() { }
@Test fun authScreen_showsExpiredStateAndRetry() { }
@Test fun authScreen_showsSuccessStateBeforeReturn() { }
```

- [ ] **Step 2: Run the auth screen test and confirm it fails**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AuthScreenTest
```

Expected: FAIL because no auth destination or screen exists yet.

- [ ] **Step 3: Implement auth navigation and QR UI**

Implement:

- QR code generation dependency and helper in `QrCodeBitmapFactory.kt`
- `Auth` route in `AppDestination`
- `AuthViewModel` backed by `AuthRepository`
- `AuthScreen` states:
  - idle
  - creating pairing
  - waiting with QR and code
  - waiting for phone completion or challenge
  - expired
  - error
  - success

Use a generated QR bitmap for the `verificationUrl` and keep the screen fully DPAD-usable.

- [ ] **Step 4: Re-run the auth UI test**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AuthScreenTest
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AnonymousBrowsingSmokeTest
```

Expected: PASS for the new auth screen tests and the real-wiring anonymous browsing smoke test.

- [ ] **Step 5: Commit the QR auth screen flow**

Run:

```powershell
git add app/build.gradle.kts gradle/libs.versions.toml app/src/main/java/com/kraat/lostfilmnewtv/ui/auth app/src/main/java/com/kraat/lostfilmnewtv/navigation app/src/main/res/values/strings.xml app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/AuthScreenTest.kt app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/AnonymousBrowsingSmokeTest.kt
git commit -m "feat: add qr auth screen flow"
```

### Task 10: Integrate home CTA, settings, watched badges, logout, and expired-session UI

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt`
- Create: `app/src/debug/java/com/kraat/lostfilmnewtv/debug/AuthDebugActions.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppDestination.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeUiState.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/BottomInfoPanel.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/components/PosterCard.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsUiState.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
- Modify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/HomeScreenTest.kt`
- Modify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt`
- Create: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/SettingsScreenTest.kt`

- [ ] **Step 1: Extend UI tests for auth entry points and watched-state visibility**

Add assertions for:

```kotlin
@Test fun anonymousHome_showsSignInAction() { }
@Test fun loggedInHome_showsWatchedStateBadge() { }
@Test fun expiredSession_showsReloginPromptWithoutBreakingContent() { }
@Test fun settingsScreen_allowsLogout() { }
```

- [ ] **Step 2: Run the updated UI tests to verify failure**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.HomeScreenTest
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.DetailsScreenTest
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.SettingsScreenTest
```

Expected: FAIL because the UI still has no auth-aware state.

- [ ] **Step 3: Implement auth-aware home, details, and settings behavior**

Implement:

- home sign-in CTA
- settings route and reachable account entry point from existing navigation
- settings account section
- logout action
- debug-only helper actions for tests and manual smoke, including a way to trigger `SessionStore.markExpired()` without changing release behavior
- watched-state badge on posters and or in the bottom panel
- details watched-state presentation if available
- soft expired-session prompt that does not block anonymous content

Keep focus restoration behavior intact when navigating from home to auth or settings and back.

- [ ] **Step 4: Re-run the Android UI suite**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Expected: PASS for home, details, and auth instrumentation tests.

- [ ] **Step 5: Commit the TV auth UI integration**

Run:

```powershell
git add app/src/debug/java/com/kraat/lostfilmnewtv/debug/AuthDebugActions.kt app/src/main/java/com/kraat/lostfilmnewtv/navigation app/src/main/java/com/kraat/lostfilmnewtv/ui app/src/androidTest/java/com/kraat/lostfilmnewtv/ui
git commit -m "feat: integrate auth state into tv ui"
```

## Chunk 5: Verification, Docs, And Deployment Readiness

### Task 11: Verify the combined backend and Android implementation

**Files:**
- Modify: `docs/auth-bridge-ops.md`
- Optionally modify: `README.md`

- [ ] **Step 1: Update operational docs for the revised auth flow**

Document:

- backend pairing endpoints
- phone-side login bridge endpoints and expected statuses
- any new environment variables or Python dependencies
- current deploy and validation path using `auth.bazuka.pp.ua`
- Android-side login smoke steps

- [ ] **Step 2: Run the backend verification suite**

Run:

```powershell
Push-Location backend/auth_bridge/backend
python -m unittest discover -s tests -p "test_*.py"
Pop-Location
```

Expected: PASS

- [ ] **Step 3: Run the Android verification suite**

Run:

```powershell
.\gradlew.bat testDebugUnitTest lint assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Expected: both commands `BUILD SUCCESSFUL`

- [ ] **Step 4: Run one real QR-to-phone-to-TV smoke test**

Verify on emulator or device:

1. open home
2. start sign-in
3. complete phone-side LostFilm login bridge flow on the QR URL
4. return to home and confirm watched-state appears
5. sign out and confirm the app returns to anonymous mode
6. use `app/src/debug/java/com/kraat/lostfilmnewtv/debug/AuthDebugActions.kt` to force an expired-session state
7. verify the app shows a re-login prompt while anonymous browsing still works
8. complete QR re-login and confirm watched-state returns

Record any environment-specific behavior in `docs/auth-bridge-ops.md`.

- [ ] **Step 5: Commit docs and final verification notes**

Run:

```powershell
git add docs/auth-bridge-ops.md README.md
git commit -m "docs: capture auth bridge verification flow"
```

## Execution Notes

- Implement chunks in order. Chunk 1 is the highest-risk integration checkpoint.
- Do not skip the real-world validation in Chunk 1 Task 4. If the backend cannot complete the real LostFilm phone-side login bridge reliably, stop implementation and revise the design.
- Preserve anonymous browsing at every checkpoint.
- Use `docs/auth-bridge-ops.md` for the existing server and deploy path instead of rediscovering environment details.
- Keep the backend limited to pairing, short-lived phone-side login bridging, and one-time session transfer.
- If the watched-state markup differs between list and details pages, keep the parser split instead of forcing a shared selector abstraction too early.
- If instrumentation is unavailable in the current environment, run all unit and build checks you can, and explicitly record the gap before claiming completion.
