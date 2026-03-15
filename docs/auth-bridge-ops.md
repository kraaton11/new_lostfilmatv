# Auth Bridge Ops Notes

This file captures the current deployment state for the LostFilm auth bridge so the setup does not need to be rediscovered from scratch.

## Repository State

- Primary GitHub repo: `https://github.com/kraaton11/new_lostfilmatv.git`
- Backup remote kept locally: `origin-lostfilmtv -> https://github.com/kraaton11/lostfilmtv.git`
- Main feature branch that delivered the app and backend scaffold: `codex/lostfilm-android-tv`
- PR merged to `main`: `#1`
- Merge commit on `main`: `133053588dc425169f535248fbf227376dab06e9`

## Android App Notes

- Package name: `com.kraat.lostfilmnewtv`
- Minimum Android version: `API 26` / Android 8.0
- Manual install target previously used: `192.168.2.246:5555`
- Successful emulator/device checks included:
  - `.\gradlew.bat testDebugUnitTest lint assembleDebug`
  - `ANDROID_SERIAL=emulator-5554 .\gradlew.bat :app:connectedDebugAndroidTest`

## Auth Bridge Source Layout

- Backend root in repo: `backend/auth_bridge/`
- FastAPI app entry point: `backend/auth_bridge/backend/src/auth_bridge/main.py`
- Server compose file: `backend/auth_bridge/docker-compose.yml`
- GitHub image workflow: `.github/workflows/auth-bridge-image.yml`

## Server Layout

- Server: `ubuntu@1.alpo.pp.ua`
- Hostname observed on server: `baza-1`
- Public auth domain: `https://auth.bazuka.pp.ua`
- Existing API domain kept working alongside it: `https://lf.bazuka.pp.ua`
- Deploy directory on server: `/home/ubuntu/lostfilm-auth-bridge`
- Compose stack names:
  - `lostfilm-auth-bridge-auth-backend-1`
  - `lostfilm-auth-bridge-auth-postgres-1`

## Reverse Proxy State

- Reverse proxy is not the Docker `proxy` service.
- Real proxy is host-level Caddy service: `hysteria-caddy`
- Systemd unit: `/etc/systemd/system/hysteria-caddy.service`
- Active Caddy config: `/etc/hysteria/core/scripts/webpanel/Caddyfile`

Current Caddyfile shape:

```caddy
# Global configuration
{
    admin off
    auto_https disable_redirects
}

lf.bazuka.pp.ua:443 {
    tls internal
    encode gzip zstd
    reverse_proxy http://127.0.0.1:3000
}

auth.bazuka.pp.ua:443 {
    tls internal
    encode gzip zstd
    reverse_proxy http://127.0.0.1:3001
}
```

Important operational note:

- `systemctl reload hysteria-caddy` is currently broken because the unit uses `caddy reload`, but the Caddyfile has `admin off`.
- The observed failure is a post to `http://localhost:2019/load` with `connection refused`.
- Use `sudo systemctl restart hysteria-caddy` to apply config changes unless the unit/config is redesigned.

## Current Runtime Configuration

- Backend binds through Docker to `127.0.0.1:3001`
- Public traffic path is `auth.bazuka.pp.ua -> host Caddy -> 127.0.0.1:3001 -> auth-backend container`
- Postgres stays internal to the compose network
- Current deployed image reference in `/home/ubuntu/lostfilm-auth-bridge/.env`:
  - `AUTH_BACKEND_IMAGE=ghcr.io/kraaton11/lostfilm-auth-bridge:sha-1330535`

## GHCR State

- Published container image: `ghcr.io/kraaton11/lostfilm-auth-bridge`
- Confirmed tags during setup:
  - `latest`
  - `sha-1330535`
- Working server update flow now:
  - `docker compose pull auth-backend`
  - `docker compose up -d auth-backend`

## Docker Credential Storage On Server

Docker credentials were migrated away from plain `config.json` auth storage.

Current state:

- Installed packages:
  - `pass`
  - `golang-docker-credential-helpers`
- Docker config for user `ubuntu`: `/home/ubuntu/.docker/config.json`
- Current content is only:

```json
{
  "credsStore": "pass"
}
```

- Password store path: `/home/ubuntu/.password-store`
- GPG fingerprint created for Docker credential storage:
  - `EC909B77956C9854BA8B756E33DB71DD2D8289F6`
- Plain backup `config.json.bak.*` files were shredded and removed after migration.

## Health Checks That Were Confirmed

- Local backend on server:
  - `curl http://127.0.0.1:3001/health/live`
- Public backend:
  - `curl -k https://auth.bazuka.pp.ua/health/live`
- Existing API after auth deploy:
  - `curl -k https://lf.bazuka.pp.ua/health`

Expected successful responses observed:

- `auth.bazuka.pp.ua/health/live` -> `{"status":"ok"}`
- `lf.bazuka.pp.ua/health` -> `{"status":"ok","database_configured":true,"database_ok":true}`

## Known Gotchas

- The server default shell behavior made some inline SSH commands flaky. Simpler one-shot commands and uploaded scripts were more reliable.
- `hysteria-caddy` reload failures were not caused by the new auth domain; the root cause was the `admin off` plus `ExecReload` mismatch.
- GHCR pulls require a token with `read:packages`.
- Ubuntu package install for `golang-docker-credential-helpers` pulled many GUI-related dependencies. This did not break the deploy, but it is expected.

## Next-Time Checklist

If this stack needs to be touched again, start here:

1. Confirm server health:
   - `curl -k https://auth.bazuka.pp.ua/health/live`
   - `curl -k https://lf.bazuka.pp.ua/health`
2. Confirm running image:
   - `ssh ubuntu@1.alpo.pp.ua 'grep ^AUTH_BACKEND_IMAGE= /home/ubuntu/lostfilm-auth-bridge/.env'`
3. Pull and restart auth backend:
   - `ssh ubuntu@1.alpo.pp.ua 'cd /home/ubuntu/lostfilm-auth-bridge && docker compose pull auth-backend && docker compose up -d auth-backend'`
4. If Caddy config changes are needed:
   - edit `/etc/hysteria/core/scripts/webpanel/Caddyfile`
   - validate with `sudo caddy validate --config /etc/hysteria/core/scripts/webpanel/Caddyfile`
   - apply with `sudo systemctl restart hysteria-caddy`
