# LostFilm Android TV Authentication Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add QR-based authentication to the Android TV app, store a LostFilm session securely on-device, surface watched-state, and extend the auth bridge backend to support pairing plus one-time session handoff.

**Architecture:** The auth bridge remains a short-lived pairing service and session handoff point, while the Android TV app stores the claimed LostFilm session locally and continues to fetch LostFilm pages directly. Personal watched-state is stored separately from the anonymous release cache and merged only in UI-facing models.

**Tech Stack:** Kotlin, Gradle Kotlin DSL, Compose for TV, Navigation Compose, Coroutines/Flow, ViewModel, OkHttp, Room, AndroidX Security Crypto, FastAPI, httpx, unittest, Docker Compose, GHCR

---

**Spec Reference:** `docs/superpowers/specs/2026-03-15-lostfilm-auth-design.md`

**Critical Assumption:** The phone-side auth flow is allowed to submit LostFilm credentials to the auth bridge backend over HTTPS, and the backend can exchange them for a stable LostFilm web session without a mandatory CAPTCHA wall. If this assumption fails during Chunk 1 verification, stop implementation and revise the spec before continuing.

**Runtime Target:** Keep anonymous browsing working at every checkpoint. No task should leave the app in a state where authentication failures block the current release browsing flow.

## Planned File Structure

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
- Settings UI: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt`
- Home/details UI updates: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeUiState.kt`, `HomeViewModel.kt`, `HomeScreen.kt`, `BottomInfoPanel.kt`, `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsUiState.kt`, `DetailsScreen.kt`
- Android tests: `app/src/test/java/com/kraat/lostfilmnewtv/data/auth/AuthRepositoryTest.kt`, `SessionStoreTest.kt`, `app/src/test/java/com/kraat/lostfilmnewtv/data/parser/WatchedStateParserTest.kt`, `app/src/test/java/com/kraat/lostfilmnewtv/data/repository/PersonalStateMergeTest.kt`, `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/AuthScreenTest.kt`, `HomeScreenTest.kt`, `DetailsScreenTest.kt`
- Auth fixtures: `app/src/test/resources/fixtures/authenticated-new-page.html`, `authenticated-series-details.html`
- Backend config and routes: `backend/auth_bridge/backend/src/auth_bridge/config.py`, `main.py`, `api/pairings.py`, `api/phone_flow.py`
- Backend schemas and services: `backend/auth_bridge/backend/src/auth_bridge/schemas/pairing.py`, `schemas/session_payload.py`, `services/pairing_store.py`, `services/pairing_service.py`, `services/lostfilm_login_client.py`
- Backend templates: `backend/auth_bridge/backend/src/auth_bridge/templates/pair.html`, `login.html`, `success.html`, `expired.html`
- Backend tests: `backend/auth_bridge/backend/tests/test_pairings.py`, `test_phone_flow.py`, `test_lostfilm_login_client.py`
- Operational docs: `docs/auth-bridge-ops.md`, optional backend README notes if login/deploy steps change

## Chunk 1: Auth Bridge Backend

### Task 1: Add pairing API contract and in-memory pairing lifecycle

**Files:**
- Create: `backend/auth_bridge/backend/src/auth_bridge/config.py`
- Create: `backend/auth_bridge/backend/src/auth_bridge/schemas/pairing.py`
- Create: `backend/auth_bridge/backend/src/auth_bridge/schemas/session_payload.py`
- Create: `backend/auth_bridge/backend/src/auth_bridge/services/pairing_store.py`
- Create: `backend/auth_bridge/backend/src/auth_bridge/services/pairing_service.py`
- Create: `backend/auth_bridge/backend/src/auth_bridge/api/pairings.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/main.py`
- Test: `backend/auth_bridge/backend/tests/test_pairings.py`

- [ ] **Step 1: Write failing backend tests for create/poll/claim lifecycle**

Add tests for:

```python
def test_create_pairing_returns_code_and_polling_metadata(self) -> None: ...
def test_poll_pairing_returns_pending_until_confirmed(self) -> None: ...
def test_claim_requires_confirmed_pairing_and_is_one_time(self) -> None: ...
```

- [ ] **Step 2: Run backend tests to confirm the pairing API does not exist yet**

Run:

```powershell
Push-Location backend/auth_bridge/backend
python -m unittest discover -s tests -p "test_*.py"
Pop-Location
```

Expected: FAIL with missing pairing routes/services

