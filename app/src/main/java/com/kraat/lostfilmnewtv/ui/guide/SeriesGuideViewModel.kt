package com.kraat.lostfilmnewtv.ui.guide

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.data.repository.SeriesGuideResult
import com.kraat.lostfilmnewtv.navigation.AppDestination
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SeriesGuideViewModel(
    private val repository: LostFilmRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SeriesGuideUiState())
    val uiState: StateFlow<SeriesGuideUiState> = _uiState.asStateFlow()

    private var started = false
    private var loadRequestToken = 0
    private var loadJob: Job? = null

    fun onStart() {
        if (started) {
            return
        }

        started = true
        loadGuide()
    }

    fun onRetry() {
        loadGuide()
    }

    private fun loadGuide() {
        val detailsUrl = savedStateHandle.get<String>(AppDestination.SeriesGuide.detailsUrlArg).orEmpty()
        val requestToken = ++loadRequestToken
        loadJob?.cancel()
        _uiState.update { state ->
            state.copy(
                isLoading = true,
                errorMessage = null,
            )
        }

        loadJob = viewModelScope.launch(ioDispatcher) {
            when (val result = repository.loadSeriesGuide(detailsUrl)) {
                is SeriesGuideResult.Success -> {
                    if (loadRequestToken != requestToken) return@launch
                    _uiState.value = SeriesGuideUiState(
                        title = result.guide.seriesTitleRu,
                        posterUrl = result.guide.posterUrl,
                        seasons = result.guide.seasons,
                        selectedEpisodeDetailsUrl = result.guide.selectedEpisodeDetailsUrl,
                        isLoading = false,
                        errorMessage = null,
                    )
                }

                is SeriesGuideResult.Error -> {
                    if (loadRequestToken != requestToken) return@launch
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }
}
