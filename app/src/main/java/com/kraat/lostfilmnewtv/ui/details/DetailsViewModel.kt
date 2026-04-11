package com.kraat.lostfilmnewtv.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.model.FavoriteMutationResult
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
class DetailsViewModel @Inject constructor(
    private val repository: LostFilmRepository,
    private val savedStateHandle: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    // isAuthenticated читается из SavedStateHandle — передаётся через nav-аргумент
    private val isAuthenticated: Boolean
        get() = savedStateHandle.get<Boolean>(AppDestination.Details.isAuthenticatedArg) ?: true

    private var started = false
    private var loadRequestToken = 0
    private var loadJob: Job? = null
    private var hasValidSession = isAuthenticated

    fun onStart() {
        if (started) return
        started = true
        loadDetails()
    }

    fun onRetry() = loadDetails()

    suspend fun markEpisodeWatched(detailsUrl: String, playEpisodeId: String): Boolean =
        repository.markEpisodeWatched(detailsUrl, playEpisodeId)

    fun onFavoriteClick() {
        val currentState = _uiState.value
        val currentDetails = currentState.details ?: return
        val currentFavorite = currentDetails.isFavorite ?: return
        if (currentState.isFavoriteMutationInFlight || currentDetails.favoriteTargetId == null) return

        _uiState.update { it.copy(isFavoriteMutationInFlight = true, favoriteStatusMessage = null).withFavoritePresentation() }

        viewModelScope.launch(ioDispatcher) {
            val targetFavorite = !currentFavorite
            when (val result = repository.setFavorite(currentDetails.detailsUrl, targetFavorite)) {
                FavoriteMutationResult.Updated -> _uiState.update { state ->
                    state.copy(
                        details = state.details?.copy(isFavorite = targetFavorite),
                        isFavoriteMutationInFlight = false,
                        favoriteStatusMessage = if (targetFavorite) "Добавлено в избранное" else "Удалено из избранного",
                        favoriteContentVersion = state.favoriteContentVersion + 1,
                    ).withFavoritePresentation()
                }
                FavoriteMutationResult.NoOp -> _uiState.update { state ->
                    state.copy(isFavoriteMutationInFlight = false, favoriteStatusMessage = null).withFavoritePresentation()
                }
                is FavoriteMutationResult.RequiresLogin -> {
                    hasValidSession = false
                    _uiState.update { state ->
                        state.copy(isFavoriteMutationInFlight = false, favoriteStatusMessage = result.message).withFavoritePresentation()
                    }
                }
                is FavoriteMutationResult.Error -> _uiState.update { state ->
                    state.copy(isFavoriteMutationInFlight = false, favoriteStatusMessage = "Не удалось обновить избранное").withFavoritePresentation()
                }
            }
        }
    }

    private fun loadDetails() {
        val detailsUrl = savedStateHandle.get<String>(AppDestination.Details.detailsUrlArg).orEmpty()
        val requestToken = ++loadRequestToken
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        loadJob = viewModelScope.launch(ioDispatcher) {
            when (val result = repository.loadDetails(detailsUrl)) {
                is DetailsResult.Success -> {
                    if (loadRequestToken != requestToken) return@launch
                    _uiState.update {
                        DetailsUiState(
                            details = result.details,
                            isLoading = false,
                            showStaleBanner = result.isStale,
                            errorMessage = null,
                        ).withFavoritePresentation()
                    }
                }
                is DetailsResult.Error -> {
                    if (loadRequestToken != requestToken) return@launch
                    _uiState.update {
                        DetailsUiState(
                            details = null,
                            isLoading = false,
                            showStaleBanner = false,
                            errorMessage = result.message,
                        ).withFavoritePresentation()
                    }
                }
            }
        }
    }

    private fun DetailsUiState.withFavoritePresentation(): DetailsUiState {
        val currentDetails = details ?: return copy(favoriteActionLabel = "", isFavoriteActionEnabled = false)
        if (isFavoriteMutationInFlight) return copy(favoriteActionLabel = "Сохраняем...", isFavoriteActionEnabled = false)
        if (!hasValidSession) return copy(favoriteActionLabel = "Войдите в LostFilm", isFavoriteActionEnabled = false)
        if (currentDetails.favoriteTargetId == null) return copy(favoriteActionLabel = "Избранное недоступно", isFavoriteActionEnabled = false)
        return when (currentDetails.isFavorite) {
            true -> copy(favoriteActionLabel = "Убрать из избранного", isFavoriteActionEnabled = true)
            false -> copy(favoriteActionLabel = "Добавить в избранное", isFavoriteActionEnabled = true)
            null -> copy(favoriteActionLabel = "Избранное недоступно", isFavoriteActionEnabled = false)
        }
    }
}
