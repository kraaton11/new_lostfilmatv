package com.kraat.lostfilmnewtv.ui.home

import com.kraat.lostfilmnewtv.data.model.ReleaseWatchFilter
import com.kraat.lostfilmnewtv.data.model.CatalogSort
import com.kraat.lostfilmnewtv.data.model.SeriesStatus
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.FavoriteSeriesResult
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.data.repository.DetailsResult
import com.kraat.lostfilmnewtv.data.repository.SeriesOverviewResult
import com.kraat.lostfilmnewtv.data.parser.LOSTFILM_OVERVIEW_SOURCE
import com.kraat.lostfilmnewtv.data.repository.FavoritesRepository
import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import com.kraat.lostfilmnewtv.updates.AppUpdateCoordinator
import com.kraat.lostfilmnewtv.updates.SavedAppUpdate
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FOCUS_KEY = "home.focused_item_key"
private const val HOME_RESUME_REFRESH_INTERVAL_MS = 30 * 60 * 1000L
private val favoriteSeriesSlugRegex = Regex("""/series/([^/]+)""")

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: LostFilmRepository,
    private val favoritesRepository: FavoritesRepository,
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
    private var favoriteSessionRevision = favoritesRepository.sessionRevision

    fun onStart() {
        if (started) {
            if (favoriteSessionRevision != favoritesRepository.sessionRevision) {
                onFavoriteContentInvalidated()
            }
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
            HomeFeedMode.FavoriteSeries -> {
                if (md.pagingErrorMessage == null || md.isPaging || !md.hasNextPage) return
                loadFavoriteSeries(pageNumber = md.nextPage, isPagingRequest = true)
            }
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
            HomeFeedMode.FavoriteSeries -> {
                if (md.contentState !is HomeModeContentState.Content || md.isPaging || !md.hasNextPage) return
                loadFavoriteSeries(pageNumber = md.nextPage, isPagingRequest = true)
            }
            HomeFeedMode.Series -> {
                if (md.isPaging || !md.hasNextPage || md.contentState !is HomeModeContentState.Content) return
                loadSeriesCatalog(pageNumber = md.nextPage, isPagingRequest = true)
            }
        }
    }

    suspend fun loadReleasePreview(detailsUrl: String): HomeReleasePreview = withContext(ioDispatcher) {
        val isSeriesOverview = detailsUrl.contains("/series/") && !detailsUrl.contains("/episode_")
        val result = when {
            isSeriesOverview -> null
            detailsUrl.contains("/movies/") -> repository.loadMovieDetailsPreview(detailsUrl)
            else -> repository.loadDetailsPreview(detailsUrl)
        }
        val details = (result as? DetailsResult.Success)?.details
        if (details?.kind == ReleaseKind.MOVIE) {
            // Movie pages store their synopsis in the shared details description field.
            return@withContext HomeReleasePreview(
                description = details.episodeOverviewRu?.takeIf { it.isNotBlank() },
                backdropUrl = details.backdropUrl ?: details.posterUrl.takeIf { it.isNotBlank() },
                movieMetadata = details.movieMetadata,
                movieYear = details.originalReleaseYear,
            )
        }
        val episodeOverview = details
            ?.takeIf { it.episodeOverviewSource == LOSTFILM_OVERVIEW_SOURCE }
            ?.episodeOverviewRu
            ?.takeIf { it.isNotBlank() }
        if (episodeOverview != null) return@withContext HomeReleasePreview(episodeOverview, details?.backdropUrl)

        val seriesOverview = (repository.loadSeriesOverview(detailsUrl) as? SeriesOverviewResult.Success)?.overview
        val description = seriesOverview?.descriptionRu?.takeIf { it.isNotBlank() }
            ?: seriesOverview?.plotRu?.takeIf { it.isNotBlank() }
        HomeReleasePreview(
            description = if (isSeriesOverview) description else description?.let { "О сериале · $it" },
            backdropUrl = details?.backdropUrl ?: seriesOverview?.backdropUrl ?: seriesOverview?.posterUrl,
            seriesOverview = seriesOverview.takeIf { isSeriesOverview },
        )
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
                        val items = md.items.updateWatched(detailsUrl, isWatched)
                        val updatedItems = if (mode == HomeFeedMode.Favorites) {
                            state.favoriteReleaseFilter.apply(items)
                        } else {
                            items
                        }
                        md.copy(
                            items = updatedItems,
                            contentState = md.contentState.updateItems(updatedItems),
                        )
                    }
                }.toTypedArray(),
            ).resolveSelection()
        }
        continueFilteredNewFeed()
        val state = _uiState.value
        if (started && state.isFavoritesRailVisible && state.favoriteReleaseFilter != ReleaseWatchFilter.All) {
            loadFavoriteReleases(retainVisibleItemsOnFailure = state.favoriteItems.isNotEmpty())
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
        favoriteSessionRevision = favoritesRepository.sessionRevision
        favoriteRequestToken++
        favoriteLoadJob?.cancel()
        favoriteSeriesLoadJob?.cancel()
        _uiState.update { state ->
            state.copy(favoriteSeriesCount = null)
                .updateMode(HomeFeedMode.Favorites) {
                    it.copy(items = emptyList(), contentState = HomeModeContentState.Loading, hasNextPage = false)
                }
                .updateMode(HomeFeedMode.FavoriteSeries) {
                    it.copy(items = emptyList(), contentState = HomeModeContentState.Loading)
                }
                .resolveSelection()
        }
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
            ).let { items ->
                state.favoriteReleaseFilter.apply(items)
            }
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
        if (mode == HomeFeedMode.AllNew) continueFilteredNewFeed()
    }

    private fun loadPage(pageNumber: Int, isPagingRequest: Boolean) {
        if (pageNumber == 1 && !isPagingRequest) {
            observeAllNewPage()
            return
        }
        collectNextPage(pageNumber, isPagingRequest)
    }

    private fun observeAllNewPage() {
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
            var sawFreshContent = false
            try {
                repository.observePage(1)
                    .onCompletion { cause ->
                        // Синхронизируем канал после сохранения свежей страницы в Room.
                        // Отдельная корутина позволяет коллектору завершиться сразу.
                        //
                        // Отмену пропускаем. Сбор первой страницы отменяют пагинация
                        // и повторная загрузка страницы 1 из onResume/onRetry, а сам
                        // ViewModel — при очистке; во всех случаях лента недогружена,
                        // поэтому публиковать её в канал рано, а syncNow идемпотентен
                        // и отработает при следующей успешной загрузке.
                        if (cause == null && sawFreshContent) {
                            viewModelScope.launch(ioDispatcher) {
                                homeChannelSyncManager.syncNow()
                            }
                        }
                    }
                    .collect { result ->
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
                                when {
                                    isStale -> hadCacheEmission = true
                                    // sawFreshContent отмечает приход свежей страницы
                                    // (isStale = false) — только тогда канал
                                    // синхронизируется, как и до прогрессивной ленты.
                                    // Свежий кэш попадает сюда тоже (в observePage
                                    // isStale = !cacheFresh), и там это оправдано;
                                    // stale-эмиссия и ошибка — нет.
                                    // lastAllNewRefreshAt — про гейт onResume, не про канал.
                                    else -> if (!sawFreshContent) {
                                        sawFreshContent = true
                                        lastAllNewRefreshAt = clock()
                                    }
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
                continueFilteredNewFeed()
            } catch (exception: CancellationException) {
                throw exception
            }
        }
    }

    private fun collectNextPage(pageNumber: Int, isPagingRequest: Boolean) {
        // Поздний ответ предыдущей загрузки может перезаписать уже применённую страницу.
        val initialState = _uiState.value
        val startingFilter = initialState.newReleaseFilter
        val startingVisibleCount = initialState.visibleNewItems.size
        allNewLoadJob?.cancel()
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
            var appliedEmission = false
            try {
                repository.observePage(pageNumber).collect { result ->
                    when (result) {
                        is PageState.Content -> {
                            // Кэш-эмиссия при пагинации — не новые данные: спиннер
                            // должен остаться, пока не придёт свежая страница. Но если
                            // кэш отдаёт pagingErrorMessage, эмиссию применять нужно —
                            // иначе пользователь теряет сообщение и кнопку повтора.
                            if (result.isStale && result.pagingErrorMessage == null) return@collect
                            appliedEmission = true
                            val updatedItems = result.items
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
                            appliedEmission = true
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
                if (appliedEmission) {
                    val visibleCount = if (_uiState.value.newReleaseFilter == startingFilter) startingVisibleCount else 0
                    continueFilteredNewFeed(visibleCount)
                }
            } finally {
                // Страховка для реализаций репозитория, чей observePage завершается
                // после одной cache-эмиссии: эмиссия пропущена, а flow уже закрыт —
                // гасим спиннер, иначе он крутится вечно. Нынешний
                // LostFilmRepositoryImpl так не заканчивает, но полагаться на это
                // нельзя.
                if (!appliedEmission) {
                    _uiState.update { state ->
                        state.updateMode(HomeFeedMode.AllNew) { md -> md.copy(isPaging = false) }
                    }
                }
            }
        }
    }

    fun onReleaseFilterSelected(filter: ReleaseWatchFilter) {
        val state = _uiState.value
        when (state.selectedMode) {
            HomeFeedMode.Favorites -> onFavoriteFilterSelected(filter)
            HomeFeedMode.AllNew -> {
                if (state.newReleaseFilter == filter) return
                _focusState.update {
                    it.copy(
                        rememberedItemKeyByMode = it.rememberedItemKeyByMode - HomeFeedMode.AllNew,
                        selectedItemKey = null,
                    )
                }
                _uiState.update { it.copy(newReleaseFilter = filter).resolveSelection() }
                continueFilteredNewFeed()
            }
            else -> Unit
        }
    }

    private fun continueFilteredNewFeed(previousVisibleCount: Int = 0) {
        val state = _uiState.value
        val data = state.modeData(HomeFeedMode.AllNew)
        if (state.selectedMode == HomeFeedMode.AllNew && state.newReleaseFilter != ReleaseWatchFilter.All &&
            state.visibleNewItems.size <= previousVisibleCount && data.contentState is HomeModeContentState.Content &&
            data.pagingErrorMessage == null
        ) {
            onEndReached()
        }
    }

    private fun onFavoriteFilterSelected(filter: ReleaseWatchFilter) {
        val state = _uiState.value
        if (state.selectedMode != HomeFeedMode.Favorites || state.favoriteReleaseFilter == filter) return

        _focusState.update { focus ->
            focus.copy(
                rememberedItemKeyByMode = focus.rememberedItemKeyByMode - HomeFeedMode.Favorites,
                selectedItemKey = null,
            )
        }
        favoriteRequestToken++
        favoriteLoadJob?.cancel()
        _uiState.update {
            it.copy(
                favoriteReleaseFilter = filter,
                rememberedItemKeyByMode = it.rememberedItemKeyByMode - HomeFeedMode.Favorites,
            ).updateMode(HomeFeedMode.Favorites) { data ->
                HomeModeData(isVisible = data.isVisible)
            }.resolveSelection()
        }
        loadFavoriteReleases()
    }

    private fun loadFavoriteReleases(
        pageNumber: Int = 1,
        isPagingRequest: Boolean = false,
        retainVisibleItemsOnFailure: Boolean = false,
    ) {
        val filter = _uiState.value.favoriteReleaseFilter
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
                favoritesRepository.observeFavoriteReleases(pageNumber, filter).collect { result ->
                    if (favoriteRequestToken != requestToken) {
                        return@collect
                    }
                    _uiState.update { state ->
                        if (state.favoriteReleaseFilter != filter) return@update state
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

    private fun loadFavoriteSeries(pageNumber: Int = 1, isPagingRequest: Boolean = false) {
        val mode = HomeFeedMode.FavoriteSeries
        val sort = _uiState.value.modeData(mode).catalogSort
        favoriteSeriesLoadJob?.cancel()
        _uiState.update { state ->
            state.updateMode(mode) { data ->
                data.copy(
                    contentState = if (isPagingRequest) data.contentState else HomeModeContentState.Loading,
                    isPaging = isPagingRequest,
                    pagingErrorMessage = null,
                )
            }
        }
        favoriteSeriesLoadJob = viewModelScope.launch(ioDispatcher) {
            val result = favoritesRepository.loadFavoriteSeries(pageNumber, sort)
            ensureActive()
            _uiState.update { state ->
                val data = state.modeData(mode)
                if (data.catalogSort != sort) return@update state
                when (result) {
                    is FavoriteSeriesResult.Success -> {
                        val items = if (isPagingRequest) {
                            (data.items + result.items).distinctBy { it.detailsUrl }
                        } else {
                            result.items
                        }
                        val count = if (result.hasNextPage) state.favoriteSeriesCount else items.size
                        state.copy(favoriteSeriesCount = count).updateMode(mode) {
                            it.copy(
                                items = items,
                                contentState = if (items.isEmpty()) HomeModeContentState.Empty else HomeModeContentState.Content(items),
                                nextPage = pageNumber + 1,
                                hasNextPage = result.hasNextPage,
                                isPaging = false,
                            )
                        }.resolveSelection()
                    }
                    is FavoriteSeriesResult.Unavailable -> {
                        val message = result.message ?: "Не удалось загрузить избранные сериалы"
                        state.updateMode(mode) {
                            when {
                                message.contains("Войдите", ignoreCase = true) -> it.copy(
                                    items = emptyList(),
                                    contentState = HomeModeContentState.LoginRequired(message),
                                    isPaging = false,
                                )
                                isPagingRequest -> it.copy(isPaging = false, pagingErrorMessage = message)
                                else -> it.copy(
                                    items = emptyList(),
                                    contentState = HomeModeContentState.Error(message),
                                    isPaging = false,
                                )
                            }
                        }.resolveSelection()
                    }
                }
            }
        }
    }

    fun onCatalogSortSelected(sort: CatalogSort) {
        val state = _uiState.value
        val mode = state.selectedMode
        val data = state.modeData(mode)
        if (data.catalogSort == sort) return
        restartCatalog(mode, sort, data.seriesStatus)
    }

    fun onSeriesStatusSelected(status: SeriesStatus) {
        val state = _uiState.value
        val data = state.modeData(HomeFeedMode.Series)
        if (state.selectedMode != HomeFeedMode.Series || data.seriesStatus == status) return

        val sort = when {
            status == SeriesStatus.Completed -> CatalogSort.CompletionDate
            data.catalogSort == CatalogSort.CompletionDate -> CatalogSort.Newest
            else -> data.catalogSort
        }
        restartCatalog(HomeFeedMode.Series, sort, status)
    }

    private fun restartCatalog(mode: HomeFeedMode, sort: CatalogSort, status: SeriesStatus) {
        when (mode) {
            HomeFeedMode.Movies -> moviesLoadJob?.cancel()
            HomeFeedMode.Series -> seriesLoadJob?.cancel()
            HomeFeedMode.FavoriteSeries -> favoriteSeriesLoadJob?.cancel()
            else -> return
        }
        _uiState.update {
            it.copy(rememberedItemKeyByMode = it.rememberedItemKeyByMode - mode)
                .updateMode(mode) { data ->
                    HomeModeData(isVisible = data.isVisible, catalogSort = sort, seriesStatus = status)
                }
                .resolveSelection()
        }
        when (mode) {
            HomeFeedMode.Movies -> loadMovies(pageNumber = 1, isPagingRequest = false)
            HomeFeedMode.Series -> loadSeriesCatalog(pageNumber = 1, isPagingRequest = false)
            HomeFeedMode.FavoriteSeries -> loadFavoriteSeries()
            else -> Unit
        }
    }

    private fun loadMovies(pageNumber: Int, isPagingRequest: Boolean) {
        val sort = _uiState.value.modeData(HomeFeedMode.Movies).catalogSort
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
            val result = repository.loadMovies(pageNumber, sort)
            ensureActive()
            when (result) {
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
                        if (state.modeData(HomeFeedMode.Movies).catalogSort != sort) return@update state
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
                        if (state.modeData(HomeFeedMode.Movies).catalogSort != sort) return@update state
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
        val selection = _uiState.value.modeData(HomeFeedMode.Series)
        val sort = selection.catalogSort
        val status = selection.seriesStatus
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
            val result = repository.loadSeriesCatalog(pageNumber, sort, status)
            ensureActive()
            when (result) {
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
                        val current = state.modeData(HomeFeedMode.Series)
                        if (current.catalogSort != sort || current.seriesStatus != status) return@update state
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
                        val current = state.modeData(HomeFeedMode.Series)
                        if (current.catalogSort != sort || current.seriesStatus != status) return@update state
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
