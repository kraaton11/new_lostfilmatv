package com.kraat.lostfilmnewtv.tvchannel

import com.kraat.lostfilmnewtv.data.db.ReleaseSummaryEntity
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.model.TmdbImageUrls
import com.kraat.lostfilmnewtv.data.poster.TmdbPosterResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeChannelContentRepositoryTest {
    @Test
    fun loadPrograms_allNew_usesOrderedCachedRows() = runTest {
        val tmdbResolver = FakeTmdbPosterResolver()
        val repository = HomeChannelContentRepository(
            reader = FakeSummaryReader(
                allRows = listOf(
                    summary(detailsUrl = "https://example.com/series", titleRu = "9-1-1"),
                    movie(detailsUrl = "https://example.com/movie", titleRu = "Необратимость"),
                ),
            ),
            tmdbResolver = tmdbResolver,
        )

        val results = repository.loadPrograms(AndroidTvChannelMode.ALL_NEW, limit = 30)

        assertEquals(listOf("9-1-1", "Необратимость"), results.map { it.title })
        assertEquals("Episode", results.first().description)
        assertEquals("21.03.2026", results.last().description)
    }

    @Test
    fun loadPrograms_unwatched_usesOnlyUnwatchedRows() = runTest {
        val tmdbResolver = FakeTmdbPosterResolver()
        val repository = HomeChannelContentRepository(
            reader = FakeSummaryReader(
                unwatchedRows = listOf(
                    summary(detailsUrl = "https://example.com/fresh", titleRu = "Fresh"),
                ),
            ),
            tmdbResolver = tmdbResolver,
        )

        val results = repository.loadPrograms(AndroidTvChannelMode.UNWATCHED, limit = 30)

        assertEquals(listOf("https://example.com/fresh"), results.map { it.detailsUrl })
    }

    @Test
    fun loadFavoritePrograms_usesFavoriteReleaseLoader() = runTest {
        val tmdbResolver = FakeTmdbPosterResolver()
        val repository = HomeChannelContentRepository(
            reader = FakeSummaryReader(),
            tmdbResolver = tmdbResolver,
            loadFavoriteReleases = {
                FavoriteReleasesResult.Success(
                    listOf(
                        favoriteSummary(detailsUrl = "https://example.com/favorites/1", titleRu = "Favorite 1"),
                        favoriteSummary(detailsUrl = "https://example.com/favorites/2", titleRu = "Favorite 2"),
                    ),
                )
            },
        )

        val results = repository.loadFavoritePrograms(limit = 1)

        assertEquals(listOf("Favorite 1"), results.map { it.title })
        assertEquals(listOf("Favorite episode"), results.map { it.description })
    }

    @Test
    fun loadFavoritePrograms_unavailable_returnsEmptyList() = runTest {
        val repository = HomeChannelContentRepository(
            reader = FakeSummaryReader(),
            tmdbResolver = FakeTmdbPosterResolver(),
            loadFavoriteReleases = { FavoriteReleasesResult.Unavailable("login required") },
        )

        val results = repository.loadFavoritePrograms(limit = 30)

        assertTrue(results.isEmpty())
    }

    @Test
    fun loadPrograms_disabled_returnsEmptyListWithoutQueryingReader() = runTest {
        val reader = FakeSummaryReader(
            allRows = listOf(summary(detailsUrl = "https://example.com/unused")),
            unwatchedRows = listOf(summary(detailsUrl = "https://example.com/unused2")),
        )
        val tmdbResolver = FakeTmdbPosterResolver()
        val repository = HomeChannelContentRepository(reader = reader, tmdbResolver = tmdbResolver)

        val results = repository.loadPrograms(AndroidTvChannelMode.DISABLED, limit = 30)

        assertTrue(results.isEmpty())
        assertEquals(0, reader.allQueryCount)
        assertEquals(0, reader.unwatchedQueryCount)
    }

    @Test
    fun loadPrograms_enrichesPostersWithTmdb() = runTest {
        val tmdbResolver = FakeTmdbPosterResolver(
            posterOverrides = mapOf(
                "https://example.com/series" to "https://image.tmdb.org/t/p/w500/series_poster.jpg",
            ),
        )
        val repository = HomeChannelContentRepository(
            reader = FakeSummaryReader(
                allRows = listOf(
                    summary(detailsUrl = "https://example.com/series", titleRu = "9-1-1"),
                ),
            ),
            tmdbResolver = tmdbResolver,
        )

        val results = repository.loadPrograms(AndroidTvChannelMode.ALL_NEW, limit = 30)

        assertEquals("https://image.tmdb.org/t/p/w500/series_poster.jpg", results.first().posterUrl)
    }

    @Test
    fun loadPrograms_enrichesBackdropWithTmdb() = runTest {
        val tmdbResolver = FakeTmdbPosterResolver(
            backdropOverrides = mapOf(
                "https://example.com/series" to "https://image.tmdb.org/t/p/w780/series_backdrop.jpg",
            ),
        )
        val repository = HomeChannelContentRepository(
            reader = FakeSummaryReader(
                allRows = listOf(
                    summary(detailsUrl = "https://example.com/series", titleRu = "9-1-1"),
                ),
            ),
            tmdbResolver = tmdbResolver,
        )

        val results = repository.loadPrograms(AndroidTvChannelMode.ALL_NEW, limit = 30)

        assertEquals("https://image.tmdb.org/t/p/w780/series_backdrop.jpg", results.first().backdropUrl)
    }

    @Test
    fun loadPrograms_fallsBackToOriginalPosterWhenTmdbEmpty() = runTest {
        val originalPoster = "https://www.lostfilm.today/Static/Images/362/Posters/image_s9.jpg"
        val tmdbResolver = FakeTmdbPosterResolver(
            posterOverrides = emptyMap(),
        )
        val repository = HomeChannelContentRepository(
            reader = FakeSummaryReader(
                allRows = listOf(
                    summary(detailsUrl = "https://example.com/series", titleRu = "9-1-1", posterUrl = originalPoster),
                ),
            ),
            tmdbResolver = tmdbResolver,
        )

        val results = repository.loadPrograms(AndroidTvChannelMode.ALL_NEW, limit = 30)

        assertEquals(originalPoster, results.first().posterUrl)
    }
}

