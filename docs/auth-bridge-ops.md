# Auth Bridge: операционные заметки

Этот документ фиксирует рабочее состояние LostFilm Auth Bridge, чтобы при следующем обслуживании не восстанавливать детали деплоя заново.

Подробная инструкция установки находится в [auth-bridge-server-install.md](auth-bridge-server-install.md).

## Репозиторий

- Основной GitHub repo: `https://github.com/kraaton11/new_lostfilmatv.git`
- Резервный remote в локальном checkout: `origin-lostfilmtv -> https://github.com/kraaton11/lostfilmtv.git`
- Backend в репозитории: `backend/auth_bridge/`
- FastAPI entry point: `backend/auth_bridge/backend/src/auth_bridge/main.py`
- Compose file: `backend/auth_bridge/docker-compose.yml`
- Workflow сборки Docker image: `.github/workflows/auth-bridge-image.yml`
- Публикуемый image: `ghcr.io/kraaton11/lostfilm-auth-bridge`

## Android-клиент

- Package name: `com.kraat.lostfilmnewtv`
- Минимальная версия Android: `API 26` / Android 8.0
- Auth bridge base URL в приложении: `https://auth.bazuka.pp.ua`
- TMDB proxy base URL в приложении: `https://auth.bazuka.pp.ua/api/tmdb`
- Основная проверка клиента:

```bash
./gradlew testDebugUnitTest lint assembleDebug
```

Instrumented UI-тесты требуют подключенного Android TV эмулятора или устройства:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Сервер

- Сервер: `ubuntu@1.alpo.pp.ua`
- Наблюдавшийся hostname: `baza-1`
- Каталог деплоя: `/home/ubuntu/lostfilm-auth-bridge`
- Публичный auth-домен: `https://auth.bazuka.pp.ua`
- Телефонный QR-flow использует wildcard hosts: `https://<phone_verifier>.auth.bazuka.pp.ua/`
- Старый API-домен `https://lf.bazuka.pp.ua` обслуживается отдельно и не является частью auth bridge.

## Runtime-состав

Текущая версия auth bridge поднимает один контейнер:

- `auth-backend`

Внутри контейнера FastAPI слушает порт `8000`. Docker публикует его только на loopback хоста:

```text
127.0.0.1:${BACKEND_PORT}:8000
```

По умолчанию в репозитории:

```dotenv
BACKEND_PORT=18015
```

На production-сервере может использоваться порт `3001`, если так настроен reverse proxy:

```dotenv
BACKEND_PORT=3001
```

Postgres в текущей версии не используется. Доверенные телефоны хранятся в SQLite базе внутри Docker volume:

```text
trusted-devices -> /data/trusted_devices.sqlite3
```

## Ключевые переменные `.env`

Обязательные публичные значения:

```dotenv
AUTH_BRIDGE_PUBLIC_BASE_URL=https://auth.bazuka.pp.ua
AUTH_BRIDGE_PUBLIC_BASE_DOMAIN=auth.bazuka.pp.ua
AUTH_BACKEND_IMAGE=ghcr.io/kraaton11/lostfilm-auth-bridge:latest
BACKEND_PORT=18015
```

Рекомендуемые значения для сервера за reverse proxy:

```dotenv
AUTH_BRIDGE_TRUSTED_PROXY_IPS=127.0.0.1
AUTH_BRIDGE_TRUSTED_DEVICE_COOKIE_DOMAIN=auth.bazuka.pp.ua
AUTH_BRIDGE_TRUSTED_DEVICE_DB_PATH=/data/trusted_devices.sqlite3
AUTH_BRIDGE_TRUSTED_DEVICE_TTL_SECONDS=31536000
AUTH_BRIDGE_LOG_FORMAT=json
```

Если образ фиксируется по commit-тегу, значение выглядит так:

```dotenv
AUTH_BACKEND_IMAGE=ghcr.io/kraaton11/lostfilm-auth-bridge:sha-<commit>
```

Опциональный DeepL-перевод включается только при наличии ключа:

```dotenv
AUTH_BRIDGE_DEEPL_API_KEY=<secret>
```

Если ключ пустой, `/api/translate` возвращает `503`, что является штатным поведением.

## Reverse proxy

Reverse proxy работает на уровне хоста, а не как Docker service.

Наблюдавшаяся конфигурация:

- systemd service: `hysteria-caddy`
- unit: `/etc/systemd/system/hysteria-caddy.service`
- Caddyfile: `/etc/hysteria/core/scripts/webpanel/Caddyfile`

Для текущего wildcard-flow proxy должен принимать оба host:

- `auth.bazuka.pp.ua`
- `*.auth.bazuka.pp.ua`

Минимальная форма Caddy-конфига:

```caddyfile
auth.bazuka.pp.ua, *.auth.bazuka.pp.ua {
    encode gzip zstd
    reverse_proxy 127.0.0.1:18015
}
```

Если на сервере используется `BACKEND_PORT=3001`, upstream должен быть `127.0.0.1:3001`.

Операционная заметка по `hysteria-caddy`: ранее `systemctl reload hysteria-caddy` был ненадежен из-за `admin off` и `ExecReload`, который вызывает `caddy reload`. Для применения изменений использовался restart:

```bash
sudo systemctl restart hysteria-caddy
```

Перед restart конфиг стоит проверить:

```bash
sudo caddy validate --config /etc/hysteria/core/scripts/webpanel/Caddyfile
```

## GHCR и обновления

