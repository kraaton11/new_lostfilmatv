# Установка LostFilm Auth Bridge на сервер

Документ описывает актуальную установку `backend/auth_bridge`: FastAPI-сервис для QR-авторизации Android TV приложения. Сервис запускается в Docker, принимает pairing-запросы от телевизора на `auth.bazuka.pp.ua` и ведет телефонный браузер через wildcard-домены вида `https://<phone_verifier>.auth.bazuka.pp.ua/`.

## Что разворачивается

- Один контейнер `auth-backend`.
- FastAPI приложение внутри контейнера слушает порт `8000`.
- Docker публикует его только на loopback хоста: `127.0.0.1:${BACKEND_PORT}`.
- TLS и публичные домены обслуживает внешний reverse proxy, например Caddy.
- Доверенные телефоны хранятся в SQLite базе в Docker volume `trusted-devices`.

Postgres в текущей версии не нужен.

## Требования

- Ubuntu-сервер с Docker Engine и Docker Compose plugin.
- Доступ к DNS-зоне `bazuka.pp.ua`.
- DNS-записи для:
  - `auth.bazuka.pp.ua`
  - `*.auth.bazuka.pp.ua`
- TLS-сертификат, покрывающий и обычный, и wildcard-домен.
- Reverse proxy перед backend-контейнером.
- Для GHCR-образа: Docker login с правом `read:packages`, если образ приватный.

Важно:

- wildcard-домен обязателен для текущего QR-flow;
- сертификата только на `auth.bazuka.pp.ua` недостаточно;
- wildcard TLS обычно выпускается через DNS challenge, а не через обычную HTTP-проверку.

## Варианты установки

### Вариант A: использовать готовый Docker image

Основной вариант для сервера: брать образ из GHCR.

Пример значения:

```dotenv
AUTH_BACKEND_IMAGE=ghcr.io/kraaton11/lostfilm-auth-bridge:latest
```

Для более предсказуемого деплоя лучше использовать `sha-*` тег из GitHub Actions, например:

```dotenv
AUTH_BACKEND_IMAGE=ghcr.io/kraaton11/lostfilm-auth-bridge:sha-<commit>
```

На сервер нужны только:

- `backend/auth_bridge/docker-compose.yml`
- `backend/auth_bridge/.env.example`
- файл `.env`, созданный из `.env.example`

Рекомендуемый каталог:

```bash
mkdir -p ~/lostfilm-auth-bridge
cd ~/lostfilm-auth-bridge
```

### Вариант B: собрать образ на сервере

Если образ собирается прямо на сервере, нужен полный checkout репозитория или как минимум:

- `backend/auth_bridge/docker-compose.yml`
- `backend/auth_bridge/.env.example`
- `backend/auth_bridge/backend/Dockerfile`
- `backend/auth_bridge/backend/src/...`
- `backend/auth_bridge/backend/pyproject.toml`

Сборка из корня репозитория:

```bash
docker build -t lostfilm-auth-bridge:auth-bazuka-pp-ua-amd64 backend/auth_bridge/backend
```

Тогда в `.env` можно оставить локальный образ:

```dotenv
AUTH_BACKEND_IMAGE=lostfilm-auth-bridge:auth-bazuka-pp-ua-amd64
```

## Настройка `.env`

Создайте файл окружения рядом с `docker-compose.yml`:

```bash
cp .env.example .env
```

Минимальные обязательные значения:

```dotenv
AUTH_BRIDGE_PUBLIC_BASE_URL=https://auth.bazuka.pp.ua
AUTH_BRIDGE_PUBLIC_BASE_DOMAIN=auth.bazuka.pp.ua
AUTH_BACKEND_IMAGE=ghcr.io/kraaton11/lostfilm-auth-bridge:latest
BACKEND_PORT=18015
```

Для текущего production-сервера может использоваться другой loopback-порт, например `3001`. Главное, чтобы reverse proxy проксировал на тот же порт:

```dotenv
BACKEND_PORT=3001
```

Рекомендуемые значения для работы за reverse proxy:

