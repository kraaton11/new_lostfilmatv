# Прогрессивная загрузка постеров — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Лента «Все новое» рисуется сразу после получения HTML, постеры проявляются по одному по мере готовности вместо 15-20 секунд полного скелетона.

**Architecture:** `ReleaseDao` получает точечный `UPDATE` только art-колонок. `TmdbEnrichmentService` получает холодный `Flow<ReleaseSummary>`, который emit'ит каждый обогащённый item и пишет арт в Room по мере готовности. `LostFilmRepository.observeNewReleases` переименовывается в `observePage` и возвращает `Flow<PageState>`: кэш из Room → сеть+persist → emit без арта → прогрессивные emit'ы. `HomeViewModel` собирает этот flow и гасит повторные `syncNow()`.

**Tech Stack:** Kotlin, Jetpack Compose for TV, Room, Hilt, coroutines/Flow, Robolectric + JUnit4.

**Спека:** `docs/superpowers/specs/2026-09-26-progressive-poster-loading-design.md`

---

## Отклонение от спеки

Спека предлагала добавить `@ApplicationScope` и скоуп для фонового обогащения внутри `loadPage`. В плане этого нет: `loadPage` остаётся **блокирующим** и используется только ручными действиями («Обновить первую страницу» в настройках, `SettingsBackendServices.kt:83` и `AppModule.kt:198`), где ожидание уместно и даже полезно — постеры гарантированно прогреты. Для Home нужен только прогрессивный `observePage`, а его обогащение живёт внутри flow и отменяется вместе с коллектором.

Итого: новая DI-скоуп не вводится, `DataModule.kt` / `AppModule.kt` / `DispatchersModule.kt` не меняются, ~40 существующих тестов на `loadPage` не ломаются.

---

## Структура файлов

| Файл | Действие | Ответственность |
|---|---|---|
| `app/src/main/java/com/kraat/lostfilmnewtv/data/db/ReleaseDao.kt` | изменить | точечный `updateSummaryArtwork` |
| `app/src/main/java/com/kraat/lostfilmnewtv/data/poster/TmdbEnrichmentService.kt` | изменить | `enrichProgressively` + persist арта |
| `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepository.kt` | изменить | переименование `observeNewReleases` → `observePage` |
| `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryImpl.kt` | изменить | прогрессивный `observePage` |
| `app/src/main/java/com/kraat/lostfilmnewtv/data/poster/TmdbPosterResolver.kt` | изменить | рефакторинг дублей в `resolve()` |
| `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModel.kt` | изменить | collect + защита от повторного `syncNow()` |
| `app/src/test/java/com/kraat/lostfilmnewtv/data/db/ReleaseDaoArtworkUpdateTest.kt` | создать | тесты точечного UPDATE |
| `app/src/test/java/com/kraat/lostfilmnewtv/data/poster/TmdbEnrichmentServiceTest.kt` | создать | тесты `enrichProgressively` |
| `app/src/test/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryTest.kt` | изменить | тесты `observePage` |
| `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModelTest.kt` | изменить | тесты на multiple emission |

В `main` появляются только два метода. Схема БД и миграции не затрагиваются.

---

## Task 1: `ReleaseDao.updateSummaryArtwork`

