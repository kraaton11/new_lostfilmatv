package com.kraat.lostfilmnewtv.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.FavoriteSeriesResult
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.data.repository.FavoritesRepository
import com.kraat.lostfilmnewtv.data.poster.TmdbEnrichmentService
import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import com.kraat.lostfilmnewtv.updates.AppUpdateCoordinator
import com.kraat.lostfilmnewtv.updates.SavedAppUpdate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val FOCUS_KEY = "home.focused_item_key"
private const val HOME_RESUME_REFRESH_INTERVAL_MS = 30 * 60 * 1000L
private val favoriteSeriesSlugRegex = Regex("""/series/([^/]+)""")

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: LostFilmRepository,
    private val favoritesRepository: FavoritesRepository,
    private val tmdbEnrichmentService: TmdbEnrichmentService,
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

    private val initialFavoriteSeriesModeVisible: Boolean
        get() = preferencesStore.readHomeFavoriteSeriesEnabled()

    private val initialMoviesModeVisible: Boolean
        get() = preferencesStore.readHomeMoviesEnabled()

    private val initialSeriesModeVisible: Boolean
        get() = preferencesStore.readHomeSeriesEnabled()

    private val initialHomeMenuLabelsEnabled: Boolean
        get() = preferencesStore.readHomeMenuLabelsEnabled()

    private val initialUiState = HomeUiState(
        selectedItemKey = savedStateHandle[FOCUS_KEY],
        isInitialLoading = true,
        selectedMode = resolvedInitialMode(
            initialSelectedMode = initialSelectedMode,
            isFavoritesVisible = initialFavoritesRailVisible,
            isFavoriteSeriesVisible = initialFavoriteSeriesModeVisible,
            isMoviesVisible = initialMoviesModeVisible,
            isSeriesVisible = initialSeriesModeVisible,
        ),
        availableModes = availableModes(
            isFavoritesVisible = initialFavoritesRailVisible,
            isFavoriteSeriesVisible = initialFavoriteSeriesModeVisible,
            isMoviesVisible = initialMoviesModeVisible,
            isSeriesVisible = initialSeriesModeVisible,
        ),
        allNewModeState = HomeModeContentState.Loading,
        favoritesModeState = if (initialFavoritesRailVisible) {
            HomeModeContentState.Loading
        } else {
            HomeModeContentState.Empty
        },
        favoriteSeriesModeState = HomeModeContentState.Loading,
        seriesModeState = HomeModeContentState.Loading,
        isFavoritesRailVisible = initialFavoritesRailVisible,
        isFavoriteSeriesModeVisible = initialFavoriteSeriesModeVisible,
        isMoviesModeVisible = initialMoviesModeVisible,
        isSeriesModeVisible = initialSeriesModeVisible,
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
    private var favoriteSeriesLoadJob: Job? = null
    private var moviesLoadJob: Job? = null
    private var seriesLoadJob: Job? = null
    private var saveFocusJob: Job? = null
    private var lastAllNewRefreshAt = 0L

    fun onStart() {
        if (started) {
            onResume()
            return
        }
        started = true
        if (initialSelectedMode !in _uiState.value.availableModes) {
            preferencesStore.writeHomeSelectedFeedMode(HomeFeedMode.AllNew)
        }
        loadPage(pageNumber = 1, isPagingRequest = false)
        if (_uiState.value.isMoviesModeVisible) loadMovies(pageNumber = 1, isPagingRequest = false)
        if (_uiState.value.isSeriesModeVisible) loadSeriesCatalog(pageNumber = 1, isPagingRequest = false)
        if (_uiState.value.isFavoritesRailVisible) loadFavoriteReleases()
        if (_uiState.value.isFavoriteSeriesModeVisible) loadFavoriteSeries()
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
                val md = _uiState.value.modeData(HomeFeedMode.Favorites)
                if (md.pagingErrorMessage == null ||
                    md.isPaging ||
                    !md.hasNextPage ||
                    md.contentState !is HomeModeContentState.Content
                ) {
                    return
                }
                loadFavoriteReleases(pageNumber = md.nextPage, isPagingRequest = true)
            }
            HomeFeedMode.Movies -> {
                loadMovies(pageNumber = 1, isPagingRequest = false)
                return
            }
            HomeFeedMode.FavoriteSeries -> {
                loadFavoriteSeries()
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
        val mode = state.selectedMode
        val md = state.modeData(mode)
        when (mode) {
            HomeFeedMode.AllNew -> {
                if (md.pagingErrorMessage == null || md.isPaging || state.isInitialLoading || !md.hasNextPage) return
                loadPage(pageNumber = md.nextPage, isPagingRequest = true)
            }
            HomeFeedMode.Movies -> {
                if (md.pagingErrorMessage == null || md.isPaging || !md.hasNextPage) return
                loadMovies(pageNumber = md.nextPage, isPagingRequest = true)
            }
            HomeFeedMode.Favorites -> {
                if (md.pagingErrorMessage == null ||
                    md.isPaging ||
                    !md.hasNextPage ||
                    md.contentState !is HomeModeContentState.Content
                ) {
                    return
                }
                loadFavoriteReleases(pageNumber = md.nextPage, isPagingRequest = true)
            }
            HomeFeedMode.FavoriteSeries -> Unit
            HomeFeedMode.Series -> {
                if (md.pagingErrorMessage == null || md.isPaging || !md.hasNextPage) return
                loadSeriesCatalog(pageNumber = md.nextPage, isPagingRequest = true)
            }
        }
    }

    fun onEndReached() {
        val state = _uiState.value
        val mode = state.selectedMode
        val md = state.modeData(mode)
        when (mode) {
            HomeFeedMode.AllNew -> {
                if (state.isInitialLoading || md.isPaging || !md.hasNextPage || state.fullScreenErrorMessage != null) return
                loadPage(pageNumber = md.nextPage, isPagingRequest = true)
            }
            HomeFeedMode.Movies -> {
                if (md.isPaging || !md.hasNextPage || md.contentState !is HomeModeContentState.Content) return
                loadMovies(pageNumber = md.nextPage, isPagingRequest = true)
            }
            HomeFeedMode.Favorites -> {
                if (md.isPaging ||
                    !md.hasNextPage ||
                    md.contentState !is HomeModeContentState.Content
                ) {
                    return
                }
                loadFavoriteReleases(pageNumber = md.nextPage, isPagingRequest = true)
            }
            HomeFeedMode.FavoriteSeries -> Unit
            HomeFeedMode.Series -> {
                if (md.isPaging || !md.hasNextPage || md.contentState !is HomeModeContentState.Content) return
                loadSeriesCatalog(pageNumber = md.nextPage, isPagingRequest = true)
            }
        }
    }

    fun onItemFocused(itemKey: String) {
        val state = _uiState.value
        val mode = itemMode(state, itemKey)
        val normalizedKey = preferredDetailsUrl(itemKey) ?: itemKey
        _focusState.update { focus ->
            focus.copy(
                rememberedItemKeyByMode = focus.rememberedItemKeyByMode + (mode to normalizedKey),
                selectedItemKey = if (mode == state.selectedMode) normalizedKey else focus.selectedItemKey,
            )
        }

        saveFocusJob?.cancel()
        saveFocusJob = viewModelScope.launch(ioDispatcher) {
            delay(500)
            savedStateHandle[FOCUS_KEY] = normalizedKey
        }
    }

    fun onNavItemSelected(item: NavItem) {
        _selectedNavItem.update { item }
    }

    fun onItemWatchedStateChanged(detailsUrl: String, isWatched: Boolean) {
        _uiState.update { state ->
            state.updateModes(
                *HomeFeedMode.entries.map { mode ->
                    mode to { md: HomeModeData ->
                        val updatedItems = md.items.updateWatched(detailsUrl, isWatched)
                        md.copy(
                            items = updatedItems,
                            contentState = md.contentState.updateItems(updatedItems),
                        )
                    }
                }.toTypedArray(),
            ).resolveSelection()
        }
    }

    fun onFavoritesRailVisibilityChanged(isVisible: Boolean) {
        onHomeModeVisibilityChanged(HomeFeedMode.Favorites, isVisible)
    }

    fun onHomeModeVisibilityChanged(mode: HomeFeedMode, isVisible: Boolean) {
        if (mode == HomeFeedMode.AllNew) return
        if (_uiState.value.modeData(mode).isVisible == isVisible) return

        when (mode) {
            HomeFeedMode.Favorites -> preferencesStore.writeHomeFavoritesRailEnabled(isVisible)
            HomeFeedMode.FavoriteSeries -> preferencesStore.writeHomeFavoriteSeriesEnabled(isVisible)
            HomeFeedMode.Movies -> preferencesStore.writeHomeMoviesEnabled(isVisible)
            HomeFeedMode.Series -> preferencesStore.writeHomeSeriesEnabled(isVisible)
            HomeFeedMode.AllNew -> Unit
        }

        favoriteLoadJob?.cancel()
        favoriteSeriesLoadJob?.cancel()
        if (mode == HomeFeedMode.Movies) moviesLoadJob?.cancel()
        if (mode == HomeFeedMode.Series) seriesLoadJob?.cancel()
        val shouldPersistFallback = _uiState.value.selectedMode == mode && !isVisible
        _uiState.update { state ->
            val selectedMode = if (state.selectedMode == mode && !isVisible) HomeFeedMode.AllNew else state.selectedMode
            state.copy(selectedMode = selectedMode).updateMode(mode) { md ->
                if (isVisible) {
                    md.copy(isVisible = true)
                } else {
                    md.copy(
                        items = emptyList(),
                        contentState = HomeModeContentState.Empty,
                        isVisible = false,
                    )
                }
            }.resolveSelection()
        }
        if (shouldPersistFallback) preferencesStore.writeHomeSelectedFeedMode(HomeFeedMode.AllNew)
        if (started && isVisible) {
            when (mode) {
                HomeFeedMode.Favorites -> loadFavoriteReleases()
                HomeFeedMode.FavoriteSeries -> loadFavoriteSeries()
                HomeFeedMode.Movies -> loadMovies(pageNumber = 1, isPagingRequest = false)
                HomeFeedMode.Series -> loadSeriesCatalog(pageNumber = 1, isPagingRequest = false)
                HomeFeedMode.AllNew -> Unit
            }
        }
    }

    fun onHomeMenuLabelsVisibilityChanged(isVisible: Boolean) {
        preferencesStore.writeHomeMenuLabelsEnabled(isVisible)
        _uiState.update { it.copy(isHomeMenuLabelsEnabled = isVisible) }
    }

    fun onFavoriteContentInvalidated() {
        if (!started) return
        if (_uiState.value.isFavoritesRailVisible) loadFavoriteReleases()
        if (_uiState.value.isFavoriteSeriesModeVisible) loadFavoriteSeries()
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
            state.updateMode(HomeFeedMode.Favorites) { md ->
                md.copy(
                    items = updatedFavoriteItems,
                    contentState = md.contentState.optimisticallyUpdateFavoriteItems(updatedFavoriteItems),
                )
            }.resolveSelection()
        }
        if (_uiState.value.isFavoritesRailVisible) loadFavoriteReleases(retainVisibleItemsOnFailure = true)
        if (_uiState.value.isFavoriteSeriesModeVisible) loadFavoriteSeries()
    }

    fun onModeSelected(mode: HomeFeedMode) {
        _uiState.update { state ->
            if (mode == state.selectedMode || mode !in state.availableModes) state
            else {
                preferencesStore.writeHomeSelectedFeedMode(mode)
                // Сбрасываем запомненную позицию фокуса для целевого режима,
                // чтобы при переключении фокус всегда вставал на первую карточку.
                _focusState.update { focus ->
                    focus.copy(
                        rememberedItemKeyByMode = focus.rememberedItemKeyByMode - mode,
                        selectedItemKey = null,
                    )
                }
                val resolved = state.copy(selectedMode = mode).resolveSelection()
                updateFocusFromResolvedState(resolved)
                resolved
            }
        }
    }

    private fun loadPage(pageNumber: Int, isPagingRequest: Boolean) {
        if (pageNumber == 1 && !isPagingRequest) {
            observeNewReleases()
            return
        }
        loadPageDirect(pageNumber, isPagingRequest)
    }

    private fun observeNewReleases() {
        allNewLoadJob?.cancel()
        _uiState.update { state ->
            state.copy(
                isInitialLoading = true,
                fullScreenErrorMessage = null,
            ).updateMode(HomeFeedMode.AllNew) { md ->
                md.copy(
                    isPaging = false,
                    pagingErrorMessage = null,
                    contentState = HomeModeContentState.Loading,
                )
            }
        }
        allNewLoadJob = viewModelScope.launch(ioDispatcher) {
            var hadCacheEmission = false
            try {
                repository.observeNewReleases(1).collect { result ->
                    when (result) {
                        is PageState.Content -> {
                            val isStale = result.isStale
                            _uiState.update { state ->
                                state.copy(
                                    isInitialLoading = false,
                                    fullScreenErrorMessage = null,
                                ).updateMode(HomeFeedMode.AllNew) { md ->
                                    md.copy(
                                        items = result.items,
                                        isPaging = false,
                                        pagingErrorMessage = result.pagingErrorMessage,
                                        nextPage = result.pageNumber + 1,
                                        hasNextPage = result.hasNextPage,
                                        contentState = HomeModeContentState.Content(result.items),
                                    )
                                }.resolveSelection()
                            }
                            if (isStale) {
                                hadCacheEmission = true
                            } else {
                                lastAllNewRefreshAt = clock()
                                homeChannelSyncManager.syncNow()
                            }
                        }
                        is PageState.Error -> {
                            if (hadCacheEmission) {
                                _uiState.update { state ->
                                    state.copy(
                                        isInitialLoading = false,
                                    ).updateMode(HomeFeedMode.AllNew) { md ->
                                        md.copy(
                                            isPaging = false,
                                            pagingErrorMessage = result.message,
                                        )
                                    }.resolveSelection()
                                }
                            } else {
                                _uiState.update { state ->
                                    state.copy(
                                        isInitialLoading = false,
                                        fullScreenErrorMessage = result.message,
                                    ).updateMode(HomeFeedMode.AllNew) { md ->
                                        md.copy(
                                            isPaging = false,
                                            pagingErrorMessage = null,
                                            contentState = HomeModeContentState.Error(result.message),
                                        )
                                    }.resolveSelection()
                                }
                            }
                        }
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            }
        }
    }

    private fun loadPageDirect(pageNumber: Int, isPagingRequest: Boolean) {
        if (!isPagingRequest) {
            allNewLoadJob?.cancel()
        }
        _uiState.update { state ->
            state.copy(
                isInitialLoading = !isPagingRequest,
                fullScreenErrorMessage = if (isPagingRequest) state.fullScreenErrorMessage else null,
            ).updateMode(HomeFeedMode.AllNew) { md ->
                md.copy(
                    isPaging = isPagingRequest,
                    pagingErrorMessage = null,
                    contentState = if (isPagingRequest) md.contentState else HomeModeContentState.Loading,
                )
            }
        }
        allNewLoadJob = viewModelScope.launch(ioDispatcher) {
            when (val result = repository.loadPage(pageNumber)) {
                is PageState.Content -> {
                    val existingItems = _uiState.value.items
                    val updatedItems = if (result.isAppend) {
                        (existingItems + result.items).distinctBy { it.detailsUrl }
                    } else {
                        result.items
                    }
                    _uiState.update { state ->
                        state.copy(
                            isInitialLoading = false,
                            fullScreenErrorMessage = null,
                        ).updateMode(HomeFeedMode.AllNew) { md ->
                            md.copy(
                                items = updatedItems,
                                isPaging = false,
                                pagingErrorMessage = result.pagingErrorMessage,
                                nextPage = result.pageNumber + 1,
                                hasNextPage = result.hasNextPage,
                                contentState = HomeModeContentState.Content(updatedItems),
                            )
                        }.resolveSelection()
                    }
                    if (!isPagingRequest) {
                        homeChannelSyncManager.syncNow()
                    }
                }
                is PageState.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            isInitialLoading = false,
                            fullScreenErrorMessage = result.message,
                        ).updateMode(HomeFeedMode.AllNew) { md ->
                            md.copy(
                                isPaging = false,
                                pagingErrorMessage = null,
                                contentState = HomeModeContentState.Error(result.message),
                            )
                        }.resolveSelection()
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
            state.updateMode(HomeFeedMode.Favorites) { md ->
                md.copy(
                    isPaging = isPagingRequest,
                    pagingErrorMessage = null,
                    contentState = if (isPagingRequest || retainVisibleItemsOnFailure) {
                        md.contentState
                    } else {
                        HomeModeContentState.Loading
                    },
                )
            }
        }
        favoriteLoadJob = viewModelScope.launch(ioDispatcher) {
            try {
                favoritesRepository.observeFavoriteReleases(pageNumber).collect { result ->
                    if (favoriteRequestToken != requestToken) {
                        return@collect
                    }
                    _uiState.update { state ->
                        val md = state.modeData(HomeFeedMode.Favorites)
                        when (result) {
                            is FavoriteReleasesResult.Partial -> {
                                val updatedItems = if (isPagingRequest) {
                                    (md.items + result.items).distinctBy { it.detailsUrl }
                                } else {
                                    result.items
                                }
                                val favoriteState = if (updatedItems.isEmpty()) {
                                    md.contentState // keep Loading while streaming
                                } else {
                                    HomeModeContentState.Content(updatedItems)
                                }
                                state.updateMode(HomeFeedMode.Favorites) {
                                    it.copy(
                                        items = updatedItems,
                                        contentState = favoriteState,
                                        isPaging = false,
                                    )
                                }.resolveSelection()
                            }
                            is FavoriteReleasesResult.Success -> {
                                val updatedItems = if (isPagingRequest) {
                                    (md.items + result.items).distinctBy { it.detailsUrl }
                                } else {
                                    result.items
                                }
                                val favoriteState = if (updatedItems.isEmpty()) {
                                    HomeModeContentState.Empty
                                } else {
                                    HomeModeContentState.Content(updatedItems)
                                }
                                state.copy(
                                    favoriteSeriesCount = result.favoriteSeriesCount,
                                ).updateMode(HomeFeedMode.Favorites) {
                                    it.copy(
                                        items = updatedItems,
                                        contentState = favoriteState,
                                        isPaging = false,
                                        pagingErrorMessage = null,
                                        nextPage = result.pageNumber + 1,
                                        hasNextPage = result.hasNextPage,
                                    )
                                }.resolveSelection()
                            }
                            is FavoriteReleasesResult.Unavailable -> {
                                if (retainVisibleItemsOnFailure) {
                                    return@update state.updateMode(HomeFeedMode.Favorites) {
                                        it.copy(isPaging = false)
                                    }.resolveSelection()
                                }
                                if (isPagingRequest && md.items.isNotEmpty()) {
                                    return@update state.updateMode(HomeFeedMode.Favorites) {
                                        it.copy(
                                            isPaging = false,
                                            pagingErrorMessage = result.message ?: "Не удалось загрузить избранное",
                                        )
                                    }.resolveSelection()
                                }
                                val favoriteState = when {
                                    result.message.isNullOrBlank() -> HomeModeContentState.Error("Не удалось загрузить избранное")
                                    result.message.contains("Войдите", ignoreCase = true) -> HomeModeContentState.LoginRequired(result.message)
                                    else -> HomeModeContentState.Error(result.message)
                                }
                                state.copy(
                                    favoriteSeriesCount = null,
                                ).updateMode(HomeFeedMode.Favorites) {
                                    it.copy(
                                        items = emptyList(),
                                        contentState = favoriteState,
                                        isPaging = false,
                                        pagingErrorMessage = null,
                                    )
                                }.resolveSelection()
                            }
                        }
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            }
        }
    }

    private fun loadFavoriteSeries() {
        favoriteSeriesLoadJob?.cancel()
        _uiState.update { state ->
            state.updateMode(HomeFeedMode.FavoriteSeries) { md ->
                md.copy(contentState = HomeModeContentState.Loading)
            }
        }
        favoriteSeriesLoadJob = viewModelScope.launch(ioDispatcher) {
            when (val result = favoritesRepository.loadFavoriteSeries()) {
                is FavoriteSeriesResult.Success -> {
                    val seriesState = if (result.items.isEmpty()) {
                        HomeModeContentState.Empty
                    } else {
                        HomeModeContentState.Content(result.items)
                    }
                    _uiState.update { state ->
                        state.copy(
                            favoriteSeriesCount = result.items.size,
                        ).updateMode(HomeFeedMode.FavoriteSeries) { md ->
                            md.copy(
                                items = result.items,
                                contentState = seriesState,
                            )
                        }.resolveSelection()
                    }
                }
                is FavoriteSeriesResult.Unavailable -> {
                    val seriesState = when {
                        result.message.isNullOrBlank() -> HomeModeContentState.Error("Не удалось загрузить избранные сериалы")
                        result.message.contains("Войдите", ignoreCase = true) -> HomeModeContentState.LoginRequired(result.message)
                        else -> HomeModeContentState.Error(result.message)
                    }
                    _uiState.update { state ->
                        state.updateMode(HomeFeedMode.FavoriteSeries) { md ->
                            md.copy(
                                items = emptyList(),
                                contentState = seriesState,
                            )
                        }.resolveSelection()
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
            state.updateMode(HomeFeedMode.Movies) { md ->
                md.copy(
                    isPaging = isPagingRequest,
                    pagingErrorMessage = null,
                    contentState = if (isPagingRequest) md.contentState else HomeModeContentState.Loading,
                )
            }
        }
        moviesLoadJob = viewModelScope.launch(ioDispatcher) {
            when (val result = repository.loadMovies(pageNumber)) {
                is PageState.Content -> {
                    val existingItems = _uiState.value.movieItems
                    val updatedItems = if (isPagingRequest) {
                        (existingItems + result.items).distinctBy { it.detailsUrl }
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
                        state.updateMode(HomeFeedMode.Movies) { md ->
                            md.copy(
                                items = updatedItems,
                                contentState = moviesState,
                                isPaging = false,
                                pagingErrorMessage = null,
                                nextPage = result.pageNumber + 1,
                                hasNextPage = result.hasNextPage,
                            )
                        }.resolveSelection()
                    }
                }
                is PageState.Error -> {
                    _uiState.update { state ->
                        val md = state.modeData(HomeFeedMode.Movies)
                        if (isPagingRequest && md.items.isNotEmpty()) {
                            state.updateMode(HomeFeedMode.Movies) {
                                it.copy(
                                    isPaging = false,
                                    pagingErrorMessage = result.message,
                                )
                            }.resolveSelection()
                        } else {
                            state.updateMode(HomeFeedMode.Movies) {
                                it.copy(
                                    items = emptyList(),
                                    isPaging = false,
                                    pagingErrorMessage = null,
                                    contentState = HomeModeContentState.Error(result.message),
                                )
                            }.resolveSelection()
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
            state.updateMode(HomeFeedMode.Series) { md ->
                md.copy(
                    isPaging = isPagingRequest,
                    pagingErrorMessage = null,
                    contentState = if (isPagingRequest) md.contentState else HomeModeContentState.Loading,
                )
            }
        }
        seriesLoadJob = viewModelScope.launch(ioDispatcher) {
            when (val result = repository.loadSeriesCatalog(pageNumber)) {
                is PageState.Content -> {
                    val existingItems = _uiState.value.seriesItems
                    val updatedItems = if (isPagingRequest) {
                        (existingItems + result.items).distinctBy { it.detailsUrl }
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
                        state.updateMode(HomeFeedMode.Series) { md ->
                            md.copy(
                                items = updatedItems,
                                contentState = seriesState,
                                isPaging = false,
                                pagingErrorMessage = null,
                                nextPage = result.pageNumber + 1,
                                hasNextPage = result.hasNextPage,
                            )
                        }.resolveSelection()
                    }
                }
                is PageState.Error -> {
                    _uiState.update { state ->
                        val md = state.modeData(HomeFeedMode.Series)
                        if (isPagingRequest && md.items.isNotEmpty()) {
                            state.updateMode(HomeFeedMode.Series) {
                                it.copy(
                                    isPaging = false,
                                    pagingErrorMessage = result.message,
                                )
                            }.resolveSelection()
                        } else {
                            state.updateMode(HomeFeedMode.Series) {
                                it.copy(
                                    items = emptyList(),
                                    isPaging = false,
                                    pagingErrorMessage = null,
                                    contentState = HomeModeContentState.Error(result.message),
                                )
                            }.resolveSelection()
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
            favoriteSeriesItems = favoriteSeriesItems,
            movieItems = movieItems,
            seriesItems = seriesItems,
            isFavoritesRailVisible = isFavoritesRailVisible,
            isFavoriteSeriesVisible = isFavoriteSeriesModeVisible,
            isMoviesVisible = isMoviesModeVisible,
            isSeriesVisible = isSeriesModeVisible,
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
        availableModes = availableModes(
            isFavoritesVisible = isFavoritesRailVisible,
            isFavoriteSeriesVisible = isFavoriteSeriesModeVisible,
            isMoviesVisible = isMoviesModeVisible,
            isSeriesVisible = isSeriesModeVisible,
        ),
        rails = resolvedRails,
        rememberedItemKeyByMode = focusState.rememberedItemKeyByMode
            .withDefaultRememberedKeys(this),
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

    if (state.isFavoriteSeriesModeVisible) {
        if (!matchesRail(index, HOME_RAIL_FAVORITE_SERIES, state.favoriteSeriesItems)) return false
        if (state.favoriteSeriesItems.isNotEmpty()) index += 1
    }

    if (state.isMoviesModeVisible) {
        if (!matchesRail(index, HOME_RAIL_MOVIES, state.movieItems)) return false
        if (state.movieItems.isNotEmpty()) index += 1
    }

    if (state.isSeriesModeVisible) {
        if (!matchesRail(index, HOME_RAIL_SERIES, state.seriesItems)) return false
        if (state.seriesItems.isNotEmpty()) index += 1
    }

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

private fun availableModes(
    isFavoritesVisible: Boolean,
    isFavoriteSeriesVisible: Boolean,
    isMoviesVisible: Boolean,
    isSeriesVisible: Boolean,
): List<HomeFeedMode> = buildList {
    add(HomeFeedMode.AllNew)
    if (isFavoritesVisible) add(HomeFeedMode.Favorites)
    if (isFavoriteSeriesVisible) add(HomeFeedMode.FavoriteSeries)
    if (isMoviesVisible) add(HomeFeedMode.Movies)
    if (isSeriesVisible) add(HomeFeedMode.Series)
}

private fun resolvedInitialMode(
    initialSelectedMode: HomeFeedMode,
    isFavoritesVisible: Boolean,
    isFavoriteSeriesVisible: Boolean,
    isMoviesVisible: Boolean,
    isSeriesVisible: Boolean,
): HomeFeedMode {
    return if (initialSelectedMode in availableModes(
            isFavoritesVisible = isFavoritesVisible,
            isFavoriteSeriesVisible = isFavoriteSeriesVisible,
            isMoviesVisible = isMoviesVisible,
            isSeriesVisible = isSeriesVisible,
        )
    ) {
        initialSelectedMode
    } else {
        HomeFeedMode.AllNew
    }
}

private fun isModeVisible(state: HomeUiState, mode: HomeFeedMode): Boolean {
    return when (mode) {
        HomeFeedMode.AllNew -> true
        HomeFeedMode.Favorites -> state.isFavoritesRailVisible
        HomeFeedMode.FavoriteSeries -> state.isFavoriteSeriesModeVisible
        HomeFeedMode.Movies -> state.isMoviesModeVisible
        HomeFeedMode.Series -> state.isSeriesModeVisible
    }
}

private fun itemMode(state: HomeUiState, itemKey: String): HomeFeedMode = when (railIdFromItemKey(itemKey)) {
    HOME_RAIL_FAVORITES -> HomeFeedMode.Favorites
    HOME_RAIL_FAVORITE_SERIES -> HomeFeedMode.FavoriteSeries
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
    state: HomeUiState,
): Map<HomeFeedMode, String> {
    val current = toMutableMap()
    for (mode in HomeFeedMode.entries) {
        if (mode !in current) {
            state.modeData(mode).items.firstOrNull()?.detailsUrl?.let { current[mode] = it }
        }
    }
    return current
}
