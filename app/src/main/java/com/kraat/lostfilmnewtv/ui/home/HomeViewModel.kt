package com.kraat.lostfilmnewtv.ui.home

import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val FOCUS_KEY = "home.focused_item_key"

class HomeViewModel(
    private val repository: LostFilmRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val onChannelContentChanged: suspend () -> Unit = {},
    private val initialSelectedMode: HomeFeedMode = HomeFeedMode.AllNew,
    private val initialFavoritesRailVisible: Boolean = false,
    private val persistSelectedMode: (HomeFeedMode) -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
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
            isFavoritesRailVisible = initialFavoritesRailVisible,
        ).withResolvedHomeSelection(),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var started = false
    private var favoriteRequestToken = 0L
    private var favoriteLoadJob: Job? = null

    fun onStart() {
        if (started) {
            return
        }

        started = true
        if (!initialFavoritesRailVisible && initialSelectedMode == HomeFeedMode.Favorites) {
            persistSelectedMode(HomeFeedMode.AllNew)
        }
        loadPage(pageNumber = 1, isPagingRequest = false)
        if (_uiState.value.isFavoritesRailVisible) {
            loadFavoriteReleases()
        }
    }

    fun onRetry() {
        if (_uiState.value.selectedMode == HomeFeedMode.Favorites && _uiState.value.isFavoritesRailVisible) {
            loadFavoriteReleases()
            return
        }

        loadPage(pageNumber = 1, isPagingRequest = false)
        if (_uiState.value.isFavoritesRailVisible) {
            loadFavoriteReleases()
        }
    }

    fun onPagingRetry() {
        if (_uiState.value.selectedMode != HomeFeedMode.AllNew) {
            return
        }
        val state = _uiState.value
        if (state.pagingErrorMessage == null || state.isPaging || state.isInitialLoading || !state.hasNextPage) {
            return
        }
        loadPage(pageNumber = state.nextPage, isPagingRequest = true)
    }

    fun onEndReached() {
        if (_uiState.value.selectedMode != HomeFeedMode.AllNew) {
            return
        }
        val state = _uiState.value
        if (state.isInitialLoading || state.isPaging || !state.hasNextPage || state.fullScreenErrorMessage != null) {
            return
        }

        loadPage(pageNumber = state.nextPage, isPagingRequest = true)
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
        _uiState.update { state ->
            val updatedItems = state.items.map { item ->
                if (item.detailsUrl == detailsUrl) {
                    item.copy(isWatched = true)
                } else {
                    item
                }
            }
            val updatedFavoriteItems = state.favoriteItems.map { item ->
                if (item.detailsUrl == detailsUrl) {
                    item.copy(isWatched = true)
                } else {
                    item
                }
            }

            state.copy(
                items = updatedItems,
                favoriteItems = updatedFavoriteItems,
                allNewModeState = state.allNewModeState.updateItems(updatedItems),
                favoritesModeState = state.favoritesModeState.updateItems(updatedFavoriteItems),
            ).withResolvedHomeSelection()
        }
    }

    fun onFavoritesRailVisibilityChanged(isVisible: Boolean) {
        if (_uiState.value.isFavoritesRailVisible == isVisible) {
            return
        }

        favoriteLoadJob?.cancel()
        val shouldPersistFallback = _uiState.value.selectedMode == HomeFeedMode.Favorites && !isVisible
        _uiState.update { state ->
            val selectedMode = if (state.selectedMode == HomeFeedMode.Favorites && !isVisible) {
                HomeFeedMode.AllNew
            } else {
                state.selectedMode
            }
            state.copy(
                selectedMode = selectedMode,
                availableModes = availableModes(isVisible),
                isFavoritesRailVisible = isVisible,
                favoriteItems = if (isVisible) state.favoriteItems else emptyList(),
                favoritesModeState = if (isVisible) state.favoritesModeState else HomeModeContentState.Empty,
            ).withResolvedHomeSelection()
        }
        if (shouldPersistFallback) persistSelectedMode(HomeFeedMode.AllNew)

        if (started && isVisible) {
            loadFavoriteReleases()
        }
    }

    fun onFavoriteContentInvalidated() {
        if (!started || !_uiState.value.isFavoritesRailVisible) {
            return
        }

        loadFavoriteReleases()
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
                            showStaleBanner = result.isStale,
                            isInitialLoading = false,
                            isPaging = false,
                            fullScreenErrorMessage = null,
                            pagingErrorMessage = result.pagingErrorMessage,
                            nextPage = result.pageNumber + 1,
                            hasNextPage = result.hasNextPage,
                            allNewModeState = HomeModeContentState.Content(result.items),
                        ).withResolvedHomeSelection()
                    }
                    onChannelContentChanged()
                }

                is PageState.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            isInitialLoading = false,
                            isPaging = false,
                            fullScreenErrorMessage = result.message,
                            pagingErrorMessage = null,
                            showStaleBanner = false,
                            allNewModeState = HomeModeContentState.Error(result.message),
                        ).withResolvedHomeSelection()
                    }
                }
            }
        }
    }

    private fun loadFavoriteReleases() {
        val requestToken = favoriteRequestToken + 1
        favoriteRequestToken = requestToken
        favoriteLoadJob?.cancel()
        favoriteLoadJob = viewModelScope.launch(ioDispatcher) {
            val result = repository.loadFavoriteReleases()
            if (favoriteRequestToken != requestToken) {
                return@launch
            }
            _uiState.update { state ->
                when (result) {
                    is FavoriteReleasesResult.Success -> {
                        val favoriteState = if (result.items.isEmpty()) {
                            HomeModeContentState.Empty
                        } else {
                            HomeModeContentState.Content(result.items)
                        }
                        state.copy(
                            favoriteItems = result.items,
                            favoritesModeState = favoriteState,
                        ).withResolvedHomeSelection()
                    }

                    is FavoriteReleasesResult.Unavailable -> {
                        val favoriteState = if (result.message.isNullOrBlank()) {
                            HomeModeContentState.Error("Не удалось загрузить избранное")
                        } else if (result.message.contains("Войдите", ignoreCase = true)) {
                            HomeModeContentState.LoginRequired(result.message)
                        } else {
                            HomeModeContentState.Error(result.message)
                        }
                        state.copy(
                            favoriteItems = emptyList(),
                            favoritesModeState = favoriteState,
                        ).withResolvedHomeSelection()
                    }
                }
            }
        }
    }

    fun onModeSelected(mode: HomeFeedMode) {
        _uiState.update { state ->
            if (mode == state.selectedMode || mode !in state.availableModes) {
                state
            } else {
                persistSelectedMode(mode)
                state.copy(selectedMode = mode).withResolvedHomeSelection()
            }
        }
    }
}

