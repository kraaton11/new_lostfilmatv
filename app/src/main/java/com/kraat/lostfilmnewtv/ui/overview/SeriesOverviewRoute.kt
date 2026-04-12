package com.kraat.lostfilmnewtv.ui.overview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SeriesOverviewRoute() {
    val viewModel: SeriesOverviewViewModel = hiltViewModel()
    val state = viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onStart() }

    SeriesOverviewScreen(
        state = state.value,
        onRetry = viewModel::onRetry,
    )
}
