# LostFilm QR Browser Proxy Auth Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fragile backend credential-submit phone flow with a wildcard browser-proxy QR flow that keeps TV pairing automatic and produces a real LostFilm browser session before the TV claims cookies.

**Architecture:** Keep the current TV pairing, poll, claim, finalize, and release contract, but change the phone-side flow entirely. The backend should mint wildcard verification URLs, proxy LostFilm through pairing-bound subdomains, hold the upstream LostFilm cookie jar server-side, detect real authenticated browser state before confirming the pairing, and then hand the final upstream cookies to Android through the existing claim path.

**Tech Stack:** Python 3.12, FastAPI, httpx, Jinja2, Docker Compose, Caddy, Kotlin Android client, JUnit/unittest

---

**Spec Reference:** `docs/superpowers/specs/2026-03-19-lostfilm-qr-browser-proxy-auth-design.md`

## Planned File Structure

- `backend/auth_bridge/backend/src/auth_bridge/config.py`
  - Add explicit wildcard/public-domain settings instead of relying only on a single `public_base_url`.
- `backend/auth_bridge/backend/src/auth_bridge/services/pairing_service.py`
  - Generate wildcard verification URLs and own the transition from proxied phone auth to `CONFIRMED`.
- `backend/auth_bridge/backend/src/auth_bridge/services/pairing_store.py`
  - Extend pairing records with proxy-session state and cleanup hooks for the new server-side upstream cookie jar.
- `backend/auth_bridge/backend/src/auth_bridge/api/phone_flow.py`
  - Keep or slim the legacy form-based flow, but stop using it as the primary QR target once the new wildcard flow is live.
- `backend/auth_bridge/backend/src/auth_bridge/api/wildcard_proxy.py`
  - New router that resolves `phone_verifier` from wildcard hosts and proxies pairing-bound browser traffic.
- `backend/auth_bridge/backend/src/auth_bridge/services/lostfilm_proxy_service.py`
  - New focused proxy service that forwards requests to LostFilm, injects upstream cookies, captures upstream `Set-Cookie`, and rewrites redirects/URLs.
- `backend/auth_bridge/backend/src/auth_bridge/services/proxy_session_store.py`
  - New small store for per-pairing upstream cookie jar and proxy flow metadata.
- `backend/auth_bridge/backend/src/auth_bridge/services/lostfilm_auth_detector.py`
  - New helper that decides when a proxied browser flow is truly authenticated.
- `backend/auth_bridge/backend/src/auth_bridge/templates/phone_shell.html`
  - New minimal shell or interstitial page for the wildcard phone flow.
- `backend/auth_bridge/backend/tests/test_pairings.py`
  - Update pairing URL expectations to wildcard subdomains.
- `backend/auth_bridge/backend/tests/test_wildcard_proxy.py`
  - New focused tests for host resolution, expiry handling, and phone flow routing.
- `backend/auth_bridge/backend/tests/test_lostfilm_proxy_service.py`
  - New unit tests for request forwarding, cookie capture, and response rewriting.
- `backend/auth_bridge/backend/tests/test_lostfilm_auth_detector.py`
  - New tests for anonymous vs authenticated proxied page detection.
- `backend/auth_bridge/backend/tests/test_phone_flow.py`
  - Update to reflect that QR no longer lands on `/pair/{phone_verifier}` as the primary path.
- `backend/auth_bridge/.env.example`
  - Add wildcard/base-domain configuration.
- `backend/auth_bridge/docker-compose.yml`
  - Pass the new wildcard/base-domain settings into the container.
- `docs/auth-bridge-server-install.md`
  - Update deployment instructions for wildcard DNS, wildcard TLS, and Caddy routing.
- `app/src/main/java/com/kraat/lostfilmnewtv/data/network/AuthBridgeClient.kt`
  - Verify no behavioral change is needed beyond accepting the new verification URL shape.
- `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/AuthRepository.kt`
  - Keep current claim/finalize/release flow unless backend contract changes force a small adjustment.

