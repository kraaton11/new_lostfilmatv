package com.kraat.lostfilmnewtv.ui.overview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MovieOverviewRoute() {
    val viewModel: MovieOverviewViewModel = hiltViewModel()
    val state = viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onStart() }

    MovieOverviewScreen(
        state = state.value,
        onRetry = viewModel::onRetry,
    )
}
