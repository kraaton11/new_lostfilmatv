package com.kraat.lostfilmnewtv.ui.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SeriesGuideRoute(onOpenDetails: (String) -> Unit) {
    val viewModel: SeriesGuideViewModel = hiltViewModel()
    val state = viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onStart() }

    SeriesGuideScreen(
        state = state.value,
        onRetry = viewModel::onRetry,
        onEpisodeClick = onOpenDetails,
    )
}