Keep unchanged unless the implementation proves otherwise:

- `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthScreen.kt`
- `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthViewModel.kt`
- `app/src/main/java/com/kraat/lostfilmnewtv/data/network/LostFilmSessionVerifier.kt`

## Chunk 1: Wildcard Contract And Pairing URL Shape

### Task 1: Add failing tests for wildcard verification URLs

**Files:**
- Modify: `backend/auth_bridge/backend/tests/test_pairings.py`
- Modify: `backend/auth_bridge/backend/tests/test_phone_flow.py`
- Verify: `backend/auth_bridge/backend/src/auth_bridge/schemas/pairing.py`

- [ ] **Step 1: Write the failing pairing tests**

Add expectations that pairing creation now emits a wildcard verification URL, for example:

```python
self.assertTrue(payload["verificationUrl"].startswith("https://"))
self.assertIn(".auth.bazuka.pp.ua/", payload["verificationUrl"])
self.assertNotIn("/pair/", payload["verificationUrl"])
```

Add one legacy guard in `test_phone_flow.py` that opening the old `/pair/{phone_verifier}` path is no longer the primary QR contract.

- [ ] **Step 2: Run the targeted backend tests and confirm they fail**

Run:

```powershell
.\.venv\Scripts\python -m unittest tests.test_pairings tests.test_phone_flow
```

Expected: FAIL because `PairingService._build_create_response()` still emits `/pair/{phone_verifier}` URLs.

- [ ] **Step 3: Implement minimal config and URL-generation support**

Modify `backend/auth_bridge/backend/src/auth_bridge/config.py` to add an explicit wildcard/base-domain setting, for example:

```python
public_base_domain: str = "auth.example.test"
```

Use aliases so both the current public base URL and the new base domain remain configurable from `.env`.

Modify `backend/auth_bridge/backend/src/auth_bridge/services/pairing_service.py` so verification URLs are generated like:

```python
verification_url = f"https://{record.phone_verifier}.{self._settings.public_base_domain}/"
```

Do not touch the TV pairing secret or polling contract in this task.

- [ ] **Step 4: Rerun the targeted tests**

Run:

```powershell
.\.venv\Scripts\python -m unittest tests.test_pairings tests.test_phone_flow
```

Expected: PASS

- [ ] **Step 5: Commit the wildcard URL slice**

```powershell
git add backend/auth_bridge/backend/src/auth_bridge/config.py backend/auth_bridge/backend/src/auth_bridge/services/pairing_service.py backend/auth_bridge/backend/tests/test_pairings.py backend/auth_bridge/backend/tests/test_phone_flow.py
git commit -m "feat: emit wildcard auth verification urls"
```

## Chunk 2: Pairing-Bound Wildcard Router Skeleton

### Task 2: Add host resolution and pairing-bound wildcard entry routing

**Files:**
- Create: `backend/auth_bridge/backend/src/auth_bridge/api/wildcard_proxy.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/main.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/services/pairing_service.py`
- Create: `backend/auth_bridge/backend/tests/test_wildcard_proxy.py`

- [ ] **Step 1: Write the failing wildcard router tests**

Cover these behaviors:

```python
def test_wildcard_host_resolves_active_pairing(self): ...
def test_unknown_wildcard_host_returns_404(self): ...
def test_expired_wildcard_host_returns_410(self): ...
```

Use `Host` headers such as `abc123.auth.bazuka.pp.ua` in the FastAPI test client.

- [ ] **Step 2: Run the targeted wildcard router tests and confirm they fail**

Run:

```powershell
.\.venv\Scripts\python -m unittest tests.test_wildcard_proxy
```

Expected: FAIL because there is no wildcard router yet.

- [ ] **Step 3: Implement the minimal wildcard router**

Implementation shape:

