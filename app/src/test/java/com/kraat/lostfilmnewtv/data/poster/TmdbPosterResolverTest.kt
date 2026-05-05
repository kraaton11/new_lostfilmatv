package com.kraat.lostfilmnewtv.data.poster

import com.kraat.lostfilmnewtv.data.db.TmdbPosterDao
import com.kraat.lostfilmnewtv.data.db.TmdbPosterMappingEntity
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.TmdbImageUrls
import com.kraat.lostfilmnewtv.data.model.TmdbMediaType
import com.kraat.lostfilmnewtv.data.model.TmdbSearchResult
import com.kraat.lostfilmnewtv.data.network.TmdbPosterClient
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TmdbPosterResolverTest {
    @Test
    fun resolve_fetchesRussianEpisodeOverview_forSeriesEpisode() = runTest {
        val dao = FakeTmdbPosterDao()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType): List<TmdbSearchResult> {
                return listOf(TmdbSearchResult(id = 777, name = "Example Show", popularity = 10.0))
            }

            override suspend fun getPosterAndBackdrop(tmdbId: Int, type: TmdbMediaType): TmdbImageUrls {
                return TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/poster.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/original/backdrop.jpg",
                )
            }

            override suspend fun getEpisodeOverviewRu(
                tmdbId: Int,
                seasonNumber: Int,
                episodeNumber: Int,
            ): String? {
                assertEquals(777, tmdbId)
                assertEquals(2, seasonNumber)
                assertEquals(8, episodeNumber)
                return "Русское описание серии из TMDB."
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
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType): List<TmdbSearchResult> {
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
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType): List<TmdbSearchResult> {
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
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType): List<TmdbSearchResult> {
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
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType): List<TmdbSearchResult> {
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
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType): List<TmdbSearchResult> {
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
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType): List<TmdbSearchResult> {
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
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType): List<TmdbSearchResult> {
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
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType): List<TmdbSearchResult> {
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
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType): List<TmdbSearchResult> {
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
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType): List<TmdbSearchResult> {
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
        assertEquals("https://www.lostfilm.today/series/9-1-1/", dao.upserted?.detailsUrl)
    }

    @Test
    fun resolve_reusesSeriesRootMappingAcrossEpisodes() = runTest {
        val dao = FakeTmdbPosterDao()
        var searchCalls = 0
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType): List<TmdbSearchResult> {
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
        assertEquals("https://www.lostfilm.today/series/Monarch_Legacy_of_Monsters/", dao.upserted?.detailsUrl)
    }

    @Test
    fun resolve_doesNotPersistNegativeMapping_whenSearchFails() = runTest {
        val dao = FakeTmdbPosterDao()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType): List<TmdbSearchResult> {
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
}
