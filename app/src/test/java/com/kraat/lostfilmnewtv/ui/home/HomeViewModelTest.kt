package com.kraat.lostfilmnewtv.ui.home

import androidx.lifecycle.SavedStateHandle
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.repository.DetailsResult
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun onStart_loadsFirstPageAutomatically() = runTest(dispatcher) {
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(summary(detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/")),
                    hasNextPage = true,
                    isStale = false,
                ),
            ),
        )
        val viewModel = HomeViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()

        assertEquals(listOf(1), repository.pageRequests)
        assertEquals(1, viewModel.uiState.value.items.size)
        assertFalse(viewModel.uiState.value.isInitialLoading)
    }

    @Test
    fun onEndReached_loadsNextPageOnce() = runTest(dispatcher) {
        val firstItem = summary(detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/")
        val secondItem = summary(
            detailsUrl = "https://www.lostfilm.today/series/Love_Story/season_1/episode_7/",
            titleRu = "История любви",
        )
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(firstItem),
                    hasNextPage = true,
                    isStale = false,
                ),
                2 to PageState.Content(
                    pageNumber = 2,
                    items = listOf(firstItem, secondItem),
                    hasNextPage = true,
                    isStale = false,
                ),
            ),
        )
        val viewModel = HomeViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()
        viewModel.onEndReached()
        viewModel.onEndReached()
        advanceUntilIdle()

        assertEquals(listOf(1, 2), repository.pageRequests)
        assertEquals(2, viewModel.uiState.value.items.size)
        assertFalse(viewModel.uiState.value.isPaging)
    }

    @Test
    fun staleCache_setsStaleBanner() = runTest(dispatcher) {
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(summary(detailsUrl = "https://www.lostfilm.today/movies/Irreversible")),
                    hasNextPage = true,
                    isStale = true,
                ),
            ),
        )
        val viewModel = HomeViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showStaleBanner)
    }

    @Test
    fun onItemWatched_marksExistingItemAndSelectedCardImmediately() = runTest(dispatcher) {
        val detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(summary(detailsUrl = detailsUrl)),
                    hasNextPage = true,
                    isStale = false,
                ),
            ),
        )
        val viewModel = HomeViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()
        viewModel.onItemFocused(detailsUrl)

        viewModel.onItemWatched(detailsUrl)

        assertTrue(viewModel.uiState.value.items.single().isWatched)
        assertTrue(viewModel.uiState.value.selectedItem?.isWatched == true)
    }
}

private class FakeLostFilmRepository(
    private val pageResults: Map<Int, PageState>,
) : LostFilmRepository {
    val pageRequests = mutableListOf<Int>()

    override suspend fun loadPage(pageNumber: Int): PageState {
        pageRequests += pageNumber
        return checkNotNull(pageResults[pageNumber]) {
            "Missing fake result for page $pageNumber"
        }
    }

    override suspend fun loadDetails(detailsUrl: String): DetailsResult {
        return DetailsResult.Success(
            details = ReleaseDetails(
                detailsUrl = detailsUrl,
                kind = ReleaseKind.MOVIE,
                titleRu = "stub",
                seasonNumber = null,
                episodeNumber = null,
                releaseDateRu = "14 марта 2026",
                posterUrl = "",
                fetchedAt = 0L,
            ),
            isStale = false,
        )
    }

    override suspend fun markEpisodeWatched(detailsUrl: String, playEpisodeId: String): Boolean = false
}

private fun summary(
    detailsUrl: String,
    titleRu: String = "9-1-1",
): ReleaseSummary = ReleaseSummary(
    id = detailsUrl,
    kind = ReleaseKind.SERIES,
    titleRu = titleRu,
    episodeTitleRu = "Маменькин сынок",
    seasonNumber = 9,
    episodeNumber = 13,
    releaseDateRu = "14.03.2026",
    posterUrl = "https://www.lostfilm.today/Static/Images/362/Posters/image_s9.jpg",
    detailsUrl = detailsUrl,
    pageNumber = 1,
    positionInPage = 0,
    fetchedAt = 0L,
)