```python
router = APIRouter()

@router.get("/", response_class=HTMLResponse)
def wildcard_entry(request: Request) -> HTMLResponse:
    phone_verifier = pairing_service.resolve_phone_verifier_from_host(request.headers["host"])
    ...
```

Implementation notes:
- Add a small helper in `PairingService` to resolve and validate `phone_verifier` from the host.
- Return:
  - `404` for unknown hosts
  - `410` for expired pairings
  - a simple placeholder shell page for active pairings
- Register the new router in `main.py` after the API routers.

- [ ] **Step 4: Rerun the wildcard router tests**

Run:

```powershell
.\.venv\Scripts\python -m unittest tests.test_wildcard_proxy
```

Expected: PASS

- [ ] **Step 5: Commit the router skeleton**

```powershell
git add backend/auth_bridge/backend/src/auth_bridge/api/wildcard_proxy.py backend/auth_bridge/backend/src/auth_bridge/main.py backend/auth_bridge/backend/src/auth_bridge/services/pairing_service.py backend/auth_bridge/backend/tests/test_wildcard_proxy.py
git commit -m "feat: add wildcard pairing router skeleton"
```

## Chunk 3: Server-Side Upstream Cookie Jar And Proxy Forwarding

### Task 3: Add a pairing-scoped upstream proxy session store

**Files:**
- Create: `backend/auth_bridge/backend/src/auth_bridge/services/proxy_session_store.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/services/pairing_store.py`
- Create: `backend/auth_bridge/backend/tests/test_lostfilm_proxy_service.py`

- [ ] **Step 1: Write the failing proxy-session store tests**

Cover these behaviors:

```python
def test_store_persists_upstream_cookies_per_pairing(self): ...
def test_store_cleanup_clears_upstream_session_on_expiry(self): ...
```

- [ ] **Step 2: Run the targeted tests and confirm they fail**

Run:

```powershell
.\.venv\Scripts\python -m unittest tests.test_lostfilm_proxy_service
```

Expected: FAIL because the proxy session store does not exist yet.

- [ ] **Step 3: Implement the minimal store**

Suggested shape:

```python
@dataclass
class ProxySessionState:
    cookie_jar: httpx.Cookies
    last_seen_at: datetime
```

Implementation notes:
- Keep the store pairing-scoped.
- Integrate cleanup with existing pairing expiry/reset behavior.
- Do not mix this store with `SessionPayload`; it is pre-claim upstream state only.

- [ ] **Step 4: Rerun the targeted tests**

Run:

```powershell
.\.venv\Scripts\python -m unittest tests.test_lostfilm_proxy_service
```

Expected: PASS

- [ ] **Step 5: Commit the proxy-session store**

```powershell
git add backend/auth_bridge/backend/src/auth_bridge/services/proxy_session_store.py backend/auth_bridge/backend/src/auth_bridge/services/pairing_store.py backend/auth_bridge/backend/tests/test_lostfilm_proxy_service.py
git commit -m "feat: add pairing scoped proxy session store"
```

### Task 4: Proxy LostFilm requests through the wildcard host

**Files:**
- Create: `backend/auth_bridge/backend/src/auth_bridge/services/lostfilm_proxy_service.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/api/wildcard_proxy.py`
- Modify: `backend/auth_bridge/backend/tests/test_lostfilm_proxy_service.py`

- [ ] **Step 1: Extend the tests with forwarding and rewrite coverage**

Add failing tests for:

```python
def test_proxy_forwards_get_and_uses_server_side_cookie_jar(self): ...
def test_proxy_captures_upstream_set_cookie_into_store(self): ...
def test_proxy_rewrites_location_headers_back_to_wildcard_host(self): ...
```

Use `httpx.MockTransport` so no real LostFilm traffic is needed in unit tests.

- [ ] **Step 2: Run the proxy service tests and confirm they fail**

Run:

```powershell
.\.venv\Scripts\python -m unittest tests.test_lostfilm_proxy_service
```

Expected: FAIL because request forwarding and response rewriting are not implemented yet.

