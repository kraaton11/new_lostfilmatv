package com.kraat.lostfilmnewtv.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import com.kraat.lostfilmnewtv.updates.AppUpdateCoordinator
import com.kraat.lostfilmnewtv.updates.SavedAppUpdate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val FOCUS_KEY = "home.focused_item_key"
private val favoriteSeriesSlugRegex = Regex("""/series/([^/]+)""")

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: LostFilmRepository,
    private val savedStateHandle: SavedStateHandle,
    private val preferencesStore: PlaybackPreferencesStore,
    private val homeChannelSyncManager: HomeChannelSyncManager,
    private val appUpdateCoordinator: AppUpdateCoordinator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val initialSelectedMode: HomeFeedMode
        get() = preferencesStore.readHomeSelectedFeedMode()

    private val initialFavoritesRailVisible: Boolean
        get() = preferencesStore.readHomeFavoritesRailEnabled()

    private val _uiState = MutableStateFlow(
        HomeUiState(
            selectedItemKey = savedStateHandle[FOCUS_KEY],
            isInitialLoading = true,
            selectedMode = resolvedInitialMode(
                initialSelectedMode = initialSelectedMode,
                isFavoritesVisible = initialFavoritesRailVisible,
            ),
            availableModes = availableModes(initialFavoritesRailVisible),
            allNewModeState = HomeModeContentState.Loading,
            favoritesModeState = if (initialFavoritesRailVisible) {
                HomeModeContentState.Loading
            } else {
                HomeModeContentState.Empty
            },
            seriesModeState = HomeModeContentState.Loading,
            isFavoritesRailVisible = initialFavoritesRailVisible,
        ).withResolvedHomeSelection(),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    val savedAppUpdate: StateFlow<SavedAppUpdate?> = appUpdateCoordinator.savedUpdateState

    private var started = false
    private var favoriteRequestToken = 0L
    private var favoriteLoadJob: Job? = null
    private var moviesLoadJob: Job? = null
    private var seriesLoadJob: Job? = null

    fun onStart() {
        if (started) return
        started = true
        if (!initialFavoritesRailVisible && initialSelectedMode == HomeFeedMode.Favorites) {
            preferencesStore.writeHomeSelectedFeedMode(HomeFeedMode.AllNew)
        }
        loadPage(pageNumber = 1, isPagingRequest = false)
        loadMovies(pageNumber = 1, isPagingRequest = false)
        loadSeriesCatalog(pageNumber = 1, isPagingRequest = false)
        if (_uiState.value.isFavoritesRailVisible) {
            loadFavoriteReleases()
        }
    }

    fun onRetry() {
        when (_uiState.value.selectedMode) {
            HomeFeedMode.Favorites -> {
                if (_uiState.value.isFavoritesRailVisible) {
                    loadFavoriteReleases()
                    return
                }
            }
            HomeFeedMode.Movies -> {
                loadMovies(pageNumber = 1, isPagingRequest = false)
                return
            }
            HomeFeedMode.Series -> {
                loadSeriesCatalog(pageNumber = 1, isPagingRequest = false)
                return
            }
            HomeFeedMode.AllNew -> Unit
        }
        loadPage(pageNumber = 1, isPagingRequest = false)
        loadMovies(pageNumber = 1, isPagingRequest = false)
        loadSeriesCatalog(pageNumber = 1, isPagingRequest = false)
        if (_uiState.value.isFavoritesRailVisible) loadFavoriteReleases()
    }

    fun onPagingRetry() {
        val state = _uiState.value
        when (state.selectedMode) {
            HomeFeedMode.AllNew -> {
                if (state.pagingErrorMessage == null || state.isPaging || state.isInitialLoading || !state.hasNextPage) return
                loadPage(pageNumber = state.nextPage, isPagingRequest = true)
            }
            HomeFeedMode.Movies -> {
                if (state.moviesPagingErrorMessage == null || state.isMoviesPaging || !state.moviesHasNextPage) return
                loadMovies(pageNumber = state.moviesNextPage, isPagingRequest = true)
            }
            HomeFeedMode.Favorites -> Unit
            HomeFeedMode.Series -> {
                if (state.seriesPagingErrorMessage == null || state.isSeriesPaging || !state.seriesHasNextPage) return
                loadSeriesCatalog(pageNumber = state.seriesNextPage, isPagingRequest = true)
            }
        }
    }

    fun onEndReached() {
        val state = _uiState.value
        when (state.selectedMode) {
            HomeFeedMode.AllNew -> {
                if (state.isInitialLoading || state.isPaging || !state.hasNextPage || state.fullScreenErrorMessage != null) return
                loadPage(pageNumber = state.nextPage, isPagingRequest = true)
            }
            HomeFeedMode.Movies -> {
                if (state.isMoviesPaging || !state.moviesHasNextPage || state.moviesModeState !is HomeModeContentState.Content) return
                loadMovies(pageNumber = state.moviesNextPage, isPagingRequest = true)
            }
            HomeFeedMode.Favorites -> Unit
            HomeFeedMode.Series -> {
                if (state.isSeriesPaging || !state.seriesHasNextPage || state.seriesModeState !is HomeModeContentState.Content) return
                loadSeriesCatalog(pageNumber = state.seriesNextPage, isPagingRequest = true)
            }
        }
    }

    fun onItemFocused(itemKey: String) {
        _uiState.update { state ->
            val mode = itemMode(state, itemKey)
            val normalizedKey = preferredDetailsUrl(itemKey) ?: itemKey
            savedStateHandle[FOCUS_KEY] = normalizedKey
            val selectedMatch = findItemForMode(state, mode, normalizedKey)
            state.copy(
                rememberedItemKeyByMode = state.rememberedItemKeyByMode + (mode to normalizedKey),
                selectedItemKey = if (mode == state.selectedMode) normalizedKey else state.selectedItemKey,
                selectedItem = selectedMatch?.item ?: state.selectedItem,
            )
        }
    }

    fun onItemWatched(detailsUrl: String) {
        onItemWatchedStateChanged(detailsUrl, isWatched = true)
    }

    fun onItemWatchedStateChanged(detailsUrl: String, isWatched: Boolean) {
        _uiState.update { state ->
            val updatedItems = state.items.map { if (it.detailsUrl == detailsUrl) it.copy(isWatched = isWatched) else it }
            val updatedFavoriteItems = state.favoriteItems.map { if (it.detailsUrl == detailsUrl) it.copy(isWatched = isWatched) else it }
            val updatedMovieItems = state.movieItems.map { if (it.detailsUrl == detailsUrl) it.copy(isWatched = isWatched) else it }
            val updatedSeriesItems = state.seriesItems.map { if (it.detailsUrl == detailsUrl) it.copy(isWatched = isWatched) else it }
            state.copy(
                items = updatedItems,
                favoriteItems = updatedFavoriteItems,
                movieItems = updatedMovieItems,
                seriesItems = updatedSeriesItems,
                allNewModeState = state.allNewModeState.updateItems(updatedItems),
                favoritesModeState = state.favoritesModeState.updateItems(updatedFavoriteItems),
                moviesModeState = state.moviesModeState.updateItems(updatedMovieItems),
                seriesModeState = state.seriesModeState.updateItems(updatedSeriesItems),
            ).withResolvedHomeSelection()
        }
    }

    fun onFavoritesRailVisibilityChanged(isVisible: Boolean) {
        if (_uiState.value.isFavoritesRailVisible == isVisible) return
        favoriteLoadJob?.cancel()
        val shouldPersistFallback = _uiState.value.selectedMode == HomeFeedMode.Favorites && !isVisible
        _uiState.update { state ->
            val selectedMode = if (state.selectedMode == HomeFeedMode.Favorites && !isVisible) HomeFeedMode.AllNew else state.selectedMode
            state.copy(
                selectedMode = selectedMode,
                availableModes = availableModes(isVisible),
                isFavoritesRailVisible = isVisible,
                favoriteItems = if (isVisible) state.favoriteItems else emptyList(),
                favoritesModeState = if (isVisible) state.favoritesModeState else HomeModeContentState.Empty,
            ).withResolvedHomeSelection()
        }
        if (shouldPersistFallback) preferencesStore.writeHomeSelectedFeedMode(HomeFeedMode.AllNew)
        if (started && isVisible) loadFavoriteReleases()
    }

    fun onFavoriteContentInvalidated() {
        if (!started || !_uiState.value.isFavoritesRailVisible) return
        loadFavoriteReleases()
    }

    fun onFavoriteStateChanged(detailsUrl: String, isFavorite: Boolean) {
        if (!started || !_uiState.value.isFavoritesRailVisible) return
        _uiState.update { state ->
            val updatedFavoriteItems = applyOptimisticFavoriteChange(
                currentFavoriteItems = state.favoriteItems,
                allNewItems = state.items,
                detailsUrl = detailsUrl,
                isFavorite = isFavorite,
            )
            state.copy(
                favoriteItems = updatedFavoriteItems,
                favoritesModeState = state.favoritesModeState.optimisticallyUpdateFavoriteItems(updatedFavoriteItems),
            ).withResolvedHomeSelection()
        }
        loadFavoriteReleases(retainVisibleItemsOnFailure = true)
    }

    fun onModeSelected(mode: HomeFeedMode) {
        _uiState.update { state ->
            if (mode == state.selectedMode || mode !in state.availableModes) state
            else {
                preferencesStore.writeHomeSelectedFeedMode(mode)
                state.copy(selectedMode = mode).withResolvedHomeSelection()
            }
        }
    }

    private fun loadPage(pageNumber: Int, isPagingRequest: Boolean) {
        _uiState.update { state ->
            state.copy(
                isInitialLoading = !isPagingRequest,
                isPaging = isPagingRequest,
                fullScreenErrorMessage = if (isPagingRequest) state.fullScreenErrorMessage else null,
                pagingErrorMessage = null,
                allNewModeState = if (isPagingRequest) state.allNewModeState else HomeModeContentState.Loading,
            )
        }
        viewModelScope.launch(ioDispatcher) {
            when (val result = repository.loadPage(pageNumber)) {
                is PageState.Content -> {
                    _uiState.update { state ->
                        state.copy(
                            items = result.items,
                            isInitialLoading = false,
                            isPaging = false,
                            fullScreenErrorMessage = null,
                            pagingErrorMessage = result.pagingErrorMessage,
                            nextPage = result.pageNumber + 1,
                            hasNextPage = result.hasNextPage,
                            allNewModeState = HomeModeContentState.Content(result.items),
                        ).withResolvedHomeSelection()
                    }
                    // Channel rows are based on the first page; avoid expensive background sync on pagination.
                    if (!isPagingRequest) {
                        homeChannelSyncManager.syncNow()
                    }
                }
                is PageState.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            isInitialLoading = false,
                            isPaging = false,
                            fullScreenErrorMessage = result.message,
                            pagingErrorMessage = null,
                            allNewModeState = HomeModeContentState.Error(result.message),
                        ).withResolvedHomeSelection()
                    }
                }
            }
        }
    }

    private fun loadFavoriteReleases(retainVisibleItemsOnFailure: Boolean = false) {
        val requestToken = favoriteRequestToken + 1
        favoriteRequestToken = requestToken
        favoriteLoadJob?.cancel()
        favoriteLoadJob = viewModelScope.launch(ioDispatcher) {
            val result = repository.loadFavoriteReleases()
            if (favoriteRequestToken != requestToken) return@launch
            _uiState.update { state ->
                when (result) {
                    is FavoriteReleasesResult.Success -> {
                        val favoriteState = if (result.items.isEmpty()) HomeModeContentState.Empty
                        else HomeModeContentState.Content(result.items)
                        state.copy(favoriteItems = result.items, favoritesModeState = favoriteState).withResolvedHomeSelection()
                    }
                    is FavoriteReleasesResult.Unavailable -> {
                        if (retainVisibleItemsOnFailure) return@update state.withResolvedHomeSelection()
                        val favoriteState = when {
                            result.message.isNullOrBlank() -> HomeModeContentState.Error("Не удалось загрузить избранное")
                            result.message.contains("Войдите", ignoreCase = true) -> HomeModeContentState.LoginRequired(result.message)
                            else -> HomeModeContentState.Error(result.message)
                        }
                        state.copy(favoriteItems = emptyList(), favoritesModeState = favoriteState).withResolvedHomeSelection()
                    }
                }
            }
        }
    }

    private fun loadMovies(pageNumber: Int, isPagingRequest: Boolean) {
        if (!isPagingRequest) {
            moviesLoadJob?.cancel()
        }
        _uiState.update { state ->
            state.copy(
                isMoviesPaging = isPagingRequest,
                moviesPagingErrorMessage = null,
                moviesModeState = if (isPagingRequest) state.moviesModeState else HomeModeContentState.Loading,
            )
        }
        moviesLoadJob = viewModelScope.launch(ioDispatcher) {
            when (val result = repository.loadMovies(pageNumber)) {
                is PageState.Content -> {
                    val updatedItems = if (isPagingRequest) {
                        (_uiState.value.movieItems + result.items).distinctBy { it.detailsUrl }
                    } else {
                        result.items
                    }
                    val moviesState = if (result.items.isEmpty()) {
                        if (isPagingRequest && updatedItems.isNotEmpty()) {
                            HomeModeContentState.Content(updatedItems)
                        } else {
                            HomeModeContentState.Empty
                        }
                    } else {
                        HomeModeContentState.Content(updatedItems)
                    }
                    _uiState.update { state ->
                        state.copy(
                            movieItems = updatedItems,
                            moviesModeState = moviesState,
                            isMoviesPaging = false,
                            moviesPagingErrorMessage = null,
                            moviesNextPage = result.pageNumber + 1,
                            moviesHasNextPage = result.hasNextPage,
                        ).withResolvedHomeSelection()
                    }
                }
                is PageState.Error -> {
                    _uiState.update { state ->
                        if (isPagingRequest && state.movieItems.isNotEmpty()) {
                            state.copy(
                                isMoviesPaging = false,
                                moviesPagingErrorMessage = result.message,
                            ).withResolvedHomeSelection()
                        } else {
                            state.copy(
                                movieItems = emptyList(),
                                isMoviesPaging = false,
                                moviesPagingErrorMessage = null,
                                moviesModeState = HomeModeContentState.Error(result.message),
                            ).withResolvedHomeSelection()
                        }
                    }
                }
            }
        }
    }

    private fun loadSeriesCatalog(pageNumber: Int, isPagingRequest: Boolean) {
        if (!isPagingRequest) {
            seriesLoadJob?.cancel()
        }
        _uiState.update { state ->
            state.copy(
                isSeriesPaging = isPagingRequest,
                seriesPagingErrorMessage = null,
                seriesModeState = if (isPagingRequest) state.seriesModeState else HomeModeContentState.Loading,
            )
        }
        seriesLoadJob = viewModelScope.launch(ioDispatcher) {
            when (val result = repository.loadSeriesCatalog(pageNumber)) {
                is PageState.Content -> {
                    val updatedItems = if (isPagingRequest) {
                        (_uiState.value.seriesItems + result.items).distinctBy { it.detailsUrl }
                    } else {
                        result.items
                    }
                    val seriesState = if (result.items.isEmpty()) {
                        if (isPagingRequest && updatedItems.isNotEmpty()) {
                            HomeModeContentState.Content(updatedItems)
                        } else {
                            HomeModeContentState.Empty
                        }
                    } else {
                        HomeModeContentState.Content(updatedItems)
                    }
                    _uiState.update { state ->
                        state.copy(
                            seriesItems = updatedItems,
                            seriesModeState = seriesState,
                            isSeriesPaging = false,
                            seriesPagingErrorMessage = null,
                            seriesNextPage = result.pageNumber + 1,
                            seriesHasNextPage = result.hasNextPage,
                        ).withResolvedHomeSelection()
                    }
                }
                is PageState.Error -> {
                    _uiState.update { state ->
                        if (isPagingRequest && state.seriesItems.isNotEmpty()) {
                            state.copy(
                                isSeriesPaging = false,
                                seriesPagingErrorMessage = result.message,
                            ).withResolvedHomeSelection()
                        } else {
                            state.copy(
                                seriesItems = emptyList(),
                                isSeriesPaging = false,
                                seriesPagingErrorMessage = null,
                                seriesModeState = HomeModeContentState.Error(result.message),
                            ).withResolvedHomeSelection()
                        }
                    }
                }
            }
        }
    }
}

