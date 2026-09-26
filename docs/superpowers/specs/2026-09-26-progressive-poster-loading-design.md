# Design: Прогрессивная загрузка постеров на главной ленте

**Дата:** 2026-09-26
**Область:** Android-клиент, лента «Все новое» (`HomeFeedMode.AllNew`)
**Статус:** согласован, готов к планированию

---

## Контекст

`lostfilm.today` не отдаёт постеры — приложение обогащает ленту через TMDB (прокси
`auth.bazuka.pp.ua/api/tmdb`) с фолбэком на Кинопоиск.

Лимиты, которые стоит понимать до изменения:

- TMDB отключила старый лимит 40 req/10s в декабре 2019; текущий мягкий потолок — ~40-50 req/s
  per IP, с явной просьбой уважать `429`
  ([developer.themoviedb.org/docs/rate-limiting](https://developer.themoviedb.org/docs/rate-limiting))
- Приложение ходит не в TMDB, а в собственный прокси на одном общем API-ключе
  (`NetworkModule.kt:121`)
- Прокси держит per-IP лимит `tmdb_rate_limit_max_requests: 240 / 60s` = 4 rps на устройство
  (`config.py:42`, `tmdb.py:60`), причём **до** проверки кеша — cache-hit тоже его тратит
- Прокси скрывает общий LRU-кеш на 5000 записей с TTL 30 дней для картинок
  (`config.py:48-52`), поэтому повторные запросы не достигают TMDB вообще
- Клиентский `TmdbPosterClient.rateLimit()` (300 мс, глобальный Mutex) подобрано ровно под
  серверные 4 rps и **не трогается**

## Проблема

`LostFilmRepositoryImpl.loadPage` (`:138-148`) ждёт `enrichSummaries` всей страницы перед тем,
как отдать `PageState.Content`. Наполнение страницы — ~20 item'ов, на каждый 3-4 запроса
(search → images → details), итого ~60 обращений к прокси. При лимите 4 rps это **15-20 секунд
полного скелетона** на чистой установке, пока в Room ещё нет ни одной строки.

На повторных запусках ситуация лучше (прокси-кеш + Room), но первая отрисовка после установки —
самый частый первый сценарий, и он сейчас самый медленный.

Дополнительно: пагинация страниц 2+ ждёт обогащение так же, хотя `loadPageDirect` всё равно
использует только один результат.

## Цель

Лента рисуется сразу после получения HTML, постеры проявляются по одному по мере готовности.
Никаких изменений лимитов, схемы БД и серверного кода.

## Границы

**В объёме**

- `HomeFeedMode.AllNew`: первая загрузка и пагинация
- рефакторинг дублирующихся блоков в `TmdbPosterResolverImpl.resolve()`

**Вне объёма (осознанно)**

- ленты «Фильмы» и «Каталог сериалов» остаются блокирующими: они не персистятся в Room
  (`persistToCache = false`) и не гейтят первую отрисовку — `HomeScreen.kt:342` ждёт
  `state.items` только для `AllNew`
- `FavoritesRepository` — без изменений
- лимиты бэкенда, `Retry-After`-backoff, `matcherVersion` вместо magic-timestamps — следующие
  итерации
- `ReleaseDao` не получает Flow-запросов (см. «Отвергнутые альтернативы»)

---

## Дизайн

### 1. `TmdbEnrichmentService.enrichProgressively`

```kotlin
fun enrichProgressively(items: List<ReleaseSummary>): Flow<ReleaseSummary>
```

Холодный Flow. На каждый обогащённый item emit'ит его и точечно пишет арт в Room.

- переиспользует существующий `Semaphore(6)` и дедупликацию по `detailsUrl`
  (`ConcurrentHashMap<String, Deferred<...>>`)
- переиспользует `TmdbPosterEnricher.enrichSummary`
- **не emit'ит** item, если арт не изменилось: тот же `posterUrl`, либо `null`/пустой результат.
  Иначе при 20 item'ах и пустых ответах TMDB получим 20 идентичных пересборок `HomeUiState`
- существующий `enrichSummaries` не меняется — он остаётся для movies/series/favorites

### 2. `ReleaseDao.updateSummaryArtwork`

```kotlin
@Query(
    """
    UPDATE release_summaries SET
        posterUrl = :posterUrl,
        backdropUrl = :backdropUrl,
        episodeOverviewRu = :episodeOverviewRu,
        episodeOverviewSource = :episodeOverviewSource,
        seriesOverviewRu = :seriesOverviewRu,
        movieOverviewRu = :movieOverviewRu,
        tmdbRating = :tmdbRating
    WHERE detailsUrl = :detailsUrl
    """,
)
suspend fun updateSummaryArtwork(...): Int
```

Только art-колонки. Это защищает от гонки: пользователь отмечает эпизод просмотренным, пока идёт
обогащение, — полный `upsertSummaries` откатил бы `isWatched` назад. Схема не меняется, миграция
не нужна.

### 3. `LostFilmRepository.observePage`

`observeNewReleases` переименовывается в `observePage` и становится единственным источником
страницы для `AllNew` (страницы 1 и 2+).

Тело реализации по умолчанию остаётся `flow { emit(loadPage(pageNumber)) }` — поэтому все
тестовые фейки, переопределяющие только `loadPage`, продолжают компилироваться без правок.
Переименовать потребуется только `HomeViewModelTest.FakeRepository`.

```kotlin
fun observePage(pageNumber: Int): Flow<PageState>
```

Последовательность:

1. Прочитать `getSummariesUpToPage(pageNumber)` + `getPageMetadata(pageNumber)`.
   Если строки есть — emit `Content(items, isStale = !cacheFresh)`. `hasNextPage` берётся из
   metadata, а при её отсутствии — `true` (текущее поведение на `:181`).
2. Если кэш свежий — `return`.
3. Сеть → `listParser.parse` → `mergeWatchedState` → `replacePage` →
   **emit сразу**, art пустой → плейсхолдеры. Обогащение не awaited.
4. `enrichProgressively(itemsToPersist).collect { enriched -> emit(Content(...)) }`,
   где список аккумулируется: текущий список с заменой item'а по `detailsUrl`.
5. Ошибка сети при непустом кэше → `Content(cached, isStale = true)`, как сейчас.
   Без кэша → `PageState.Error`.

`suspend fun loadPage(pageNumber)` сохраняется — его зовут «Обновить первую страницу» в
настройках (`SettingsBackendServices.kt:83`, `AppModule.kt:198`). Семантика меняется: ждать
обогащения он больше не будет, а запустит его в отсоединённой скоупе.

### 4. `@ApplicationScope`

В проекте нет application-скоупа. Добавляется квалификатор и провайдер в `AppModule`:

```kotlin
CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

Нужен только для `loadPage`, где вызывающий suspend-метод не может удерживать дочернюю
coroutine. В `observePage` обогащение живёт внутри flow и отменяется вместе с коллектором
автоматически — там application-скоуп не используется.

### 5. `HomeViewModel`

Три правки:

- `observeNewReleases()` (`:390`) → `repository.observePage(1)`. Сборка уже умеет множественные
  emission `Content` и заменяет `items` целиком (`:407-432`)
- **защита от повторной синхронизации:** сейчас на каждой emission с `isStale = false` вызывается
  `homeChannelSyncManager.syncNow()` (`:426-431`). При 20 прогрессивных emission это будет 20
  синхронизаций. Добавляется флаг `didSyncChannel` на коллекцию — по образцу уже существующего
  `hadCacheEmission`. `lastAllNewRefreshAt` выставляется в той же ветке
- `loadPageDirect` (`:485`): `when (val result = repository.loadPage(pageNumber))` →
  `repository.observePage(pageNumber).collect { result -> ... }`. Обработка `Content`/`Error`
  та же, `distinctBy { it.detailsUrl }` (`:489`) уже есть. Пагинация получает прогрессивные
  постеры тем же кодом
- удалить неиспользуемый `tmdbEnrichmentService` из конструктора (`:40`) — после рефакторинга он
  остался бы мёртвым

### 6. Рефакторинг `TmdbPosterResolverImpl.resolve()`

Блоки `:87-138` (fast path) и `:141-197` (тот же cache-hit под локом) — почти дословные копии.
Выносятся в общий приватный `lookupCachedMapping(...)`. Поведение не меняется, существующий
`TmdbPosterResolverTest` (858 строк) должен остаться зелёным без правок. Отдельный коммит.

---

## Поток данных

```
onStart() → HomeViewModel.observeNewReleases()
  └─ repository.observePage(1).collect
       ├─ Room есть?  → emit Content(из Room, isStale=?)
       │                 кэш свежий? → return
       ├─ сеть + парс + replacePage
       ├─ emit Content(art пуст)                    ← лента на экране, плейсхолдеры
       └─ enrichProgressively().collect
            ├─ UPDATE release_summaries SET art…    ← по одной строке
            └─ emit Content(с артом)                ← постер проявляется
```

## Ошибки и отмена

- ошибка сети до emit'а свежих данных → `Content(cached, isStale = true)` либо `Error`
- ошибка/отсутствие арта у отдельного item → item остаётся с плейсхолдером, `PageState` не
  меняется, negative-кэш пишется резолвером как раньше
- отмена коллектора (пользователь ушёл с экрана) → обогащение прерывается, уже записанный арт
  остаётся в Room, догрузка продолжится при следующем заходе
- `CancellationException` пробрасывается, как сейчас

## Тестирование

TDD, `./gradlew :app:testDebugUnitTest`. Инфраструктура готова: Robolectric + in-memory Room +
реальные парсеры и фикстуры (`LostFilmRepositoryTest.kt:63`).

| Тест | Что проверяет |
|---|---|
| `LostFilmRepositoryTest` | первая `Content` приходит **до** завершения обогащения (фейк-резолвер, удерживающий через suspend-гейт); следующие emission несут арт |
| `LostFilmRepositoryTest` | `isWatched = true`, проставленное после `replacePage`, не сбрасывается `updateSummaryArtwork` |
| `LostFilmRepositoryTest` | свежий кэш → одна emission, сеть не вызвана |
| `LostFilmRepositoryTest` | сеть упала, кэш есть → `Content(isStale = true)`; кэша нет → `Error` |
| `LostFilmRepositoryTest` | item без арта не приводит к лишней emission |
| `HomeViewModelTest` | три emission `Content` → `syncNow()` вызван **один** раз |
| `HomeViewModelTest` | пагинация: emissions добавляются, дублей `detailsUrl` нет |
| `ReleaseDaoArtworkUpdateTest` | `updateSummaryArtwork` меняет только art-колонки |
| `TmdbPosterResolverTest` | существующие тесты зелёные без правок (рефакторинг) |

## Риски

- **`matchesRail` сравнивает списки по `===`** (`HomeViewModel.kt:929`) → на каждую emission
  рельсы пересоздаются. `LazyRow` использует `key = item.detailsUrl` (`HomeRail.kt:133`), а
  фокус резолвится по `detailsUrl` (`HomeViewModel.kt:872-876`), поэтому фокус и позиция
  сохраняются. Проверяется существующим `HomeScreenTest`
- **20 emission = 20 пересборок `HomeUiState`.** На TV приемлемо; именно поэтому в
  `enrichProgressively` запрещены no-op emission'ы
- **`LaunchedEffect` с `targetIndex` в `HomeRail.kt:101`** может вызвать нежелательный
  `scrollToItem`, если прогрессивная emission посчитана как смена фокуса. Отслеживается при
  ручной проверке Home-экрана
- **`loadPage` без ожидания меняет контракт** для настроек и `AppModule.kt:198` — они теперь
  получают `Content` раньше, с плейсхолдерами. Это ожидаемо, но проверяется тестом

## Отвергнутые альтернативы

- **Room Flow как единственный источник истины.** Flow пере-эмитит на любую запись в
  `release_summaries`, включая `updateSummaryWatched` и `deleteExpiredSummaries` — лишние
  пересборки UI на каждом чекбоксе «просмотрено». Плюс `isStale`/`hasNextPage` не выразить в
  Flow, что требует реструктуризации `observeNewReleases`
- **Callback из `enrichSummaries` + свой `MutableStateFlow` в ViewModel.** Меньше кода, но
  callback-сигнатура и ещё один стор поверх Room. Flow уже является идиоматичным способом
  выразить «эмитим по мере готовности»
- **Поднять лимиты.** Серверные 240/мин и клиентские 300 мс подобраны согласованно; разрыв
  даст `429` от собственного бэкенда. Быстрый холодный старт достигается структурно, а не
  скоростью запросов
