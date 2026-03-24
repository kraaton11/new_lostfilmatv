package com.kraat.lostfilmnewtv.ui.details

import androidx.lifecycle.SavedStateHandle
import com.kraat.lostfilmnewtv.data.model.FavoriteMutationResult
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.repository.DetailsResult
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.navigation.AppDestination
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
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

    @Test
    fun startAndRetry_doNotExposeRouteOwnedTorrServeMessageState() = runTest(dispatcher) {
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
        viewModel.onRetry()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun retry_ignoresOlderLoadResult_whenPreviousRequestFinishesLater() = runTest(dispatcher) {
        val firstResult = CompletableDeferred<DetailsResult>()
        val secondResult = CompletableDeferred<DetailsResult>()
        val repository = SequencedDetailsRepository(
            results = listOf(firstResult, secondResult),
        )
        val viewModel = DetailsViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(
                mapOf(AppDestination.Details.detailsUrlArg to "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"),
            ),
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        runCurrent()
        viewModel.onRetry()
        runCurrent()

        secondResult.complete(
            DetailsResult.Success(
                details = details(
                    kind = ReleaseKind.SERIES,
                    titleRu = "Recovered",
                    seasonNumber = 9,
                    episodeNumber = 13,
                    releaseDateRu = "14 марта 2026",
                ),
                isStale = false,
            ),
        )
        advanceUntilIdle()

        firstResult.complete(
            DetailsResult.Error(
                detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/",
                message = "boom",
            ),
        )
        advanceUntilIdle()

        assertEquals("Recovered", viewModel.uiState.value.details?.titleRu)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun onFavoriteClick_setsBusyState_thenMarksFavoriteUpdated() = runTest(dispatcher) {
        val favoriteResult = CompletableDeferred<FavoriteMutationResult>()
        val repository = FakeDetailsRepository(
            detailsResult = DetailsResult.Success(
                details = details(
                    kind = ReleaseKind.SERIES,
                    titleRu = "9-1-1",
                    seasonNumber = 9,
                    episodeNumber = 13,
                    releaseDateRu = "14 марта 2026",
                ).copy(
                    favoriteTargetId = 915,
                    isFavorite = false,
                ),
                isStale = false,
            ),
            favoriteResult = favoriteResult,
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
        viewModel.onFavoriteClick()
        runCurrent()

        assertEquals(true, viewModel.uiState.value.isFavoriteMutationInFlight)
        assertEquals("Сохраняем...", viewModel.uiState.value.favoriteActionLabel)
        assertEquals(false, viewModel.uiState.value.isFavoriteActionEnabled)

        favoriteResult.complete(FavoriteMutationResult.Updated)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.details?.isFavorite)
        assertEquals("Убрать из избранного", viewModel.uiState.value.favoriteActionLabel)
        assertEquals(false, viewModel.uiState.value.isFavoriteMutationInFlight)
    }

    @Test
    fun onFavoriteClick_failure_restoresPreviousStableStateAndMessage() = runTest(dispatcher) {
        val repository = FakeDetailsRepository(
            detailsResult = DetailsResult.Success(
                details = details(
                    kind = ReleaseKind.SERIES,
                    titleRu = "9-1-1",
                    seasonNumber = 9,
                    episodeNumber = 13,
                    releaseDateRu = "14 марта 2026",
                ).copy(
                    favoriteTargetId = 915,
                    isFavorite = true,
                ),
                isStale = false,
            ),
            favoriteResult = CompletableDeferred(FavoriteMutationResult.Error("boom")),
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
        viewModel.onFavoriteClick()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.details?.isFavorite)
        assertEquals("Убрать из избранного", viewModel.uiState.value.favoriteActionLabel)
        assertEquals("Не удалось обновить избранное", viewModel.uiState.value.favoriteStatusMessage)
    }

    @Test
    fun onFavoriteClick_requiresLogin_disablesActionWithLoginMessage() = runTest(dispatcher) {
        val repository = FakeDetailsRepository(
            detailsResult = DetailsResult.Success(
                details = details(
                    kind = ReleaseKind.SERIES,
                    titleRu = "9-1-1",
                    seasonNumber = 9,
                    episodeNumber = 13,
                    releaseDateRu = "14 марта 2026",
                ).copy(
                    favoriteTargetId = 915,
                    isFavorite = false,
                ),
                isStale = false,
            ),
            favoriteResult = CompletableDeferred(FavoriteMutationResult.RequiresLogin()),
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
        viewModel.onFavoriteClick()
        advanceUntilIdle()

        assertEquals("Войдите в LostFilm", viewModel.uiState.value.favoriteActionLabel)
        assertEquals(false, viewModel.uiState.value.isFavoriteActionEnabled)
        assertEquals(false, viewModel.uiState.value.isFavoriteMutationInFlight)
    }
}

private class FakeDetailsRepository(
    private val detailsResult: DetailsResult,
    private val favoriteResult: CompletableDeferred<FavoriteMutationResult> = CompletableDeferred(FavoriteMutationResult.RequiresLogin()),
) : LostFilmRepository {
    override suspend fun loadPage(pageNumber: Int): PageState {
        error("Page loading is not used in details tests")
    }

    override suspend fun loadDetails(detailsUrl: String): DetailsResult = detailsResult

    override suspend fun markEpisodeWatched(detailsUrl: String, playEpisodeId: String): Boolean = false

    override suspend fun setFavorite(detailsUrl: String, targetFavorite: Boolean): FavoriteMutationResult = favoriteResult.await()

    override suspend fun loadFavoriteReleases(): FavoriteReleasesResult {
        return FavoriteReleasesResult.Unavailable()
    }
}

private class SequencedDetailsRepository(
    private val results: List<CompletableDeferred<DetailsResult>>,
) : LostFilmRepository {
    private var index = 0

    override suspend fun loadPage(pageNumber: Int): PageState {
        error("Page loading is not used in details tests")
    }

    override suspend fun loadDetails(detailsUrl: String): DetailsResult {
        return results[index++].await()
    }

    override suspend fun markEpisodeWatched(detailsUrl: String, playEpisodeId: String): Boolean = false

    override suspend fun setFavorite(detailsUrl: String, targetFavorite: Boolean): FavoriteMutationResult {
        return FavoriteMutationResult.RequiresLogin()
    }

    override suspend fun loadFavoriteReleases(): FavoriteReleasesResult {
        return FavoriteReleasesResult.Unavailable()
    }
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
