package com.kraat.lostfilmnewtv.ui.details

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kraat.lostfilmnewtv.data.model.FavoriteMutationResult
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.network.ProwlarrClientFactory
import com.kraat.lostfilmnewtv.data.network.ProwlarrSearchResult
import com.kraat.lostfilmnewtv.data.repository.DetailsResult
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.data.repository.FavoritesRepository
import com.kraat.lostfilmnewtv.navigation.AppDestination
import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
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

private const val MAX_PROWLARR_RESULTS = 5
private val prowlarrYearRegex = Regex("""\b((?:19|20)\d{2})\b""")
private val prowlarrTitleSeparatorRegex = Regex("""[^a-z0-9а-я]+""")
private val prowlarrSeasonEpisodeRegex = Regex("""(?i)\bS0*(\d{1,2})E0*(\d{1,3})\b""")
private val prowlarrStopWords = setOf("the", "a", "an")

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val repository: LostFilmRepository,
    private val favoritesRepository: FavoritesRepository,
    private val savedStateHandle: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val preferencesStore: PlaybackPreferencesStore? = null,
    private val prowlarrClientFactory: ProwlarrClientFactory? = null,
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
    private var prowlarrSearchJob: Job? = null
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

    fun onProwlarrSearchClick() {
        val details = _uiState.value.details ?: return
        val baseUrl = preferencesStore?.readProwlarrBaseUrl().orEmpty()
        val apiKey = preferencesStore?.readProwlarrApiKey().orEmpty()
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    prowlarrStatusMessage = "Настройте Prowlarr в настройках",
                    isProwlarrPanelVisible = true,
                )
            }
            return
        }
        if (prowlarrSearchJob?.isActive == true) return

        val queries = buildProwlarrSearchQueries(details)
        val searchSpec = ProwlarrSearchSpec.from(details)
        prowlarrSearchJob = viewModelScope.launch(ioDispatcher) {
            _uiState.update {
                it.copy(
                    isProwlarrSearching = true,
                    prowlarrStatusMessage = "Ищем в Prowlarr...",
                    prowlarrResults = emptyList(),
                    isProwlarrPanelVisible = true,
                )
            }
            val results = runCatching {
                val client = prowlarrClientFactory?.create(baseUrl = baseUrl, apiKey = apiKey)
                    ?: return@runCatching emptyList<ProwlarrSearchResult>()
                queries
                    .flatMap { query -> client.search(query) }
                    .bestProwlarrResults(searchSpec)
            }.getOrElse { error ->
                Log.w(TAG, "Prowlarr search failed for $baseUrl", error)
                _uiState.update { state ->
                    state.copy(
                        isProwlarrSearching = false,
                        prowlarrStatusMessage = "Не удалось выполнить поиск Prowlarr",
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isProwlarrSearching = false,
                    prowlarrResults = results,
                    prowlarrStatusMessage = if (results.isEmpty()) "Ничего не найдено" else null,
                    isProwlarrPanelVisible = true,
                )
            }
        }
    }

    fun onProwlarrPanelDismiss() {
        prowlarrSearchJob?.cancel()
        _uiState.update {
            it.copy(
                isProwlarrSearching = false,
                isProwlarrPanelVisible = false,
            )
        }
    }

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
            when (val result = favoritesRepository.setFavorite(currentDetails.detailsUrl, targetFavorite)) {
                FavoriteMutationResult.Updated -> _uiState.update { state ->
                    state.copy(
                        details = state.details?.copy(isFavorite = targetFavorite),
                        isFavoriteMutationInFlight = false,
                        favoriteStatusMessage = if (targetFavorite) "Добавлено в избранное" else "Удалено из избранного",
                        favoriteContentVersion = state.favoriteContentVersion + 1,
                    ).withFavoritePresentation()
                }
                FavoriteMutationResult.NoOp -> _uiState.update { state ->
                    state.copy(
                        details = state.details?.copy(isFavorite = targetFavorite),
                        isFavoriteMutationInFlight = false,
                        favoriteStatusMessage = null,
                    ).withFavoritePresentation()
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
            when (val result = repository.loadDetailsPreview(detailsUrl)) {
                is DetailsResult.Success -> {
                    if (loadRequestToken != requestToken) return@launch
                    _uiState.update {
                        DetailsUiState(
                            details = result.details,
                            isLoading = false,
                            showStaleBanner = result.isStale,
                            errorMessage = null,
                            isProwlarrConfigured = preferencesStore?.readProwlarrBaseUrl().orEmpty().isNotBlank() &&
                                preferencesStore?.readProwlarrApiKey().orEmpty().isNotBlank(),
                        ).withFavoritePresentation().withWatchedPresentation()
                    }
                    refreshWatchedState(
                        requestToken = requestToken,
                        detailsUrl = result.details.detailsUrl,
                        playEpisodeId = result.details.playEpisodeId,
                    )
                    when (val enrichedResult = repository.refreshDetailsExtras(result.details)) {
                        is DetailsResult.Success -> {
                            if (loadRequestToken != requestToken) return@launch
                            _uiState.update { state ->
                                state.copy(
                                    details = enrichedResult.details,
                                    showStaleBanner = enrichedResult.isStale,
                                ).withFavoritePresentation().withWatchedPresentation()
                            }
                        }
                        is DetailsResult.Error -> Unit
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
                        ).withFavoritePresentation().withWatchedPresentation()
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "DetailsViewModel"
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

internal fun buildProwlarrSearchQueries(details: ReleaseDetails): List<String> {
    val englishTitle = details.detailsUrl.extractLostFilmSlugTitle()
    val russianTitle = details.titleRu.takeIf { it.isNotBlank() }

    val yearSuffix = details.prowlarrReleaseYear()?.toString()

    val englishQueries = englishTitle?.let { title ->
        listOfNotNull(
            yearSuffix?.let { "$title $it" },
            title,
        )
    }.orEmpty()
    val russianQueries = russianTitle?.let { title ->
        listOf(title)
    }.orEmpty()

    return (englishQueries + russianQueries)
        .distinct()
}

internal fun List<ProwlarrSearchResult>.bestProwlarrResults(searchSpec: ProwlarrSearchSpec): List<ProwlarrSearchResult> {
    return filter { searchSpec.matches(it) }
        .sortedWith(
            compareByDescending<ProwlarrSearchResult> { searchSpec.score(it) }
                .thenByDescending { it.seeders ?: 0 }
                .thenByDescending { it.leechers ?: 0 },
        )
        .distinctBy { it.sourceUrl }
        .take(MAX_PROWLARR_RESULTS)
}

internal data class ProwlarrSearchSpec(
    val titleTokenGroups: List<Set<String>>,
    val episodeCode: String?,
    val releaseYear: Int?,
    val requireYear: Boolean,
) {
    fun matches(result: ProwlarrSearchResult): Boolean {
        val normalizedTitle = result.title.toProwlarrNormalizedText()
        // Требуется хотя бы одно совпадение из любой группы токенов
        if (
            titleTokenGroups.isNotEmpty() &&
            titleTokenGroups.none { tokens -> tokens.any { token -> normalizedTitle.containsToken(token) } }
        ) {
            return false
        }
        // Год не фильтруем строго — только влияем на сортировку
        return true
    }

    fun score(result: ProwlarrSearchResult): Int {
        val normalizedTitle = result.title.toProwlarrNormalizedText()
        val yearScore = if (releaseYear != null && result.title.contains(releaseYear.toString())) 10_000 else 0
        val episodeScore = if (episodeCode != null && normalizedTitle.hasEpisodeCode(episodeCode)) 5_000 else 0
        return yearScore + episodeScore
    }

    companion object {
        fun from(details: ReleaseDetails): ProwlarrSearchSpec {
            val englishTitle = details.detailsUrl.extractLostFilmSlugTitle()
            val releaseYear = details.prowlarrReleaseYear()
            return ProwlarrSearchSpec(
                titleTokenGroups = listOfNotNull(
                    englishTitle?.toProwlarrTitleTokens(),
                    details.titleRu.takeIf { it.isNotBlank() }?.toProwlarrTitleTokens(),
                ).filter { it.isNotEmpty() },
                episodeCode = null,
                releaseYear = releaseYear,
                requireYear = details.kind == com.kraat.lostfilmnewtv.data.model.ReleaseKind.MOVIE && releaseYear != null,
            )
        }
    }
}

private fun String.extractLostFilmSlugTitle(): String? {
    val slug = Regex("""/(?:series|movies)/([^/?#]+)""")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.replace(Regex("""[_-]+"""), " ")
        ?.trim()
        .orEmpty()
    return slug.takeIf { value -> value.any { it in 'A'..'Z' || it in 'a'..'z' } }
}

private fun ReleaseDetails.prowlarrEpisodeCode(): String? {
    val season = seasonNumber ?: return null
    val episode = episodeNumber ?: return null
    return "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
}

private fun ReleaseDetails.prowlarrReleaseYear(): Int? {
    return originalReleaseYear
}

private fun String.toProwlarrTitleTokens(): Set<String> {
    return toProwlarrNormalizedText()
        .split(' ')
        .filter { it.length > 1 && it !in prowlarrStopWords }
        .toSet()
}

private fun String.toProwlarrNormalizedText(): String {
    return lowercase()
        .replace(prowlarrSeasonEpisodeRegex) { match ->
            val season = match.groupValues[1].toIntOrNull()?.toString()?.padStart(2, '0') ?: match.groupValues[1]
            val episode = match.groupValues[2].toIntOrNull()?.toString()?.padStart(2, '0') ?: match.groupValues[2]
            " s${season}e${episode} "
        }
        .replace(prowlarrTitleSeparatorRegex, " ")
        .trim()
}

private fun String.containsToken(token: String): Boolean {
    return split(' ').any { it == token }
}

private fun String.hasEpisodeCode(episodeCode: String): Boolean {
    return contains(episodeCode.lowercase())
}
