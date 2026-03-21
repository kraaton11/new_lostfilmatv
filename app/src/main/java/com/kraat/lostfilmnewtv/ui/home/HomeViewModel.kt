package com.kraat.lostfilmnewtv.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(
            selectedItemKey = savedStateHandle[FOCUS_KEY],
        ),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var started = false

    fun onStart() {
        if (started) {
            return
        }

        started = true
        loadPage(pageNumber = 1, isPagingRequest = false)
    }

    fun onRetry() {
        loadPage(pageNumber = 1, isPagingRequest = false)
    }

    fun onEndReached() {
        val state = _uiState.value
        if (state.isInitialLoading || state.isPaging || !state.hasNextPage || state.fullScreenErrorMessage != null) {
            return
        }

        loadPage(pageNumber = state.nextPage, isPagingRequest = true)
    }

    fun onItemFocused(detailsUrl: String) {
        savedStateHandle[FOCUS_KEY] = detailsUrl
        _uiState.update { state ->
            state.copy(
                selectedItemKey = detailsUrl,
                selectedItem = state.items.find { it.detailsUrl == detailsUrl } ?: state.selectedItem,
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
            val updatedSelectedItem = updatedItems.find { it.detailsUrl == state.selectedItemKey }
                ?: state.selectedItem?.let { selectedItem ->
                    if (selectedItem.detailsUrl == detailsUrl) {
                        selectedItem.copy(isWatched = true)
                    } else {
                        selectedItem
                    }
                }

            state.copy(
                items = updatedItems,
                selectedItem = updatedSelectedItem,
            )
        }
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
                        val selectedKey = state.selectedItemKey
                        val selectedItem = resolveSelectedItem(
                            items = result.items,
                            preferredKey = selectedKey,
                        )

                        state.copy(
                            items = result.items,
                            selectedItem = selectedItem,
                            selectedItemKey = selectedItem?.detailsUrl ?: selectedKey,
                            showStaleBanner = result.isStale,
                            isInitialLoading = false,
                            isPaging = false,
                            fullScreenErrorMessage = null,
                            pagingErrorMessage = result.pagingErrorMessage,
                            nextPage = result.pageNumber + 1,
                            hasNextPage = result.hasNextPage,
                        )
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
                        )
                    }
                }
            }
        }
    }

    private fun resolveSelectedItem(
        items: List<ReleaseSummary>,
        preferredKey: String?,
    ): ReleaseSummary? {
        return items.find { it.detailsUrl == preferredKey } ?: items.firstOrNull()
    }
}