private fun HomeUiState.withResolvedHomeSelection(): HomeUiState {
    val rails = buildHomeRails(
        items = items,
        favoriteItems = favoriteItems,
        isFavoritesRailVisible = isFavoritesRailVisible,
    )
    val normalizedSelectedMode = if (selectedMode in availableModes) {
        selectedMode
    } else {
        HomeFeedMode.AllNew
    }
    val activeItems = itemsForMode(normalizedSelectedMode)
    val preferredKey = rememberedItemKeyByMode[normalizedSelectedMode]
        ?: preferredDetailsUrl(selectedItemKey)
    val selectedItem = activeItems.firstOrNull { it.detailsUrl == preferredKey }
        ?: activeItems.firstOrNull()
    return copy(
        selectedMode = normalizedSelectedMode,
        availableModes = if (isFavoritesRailVisible) {
            availableModes
        } else {
            listOf(HomeFeedMode.AllNew)
        },
        rails = rails,
        rememberedItemKeyByMode = rememberedItemKeyByMode.withDefaultRememberedKeys(items, favoriteItems),
        selectedItem = selectedItem,
        selectedItemKey = selectedItem?.detailsUrl,
    )
}

private fun HomeModeContentState.updateItems(items: List<ReleaseSummary>): HomeModeContentState {
    return when (this) {
        HomeModeContentState.Loading -> this
        HomeModeContentState.Empty -> this
        is HomeModeContentState.Error -> this
        is HomeModeContentState.LoginRequired -> this
        is HomeModeContentState.Content -> HomeModeContentState.Content(items)
    }
}

private fun availableModes(isFavoritesVisible: Boolean): List<HomeFeedMode> {
    return if (isFavoritesVisible) {
        listOf(HomeFeedMode.AllNew, HomeFeedMode.Favorites)
    } else {
        listOf(HomeFeedMode.AllNew)
    }
}

private fun resolvedInitialMode(
    initialSelectedMode: HomeFeedMode,
    isFavoritesVisible: Boolean,
): HomeFeedMode {
    return if (initialSelectedMode == HomeFeedMode.Favorites && !isFavoritesVisible) {
        HomeFeedMode.AllNew
    } else {
        initialSelectedMode
    }
}

private fun findItemForMode(
    state: HomeUiState,
    mode: HomeFeedMode,
    itemKey: String,
): HomeModeItemMatch? {
    return state.itemsForMode(mode)
        .firstOrNull { it.detailsUrl == itemKey }
        ?.let { HomeModeItemMatch(mode = mode, item = it) }
}

private fun itemMode(
    state: HomeUiState,
    itemKey: String,
): HomeFeedMode {
    return when (railIdFromItemKey(itemKey)) {
        HOME_RAIL_FAVORITES -> HomeFeedMode.Favorites
        HOME_RAIL_ALL_NEW -> HomeFeedMode.AllNew
        else -> state.selectedMode
    }
}

private fun preferredDetailsUrl(itemKey: String?): String? {
    if (itemKey.isNullOrBlank()) return null
    return if (itemKey.contains("::")) {
        itemKey.substringAfter("::", "")
    } else {
        itemKey
    }
}

private fun Map<HomeFeedMode, String>.withDefaultRememberedKeys(
    items: List<ReleaseSummary>,
    favoriteItems: List<ReleaseSummary>,
): Map<HomeFeedMode, String> {
    val current = toMutableMap()
    if (HomeFeedMode.AllNew !in current) {
        items.firstOrNull()?.detailsUrl?.let { current[HomeFeedMode.AllNew] = it }
    }
    if (HomeFeedMode.Favorites !in current) {
        favoriteItems.firstOrNull()?.detailsUrl?.let { current[HomeFeedMode.Favorites] = it }
    }
    return current
}

private data class HomeModeItemMatch(
    val mode: HomeFeedMode,
    val item: ReleaseSummary,
)