```dotenv
AUTH_BRIDGE_TRUSTED_PROXY_IPS=127.0.0.1
AUTH_BRIDGE_TRUSTED_DEVICE_COOKIE_DOMAIN=auth.bazuka.pp.ua
AUTH_BRIDGE_TRUSTED_DEVICE_DB_PATH=/data/trusted_devices.sqlite3
AUTH_BRIDGE_TRUSTED_DEVICE_TTL_SECONDS=31536000
AUTH_BRIDGE_LOG_FORMAT=json
```

Если reverse proxy ходит в backend не с `127.0.0.1`, укажите его IP или CIDR в `AUTH_BRIDGE_TRUSTED_PROXY_IPS`. Это нужно, чтобы rate limiting корректно учитывал `X-Forwarded-For` только от доверенного proxy.

Опциональные интеграции:

```dotenv
AUTH_BRIDGE_DEEPL_API_KEY=
AUTH_BRIDGE_DEEPL_API_URL=https://api-free.deepl.com/v2/translate
AUTH_BRIDGE_TRANSLATION_RATE_LIMIT_MAX_REQUESTS=120
AUTH_BRIDGE_TRANSLATION_RATE_LIMIT_WINDOW_SECONDS=60
```

Если `AUTH_BRIDGE_DEEPL_API_KEY` пустой, endpoint `/api/translate` останется доступен, но будет возвращать `503 Translation is not configured`.

## Запуск backend

Из каталога, где лежат `docker-compose.yml` и `.env`:

```bash
docker compose pull auth-backend
docker compose up -d
docker compose ps
docker compose logs --tail=100 auth-backend
```

Если используется локально собранный образ, `docker compose pull auth-backend` не нужен.

Ожидаемый результат:

- контейнер `auth-backend` запущен;
- healthcheck переходит в `healthy`;
- backend доступен на `127.0.0.1:${BACKEND_PORT}`;
- Docker volume `trusted-devices` создан и подключен в `/data`.

## Reverse proxy и wildcard TLS

Backend должен получать запросы с обоих host:

- `auth.bazuka.pp.ua`
- `*.auth.bazuka.pp.ua`

Минимальная форма Caddy-конфига, если wildcard-сертификат уже доступен:

```caddyfile
auth.bazuka.pp.ua, *.auth.bazuka.pp.ua {
    encode gzip zstd
    reverse_proxy 127.0.0.1:18015
}
```

Если `BACKEND_PORT=3001`, proxy должен указывать на `127.0.0.1:3001`.

Если Caddy сам выпускает wildcard-сертификат, настройте DNS challenge provider для вашего DNS-провайдера. Обычный HTTP challenge не выпустит сертификат для `*.auth.bazuka.pp.ua`.

## Проверка установки

### 1. Локальная готовность контейнера

```bash
curl -fsS http://127.0.0.1:18015/health/ready
```

Ожидаемый ответ:

```json
{"status":"ok"}
```

Если backend опубликован на другом порту, замените `18015` на значение `BACKEND_PORT`.

### 2. Публичная готовность

```bash
curl -fsS https://auth.bazuka.pp.ua/health/ready
```

Ожидаемый ответ:

```json
{"status":"ok"}
```

Дополнительные health endpoints:

```bash
curl -fsS https://auth.bazuka.pp.ua/health/live
curl -fsS https://auth.bazuka.pp.ua/health/translation
curl -fsS https://auth.bazuka.pp.ua/health/tmdb
```

`/health/translation` и `/health/tmdb` не раскрывают секреты и не делают внешние запросы.

### 3. Контракт создания pairing

```bash
curl -fsS -X POST https://auth.bazuka.pp.ua/api/pairings
```

Ожидаемый ответ содержит:

- `pairingId`
- `pairingSecret`
- `phoneVerifier`
- `userCode`
- `verificationUrl`
- `expiresIn`
- `pollInterval`
- `status`

`verificationUrl` должен иметь вид:

```text
https://<phone_verifier>.auth.bazuka.pp.ua/
```

Он не должен содержать старый путь `/pair/`. Android-приложение должно считать `verificationUrl` непрозрачной строкой и напрямую кодировать ее в QR.

### 4. Статус pairing

Для статуса нужен `X-Pairing-Secret` из ответа создания pairing:

```bash
curl -fsS \
  -H "X-Pairing-Secret: <pairingSecret>" \
  https://auth.bazuka.pp.ua/api/pairings/<pairingId>
```