- [ ] **Step 3: Implement minimal pairing domain and HTTP endpoints**

Implement:

- TTL settings in `config.py`
- request/response schemas for create/status/claim
- in-memory TTL store keyed by `pairingId`
- service methods for `create_pairing`, `get_status`, `confirm_pairing`, `claim_session`
- `POST /api/pairings`
- `GET /api/pairings/{pairing_id}`
- `POST /api/pairings/{pairing_id}/claim`

Keep the store process-local for `v1`. The app can tolerate in-flight pairing loss on server restart.

- [ ] **Step 4: Re-run backend tests and verify the pairing lifecycle passes**

Run:

```powershell
Push-Location backend/auth_bridge/backend
python -m unittest discover -s tests -p "test_*.py"
Pop-Location
```

Expected: PASS for `test_health.py` and `test_pairings.py`

- [ ] **Step 5: Commit the pairing API foundation**

Run:

```powershell
git add backend/auth_bridge/backend/src/auth_bridge backend/auth_bridge/backend/tests
git commit -m "feat: add auth bridge pairing api"
```

### Task 2: Add phone-side login flow and LostFilm session acquisition service

**Files:**
- Create: `backend/auth_bridge/backend/src/auth_bridge/services/lostfilm_login_client.py`
- Create: `backend/auth_bridge/backend/src/auth_bridge/api/phone_flow.py`
- Create: `backend/auth_bridge/backend/src/auth_bridge/templates/pair.html`
- Create: `backend/auth_bridge/backend/src/auth_bridge/templates/login.html`
- Create: `backend/auth_bridge/backend/src/auth_bridge/templates/success.html`
- Create: `backend/auth_bridge/backend/src/auth_bridge/templates/expired.html`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/main.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/services/pairing_service.py`
- Test: `backend/auth_bridge/backend/tests/test_phone_flow.py`
- Test: `backend/auth_bridge/backend/tests/test_lostfilm_login_client.py`

- [ ] **Step 1: Write failing tests for the phone-side completion flow**

Add tests for:

```python
def test_pair_page_shows_login_form_for_pending_code(self) -> None: ...
def test_submit_credentials_confirms_pairing_and_stores_session_payload(self) -> None: ...
def test_lostfilm_login_client_extracts_required_cookies_from_mocked_responses(self) -> None: ...
```

Mock LostFilm HTTP calls with `unittest.mock` or `httpx.MockTransport` so no real credentials are needed for CI/local red-green.

- [ ] **Step 2: Run backend tests and verify the new auth flow tests fail**

Run:

```powershell
Push-Location backend/auth_bridge/backend
python -m unittest discover -s tests -p "test_*.py"
Pop-Location
```

Expected: FAIL with missing routes/templates/login client

- [ ] **Step 3: Implement the phone-side web flow and session acquisition**

Implement:

- `GET /pair/{user_code}` to show pairing status / login entry
- `POST /pair/{user_code}/login` to accept LostFilm credentials over HTTPS
- `LostFilmLoginClient` that submits credentials to LostFilm and extracts the cookies required for authenticated browsing
- handoff from login success into `confirm_pairing`
- success and expired pages

Keep the backend contract testable by isolating LostFilm-specific network logic inside `LostFilmLoginClient`.

- [ ] **Step 4: Re-run backend tests and perform one manual smoke validation**

Run:

```powershell
Push-Location backend/auth_bridge/backend
python -m unittest discover -s tests -p "test_*.py"
Pop-Location
```

Expected: PASS

Then manually verify in a dev environment:

```powershell
curl.exe http://127.0.0.1:8000/health/live
```

Expected: `{"status":"ok"}`

If a disposable LostFilm test account is available, also manually complete one `/pair/{code}` login in a browser. If CAPTCHA or mandatory extra steps block backend-side login, stop here and revise the design before continuing.

- [ ] **Step 5: Commit the phone-side auth flow**

Run:

```powershell
git add backend/auth_bridge/backend/src/auth_bridge backend/auth_bridge/backend/tests
git commit -m "feat: add phone-side auth bridge flow"
```

## Chunk 2: Android Auth Foundation

### Task 3: Add auth models and secure session storage

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
```

Expected: FAIL with missing auth model/storage classes and dependency symbols

- [ ] **Step 3: Add auth dependencies and implement encrypted session storage**

Implement:

