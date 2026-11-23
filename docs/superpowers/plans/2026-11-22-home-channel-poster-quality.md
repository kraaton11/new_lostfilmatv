# Качественные постеры канала Android TV — план реализации

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Публиковать полноразмерные LostFilm-постеры в канале Android TV, чтобы карточки и фокусное превью не растягивали маленькие горизонтальные thumbnails.

**Architecture:** `HomeChannelContentRepository` уже разрешает пути картинок на выбранное зеркало и формирует модель программы для launcher. Перед публикацией заменяем сезонный thumbnail на доступный `shmoster_sN.jpg` через существующий `lostFilmPortraitUrl`; если вариант не найден, оставляем текущий URL. Для фокусного превью используем фон из сводки, а если он пуст, читаем уже сохранённый `ReleaseDetailsEntity.backdropUrl` по `detailsUrl`. Сетевой загрузки в channel sync нет.

**Tech Stack:** Kotlin, Android TV Preview Channels, JUnit, Gradle wrapper, ADB.

---

## Файлы

- Изменить `app/src/main/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelContentRepository.kt`: предпочесть полноразмерный сезонный постер при формировании `HomeChannelProgram.posterUrl`.
- Изменить `app/src/main/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelContentRepository.kt`: при пустом summary backdrop подставлять backdrop из уже закешированных деталей.
- Изменить `app/src/test/java/com/kraat/lostfilmnewtv/tvchannel/HomeChannelContentRepositoryTest.kt`: проверить high-resolution вариант, активное зеркало, fallback неизвестного изображения и неизменность настоящего фона.
- При необходимости изменить `app/src/test/java/com/kraat/lostfilmnewtv/tvchannel/AndroidHomeChannelPublisherTest.kt`: подтвердить, что без отдельного фона launcher получает высококачественный постер также в thumbnail field.

## Task 1: Использовать полноразмерный постер для канала

- [x] **Шаг 1: Обновить тесты преобразования адреса и фона.** В `loadPrograms_usesCurrentMirrorAndSkipsLegacyProxyArtwork` ожидать `/Static/Images/362/Posters/shmoster_s9.jpg` для сезонного постера, проверить прямой и закешированный backdrop, включая разрешение обоих на текущее зеркало. Добавить обычный `image.jpg` и проверить, что для него остаётся исходный путь.
- [x] **Шаг 2: Запустить целевой тест и подтвердить ошибку.** Выполнить `./gradlew :app:testDebugUnitTest --tests 'com.kraat.lostfilmnewtv.tvchannel.HomeChannelContentRepositoryTest'`; новое ожидание `shmoster_s9.jpg` не прошло до реализации.
- [x] **Шаг 3: Сделать минимальное изменение.** В `mapPrograms` использовать `lostFilmPortraitUrl(item.posterUrl, baseUrl)` с fallback на `lostFilmArtworkUrl(item.posterUrl, baseUrl)`. Когда summary backdrop пуст, прочитать `releaseDao.getReleaseDetails(detailsUrl)?.backdropUrl` через `HomeChannelSummaryReader`. Сетевые запросы не добавляются.
- [x] **Шаг 4: Проверить publisher fallback.** Существующий тест publisher подтверждает, что при пустом `backdropUrl` `thumbnailUrl` совпадает с poster URL; при наличии фона остается текущий фон.
- [x] **Шаг 5: Запустить проверки.** `./gradlew --no-parallel testDebugUnitTest lint assembleDebug` завершилась успешно.
- [x] **Шаг 6: Установить сборку и проверить приставку.** APK установлен на `192.168.2.209:5555`, channel sync завершился успешно. В Android TV launcher проверены чёткий сезонный постер и закешированный широкий кадр при фокусе.
