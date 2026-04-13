# AGENTS.md — LostFilm New TV

Руководство для AI-агентов (Codex CLI, Claude Code и аналогов), работающих с этим репозиторием.

---

## Что это за проект

Android TV приложение (`com.kraat.lostfilmnewtv`) для просмотра новых релизов и избранного с lostfilm.today. Рядом живёт небольшой FastAPI-сервис (`backend/auth_bridge`) для QR-авторизации.

---

## Структура репозитория

```
app/                        Android TV клиент (Kotlin, Jetpack Compose for TV)
  src/main/java/com/kraat/lostfilmnewtv/
    data/
      auth/                 QR-авторизация, хранение сессии
      db/                   Room: ReleaseDao, сущности, миграции
      model/                доменные модели (ReleaseSummary, ReleaseDetails, …)
      network/              OkHttp-клиенты: LostFilmHttpClient, AuthBridgeClient, TmdbPosterClient
      parser/               Jsoup-парсеры HTML-страниц lostfilm.today
      poster/               Обогащение постерами через TMDB API
      repository/           LostFilmRepository / LostFilmRepositoryImpl
    di/                     Hilt-модули (AppModule, DataModule, NetworkModule, …)
    navigation/             AppNavGraph, AppDestination
    platform/torrserve/     Интеграция с TorrServe
    playback/               Настройки качества воспроизведения
    tvchannel/              Публикация канала на главном экране Android TV
    updates/                Проверка обновлений через GitHub Releases
    ui/                     Compose-экраны: home, details, guide, overview, search, settings, auth
  src/test/                 JVM unit-тесты (парсеры, DAO, poster resolver)
  src/androidTest/          Instrumented UI-тесты (Hilt + Compose Test)

backend/
  auth_bridge/
    backend/                FastAPI-приложение (Python 3.12)
      src/                  исходники
      tests/                unittest-тесты

docs/                       Дополнительная документация
scripts/
  run-emulator.sh           Запуск AVD tv_test
```

---

## Окружение разработчика

| Зависимость | Версия |
|---|---|
| JDK | 17 (Temurin) |
| Android SDK | compileSdk 35, minSdk 26 |
| Python | 3.12 |
| Gradle | через wrapper `./gradlew` |

Не вызывай `gradle` напрямую — только `./gradlew`.

---

## Команды сборки и проверок

### Android / Kotlin

```bash
# Все проверки, которые прогоняет CI на каждый PR:
./gradlew testDebugUnitTest lint assembleDebug

# Только unit-тесты:
./gradlew :app:testDebugUnitTest

# Только lint:
./gradlew :app:lint

# Debug APK:
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Release APK (нужны переменные окружения с ключами — см. ниже):
./gradlew assembleRelease

# Instrumented UI-тесты (требует подключённого эмулятора/устройства):
./gradlew :app:connectedDebugAndroidTest
```

### Python / auth bridge

```bash
pip install ./backend/auth_bridge/backend
python -m unittest discover -s backend/auth_bridge/backend/tests -p 'test_*.py'
```

### Запуск auth bridge локально

```bash
cd backend/auth_bridge
cp .env.example .env   # заполни переменные
docker compose up -d
```

### Запуск эмулятора

```bash
./scripts/run-emulator.sh   # поднимает AVD tv_test, ждёт adb device
```

---

## CI (GitHub Actions)

**`pull-request-checks`** — срабатывает на push/PR в `main`:
1. Устанавливает Python 3.12, прогоняет auth bridge тесты.
2. Устанавливает JDK 17, прогоняет `testDebugUnitTest lint assembleDebug`.

**`release`** — срабатывает на push в `main`, создаёт подписанный release APK и GitHub Release.

Переменные окружения для release-сборки:
- `ANDROID_KEYSTORE_PATH` или `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

---

## Архитектура приложения

### Слои

```
UI (Jetpack Compose for TV)
  └─ ViewModel (Hilt, coroutines)
       └─ Repository (LostFilmRepositoryImpl)
            ├─ LostFilmHttpClient  →  lostfilm.today (OkHttp + Jsoup)
            ├─ TmdbPosterResolver  →  TMDB API
            └─ ReleaseDao          →  Room (SQLite)
