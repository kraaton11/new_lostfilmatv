package com.kraat.lostfilmnewtv.tvchannel

import com.kraat.lostfilmnewtv.data.db.ReleaseSummaryEntity
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.parser.LostFilmBaseUrl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeChannelContentRepositoryTest {
    @Test
    fun loadPrograms_allNew_usesOrderedCachedRows() = runTest {
        val repository = HomeChannelContentRepository(
            reader = FakeSummaryReader(
                allRows = listOf(
                    summary(detailsUrl = "https://example.com/series", titleRu = "9-1-1"),
                    movie(detailsUrl = "https://example.com/movie", titleRu = "Необратимость"),
                ),
            ),
        )

        val results = repository.loadPrograms(AndroidTvChannelMode.ALL_NEW, limit = 30)

        assertEquals(listOf("9-1-1", "Необратимость"), results.map { it.title })
        assertEquals("Episode", results.first().description)
        assertEquals("21.03.2026", results.last().description)
    }

    @Test
    fun loadPrograms_unwatched_usesOnlyUnwatchedRows() = runTest {
        val repository = HomeChannelContentRepository(
            reader = FakeSummaryReader(
                unwatchedRows = listOf(
                    summary(detailsUrl = "https://example.com/fresh", titleRu = "Fresh"),
                ),
            ),
        )

        val results = repository.loadPrograms(AndroidTvChannelMode.UNWATCHED, limit = 30)

        assertEquals(listOf("https://example.com/fresh"), results.map { it.detailsUrl })
    }

    @Test
    fun loadFavoritePrograms_usesFavoriteReleaseLoader() = runTest {
        val repository = HomeChannelContentRepository(
            reader = FakeSummaryReader(),
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
        assertEquals(
            "${LostFilmBaseUrl.get()}/Static/Images/362/Posters/shmoster_s9.jpg",
            results.single().posterUrl,
        )
    }

    @Test
    fun loadFavoritePrograms_unavailable_returnsEmptyList() = runTest {
        val repository = HomeChannelContentRepository(
            reader = FakeSummaryReader(),
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
        val repository = HomeChannelContentRepository(reader = reader)

        val results = repository.loadPrograms(AndroidTvChannelMode.DISABLED, limit = 30)

        assertTrue(results.isEmpty())
        assertEquals(0, reader.allQueryCount)
        assertEquals(0, reader.unwatchedQueryCount)
    }

    @Test
    fun loadPrograms_usesCurrentMirrorAndSkipsLegacyProxyArtwork() = runTest {
        val currentBaseUrl = LostFilmBaseUrl.get()
        val posterPath = "/Static/Images/362/Posters/image_s9.jpg"
        val backdropPath = "/Static/Images/362/Posters/e_9_13.jpg"
        val repository = HomeChannelContentRepository(
            reader = FakeSummaryReader(
                allRows = listOf(
                    summary(
                        detailsUrl = "https://example.com/series",
                        posterUrl = posterPath,
                    ).copy(backdropUrl = backdropPath),
                    summary(
                        detailsUrl = "https://example.com/old",
                        posterUrl = "https://auth.bazuka.pp.ua/api/tmdb/t/p/w500/old.jpg",
                    ).copy(backdropUrl = "https://image.tmdb.org/t/p/original/old.jpg"),
                    movie(detailsUrl = "https://example.com/movie", titleRu = "Movie")
                        .copy(posterUrl = "/Static/Images/1184/Posters/image.jpg"),
                ),
            ),
        )

        try {
            LostFilmBaseUrl.set("https://www.lostfilm.download")
            val results = repository.loadPrograms(AndroidTvChannelMode.ALL_NEW, limit = 30)
            assertEquals(
                "https://www.lostfilm.download/Static/Images/362/Posters/shmoster_s9.jpg",
                results.first().posterUrl,
            )
            assertEquals("https://www.lostfilm.download$backdropPath", results.first().backdropUrl)
            assertEquals("", results[1].posterUrl)
            assertEquals("", results[1].backdropUrl)
            assertEquals(
                "https://www.lostfilm.download/Static/Images/1184/Posters/image.jpg",
                results[2].posterUrl,
            )

            LostFilmBaseUrl.set("https://www.lostfilm.tv")
            assertEquals(
                "https://www.lostfilm.tv/Static/Images/362/Posters/shmoster_s9.jpg",
                repository.loadPrograms(AndroidTvChannelMode.ALL_NEW, limit = 30).first().posterUrl,
            )
        } finally {
            LostFilmBaseUrl.set(currentBaseUrl)
        }
    }

    @Test
    fun loadPrograms_usesCachedDetailsBackdropWhenSummaryHasNone() = runTest {
        val detailsUrl = "https://example.com/series"
        val backdropPath = "/Static/Images/362/Posters/e_9_13.jpg"
        val repository = HomeChannelContentRepository(
            reader = FakeSummaryReader(
                allRows = listOf(summary(detailsUrl = detailsUrl)),
                cachedBackdrops = mapOf(detailsUrl to backdropPath),
            ),
        )

        val result = repository.loadPrograms(AndroidTvChannelMode.ALL_NEW, limit = 30).single()

        assertEquals("${LostFilmBaseUrl.get()}$backdropPath", result.backdropUrl)
    }

}

private class FakeSummaryReader(
    private val allRows: List<ReleaseSummaryEntity> = emptyList(),
    private val unwatchedRows: List<ReleaseSummaryEntity> = emptyList(),
    private val cachedBackdrops: Map<String, String> = emptyMap(),
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

    override suspend fun cachedBackdrop(detailsUrl: String): String? = cachedBackdrops[detailsUrl]
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
        posterUrl = "/Static/Images/362/Posters/image_s9.jpg",
        detailsUrl = detailsUrl,
        pageNumber = 1,
        positionInPage = 0,
        fetchedAt = 1L,
        isWatched = false,
    )
}