- [ ] **Step 3: Implement the minimal proxy service**

Implementation outline:

```python
class LostFilmProxyService:
    def proxy(self, pairing, incoming_request) -> ProxyResponse:
        upstream_request = ...
        upstream_response = ...
        self._capture_set_cookie(pairing, upstream_response)
        return self._rewrite_response(pairing, upstream_response)
```

Implementation notes:
- Forward method, path, query string, and allowed headers.
- Use the pairing-scoped server-side cookie jar on upstream requests.
- Capture upstream `Set-Cookie` into the store; do not rely on browser-held LostFilm cookies.
- Rewrite `Location` headers pointing to LostFilm back to the wildcard host.
- Start with GET + form POST support; avoid premature generality.

- [ ] **Step 4: Rerun the proxy service tests**

Run:

```powershell
.\.venv\Scripts\python -m unittest tests.test_lostfilm_proxy_service
```

Expected: PASS

- [ ] **Step 5: Commit the proxy-forwarding slice**

```powershell
git add backend/auth_bridge/backend/src/auth_bridge/services/lostfilm_proxy_service.py backend/auth_bridge/backend/src/auth_bridge/api/wildcard_proxy.py backend/auth_bridge/backend/tests/test_lostfilm_proxy_service.py
git commit -m "feat: proxy lostfilm phone auth flow through wildcard hosts"
```

## Chunk 4: Real Auth Detection And Pairing Confirmation

### Task 5: Confirm pairings only after a real authenticated proxied page

**Files:**
- Create: `backend/auth_bridge/backend/src/auth_bridge/services/lostfilm_auth_detector.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/services/pairing_service.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/api/wildcard_proxy.py`
- Create: `backend/auth_bridge/backend/tests/test_lostfilm_auth_detector.py`

- [ ] **Step 1: Write the failing auth detector tests**

Cover:

```python
def test_detector_rejects_ajax_success_without_authenticated_page(self): ...
def test_detector_accepts_authenticated_profile_page(self): ...
def test_detector_rejects_anonymous_redirect_shim(self): ...
```

- [ ] **Step 2: Run the targeted detector tests and confirm they fail**

Run:

```powershell
.\.venv\Scripts\python -m unittest tests.test_lostfilm_auth_detector
```

Expected: FAIL because the detector does not exist yet.

- [ ] **Step 3: Implement the minimal detector and confirmation hook**

Suggested detector contract:

```python
class LostFilmAuthDetector:
    def is_authenticated(self, html: str, cookie_names: list[str]) -> bool:
        ...
```

Implementation notes:
- Refuse to rely on `ajaxik.users.php` success alone.
- Treat the anonymous redirect shim (`location.replace("/")`, meta refresh, “Если по какой-то причине...”) as unauthenticated.
- Lock at least one stable authenticated marker in fixtures/tests.
- In `wildcard_proxy.py`, call the detector after proxied navigations that plausibly land on an authenticated page.
- Once detector returns true, freeze the upstream cookie jar into `session_payload`, set pairing status to `CONFIRMED`, and stop the phone flow from mutating that payload further.

- [ ] **Step 4: Rerun detector + pairing tests**

Run:

```powershell
.\.venv\Scripts\python -m unittest tests.test_lostfilm_auth_detector tests.test_pairings tests.test_phone_flow
```

Expected: PASS

- [ ] **Step 5: Commit the auth-detection slice**

```powershell
git add backend/auth_bridge/backend/src/auth_bridge/services/lostfilm_auth_detector.py backend/auth_bridge/backend/src/auth_bridge/services/pairing_service.py backend/auth_bridge/backend/src/auth_bridge/api/wildcard_proxy.py backend/auth_bridge/backend/tests/test_lostfilm_auth_detector.py backend/auth_bridge/backend/tests/test_pairings.py backend/auth_bridge/backend/tests/test_phone_flow.py
git commit -m "feat: confirm pairings from proxied browser auth state"
```