Ожидаемый начальный статус:

```json
{
  "pairingId": "<pairingId>",
  "status": "pending",
  "expiresIn": 600,
  "retryable": null,
  "failureReason": null
}
```

### 5. Телефонный wildcard-flow

Откройте `verificationUrl` из ответа `/api/pairings` на телефоне или в desktop-браузере.

Ожидаемо:

- открывается wildcard host `*.auth.bazuka.pp.ua`;
- при отсутствии готовой trusted-device сессии пользователь уходит на `/login`;
- вход идет через проксируемую страницу LostFilm;
- после успешного входа backend подтверждает pairing;
- телевизор получает cookies только через `POST /api/pairings/{pairingId}/claim`.

### 6. Старые ссылки `/pair/...`

Если где-то осталась старая ссылка вида:

```text
https://auth.bazuka.pp.ua/pair/<phone_verifier>
```

она должна временно редиректить на `/` с установкой cookie телефонного flow. Основной сценарий все равно должен использовать wildcard `verificationUrl`.

## Обновление версии

Для GHCR-образа:

```bash
cd ~/lostfilm-auth-bridge
docker compose pull auth-backend
docker compose up -d auth-backend
docker compose logs --tail=100 auth-backend
```

Для фиксированного `sha-*` тега:

1. Обновите `AUTH_BACKEND_IMAGE` в `.env`.
2. Выполните:

```bash
docker compose pull auth-backend
docker compose up -d auth-backend
```

Проверка после обновления:

```bash
docker compose ps
curl -fsS https://auth.bazuka.pp.ua/health/ready
```

## Диагностика

### `verificationUrl` указывает на `auth.example.test`

Проверьте:

- `.env` лежит рядом с `docker-compose.yml`;
- контейнер перезапущен после изменения `.env`;
- заданы оба значения:
  - `AUTH_BRIDGE_PUBLIC_BASE_URL`
  - `AUTH_BRIDGE_PUBLIC_BASE_DOMAIN`

Полезная команда:

```bash
docker compose config
```

Если переменных нет, compose должен завершиться ошибкой еще до запуска.

### Wildcard-страница не открывается

Проверьте:

- DNS-запись для `*.auth.bazuka.pp.ua`;
- reverse proxy принимает wildcard host;
- TLS-сертификат покрывает wildcard host;
- proxy отправляет wildcard-запросы в тот же backend, что и `auth.bazuka.pp.ua`.

### Публичный health работает, но wildcard host показывает не тот сайт

Почти всегда причина в matcher-конфиге reverse proxy. В Caddy server block должен включать и `auth.bazuka.pp.ua`, и `*.auth.bazuka.pp.ua`.

### Статус или claim возвращает `403 Pairing secret is invalid`

Текущая версия защищает pairing-действия заголовком:

```http
X-Pairing-Secret: <pairingSecret>
```

Используйте `pairingSecret` из ответа `POST /api/pairings`.

### Телефонный вход проходит, но TV не получает сессию

Проверьте:

- Android-приложение использует `https://auth.bazuka.pp.ua`;
- pairing response содержит wildcard `verificationUrl`;
- браузер телефона остается на `*.auth.bazuka.pp.ua` во время входа;
- `POST /api/pairings/{pairingId}/claim` вызывается с правильным `X-Pairing-Secret`;
- после успешной проверки на TV вызывается `POST /api/pairings/{pairingId}/finalize`.

### Trusted device не сохраняется

Проверьте:

- volume `trusted-devices` подключен;
- `AUTH_BRIDGE_TRUSTED_DEVICE_DB_PATH=/data/trusted_devices.sqlite3`;
- `AUTH_BRIDGE_TRUSTED_DEVICE_COOKIE_DOMAIN=auth.bazuka.pp.ua`;
- если задан `AUTH_BRIDGE_TRUSTED_DEVICE_SECRET`, он не меняется между рестартами.

### `/api/translate` возвращает 503

Это ожидаемо, если не задан `AUTH_BRIDGE_DEEPL_API_KEY`. Для включения перевода добавьте ключ в `.env` и перезапустите backend:

```bash
docker compose up -d auth-backend
```
