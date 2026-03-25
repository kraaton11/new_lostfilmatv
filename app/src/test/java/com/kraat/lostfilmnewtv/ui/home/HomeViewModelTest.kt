package com.kraat.lostfilmnewtv.ui.home

import androidx.lifecycle.SavedStateHandle
import com.kraat.lostfilmnewtv.data.model.FavoriteMutationResult
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
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
    fun onStart_successfulContentLoad_triggersChannelSync() = runTest(dispatcher) {
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(summary(detailsUrl = "https://www.lostfilm.today/series/channel-sync/season_1/episode_1/")),
                    hasNextPage = false,
                    isStale = false,
                ),
            ),
        )
        var syncCalls = 0
        val viewModel = HomeViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            onChannelContentChanged = { syncCalls += 1 },
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()

        assertEquals(1, syncCalls)
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
    fun onPagingRetry_loadsNextPageWhenSoftPagingErrorPresent() = runTest(dispatcher) {
        val firstItem = summary(detailsUrl = "https://www.lostfilm.today/series/a/season_1/episode_1/")
        val secondItem = summary(
            detailsUrl = "https://www.lostfilm.today/series/b/season_1/episode_1/",
            titleRu = "Другой сериал",
        )
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(firstItem),
                    hasNextPage = true,
                    isStale = false,
                    pagingErrorMessage = "Не удалось догрузить страницу",
                ),
                2 to PageState.Content(
                    pageNumber = 2,
                    items = listOf(firstItem, secondItem),
                    hasNextPage = false,
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

        assertEquals("Не удалось догрузить страницу", viewModel.uiState.value.pagingErrorMessage)

        repository.pageRequests.clear()
        viewModel.onPagingRetry()
        advanceUntilIdle()

        assertEquals(listOf(2), repository.pageRequests)
        assertEquals(null, viewModel.uiState.value.pagingErrorMessage)
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

    @Test
    fun onStart_whenFavoritesRailVisible_loadsFavoritesRailAfterMainRail() = runTest(dispatcher) {
        val allNewItem = summary(detailsUrl = "https://www.lostfilm.today/series/main/season_1/episode_1/")
        val favoriteItem = summary(
            detailsUrl = "https://www.lostfilm.today/series/favorite/season_4/episode_7/",
            titleRu = "Любимый сериал",
        )
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(allNewItem),
                    hasNextPage = false,
                    isStale = false,
                ),
            ),
            favoriteReleaseResults = mutableListOf(
                FavoriteReleasesResult.Success(listOf(favoriteItem)),
            ),
        )
        val viewModel = HomeViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            initialFavoritesRailVisible = true,
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()

        assertEquals(1, repository.favoriteReleaseCalls)
        assertEquals(
            listOf(HOME_RAIL_ALL_NEW, HOME_RAIL_FAVORITES),
            viewModel.uiState.value.rails.map { it.id },
        )
        assertEquals(
            listOf(favoriteItem),
            viewModel.uiState.value.rails.last().items,
        )
    }

    @Test
    fun onStart_keepsFavoritesRailHidden_whenFeedIsUnavailableOrEmpty() = runTest(dispatcher) {
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(summary(detailsUrl = "https://www.lostfilm.today/series/main/season_1/episode_1/")),
                    hasNextPage = false,
                    isStale = false,
                ),
            ),
            favoriteReleaseResults = mutableListOf(
                FavoriteReleasesResult.Success(emptyList()),
            ),
        )
        val viewModel = HomeViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            initialFavoritesRailVisible = true,
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()

        assertEquals(1, repository.favoriteReleaseCalls)
        assertEquals(listOf(HOME_RAIL_ALL_NEW), viewModel.uiState.value.rails.map { it.id })
    }

    @Test
    fun onFavoritesRailVisibilityChanged_afterStart_loadsFeed_andCanHideRailAgain() = runTest(dispatcher) {
        val favoriteItem = summary(
            detailsUrl = "https://www.lostfilm.today/series/favorite/season_4/episode_7/",
            titleRu = "Любимый сериал",
        )
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(summary(detailsUrl = "https://www.lostfilm.today/series/main/season_1/episode_1/")),
                    hasNextPage = false,
                    isStale = false,
                ),
            ),
            favoriteReleaseResults = mutableListOf(
                FavoriteReleasesResult.Success(listOf(favoriteItem)),
            ),
        )
        val viewModel = HomeViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            initialFavoritesRailVisible = false,
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()
        viewModel.onFavoritesRailVisibilityChanged(true)
        advanceUntilIdle()

        assertEquals(1, repository.favoriteReleaseCalls)
        assertEquals(listOf(HOME_RAIL_ALL_NEW, HOME_RAIL_FAVORITES), viewModel.uiState.value.rails.map { it.id })

        viewModel.onFavoritesRailVisibilityChanged(false)

        assertEquals(listOf(HOME_RAIL_ALL_NEW), viewModel.uiState.value.rails.map { it.id })
    }

    @Test
    fun onFavoriteContentInvalidated_reloadsVisibleFavoritesRail() = runTest(dispatcher) {
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(summary(detailsUrl = "https://www.lostfilm.today/series/main/season_1/episode_1/")),
                    hasNextPage = false,
                    isStale = false,
                ),
            ),
            favoriteReleaseResults = mutableListOf(
                FavoriteReleasesResult.Success(
                    listOf(
                        summary(
                            detailsUrl = "https://www.lostfilm.today/series/favorite/season_4/episode_7/",
                            titleRu = "До обновления",
                        ),
                    ),
                ),
                FavoriteReleasesResult.Success(
                    listOf(
                        summary(
                            detailsUrl = "https://www.lostfilm.today/series/favorite/season_4/episode_8/",
                            titleRu = "После обновления",
                        ),
                    ),
                ),
            ),
        )
        val viewModel = HomeViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            initialFavoritesRailVisible = true,
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()
        viewModel.onFavoriteContentInvalidated()
        advanceUntilIdle()

        assertEquals(2, repository.favoriteReleaseCalls)
        assertEquals(
            "После обновления",
            viewModel.uiState.value.rails.last().items.single().titleRu,
        )
    }

    @Test
    fun onStart_withPersistedFavoritesMode_keepsFavoritesSelected_andLoadsFavoritesEagerly() = runTest(dispatcher) {
        val favoriteItem = summary(
            detailsUrl = "https://www.lostfilm.today/series/favorite/season_4/episode_7/",
            titleRu = "Любимый сериал",
        )
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(summary(detailsUrl = "https://www.lostfilm.today/series/main/season_1/episode_1/")),
                    hasNextPage = false,
                    isStale = false,
                ),
            ),
            favoriteReleaseResults = mutableListOf(
                FavoriteReleasesResult.Success(listOf(favoriteItem)),
            ),
        )
        val viewModel = HomeViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            initialSelectedMode = HomeFeedMode.Favorites,
            initialFavoritesRailVisible = true,
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()

        assertEquals(1, repository.favoriteReleaseCalls)
        assertEquals(HomeFeedMode.Favorites, viewModel.uiState.value.selectedMode)
        assertTrue(viewModel.uiState.value.favoritesModeState is HomeModeContentState.Content)
    }

    @Test
    fun onStart_withPersistedFavoritesMode_andMissingSession_keepsFavoritesSelectedInLoginRequiredState() = runTest(dispatcher) {
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(summary(detailsUrl = "https://www.lostfilm.today/series/main/season_1/episode_1/")),
                    hasNextPage = false,
                    isStale = false,
                ),
            ),
            favoriteReleaseResults = mutableListOf(
                FavoriteReleasesResult.Unavailable("Войдите в LostFilm"),
            ),
        )
        val viewModel = HomeViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            initialSelectedMode = HomeFeedMode.Favorites,
            initialFavoritesRailVisible = true,
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()

        assertEquals(HomeFeedMode.Favorites, viewModel.uiState.value.selectedMode)
        assertEquals(
            HomeModeContentState.LoginRequired("Войдите в LostFilm"),
            viewModel.uiState.value.favoritesModeState,
        )
    }

    @Test
    fun onModeSelected_restoresRememberedCardPerMode() = runTest(dispatcher) {
        val allNewFirst = summary(detailsUrl = "https://www.lostfilm.today/series/main/season_1/episode_1/")
        val allNewSecond = summary(
            detailsUrl = "https://www.lostfilm.today/series/main/season_1/episode_2/",
            titleRu = "Второй релиз",
        )
        val favoriteFirst = summary(
            detailsUrl = "https://www.lostfilm.today/series/favorite/season_4/episode_7/",
            titleRu = "Любимый сериал",
        )
        val favoriteSecond = summary(
            detailsUrl = "https://www.lostfilm.today/series/favorite/season_4/episode_8/",
            titleRu = "Любимый сериал 2",
        )
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(allNewFirst, allNewSecond),
                    hasNextPage = false,
                    isStale = false,
                ),
            ),
            favoriteReleaseResults = mutableListOf(
                FavoriteReleasesResult.Success(listOf(favoriteFirst, favoriteSecond)),
            ),
        )
        val viewModel = HomeViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            initialSelectedMode = HomeFeedMode.AllNew,
            initialFavoritesRailVisible = true,
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()
        viewModel.onItemFocused(allNewSecond.detailsUrl)
        viewModel.onModeSelected(HomeFeedMode.Favorites)
        viewModel.onItemFocused(favoriteSecond.detailsUrl)
        viewModel.onModeSelected(HomeFeedMode.AllNew)

        assertEquals(HomeFeedMode.AllNew, viewModel.uiState.value.selectedMode)
        assertEquals(allNewSecond.detailsUrl, viewModel.uiState.value.selectedItemKey)
    }

    @Test
    fun onFavoritesModeAvailabilityChanged_whenFavoritesSelected_fallsBackToAllNew_andPersistsFallback() = runTest(dispatcher) {
        val persistedModes = mutableListOf<HomeFeedMode>()
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(summary(detailsUrl = "https://www.lostfilm.today/series/main/season_1/episode_1/")),
                    hasNextPage = false,
                    isStale = false,
                ),
            ),
            favoriteReleaseResults = mutableListOf(
                FavoriteReleasesResult.Success(
                    listOf(
                        summary(
                            detailsUrl = "https://www.lostfilm.today/series/favorite/season_4/episode_7/",
                            titleRu = "Любимый сериал",
                        ),
                    ),
                ),
            ),
        )
        val viewModel = HomeViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            initialSelectedMode = HomeFeedMode.Favorites,
            initialFavoritesRailVisible = true,
            persistSelectedMode = { persistedModes += it },
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()
        viewModel.onFavoritesRailVisibilityChanged(false)

        assertEquals(HomeFeedMode.AllNew, viewModel.uiState.value.selectedMode)
        assertEquals(listOf(HomeFeedMode.AllNew), persistedModes)
    }
}

private class FakeLostFilmRepository(
    private val pageResults: Map<Int, PageState>,
    private val favoriteReleaseResults: MutableList<FavoriteReleasesResult> = mutableListOf(
        FavoriteReleasesResult.Unavailable(),
    ),
) : LostFilmRepository {
    val pageRequests = mutableListOf<Int>()
    var favoriteReleaseCalls = 0

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

    override suspend fun loadSeriesGuide(detailsUrl: String): com.kraat.lostfilmnewtv.data.repository.SeriesGuideResult {
        return com.kraat.lostfilmnewtv.data.repository.SeriesGuideResult.Error("not needed")
    }

    override suspend fun markEpisodeWatched(detailsUrl: String, playEpisodeId: String): Boolean = false

    override suspend fun setFavorite(detailsUrl: String, targetFavorite: Boolean): FavoriteMutationResult {
        return FavoriteMutationResult.RequiresLogin()
    }

    override suspend fun loadFavoriteReleases(): FavoriteReleasesResult {
        favoriteReleaseCalls += 1
        return favoriteReleaseResults.removeFirstOrNull() ?: FavoriteReleasesResult.Unavailable()
    }
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