- AndroidX Security Crypto dependency in the version catalog and app module
- serializable auth models
- `SessionStore` interface
- `EncryptedSessionStore` backed by `EncryptedSharedPreferences` or an equivalently Keystore-backed encrypted store

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
```

Expected: PASS

- [ ] **Step 5: Commit the secure session-storage layer**

Run:

```powershell
git add app/build.gradle.kts gradle/libs.versions.toml app/src/main/java/com/kraat/lostfilmnewtv/data/model app/src/main/java/com/kraat/lostfilmnewtv/data/auth app/src/test/java/com/kraat/lostfilmnewtv/data/auth
git commit -m "feat: add secure auth session storage"
```

### Task 4: Add auth bridge client, cookie jar, and application wiring

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/network/AuthBridgeModels.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/network/AuthBridgeClient.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/LostFilmCookieJar.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/AuthRepository.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/AuthRepositoryImpl.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/network/LostFilmHttpClient.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt`
- Test: `app/src/test/java/com/kraat/lostfilmnewtv/data/auth/AuthRepositoryTest.kt`

- [ ] **Step 1: Write failing tests for pairing lifecycle and logout**

Add tests for:

```kotlin
@Test fun startLogin_createsPairingAndExposesQrState() = runTest { }
@Test fun pollingConfirmedPairing_claimsAndStoresSession() = runTest { }
@Test fun logout_clearsSessionAndReturnsAnonymousState() = runTest { }
```

Mock the auth bridge client and session store.

- [ ] **Step 2: Run the auth repository tests to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.auth.AuthRepositoryTest"
```

Expected: FAIL because the repository/client wiring does not exist yet

- [ ] **Step 3: Implement auth bridge client, cookie jar, and app dependency graph**

Implement:

- auth bridge API DTOs
- `AuthBridgeClient` with `createPairing`, `getPairingStatus`, `claimSession`
- `LostFilmCookieJar` that reads from `SessionStore`
- `AuthRepositoryImpl`
- `BuildConfig` field or equivalent constant for the auth bridge base URL
- `LostFilmApplication` wiring for:
  - session store
  - auth repository
  - authenticated LostFilm HTTP client sharing the cookie jar

Keep `LostFilmHttpClient` API stable for content consumers where possible.

- [ ] **Step 4: Re-run the auth repository tests and existing build checks**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.auth.AuthRepositoryTest"
.\gradlew.bat :app:assembleDebug
```

Expected: both commands `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the auth foundation and DI wiring**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/data/network app/src/main/java/com/kraat/lostfilmnewtv/data/auth app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt app/src/test/java/com/kraat/lostfilmnewtv/data/auth
git commit -m "feat: add auth bridge client and session wiring"
```

## Chunk 3: Personal Watched-State Data Layer

### Task 5: Capture authenticated fixtures and implement watched-state parser

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

Expected: FAIL because the parser does not exist yet

- [ ] **Step 4: Implement the watched-state parser and parser integration points**

Implement:

- `WatchedStateParser`
- extensions to list/details parsing so watched-state can be surfaced in parsed models without breaking anonymous fixtures
- `UNKNOWN` fallback when the expected authenticated markup is absent

- [ ] **Step 5: Commit the watched-state parser**

Run:

```powershell
git add app/src/test/resources/fixtures/authenticated-* app/src/main/java/com/kraat/lostfilmnewtv/data/parser app/src/test/java/com/kraat/lostfilmnewtv/data/parser
git commit -m "feat: parse authenticated watched state"
```

### Task 6: Add personal watched-state persistence and repository merge logic

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
@Test fun missingAuthenticatedMarkup_marksSessionExpiredAndFallsBackToAnonymous() = runTest { }
```

- [ ] **Step 2: Run repository tests and verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.repository.PersonalStateMergeTest"
```

Expected: FAIL with missing watched-state schema/repository behavior

- [ ] **Step 3: Implement watched-state schema and repository integration**

Implement:

- `WatchStateEntity`
- DAO methods to read, upsert, and clear watched-state by account
- merge logic in the repository so UI models receive personal watched-state when a valid session exists
- session-expiry detection path that clears auth state softly and preserves anonymous content

Do not change the semantics of the existing anonymous cache cleanup rules.

