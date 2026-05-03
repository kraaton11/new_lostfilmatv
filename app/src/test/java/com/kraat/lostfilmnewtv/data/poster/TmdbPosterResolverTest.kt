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
