# Auth Bridge Phase 1 Hardening Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the auth bridge safer to deploy by adding fail-fast config validation, backend CI gates, readiness-first container health, and non-root runtime hardening without changing the pairing/auth business flow.

**Architecture:** Keep the current FastAPI composition and pairing flow intact. Limit Phase 1 to low-risk operational hardening around configuration, CI, health checks, and container runtime so the first PR is small, testable, and safe to ship before deeper state-management refactors.

**Tech Stack:** Python 3.12, FastAPI, Pydantic Settings, unittest, Docker, Docker Compose, GitHub Actions

**Reference Inputs:** Backend audit findings from the current session, especially around [backend/auth_bridge/backend/src/auth_bridge/config.py](backend/auth_bridge/backend/src/auth_bridge/config.py), [backend/auth_bridge/docker-compose.yml](backend/auth_bridge/docker-compose.yml), [backend/auth_bridge/backend/Dockerfile](backend/auth_bridge/backend/Dockerfile), [.github/workflows/pull-request-checks.yml](.github/workflows/pull-request-checks.yml), and [.github/workflows/auth-bridge-image.yml](.github/workflows/auth-bridge-image.yml).

**Execution Notes:** Follow @test-driven-development for Python changes. Do not change pairing, proxy, or login behavior in this phase except where needed to support readiness or startup validation. Keep the scope to config/deploy safety only; Redis, state-machine extraction, and AsyncClient migration belong to later phases.

---

## Planned File Structure

- Modify: `backend/auth_bridge/backend/src/auth_bridge/config.py`
- Modify: `backend/auth_bridge/backend/tests/test_config.py`
- Verify: `backend/auth_bridge/backend/src/auth_bridge/main.py`
- Verify: `backend/auth_bridge/backend/src/auth_bridge/api/health.py`
- Modify: `backend/auth_bridge/docker-compose.yml`
- Modify: `backend/auth_bridge/backend/Dockerfile`
- Modify: `.github/workflows/pull-request-checks.yml`
- Modify: `.github/workflows/auth-bridge-image.yml`
- Modify: `backend/auth_bridge/.env.example`
- Modify: `docs/auth-bridge-server-install.md`

Notes:
- Phase 1 intentionally does **not** make `Settings()` impossible to construct in unit tests without env vars. Instead, validate shape and require explicit production env in compose/workflows.
- Keep `create_app()` import behavior stable so the existing backend unit tests still import `auth_bridge.main` without needing a repo-wide test harness rewrite.
- Use backend-local verification commands only:
  - `python -m unittest discover -s backend/auth_bridge/backend/tests -p 'test_config.py' -v`
  - `python -m unittest discover -s backend/auth_bridge/backend/tests -p 'test_health.py' -v`
  - `python -m unittest discover -s backend/auth_bridge/backend/tests -p 'test_*.py'`
  - `docker compose config`
  - `docker build -t lostfilm-auth-bridge-phase1 backend/auth_bridge/backend`

## Chunk 1: Lock The Config And Deploy Contract

### Task 1: Add failing tests for stricter settings validation

**Files:**
- Modify: `backend/auth_bridge/backend/tests/test_config.py`
- Modify: `backend/auth_bridge/backend/src/auth_bridge/config.py`

- [ ] **Step 1: Add failing tests for URL/domain validation**

Add tests that explicitly lock the Phase 1 rules:

```python
def test_public_base_url_must_use_https(self) -> None:
    with self.assertRaises(ValueError):
        Settings(public_base_url="http://auth.bazuka.pp.ua")


def test_public_base_domain_must_be_host_only(self) -> None:
    with self.assertRaises(ValueError):
        Settings(
            public_base_url="https://auth.bazuka.pp.ua",
            public_base_domain="https://auth.bazuka.pp.ua/path",
        )


def test_public_base_domain_cannot_include_path_or_query(self) -> None:
    with self.assertRaises(ValueError):
        Settings(
            public_base_url="https://auth.bazuka.pp.ua",
            public_base_domain="auth.bazuka.pp.ua/path?x=1",
        )
```

- [ ] **Step 2: Run the targeted config tests and confirm they fail**

Run: `python -m unittest discover -s backend/auth_bridge/backend/tests -p 'test_config.py' -v`
Expected: FAIL because the new tests do not pass with the current permissive `Settings` model.

- [ ] **Step 3: Implement minimal validators in `config.py`**

