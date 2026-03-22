# LostFilm Auth Bridge Server Install

## Goal

Deploy the QR auth backend in Docker so that TV pairings generate wildcard phone URLs like `https://<phone_verifier>.auth.bazuka.pp.ua/` and the phone browser flow stays on `*.auth.bazuka.pp.ua`.

## Prerequisites

- Ubuntu server with Docker Engine and Docker Compose plugin installed
- DNS control for `bazuka.pp.ua`
- Wildcard DNS for `*.auth.bazuka.pp.ua`
- Wildcard TLS certificate for `*.auth.bazuka.pp.ua`
- Reverse proxy or Caddy in front of the backend

Important:

- a wildcard hostname is required for the QR flow
- a normal single-host certificate for only `auth.bazuka.pp.ua` is not enough
- wildcard TLS usually requires a DNS challenge, not plain HTTP validation

## Files To Upload

### Option A: install from a prebuilt image tarball

Copy these files to the server:

- `backend/auth_bridge/docker-compose.yml`
- `backend/auth_bridge/.env.example`
- backend image tarball

Suggested target directory:

```bash
mkdir -p ~/lostfilm-auth-bridge
cd ~/lostfilm-auth-bridge
```

### Option B: build on the server from source

Do not upload only `docker-compose.yml` and `.env.example`.

For this path, clone or upload the full repository so the server has:

- `backend/auth_bridge/docker-compose.yml`
- `backend/auth_bridge/.env.example`
- `backend/auth_bridge/backend/Dockerfile`
- `backend/auth_bridge/backend/src/...`

## Create The Runtime Env File

Use the example as the starting point:

```bash
cp .env.example .env
```

Required values for this deployment:

```dotenv
AUTH_BRIDGE_PUBLIC_BASE_URL=https://auth.bazuka.pp.ua
AUTH_BRIDGE_PUBLIC_BASE_DOMAIN=auth.bazuka.pp.ua
AUTH_BACKEND_IMAGE=lostfilm-auth-bridge:auth-bazuka-pp-ua-amd64
BACKEND_PORT=18015
```

Notes:

- `AUTH_BRIDGE_PUBLIC_BASE_URL` is the canonical public origin
- `AUTH_BRIDGE_PUBLIC_BASE_DOMAIN` is what the backend uses to mint `https://<phone_verifier>.auth.bazuka.pp.ua/`
- both values are required by `docker compose`; the stack should fail fast if either one is missing
- keep `BACKEND_PORT` bound only to `127.0.0.1`; TLS should terminate at the reverse proxy

## Load The Docker Image

If you are installing from a prebuilt tarball:

```bash
docker load -i lostfilm-auth-bridge-auth-bazuka-pp-ua-amd64.tar
```

If you are building on the server from the repository root instead:

```bash
docker build -t lostfilm-auth-bridge:auth-bazuka-pp-ua-amd64 backend/auth_bridge/backend
```

## Start The Backend

If you installed from a prebuilt tarball, run from the directory that contains `docker-compose.yml` and `.env`:

```bash
docker compose up -d
docker compose ps
docker compose logs --tail=100 auth-backend
```

If you are working from the repository checkout, run from `backend/auth_bridge`:

```bash
cd backend/auth_bridge
docker compose up -d
docker compose ps
docker compose logs --tail=100 auth-backend
```

Expected result:

- one `auth-backend` container
- status becomes `healthy`
- backend listens on `127.0.0.1:18015`

## Reverse Proxy And Wildcard TLS

The backend must receive both:

- `auth.bazuka.pp.ua`
- `*.auth.bazuka.pp.ua`

Example Caddy shape once a wildcard certificate is already available:

```caddyfile
auth.bazuka.pp.ua, *.auth.bazuka.pp.ua {
    reverse_proxy 127.0.0.1:18015
}
```

If Caddy is responsible for obtaining the wildcard certificate, configure a DNS challenge provider for your DNS host. Do not rely on plain HTTP challenge for the wildcard name.

## Validation Checklist

### 1. Local container health

```bash
curl -fsS http://127.0.0.1:18015/health/ready
```

Expected:

```json
{"status":"ok"}
```

### 2. Public HTTPS health

```bash
curl -fsS https://auth.bazuka.pp.ua/health/ready
```

Expected:

```json
{"status":"ok"}
```

### 3. Pairing contract now uses wildcard hosts

```bash
curl -fsS -X POST https://auth.bazuka.pp.ua/api/pairings
```

Expected:

- response contains `phoneVerifier`
- `verificationUrl` matches `https://<phone_verifier>.auth.bazuka.pp.ua/`
- `verificationUrl` does not contain `/pair/`
- the TV should treat `verificationUrl` as an opaque URL and render it directly into the QR code

### 4. Wildcard host reaches the backend

Open the returned `verificationUrl` on a phone or desktop browser.

Expected:

- page shows `Connect your TV`
- page contains a `Continue in LostFilm` link
- after login, the backend should capture the upstream LostFilm cookie jar and later expose it to the TV only through `/api/pairings/{pairingId}/claim`

### 5. Legacy pair links only redirect into the wildcard flow

If you still have an old URL like `https://auth.bazuka.pp.ua/pair/<phone_verifier>`, open it in a browser.

Expected:

- the backend returns a temporary redirect to `https://<phone_verifier>.auth.bazuka.pp.ua/`
- the backend does not render a standalone login form on `/pair/...`

## Troubleshooting

### `verificationUrl` still points to `auth.example.test`

Check:

- `.env` was created next to `docker-compose.yml`
- container was restarted after the env change
- `AUTH_BRIDGE_PUBLIC_BASE_URL` and `AUTH_BRIDGE_PUBLIC_BASE_DOMAIN` are both set

If `docker compose config` fails before startup, check that both variables are present in `.env` or exported in the shell.

Then restart:

```bash
docker compose down
docker compose up -d
```

### `verificationUrl` uses the correct domain but the wildcard page does not open

Check:

- wildcard DNS exists for `*.auth.bazuka.pp.ua`
- reverse proxy accepts both the apex host and wildcard hosts
- wildcard TLS certificate is valid
- if an old `/pair/...` link exists anywhere, it should redirect into the wildcard host instead of rendering its own login page

### Public health works but wildcard pages return the wrong site

The reverse proxy is probably not routing wildcard hosts to the auth backend. Recheck the host matcher in the proxy config.

### TV QR flow still fails after backend deploy

Check both sides:

- Android app must use `https://auth.bazuka.pp.ua`
- pairing response must contain wildcard `verificationUrl`
- phone browser must stay on `*.auth.bazuka.pp.ua` during login
- the TV should receive cookies only by claiming `SessionPayload` from the auth bridge after confirmation