Workflow `.github/workflows/auth-bridge-image.yml` собирает и публикует `linux/amd64` image в GHCR.

Ожидаемые теги:

- `latest` для `main`
- `sha-*` для commit-based deploy
- `v*` для tag-based release

Обновление на сервере:

```bash
ssh ubuntu@1.alpo.pp.ua
cd /home/ubuntu/lostfilm-auth-bridge
docker compose pull auth-backend
docker compose up -d auth-backend
docker compose logs --tail=100 auth-backend
```

Если используется фиксированный `sha-*` тег:

```bash
grep ^AUTH_BACKEND_IMAGE= .env
sed -i 's#^AUTH_BACKEND_IMAGE=.*#AUTH_BACKEND_IMAGE=ghcr.io/kraaton11/lostfilm-auth-bridge:sha-<commit>#' .env
docker compose pull auth-backend
docker compose up -d auth-backend
```

GHCR pull для приватного образа требует Docker login с токеном, у которого есть `read:packages`.

## Docker credentials на сервере

Docker credentials были перенесены из plain `config.json` в credential store.

Наблюдавшееся состояние:

- установлены `pass` и `golang-docker-credential-helpers`;
- Docker config пользователя `ubuntu`: `/home/ubuntu/.docker/config.json`;
- содержимое config:

```json
{
  "credsStore": "pass"
}
```

- password store: `/home/ubuntu/.password-store`;
- GPG fingerprint для Docker credential storage:

```text
EC909B77956C9854BA8B756E33DB71DD2D8289F6
```

Старые plain backup-файлы `config.json.bak.*` были удалены после миграции.

## Проверки здоровья

Локально на сервере:

```bash
curl -fsS http://127.0.0.1:18015/health/live
curl -fsS http://127.0.0.1:18015/health/ready
```

Если production использует `BACKEND_PORT=3001`, замените порт:

```bash
curl -fsS http://127.0.0.1:3001/health/ready
```

Публично:

```bash
curl -fsS https://auth.bazuka.pp.ua/health/live
curl -fsS https://auth.bazuka.pp.ua/health/ready
curl -fsS https://auth.bazuka.pp.ua/health/translation
curl -fsS https://auth.bazuka.pp.ua/health/tmdb
```

Ожидаемые ответы для базовых endpoints:

```json
{"status":"ok"}
```

`/health/translation` и `/health/tmdb` возвращают counters/config status без секретов и без внешних запросов к DeepL/TMDB.

## Проверка QR-flow

Создать pairing:

```bash
curl -fsS -X POST https://auth.bazuka.pp.ua/api/pairings
```

Ответ должен содержать:

- `pairingId`
- `pairingSecret`
- `phoneVerifier`
- `userCode`
- `verificationUrl`
- `expiresIn`
- `pollInterval`
- `status`

`verificationUrl` должен быть wildcard URL:

```text
https://<phone_verifier>.auth.bazuka.pp.ua/
```

Проверить статус:

```bash
curl -fsS \
  -H "X-Pairing-Secret: <pairingSecret>" \
  https://auth.bazuka.pp.ua/api/pairings/<pairingId>
```

Важно: текущий API требует `X-Pairing-Secret` для `status`, `claim`, `finalize`, `release` и `cancel`.

## Известные нюансы

- Для wildcard QR-flow нужны DNS и TLS для `*.auth.bazuka.pp.ua`.
- Если Caddy matcher включает только `auth.bazuka.pp.ua`, публичный health может работать, а телефонный wildcard-flow будет попадать не туда.
- `AUTH_BRIDGE_PUBLIC_BASE_URL` должен быть HTTPS origin без path/query.
- `AUTH_BRIDGE_PUBLIC_BASE_DOMAIN` должен быть bare domain, без схемы и порта.
- `AUTH_BRIDGE_TRUSTED_PROXY_IPS` должен соответствовать реальному IP reverse proxy, иначе rate limiting будет видеть proxy вместо клиента или проигнорирует `X-Forwarded-For`.
- `AUTH_BRIDGE_TRUSTED_DEVICE_SECRET`, если задан, нельзя менять без понимания последствий: старые trusted-device cookies перестанут проверяться.
- Установка `golang-docker-credential-helpers` на Ubuntu может подтянуть GUI-зависимости; это ожидаемо для пакета и не ломает deploy.

## Чеклист следующего обслуживания

1. Проверить, что рабочее дерево на сервере соответствует ожидаемому compose:

```bash
ssh ubuntu@1.alpo.pp.ua 'cd /home/ubuntu/lostfilm-auth-bridge && docker compose ps'
```

2. Проверить текущий image:

```bash
ssh ubuntu@1.alpo.pp.ua 'grep ^AUTH_BACKEND_IMAGE= /home/ubuntu/lostfilm-auth-bridge/.env'
```

3. Проверить health:

```bash
curl -fsS https://auth.bazuka.pp.ua/health/ready
```

4. Обновить backend:

```bash
ssh ubuntu@1.alpo.pp.ua 'cd /home/ubuntu/lostfilm-auth-bridge && docker compose pull auth-backend && docker compose up -d auth-backend'
```

5. Проверить логи:

```bash
ssh ubuntu@1.alpo.pp.ua 'cd /home/ubuntu/lostfilm-auth-bridge && docker compose logs --tail=100 auth-backend'
```

6. Если менялся reverse proxy:

```bash
sudo caddy validate --config /etc/hysteria/core/scripts/webpanel/Caddyfile
sudo systemctl restart hysteria-caddy
```
