package com.kraat.lostfilmnewtv.ui.details

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.playback.WatchedMarkingMode
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeActionHandler
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeLinkBuilder
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeOpenResult
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun DetailsRoute(
    detailsUrl: String,
    isAuthenticated: Boolean = true,
    preferredPlaybackQuality: PlaybackQualityPreference = PlaybackQualityPreference.Q1080,
    watchedMarkingMode: WatchedMarkingMode = WatchedMarkingMode.AUTO,
    actionHandler: TorrServeActionHandler,
    linkBuilder: TorrServeLinkBuilder,
    viewModel: DetailsViewModel? = null,
    onMarkedWatched: (String) -> Unit = {},
    onWatchedStateChanged: (String, Boolean) -> Unit = { _, _ -> },
    onOpenMovieOverview: (String) -> Unit = {},
    onOpenSeriesOverview: (String) -> Unit = {},
    onOpenSeriesGuide: (String) -> Unit = {},
    onAuthClick: () -> Unit = {},
    onFavoriteContentChanged: (String, Boolean) -> Unit = { _, _ -> },
    onChannelContentChanged: suspend () -> Unit = {},
    openTorrServe: suspend (Context, String, String, String) -> TorrServeOpenResult = actionHandler::open,
) {
    // hiltViewModel() создаёт ViewModel через Hilt — SavedStateHandle заполняется
    // из nav-аргументов (detailsUrl и isAuthenticated передаются через route)
    val detailsViewModel: DetailsViewModel = viewModel ?: hiltViewModel()
    val state by detailsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val supportedTorrentRows = remember(state.details?.torrentLinks, detailsUrl, linkBuilder, preferredPlaybackQuality) {
        state.details?.torrentLinks.orEmpty().mapIndexed { index, link ->
            DetailsTorrentRowUiModel(
                rowId = "$detailsUrl-$index",
                label = link.label,
                url = link.url,
                isTorrServeSupported = linkBuilder.supportsSource(link.url),
            )
        }.filter { it.isTorrServeSupported }
    }
    val playbackRow = remember(supportedTorrentRows, preferredPlaybackQuality) {
        resolvePreferredTorrentRow(preferredPlaybackQuality, supportedTorrentRows)
    }

    var torrServeMessage by remember(detailsUrl) { mutableStateOf<TorrServeMessage?>(null) }
    var activeTorrServeRowId by remember(detailsUrl) { mutableStateOf<String?>(null) }
    var isTorrServeBusy by remember(detailsUrl) { mutableStateOf(false) }
    var requestToken by remember(detailsUrl) { mutableIntStateOf(0) }
    var handledFavoriteContentVersion by remember(detailsUrl) { mutableIntStateOf(0) }
    var handledWatchedContentVersion by remember(detailsUrl) { mutableIntStateOf(0) }
    var inFlightJob by remember(detailsUrl) { mutableStateOf<Job?>(null) }

    LaunchedEffect(isAuthenticated) { detailsViewModel.onAuthenticationChanged(isAuthenticated) }
    LaunchedEffect(detailsUrl) { detailsViewModel.onStart() }

    LaunchedEffect(state.favoriteContentVersion) {
        if (state.favoriteContentVersion > handledFavoriteContentVersion) {
            handledFavoriteContentVersion = state.favoriteContentVersion
            val currentDetails = state.details
            val isFavorite = currentDetails?.isFavorite
            if (currentDetails != null && isFavorite != null) {
                onFavoriteContentChanged(currentDetails.detailsUrl, isFavorite)
            }
        }
    }
    LaunchedEffect(state.watchedContentVersion) {
        if (state.watchedContentVersion > handledWatchedContentVersion) {
            handledWatchedContentVersion = state.watchedContentVersion
            val currentDetails = state.details
            val isWatched = state.isWatched
            if (currentDetails != null && isWatched != null) {
                if (isWatched) onMarkedWatched(currentDetails.detailsUrl)
                onWatchedStateChanged(currentDetails.detailsUrl, isWatched)
                onChannelContentChanged()
            }
        }
    }

    DisposableEffect(detailsUrl) {
        onDispose {
            requestToken += 1
            inFlightJob?.cancel()
            inFlightJob = null
        }
    }

    val handleOpenTorrServe: (String, String) -> Unit = handleOpenTorrServe@{ rowId, url ->
        if (isTorrServeBusy) return@handleOpenTorrServe
        val currentDetails = state.details
        val token = requestToken + 1
        requestToken = token
        torrServeMessage = null
        activeTorrServeRowId = rowId
        isTorrServeBusy = true
        inFlightJob?.cancel()
        inFlightJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val message = when (openTorrServe(context, url, currentDetails?.titleRu.orEmpty(), currentDetails?.posterUrl.orEmpty())) {
                    TorrServeOpenResult.Success -> {
                        val playEpisodeId = currentDetails?.playEpisodeId
                        if (playEpisodeId != null && watchedMarkingMode == WatchedMarkingMode.AUTO) {
                            launch {
                                val marked = detailsViewModel.markEpisodeWatched(
                                    detailsUrl = currentDetails.detailsUrl,
                                    playEpisodeId = playEpisodeId,
                                )
                                if (marked) {
                                    onMarkedWatched(currentDetails.detailsUrl)
                                    onWatchedStateChanged(currentDetails.detailsUrl, true)
                                    onChannelContentChanged()
                                }
                            }
                        }
                        null
                    }
                    TorrServeOpenResult.Unavailable -> TorrServeMessage(rowId, "Не удалось подключиться к TorrServe")
                    TorrServeOpenResult.LaunchError -> TorrServeMessage(rowId, "Не удалось открыть TorrServe")
                }
                if (requestToken == token) torrServeMessage = message
            } finally {
                if (requestToken == token) {
                    isTorrServeBusy = false
                    activeTorrServeRowId = null
                    inFlightJob = null
                }
            }
        }
    }

    DetailsScreen(
        state = state,
        isAuthenticated = isAuthenticated,
        availableTorrentRowsCount = supportedTorrentRows.size,
        playbackRow = playbackRow,
        torrServeMessage = torrServeMessage,
        activeTorrServeRowId = activeTorrServeRowId,
        isTorrServeBusy = isTorrServeBusy,
        onRetry = detailsViewModel::onRetry,
        onWatchedClick = detailsViewModel::onWatchedClick,
        onFavoriteClick = detailsViewModel::onFavoriteClick,
        onMovieOverviewClick = { onOpenMovieOverview(detailsUrl) },
        onSeriesOverviewClick = { onOpenSeriesOverview(detailsUrl) },
        onSeriesGuideClick = { onOpenSeriesGuide(detailsUrl) },
        onAuthClick = onAuthClick,
        onOpenTorrServe = handleOpenTorrServe,
    )
}