private class FakeSummaryReader(
    private val allRows: List<ReleaseSummaryEntity> = emptyList(),
    private val unwatchedRows: List<ReleaseSummaryEntity> = emptyList(),
) : HomeChannelSummaryReader {
    var allQueryCount: Int = 0
        private set
    var unwatchedQueryCount: Int = 0
        private set

    override suspend fun latest(limit: Int): List<ReleaseSummaryEntity> {
        allQueryCount += 1
        return allRows.take(limit)
    }

    override suspend fun latestUnwatched(limit: Int): List<ReleaseSummaryEntity> {
        unwatchedQueryCount += 1
        return unwatchedRows.take(limit)
    }
}

private class FakeTmdbPosterResolver(
    private val posterOverrides: Map<String, String> = emptyMap(),
    private val backdropOverrides: Map<String, String> = emptyMap(),
) : TmdbPosterResolver {
    override suspend fun resolve(
        detailsUrl: String,
        titleRu: String,
        releaseDateRu: String,
        kind: ReleaseKind,
    ): TmdbImageUrls? {
        val posterUrl = posterOverrides[detailsUrl]
        val backdropUrl = backdropOverrides[detailsUrl]
        return if (posterUrl != null || backdropUrl != null) {
            TmdbImageUrls(
                posterUrl = posterUrl.orEmpty(),
                backdropUrl = backdropUrl.orEmpty(),
            )
        } else {
            null
        }
    }
}

private fun summary(
    detailsUrl: String,
    titleRu: String = "Title",
    episodeTitleRu: String? = "Episode",
    posterUrl: String = "https://example.com/poster.jpg",
): ReleaseSummaryEntity {
    return ReleaseSummaryEntity(
        detailsUrl = detailsUrl,
        kind = ReleaseKind.SERIES.name,
        titleRu = titleRu,
        episodeTitleRu = episodeTitleRu,
        seasonNumber = 1,
        episodeNumber = 2,
        releaseDateRu = "21.03.2026",
        posterUrl = posterUrl,
        pageNumber = 1,
        positionInPage = 0,
        fetchedAt = 1L,
        isWatched = false,
    )
}

private fun movie(
    detailsUrl: String,
    titleRu: String,
): ReleaseSummaryEntity {
    return ReleaseSummaryEntity(
        detailsUrl = detailsUrl,
        kind = ReleaseKind.MOVIE.name,
        titleRu = titleRu,
        episodeTitleRu = null,
        seasonNumber = null,
        episodeNumber = null,
        releaseDateRu = "21.03.2026",
        posterUrl = "https://example.com/movie.jpg",
        pageNumber = 1,
        positionInPage = 1,
        fetchedAt = 1L,
        isWatched = false,
    )
}

private fun favoriteSummary(
    detailsUrl: String,
    titleRu: String,
): ReleaseSummary {
    return ReleaseSummary(
        id = detailsUrl,
        kind = ReleaseKind.SERIES,
        titleRu = titleRu,
        episodeTitleRu = "Favorite episode",
        seasonNumber = 1,
        episodeNumber = 2,
        releaseDateRu = "21.03.2026",
        posterUrl = "https://example.com/favorite.jpg",
        detailsUrl = detailsUrl,
        pageNumber = 1,
        positionInPage = 0,
        fetchedAt = 1L,
        isWatched = false,
    )
}
