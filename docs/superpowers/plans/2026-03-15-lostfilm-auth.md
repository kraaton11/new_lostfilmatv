# LostFilm Android TV Authentication Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a working QR-based LostFilm login flow where the TV app pairs with the backend, the phone completes backend-hosted login plus CAPTCHA when required, the TV stores the claimed session securely, and authenticated cookies are used in LostFilm requests.

**Architecture:** The backend auth bridge owns pairing, phone-side login pages, server-side LostFilm login, CAPTCHA handling, and one-time session claim. The Android TV app owns QR rendering, pairing polling, secure session storage, auth-aware request headers, auth state restore, and logout while preserving anonymous browsing.

**Tech Stack:** Kotlin, Compose for TV, Navigation Compose, Coroutines, OkHttp, Room, AndroidX Security Crypto, FastAPI, Jinja2, httpx, unittest

---

**Spec Reference:** `docs/superpowers/specs/2026-03-15-lostfilm-auth-design.md`

## Current Status

### Completed

- [x] Anonymous browsing baseline preserved
- [x] Backend pairing lifecycle implemented
- [x] Backend `pairingSecret` + `phoneVerifier` contract implemented
- [x] Backend phone login page implemented
- [x] LostFilm JS login parsing implemented for `/ajaxik.users.php`
- [x] Backend CAPTCHA challenge step implemented
- [x] CAPTCHA image now proxied through backend pairing session
- [x] False-success phone page bug fixed
- [x] Android QR rendering implemented
- [x] Android secure session storage implemented
- [x] Android auth state restore implemented
- [x] Android logout implemented and verified on TV
- [x] Authenticated cookies are attached to LostFilm requests in the app
- [x] TV shows authenticated state correctly after login (`Выйти`)

### Remaining

- [ ] Verify authenticated cookies unlock authenticated-only LostFilm behavior in the app, not just stored state
- [ ] Add targeted regression coverage for Android auth restore/logout wiring where practical
- [ ] Clean up rough edges in backend/Android auth flow and docs
- [ ] Prepare final review and commit if requested

## Planned File Structure

- Backend config and wiring:
  - `backend/auth_bridge/backend/src/auth_bridge/config.py`
  - `backend/auth_bridge/backend/src/auth_bridge/main.py`
- Backend APIs:
  - `backend/auth_bridge/backend/src/auth_bridge/api/pairings.py`
  - `backend/auth_bridge/backend/src/auth_bridge/api/phone_flow.py`
- Backend schemas and services:
  - `backend/auth_bridge/backend/src/auth_bridge/schemas/pairing.py`
  - `backend/auth_bridge/backend/src/auth_bridge/schemas/session_payload.py`
  - `backend/auth_bridge/backend/src/auth_bridge/services/pairing_store.py`
  - `backend/auth_bridge/backend/src/auth_bridge/services/pairing_service.py`
  - `backend/auth_bridge/backend/src/auth_bridge/services/lostfilm_login_client.py`
- Backend templates:
  - `backend/auth_bridge/backend/src/auth_bridge/templates/login.html`
  - `backend/auth_bridge/backend/src/auth_bridge/templates/challenge.html`
  - `backend/auth_bridge/backend/src/auth_bridge/templates/success.html`
  - `backend/auth_bridge/backend/src/auth_bridge/templates/expired.html`
  - `backend/auth_bridge/backend/src/auth_bridge/templates/error.html`
- Backend tests:
  - `backend/auth_bridge/backend/tests/test_pairings.py`
  - `backend/auth_bridge/backend/tests/test_phone_flow.py`
  - `backend/auth_bridge/backend/tests/test_lostfilm_login_client.py`