Use Pydantic validators to enforce:

```python
@field_validator("public_base_url")
@classmethod
def validate_public_base_url(cls, value: str) -> str:
    parsed = urlparse(value)
    if parsed.scheme != "https" or not parsed.netloc or parsed.path not in ("", "/") or parsed.query or parsed.fragment:
        raise ValueError("public_base_url must be an https origin without path, query, or fragment")
    return value


@field_validator("public_base_domain")
@classmethod
def validate_public_base_domain(cls, value: str | None) -> str | None:
    if value is None:
        return value
    parsed = urlparse(f"//{value}", scheme="https")
    if not parsed.hostname or parsed.path or parsed.query or parsed.fragment or parsed.username or parsed.password:
        raise ValueError("public_base_domain must be a bare host name")
    return parsed.hostname.lower().rstrip(".")
```

Keep `wildcard_base_domain` derivation behavior compatible with existing tests.

- [ ] **Step 4: Re-run the targeted config tests**

Run: `python -m unittest discover -s backend/auth_bridge/backend/tests -p 'test_config.py' -v`
Expected: PASS for all config tests, including the newly added validation cases.

- [ ] **Step 5: Commit the config validation slice**

```bash
git add backend/auth_bridge/backend/src/auth_bridge/config.py backend/auth_bridge/backend/tests/test_config.py
git commit -m "feat: validate auth bridge public url settings"
```

### Task 2: Require explicit production env in compose and keep readiness as the deploy gate

**Files:**
- Modify: `backend/auth_bridge/docker-compose.yml`
- Modify: `backend/auth_bridge/.env.example`
- Modify: `docs/auth-bridge-server-install.md`
- Verify: `backend/auth_bridge/backend/src/auth_bridge/api/health.py`

- [ ] **Step 1: Change compose to require the public origin variables**

Update `docker-compose.yml` so the critical env vars fail fast:

```yaml
environment:
  AUTH_BRIDGE_PUBLIC_BASE_URL: ${AUTH_BRIDGE_PUBLIC_BASE_URL:?set AUTH_BRIDGE_PUBLIC_BASE_URL}
  AUTH_BRIDGE_PUBLIC_BASE_DOMAIN: ${AUTH_BRIDGE_PUBLIC_BASE_DOMAIN:?set AUTH_BRIDGE_PUBLIC_BASE_DOMAIN}
```

Change the healthcheck endpoint from `/health/live` to `/health/ready`.

- [ ] **Step 2: Update `.env.example` and install docs**

Make the example env and server-install doc explicitly say:
- both public env vars are required
- health should be checked via `/health/ready`
- liveness remains for “process is up”, not rollout gating

- [ ] **Step 3: Validate the compose file**

Run: `docker compose -f backend/auth_bridge/docker-compose.yml config`
Expected: rendered compose output succeeds when required env vars are present and fails with a clear message when they are absent.

- [ ] **Step 4: Re-run health tests to ensure no app behavior regressed**

Run: `python -m unittest discover -s backend/auth_bridge/backend/tests -p 'test_health.py' -v`
Expected: PASS. App health routes still behave exactly as before.

- [ ] **Step 5: Commit the deploy contract slice**

```bash
git add backend/auth_bridge/docker-compose.yml backend/auth_bridge/.env.example docs/auth-bridge-server-install.md
git commit -m "chore: require explicit auth bridge deploy env"
```

## Chunk 2: Add Delivery Gates And Container Hardening

### Task 3: Add backend test execution to PR and image workflows

**Files:**
- Modify: `.github/workflows/pull-request-checks.yml`
- Modify: `.github/workflows/auth-bridge-image.yml`

- [ ] **Step 1: Add Python setup and backend test steps to `pull-request-checks.yml`**

Insert a backend verification block before or alongside the Gradle job:

```yaml
      - uses: actions/setup-python@v5
        with:
          python-version: "3.12"

      - name: Install auth bridge dependencies
        run: pip install ./backend/auth_bridge/backend

      - name: Run auth bridge tests
        run: python -m unittest discover -s backend/auth_bridge/backend/tests -p 'test_*.py'
```

- [ ] **Step 2: Add the same backend test gate to `auth-bridge-image.yml` before build/push**

The image workflow must install the backend and run the backend tests before `docker/build-push-action`.

- [ ] **Step 3: Verify the backend test command locally**

