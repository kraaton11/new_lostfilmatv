package com.kraat.lostfilmnewtv.ui.home

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.kraat.lostfilmnewtv.data.model.FavoriteMutationResult
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.FavoriteSeriesResult
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.repository.DetailsResult
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPreferences
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelProgram
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelProgramSource
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPublisher
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPublisherResult
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelSyncManager
import com.kraat.lostfilmnewtv.updates.AppUpdateAvailabilityStore
import com.kraat.lostfilmnewtv.updates.AppUpdateCoordinator
import com.kraat.lostfilmnewtv.updates.AppUpdateInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
        val viewModel = createViewModel(
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
    fun onResume_refreshesFirstPageAfterInterval() = runTest(dispatcher) {
        var now = 1_000L
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(summary(detailsUrl = "https://www.lostfilm.today/series/resume/season_1/episode_1/")),
                    hasNextPage = true,
                    isStale = false,
                ),
            ),
        )
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = dispatcher,
            clock = { now },
        )

        viewModel.onStart()
        advanceUntilIdle()
        viewModel.onResume()
        advanceUntilIdle()

        assertEquals(listOf(1), repository.pageRequests)

        now += 30 * 60 * 1000L + 1
        viewModel.onResume()
        advanceUntilIdle()

        assertEquals(listOf(1, 1), repository.pageRequests)
    }

    @Test
    fun onStart_whenAlreadyStarted_refreshesFirstPageAfterInterval() = runTest(dispatcher) {
        var now = 1_000L
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(summary(detailsUrl = "https://www.lostfilm.today/series/return/season_1/episode_1/")),
                    hasNextPage = true,
                    isStale = false,
                ),
            ),
        )
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = dispatcher,
            clock = { now },
        )

        viewModel.onStart()
        advanceUntilIdle()
        now += 30 * 60 * 1000L + 1
        viewModel.onStart()
        advanceUntilIdle()

        assertEquals(listOf(1, 1), repository.pageRequests)
    }

    @Test
    fun initialState_hidesFavoritesInAvailableModes_whenFavoritesRailIsHidden() = runTest(dispatcher) {
        val viewModel = createViewModel(
            repository = FakeLostFilmRepository(pageResults = emptyMap()),
            savedStateHandle = SavedStateHandle(),
            initialFavoritesRailVisible = false,
            ioDispatcher = dispatcher,
        )

        assertEquals(
            listOf(
                HomeFeedMode.AllNew,
                HomeFeedMode.FavoriteSeries,
                HomeFeedMode.Movies,
                HomeFeedMode.Series,
            ),
            viewModel.uiState.value.availableModes,
        )
    }

    @Test
    fun onStart_doesNotLoadFavoritesMode_whenFavoritesRailIsHidden() = runTest(dispatcher) {
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
            favoriteReleaseResults = mutableListOf(FavoriteReleasesResult.Success(listOf(favoriteItem))),
        )
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            initialFavoritesRailVisible = false,
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()

        assertEquals(0, repository.favoriteReleaseCalls)
        assertEquals(emptyList<ReleaseSummary>(), viewModel.uiState.value.favoriteItems)
        assertEquals(HomeModeContentState.Empty, viewModel.uiState.value.favoritesModeState)
        assertEquals(listOf(HOME_RAIL_ALL_NEW), viewModel.uiState.value.rails.map { it.id })
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
        val viewModel = createViewModel(
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
        val viewModel = createViewModel(
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
    fun onEndReached_doesNotTriggerChannelSyncForPagination() = runTest(dispatcher) {
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
        var syncCalls = 0
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            onChannelContentChanged = { syncCalls += 1 },
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()
        viewModel.onEndReached()
        advanceUntilIdle()

        assertEquals(listOf(1, 2), repository.pageRequests)
        assertEquals(1, syncCalls)
    }

    @Test
    fun onEndReached_inMoviesMode_loadsNextMoviePage() = runTest(dispatcher) {
        val firstMovie = summary(
            detailsUrl = "https://www.lostfilm.today/movies/Dune_Part_Three",
            titleRu = "Дюна: Часть третья",
        )
        val secondMovie = summary(
            detailsUrl = "https://www.lostfilm.today/movies/Frankenstein",
            titleRu = "Франкенштейн",
        )
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = emptyList(),
                    hasNextPage = false,
                    isStale = false,
                ),
            ),
            movieResults = mutableMapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(firstMovie),
                    hasNextPage = true,
                    isStale = false,
                ),
                2 to PageState.Content(
                    pageNumber = 2,
                    items = listOf(secondMovie),
                    hasNextPage = false,
                    isStale = false,
                ),
            ),
        )
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            initialSelectedMode = HomeFeedMode.Movies,
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()
        viewModel.onEndReached()
        advanceUntilIdle()

        assertEquals(listOf(1, 2), repository.movieRequests)
        assertEquals(listOf(firstMovie, secondMovie), viewModel.uiState.value.movieItems)
        assertFalse(viewModel.uiState.value.moviesHasNextPage)
    }

    @Test
    fun onEndReached_inFavoritesMode_loadsNextFavoritePage() = runTest(dispatcher) {
        val firstFavorite = summary(detailsUrl = "https://www.lostfilm.today/series/favorite/season_1/episode_1/")
        val secondFavorite = summary(
            detailsUrl = "https://www.lostfilm.today/series/favorite/season_1/episode_2/",
            titleRu = "Favorite 2",
        )
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(pageNumber = 1, items = emptyList(), hasNextPage = false, isStale = false),
            ),
            favoriteReleaseResults = mutableListOf(
                FavoriteReleasesResult.Success(
                    items = listOf(firstFavorite),
                    pageNumber = 1,
                    hasNextPage = true,
                ),
                FavoriteReleasesResult.Success(
                    items = listOf(secondFavorite),
                    pageNumber = 2,
                    hasNextPage = false,
                ),
            ),
        )
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            initialSelectedMode = HomeFeedMode.Favorites,
            initialFavoritesRailVisible = true,
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()
        viewModel.onEndReached()
        advanceUntilIdle()

        assertEquals(listOf(1, 2), repository.favoriteReleaseRequests)
        assertEquals(listOf(firstFavorite, secondFavorite), viewModel.uiState.value.favoriteItems)
        assertFalse(viewModel.uiState.value.isFavoritesPaging)
        assertFalse(viewModel.uiState.value.favoritesHasNextPage)
    }

    @Test
    fun onPagingRetry_inMoviesMode_retriesSoftMoviePagingError() = runTest(dispatcher) {
        val firstMovie = summary(detailsUrl = "https://www.lostfilm.today/movies/Dune_Part_Three")
        val secondMovie = summary(detailsUrl = "https://www.lostfilm.today/movies/Frankenstein")
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(pageNumber = 1, items = emptyList(), hasNextPage = false, isStale = false),
            ),
            movieResults = mutableMapOf(
                1 to PageState.Content(pageNumber = 1, items = listOf(firstMovie), hasNextPage = true, isStale = false),
                2 to PageState.Error(pageNumber = 2, message = "Не удалось догрузить фильмы"),
            ),
        )
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            initialSelectedMode = HomeFeedMode.Movies,
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()
        viewModel.onEndReached()
        advanceUntilIdle()

        assertEquals("Не удалось догрузить фильмы", viewModel.uiState.value.moviesPagingErrorMessage)

        repository.movieResults[2] = PageState.Content(
            pageNumber = 2,
            items = listOf(secondMovie),
            hasNextPage = false,
            isStale = false,
        )
        repository.movieRequests.clear()
        viewModel.onPagingRetry()
        advanceUntilIdle()

        assertEquals(listOf(2), repository.movieRequests)
        assertEquals(null, viewModel.uiState.value.moviesPagingErrorMessage)
        assertEquals(listOf(firstMovie, secondMovie), viewModel.uiState.value.movieItems)
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
        val viewModel = createViewModel(
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
        val viewModel = createViewModel(
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
    fun onItemWatchedStateChanged_unmarksExistingItemAndSelectedCardImmediately() = runTest(dispatcher) {
        val detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(summary(detailsUrl = detailsUrl, isWatched = true)),
                    hasNextPage = true,
                    isStale = false,
                ),
            ),
        )
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()
        viewModel.onItemFocused(detailsUrl)

        viewModel.onItemWatchedStateChanged(detailsUrl, isWatched = false)

        assertFalse(viewModel.uiState.value.items.single().isWatched)
        assertTrue(viewModel.uiState.value.selectedItem?.isWatched == false)
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
        val viewModel = createViewModel(
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
        val viewModel = createViewModel(
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
        val viewModel = createViewModel(
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
        val viewModel = createViewModel(
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
    fun onFavoriteStateChanged_removesFavoriteSeriesImmediately_beforeBackgroundReloadFinishes() = runTest(dispatcher) {
        val favoriteRailItem = summary(
            detailsUrl = "https://www.lostfilm.today/series/favorite/season_4/episode_8/",
            titleRu = "Любимый сериал",
        )
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(
                        summary(detailsUrl = "https://www.lostfilm.today/series/main/season_1/episode_1/"),
                        summary(detailsUrl = "https://www.lostfilm.today/series/favorite/season_4/episode_8/"),
                    ),
                    hasNextPage = false,
                    isStale = false,
                ),
            ),
            favoriteReleaseResults = mutableListOf(
                FavoriteReleasesResult.Success(listOf(favoriteRailItem)),
                FavoriteReleasesResult.Success(emptyList()),
            ),
        )
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            initialFavoritesRailVisible = true,
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()

        viewModel.onFavoriteStateChanged(
            detailsUrl = "https://www.lostfilm.today/series/favorite/season_1/episode_1/",
            isFavorite = false,
        )

        assertTrue(viewModel.uiState.value.favoriteItems.isEmpty())
        assertEquals(HomeModeContentState.Empty, viewModel.uiState.value.favoritesModeState)

        advanceUntilIdle()

        assertEquals(2, repository.favoriteReleaseCalls)
        assertTrue(viewModel.uiState.value.favoriteItems.isEmpty())
    }

    @Test
    fun onFavoriteStateChanged_addsMatchingAllNewSeriesImmediately_beforeBackgroundReloadFinishes() = runTest(dispatcher) {
        val addedAllNewItem = summary(
            detailsUrl = "https://www.lostfilm.today/series/favorite/season_4/episode_8/",
            titleRu = "Любимый сериал",
        )
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(
                        summary(detailsUrl = "https://www.lostfilm.today/series/main/season_1/episode_1/"),
                        addedAllNewItem,
                    ),
                    hasNextPage = false,
                    isStale = false,
                ),
            ),
            favoriteReleaseResults = mutableListOf(
                FavoriteReleasesResult.Success(emptyList()),
                FavoriteReleasesResult.Success(listOf(addedAllNewItem)),
            ),
        )
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            initialFavoritesRailVisible = true,
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()

        viewModel.onFavoriteStateChanged(
            detailsUrl = "https://www.lostfilm.today/series/favorite/season_1/episode_1/",
            isFavorite = true,
        )

        assertEquals(listOf(addedAllNewItem), viewModel.uiState.value.favoriteItems)
        assertEquals(
            HomeModeContentState.Content(listOf(addedAllNewItem)),
            viewModel.uiState.value.favoritesModeState,
        )

        advanceUntilIdle()

        assertEquals(2, repository.favoriteReleaseCalls)
        assertEquals(listOf(addedAllNewItem), viewModel.uiState.value.favoriteItems)
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
        val viewModel = createViewModel(
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
        val viewModel = createViewModel(
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
        val viewModel = createViewModel(
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
    fun onItemFocused_updatesFocusStateWithoutEmittingUiState() = runTest(dispatcher) {
        val first = summary(detailsUrl = "https://www.lostfilm.today/series/main/season_1/episode_1/")
        val second = summary(
            detailsUrl = "https://www.lostfilm.today/series/main/season_1/episode_2/",
            titleRu = "Второй релиз",
        )
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(first, second),
                    hasNextPage = false,
                    isStale = false,
                ),
            ),
        )
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()

        var uiStateEmissions = 0
        val uiStateJob = launch {
            viewModel.uiState.drop(1).collect { uiStateEmissions += 1 }
        }

        viewModel.onItemFocused(second.detailsUrl)
        advanceUntilIdle()

        assertEquals(0, uiStateEmissions)
        assertEquals(second.detailsUrl, viewModel.focusState.value.selectedItemKey)
        uiStateJob.cancel()
    }

    @Test
    fun onFavoritesModeAvailabilityChanged_whenFavoritesSelected_fallsBackToAllNew_andPersistsFallback() = runTest(dispatcher) {
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
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            initialSelectedMode = HomeFeedMode.Favorites,
            initialFavoritesRailVisible = true,
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()
        viewModel.onFavoritesRailVisibilityChanged(false)

        assertEquals(HomeFeedMode.AllNew, viewModel.uiState.value.selectedMode)
        assertEquals(HomeFeedMode.AllNew, checkNotNull(lastHomePreferencesStore).readHomeSelectedFeedMode())
    }

    @Test
    fun favoriteSeriesMode_loadsSeriesRoots_andStoresCount() = runTest(dispatcher) {
        val favoriteSeries = listOf(
            summary(
                detailsUrl = "https://www.lostfilm.today/series/alpha",
                titleRu = "Alpha",
            ),
            summary(
                detailsUrl = "https://www.lostfilm.today/series/beta",
                titleRu = "Beta",
            ),
        )
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(pageNumber = 1, items = emptyList(), hasNextPage = false, isStale = false),
            ),
            favoriteSeriesResults = mutableListOf(FavoriteSeriesResult.Success(favoriteSeries)),
        )
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            initialSelectedMode = HomeFeedMode.FavoriteSeries,
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()

        assertEquals(HomeFeedMode.FavoriteSeries, viewModel.uiState.value.selectedMode)
        assertEquals(favoriteSeries, viewModel.uiState.value.favoriteSeriesItems)
        assertEquals(HomeModeContentState.Content(favoriteSeries), viewModel.uiState.value.favoriteSeriesModeState)
        assertEquals(2, viewModel.uiState.value.favoriteSeriesCount)
    }

    @Test
    fun onStart_withCache_emitsStaleFirstThenFresh() = runTest(dispatcher) {
        // SharedFlow даёт тесту контроль над таймингом эмиссий:
        // сначала отправляем cache, проверяем промежуточное состояние,
        // затем отправляем fresh и проверяем финальное.
        val sharedFlow = MutableSharedFlow<PageState>(replay = 0, extraBufferCapacity = 4)
        val cachedItem = summary(detailsUrl = "https://www.lostfilm.today/series/cached/season_1/episode_1/")
        val freshItem = summary(detailsUrl = "https://www.lostfilm.today/series/fresh/season_1/episode_1/")
        val repository = FakeLostFilmRepository(
            pageResults = emptyMap(),
            newReleasesFlows = mapOf(1 to sharedFlow),
        )
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()

        // 1. Cache-эмиссия: показываем stale-контент, скелетон НЕ показывается.
        sharedFlow.tryEmit(
            PageState.Content(
                pageNumber = 1,
                items = listOf(cachedItem),
                hasNextPage = true,
                isStale = true,
            )
        )
        advanceUntilIdle()

        val afterCache = viewModel.uiState.value
        assertEquals(listOf(cachedItem), afterCache.items)
        assertFalse(afterCache.isInitialLoading)
        assertNull(afterCache.fullScreenErrorMessage)
        assertTrue(afterCache.allNewModeState is HomeModeContentState.Content)
        assertEquals(
            HomeModeContentState.Content(listOf(cachedItem)),
            afterCache.allNewModeState,
        )
        assertEquals(1, repository.observeNewReleasesCalls)

        // 2. Fresh-эмиссия: заменяет items, lastAllNewRefreshAt обновляется.
        sharedFlow.tryEmit(
            PageState.Content(
                pageNumber = 1,
                items = listOf(freshItem),
                hasNextPage = true,
                isStale = false,
            )
        )
        advanceUntilIdle()

        val afterFresh = viewModel.uiState.value
        assertEquals(listOf(freshItem), afterFresh.items)
        assertFalse(afterFresh.isInitialLoading)
        assertNull(afterFresh.fullScreenErrorMessage)
        assertEquals(1, repository.observeNewReleasesCalls)
    }

    @Test
    fun onStart_withEmptyCache_emitsOnlyFresh() = runTest(dispatcher) {
        val freshItem = summary(detailsUrl = "https://www.lostfilm.today/series/fresh/season_1/episode_1/")
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(freshItem),
                    hasNextPage = true,
                    isStale = false,
                ),
            ),
        )
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()

        // Без кастомного flow observeNewReleases fallback'ит на однократный loadPage —
        // ведёт себя как до изменений: спиннер, потом данные.
        assertEquals(listOf(freshItem), viewModel.uiState.value.items)
        assertFalse(viewModel.uiState.value.isInitialLoading)
        assertNull(viewModel.uiState.value.fullScreenErrorMessage)
        assertEquals(listOf(1), repository.pageRequests)
        assertEquals(1, repository.observeNewReleasesCalls)
    }

    @Test
    fun onStart_whenNetworkFailsAfterCache_keepsCachedItems() = runTest(dispatcher) {
        val cachedItem = summary(detailsUrl = "https://www.lostfilm.today/series/cached/season_1/episode_1/")
        val repository = FakeLostFilmRepository(
            pageResults = emptyMap(),
            newReleasesEmissions = mapOf(
                1 to listOf(
                    PageState.Content(
                        pageNumber = 1,
                        items = listOf(cachedItem),
                        hasNextPage = true,
                        isStale = true,
                    ),
                    PageState.Error(pageNumber = 1, message = "Сеть недоступна"),
                ),
            ),
        )
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Кэш остаётся на экране, fullScreenErrorMessage не выставляется
        // (иначе UI решит, что данных нет), только pagingErrorMessage для индикации.
        assertEquals(listOf(cachedItem), state.items)
        assertFalse(state.isInitialLoading)
        assertNull(state.fullScreenErrorMessage)
        assertEquals("Сеть недоступна", state.pagingErrorMessage)
        assertTrue(state.allNewModeState is HomeModeContentState.Content)
    }

    @Test
    fun loadPage_paginationUsesDirectPath_notObserveNewReleases() = runTest(dispatcher) {
        val firstPageItem = summary(detailsUrl = "https://www.lostfilm.today/series/page1/season_1/episode_1/")
        val secondPageItem = summary(detailsUrl = "https://www.lostfilm.today/series/page2/season_1/episode_1/")
        val repository = FakeLostFilmRepository(
            pageResults = mapOf(
                1 to PageState.Content(
                    pageNumber = 1,
                    items = listOf(firstPageItem),
                    hasNextPage = true,
                    isStale = false,
                ),
                2 to PageState.Content(
                    pageNumber = 2,
                    items = listOf(secondPageItem),
                    hasNextPage = true,
                    isStale = false,
                    isAppend = true,
                ),
            ),
        )
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(),
            ioDispatcher = dispatcher,
        )

        viewModel.onStart()
        advanceUntilIdle()
        // observeNewReleases был вызван для page=1 (onStart).
        assertEquals(1, repository.observeNewReleasesCalls)
        assertEquals(listOf(1), repository.pageRequests)

        // Пагинация должна идти напрямую через loadPage, не через observeNewReleases.
        viewModel.onEndReached()
        advanceUntilIdle()

        assertEquals(1, repository.observeNewReleasesCalls) // не увеличилось
        assertEquals(listOf(1, 2), repository.pageRequests)
        assertEquals(listOf(firstPageItem, secondPageItem), viewModel.uiState.value.items)
    }
}