private fun applyOptimisticFavoriteChange(
    currentFavoriteItems: List<ReleaseSummary>,
    allNewItems: List<ReleaseSummary>,
    detailsUrl: String,
    isFavorite: Boolean,
): List<ReleaseSummary> {
    val favoriteSeriesSlug = favoriteSeriesSlug(detailsUrl) ?: return currentFavoriteItems
    val favoriteItemsWithoutSeries = currentFavoriteItems.filterNot { favoriteSeriesSlug(it.detailsUrl) == favoriteSeriesSlug }
    if (!isFavorite) return favoriteItemsWithoutSeries
    val matchingAllNewItem = allNewItems.firstOrNull { favoriteSeriesSlug(it.detailsUrl) == favoriteSeriesSlug }
        ?: return currentFavoriteItems
    val matchingAllNewIndex = allNewItems.indexOfFirst { it.detailsUrl == matchingAllNewItem.detailsUrl }
    val insertIndex = favoriteItemsWithoutSeries.indexOfFirst { existing ->
        val idx = allNewItems.indexOfFirst { it.detailsUrl == existing.detailsUrl }
        idx == -1 || idx > matchingAllNewIndex
    }.let { if (it >= 0) it else favoriteItemsWithoutSeries.size }
    return favoriteItemsWithoutSeries.toMutableList().apply { add(insertIndex, matchingAllNewItem) }
}

