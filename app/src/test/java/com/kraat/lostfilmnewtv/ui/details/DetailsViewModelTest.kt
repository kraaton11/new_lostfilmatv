package com.kraat.lostfilmnewtv.ui.details

import androidx.lifecycle.SavedStateHandle
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.repository.DetailsResult
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.navigation.AppDestination
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun seriesDetails_showSeasonEpisodeAndRuDate() = runTest(dispatcher) {
        val repository = FakeDetailsRepository(
            detailsResult = DetailsResult.Success(
                details = details(
                    kind = ReleaseKind.SERIES,
                    titleRu = "9-1-1",
                    seasonNumber = 9,
                    episodeNumber = 13,
                    releaseDateRu = "14 марта 2026",
                ),
                isStale = false,
            ),
        )
        val viewModel = DetailsViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(
                mapOf(AppDestination.Details.detailsUrlArg to "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"),
            ),
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()

        assertEquals("9-1-1", viewModel.uiState.value.details?.titleRu)
        assertEquals(9, viewModel.uiState.value.details?.seasonNumber)
        assertEquals(13, viewModel.uiState.value.details?.episodeNumber)
        assertEquals("14 марта 2026", viewModel.uiState.value.details?.releaseDateRu)
    }

    @Test
    fun movieDetails_hideSeasonEpisode() = runTest(dispatcher) {
        val repository = FakeDetailsRepository(
            detailsResult = DetailsResult.Success(
                details = details(
                    kind = ReleaseKind.MOVIE,
                    titleRu = "Необратимость",
                    seasonNumber = null,
                    episodeNumber = null,
                    releaseDateRu = "13 марта 2026",
                ),
                isStale = false,
            ),
        )
        val viewModel = DetailsViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(
                mapOf(AppDestination.Details.detailsUrlArg to "https://www.lostfilm.today/movies/Irreversible"),
            ),
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()

        assertEquals("Необратимость", viewModel.uiState.value.details?.titleRu)
        assertNull(viewModel.uiState.value.details?.seasonNumber)
        assertNull(viewModel.uiState.value.details?.episodeNumber)
    }
}

private class FakeDetailsRepository(
    private val detailsResult: DetailsResult,
) : LostFilmRepository {
    override suspend fun loadPage(pageNumber: Int): PageState {
        error("Page loading is not used in details tests")
    }

    override suspend fun loadDetails(detailsUrl: String): DetailsResult = detailsResult
}

private fun details(
    kind: ReleaseKind,
    titleRu: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    releaseDateRu: String,
): ReleaseDetails = ReleaseDetails(
    detailsUrl = "https://www.lostfilm.today/example",
    kind = kind,
    titleRu = titleRu,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    releaseDateRu = releaseDateRu,
    posterUrl = "https://www.lostfilm.today/poster.jpg",
    fetchedAt = 0L,
)
