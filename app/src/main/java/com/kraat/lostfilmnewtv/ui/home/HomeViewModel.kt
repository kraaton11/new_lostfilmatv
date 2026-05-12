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
private const val HOME_RESUME_REFRESH_INTERVAL_MS = 30 * 60 * 1000L
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
    internal var clock: () -> Long = { System.currentTimeMillis() }

    private val initialSelectedMode: HomeFeedMode
        get() = preferencesStore.readHomeSelectedFeedMode()

    private val initialFavoritesRailVisible: Boolean
        get() = preferencesStore.readHomeFavoritesRailEnabled()

    private val initialHomeMenuLabelsEnabled: Boolean
        get() = preferencesStore.readHomeMenuLabelsEnabled()

    private val initialUiState = HomeUiState(
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
        isHomeMenuLabelsEnabled = initialHomeMenuLabelsEnabled,
    ).withResolvedHomeSelection()

    private val _uiState = MutableStateFlow(initialUiState)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _focusState = MutableStateFlow(HomeFocusState.from(initialUiState))
    val focusState: StateFlow<HomeFocusState> = _focusState.asStateFlow()

    private val _selectedNavItem = MutableStateFlow(NavItem.HOME)
    val selectedNavItem: StateFlow<NavItem> = _selectedNavItem.asStateFlow()

    val savedAppUpdate: StateFlow<SavedAppUpdate?> = appUpdateCoordinator.savedUpdateState

    private fun HomeUiState.resolveSelection(): HomeUiState =
        withResolvedHomeSelection(_focusState.value)

    private fun updateFocusFromResolvedState(state: HomeUiState) {
        _focusState.value = HomeFocusState.from(state)
    }

    private var started = false
    private var favoriteRequestToken = 0L
    private var allNewLoadJob: Job? = null
    private var favoriteLoadJob: Job? = null
    private var moviesLoadJob: Job? = null
    private var seriesLoadJob: Job? = null
    private var lastAllNewRefreshAt = 0L

    fun onStart() {
        if (started) {
            onResume()
            return
        }
        started = true
        if (!initialFavoritesRailVisible && initialSelectedMode == HomeFeedMode.Favorites) {
            preferencesStore.writeHomeSelectedFeedMode(HomeFeedMode.AllNew)
        }
        loadPage(pageNumber = 1, isPagingRequest = false)
        loadMovies(pageNumber = 1, isPagingRequest = false)
        loadSeriesCatalog(pageNumber = 1, isPagingRequest = false)
        loadFavoriteReleases()
    }

    fun onResume() {
        if (!started) return
        if (allNewLoadJob?.isActive == true) return

        val now = clock()
        if (now - lastAllNewRefreshAt < HOME_RESUME_REFRESH_INTERVAL_MS) return

        loadPage(pageNumber = 1, isPagingRequest = false)
    }

    fun onRetry() {
        when (_uiState.value.selectedMode) {
            HomeFeedMode.Favorites -> {
                val state = _uiState.value
                if (state.favoritesPagingErrorMessage == null ||
                    state.isFavoritesPaging ||
                    !state.favoritesHasNextPage ||
                    state.favoritesModeState !is HomeModeContentState.Content
                ) {
                    return
                }
                loadFavoriteReleases(pageNumber = state.favoritesNextPage, isPagingRequest = true)
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
        loadFavoriteReleases()
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
            HomeFeedMode.Favorites -> {
                if (state.favoritesPagingErrorMessage == null ||
                    state.isFavoritesPaging ||
                    !state.favoritesHasNextPage ||
                    state.favoritesModeState !is HomeModeContentState.Content
                ) {
                    return
                }
                loadFavoriteReleases(pageNumber = state.favoritesNextPage, isPagingRequest = true)
            }
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
            HomeFeedMode.Favorites -> {
                if (state.isFavoritesPaging ||
                    !state.favoritesHasNextPage ||
                    state.favoritesModeState !is HomeModeContentState.Content
                ) {
                    return
                }
                loadFavoriteReleases(pageNumber = state.favoritesNextPage, isPagingRequest = true)
            }
            HomeFeedMode.Series -> {
                if (state.isSeriesPaging || !state.seriesHasNextPage || state.seriesModeState !is HomeModeContentState.Content) return
                loadSeriesCatalog(pageNumber = state.seriesNextPage, isPagingRequest = true)
            }
        }
    }

    fun onItemFocused(itemKey: String) {
        val state = _uiState.value
        val mode = itemMode(state, itemKey)
        val normalizedKey = preferredDetailsUrl(itemKey) ?: itemKey
        savedStateHandle[FOCUS_KEY] = normalizedKey
        _focusState.update { focus ->
            focus.copy(
                rememberedItemKeyByMode = focus.rememberedItemKeyByMode + (mode to normalizedKey),
                selectedItemKey = if (mode == state.selectedMode) normalizedKey else focus.selectedItemKey,
            )
        }
    }

    fun onNavItemSelected(item: NavItem) {
        _selectedNavItem.update { item }
    }

    fun onItemWatched(detailsUrl: String) {
        onItemWatchedStateChanged(detailsUrl, isWatched = true)
    }

    fun onItemWatchedStateChanged(detailsUrl: String, isWatched: Boolean) {
        _uiState.update { state ->
            val updatedItems = state.items.updateWatched(detailsUrl, isWatched)
            val updatedFavoriteItems = state.favoriteItems.updateWatched(detailsUrl, isWatched)
            val updatedMovieItems = state.movieItems.updateWatched(detailsUrl, isWatched)
            val updatedSeriesItems = state.seriesItems.updateWatched(detailsUrl, isWatched)
            state.copy(
                items = updatedItems,
                favoriteItems = updatedFavoriteItems,
                movieItems = updatedMovieItems,
                seriesItems = updatedSeriesItems,
                allNewModeState = state.allNewModeState.updateItems(updatedItems),
                favoritesModeState = state.favoritesModeState.updateItems(updatedFavoriteItems),
                moviesModeState = state.moviesModeState.updateItems(updatedMovieItems),
                seriesModeState = state.seriesModeState.updateItems(updatedSeriesItems),
            ).resolveSelection()
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
            ).resolveSelection()
        }
        if (shouldPersistFallback) preferencesStore.writeHomeSelectedFeedMode(HomeFeedMode.AllNew)
        if (started && isVisible && _uiState.value.favoritesModeState !is HomeModeContentState.Content) {
            loadFavoriteReleases()
        }
    }

    fun onHomeMenuLabelsVisibilityChanged(isVisible: Boolean) {
        preferencesStore.writeHomeMenuLabelsEnabled(isVisible)
        _uiState.update { it.copy(isHomeMenuLabelsEnabled = isVisible) }
    }

    fun onFavoriteContentInvalidated() {
        if (!started) return
        loadFavoriteReleases()
    }

    fun onFavoriteStateChanged(detailsUrl: String, isFavorite: Boolean) {
        if (!started) return
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
            ).resolveSelection()
        }
        loadFavoriteReleases(retainVisibleItemsOnFailure = true)
    }

    fun onModeSelected(mode: HomeFeedMode) {
        _uiState.update { state ->
            if (mode == state.selectedMode || mode !in state.availableModes) state
            else {
                preferencesStore.writeHomeSelectedFeedMode(mode)
                val resolved = state.copy(selectedMode = mode).resolveSelection()
                updateFocusFromResolvedState(resolved)
                resolved
            }
        }
    }

    private fun loadPage(pageNumber: Int, isPagingRequest: Boolean) {
        if (!isPagingRequest && pageNumber == 1) {
            lastAllNewRefreshAt = clock()
        }
        if (!isPagingRequest) {
            allNewLoadJob?.cancel()
        }
        _uiState.update { state ->
            state.copy(
                isInitialLoading = !isPagingRequest,
                isPaging = isPagingRequest,
                fullScreenErrorMessage = if (isPagingRequest) state.fullScreenErrorMessage else null,
                pagingErrorMessage = null,
                allNewModeState = if (isPagingRequest) state.allNewModeState else HomeModeContentState.Loading,
            )
        }
        allNewLoadJob = viewModelScope.launch(ioDispatcher) {
            when (val result = repository.loadPage(pageNumber)) {
                is PageState.Content -> {
                    val updatedItems = if (result.isAppend) {
                        (_uiState.value.items + result.items).distinctBy { it.detailsUrl }
                    } else {
                        result.items
                    }
                    _uiState.update { state ->
                        state.copy(
                            items = updatedItems,
                            isInitialLoading = false,
                            isPaging = false,
                            fullScreenErrorMessage = null,
                            pagingErrorMessage = result.pagingErrorMessage,
                            nextPage = result.pageNumber + 1,
                            hasNextPage = result.hasNextPage,
                            allNewModeState = HomeModeContentState.Content(updatedItems),
                        ).resolveSelection()
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
                        ).resolveSelection()
                    }
                }
            }
        }
    }

    private fun loadFavoriteReleases(
        pageNumber: Int = 1,
        isPagingRequest: Boolean = false,
        retainVisibleItemsOnFailure: Boolean = false,
    ) {
        val requestToken = favoriteRequestToken + 1
        favoriteRequestToken = requestToken
        if (!isPagingRequest) {
            favoriteLoadJob?.cancel()
        }
        _uiState.update { state ->
            state.copy(
                isFavoritesPaging = isPagingRequest,
                favoritesPagingErrorMessage = null,
                favoritesModeState = if (isPagingRequest || retainVisibleItemsOnFailure) {
                    state.favoritesModeState
                } else {
                    HomeModeContentState.Loading
                },
            )
        }
        favoriteLoadJob = viewModelScope.launch(ioDispatcher) {
            val result = repository.loadFavoriteReleases(pageNumber)
            if (favoriteRequestToken != requestToken) return@launch
            _uiState.update { state ->
                when (result) {
                    is FavoriteReleasesResult.Success -> {
                        val updatedItems = if (isPagingRequest) {
                            (state.favoriteItems + result.items).distinctBy { it.detailsUrl }
                        } else {
                            result.items
                        }
                        val favoriteState = if (updatedItems.isEmpty()) {
                            HomeModeContentState.Empty
                        } else {
                            HomeModeContentState.Content(updatedItems)
                        }
                        state.copy(
                            favoriteItems = updatedItems,
                            favoritesModeState = favoriteState,
                            isFavoritesPaging = false,
                            favoritesPagingErrorMessage = null,
                            favoritesNextPage = result.pageNumber + 1,
                            favoritesHasNextPage = result.hasNextPage,
                        ).resolveSelection()
                    }
                    is FavoriteReleasesResult.Unavailable -> {
                        if (retainVisibleItemsOnFailure) {
                            return@update state.copy(isFavoritesPaging = false).resolveSelection()
                        }
                        if (isPagingRequest && state.favoriteItems.isNotEmpty()) {
                            return@update state.copy(
                                isFavoritesPaging = false,
                                favoritesPagingErrorMessage = result.message ?: "Не удалось загрузить избранное",
                            ).resolveSelection()
                        }
                        val favoriteState = when {
                            result.message.isNullOrBlank() -> HomeModeContentState.Error("Не удалось загрузить избранное")
                            result.message.contains("Войдите", ignoreCase = true) -> HomeModeContentState.LoginRequired(result.message)
                            else -> HomeModeContentState.Error(result.message)
                        }
                        state.copy(
                            favoriteItems = emptyList(),
                            favoritesModeState = favoriteState,
                            isFavoritesPaging = false,
                            favoritesPagingErrorMessage = null,
                        ).resolveSelection()
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
                        ).resolveSelection()
                    }
                }
                is PageState.Error -> {
                    _uiState.update { state ->
                        if (isPagingRequest && state.movieItems.isNotEmpty()) {
                            state.copy(
                                isMoviesPaging = false,
                                moviesPagingErrorMessage = result.message,
                            ).resolveSelection()
                        } else {
                            state.copy(
                                movieItems = emptyList(),
                                isMoviesPaging = false,
                                moviesPagingErrorMessage = null,
                                moviesModeState = HomeModeContentState.Error(result.message),
                            ).resolveSelection()
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
                        ).resolveSelection()
                    }
                }
                is PageState.Error -> {
                    _uiState.update { state ->
                        if (isPagingRequest && state.seriesItems.isNotEmpty()) {
                            state.copy(
                                isSeriesPaging = false,
                                seriesPagingErrorMessage = result.message,
                            ).resolveSelection()
                        } else {
                            state.copy(
                                seriesItems = emptyList(),
                                isSeriesPaging = false,
                                seriesPagingErrorMessage = null,
                                seriesModeState = HomeModeContentState.Error(result.message),
                            ).resolveSelection()
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

private fun HomeUiState.withResolvedHomeSelection(
    focusState: HomeFocusState = HomeFocusState(
        rememberedItemKeyByMode = rememberedItemKeyByMode,
        selectedItemKey = selectedItemKey,
    ),
): HomeUiState {
    val resolvedRails = if (rails.matchesHomeRailInputs(this)) {
        rails
    } else {
        buildHomeRails(
            items = items,
            favoriteItems = favoriteItems,
            movieItems = movieItems,
            seriesItems = seriesItems,
            isFavoritesRailVisible = isFavoritesRailVisible,
        )
    }
    val normalizedSelectedMode = if (selectedMode in availableModes) selectedMode else HomeFeedMode.AllNew
    val activeItems = itemsForMode(normalizedSelectedMode)
    val preferredKey = focusState.rememberedItemKeyByMode[normalizedSelectedMode]
        ?: preferredDetailsUrl(focusState.selectedItemKey)
        ?: rememberedItemKeyByMode[normalizedSelectedMode]
        ?: preferredDetailsUrl(selectedItemKey)
    val selectedItem = activeItems.firstOrNull { it.detailsUrl == preferredKey } ?: activeItems.firstOrNull()
    return copy(
        selectedMode = normalizedSelectedMode,
        availableModes = availableModes(isFavoritesRailVisible),
        rails = resolvedRails,
        rememberedItemKeyByMode = focusState.rememberedItemKeyByMode
            .withDefaultRememberedKeys(items, favoriteItems, movieItems, seriesItems),
        selectedItem = selectedItem,
        selectedItemKey = selectedItem?.detailsUrl,
    )
}

private fun List<ReleaseSummary>.updateWatched(detailsUrl: String, isWatched: Boolean): List<ReleaseSummary> =
    if (none { it.detailsUrl == detailsUrl }) this
    else map { if (it.detailsUrl == detailsUrl) it.copy(isWatched = isWatched) else it }

private fun List<HomeContentRail>.matchesHomeRailInputs(state: HomeUiState): Boolean {
    var index = 0

    if (!matchesRail(index, HOME_RAIL_ALL_NEW, state.items)) return false
    if (state.items.isNotEmpty()) index += 1

    if (state.isFavoritesRailVisible) {
        if (!matchesRail(index, HOME_RAIL_FAVORITES, state.favoriteItems)) return false
        if (state.favoriteItems.isNotEmpty()) index += 1
    }

    if (!matchesRail(index, HOME_RAIL_MOVIES, state.movieItems)) return false
    if (state.movieItems.isNotEmpty()) index += 1

    if (!matchesRail(index, HOME_RAIL_SERIES, state.seriesItems)) return false
    if (state.seriesItems.isNotEmpty()) index += 1

    return index == size
}

private fun List<HomeContentRail>.matchesRail(index: Int, railId: String, items: List<ReleaseSummary>): Boolean {
    if (items.isEmpty()) return true
    val rail = getOrNull(index) ?: return false
    return rail.id == railId && rail.items === items
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
    listOf(HomeFeedMode.AllNew, HomeFeedMode.Favorites, HomeFeedMode.Movies, HomeFeedMode.Series)

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
