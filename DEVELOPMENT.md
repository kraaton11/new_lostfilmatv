# Руководство для разработчиков

Документация для разработчиков, желающих собрать приложение из исходников, внести изменения или контрибьютить в проект.

## Требования для разработки

| Зависимость | Версия |
|---|---|
| JDK | 17 |
| Android SDK | compileSdk 35, minSdk 26 |
| Gradle | 8.x (wrapper включен) |
| Python | 3.12+ (для auth bridge) |
| Docker | Опционально, для auth bridge |

## Сборка из исходников

### Клонирование репозитория

```bash
git clone https://github.com/kraaton11/new_lostfilmatv.git
cd new_lostfilmatv
```

### Debug сборка

```bash
./gradlew assembleDebug
```

Debug APK будет доступен:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Установка через ADB

```bash
adb connect 192.168.x.x:5555
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Разработка

### Основные команды

Запуск всех проверок:

```bash
./gradlew testDebugUnitTest lint assembleDebug
```

Отдельные команды:

```bash
# Unit-тесты
./gradlew :app:testDebugUnitTest

# Линтер
./gradlew :app:lint

# Сборка debug APK
./gradlew assembleDebug

# Instrumented тесты (требуют подключенное устройство/эмулятор)
./gradlew :app:connectedDebugAndroidTest
```

### Запуск Android TV эмулятора

```bash
./scripts/run-emulator.sh
```

Убедитесь, что у вас установлен Android TV system image через Android Studio SDK Manager.

## Release-сборка

### Подготовка

Для подписанной release-сборки настройте переменные окружения:

```bash
export ANDROID_KEYSTORE_PATH=/path/to/keystore.jks
export ANDROID_KEYSTORE_PASSWORD=your_keystore_password
export ANDROID_KEY_ALIAS=your_key_alias
export ANDROID_KEY_PASSWORD=your_key_password
```

Или используйте base64-encoded keystore для CI:

```bash
export ANDROID_KEYSTORE_BASE64=$(cat keystore.jks | base64 -w 0)
```

### Версионирование

Версия задается в `gradle.properties`:

```properties
releaseVersionCode=1
releaseVersionName=1.0.0
```

### Сборка

```bash
./gradlew assembleRelease
```

Release APK:

```text
app/build/outputs/apk/release/app-release.apk
```

## Auth Bridge

`backend/auth_bridge` — FastAPI-сервис для QR-авторизации. Телевизор создает pairing, показывает QR-код, пользователь сканирует его телефоном и входит через браузер, после чего приложение получает сессию.

### Локальный запуск

```bash
cd backend/auth_bridge
cp .env.example .env
# Отредактируйте .env при необходимости
docker compose up -d
```

Сервис будет доступен на `http://localhost:8000`.

### Разработка без Docker

```bash
cd backend/auth_bridge/backend
pip install -e .
uvicorn app.main:app --reload
```

### Тесты backend

```bash
pip install ./backend/auth_bridge/backend
python -m unittest discover -s backend/auth_bridge/backend/tests -p 'test_*.py'
```

### Документация Auth Bridge

- [auth-bridge-server-install.md](docs/auth-bridge-server-install.md) — установка на сервер
- [auth-bridge-ops.md](docs/auth-bridge-ops.md) — эксплуатация и мониторинг

## Стек технологий

### Android клиент

- **Язык:** Kotlin 2.x
- **UI:** Jetpack Compose for TV
- **Навигация:** Navigation Compose
- **DI:** Hilt
- **База данных:** Room
- **Сеть:** OkHttp, Retrofit
- **HTML парсинг:** Jsoup
- **Изображения:** Coil
- **Фоновые задачи:** WorkManager
- **Сериализация:** kotlinx.serialization

### Backend

- **Язык:** Python 3.12+
- **Фреймворк:** FastAPI
- **ASGI сервер:** Uvicorn
- **Валидация:** Pydantic

## Структура проекта

```text
new_lostfilmatv/
├── app/                          Android TV приложение
│   ├── src/main/
│   │   ├── java/com/example/lostfilm/
│   │   │   ├── ui/               Compose UI компоненты
│   │   │   ├── data/             Репозитории, API, БД
│   │   │   ├── domain/           Use cases, модели
│   │   │   └── di/               Hilt модули
│   │   ├── res/                  Ресурсы (layouts, drawables, strings)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── backend/
│   └── auth_bridge/              FastAPI сервис QR-авторизации
│       ├── backend/
│       │   ├── app/              FastAPI приложение
│       │   └── tests/            Unit-тесты
│       └── docker-compose.yml
├── docs/                         Документация
│   ├── screenshots/              Скриншоты приложения
│   ├── auth-bridge-*.md          Документация backend
│   └── README.project.md         Описание проекта
├── scripts/                      Вспомогательные скрипты
├── .github/workflows/            CI/CD пайплайны
├── README.md                     Основная документация (для пользователей)
├── DEVELOPMENT.md                Это руководство (для разработчиков)
└── build.gradle.kts              Root build config
```

## Контрибуция

### Процесс

1. Форкните репозиторий
2. Создайте feature-ветку (`git checkout -b feature/amazing-feature`)
3. Внесите изменения
4. Убедитесь, что проходят тесты и линтер: `./gradlew testDebugUnitTest lint`
5. Закоммитьте изменения (`git commit -m 'Add amazing feature'`)
6. Запушьте в ветку (`git push origin feature/amazing-feature`)
7. Откройте Pull Request

### Code style

- Следуйте [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Используйте ktlint для форматирования (настроен в проекте)
- Пишите осмысленные commit messages

### Тестирование

- Добавляйте unit-тесты для новой бизнес-логики
- Проверяйте UI-изменения на эмуляторе Android TV
- Убедитесь, что все существующие тесты проходят

## CI/CD

GitHub Actions автоматически:
- Собирает debug APK на каждый push
- Запускает тесты и линтер
- Собирает release APK для tagged commits
- Публикует release на GitHub Releases

## Дополнительная документация

- [docs/README.project.md](docs/README.project.md) — подробное описание проекта
- [docs/github-setup.md](docs/github-setup.md) — настройка GitHub репозитория
- [docs/auth-bridge-server-install.md](docs/auth-bridge-server-install.md) — установка auth bridge
- [docs/auth-bridge-ops.md](docs/auth-bridge-ops.md) — эксплуатация auth bridge

## Поддержка

Вопросы по разработке? Создайте [Discussion](../../discussions) или [Issue](../../issues) на GitHub.
