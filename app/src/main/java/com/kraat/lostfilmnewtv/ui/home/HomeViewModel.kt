package com.kraat.lostfilmnewtv.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.FavoriteSeriesResult
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
import kotlinx.coroutines.CancellationException
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
            HomeFeedMode.FavoriteSeries -> Unit
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
            HomeFeedMode.FavoriteSeries -> Unit
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

    fun onItemWatchedStateChanged(detailsUrl: String, isWatched: Boolean) {
        _uiState.update { state ->
            val updatedItems = state.items.updateWatched(detailsUrl, isWatched)
            val updatedFavoriteItems = state.favoriteItems.updateWatched(detailsUrl, isWatched)
            val updatedFavoriteSeriesItems = state.favoriteSeriesItems.updateWatched(detailsUrl, isWatched)
            val updatedMovieItems = state.movieItems.updateWatched(detailsUrl, isWatched)
            val updatedSeriesItems = state.seriesItems.updateWatched(detailsUrl, isWatched)
            state.copy(
                items = updatedItems,
                favoriteItems = updatedFavoriteItems,
                favoriteSeriesItems = updatedFavoriteSeriesItems,
                movieItems = updatedMovieItems,
                seriesItems = updatedSeriesItems,
                allNewModeState = state.allNewModeState.updateItems(updatedItems),
                favoritesModeState = state.favoritesModeState.updateItems(updatedFavoriteItems),
                favoriteSeriesModeState = state.favoriteSeriesModeState.updateItems(updatedFavoriteSeriesItems),
                moviesModeState = state.moviesModeState.updateItems(updatedMovieItems),
                seriesModeState = state.seriesModeState.updateItems(updatedSeriesItems),
            ).resolveSelection()
        }
    }

    fun onFavoritesRailVisibilityChanged(isVisible: Boolean) {
        onHomeModeVisibilityChanged(HomeFeedMode.Favorites, isVisible)
    }

    fun onHomeModeVisibilityChanged(mode: HomeFeedMode, isVisible: Boolean) {
        if (mode == HomeFeedMode.AllNew) return
        if (isModeVisible(_uiState.value, mode) == isVisible) return

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
            state.copy(
                selectedMode = selectedMode,
                isFavoritesRailVisible = if (mode == HomeFeedMode.Favorites) isVisible else state.isFavoritesRailVisible,
                isFavoriteSeriesModeVisible = if (mode == HomeFeedMode.FavoriteSeries) isVisible else state.isFavoriteSeriesModeVisible,
                isMoviesModeVisible = if (mode == HomeFeedMode.Movies) isVisible else state.isMoviesModeVisible,
                isSeriesModeVisible = if (mode == HomeFeedMode.Series) isVisible else state.isSeriesModeVisible,
                favoriteItems = if (mode == HomeFeedMode.Favorites && !isVisible) emptyList() else state.favoriteItems,
                favoriteSeriesItems = if (mode == HomeFeedMode.FavoriteSeries && !isVisible) emptyList() else state.favoriteSeriesItems,
                movieItems = if (mode == HomeFeedMode.Movies && !isVisible) emptyList() else state.movieItems,
                seriesItems = if (mode == HomeFeedMode.Series && !isVisible) emptyList() else state.seriesItems,
                favoritesModeState = if (mode == HomeFeedMode.Favorites && !isVisible) HomeModeContentState.Empty else state.favoritesModeState,
                favoriteSeriesModeState = if (mode == HomeFeedMode.FavoriteSeries && !isVisible) HomeModeContentState.Empty else state.favoriteSeriesModeState,
                moviesModeState = if (mode == HomeFeedMode.Movies && !isVisible) HomeModeContentState.Empty else state.moviesModeState,
                seriesModeState = if (mode == HomeFeedMode.Series && !isVisible) HomeModeContentState.Empty else state.seriesModeState,
            ).resolveSelection()
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
            state.copy(
                favoriteItems = updatedFavoriteItems,
                favoritesModeState = state.favoritesModeState.optimisticallyUpdateFavoriteItems(updatedFavoriteItems),
            ).resolveSelection()
        }
        if (_uiState.value.isFavoritesRailVisible) loadFavoriteReleases(retainVisibleItemsOnFailure = true)
        if (_uiState.value.isFavoriteSeriesModeVisible) loadFavoriteSeries()
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
        // For page 1, use stale-while-revalidate: показать кэш из Room мгновенно, а сеть
        // догрузить в фоне. На повторных запусках пользователь видит список за миллисекунды,
        // а не ждёт ~3–10 сек полного сетевого roundtrip. Пагинация (page > 1) идёт напрямую —
        // там кэш и так уже на экране, и нужна свежая догрузка.
        if (pageNumber == 1 && !isPagingRequest) {
            observeNewReleases()
            return
        }
        loadPageDirect(pageNumber, isPagingRequest)
    }

    /**
     * Stale-while-revalidate для первой страницы: первая эмиссия [observeNewReleases] — кэш
     * из Room (если есть) с `isStale=true`, вторая — свежий результат [loadPage]. Поведение
     * в HomeScreen: кэш рендерится сразу, скелетон не показывается; если кэша нет, остаётся
     * [HomeModeContentState.Loading] до свежей эмиссии.
     */
    private fun observeNewReleases() {
        allNewLoadJob?.cancel()
        _uiState.update { state ->
            state.copy(
                isInitialLoading = true,
                isPaging = false,
                fullScreenErrorMessage = null,
                pagingErrorMessage = null,
                allNewModeState = HomeModeContentState.Loading,
            )
        }
        allNewLoadJob = viewModelScope.launch(ioDispatcher) {
            // hadCacheEmission фиксирует, был ли показан кэш до ошибки сети. Если да —
            // items не стираем, только показываем pagingErrorMessage. Если кэша не было —
            // показываем fullScreenErrorMessage, как раньше.
            var hadCacheEmission = false
            try {
                repository.observeNewReleases(1).collect { result ->
                    when (result) {
                        is PageState.Content -> {
                            val isStale = result.isStale
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
                                ).resolveSelection()
                            }
                            if (isStale) {
                                hadCacheEmission = true
                            } else {
                                // lastAllNewRefreshAt обновляем только после успешной сетевой
                                // загрузки, не на stale — иначе 30-минутный троттлинг в onResume
                                // будет сбрасываться при показе любого кэша.
                                lastAllNewRefreshAt = clock()
                                // syncNow() — на свежей эмиссии, не на stale: каналы должны
                                // отражать актуальные данные, а не вчерашний кэш.
                                homeChannelSyncManager.syncNow()
                            }
                        }
                        is PageState.Error -> {
                            if (hadCacheEmission) {
                                // Кэш уже на экране — не стираем items, только сообщаем об ошибке.
                                _uiState.update { state ->
                                    state.copy(
                                        isInitialLoading = false,
                                        isPaging = false,
                                        pagingErrorMessage = result.message,
                                    ).resolveSelection()
                                }
                            } else {
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
            try {
                repository.observeFavoriteReleases(pageNumber).collect { result ->
                    if (favoriteRequestToken != requestToken) return@collect
                    _uiState.update { state ->
                        when (result) {
                            is FavoriteReleasesResult.Partial -> {
                                val updatedItems = if (isPagingRequest) {
                                    (state.favoriteItems + result.items).distinctBy { it.detailsUrl }
                                } else {
                                    result.items
                                }
                                val favoriteState = if (updatedItems.isEmpty()) {
                                    state.favoritesModeState // keep Loading while streaming
                                } else {
                                    HomeModeContentState.Content(updatedItems)
                                }
                                state.copy(
                                    favoriteItems = updatedItems,
                                    favoritesModeState = favoriteState,
                                    isFavoritesPaging = false,
                                ).resolveSelection()
                            }
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
                                    favoriteSeriesCount = result.favoriteSeriesCount,
                                    isFavoritesPaging = false,
                                    favoritesPagingErrorMessage = null,
                                    favoritesNextPage = result.pageNumber + 1,
                                    favoritesHasNextPage = result.hasNextPage,
                                ).resolveSelection()
                            }
                            is FavoriteReleasesResult.Unavailable -> {
                                if (retainVisibleItemsOnFailure) {
                                    _uiState.update { s -> s.copy(isFavoritesPaging = false).resolveSelection() }
                                    return@collect
                                }
                                if (isPagingRequest && state.favoriteItems.isNotEmpty()) {
                                    _uiState.update { s ->
                                        s.copy(
                                            isFavoritesPaging = false,
                                            favoritesPagingErrorMessage = result.message ?: "Не удалось загрузить избранное",
                                        ).resolveSelection()
                                    }
                                    return@collect
                                }
                                val favoriteState = when {
                                    result.message.isNullOrBlank() -> HomeModeContentState.Error("Не удалось загрузить избранное")
                                    result.message.contains("Войдите", ignoreCase = true) -> HomeModeContentState.LoginRequired(result.message)
                                    else -> HomeModeContentState.Error(result.message)
                                }
                                state.copy(
                                    favoriteItems = emptyList(),
                                    favoritesModeState = favoriteState,
                                    favoriteSeriesCount = null,
                                    isFavoritesPaging = false,
                                    favoritesPagingErrorMessage = null,
                                ).resolveSelection()
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
            state.copy(
                favoriteSeriesModeState = HomeModeContentState.Loading,
            )
        }
        favoriteSeriesLoadJob = viewModelScope.launch(ioDispatcher) {
            when (val result = repository.loadFavoriteSeries()) {
                is FavoriteSeriesResult.Success -> {
                    val seriesState = if (result.items.isEmpty()) {
                        HomeModeContentState.Empty
                    } else {
                        HomeModeContentState.Content(result.items)
                    }
                    _uiState.update { state ->
                        state.copy(
                            favoriteSeriesItems = result.items,
                            favoriteSeriesModeState = seriesState,
                            favoriteSeriesCount = result.items.size,
                        ).resolveSelection()
                    }
                }
                is FavoriteSeriesResult.Unavailable -> {
                    val seriesState = when {
                        result.message.isNullOrBlank() -> HomeModeContentState.Error("Не удалось загрузить избранные сериалы")
                        result.message.contains("Войдите", ignoreCase = true) -> HomeModeContentState.LoginRequired(result.message)
                        else -> HomeModeContentState.Error(result.message)
                    }
                    _uiState.update { state ->
                        state.copy(
                            favoriteSeriesItems = emptyList(),
                            favoriteSeriesModeState = seriesState,
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
            .withDefaultRememberedKeys(items, favoriteItems, favoriteSeriesItems, movieItems, seriesItems),
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
    items: List<ReleaseSummary>,
    favoriteItems: List<ReleaseSummary>,
    favoriteSeriesItems: List<ReleaseSummary>,
    movieItems: List<ReleaseSummary>,
    seriesItems: List<ReleaseSummary>,
): Map<HomeFeedMode, String> {
    val current = toMutableMap()
    if (HomeFeedMode.AllNew !in current) items.firstOrNull()?.detailsUrl?.let { current[HomeFeedMode.AllNew] = it }
    if (HomeFeedMode.Favorites !in current) favoriteItems.firstOrNull()?.detailsUrl?.let { current[HomeFeedMode.Favorites] = it }
    if (HomeFeedMode.FavoriteSeries !in current) favoriteSeriesItems.firstOrNull()?.detailsUrl?.let { current[HomeFeedMode.FavoriteSeries] = it }
    if (HomeFeedMode.Movies !in current) movieItems.firstOrNull()?.detailsUrl?.let { current[HomeFeedMode.Movies] = it }
    if (HomeFeedMode.Series !in current) seriesItems.firstOrNull()?.detailsUrl?.let { current[HomeFeedMode.Series] = it }
    return current
}
