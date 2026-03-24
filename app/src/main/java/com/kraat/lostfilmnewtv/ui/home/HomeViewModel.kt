package com.kraat.lostfilmnewtv.ui.home

import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.model.PageState
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
    private val initialFavoritesRailVisible: Boolean = false,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(
            selectedItemKey = savedStateHandle[FOCUS_KEY],
            isInitialLoading = true,
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
        loadPage(pageNumber = 1, isPagingRequest = false)
        if (_uiState.value.isFavoritesRailVisible) {
            loadFavoriteReleases()
        }
    }

    fun onRetry() {
        loadPage(pageNumber = 1, isPagingRequest = false)
        if (_uiState.value.isFavoritesRailVisible) {
            loadFavoriteReleases()
        }
    }

    fun onPagingRetry() {
        val state = _uiState.value
        if (state.pagingErrorMessage == null || state.isPaging || state.isInitialLoading || !state.hasNextPage) {
            return
        }
        loadPage(pageNumber = state.nextPage, isPagingRequest = true)
    }

    fun onEndReached() {
        val state = _uiState.value
        if (state.isInitialLoading || state.isPaging || !state.hasNextPage || state.fullScreenErrorMessage != null) {
            return
        }

        loadPage(pageNumber = state.nextPage, isPagingRequest = true)
    }

    fun onItemFocused(itemKey: String) {
        savedStateHandle[FOCUS_KEY] = itemKey
        _uiState.update { state ->
            val selectedMatch = findHomeRailItem(state.rails, itemKey)
            state.copy(
                selectedItemKey = selectedMatch?.key ?: itemKey,
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
            ).withResolvedHomeSelection()
        }
    }

    fun onFavoritesRailVisibilityChanged(isVisible: Boolean) {
        if (_uiState.value.isFavoritesRailVisible == isVisible) {
            return
        }

        favoriteLoadJob?.cancel()
        _uiState.update { state ->
            state.copy(
                isFavoritesRailVisible = isVisible,
                favoriteItems = if (isVisible) state.favoriteItems else emptyList(),
            ).withResolvedHomeSelection()
        }

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
                        state.copy(
                            favoriteItems = result.items,
                        ).withResolvedHomeSelection()
                    }

                    is FavoriteReleasesResult.Unavailable -> {
                        state.copy(
                            favoriteItems = emptyList(),
                        ).withResolvedHomeSelection()
                    }
                }
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
    val selectedMatch = findHomeRailItem(rails, selectedItemKey)
    return copy(
        rails = rails,
        selectedItem = selectedMatch?.item ?: selectedItem,
        selectedItemKey = selectedMatch?.key ?: selectedItemKey,
    )
}