private fun HomeUiState.withResolvedHomeSelection(): HomeUiState {
    val rails = buildHomeRails(
        items = items,
        favoriteItems = favoriteItems,
        movieItems = movieItems,
        seriesItems = seriesItems,
        isFavoritesRailVisible = isFavoritesRailVisible,
    )
    val normalizedSelectedMode = if (selectedMode in availableModes) selectedMode else HomeFeedMode.AllNew
    val activeItems = itemsForMode(normalizedSelectedMode)
    val preferredKey = rememberedItemKeyByMode[normalizedSelectedMode] ?: preferredDetailsUrl(selectedItemKey)
    val selectedItem = activeItems.firstOrNull { it.detailsUrl == preferredKey } ?: activeItems.firstOrNull()
    return copy(
        selectedMode = normalizedSelectedMode,
        availableModes = availableModes(isFavoritesRailVisible),
        rails = rails,
        rememberedItemKeyByMode = rememberedItemKeyByMode.withDefaultRememberedKeys(items, favoriteItems, movieItems, seriesItems),
        selectedItem = selectedItem,
        selectedItemKey = selectedItem?.detailsUrl,
    )
}

private fun HomeModeContentState.updateItems(items: List<ReleaseSummary>): HomeModeContentState =
    if (this is HomeModeContentState.Content) HomeModeContentState.Content(items) else this