Run: `python -m unittest discover -s backend/auth_bridge/backend/tests -p 'test_*.py'`
Expected: PASS across the full backend suite.

- [ ] **Step 4: Sanity-check the workflow YAML**

Read both workflow files and confirm:
- backend tests run on PR/push to `main`
- image publish cannot happen before backend tests pass
- Android checks remain intact

- [ ] **Step 5: Commit the CI gate slice**

```bash
git add .github/workflows/pull-request-checks.yml .github/workflows/auth-bridge-image.yml
git commit -m "ci: gate auth bridge changes with backend tests"
```

### Task 4: Harden the Docker runtime to run as non-root

**Files:**
- Modify: `backend/auth_bridge/backend/Dockerfile`

- [ ] **Step 1: Add a dedicated runtime user**

Update the Dockerfile to create a non-root user and run uvicorn under that account:

```dockerfile
RUN addgroup --system app && adduser --system --ingroup app app
USER app
```

Place this after dependencies are installed and before the final `CMD`.

- [ ] **Step 2: Build the image locally**

Run: `docker build -t lostfilm-auth-bridge-phase1 backend/auth_bridge/backend`
Expected: build succeeds.

- [ ] **Step 3: Verify the container runs as non-root**

Run:

```bash
docker run --rm --entrypoint python lostfilm-auth-bridge-phase1 -c "import os; print(os.getuid())"
```

Expected: output is not `0`.

- [ ] **Step 4: Confirm the default command still starts cleanly**

Run:

```bash
docker run --rm -p 18015:8000 \
  -e AUTH_BRIDGE_PUBLIC_BASE_URL=https://auth.bazuka.pp.ua \
  -e AUTH_BRIDGE_PUBLIC_BASE_DOMAIN=auth.bazuka.pp.ua \
  lostfilm-auth-bridge-phase1
```

Expected: uvicorn starts without permission errors.

- [ ] **Step 5: Commit the container hardening slice**

```bash
git add backend/auth_bridge/backend/Dockerfile
git commit -m "chore: run auth bridge container as non-root"
```

### Task 5: Run the final Phase 1 verification sweep

**Files:**
- Verify only

- [ ] **Step 1: Run the targeted backend tests**

Run:

```bash
python -m unittest discover -s backend/auth_bridge/backend/tests -p 'test_config.py' -v
python -m unittest discover -s backend/auth_bridge/backend/tests -p 'test_health.py' -v
```

Expected: PASS.

- [ ] **Step 2: Run the full backend suite**

Run: `python -m unittest discover -s backend/auth_bridge/backend/tests -p 'test_*.py'`
Expected: PASS.

- [ ] **Step 3: Re-render compose**

Run: `docker compose -f backend/auth_bridge/docker-compose.yml config`
Expected: PASS with explicit env set; clear failure if required env missing.

- [ ] **Step 4: Rebuild the image**

Run: `docker build -t lostfilm-auth-bridge-phase1 backend/auth_bridge/backend`
Expected: PASS.

- [ ] **Step 5: Commit the final verification checkpoint**

```bash
git status --short
```

Expected: only the intended Phase 1 files are changed.

## Chunk 3: Handoff Notes

### Task 6: Keep Phase 2 work explicitly out of this PR

**Files:**
- Verify only

- [ ] **Step 1: Confirm that none of these files are touched in Phase 1**

Do not modify:
- `backend/auth_bridge/backend/src/auth_bridge/services/pairing_service.py`
- `backend/auth_bridge/backend/src/auth_bridge/services/pairing_store.py`
- `backend/auth_bridge/backend/src/auth_bridge/services/proxy_session_store.py`
- `backend/auth_bridge/backend/src/auth_bridge/services/lostfilm_proxy_service.py`
- `backend/auth_bridge/backend/src/auth_bridge/services/lostfilm_login_client.py`

Expected: pairing/runtime behavior remains stable; the PR stays operational-hardening-only.

- [ ] **Step 2: Record follow-up work for Phase 2 instead of expanding scope**

Document, but do not implement here:
- Redis-backed state
- explicit pairing state machine
- AsyncClient migration
- proxy path/header allowlist
- request-id + metrics

- [ ] **Step 3: Final handoff note**

Phase 1 is complete when:
- config validation is stricter
- deploy env is explicit
- readiness is the health gate
- backend tests run in CI
- image runs non-root

At that point, Phase 2 can safely focus on state, security, and performance without bundling operational-risk changes into the same PR.