private class FakeLostFilmRepository(
    private val pageResults: Map<Int, PageState>,
    val movieResults: MutableMap<Int, PageState> = mutableMapOf(
        1 to PageState.Content(pageNumber = 1, items = emptyList(), hasNextPage = false, isStale = false),
    ),
    private val favoriteReleaseResults: MutableList<FavoriteReleasesResult> = mutableListOf(
        FavoriteReleasesResult.Unavailable(),
    ),
    private val favoriteSeriesResults: MutableList<FavoriteSeriesResult> = mutableListOf(
        FavoriteSeriesResult.Unavailable(),
    ),
    /**
     * Кастомные flow для [observeNewReleases] (для тестов stale-while-revalidate).
     * Если для pageNumber нет записи, [observeNewReleases] возвращает однократный
     * вызов [loadPage] (как default-метод интерфейса).
     */
    val newReleasesFlows: Map<Int, Flow<PageState>> = emptyMap(),
    /**
     * Список эмиссий для [observeNewReleases] (для простых тестов с фиксированной
     * последовательностью cache → fresh). Удобнее [newReleasesFlows] когда не нужен
     * контроль тайминга между эмиссиями.
     */
    val newReleasesEmissions: Map<Int, List<PageState>> = emptyMap(),
) : LostFilmRepository {
    val pageRequests = mutableListOf<Int>()
    val movieRequests = mutableListOf<Int>()
    val favoriteReleaseRequests = mutableListOf<Int>()
    var favoriteReleaseCalls = 0
    var observeNewReleasesCalls = 0

    override suspend fun loadPage(pageNumber: Int): PageState {
        pageRequests += pageNumber
        return checkNotNull(pageResults[pageNumber]) {
            "Missing fake result for page $pageNumber"
        }
    }

    override fun observeNewReleases(pageNumber: Int): Flow<PageState> {
        observeNewReleasesCalls += 1
        newReleasesFlows[pageNumber]?.let { return it }
        newReleasesEmissions[pageNumber]?.let { return it.asFlow() }
        // Fallback: однократный вызов loadPage, имитируя default-метод интерфейса.
        return flow { emit(loadPage(pageNumber)) }
    }

    override suspend fun loadMovies(pageNumber: Int): PageState {
        movieRequests += pageNumber
        return checkNotNull(movieResults[pageNumber]) {
            "Missing fake movie result for page $pageNumber"
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

    override suspend fun loadWatchedState(detailsUrl: String): Boolean? = null

    override suspend fun setEpisodeWatched(detailsUrl: String, playEpisodeId: String, targetWatched: Boolean): Boolean? = targetWatched

    override suspend fun setFavorite(detailsUrl: String, targetFavorite: Boolean): FavoriteMutationResult {
        return FavoriteMutationResult.RequiresLogin()
    }

    override suspend fun loadFavoriteReleases(pageNumber: Int): FavoriteReleasesResult {
        favoriteReleaseCalls += 1
        favoriteReleaseRequests += pageNumber
        return favoriteReleaseResults.removeFirstOrNull() ?: FavoriteReleasesResult.Unavailable()
    }

    override suspend fun loadFavoriteSeries(): FavoriteSeriesResult {
        return favoriteSeriesResults.removeFirstOrNull() ?: FavoriteSeriesResult.Unavailable()
    }
}

private fun summary(
    detailsUrl: String,
    titleRu: String = "9-1-1",
    isWatched: Boolean = false,
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
    isWatched = isWatched,
)

private var homePrefsCounter = 0
private var updatePrefsCounter = 0
private var lastHomePreferencesStore: PlaybackPreferencesStore? = null

private fun createViewModel(
    repository: LostFilmRepository,
    savedStateHandle: SavedStateHandle,
    initialSelectedMode: HomeFeedMode = HomeFeedMode.AllNew,
    initialFavoritesRailVisible: Boolean = false,
    onChannelContentChanged: () -> Unit = {},
    ioDispatcher: CoroutineDispatcher,
    clock: () -> Long = { System.currentTimeMillis() },
): HomeViewModel {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefsName = "home-view-model-test-${homePrefsCounter++}"
    context.deleteSharedPreferences(prefsName)
    val preferencesStore = PlaybackPreferencesStore(context, prefsName = prefsName).also {
        it.writeHomeSelectedFeedMode(initialSelectedMode)
        it.writeHomeFavoritesRailEnabled(initialFavoritesRailVisible)
    }
    lastHomePreferencesStore = preferencesStore

    val updatePrefsName = "home-view-model-updates-${updatePrefsCounter++}"
    context.deleteSharedPreferences(updatePrefsName)
    val appUpdateCoordinator = AppUpdateCoordinator(
        installedVersion = "0.1.0",
        store = AppUpdateAvailabilityStore(context, prefsName = updatePrefsName),
        checkForUpdates = { AppUpdateInfo.UpToDate(installedVersion = "0.1.0") },
    )

    val homeChannelSyncManager = HomeChannelSyncManager(
        programSource = object : HomeChannelProgramSource {
            override suspend fun loadPrograms(mode: AndroidTvChannelMode, limit: Int): List<HomeChannelProgram> {
                return emptyList()
            }
        },
        preferences = object : HomeChannelPreferences {
            private var channelId: Long? = null

            override fun readMode(): AndroidTvChannelMode = AndroidTvChannelMode.ALL_NEW

            override fun readChannelId(): Long? = channelId

            override fun writeChannelId(channelId: Long) {
                this.channelId = channelId
            }

            override fun clearChannelId() {
                channelId = null
            }
        },
        publisher = object : HomeChannelPublisher {
            override suspend fun reconcile(
                mode: AndroidTvChannelMode,
                existingChannelId: Long?,
                programs: List<HomeChannelProgram>,
            ): HomeChannelPublisherResult {
                onChannelContentChanged()
                return HomeChannelPublisherResult(channelId = existingChannelId ?: 1L)
            }

            override suspend fun deleteChannel(channelId: Long) {
                onChannelContentChanged()
            }
        },
    )

    return HomeViewModel(
        repository = repository,
        savedStateHandle = savedStateHandle,
        preferencesStore = preferencesStore,
        homeChannelSyncManager = homeChannelSyncManager,
        appUpdateCoordinator = appUpdateCoordinator,
        ioDispatcher = ioDispatcher,
    ).apply {
        this.clock = clock
    }
}