private fun HomeModeContentState.optimisticallyUpdateFavoriteItems(items: List<ReleaseSummary>): HomeModeContentState {
    if (items.isNotEmpty()) return HomeModeContentState.Content(items)
    return when (this) {
        HomeModeContentState.Loading -> HomeModeContentState.Loading
        HomeModeContentState.Empty -> HomeModeContentState.Empty
        is HomeModeContentState.LoginRequired -> this
        is HomeModeContentState.Error -> this
        is HomeModeContentState.Content -> HomeModeContentState.Empty
    }
}

private fun availableModes(isFavoritesVisible: Boolean) =
    if (isFavoritesVisible) {
        listOf(HomeFeedMode.AllNew, HomeFeedMode.Favorites, HomeFeedMode.Movies, HomeFeedMode.Series)
    } else {
        listOf(HomeFeedMode.AllNew, HomeFeedMode.Movies, HomeFeedMode.Series)
    }

private fun resolvedInitialMode(initialSelectedMode: HomeFeedMode, isFavoritesVisible: Boolean) =
    if (initialSelectedMode == HomeFeedMode.Favorites && !isFavoritesVisible) HomeFeedMode.AllNew else initialSelectedMode

private fun findItemForMode(state: HomeUiState, mode: HomeFeedMode, itemKey: String): HomeModeItemMatch? =
    state.itemsForMode(mode).firstOrNull { it.detailsUrl == itemKey }?.let { HomeModeItemMatch(mode, it) }

