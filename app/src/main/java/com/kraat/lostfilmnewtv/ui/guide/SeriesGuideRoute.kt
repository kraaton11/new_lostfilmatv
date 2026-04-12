package com.kraat.lostfilmnewtv.ui.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SeriesGuideRoute(
    onOpenDetails: (String) -> Unit,
    viewModel: SeriesGuideViewModel? = null,
) {
    val routeViewModel: SeriesGuideViewModel = viewModel ?: hiltViewModel()
    val state = routeViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { routeViewModel.onStart() }

    SeriesGuideScreen(
        state = state.value,
        onRetry = routeViewModel::onRetry,
        onEpisodeClick = onOpenDetails,
    )
}
