package com.kraat.lostfilmnewtv.ui.overview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.repository.DetailsResult
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.navigation.AppDestination
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

@HiltViewModel
class MovieOverviewViewModel @Inject constructor(
    private val repository: LostFilmRepository,
    private val savedStateHandle: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovieOverviewUiState())
    val uiState: StateFlow<MovieOverviewUiState> = _uiState.asStateFlow()

    private var started = false
    private var loadRequestToken = 0
    private var loadJob: Job? = null

    fun onStart() {
        if (started) return
        started = true
        loadOverview()
    }

    fun onRetry() = loadOverview()

    private fun loadOverview() {
        val detailsUrl = savedStateHandle.get<String>(AppDestination.MovieOverview.detailsUrlArg).orEmpty()
        val requestToken = ++loadRequestToken
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        loadJob = viewModelScope.launch(ioDispatcher) {
            when (val result = repository.loadDetails(detailsUrl)) {
                is DetailsResult.Success -> {
                    if (loadRequestToken != requestToken) return@launch
                    if (result.details.kind != ReleaseKind.MOVIE) {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Описание фильма недоступно") }
                        return@launch
                    }
                    _uiState.value = MovieOverviewUiState(
                        details = result.details,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
                is DetailsResult.Error -> {
                    if (loadRequestToken != requestToken) return@launch
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }
}