- [ ] **Step 4: Re-run parser and repository tests together**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.parser.*" --tests "com.kraat.lostfilmnewtv.data.repository.*"
```

Expected: PASS

- [ ] **Step 5: Commit the personal watched-state data layer**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/data/db app/src/main/java/com/kraat/lostfilmnewtv/data/model app/src/main/java/com/kraat/lostfilmnewtv/data/repository app/src/main/java/com/kraat/lostfilmnewtv/data/parser app/src/test/java/com/kraat/lostfilmnewtv/data/repository
git commit -m "feat: merge personal watched state into content"
```

## Chunk 4: TV Authentication And Settings UI

### Task 7: Add navigation, QR auth screen, and auth view model

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthUiState.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthViewModel.kt`
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthScreen.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppDestination.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/AuthScreenTest.kt`

- [ ] **Step 1: Write failing UI tests for the auth screen states**

Add tests for:

```kotlin
@Test fun authScreen_showsQrAndCodeWhenPairingStarts() { }
@Test fun authScreen_showsExpiredStateAndRetry() { }
@Test fun authScreen_showsSuccessStateBeforeReturn() { }
```

- [ ] **Step 2: Run the auth screen test and confirm it fails**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AuthScreenTest
```

Expected: FAIL because no auth destination or screen exists

- [ ] **Step 3: Implement auth navigation and QR UI**

Implement:

- `Auth` route in `AppDestination`
- `AuthViewModel` backed by `AuthRepository`
- `AuthScreen` states:
  - idle
  - creating pairing
  - waiting with QR and code
  - expired
  - error
  - success

Use a generated QR bitmap for the `verificationUrl` and keep the screen fully DPAD-usable.

- [ ] **Step 4: Re-run the auth UI test**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.AuthScreenTest
```

Expected: PASS

- [ ] **Step 5: Commit the QR auth screen flow**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/auth app/src/main/java/com/kraat/lostfilmnewtv/navigation app/src/main/res/values/strings.xml app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/AuthScreenTest.kt
git commit -m "feat: add qr auth screen flow"
```

### Task 8: Integrate home CTA, settings, watched badges, logout, and expired-session UI

**Files:**
- Create: `app/src/main/java/com/kraat/lostfilmnewtv/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeUiState.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/BottomInfoPanel.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/components/PosterCard.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsUiState.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
- Modify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/HomeScreenTest.kt`
- Modify: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/DetailsScreenTest.kt`

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
```

Expected: FAIL because the UI still has no auth-aware state

- [ ] **Step 3: Implement auth-aware home/details/settings behavior**

Implement:

- home sign-in CTA
- settings account section
- logout action
- watched-state badge on posters and/or in bottom panel
- details watched-state presentation if available
- soft expired-session prompt that does not block anonymous content

Keep focus restoration behavior intact when navigating from home to auth/settings and back.

- [ ] **Step 4: Re-run the Android UI suite**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Expected: PASS for home/details/auth instrumentation tests

- [ ] **Step 5: Commit the TV auth UI integration**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui app/src/androidTest/java/com/kraat/lostfilmnewtv/ui
git commit -m "feat: integrate auth state into tv ui"
```

## Chunk 5: Verification, Docs, And Deployment Readiness

### Task 9: Verify the combined backend and Android implementation

**Files:**
- Modify: `docs/auth-bridge-ops.md`
- Optionally modify: `README.md`

- [ ] **Step 1: Update operational docs for the new auth flow**

Document:

- backend pairing endpoints
- expected auth image/build path
- any new environment variables
- phone-side flow verification notes
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

- [ ] **Step 4: Run one manual smoke test for the real QR flow**

Verify on emulator or device:

1. open home
2. start sign-in
3. complete phone login
4. return to home and confirm watched-state appears
5. sign out and confirm the app returns to anonymous mode

Record any gaps in `docs/auth-bridge-ops.md` if environment-specific behavior is discovered.

- [ ] **Step 5: Commit docs and final verification notes**

Run:

```powershell
git add docs/auth-bridge-ops.md README.md
git commit -m "docs: capture auth flow verification"
```

## Execution Notes

- Implement chunks in order. Chunk 1 is the highest-risk integration checkpoint.
- Do not skip the real-world validation note in Task 2. If LostFilm login cannot be acquired server-side, the rest of the plan must stop.
- Preserve anonymous browsing at every checkpoint.
- Prefer small interfaces and constructor injection over expanding `LostFilmRepositoryImpl` into a second app container.
- If the watched-state markup differs between list and details pages, keep the parser split instead of forcing a shared selector abstraction too early.
- If instrumentation is unavailable in the current environment, run all unit/build checks and explicitly record the gap before claiming completion.
