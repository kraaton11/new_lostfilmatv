package com.kraat.lostfilmnewtv.tvchannel

import com.kraat.lostfilmnewtv.data.db.ReleaseSummaryEntity
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
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
}

private class FakeSummaryReader(
    private val allRows: List<ReleaseSummaryEntity> = emptyList(),
    private val unwatchedRows: List<ReleaseSummaryEntity> = emptyList(),
    private val favoriteRows: List<ReleaseSummaryEntity> = emptyList(),
) : HomeChannelSummaryReader {
    var allQueryCount: Int = 0
        private set
    var unwatchedQueryCount: Int = 0
        private set

    override suspend fun latest(limit: Int): List<ChannelProgramRow> {
        allQueryCount += 1
        return allRows.take(limit).map { it.toChannelRow() }
    }

    override suspend fun latestUnwatched(limit: Int): List<ChannelProgramRow> {
        unwatchedQueryCount += 1
        return unwatchedRows.take(limit).map { it.toChannelRow() }
    }

    override suspend fun latestFavorites(limit: Int): List<ChannelProgramRow> {
        return favoriteRows.take(limit).map { it.toChannelRow() }
    }
}

private fun summary(
    detailsUrl: String,
    titleRu: String = "Title",
    episodeTitleRu: String? = "Episode",
): ReleaseSummaryEntity {
    return ReleaseSummaryEntity(
        detailsUrl = detailsUrl,
        kind = ReleaseKind.SERIES.name,
        titleRu = titleRu,
        episodeTitleRu = episodeTitleRu,
        seasonNumber = 1,
        episodeNumber = 2,
        releaseDateRu = "21.03.2026",
        posterUrl = "https://example.com/poster.jpg",
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
