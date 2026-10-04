package com.kraat.lostfilmnewtv.data.poster

import com.kraat.lostfilmnewtv.data.db.TmdbPosterDao
import com.kraat.lostfilmnewtv.data.db.TmdbPosterMappingEntity
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.TmdbEpisodeOverview
import com.kraat.lostfilmnewtv.data.model.TmdbEpisodeOverviewSource
import com.kraat.lostfilmnewtv.data.model.TmdbImageUrls
import com.kraat.lostfilmnewtv.data.model.TmdbMediaType
import com.kraat.lostfilmnewtv.data.model.TmdbSearchResult
import com.kraat.lostfilmnewtv.data.network.TmdbPosterClient
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TmdbPosterResolverTest {
    @Test
    fun resolve_trimsTrailingSpaceInSlug_beforeSearching() = runTest {
        // lostfilm отдаёт часть URL с лишним пробелом в slug:
        // «/series/Dark_Matter_2024 /season_2/». Год в конце строки из-за
        // пробела не отбрасывался, поиск уходил по «Dark Matter 2024»,
        // промахивался, и сериал уезжал в Кинопоиск без описаний эпизодов.
        var slugQuery: String? = null
        val requestedIds = mutableListOf<Int>()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(
                query: String,
                year: Int?,
                type: TmdbMediaType,
                page: Int,
                language: String?,
            ): List<TmdbSearchResult> {
                if (query.all { it.code < 128 }) {
                    slugQuery = query
                    return listOf(
                        TmdbSearchResult(
                            id = 196_322,
                            name = "Dark Matter",
                            originalName = "Dark Matter",
                            popularity = 10.0,
                            releaseYear = 2024,
                        ),
                    )
                }
                return emptyList()
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls {
                requestedIds += tmdbId
                return TmdbImageUrls(posterUrl = "p", backdropUrl = "b")
            }

            override suspend fun getEpisodeOverview(
                tmdbId: Int,
                seasonNumber: Int,
                episodeNumber: Int,
            ): TmdbEpisodeOverview = TmdbEpisodeOverview(
                text = "Спокойная жизнь.",
                source = TmdbEpisodeOverviewSource.TMDB_RU,
            )
        }
        val resolver = TmdbPosterResolverImpl(client, FakeTmdbPosterDao())

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/Dark_Matter_2024 /season_2/episode_1/",
            titleRu = "Тёмная материя",
            releaseDateRu = "01.01.2026",
            kind = ReleaseKind.SERIES,
        )

        assertEquals("Год в конце slug'а должен отбрасываться, а пробел — не мешать", "Dark Matter", slugQuery)
        assertEquals(listOf(196_322), requestedIds)
        assertEquals("Спокойная жизнь.", result?.episodeOverviewRu)
    }

    @Test
    fun resolve_retriesEpisodeOverview_afterEarlierRequestFailed() = runTest {
        // Отрицательный кэш на 24 часа глушил описание после одной неудачи, а
        // вместе с фильтром «готовности» это означало, что описание не
        // появлялось никогда. Повтор должен опрашивать TMDB заново.
        var attempts = 0
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(
                query: String,
                year: Int?,
                type: TmdbMediaType,
                page: Int,
                language: String?,
            ): List<TmdbSearchResult> = listOf(
                TmdbSearchResult(id = 777, name = "Example Show", popularity = 10.0, releaseYear = 2026),
            )

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType) =
                TmdbImageUrls(posterUrl = "p", backdropUrl = "b")

            override suspend fun getEpisodeOverview(
                tmdbId: Int,
                seasonNumber: Int,
                episodeNumber: Int,
            ): TmdbEpisodeOverview? {
                attempts++
                return if (attempts == 1) {
                    null
                } else {
                    TmdbEpisodeOverview(
                        text = "Описание серии из TMDB.",
                        source = TmdbEpisodeOverviewSource.TMDB_RU,
                    )
                }
            }
        }
        val resolver = TmdbPosterResolverImpl(client, FakeTmdbPosterDao())
        val url = "https://www.lostfilm.today/series/Example_Show/season_2/episode_8/"

        assertNull(resolver.resolve(detailsUrl = url, titleRu = "Пример", releaseDateRu = "14.03.2026", kind = ReleaseKind.SERIES)?.episodeOverviewRu)
        val second = resolver.resolve(detailsUrl = url, titleRu = "Пример", releaseDateRu = "14.03.2026", kind = ReleaseKind.SERIES)

        assertEquals(2, attempts)
        assertEquals("Описание серии из TMDB.", second?.episodeOverviewRu)
    }

    @Test
    fun resolve_fetchesRussianEpisodeOverview_forSeriesEpisode() = runTest {
        val dao = FakeTmdbPosterDao()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                return listOf(TmdbSearchResult(id = 777, name = "Example Show", popularity = 10.0, rating = "8.4"))
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls {
                return TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/poster.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/original/backdrop.jpg",
                )
            }

            override suspend fun getEpisodeOverview(
                tmdbId: Int,
                seasonNumber: Int,
                episodeNumber: Int,
            ): TmdbEpisodeOverview? {
                assertEquals(777, tmdbId)
                assertEquals(2, seasonNumber)
                assertEquals(8, episodeNumber)
                return TmdbEpisodeOverview(
                    text = "Русское описание серии из TMDB.",
                    source = TmdbEpisodeOverviewSource.TMDB_RU,
                )
            }

            override suspend fun getSeriesOverviewRu(tmdbId: Int): String? {
                assertEquals(777, tmdbId)
                return "Русское описание сериала из TMDB."
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/Example_Show/season_2/episode_8/",
            titleRu = "Пример шоу",
            releaseDateRu = "05.05.2026",
            kind = ReleaseKind.SERIES,
        )

        assertEquals("Русское описание серии из TMDB.", result?.episodeOverviewRu)
        assertEquals(TmdbEpisodeOverviewSource.TMDB_RU.name, result?.episodeOverviewSource)
        assertEquals("Русское описание сериала из TMDB.", result?.seriesOverviewRu)
        assertEquals("8.4", result?.rating)
        assertEquals("8.4", dao.upserted?.rating)
    }

    @Test
    fun resolve_fetchesMovieOverview_forMovieOnly() = runTest {
        val dao = FakeTmdbPosterDao()
        var seriesOverviewCalls = 0
        var movieOverviewCalls = 0
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                return listOf(TmdbSearchResult(id = 524, name = "Casino", popularity = 10.0))
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls {
                return TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/poster.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/original/backdrop.jpg",
                )
            }

            override suspend fun getSeriesOverviewRu(tmdbId: Int): String? {
                seriesOverviewCalls += 1
                return "Описание сериала не должно запрашиваться."
            }

            override suspend fun getMovieOverviewRu(tmdbId: Int): String? {
                assertEquals(524, tmdbId)
                movieOverviewCalls += 1
                return "Русское описание фильма из TMDB."
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/movies/Casino",
            titleRu = "Казино",
            releaseDateRu = "05.04.2026",
            kind = ReleaseKind.MOVIE,
            originalReleaseYear = 1995,
        )

        assertNotNull(result)
        assertNull(result?.seriesOverviewRu)
        assertEquals("Русское описание фильма из TMDB.", result?.movieOverviewRu)
        assertEquals(0, seriesOverviewCalls)
        assertEquals(1, movieOverviewCalls)
    }

    @Test
    fun resolve_returnsRating_whenTmdbImagesAreMissing() = runTest {
        val dao = FakeTmdbPosterDao()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                return listOf(TmdbSearchResult(id = 42, name = "Catalog Movie", popularity = 10.0, rating = "7.6"))
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls? {
                return null
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/movies/Catalog_Movie",
            titleRu = "Фильм каталога",
            releaseDateRu = "2026",
            kind = ReleaseKind.MOVIE,
            originalReleaseYear = 2026,
        )

        assertNotNull(result)
        assertEquals("7.6", result?.rating)
        assertEquals("", result?.posterUrl)
        assertEquals("", result?.backdropUrl)
        assertEquals("7.6", dao.upserted?.rating)
    }

    @Test
    fun resolve_matchesAmpersandTitleFromAndSlug() = runTest {
        val dao = FakeTmdbPosterDao()
        var searchCalls = 0
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                searchCalls += 1
                assertEquals("Example and Show", query)
                return listOf(TmdbSearchResult(id = 259731, name = "Example & Show", popularity = 10.0, rating = "6.9"))
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls {
                assertEquals(259731, tmdbId)
                return TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/his-hers.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/original/his-hers-backdrop.jpg",
                )
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/Example_and_Show/",
            titleRu = "Пример",
            releaseDateRu = "2025",
            kind = ReleaseKind.SERIES,
        )

        assertEquals("https://image.tmdb.org/t/p/w780/his-hers.jpg", result?.posterUrl)
        assertEquals("6.9", result?.rating)
        assertEquals(1, searchCalls)
    }

    @Test
    fun resolve_usesKnownTmdbIdOverrideForHisAndHers_evenWhenNegativeCached() = runTest {
        val dao = FakeTmdbPosterDao(
            cached = TmdbPosterMappingEntity.negative(
                detailsUrl = "https://www.lostfilm.today/series/His_and_Hers/",
                tmdbType = TmdbMediaType.TV.name,
                fetchedAt = System.currentTimeMillis(),
            ),
        )
        var searchCalls = 0
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                searchCalls += 1
                return emptyList()
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls {
                assertEquals(259731, tmdbId)
                return TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/his-hers.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/original/his-hers-backdrop.jpg",
                )
            }

            override suspend fun getSeriesOverviewRu(tmdbId: Int): String? {
                assertEquals(259731, tmdbId)
                return "Описание His & Hers из TMDB."
            }

            override suspend fun getRating(tmdbId: Int, type: TmdbMediaType): String? {
                assertEquals(259731, tmdbId)
                return "6.9"
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/His_and_Hers/",
            titleRu = "Его и её",
            releaseDateRu = "2025",
            kind = ReleaseKind.SERIES,
        )

        assertEquals("https://image.tmdb.org/t/p/w780/his-hers.jpg", result?.posterUrl)
        assertEquals("Описание His & Hers из TMDB.", result?.seriesOverviewRu)
        assertEquals("6.9", result?.rating)
        assertEquals(0, searchCalls)
        assertEquals(259731, dao.upserted?.tmdbId)
    }

    @Test
    fun resolve_refetchesCachedMapping_whenBackdropMissing() = runTest {
        val dao = FakeTmdbPosterDao(
            cached = TmdbPosterMappingEntity.create(
                detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_16/",
                tmdbId = 1,
                tmdbType = TmdbMediaType.TV.name,
                posterUrl = "https://image.tmdb.org/t/p/w780/old-poster.jpg",
                backdropUrl = "",
                fetchedAt = System.currentTimeMillis(),
            ),
        )
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                return listOf(TmdbSearchResult(id = 99, name = "9-1-1", popularity = 10.0))
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls {
                return TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/new-poster.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/original/new-backdrop.jpg",
                )
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_16/",
            titleRu = "9-1-1",
            releaseDateRu = "05.04.2026",
            kind = ReleaseKind.SERIES,
        )

        assertNotNull(result)
        assertEquals("https://image.tmdb.org/t/p/original/new-backdrop.jpg", result?.backdropUrl)
        assertEquals("https://image.tmdb.org/t/p/original/new-backdrop.jpg", dao.upserted?.backdropUrl)
    }

    @Test
    fun resolve_refetchesCachedMapping_whenPosterMissing() = runTest {
        val dao = FakeTmdbPosterDao(
            cached = TmdbPosterMappingEntity.create(
                detailsUrl = "https://www.lostfilm.today/series/The_Testaments/season_1/episode_1/",
                tmdbId = 287527,
                tmdbType = TmdbMediaType.TV.name,
                posterUrl = "",
                backdropUrl = "https://image.tmdb.org/t/p/original/old-backdrop.jpg",
                fetchedAt = System.currentTimeMillis(),
            ),
        )
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                return listOf(TmdbSearchResult(id = 287527, name = "The Testaments", popularity = 10.0))
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls {
                return TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/new-poster.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/original/new-backdrop.jpg",
                )
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/The_Testaments/season_1/episode_1/",
            titleRu = "Заветы",
            releaseDateRu = "11.04.2026",
            kind = ReleaseKind.SERIES,
        )

        assertNotNull(result)
        assertEquals("https://image.tmdb.org/t/p/w780/new-poster.jpg", result?.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w780/new-poster.jpg", dao.upserted?.posterUrl)
    }

    @Test
    fun resolve_prefersExactEnglishSlugMatch_forGenericRussianTitle() = runTest {
        val dao = FakeTmdbPosterDao()
        val searchQueries = mutableListOf<String>()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                searchQueries += query
                return when (query) {
                    "Paradise" -> listOf(
                        TmdbSearchResult(
                            id = 117465,
                            name = "Hell's Paradise",
                            originalName = "Jigokuraku",
                            popularity = 100.0,
                        ),
                        TmdbSearchResult(
                            id = 245927,
                            name = "Paradise",
                            originalName = "Paradise",
                            popularity = 50.0,
                        ),
                    )

                    "Рай" -> listOf(
                        TmdbSearchResult(
                            id = 117465,
                            name = "Адский рай",
                            originalName = "Hell's Paradise",
                            popularity = 100.0,
                        ),
                        TmdbSearchResult(
                            id = 245927,
                            name = "Рай",
                            originalName = "Paradise",
                            popularity = 50.0,
                        ),
                    )

                    else -> emptyList()
                }
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls {
                return when (tmdbId) {
                    245927 -> TmdbImageUrls(
                        posterUrl = "https://image.tmdb.org/t/p/w780/correct-paradise-poster.jpg",
                        backdropUrl = "https://image.tmdb.org/t/p/original/correct-paradise-backdrop.jpg",
                    )

                    else -> TmdbImageUrls(
                        posterUrl = "https://image.tmdb.org/t/p/w780/wrong-poster.jpg",
                        backdropUrl = "https://image.tmdb.org/t/p/original/wrong-backdrop.jpg",
                    )
                }
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/Paradise/season_2/episode_8/",
            titleRu = "Рай",
            releaseDateRu = "05.04.2026",
            kind = ReleaseKind.SERIES,
        )

        assertNotNull(result)
        assertEquals("https://image.tmdb.org/t/p/w780/correct-paradise-poster.jpg", result?.posterUrl)
        assertEquals(245927, dao.upserted?.tmdbId)
        assertEquals(listOf("Paradise"), searchQueries)
    }

    @Test
    fun resolve_prefersExactSlugMatchWithOriginalReleaseYear_forAmbiguousMovieTitle() = runTest {
        val dao = FakeTmdbPosterDao()
        val searchRequests = mutableListOf<Pair<String, Int?>>()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                searchRequests += query to year
                return listOf(
                    TmdbSearchResult(
                        id = 2,
                        name = "Casino",
                        originalName = "Casino",
                        popularity = 100.0,
                        releaseYear = 2025,
                    ),
                    TmdbSearchResult(
                        id = 524,
                        name = "Casino",
                        originalName = "Casino",
                        popularity = 80.0,
                        releaseYear = 1995,
                    ),
                )
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls {
                return when (tmdbId) {
                    524 -> TmdbImageUrls(
                        posterUrl = "https://image.tmdb.org/t/p/w780/casino-1995-poster.jpg",
                        backdropUrl = "https://image.tmdb.org/t/p/original/casino-1995-backdrop.jpg",
                    )

                    else -> TmdbImageUrls(
                        posterUrl = "https://image.tmdb.org/t/p/w780/wrong-casino-poster.jpg",
                        backdropUrl = "https://image.tmdb.org/t/p/original/wrong-casino-backdrop.jpg",
                    )
                }
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/movies/Casino",
            titleRu = "Казино",
            releaseDateRu = "05.04.2026",
            kind = ReleaseKind.MOVIE,
            originalReleaseYear = 1995,
        )

        assertNotNull(result)
        assertEquals("https://image.tmdb.org/t/p/w780/casino-1995-poster.jpg", result?.posterUrl)
        assertEquals(524, dao.upserted?.tmdbId)
        assertEquals(listOf("Casino" to 1995), searchRequests)
    }

    @Test
    fun resolve_reusesFreshCompleteCachedMapping_withoutNetworkValidation() = runTest {
        val dao = FakeTmdbPosterDao(
            cached = TmdbPosterMappingEntity.create(
                detailsUrl = "https://www.lostfilm.today/series/Paradise/season_2/episode_8/",
                tmdbId = 117465,
                tmdbType = TmdbMediaType.TV.name,
                posterUrl = "https://image.tmdb.org/t/p/w780/wrong-poster.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/wrong-backdrop.jpg",
                fetchedAt = System.currentTimeMillis(),
            ),
        )
        var searchCalls = 0
        var imageCalls = 0
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                searchCalls += 1
                return emptyList()
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls {
                imageCalls += 1
                return TmdbImageUrls("", "")
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/Paradise/season_2/episode_8/",
            titleRu = "Рай",
            releaseDateRu = "05.04.2026",
            kind = ReleaseKind.SERIES,
        )

        assertNotNull(result)
        assertEquals("https://image.tmdb.org/t/p/w780/wrong-poster.jpg", result?.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/original/wrong-backdrop.jpg", result?.backdropUrl)
        assertEquals(0, searchCalls)
        assertEquals(0, imageCalls)
        assertNull(dao.upserted)
    }

    @Test
    fun resolve_refetchesOlderCachedMapping_whenOriginalReleaseYearIsAvailable() = runTest {
        val matchingUpdateTime = 1_777_852_800_000L
        val dao = FakeTmdbPosterDao(
            cached = TmdbPosterMappingEntity.create(
                detailsUrl = "https://www.lostfilm.today/movies/Casino",
                tmdbId = 2,
                tmdbType = TmdbMediaType.MOVIE.name,
                posterUrl = "https://image.tmdb.org/t/p/w780/wrong-casino-poster.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/wrong-casino-backdrop.jpg",
                fetchedAt = matchingUpdateTime - 1,
            ),
        )
        var searchCalls = 0
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                searchCalls += 1
                return listOf(
                    TmdbSearchResult(
                        id = 524,
                        name = "Casino",
                        originalName = "Casino",
                        popularity = 80.0,
                        releaseYear = 1995,
                    ),
                )
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls {
                return TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/casino-1995-poster.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/original/casino-1995-backdrop.jpg",
                )
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao, clock = { matchingUpdateTime })

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/movies/Casino",
            titleRu = "Казино",
            releaseDateRu = "05.04.2026",
            kind = ReleaseKind.MOVIE,
            originalReleaseYear = 1995,
        )

        assertEquals("https://image.tmdb.org/t/p/w780/casino-1995-poster.jpg", result?.posterUrl)
        assertEquals(1, searchCalls)
        assertEquals(524, dao.upserted?.tmdbId)
    }

    @Test
    fun resolve_persistsNegativeMapping_whenSearchHasNoMatches() = runTest {
        val dao = FakeTmdbPosterDao()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                return emptyList()
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls? {
                error("Images should not be fetched without a TMDB match")
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/Unknown_Title/season_1/episode_1/",
            titleRu = "Неизвестный сериал",
            releaseDateRu = "05.04.2026",
            kind = ReleaseKind.SERIES,
        )

        assertNull(result)
        assertEquals(true, dao.upserted?.isNegative)
        assertEquals("", dao.upserted?.posterUrl)
    }

    @Test
    fun resolve_reusesFreshNegativeMapping_withoutNetworkRequest() = runTest {
        val now = System.currentTimeMillis()
        val dao = FakeTmdbPosterDao(
            cached = TmdbPosterMappingEntity.negative(
                detailsUrl = "https://www.lostfilm.today/series/Unknown_Title/season_1/episode_1/",
                tmdbType = TmdbMediaType.TV.name,
                fetchedAt = now,
            ),
        )
        var searchCalls = 0
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                searchCalls += 1
                return emptyList()
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao, clock = { now })

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/Unknown_Title/season_1/episode_1/",
            titleRu = "Неизвестный сериал",
            releaseDateRu = "05.04.2026",
            kind = ReleaseKind.SERIES,
        )

        assertNull(result)
        assertEquals(0, searchCalls)
        assertNull(dao.upserted)
    }

    @Test
    fun resolve_refetchesNegativeMappingCreatedBeforeYearAwareMatching() = runTest {
        val matchingUpdateTime = 1_777_867_930_731L
        val dao = FakeTmdbPosterDao(
            cached = TmdbPosterMappingEntity.negative(
                detailsUrl = "https://www.lostfilm.today/movies/Peaky_Blinders_The_Immortal_Man",
                tmdbType = TmdbMediaType.MOVIE.name,
                fetchedAt = matchingUpdateTime - 1,
            ),
        )
        var searchCalls = 0
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                searchCalls += 1
                return listOf(
                    TmdbSearchResult(
                        id = 999,
                        name = "Peaky Blinders: The Immortal Man",
                        originalName = "Peaky Blinders: The Immortal Man",
                        popularity = 100.0,
                        releaseYear = 2026,
                    ),
                )
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls {
                return TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/peaky-poster.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/original/peaky-backdrop.jpg",
                )
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao, clock = { matchingUpdateTime })

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/movies/Peaky_Blinders_The_Immortal_Man",
            titleRu = "Острые козырьки: Бессмертный человек",
            releaseDateRu = "24 марта 2026",
            kind = ReleaseKind.MOVIE,
        )

        assertEquals("https://image.tmdb.org/t/p/w780/peaky-poster.jpg", result?.posterUrl)
        assertEquals(1, searchCalls)
        assertEquals(999, dao.upserted?.tmdbId)
    }

    @Test
    fun resolve_doesNotUseEpisodeReleaseYearAsTvFirstAirDateYear() = runTest {
        val dao = FakeTmdbPosterDao()
        val searchRequests = mutableListOf<Pair<String, Int?>>()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                searchRequests += query to year
                return listOf(
                    TmdbSearchResult(
                        id = 75219,
                        name = "9-1-1",
                        originalName = "9-1-1",
                        popularity = 100.0,
                        releaseYear = 2018,
                    ),
                )
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls {
                return TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/911-poster.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/original/911-backdrop.jpg",
                )
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_17/",
            titleRu = "9-1-1",
            releaseDateRu = "02.05.2026",
            kind = ReleaseKind.SERIES,
            originalReleaseYear = 2026,
        )

        assertEquals("https://image.tmdb.org/t/p/w780/911-poster.jpg", result?.posterUrl)
        assertEquals(listOf("9-1-1" to null), searchRequests)
        assertEquals(75219, dao.upserted?.tmdbId)
        assertEquals("https://www.lostfilm.today/series/9-1-1/season_9/", dao.upserted?.detailsUrl)
    }

    @Test
    fun resolve_reusesSeriesRootMappingAcrossEpisodes() = runTest {
        val dao = FakeTmdbPosterDao()
        var searchCalls = 0
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                searchCalls += 1
                return listOf(
                    TmdbSearchResult(
                        id = 202411,
                        name = "Monarch: Legacy of Monsters",
                        originalName = "Monarch: Legacy of Monsters",
                        popularity = 100.0,
                    ),
                )
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls {
                return TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/monarch-poster.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/original/monarch-backdrop.jpg",
                )
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        val episode9 = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/Monarch_Legacy_of_Monsters/season_2/episode_9/",
            titleRu = "Монарх: Наследие монстров",
            releaseDateRu = "26.04.2026",
            kind = ReleaseKind.SERIES,
        )
        val episode10 = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/Monarch_Legacy_of_Monsters/season_2/episode_10/",
            titleRu = "Монарх: Наследие монстров",
            releaseDateRu = "03.05.2026",
            kind = ReleaseKind.SERIES,
        )

        assertEquals("https://image.tmdb.org/t/p/w780/monarch-poster.jpg", episode9?.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w780/monarch-poster.jpg", episode10?.posterUrl)
        assertEquals(1, searchCalls)
        assertEquals("https://www.lostfilm.today/series/Monarch_Legacy_of_Monsters/season_2/", dao.upserted?.detailsUrl)
    }

    @Test
    fun resolve_doesNotPersistNegativeMapping_whenSearchFails() = runTest {
        val dao = FakeTmdbPosterDao()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                throw java.io.IOException("offline")
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/Unknown_Title/season_1/episode_1/",
            titleRu = "Неизвестный сериал",
            releaseDateRu = "05.04.2026",
            kind = ReleaseKind.SERIES,
        )

        assertNull(result)
        assertNull(dao.upserted)
    }

    @Test
    fun resolve_usesSeasonPoster_whenAvailable() = runTest {
        val dao = FakeTmdbPosterDao()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                return listOf(TmdbSearchResult(id = 888, name = "The Terror", popularity = 10.0, rating = "8.1"))
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls {
                return TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/series-poster.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/w1280/series-backdrop.jpg",
                )
            }

            override suspend fun getSeasonImages(tmdbId: Int, seasonNumber: Int): TmdbImageUrls {
                assertEquals(888, tmdbId)
                assertEquals(3, seasonNumber)
                return TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/season3-poster.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/w1280/season3-backdrop.jpg",
                )
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/The_Terror/season_3/episode_1/",
            titleRu = "Террор",
            releaseDateRu = "05.06.2026",
            kind = ReleaseKind.SERIES,
        )

        assertNotNull(result)
        assertEquals("https://image.tmdb.org/t/p/w780/season3-poster.jpg", result?.posterUrl)
    }

    @Test
    fun resolve_fallsBackToSeriesPoster_whenSeasonHasNoImages() = runTest {
        val dao = FakeTmdbPosterDao()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                return listOf(TmdbSearchResult(id = 888, name = "The Terror", popularity = 10.0, rating = "8.1"))
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls {
                return TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/series-poster.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/w1280/series-backdrop.jpg",
                )
            }

            override suspend fun getSeasonImages(tmdbId: Int, seasonNumber: Int): TmdbImageUrls? {
                return null
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/The_Terror/season_3/episode_1/",
            titleRu = "Террор",
            releaseDateRu = "05.06.2026",
            kind = ReleaseKind.SERIES,
        )

        assertNotNull(result)
        assertEquals("https://image.tmdb.org/t/p/w780/series-poster.jpg", result?.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w1280/series-backdrop.jpg", result?.backdropUrl)
    }

    @Test
    fun resolve_usesSeasonYearHint_onSeasonCard_soNewerBrothersWins() = runTest {
        // «Братья» без года в slug. У 66515 в TMDB английское имя тоже
        // «Brothers» (original_name = «Ang Probinsyano»), поэтому точное
        // совпадение по slug проходят сразу два кандидата, и без подсказки
        // по году побеждал более популярный, но чужой.
        val dao = FakeTmdbPosterDao()
        val searchRequests = mutableListOf<Int?>()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                searchRequests += year
                return listOf(
                    TmdbSearchResult(
                        id = 66515,
                        name = "Brothers",
                        originalName = "Ang Probinsyano",
                        popularity = 63.5,
                        releaseYear = 2015,
                    ),
                    TmdbSearchResult(
                        id = 250203,
                        name = "Brothers",
                        originalName = "Brothers",
                        popularity = 41.1,
                        releaseYear = 2026,
                    ),
                )
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls =
                TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/$tmdbId.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/original/$tmdbId-backdrop.jpg",
                )
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        val season = resolver.resolve(
            detailsUrl = "https://www.lostfilm.one/series/Brothers/season_1/",
            titleRu = "\u0411\u0440\u0430\u0442\u044c\u044f",
            releaseDateRu = "22.09.2026",
            kind = ReleaseKind.SERIES,
            originalReleaseYear = 2026,
        )

        assertEquals("https://image.tmdb.org/t/p/w780/250203.jpg", season?.posterUrl)
        assertEquals(250203, dao.upserted?.tmdbId)
        assertEquals("https://www.lostfilm.one/series/Brothers/season_1/", dao.upserted?.detailsUrl)
        // Год обязан уходить в поиск: без него TMDB не отсекает 2015-й год.
        assertTrue("год должен уходить в поиск, было: $searchRequests", searchRequests.any { it == 2026 })
    }

    @Test
    fun resolve_doesNotUseEpisodeYearHint_onEpisodeCard() = runTest {
        // Год в ленте на строке эпизода — это год самой серии, а не год
        // премьеры. Долгоиграющий сериал обязан оставаться в своём матче.
        val dao = FakeTmdbPosterDao()
        val searchRequests = mutableListOf<Pair<String, Int?>>()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                searchRequests += query to year
                return listOf(
                    TmdbSearchResult(
                        id = 75219,
                        name = "9-1-1",
                        originalName = "9-1-1",
                        popularity = 100.0,
                        releaseYear = 2018,
                    ),
                )
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls =
                TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/911-poster.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/original/911-backdrop.jpg",
                )
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        val episode = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_17/",
            titleRu = "9-1-1",
            releaseDateRu = "02.05.2026",
            kind = ReleaseKind.SERIES,
            originalReleaseYear = 2026,
        )

        assertEquals("https://image.tmdb.org/t/p/w780/911-poster.jpg", episode?.posterUrl)
        assertEquals(75219, dao.upserted?.tmdbId)
        // Подсказка по году не должна уходить в поиск на строке эпизода.
        assertTrue(searchRequests.all { it.second == null })
    }

    @Test
    fun resolve_usesSeasonOverview_whenAvailable() = runTest {
        val dao = FakeTmdbPosterDao()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType, page: Int, language: String?): List<TmdbSearchResult> {
                return listOf(TmdbSearchResult(id = 888, name = "The Terror", popularity = 10.0, rating = "8.1"))
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls {
                return TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/series-poster.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/w1280/series-backdrop.jpg",
                )
            }

            override suspend fun getSeriesOverviewRu(tmdbId: Int): String {
                return "Описание всего сериала Террор."
            }

            override suspend fun getSeasonOverviewRu(tmdbId: Int, seasonNumber: Int): String {
                assertEquals(888, tmdbId)
                assertEquals(3, seasonNumber)
                return "Описание третьего сезона Террор."
            }
        }
        val resolver = TmdbPosterResolverImpl(client, dao)

        val result = resolver.resolve(
            detailsUrl = "https://www.lostfilm.today/series/The_Terror/season_3/episode_1/",
            titleRu = "Террор",
            releaseDateRu = "05.06.2026",
            kind = ReleaseKind.SERIES,
        )

        assertNotNull(result)
        assertEquals("Описание третьего сезона Террор.", result?.seriesOverviewRu)
    }
}

private class FakeTmdbPosterDao(
    private val cached: TmdbPosterMappingEntity? = null,
) : TmdbPosterDao {
    var upserted: TmdbPosterMappingEntity? = null
        private set

    override suspend fun getByDetailsUrl(detailsUrl: String): TmdbPosterMappingEntity? = cached

    override suspend fun upsert(entity: TmdbPosterMappingEntity) {
        upserted = entity
    }

    override suspend fun deleteExpired(threshold: Long) = Unit

    override suspend fun deleteSeasonMappingsUnder(seriesPrefix: String) = Unit

    override suspend fun deleteAll() = Unit
}
