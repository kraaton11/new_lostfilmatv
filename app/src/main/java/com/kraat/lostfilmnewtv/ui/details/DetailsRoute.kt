package com.kraat.lostfilmnewtv.ui.details

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.navigation.AppDestination
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeActionHandler
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeLinkBuilder
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeOpenResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@Composable
fun DetailsRoute(
    detailsUrl: String,
    repository: LostFilmRepository,
    isAuthenticated: Boolean = true,
    actionHandler: TorrServeActionHandler,
    linkBuilder: TorrServeLinkBuilder,
    onBack: () -> Unit,
    openTorrServe: suspend (Context, String) -> TorrServeOpenResult = actionHandler::open,
    openExternalLink: (Context, String) -> Unit = ::openLink,
) {
    val detailsViewModel: DetailsViewModel = viewModel(
        key = "details:$detailsUrl",
        factory = detailsViewModelFactory(repository, detailsUrl),
    )
    val state by detailsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = remember(detailsUrl) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    val torrentRows = remember(state.details?.torrentLinks, detailsUrl, linkBuilder) {
        state.details?.torrentLinks.orEmpty().mapIndexed { index, link ->
            DetailsTorrentRowUiModel(
                rowId = "$detailsUrl#$index",
                label = link.label,
                url = link.url,
                isTorrServeSupported = linkBuilder.supportsSource(link.url),
            )
        }.filter { it.isTorrServeSupported }
    }

    var torrServeMessage by remember(detailsUrl) { mutableStateOf<TorrServeMessage?>(null) }
    var activeTorrServeRowId by remember(detailsUrl) { mutableStateOf<String?>(null) }
    var isTorrServeBusy by remember(detailsUrl) { mutableStateOf(false) }
    var requestToken by remember(detailsUrl) { mutableIntStateOf(0) }
    var inFlightJob by remember(detailsUrl) { mutableStateOf<Job?>(null) }

    LaunchedEffect(detailsUrl) {
        detailsViewModel.onStart()
    }

    DisposableEffect(detailsUrl) {
        onDispose {
            requestToken += 1
            inFlightJob?.cancel()
            inFlightJob = null
            scope.cancel()
        }
    }

    val handleOpenTorrServe: (String, String) -> Unit = handleOpenTorrServe@{ rowId, url ->
        if (isTorrServeBusy) {
            return@handleOpenTorrServe
        }

        val token = requestToken + 1
        requestToken = token
        torrServeMessage = null
        activeTorrServeRowId = rowId
        isTorrServeBusy = true
        inFlightJob?.cancel()
        inFlightJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val message = when (openTorrServe(context, url)) {
                    TorrServeOpenResult.Success -> null
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
    val handleOpenLink: (String) -> Unit = { url ->
        openExternalLink(context, url)
    }

    DetailsScreen(
        state = state,
        isAuthenticated = isAuthenticated,
        torrentRows = torrentRows,
        torrServeMessage = torrServeMessage,
        activeTorrServeRowId = activeTorrServeRowId,
        isTorrServeBusy = isTorrServeBusy,
        onBack = onBack,
        onRetry = detailsViewModel::onRetry,
        onOpenTorrServe = handleOpenTorrServe,
        onOpenLink = handleOpenLink,
    )
}

private fun detailsViewModelFactory(
    repository: LostFilmRepository,
    detailsUrl: String,
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return DetailsViewModel(
                repository = repository,
                savedStateHandle = SavedStateHandle(
                    mapOf(AppDestination.Details.detailsUrlArg to detailsUrl),
                ),
            ) as T
        }
    }
}

private fun openLink(context: Context, rawUrl: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl)).apply {
        if (context !is android.app.Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    } catch (_: IllegalArgumentException) {
    }
}