**Files:**
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/data/db/ReleaseDaoArtworkUpdateTest.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/db/ReleaseDao.kt:63`

- [ ] **Step 1: Написать падающий тест**

Создать `app/src/test/java/com/kraat/lostfilmnewtv/data/db/ReleaseDaoArtworkUpdateTest.kt`:

```kotlin
package com.kraat.lostfilmnewtv.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val DETAILS_URL = "https://www.lostfilm.tv/series/casino/season_1/episode_1/"
private const val SEED_FETCHED_AT = 1_773_576_000_000L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReleaseDaoArtworkUpdateTest {
    private lateinit var database: LostFilmDatabase
    private lateinit var releaseDao: ReleaseDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LostFilmDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        releaseDao = database.releaseDao()
    }

    @After
    fun tearDown() {
        if (this::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun updateSummaryArtwork_writesAllArtworkColumns() = runTest {
        releaseDao.upsertSummaries(listOf(seedEntity()))

        val rowsUpdated = releaseDao.updateSummaryArtwork(
            detailsUrl = DETAILS_URL,
            posterUrl = "https://image.tmdb.org/t/p/w780/poster.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w1280/backdrop.jpg",
            episodeOverviewRu = "Эпизод идёт",
            episodeOverviewSource = "TMDB_RU",
            seriesOverviewRu = "Обзор сериала",
            movieOverviewRu = null,
            tmdbRating = "8.4",
        )

        assertEquals(1, rowsUpdated)
        val stored = releaseDao.getSummary(DETAILS_URL)
        requireNotNull(stored)
        assertEquals("https://image.tmdb.org/t/p/w780/poster.jpg", stored.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w1280/backdrop.jpg", stored.backdropUrl)
        assertEquals("Эпизод идёт", stored.episodeOverviewRu)
        assertEquals("TMDB_RU", stored.episodeOverviewSource)
        assertEquals("Обзор сериала", stored.seriesOverviewRu)
        assertNull(stored.movieOverviewRu)
        assertEquals("8.4", stored.tmdbRating)
    }

    @Test
    fun updateSummaryArtwork_keepsWatchedFlagAndOrderingColumns() = runTest {
        releaseDao.upsertSummaries(listOf(seedEntity(isWatched = true)))

        releaseDao.updateSummaryArtwork(
            detailsUrl = DETAILS_URL,
            posterUrl = "https://image.tmdb.org/t/p/w780/poster.jpg",
            backdropUrl = null,
            episodeOverviewRu = null,
            episodeOverviewSource = null,
            seriesOverviewRu = null,
            movieOverviewRu = null,
            tmdbRating = null,
        )

        val stored = releaseDao.getSummary(DETAILS_URL)
        requireNotNull(stored)
        assertTrue("isWatched не должен сбрасываться точечным UPDATE", stored.isWatched)
        assertEquals(3, stored.positionInPage)
        assertEquals(2, stored.pageNumber)
        assertEquals(SEED_FETCHED_AT, stored.fetchedAt)
        assertEquals("Казино", stored.titleRu)
    }

    @Test
    fun updateSummaryArtwork_returnsZero_whenDetailsUrlIsUnknown() = runTest {
        releaseDao.upsertSummaries(listOf(seedEntity()))

        val rowsUpdated = releaseDao.updateSummaryArtwork(
            detailsUrl = "https://www.lostfilm.tv/series/unknown/",
            posterUrl = "https://image.tmdb.org/t/p/w780/poster.jpg",
            backdropUrl = null,
            episodeOverviewRu = null,
            episodeOverviewSource = null,
            seriesOverviewRu = null,
            movieOverviewRu = null,
            tmdbRating = null,
        )

        assertEquals(0, rowsUpdated)
    }

    private fun seedEntity(isWatched: Boolean = false) = ReleaseSummaryEntity(
        detailsUrl = DETAILS_URL,
        kind = "SERIES",
        titleRu = "Казино",
        episodeTitleRu = null,
        seasonNumber = 1,
        episodeNumber = 1,
        releaseDateRu = "14 марта 2026",
        posterUrl = "",
        pageNumber = 2,
        positionInPage = 3,
        fetchedAt = SEED_FETCHED_AT,
        isWatched = isWatched,
    )
}
```

- [ ] **Step 2: Убедиться, что тест падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReleaseDaoArtworkUpdateTest*'`

Expected: FAIL — компиляционная ошибка `Unresolved reference: updateSummaryArtwork`

- [ ] **Step 3: Добавить метод в DAO**

В `app/src/main/java/com/kraat/lostfilmnewtv/data/db/ReleaseDao.kt` вставить сразу после `upsertSummaries` (строка 63):

```kotlin
    /**
     * Обновляет только art-поля карточки. Полный [upsertSummaries] откатил бы
     * isWatched, если пользователь отметил эпизод просмотренным, пока шло
     * обогащение постерами.
     */
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
    suspend fun updateSummaryArtwork(
        detailsUrl: String,
        posterUrl: String,
        backdropUrl: String?,
        episodeOverviewRu: String?,
        episodeOverviewSource: String?,
        seriesOverviewRu: String?,
        movieOverviewRu: String?,
        tmdbRating: String?,
    ): Int
```

- [ ] **Step 4: Убедиться, что тест проходит**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReleaseDaoArtworkUpdateTest*'`

Expected: PASS (3 теста)

- [ ] **Step 5: Закоммитить**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/data/db/ReleaseDao.kt \
        app/src/test/java/com/kraat/lostfilmnewtv/data/db/ReleaseDaoArtworkUpdateTest.kt
git commit -m "feat: точечный UPDATE art-колонок в release_summaries"
```

---

## Task 2: `TmdbEnrichmentService.enrichProgressively`

**Files:**
- Create: `app/src/test/java/com/kraat/lostfilmnewtv/data/poster/TmdbEnrichmentServiceTest.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/poster/TmdbEnrichmentService.kt:20-27` (интерфейс), `:29-129` (реализация)

- [ ] **Step 1: Написать падающий тест**

Создать `app/src/test/java/com/kraat/lostfilmnewtv/data/poster/TmdbEnrichmentServiceTest.kt`:

```kotlin
package com.kraat.lostfilmnewtv.data.poster

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kraat.lostfilmnewtv.data.db.LostFilmDatabase
import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.db.ReleaseSummaryEntity
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.model.TmdbImageUrls
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val FIRST_URL = "https://www.lostfilm.tv/series/first/season_1/episode_1/"
private const val SECOND_URL = "https://www.lostfilm.tv/series/second/season_1/episode_1/"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TmdbEnrichmentServiceTest {
    private lateinit var database: LostFilmDatabase
    private lateinit var releaseDao: ReleaseDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LostFilmDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        releaseDao = database.releaseDao()
    }

    @After
    fun tearDown() {
        if (this::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun enrichProgressively_emitsItemAndPersistsArtwork_whenResolverFindsMatch() = runTest {
        releaseDao.upsertSummaries(listOf(entity(FIRST_URL), entity(SECOND_URL)))
        val service = createService { detailsUrl ->
            TmdbImageUrls(
                posterUrl = "https://image.tmdb.org/t/p/w780/${detailsUrl.hashCode()}.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w1280/${detailsUrl.hashCode()}.jpg",
                rating = "7.7",
            )
        }

        val emitted = service.enrichProgressively(listOf(summary(FIRST_URL), summary(SECOND_URL))).toList()

        assertEquals(listOf(FIRST_URL, SECOND_URL), emitted.map { it.detailsUrl })
        assertTrue(emitted.all { it.posterUrl.isNotBlank() })
        assertEquals("7.7", emitted.first().tmdbRating)

        val stored = releaseDao.getSummary(FIRST_URL)
        requireNotNull(stored)
        assertEquals(emitted.first().posterUrl, stored.posterUrl)
        assertEquals(emitted.first().backdropUrl, stored.backdropUrl)
        assertEquals("7.7", stored.tmdbRating)
    }

    @Test
    fun enrichProgressively_emitsNothing_whenResolverReturnsNothing() = runTest {
        releaseDao.upsertSummaries(listOf(entity(FIRST_URL)))
        val service = createService { null }

        val emitted = service.enrichProgressively(listOf(summary(FIRST_URL))).toList()

        assertTrue("Пустой результат не должен приводить к эмиссии", emitted.isEmpty())
    }

    @Test
    fun enrichProgressively_skipsItemsThatAlreadyHaveCompleteArtwork() = runTest {
        val resolvedUrls = mutableListOf<String>()
        val service = createService { detailsUrl ->
            resolvedUrls += detailsUrl
            TmdbImageUrls(posterUrl = "p", backdropUrl = "b")
        }
        val complete = summary(FIRST_URL).copy(
            posterUrl = "https://image.tmdb.org/t/p/w780/old.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w1280/old.jpg",
        )

        val emitted = service.enrichProgressively(listOf(complete)).toList()

        assertTrue(emitted.isEmpty())
        assertTrue("Резолвер не должен вызываться для готовых карточек", resolvedUrls.isEmpty())
    }

    @Test
    fun enrichProgressively_emitsNothing_whenItemListIsEmpty() = runTest {
        val service = createService { error("резолвер не должен вызываться") }

        val emitted = service.enrichProgressively(emptyList()).toList()

        assertTrue(emitted.isEmpty())
    }

    private fun createService(resolve: (String) -> TmdbImageUrls?) = TmdbEnrichmentServiceImpl(
        tmdbResolver = object : TmdbPosterResolver {
            override suspend fun resolve(
                detailsUrl: String,
                titleRu: String,
                releaseDateRu: String,
                kind: ReleaseKind,
                originalReleaseYear: Int?,
            ): TmdbImageUrls? = resolve(detailsUrl)
        },
        releaseDao = releaseDao,
    )

    private fun summary(detailsUrl: String) = ReleaseSummary(
        id = detailsUrl,
        kind = ReleaseKind.SERIES,
        titleRu = "Казино",
        episodeTitleRu = null,
        seasonNumber = 1,
        episodeNumber = 1,
        releaseDateRu = "14 марта 2026",
        posterUrl = "",
        detailsUrl = detailsUrl,
        pageNumber = 1,
        positionInPage = 1,
        fetchedAt = 0L,
    )

    private fun entity(detailsUrl: String) = ReleaseSummaryEntity.fromModel(summary(detailsUrl))
}
```

- [ ] **Step 2: Убедиться, что тест падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*TmdbEnrichmentServiceTest*'`

Expected: FAIL — `Unresolved reference: enrichProgressively`

- [ ] **Step 3: Добавить метод в интерфейс**

В `app/src/main/java/com/kraat/lostfilmnewtv/data/poster/TmdbEnrichmentService.kt` в интерфейс `TmdbEnrichmentService` после `enrichSearchItems` (строка 26) добавить:

```kotlin
    /**
     * Обогащает постеры постепенно: эмитит каждый item по мере готовности и
     * точечно пишет art-поля в Room. Холодный flow — обогащение отменяется
     * вместе с коллектором.
     */
    fun enrichProgressively(items: List<ReleaseSummary>): Flow<ReleaseSummary>
```

Добавить импорт `kotlinx.coroutines.flow.Flow` в список импортов файла (рядом с существующим `kotlinx.coroutines.flow` — его нет, импорты начинаются с `kotlinx.coroutines.CancellationException`).

- [ ] **Step 4: Реализовать в `TmdbEnrichmentServiceImpl`**

В `app/src/main/java/com/kraat/lostfilmnewtv/data/poster/TmdbEnrichmentService.kt` после закрывающей скобки `enrichSearchItems` (конец метода — строка 120) вставить:

```kotlin

    override fun enrichProgressively(items: List<ReleaseSummary>): Flow<ReleaseSummary> = channelFlow {
        val pending = items.filterNot { it.hasCompleteArt() }
        if (pending.isEmpty()) {
            return@channelFlow
        }

        val semaphore = Semaphore(6)
        val deferred = pending.map { item ->
            async {
                semaphore.withPermit {
                    item to tmdbResolver.resolve(
                        detailsUrl = item.detailsUrl,
                        titleRu = item.titleRu,
                        releaseDateRu = item.releaseDateRu,
                        kind = item.kind,
                        originalReleaseYear = item.originalReleaseYear,
                    )
                }
            }
        }

        // Порядок эмиссии — порядок item'ов в странице: постеры проявляются
        // слева направо, а не в случайном порядке завершения.
        for (deferredItem in deferred) {
            val (item, urls) = deferredItem.await()
            val enriched = TmdbPosterEnricher.enrichSummary(item, urls)
            if (enriched.hasSameArtworkAs(item)) {
                continue
            }
            releaseDao.updateSummaryArtwork(
                detailsUrl = enriched.detailsUrl,
                posterUrl = enriched.posterUrl,
                backdropUrl = enriched.backdropUrl,
                episodeOverviewRu = enriched.episodeOverviewRu,
                episodeOverviewSource = enriched.episodeOverviewSource,
                seriesOverviewRu = enriched.seriesOverviewRu,
                movieOverviewRu = enriched.movieOverviewRu,
                tmdbRating = enriched.tmdbRating,
            )
            send(enriched)
        }
    }

    private fun ReleaseSummary.hasSameArtworkAs(other: ReleaseSummary): Boolean =
        posterUrl == other.posterUrl &&
            backdropUrl == other.backdropUrl &&
            episodeOverviewRu == other.episodeOverviewRu &&
            episodeOverviewSource == other.episodeOverviewSource &&
            seriesOverviewRu == other.seriesOverviewRu &&
            movieOverviewRu == other.movieOverviewRu &&
            tmdbRating == other.tmdbRating
```

Добавить импорт `kotlinx.coroutines.flow.channelFlow` в список импортов файла.

`async` внутри `channelFlow` доступен через `ProducerScope` (наследник `CoroutineScope`); `Semaphore`, `withPermit`, `async`, `await`, `coroutineScope` уже импортированы.

- [ ] **Step 5: Убедиться, что тесты проходят**

Run: `./gradlew :app:testDebugUnitTest --tests '*TmdbEnrichmentServiceTest*'`

Expected: PASS (4 теста)

- [ ] **Step 6: Закоммитить**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/data/poster/TmdbEnrichmentService.kt \
        app/src/test/java/com/kraat/lostfilmnewtv/data/poster/TmdbEnrichmentServiceTest.kt
git commit -m "feat: прогрессивное обогащение постерами через Flow"
```

---

## Task 3: `observePage` в репозитории

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepository.kt:73-81`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryImpl.kt:62-64` (комментарий), `:166-191` (замена `observeNewReleases`)
- Test: `app/src/test/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryTest.kt`

- [ ] **Step 1: Переименовать вызовы в существующих тестах**

В `app/src/test/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryTest.kt`:
- заменить `repository.observeNewReleases(1).toList()` → `repository.observePage(1).toList()` в строках 120 и 136
- переименовать тесты: `observeNewReleases_skipsNetwork_whenRoomCacheIsFresh` → `observePage_skipsNetwork_whenRoomCacheIsFresh`, `observeNewReleases_hitsNetwork_whenRoomCacheIsStale` → `observePage_hitsNetwork_whenRoomCacheIsStale`

Run: `./gradlew :app:testDebugUnitTest --tests '*LostFilmRepositoryTest*'`

Expected: FAIL — `Unresolved reference: observePage`

- [ ] **Step 2: Переименовать и переписать в интерфейсе и реализации одним шагом**

Интерфейс и реализация правятся вместе: пока в интерфейсе новое имя, а в реализации старое, проект не компилируется.

В `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepository.kt` заменить блок строк 73-81 на:

```kotlin
    /**
     * Источник страницы ленты «Все новое». Первая эмиссия — кэш из Room
     * (если он есть, с `isStale=true`). Если кэш протух, сначала эмитится
     * свежая страница без артов, затем постеры приезжают по одному.
     * Реализация по умолчанию эмитит только свежий результат [loadPage] —
     * перекрывается в реальной реализации.
     */
    fun observePage(pageNumber: Int = 1): Flow<PageState> = flow {
        emit(loadPage(pageNumber))
    }
```

В `app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryImpl.kt` заменить комментарий на строках 62-64:

```kotlin
// How long the Room-persisted page 1 cache stays fresh. While the cache is fresh,
// observePage skips the network request and serves data directly from Room.
```

Заменить `observeNewReleases` (строки 166-191) на:

```kotlin
    override fun observePage(pageNumber: Int): Flow<PageState> = flow {
        // 1. Сначала отдаём кэш из Room (если он есть), без обращения к сети.
        //    Скелетон в HomeScreen не показывается, если items непустые и isInitialLoading=false —
        //    поэтому в HomeViewModel на cache-эмиссии нужно одновременно сбросить этот флаг
        //    и fullScreenErrorMessage, иначе UI решит, что данных нет.
        val cachedItems = releaseDao.getSummariesUpToPage(pageNumber).toSummaryModels()
        if (cachedItems.isNotEmpty()) {
            val metadata = releaseDao.getPageMetadata(pageNumber)
            val cacheFresh = metadata != null &&
                (clock() - metadata.fetchedAt) < NEW_RELEASES_ROOM_FRESH_MS
            emit(
                PageState.Content(
                    pageNumber = pageNumber,
                    items = cachedItems,
                    hasNextPage = metadata?.hasNextPage ?: true,
                    isStale = !cacheFresh,
                    isAppend = pageNumber > 1,
                ),
            )
            // Если кэш свежий — пропускаем сетевой запрос.
            if (cacheFresh) return@flow
        }

        // 2. Загружаем страницу и сразу отдаём её без постеров, чтобы лента
        //    отрисовалась, не дожидаясь обогащения всей страницы.
        val fetchedAt = clock()
        val hasAuthenticatedSession = hasAuthenticatedSession()
        val itemsToPersist: List<ReleaseSummary>
        val pageHasNext: Boolean
        try {
            val html = anonymousHttpClient.fetchNewPage(pageNumber)
            val parsedItems = withContext(Dispatchers.Default) {
                listParser.parse(
                    html = html,
                    pageNumber = pageNumber,
                    fetchedAt = fetchedAt,
                )
            }
            itemsToPersist = mergeWatchedState(
                pageNumber = pageNumber,
                html = html,
                parsedItems = parsedItems,
                hasAuthenticatedSession = hasAuthenticatedSession,
            )

            pageHasNext = hasNextPage(html, pageNumber, parsedItems.isNotEmpty())
            releaseDao.replacePage(
                pageNumber = pageNumber,
                summaries = itemsToPersist.toSummaryEntities(),
                metadata = PageCacheMetadataEntity(
                    pageNumber = pageNumber,
                    fetchedAt = fetchedAt,
                    itemCount = parsedItems.size,
                    hasNextPage = pageHasNext,
                ),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (exception is IOException || exception is IllegalStateException) {
                emit(fallbackPageState(pageNumber, exception))
            } else {
                throw exception
            }
            return@flow
        }

        var currentItems = releaseDao.getSummariesUpToPage(pageNumber).toSummaryModels()
        emit(
            PageState.Content(
                pageNumber = pageNumber,
                items = currentItems,
                hasNextPage = pageHasNext,
                isStale = false,
                isAppend = pageNumber > 1,
            ),
        )

        // 3. Постеры догружаются по одному; каждый emit заменяет item в списке.
        tmdbEnrichmentService.enrichProgressively(itemsToPersist).collect { enriched ->
            currentItems = currentItems.map { item ->
                if (item.detailsUrl == enriched.detailsUrl) enriched else item
            }
            emit(
                PageState.Content(
                    pageNumber = pageNumber,
                    items = currentItems,
                    hasNextPage = pageHasNext,
                    isStale = false,
                    isAppend = pageNumber > 1,
                ),
            )
        }
    }
```

- [ ] **Step 3: Убедиться, что старые тесты снова зелёные**

Run: `./gradlew :app:testDebugUnitTest --tests '*LostFilmRepositoryTest*'`

Expected: PASS

- [ ] **Step 4: Написать падающие тесты на прогрессивность**

В `app/src/test/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryTest.kt` добавить импорты `kotlinx.coroutines.CompletableDeferred`, `kotlinx.coroutines.CoroutineStart`, `kotlinx.coroutines.launch`.

Добавить четыре теста перед `private fun createRepository` (строка 2297):

```kotlin
    @Test
    fun observePage_emitsFreshPageBeforeEnrichmentCompletes() = runTest {
        val releaseGate = CompletableDeferred<Unit>()
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            tmdbResolver = GatedTmdbResolver(releaseGate),
        )

        val emissions = mutableListOf<PageState>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.observePage(1).toList(emissions)
        }

        // Свежая страница приходит до того, как резолвер что-то отдаст.
        val fresh = emissions.filterIsInstance<PageState.Content>().first { !it.isStale }
        assertTrue(fresh.items.isNotEmpty())
        assertTrue(fresh.items.all { it.posterUrl.isBlank() })

        releaseGate.complete(Unit)
        job.join()

        val enriched = emissions.filterIsInstance<PageState.Content>().last()
        assertTrue(enriched.items.any { it.posterUrl.isNotBlank() })
    }

    @Test
    fun observePage_persistsArtworkIntoRoomAsItResolves() = runTest {
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            tmdbResolver = PosterTmdbResolver(),
        )

        repository.observePage(1).toList()

        val stored = releaseDao.getSummariesUpToPage(1)
        assertTrue(stored.isNotEmpty())
        assertTrue("Арт должен быть записан в Room", stored.any { it.posterUrl.isNotBlank() })
    }

    @Test
    fun observePage_keepsWatchedFlagSetDuringEnrichment() = runTest {
        val releaseGate = CompletableDeferred<Unit>()
        val repository = createRepository(
            pageHandler = { fixture("new-page-1.html") },
            tmdbResolver = GatedTmdbResolver(releaseGate),
        )

        val emissions = mutableListOf<PageState>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.observePage(1).toList(emissions)
        }
        val fresh = emissions.filterIsInstance<PageState.Content>().first { !it.isStale }
        val targetUrl = fresh.items.first().detailsUrl

        releaseDao.updateSummaryWatched(targetUrl, true)
        releaseGate.complete(Unit)
        job.join()

        val stored = releaseDao.getSummary(targetUrl)
        requireNotNull(stored)
        assertTrue("Прогрессивное обогащение не должно сбрасывать isWatched", stored.isWatched)
    }

    @Test
    fun observePage_emitsOnlyStaleContent_whenNetworkFailsWithCache() = runTest {
        seedPage(pageNumber = 1, fetchedAt = NOW - 15 * 60 * 1000L)
        val repository = createRepository(
            pageHandler = { throw IOException("offline") },
        )

        val emissions = repository.observePage(1).toList()

        val contents = emissions.filterIsInstance<PageState.Content>()
        assertTrue(contents.isNotEmpty())
        assertTrue(contents.all { it.isStale })
    }
