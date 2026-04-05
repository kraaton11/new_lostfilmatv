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
    fun resolve_prefersExactEnglishSlugMatch_forGenericRussianTitle() = runTest {
        val dao = FakeTmdbPosterDao()
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType): List<TmdbSearchResult> {
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
    }

    @Test
    fun resolve_refreshesCachedMapping_whenExactSlugPointsToDifferentTmdbId() = runTest {
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
        val client = object : TmdbPosterClient(OkHttpClient(), "fake") {
            override suspend fun searchByTitle(query: String, year: Int?, type: TmdbMediaType): List<TmdbSearchResult> {
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
                return TmdbImageUrls(
                    posterUrl = "https://image.tmdb.org/t/p/w780/correct-paradise-poster.jpg",
                    backdropUrl = "https://image.tmdb.org/t/p/original/correct-paradise-backdrop.jpg",
                )
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
