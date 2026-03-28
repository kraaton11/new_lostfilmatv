# LostFilm New TV

Kotlin Android TV application that parses the latest releases from `https://www.lostfilm.today/new/`, caches them locally, and shows them in a poster-first TV UI with a minimal details screen.

## Auth Flow

LostFilm authentication uses a single QR pairing flow:

- the TV calls the auth bridge and gets a wildcard `verificationUrl`
- the phone opens `https://<phone_verifier>.<auth-domain>/`
- the backend proxies the real LostFilm browser session on that wildcard host
- once the backend detects authenticated LostFilm state, the TV claims a `SessionPayload`
- the Android app verifies and stores the claimed cookies locally

There is no manual cookie export/import in the normal flow. The legacy `/pair/{phone_verifier}` route remains only as a compatibility redirect to the wildcard host.

## Local Debug Build

Run:

```powershell
.\gradlew.bat assembleDebug
```

## Project Docs

- Spec: `docs/superpowers/specs/2026-03-15-lostfilm-android-tv-design.md`
- Plan: `docs/superpowers/plans/2026-03-15-lostfilm-android-tv.md`
- GitHub setup: `docs/github-setup.md`
- Auth bridge ops: `docs/auth-bridge-ops.md`
- Auth bridge server install: `docs/auth-bridge-server-install.md`
