package com.kraat.lostfilmnewtv.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.data.repository.SearchResultsResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val QUERY_KEY = "search.query"
private const val MIN_QUERY_LENGTH = 2
private const val SEARCH_DEBOUNCE_MS = 350L

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: LostFilmRepository,
    private val savedStateHandle: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SearchUiState(
            query = savedStateHandle.get<String>(QUERY_KEY).orEmpty(),
        ),
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var started = false
    private var searchRequestToken = 0
    private var searchJob: Job? = null

    fun onStart() {
        if (started) return
        started = true

        val initialQuery = _uiState.value.query.normalizeSearchQuery()
        if (initialQuery.length >= MIN_QUERY_LENGTH) {
            scheduleSearch(initialQuery, debounce = false)
        }
    }

    fun onQueryChanged(query: String) {
        val sanitizedQuery = query.take(120)
        savedStateHandle[QUERY_KEY] = sanitizedQuery
        _uiState.update {
            it.copy(
                query = sanitizedQuery,
                errorMessage = null,
            )
        }

        val normalizedQuery = sanitizedQuery.normalizeSearchQuery()
        if (normalizedQuery.length < MIN_QUERY_LENGTH) {
            searchJob?.cancel()
            searchRequestToken += 1
            _uiState.update {
                it.copy(
                    submittedQuery = null,
                    items = emptyList(),
                    isLoading = false,
                    errorMessage = null,
                )
            }
            return
        }

        scheduleSearch(normalizedQuery, debounce = true)
    }

    fun onSearchTriggered() {
        val normalizedQuery = _uiState.value.query.normalizeSearchQuery()
        if (normalizedQuery.length < MIN_QUERY_LENGTH) {
            searchJob?.cancel()
            searchRequestToken += 1
            _uiState.update {
                it.copy(
                    submittedQuery = null,
                    items = emptyList(),
                    isLoading = false,
                    errorMessage = null,
                )
            }
            return
        }

        scheduleSearch(normalizedQuery, debounce = false)
    }

    fun onRetry() {
        val submittedQuery = _uiState.value.submittedQuery.orEmpty()
        if (submittedQuery.length < MIN_QUERY_LENGTH) return
        scheduleSearch(submittedQuery, debounce = false)
    }

    private fun scheduleSearch(query: String, debounce: Boolean) {
        val requestToken = ++searchRequestToken
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                submittedQuery = query,
                isLoading = true,
                errorMessage = null,
            )
        }

        searchJob = viewModelScope.launch(ioDispatcher) {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)

            when (val result = repository.search(query)) {
                is SearchResultsResult.Success -> {
                    if (searchRequestToken != requestToken) return@launch
                    _uiState.update {
                        it.copy(
                            submittedQuery = result.query,
                            items = result.items,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
                is SearchResultsResult.Error -> {
                    if (searchRequestToken != requestToken) return@launch
                    _uiState.update {
                        it.copy(
                            submittedQuery = result.query,
                            items = emptyList(),
                            isLoading = false,
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }
}

private fun String.normalizeSearchQuery(): String = trim().replace(Regex("""\s+"""), " ")