private fun itemMode(state: HomeUiState, itemKey: String): HomeFeedMode = when (railIdFromItemKey(itemKey)) {
    HOME_RAIL_FAVORITES -> HomeFeedMode.Favorites
    HOME_RAIL_MOVIES -> HomeFeedMode.Movies
    HOME_RAIL_SERIES -> HomeFeedMode.Series
    HOME_RAIL_ALL_NEW -> HomeFeedMode.AllNew
    else -> state.selectedMode
}

private fun preferredDetailsUrl(itemKey: String?): String? {
    if (itemKey.isNullOrBlank()) return null
    return if (itemKey.contains("::")) itemKey.substringAfter("::", "") else itemKey
}

private fun favoriteSeriesSlug(detailsUrl: String): String? =
    favoriteSeriesSlugRegex.find(detailsUrl)?.groupValues?.getOrNull(1)

private fun Map<HomeFeedMode, String>.withDefaultRememberedKeys(
    items: List<ReleaseSummary>,
    favoriteItems: List<ReleaseSummary>,
    movieItems: List<ReleaseSummary>,
    seriesItems: List<ReleaseSummary>,
): Map<HomeFeedMode, String> {
    val current = toMutableMap()
    if (HomeFeedMode.AllNew !in current) items.firstOrNull()?.detailsUrl?.let { current[HomeFeedMode.AllNew] = it }
    if (HomeFeedMode.Favorites !in current) favoriteItems.firstOrNull()?.detailsUrl?.let { current[HomeFeedMode.Favorites] = it }
    if (HomeFeedMode.Movies !in current) movieItems.firstOrNull()?.detailsUrl?.let { current[HomeFeedMode.Movies] = it }
    if (HomeFeedMode.Series !in current) seriesItems.firstOrNull()?.detailsUrl?.let { current[HomeFeedMode.Series] = it }
    return current
}

private data class HomeModeItemMatch(val mode: HomeFeedMode, val item: ReleaseSummary)
