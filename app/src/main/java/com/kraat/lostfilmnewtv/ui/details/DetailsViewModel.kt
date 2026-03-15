package com.kraat.lostfilmnewtv.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.repository.DetailsResult
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.navigation.AppDestination
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DetailsViewModel(
    private val repository: LostFilmRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private var started = false

    fun onStart() {
        if (started) {
            return
        }

        started = true
        loadDetails()
    }

    fun onRetry() {
        loadDetails()
    }

    private fun loadDetails() {
        val detailsUrl = savedStateHandle.get<String>(AppDestination.Details.detailsUrlArg).orEmpty()
        _uiState.update { state ->
            state.copy(
                isLoading = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch(ioDispatcher) {
            when (val result = repository.loadDetails(detailsUrl)) {
                is DetailsResult.Success -> {
                    _uiState.update {
                        DetailsUiState(
                            details = result.details,
                            isLoading = false,
                            showStaleBanner = result.isStale,
                            errorMessage = null,
                        )
                    }
                }

                is DetailsResult.Error -> {
                    _uiState.update {
                        DetailsUiState(
                            details = null,
                            isLoading = false,
                            showStaleBanner = false,
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }
}