```

Добавить два фейка перед `private class FakeLostFilmHttpClient` (строка 2350):

```kotlin
private class PosterTmdbResolver : TmdbPosterResolver {
    override suspend fun resolve(
        detailsUrl: String,
        titleRu: String,
        releaseDateRu: String,
        kind: com.kraat.lostfilmnewtv.data.model.ReleaseKind,
        originalReleaseYear: Int?,
    ): TmdbImageUrls = TmdbImageUrls(
        posterUrl = "https://image.tmdb.org/t/p/w780/$detailsUrl.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/w1280/$detailsUrl.jpg",
    )
}

private class GatedTmdbResolver(
    private val gate: CompletableDeferred<Unit>,
) : TmdbPosterResolver {
    override suspend fun resolve(
        detailsUrl: String,
        titleRu: String,
        releaseDateRu: String,
        kind: com.kraat.lostfilmnewtv.data.model.ReleaseKind,
        originalReleaseYear: Int?,
    ): TmdbImageUrls {
        gate.await()
        return TmdbImageUrls(
            posterUrl = "https://image.tmdb.org/t/p/w780/$detailsUrl.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w1280/$detailsUrl.jpg",
        )
    }
}
```

- [ ] **Step 5: Убедиться, что новые тесты проходят**

Run: `./gradlew :app:testDebugUnitTest --tests '*LostFilmRepositoryTest*'`

Expected: PASS

Если `observePage_emitsFreshPageBeforeEnrichmentCompletes` падает на `NoSuchElementException` — свежая эмиссия не успела произойти до `gate.await()`. Тогда заменить тест на вариант с `backgroundScope.launch { repository.observePage(1).toList(emissions) }` и `runCurrent()` из `kotlinx.coroutines.test` перед `first { !it.isStale }`.

- [ ] **Step 6: Закоммитить**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepository.kt \
        app/src/main/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryImpl.kt \
        app/src/test/java/com/kraat/lostfilmnewtv/data/repository/LostFilmRepositoryTest.kt
git commit -m "feat: observePage отдаёт ленту до обогащения, постеры по одному"
```

