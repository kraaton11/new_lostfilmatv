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
    private var watchedStateJob: Job? = null
    private var hasValidSession = isAuthenticated

    fun onStart() {
        if (started) return
        started = true
        loadDetails()
    }

    fun onAuthenticationChanged(isAuthenticated: Boolean) {
        val authStateChanged = hasValidSession != isAuthenticated
        hasValidSession = isAuthenticated

        if (!started) {
            return
        }

        val needsFavoriteRefresh = isAuthenticated && authStateChanged && _uiState.value.details?.let { details ->
            details.favoriteTargetId == null || details.isFavorite == null
        } == true
        val needsWatchedRefresh = isAuthenticated && authStateChanged && _uiState.value.details?.playEpisodeId != null

        if (needsFavoriteRefresh) {
            loadDetails()
        } else {
            _uiState.update { it.withFavoritePresentation().withWatchedPresentation() }
            if (needsWatchedRefresh) {
                refreshWatchedState(
                    requestToken = loadRequestToken,
                    detailsUrl = _uiState.value.details?.detailsUrl.orEmpty(),
                    playEpisodeId = _uiState.value.details?.playEpisodeId,
                )
            }
        }
    }

    fun onRetry() = loadDetails()

    suspend fun markEpisodeWatched(detailsUrl: String, playEpisodeId: String): Boolean =
        repository.setEpisodeWatched(detailsUrl, playEpisodeId, targetWatched = true) == true

    fun onWatchedClick() {
        val currentState = _uiState.value
        val currentDetails = currentState.details ?: return
        val currentWatched = currentState.isWatched ?: return
        val playEpisodeId = currentDetails.playEpisodeId ?: return
        if (currentState.isWatchedMutationInFlight) return

        _uiState.update {
            it.copy(
                isWatchedMutationInFlight = true,
                watchedStatusMessage = null,
            ).withWatchedPresentation()
        }

        viewModelScope.launch(ioDispatcher) {
            val targetWatched = !currentWatched
            val effectiveWatched = repository.setEpisodeWatched(
                detailsUrl = currentDetails.detailsUrl,
                playEpisodeId = playEpisodeId,
                targetWatched = targetWatched,
            )
            _uiState.update { state ->
                when {
                    effectiveWatched == null -> {
                        state.copy(
                            isWatchedMutationInFlight = false,
                            watchedStatusMessage = "Не удалось обновить статус просмотра",
                        ).withWatchedPresentation()
                    }

                    else -> {
                        state.copy(
                            isWatched = effectiveWatched,
                            isWatchedMutationInFlight = false,
                            watchedContentVersion = state.watchedContentVersion + 1,
                            watchedStatusMessage = if (effectiveWatched) {
                                "Отмечено как просмотренное"
                            } else {
                                "Отмечено как непросмотренное"
                            },
                        ).withWatchedPresentation()
                    }
                }
            }
        }
    }

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
        watchedStateJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                isWatched = null,
                isWatchedStateLoading = false,
                isWatchedMutationInFlight = false,
                watchedStatusMessage = null,
            ).withWatchedPresentation()
        }

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
                        ).withFavoritePresentation().withWatchedPresentation()
                    }
                    refreshWatchedState(
                        requestToken = requestToken,
                        detailsUrl = result.details.detailsUrl,
                        playEpisodeId = result.details.playEpisodeId,
                    )
                }
                is DetailsResult.Error -> {
                    if (loadRequestToken != requestToken) return@launch
                    _uiState.update {
                        DetailsUiState(
                            details = null,
                            isLoading = false,
                            showStaleBanner = false,
                            errorMessage = result.message,
                        ).withFavoritePresentation().withWatchedPresentation()
                    }
                }
            }
        }
    }

    private fun refreshWatchedState(
        requestToken: Int,
        detailsUrl: String,
        playEpisodeId: String?,
    ) {
        if (!hasValidSession || playEpisodeId == null) {
            _uiState.update { it.copy(isWatchedStateLoading = false).withWatchedPresentation() }
            return
        }

        _uiState.update { it.copy(isWatchedStateLoading = true).withWatchedPresentation() }
        watchedStateJob?.cancel()
        watchedStateJob = viewModelScope.launch(ioDispatcher) {
            val watchedState = repository.loadWatchedState(detailsUrl)
            if (loadRequestToken != requestToken) return@launch
            _uiState.update {
                it.copy(
                    isWatched = watchedState,
                    isWatchedStateLoading = false,
                ).withWatchedPresentation()
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

    private fun DetailsUiState.withWatchedPresentation(): DetailsUiState {
        val currentDetails = details ?: return copy(watchedActionLabel = "", isWatchedActionEnabled = false)
        if (!hasValidSession) return copy(watchedActionLabel = "Войдите в LostFilm", isWatchedActionEnabled = false)
        if (currentDetails.playEpisodeId == null) return copy(watchedActionLabel = "Статус недоступен", isWatchedActionEnabled = false)
        if (isWatchedMutationInFlight) return copy(watchedActionLabel = "Сохраняем...", isWatchedActionEnabled = false)
        if (isWatchedStateLoading) return copy(watchedActionLabel = "Проверяем статус...", isWatchedActionEnabled = false)
        return when (isWatched) {
            true -> copy(watchedActionLabel = "Просмотрено", isWatchedActionEnabled = true)
            false -> copy(watchedActionLabel = "Не просмотрено", isWatchedActionEnabled = true)
            null -> copy(watchedActionLabel = "Статус недоступен", isWatchedActionEnabled = false)
        }
    }
}