```

### Навигация

Управляется `AppNavGraph` (Navigation Compose). Экраны:

| Маршрут | Описание |
|---|---|
| `home` | Лента новых релизов + избранное; переключение одной кнопкой |
| `details/{url}` | Карточка релиза: постер, описание, торрент-ссылки, избранное |
| `series_overview/{url}` | Обзор сериала |
| `series_guide/{url}` | Сезоны и эпизоды с отметками просмотра |
| `search` | Поиск по названию |
| `settings` | Качество, TV-канал, обновления, аккаунт |
| `auth` | QR-авторизация |

### QR-авторизация

```
TV (AuthRepository)
  → AuthBridgeClient.createPairing()        POST /pairing
  → показывает QR-код пользователю
  → pollPairingStatus() каждые N секунд     GET  /pairing/{id}
  → claimAndPersistSession()                POST /pairing/{id}/claim
  → LostFilmSessionVerifier.verify(session) проверяет сессию на lostfilm.today
  → AuthBridgeClient.finalizeClaim()        POST /pairing/{id}/finalize
  → EncryptedSessionStore.save(session)     шифрует и сохраняет локально
```

### Кеш данных

Room хранит `ReleaseSummaryEntity` и `ReleaseDetailsEntity`. Страница считается свежей 6 часов, удаляется через 7 дней (`FRESH_WINDOW_MS`, `RETENTION_WINDOW_MS` в `LostFilmRepositoryImpl`).

### Воспроизведение

Через TorrServe, ожидаемый адрес `127.0.0.1:8090`. Логика: `TorrServeAvailabilityProbe` → `TorrServeLinkBuilder` → `TorrServeActionHandler`.

### Android TV Home Channel

`HomeChannelSyncManager` публикует программы в канал лаунчера. Фоновое обновление — `HomeChannelBackgroundRefreshRunner` через WorkManager.

---

## Добавление нового экрана

1. Добавь маршрут в `AppDestination.kt`.
2. Зарегистрируй `composable(...)` в `AppNavGraph.kt`.
3. Создай ViewModel с `@HiltViewModel`.
4. Создай Composable в `ui/<feature>/`.
5. Если нужны новые данные — расширь `LostFilmRepository` и `LostFilmRepositoryImpl`.

---

## Добавление нового Hilt-модуля

Используй `@Module @InstallIn(SingletonComponent::class)` (см. `AppModule.kt`). Для сетевых зависимостей — `NetworkModule.kt`, для базы данных — `DatabaseModule.kt`.

---

## Тесты

### Unit-тесты (`src/test`)

Тестируют парсеры (`LostFilmListParserTest`, `LostFilmDetailsParserTest`, …), DAO-запросы (`ReleaseDaoChannelQueryTest`), `TmdbPosterResolver`. Фикстуры HTML хранятся рядом с тестами, загружаются через `FixtureLoader`.

Правило: новый парсер или нетривиальная логика — обязательно с unit-тестами.

### UI/Instrumented тесты (`src/androidTest`)

Покрывают основные пользовательские сценарии:
- `HomeScreenTest` — лента, переключение режимов
- `DetailsScreenTest` — карточка релиза
- `AuthScreenTest` — QR-авторизация
- `AnonymousBrowsingSmokeTest` — работа без аккаунта
- `SettingsScreenFocusTest` — фокус-навигация пультом

Используют `TestDataModule` и `TestNetworkModule` для подмены зависимостей.

---

## Стиль кода

- Kotlin: стандартный Android code style, coroutines + Flow для асинхронщины.
- Compose: функциональные composables, state hoisting, никакого бизнес-логики в UI.
- DI: Hilt везде, вручную конструировать зависимости только в тестах.
- Репозиторий возвращает доменные модели, а не сущности Room напрямую.
- Парсеры (`data/parser/`) не должны знать ни про сеть, ни про БД — только HTML → модели.

---

## Что не трогать без явной задачи

- `EncryptedSessionStore` — работает с ключами KeyStore, легко сломать.
- `Migrations.kt` — миграции Room необратимы; новую миграцию добавлять строго по шаблону уже существующих.
- `build.gradle.kts` — версионирование APK управляется CI через `releaseVersionCode` / `releaseVersionName`; не меняй их вручную.
- `.github/workflows/` — без согласования с владельцем проекта.

---

## Разрешённые автоматические действия (`.qwen/settings.json`)

Агент может выполнять без подтверждения:
`adb *`, `sleep *`, `git add *`, `git commit *`, `git push`, `sed *`, `git reset *`

Всё остальное — запрашивать подтверждение.