## Chunk 5: Phone Shell, Config, And Infrastructure Docs

### Task 6: Add the phone shell page and de-emphasize the legacy `/pair` form flow

**Files:**
- Create: `backend/auth_bridge/backend/src/auth_bridge/templates/phone_shell.html`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/api/wildcard_proxy.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/api/phone_flow.py`
- Modify: `backend/auth_bridge/backend/tests/test_wildcard_proxy.py`

- [ ] **Step 1: Write the failing shell-page tests**

Cover:

```python
def test_wildcard_root_renders_phone_shell_for_active_pairing(self): ...
def test_shell_transitions_to_success_message_after_confirmation(self): ...
```

- [ ] **Step 2: Run the targeted wildcard tests and confirm they fail**

Run:

```powershell
.\.venv\Scripts\python -m unittest tests.test_wildcard_proxy
```

Expected: FAIL because the wildcard root still returns only a placeholder response.

- [ ] **Step 3: Implement the shell page**

Implementation notes:
- Root page should explain that the phone is connecting the TV.
- Keep the shell minimal.
- From the shell, send the user into the proxied LostFilm login flow.
- On confirmed state, show `Device connected. Return to your TV.`
- Keep the old `/pair/{phone_verifier}` endpoints available only as non-primary legacy fallback until the new flow is proven.

- [ ] **Step 4: Rerun the wildcard tests**

Run:

```powershell
.\.venv\Scripts\python -m unittest tests.test_wildcard_proxy
```

Expected: PASS

- [ ] **Step 5: Commit the shell slice**

```powershell
git add backend/auth_bridge/backend/src/auth_bridge/templates/phone_shell.html backend/auth_bridge/backend/src/auth_bridge/api/wildcard_proxy.py backend/auth_bridge/backend/src/auth_bridge/api/phone_flow.py backend/auth_bridge/backend/tests/test_wildcard_proxy.py
git commit -m "feat: add wildcard phone auth shell"
```

### Task 7: Update Docker/env/docs for wildcard deployment

**Files:**
- Modify: `backend/auth_bridge/.env.example`
- Modify: `backend/auth_bridge/docker-compose.yml`
- Modify: `docs/auth-bridge-server-install.md`

- [ ] **Step 1: Add failing config/doc assertions where feasible**

At minimum, add a lightweight config test in:

```text
backend/auth_bridge/backend/tests/test_config.py
```

for the new wildcard/base-domain setting.

- [ ] **Step 2: Run the targeted config tests and confirm they fail**

Run:

```powershell
.\.venv\Scripts\python -m unittest tests.test_config
```

Expected: FAIL because the new wildcard/base-domain setting is not wired yet.

- [ ] **Step 3: Implement config and docs updates**

Required updates:
- add the new wildcard/base-domain env var to `.env.example`
- pass it through `docker-compose.yml`
- rewrite `docs/auth-bridge-server-install.md` to cover:
  - wildcard DNS
  - wildcard TLS
  - wildcard Caddy routing
  - verification that `verificationUrl` uses the wildcard host

- [ ] **Step 4: Rerun config tests and validate compose**

Run:

```powershell
.\.venv\Scripts\python -m unittest tests.test_config
docker compose -f backend/auth_bridge/docker-compose.yml --env-file backend/auth_bridge/.env.example config > $null
```

Expected: PASS

- [ ] **Step 5: Commit the deployment/docs slice**

```powershell
git add backend/auth_bridge/.env.example backend/auth_bridge/docker-compose.yml docs/auth-bridge-server-install.md backend/auth_bridge/backend/tests/test_config.py
git commit -m "docs: prepare wildcard auth bridge deployment"
```

## Chunk 6: Android Compatibility And End-To-End Validation

### Task 8: Verify Android still works with the new verification URL shape

**Files:**
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/data/network/AuthBridgeClient.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/data/auth/AuthRepository.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/auth/AuthViewModel.kt`
- Modify only if needed: `app/src/test/java/com/kraat/lostfilmnewtv/data/network/AuthBridgeClientTest.kt`

