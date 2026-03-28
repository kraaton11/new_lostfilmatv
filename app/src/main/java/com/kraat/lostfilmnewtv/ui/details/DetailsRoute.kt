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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.navigation.AppDestination
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeActionHandler
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeLinkBuilder
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeOpenResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun DetailsRoute(
    detailsUrl: String,
    repository: LostFilmRepository,
    isAuthenticated: Boolean = true,
    preferredPlaybackQuality: PlaybackQualityPreference = PlaybackQualityPreference.Q1080,
    actionHandler: TorrServeActionHandler,
    linkBuilder: TorrServeLinkBuilder,
    onMarkedWatched: (String) -> Unit = {},
    onOpenSeriesGuide: (String) -> Unit = {},
    onFavoriteContentChanged: () -> Unit = {},
    onChannelContentChanged: suspend () -> Unit = {},
    openTorrServe: suspend (Context, String) -> TorrServeOpenResult = actionHandler::open,
) {
    val detailsViewModel: DetailsViewModel = viewModel(
        key = "details:$detailsUrl",
        factory = detailsViewModelFactory(repository, detailsUrl, isAuthenticated),
    )
    val state by detailsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val supportedTorrentRows = remember(
        state.details?.torrentLinks,
        detailsUrl,
        linkBuilder,
        preferredPlaybackQuality,
    ) {
        state.details?.torrentLinks.orEmpty().mapIndexed { index, link ->
            DetailsTorrentRowUiModel(
                rowId = "$detailsUrl#$index",
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
    var inFlightJob by remember(detailsUrl) { mutableStateOf<Job?>(null) }

    LaunchedEffect(detailsUrl) {
        detailsViewModel.onStart()
    }

    LaunchedEffect(state.favoriteContentVersion) {
        if (state.favoriteContentVersion > handledFavoriteContentVersion) {
            handledFavoriteContentVersion = state.favoriteContentVersion
            onFavoriteContentChanged()
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
        if (isTorrServeBusy) {
            return@handleOpenTorrServe
        }

        val currentDetails = state.details
        val token = requestToken + 1
        requestToken = token
        torrServeMessage = null
        activeTorrServeRowId = rowId
        isTorrServeBusy = true
        inFlightJob?.cancel()
        inFlightJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val message = when (openTorrServe(context, url)) {
                    TorrServeOpenResult.Success -> {
                        val playEpisodeId = currentDetails?.playEpisodeId
                        if (playEpisodeId != null) {
                            launch {
                                val marked = repository.markEpisodeWatched(
                                    detailsUrl = currentDetails.detailsUrl,
                                    playEpisodeId = playEpisodeId,
                                )
                                if (marked) {
                                    onMarkedWatched(currentDetails.detailsUrl)
                                    onChannelContentChanged()
                                }
                            }
                        }
                        null
                    }
                    TorrServeOpenResult.Unavailable -> TorrServeMessage(
                        rowId = rowId,
                        text = "Не удалось подключиться к TorrServe",
                    )
                    TorrServeOpenResult.LaunchError -> TorrServeMessage(
                        rowId = rowId,
                        text = "Не удалось открыть TorrServe",
                    )
                }
                if (requestToken == token) {
                    torrServeMessage = message
                }
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
        onFavoriteClick = detailsViewModel::onFavoriteClick,
        onSeriesGuideClick = { onOpenSeriesGuide(detailsUrl) },
        onOpenTorrServe = handleOpenTorrServe,
    )
}

private fun detailsViewModelFactory(
    repository: LostFilmRepository,
    detailsUrl: String,
    isAuthenticated: Boolean,
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return DetailsViewModel(
                repository = repository,
                savedStateHandle = SavedStateHandle(
                    mapOf(AppDestination.Details.detailsUrlArg to detailsUrl),
                ),
                isAuthenticated = isAuthenticated,
            ) as T
        }
    }
}