---

## Task 4: `HomeViewModel` — collect и защита от повторного `syncNow()`

**Files:**
- Test: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModelTest.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModel.kt:12`, `:40`, `:404-432`, `:484-528`

- [ ] **Step 1: Переименовать метод в фейке репозитория и переехать на список page-номеров**

В `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModelTest.kt`:

Заменить в `FakeLostFilmRepository` (строки 1204-1236):

```kotlin
    /**
     * Кастомные flow для [observePage] (для тестов stale-while-revalidate).
     * Если для pageNumber нет записи, [observePage] возвращает однократный
     * вызов [loadPage] (как default-метод интерфейса).
     */
    val newReleasesFlows: Map<Int, Flow<PageState>> = emptyMap(),
    /**
     * Список эмиссий для [observePage] (для простых тестов с фиксированной
     * последовательностью cache → fresh). Удобнее [newReleasesFlows] когда не нужен
     * контроль тайминга между эмиссиями.
     */
    val newReleasesEmissions: Map<Int, List<PageState>> = emptyMap(),
) : LostFilmRepository, FavoritesRepository {
    val pageRequests = mutableListOf<Int>()
    val movieRequests = mutableListOf<Int>()
    val favoriteReleaseRequests = mutableListOf<Int>()
    var favoriteReleaseCalls = 0
    val observePageCalls = mutableListOf<Int>()

    override suspend fun loadPage(pageNumber: Int): PageState {
        pageRequests += pageNumber
        return checkNotNull(pageResults[pageNumber]) {
            "Missing fake result for page $pageNumber"
        }
    }

    override fun observePage(pageNumber: Int): Flow<PageState> {
        observePageCalls += pageNumber
        newReleasesFlows[pageNumber]?.let { return it }
        newReleasesEmissions[pageNumber]?.let { return it.asFlow() }
        // Fallback: однократный вызов loadPage, имитируя default-метод интерфейса.
        return flow { emit(loadPage(pageNumber)) }
    }
```

Обновить пять использований счётчика в тестах:
- строка 1063 → `assertEquals(listOf(1), repository.observePageCalls)`
- строка 1080 → `assertEquals(listOf(1), repository.observePageCalls)`
- строка 1111 → `assertEquals(listOf(1), repository.observePageCalls)`
- строка 1180 → `assertEquals(listOf(1), repository.observePageCalls)`
- строка 1187 → `assertEquals(listOf(1, 2), repository.observePageCalls)` (пагинация тоже идёт через `observePage`)

Переименовать тест `loadPage_paginationUsesDirectPath_notObserveNewReleases` (строка 1151) в `loadPage_paginationUsesObservePage`.

Заменить комментарий на строке 1105-1106: `// Без кастомного flow observePage fallback'ит на однократный loadPage — ведёт себя как до изменений: спиннер, потом данные.`

Заменить комментарий на строке 1183: `// Пагинация идёт через observePage, а не через отдельный loadPage.`

- [ ] **Step 2: Убрать мёртвый `tmdbEnrichmentService` из ViewModel и его фейка**

В `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModel.kt` удалить импорт `com.kraat.lostfilmnewtv.data.poster.TmdbEnrichmentService` (строка 12) и параметр конструктора `private val tmdbEnrichmentService: TmdbEnrichmentService,` (строка 40) — он не используется нигде в классе.

В `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModelTest.kt` удалить из `createViewModel` (строки 1376-1379) блок:

```kotlin
        tmdbEnrichmentService = object : TmdbEnrichmentService {
            override suspend fun enrichSummaries(items: List<ReleaseSummary>, persistToCache: Boolean) = items
            override suspend fun enrichSearchItems(items: List<LostFilmSearchItem>) = items
        },
```

и удалить ставшие неиспользуемыми импорты `com.kraat.lostfilmnewtv.data.poster.TmdbEnrichmentService` (строка 17) и `com.kraat.lostfilmnewtv.data.model.LostFilmSearchItem` (строка 18).

- [ ] **Step 3: Написать падающие тесты на multiple emission**

В `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModelTest.kt` добавить перед закрывающей скобкой класса (строка 1191) два теста:

```kotlin
    @Test
    fun progressivePosterEmissions_syncHomeChannelOnlyOnce() = runTest(dispatcher) {
        val placeholder = summary("https://www.lostfilm.today/series/a/season_1/episode_1/").copy(posterUrl = "")
        val withPoster = placeholder.copy(posterUrl = "https://image.tmdb.org/t/p/w780/a.jpg")
        val repository = FakeLostFilmRepository(
            newReleasesEmissions = mapOf(
                1 to listOf(
                    PageState.Content(pageNumber = 1, items = listOf(placeholder), hasNextPage = true, isStale = false),
                    PageState.Content(pageNumber = 1, items = listOf(placeholder), hasNextPage = true, isStale = false),
                    PageState.Content(pageNumber = 1, items = listOf(withPoster), hasNextPage = true, isStale = false),
                    PageState.Content(pageNumber = 1, items = listOf(withPoster), hasNextPage = true, isStale = false),
                ),
            ),
        )
        var channelSyncs = 0
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            onChannelContentChanged = { channelSyncs += 1 },
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()

        assertEquals(listOf(withPoster), viewModel.uiState.value.items)
        assertEquals("Прогрессивные эмиссии не должны заново синхронизировать канал", 1, channelSyncs)
    }

    @Test
    fun progressivePagingEmissions_appendWithoutDuplicates() = runTest(dispatcher) {
        val firstPageItem = summary("https://www.lostfilm.today/series/p1/season_1/episode_1/")
        val secondPageItem = summary("https://www.lostfilm.today/series/p2/season_1/episode_1/")
        val secondPageWithPoster = secondPageItem.copy(posterUrl = "https://image.tmdb.org/t/p/w780/p2.jpg")
        val repository = FakeLostFilmRepository(
            newReleasesEmissions = mapOf(
                1 to listOf(
                    PageState.Content(pageNumber = 1, items = listOf(firstPageItem), hasNextPage = true, isStale = false),
                ),
                2 to listOf(
                    PageState.Content(pageNumber = 2, items = listOf(firstPageItem), hasNextPage = true, isStale = true),
                    PageState.Content(
                        pageNumber = 2,
                        items = listOf(firstPageItem, secondPageItem),
                        hasNextPage = true,
                        isStale = false,
                        isAppend = true,
                    ),
                    PageState.Content(
                        pageNumber = 2,
                        items = listOf(firstPageItem, secondPageWithPoster),
                        hasNextPage = true,
                        isStale = false,
                        isAppend = true,
                    ),
                ),
            ),
        )
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()
        viewModel.onEndReached()
        advanceUntilIdle()

        val items = viewModel.uiState.value.items
        assertEquals(2, items.size)
        assertEquals(2, items.map { it.detailsUrl }.distinct().size)
        assertEquals("Прогрессивный постер второй страницы должен заменить плейсхолдер", "https://image.tmdb.org/t/p/w780/p2.jpg", items.last().posterUrl)
        assertFalse(viewModel.uiState.value.isPaging)
    }
```

- [ ] **Step 4: Убедиться, что тесты падают**

Run: `./gradlew :app:testDebugUnitTest --tests '*HomeViewModelTest*'`

Expected:
- `progressivePosterEmissions_syncHomeChannelOnlyOnce` FAIL — `channelSyncs` = 4 вместо 1
- `progressivePagingEmissions_appendWithoutDuplicates` FAIL — в `items.last()` остаётся плейсхолдер, потому что `distinctBy` берёт версию из `existingItems`

- [ ] **Step 5: Переключить `observeNewReleases` на `observePage` с защитой от повторного sync**

В `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModel.kt` в `observeNewReleases()` заменить строки 404-432 на:

```kotlin
        allNewLoadJob = viewModelScope.launch(ioDispatcher) {
            var hadCacheEmission = false
            var didSyncChannel = false
            try {
                repository.observePage(1).collect { result ->
                    when (result) {
                        is PageState.Content -> {
                            val isStale = result.isStale
                            _uiState.update { state ->
                                state.copy(
                                    isInitialLoading = false,
                                    fullScreenErrorMessage = null,
                                ).updateMode(HomeFeedMode.AllNew) { md ->
                                    md.copy(
                                        items = result.items,
                                        isPaging = false,
                                        pagingErrorMessage = result.pagingErrorMessage,
                                        nextPage = result.pageNumber + 1,
                                        hasNextPage = result.hasNextPage,
                                        contentState = HomeModeContentState.Content(result.items),
                                    )
                                }.resolveSelection()
                            }
                            when {
                                isStale -> hadCacheEmission = true
                                // Прогрессивные эмиссии постеров не должны заново
                                // синхронизировать домашний канал.
                                !didSyncChannel -> {
                                    didSyncChannel = true
                                    lastAllNewRefreshAt = clock()
                                    homeChannelSyncManager.syncNow()
                                }
                            }
                        }
                        is PageState.Error -> {
```

Блок `is PageState.Error` (строки 433-459) и `catch` оставить без изменений.

- [ ] **Step 6: Перевести пагинацию на `observePage`**

В том же файле `loadPageDirect` заменить строки 484-528 на:

```kotlin
        allNewLoadJob = viewModelScope.launch(ioDispatcher) {
            var appliedEmission = false
            try {
                repository.observePage(pageNumber).collect { result ->
                    when (result) {
                        is PageState.Content -> {
                            // Кэш-эмиссия при пагинации — не новые данные: спиннер
                            // должен остаться, пока не придёт свежая страница.
                            if (result.isStale) return@collect
                            appliedEmission = true
                            val existingItems = _uiState.value.items
                            val updatedItems = if (result.isAppend) {
                                // result.items идёт первым: он содержит и страницу из
                                // Room, и свежий постер, поэтому distinctBy оставит
                                // именно его, а не устаревшую копию из existingItems.
                                (result.items + existingItems).distinctBy { it.detailsUrl }
                            } else {
                                result.items
                            }
                            _uiState.update { state ->
                                state.copy(
                                    isInitialLoading = false,
                                    fullScreenErrorMessage = null,
                                ).updateMode(HomeFeedMode.AllNew) { md ->
                                    md.copy(
                                        items = updatedItems,
                                        isPaging = false,
                                        pagingErrorMessage = result.pagingErrorMessage,
                                        nextPage = result.pageNumber + 1,
                                        hasNextPage = result.hasNextPage,
                                        contentState = HomeModeContentState.Content(updatedItems),
                                    )
                                }.resolveSelection()
                            }
                            if (!isPagingRequest) {
                                homeChannelSyncManager.syncNow()
                            }
                        }
                        is PageState.Error -> {
                            appliedEmission = true
                            _uiState.update { state ->
                                state.copy(
                                    isInitialLoading = false,
                                    fullScreenErrorMessage = result.message,
                                ).updateMode(HomeFeedMode.AllNew) { md ->
                                    md.copy(
                                        isPaging = false,
                                        pagingErrorMessage = null,
                                        contentState = HomeModeContentState.Error(result.message),
                                    )
                                }.resolveSelection()
                            }
                        }
                    }
                }
            } finally {
                // Оффлайн с кэшем: stale-эмиссия пропущена, но flow завершился —
                // гасим спиннер, иначе он крутится вечно.
                if (!appliedEmission) {
                    _uiState.update { state ->
                        state.updateMode(HomeFeedMode.AllNew) { md -> md.copy(isPaging = false) }
                    }
                }
            }
        }
```

- [ ] **Step 7: Убедиться, что тесты проходят**

Run: `./gradlew :app:testDebugUnitTest --tests '*HomeViewModelTest*'`

Expected: PASS

- [ ] **Step 8: Прогнать весь набор unit-тестов**

Run: `./gradlew :app:testDebugUnitTest`

Expected: PASS

Путь пагинации меняется, эти тесты стоит проверить первыми, если что-то упало:
- `onPagingRetry_loadsNextPageWhenSoftPagingErrorPresent` (строка 441) — `pageRequests` должен остаться `listOf(2)`, фейковый `observePage` вызывает `loadPage`
- `loadPage_paginationUsesObservePage` (строка 1151, переименован в Task 4) — `observePageCalls` теперь `listOf(1, 2)`
- `onStart_withCache_emitsStaleFirstThenFresh` (строка 1023) и `onStart_whenNetworkFailsAfterCache_keepsCachedItems` (строка 1115) — счётчик переименован в `observePageCalls`

- [ ] **Step 9: Закоммитить**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModel.kt \
        app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeViewModelTest.kt
git commit -m "feat: HomeViewModel собирает observePage, sync канала один раз"
```

---

## Task 5: рефакторинг `TmdbPosterResolverImpl.resolve()`

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/data/poster/TmdbPosterResolver.kt:77-198`

Чистая переработка: блоки `:87-138` (fast path) и `:141-197` (тот же cache-hit под локом) — почти дословные копии. Поведение не меняется, правок тестов не требуется.

- [ ] **Step 1: Зафиксировать зелёный старт**

Run: `./gradlew :app:testDebugUnitTest --tests '*TmdbPosterResolverTest*'`

Expected: PASS. Если падает — причина вне этой задачи, разобраться до рефакторинга.

- [ ] **Step 2: Вынести общий lookup**

В `app/src/main/java/com/kraat/lostfilmnewtv/data/poster/TmdbPosterResolver.kt` добавить перед `private suspend fun <T> withKeyLock` (строка 200) два блока:

```kotlin
    /**
     * Общий cache-hit путь для [resolve]: LRU в памяти, negative-кэш и Room.
     * null означает «в кеше ничего нет, иди в сеть».
     */
    private suspend fun lookupCached(
        cacheKey: String,
        detailsUrl: String,
        kind: ReleaseKind,
        originalReleaseYear: Int?,
        hasTmdbIdOverride: Boolean,
    ): CachedMapping? {
        inMemoryCache[cacheKey]?.let { cached ->
            val cachedTmdbId = inMemoryTmdbIdCache[cacheKey]
            if (cached.seriesOverviewRu != null || cached.movieOverviewRu != null) {
                val episodeOverview = if (cached.episodeOverviewRu == null && cachedTmdbId != null) {
                    resolveEpisodeOverview(detailsUrl, cachedTmdbId, kind)
                } else null
                return CachedMapping(
                    cached.copy(
                        episodeOverviewRu = episodeOverview?.text,
                        episodeOverviewSource = episodeOverview?.source?.name,
                    ),
                )
            }
            val overviews = resolveOverviews(
                detailsUrl = detailsUrl,
                tmdbId = cachedTmdbId,
                kind = kind,
            )
            return CachedMapping(
                cached.copy(
                    episodeOverviewRu = overviews.episodeOverview?.text,
                    episodeOverviewSource = overviews.episodeOverview?.source?.name,
                    seriesOverviewRu = overviews.seriesOverviewRu,
                    movieOverviewRu = overviews.movieOverviewRu,
                    rating = cached.rating,
                ),
            )
        }
        if (!hasTmdbIdOverride && negativeMemoryCache[cacheKey] != null) {
            return CachedMapping(null)
        }

        val cached = tmdbDao.getByDetailsUrl(cacheKey) ?: return null
        if (canReuseNegativeMapping(cached) && !hasTmdbIdOverride) {
            negativeMemoryCache[cacheKey] = Unit
            return CachedMapping(null)
        }
        if (!canReuseCachedMapping(cached, originalReleaseYear)) {
            return null
        }

        val overviews = resolveOverviews(
            detailsUrl = detailsUrl,
            tmdbId = cached.tmdbId,
            kind = kind,
        )
        val urls = TmdbImageUrls(
            posterUrl = cached.posterUrl,
            backdropUrl = cached.backdropUrl,
            episodeOverviewRu = overviews.episodeOverview?.text,
            episodeOverviewSource = overviews.episodeOverview?.source?.name,
            seriesOverviewRu = overviews.seriesOverviewRu,
            movieOverviewRu = overviews.movieOverviewRu,
            rating = cached.rating,
        )
        inMemoryCache[cacheKey] = urls.copy(episodeOverviewRu = null, episodeOverviewSource = null)
        inMemoryTmdbIdCache[cacheKey] = cached.tmdbId
        return CachedMapping(urls)
    }

    private data class CachedMapping(
        /**
         * null — точный промах, закешированный как negative. Репозиторий
         * трактует его так же, как отсутствие результата.
         */
        val urls: TmdbImageUrls?,
    )
```

- [ ] **Step 3: Переписать `resolve()` через `lookupCached`**

В том же файле заменить тело `resolve` (строки 83-198) на:

```kotlin
    override suspend fun resolve(
        detailsUrl: String,
        titleRu: String,
        releaseDateRu: String,
        kind: ReleaseKind,
        originalReleaseYear: Int?,
    ): TmdbImageUrls? {
        val cacheKey = tmdbCacheKey(detailsUrl, kind)
        val hasTmdbIdOverride = tmdbIdOverride(extractEnglishSlug(detailsUrl), kind) != null

        lookupCached(
            cacheKey = cacheKey,
            detailsUrl = detailsUrl,
            kind = kind,
            originalReleaseYear = originalReleaseYear,
            hasTmdbIdOverride = hasTmdbIdOverride,
        )?.let { return it.urls }

        return withKeyLock(cacheKey) {
            // Под локом кеш могли заполнить параллельные вызовы.
            lookupCached(
                cacheKey = cacheKey,
                detailsUrl = detailsUrl,
                kind = kind,
                originalReleaseYear = originalReleaseYear,
                hasTmdbIdOverride = hasTmdbIdOverride,
            )?.let { return@withKeyLock it.urls }

            val result = performSearch(cacheKey, detailsUrl, titleRu, kind, originalReleaseYear)
            result?.let {
                inMemoryCache[cacheKey] = it.copy(episodeOverviewRu = null, episodeOverviewSource = null)
            }
            result
        }
    }
```

- [ ] **Step 4: Убедиться, что поведение не изменилось**

Run: `./gradlew :app:testDebugUnitTest --tests '*TmdbPosterResolverTest*' --tests '*TmdbEnrichmentServiceTest*' --tests '*LostFilmRepositoryTest*'`

Expected: PASS, тесты не правились

- [ ] **Step 5: Закоммитить**

```bash
git add app/src/main/java/com/kraat/lostfilmnewtv/data/poster/TmdbPosterResolver.kt
git commit -m "refactor: убрать дублирование cache-hit в TmdbPosterResolver.resolve"
```

---

## Task 6: финальная проверка

- [ ] **Step 1: Полный набор проверок CI**

Run: `./gradlew testDebugUnitTest lint assembleDebug`

Expected: BUILD SUCCESSFUL, все тесты зелёные, lint без errors

- [ ] **Step 2: Ручная проверка на экране Home**

Run: `./scripts/run-emulator.sh`, установить `app/build/outputs/apk/debug/app-debug.apk`, затем `adb shell pm clear com.kraat.lostfilmnewtv` для чистых данных.

Проверить:
1. Лента «Все новое» появляется почти сразу, постеры проявляются по одному вместо общего скелетона
2. Фокус не убегает при появлении постеров
3. «Загрузить ещё» подгружает страницу 2 с прогрессивными постерами, без дублей
4. Отметка «просмотрено» не исчезает после догрузки постеров
5. Настройки → «Обновить первую страницу» работает
6. Оффлайн с кэшем: лента из кэша показывается, спиннер пагинации не зависает

- [ ] **Step 3: Зафиксировать отклонение в спеке**

В `docs/superpowers/specs/2026-09-26-progressive-poster-loading-design.md` в разделе «Дизайн» удалить пункт 4 (`@ApplicationScope`) и из границ «В объёме» убрать упоминание фоновой скоупы; в пункте 3 `loadPage` отметить, что он остаётся блокирующим.

```bash
git add docs/superpowers/specs/2026-09-26-progressive-poster-loading-design.md
git commit -m "docs: отметить отклонение по ApplicationScope в спеке"
```

---

## Вне объёма

- ленты «Фильмы» и «Каталог сериалов» остаются блокирующими (`persistToCache = false`, Room не является источником истины)
- `FavoritesRepository` без изменений
- лимиты бэкенда, `Retry-After`-backoff, `matcherVersion` вместо magic-timestamps — следующие итерации