- [ ] **Step 1: Add a failing Android test only if the current parsing assumes `/pair/...`**

If no such assumption exists, record that no code change is required and skip directly to verification.

- [ ] **Step 2: Run the relevant Android auth tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.network.AuthBridgeClientTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.data.auth.AuthRepositoryTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.auth.AuthViewModelTest"
```

Expected: PASS, or one focused failure if a wildcard URL assumption must be fixed.

- [ ] **Step 3: Implement the minimal Android compatibility change if required**

Keep this task narrow:
- accept the verification URL as opaque for QR generation
- do not redesign TV auth UI here

- [ ] **Step 4: Rerun the Android auth tests**

Run the same commands from Step 2.

Expected: PASS

- [ ] **Step 5: Commit the Android compatibility slice if code changed**

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/data/network/AuthBridgeClient.kt app/src/test/java/com/kraat/lostfilmnewtv/data/network/AuthBridgeClientTest.kt
git commit -m "test: accept wildcard auth verification urls"
```

### Task 9: Run real-system verification on server and TV

**Files:**
- Verify: `backend/auth_bridge/backend/src/auth_bridge/api/wildcard_proxy.py`
- Verify: `backend/auth_bridge/backend/src/auth_bridge/services/lostfilm_proxy_service.py`
- Verify: `backend/auth_bridge/backend/src/auth_bridge/services/lostfilm_auth_detector.py`
- Verify: `docs/auth-bridge-server-install.md`

- [ ] **Step 1: Run the full backend test suite**

```powershell
Set-Location backend/auth_bridge/backend
.\.venv\Scripts\python -m unittest tests.test_config tests.test_health tests.test_pairings tests.test_phone_flow tests.test_rate_limit tests.test_wildcard_proxy tests.test_lostfilm_proxy_service tests.test_lostfilm_auth_detector
```

Expected: PASS

- [ ] **Step 2: Build the backend image**

```powershell
docker build -t lostfilm-auth-bridge:auth-bazuka-pp-ua-amd64 backend/auth_bridge/backend
```

Expected: PASS

- [ ] **Step 3: Deploy to the server**

Use the documented wildcard deployment flow from:

```text
docs/auth-bridge-server-install.md
```

Minimum checks:
- wildcard DNS resolves
- wildcard TLS is valid
- `https://<test-verifier>.auth.bazuka.pp.ua/` reaches the backend

- [ ] **Step 4: Run one real pairing from TV**

Checklist:
1. TV shows QR with the new wildcard verification URL encoded.
2. Phone opens the wildcard host.
3. LostFilm browser flow works on the phone through the proxy.
4. Backend reaches `CONFIRMED`.
5. TV claims the session and verifies it locally.
6. TV reaches authenticated state without manual confirmation.

- [ ] **Step 5: Commit the final implementation state**

```powershell
git add backend/auth_bridge backend/auth_bridge/backend docs/auth-bridge-server-install.md docs/superpowers/specs/2026-03-19-lostfilm-qr-browser-proxy-auth-design.md docs/superpowers/plans/2026-03-19-lostfilm-qr-browser-proxy-auth.md app/src/main/java/com/kraat/lostfilmnewtv/data/network/AuthBridgeClient.kt app/src/test/java/com/kraat/lostfilmnewtv/data/network/AuthBridgeClientTest.kt
git commit -m "feat: switch lostfilm qr auth to browser proxy flow"
```

## Notes

- Keep the old `/pair/{phone_verifier}` form flow until the wildcard proxy path is proven end-to-end. Remove it only in a follow-up cleanup if it becomes dead code.
- Do not widen scope into a full permanent LostFilm browsing proxy for the TV app.
- Treat the upstream cookie jar as sensitive temporary state; never log raw values during debugging.
- Prefer backend-first implementation and real-server validation before touching Android code beyond verification URL compatibility.