- Android auth/session:
  - `app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt`
  - `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/SessionStore.kt`
  - `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/EncryptedSessionStore.kt`
  - `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/AuthRepository.kt`
  - `app/src/main/java/com/kraat/lostfilmnewtv/data/model/AuthModels.kt`
  - `app/src/main/java/com/kraat/lostfilmnewtv/data/network/AuthBridgeClient.kt`
  - `app/src/main/java/com/kraat/lostfilmnewtv/data/network/LostFilmHttpClient.kt`
  - `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
  - `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthViewModel.kt`
  - `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthScreen.kt`
  - `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/QrCodeGenerator.kt`
- Android tests:
  - `app/src/test/java/com/kraat/lostfilmnewtv/ui/auth/QrCodeGeneratorTest.kt`
  - `app/src/test/java/com/kraat/lostfilmnewtv/data/network/AuthenticatedLostFilmHttpClientTest.kt`

## Chunk 1: Backend Pairing And Phone Login Flow

### Task 1: Keep backend pairing + claim flow stable

**Files:**
- Verify: `backend/auth_bridge/backend/src/auth_bridge/api/pairings.py`
- Verify: `backend/auth_bridge/backend/src/auth_bridge/services/pairing_service.py`
- Verify: `backend/auth_bridge/backend/tests/test_pairings.py`

- [x] Pairing create/status/claim/finalize/release contract implemented
- [x] Protected pairing API with `pairingSecret`
- [x] Verifier-based phone route implemented
- [x] Lease-style claim flow implemented

### Task 2: Keep backend LostFilm login + CAPTCHA flow stable

**Files:**
- Verify: `backend/auth_bridge/backend/src/auth_bridge/services/lostfilm_login_client.py`
- Verify: `backend/auth_bridge/backend/src/auth_bridge/api/phone_flow.py`
- Verify: `backend/auth_bridge/backend/src/auth_bridge/templates/login.html`
- Verify: `backend/auth_bridge/backend/src/auth_bridge/templates/challenge.html`
- Verify: `backend/auth_bridge/backend/tests/test_lostfilm_login_client.py`
- Verify: `backend/auth_bridge/backend/tests/test_phone_flow.py`

- [x] JS login flow parsing implemented
- [x] CAPTCHA challenge detection implemented
- [x] Backend-proxied captcha image implemented
- [x] Success page now requires persisted confirmed state
- [ ] Manually verify one more full login + CAPTCHA + confirmed pairing pass after any further backend edits

## Chunk 2: Android Auth Flow

### Task 3: Keep Android auth UI, storage, and state restore stable

**Files:**
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthScreen.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthViewModel.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/navigation/AppNavGraph.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/EncryptedSessionStore.kt`

- [x] QR displayed on TV
- [x] Session stored in encrypted prefs
- [x] Auth state restored on app launch
- [x] Home screen reflects auth state (`Войти` / `Выйти`)
- [x] Logout verified on TV

### Task 4: Use stored cookies in app requests

**Files:**
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/data/network/LostFilmHttpClient.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/LostFilmApplication.kt`
- Test: `app/src/test/java/com/kraat/lostfilmnewtv/data/network/AuthenticatedLostFilmHttpClientTest.kt`

- [x] Added `AuthenticatedLostFilmHttpClient`
- [x] Attached `Cookie` header from stored session
- [x] Covered with unit test
- [ ] Manually verify authenticated-only LostFilm behavior in real app requests if/when a stable user-visible authenticated marker is available

## Chunk 3: Final Cleanup

### Task 5: Final verification and cleanup

**Files:**
- Verify: `docs/superpowers/specs/2026-03-15-lostfilm-auth-design.md`
- Verify: `docs/superpowers/plans/2026-03-15-lostfilm-auth.md`
- Verify: changed backend and Android auth files above

- [ ] Run backend suite

```powershell
Push-Location backend/auth_bridge/backend
python -m unittest discover -s tests -p "test_*.py"
Pop-Location
```

Expected: PASS

- [ ] Run targeted Android unit tests

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.auth.QrCodeGeneratorTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.network.AuthenticatedLostFilmHttpClientTest"
```

Expected: PASS

- [ ] Build Android app

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: PASS

- [ ] Perform one live end-to-end check

Checklist:

1. TV shows QR and code
2. phone opens backend verifier URL
3. login page appears
4. challenge page appears if required
5. success page appears only after real confirmed pairing
6. TV returns to authenticated home state with `Выйти`
7. logout returns home state to `Войти`

- [ ] Commit only if requested

```powershell
git add app backend/auth_bridge/backend docs/superpowers/specs/2026-03-15-lostfilm-auth-design.md docs/superpowers/plans/2026-03-15-lostfilm-auth.md
git commit -m "feat: add QR-based LostFilm TV authentication flow"
```

## Notes For Future Work

- Watched-state parsing/storage is still a follow-up area if user-specific markup needs to be surfaced in UI.
- If LostFilm changes the JS login/captcha flow again, update `LostFilmLoginClient` first and keep routes thin.
- If app process death during in-flight pairing becomes important, persist pairing-in-progress state explicitly instead of relying on in-memory `AuthViewModel` state.
