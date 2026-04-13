package com.kraat.lostfilmnewtv.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kraat.lostfilmnewtv.data.model.LostFilmSearchItem

@Composable
fun SearchRoute(
    onOpenItem: (LostFilmSearchItem) -> Unit,
) {
    val viewModel: SearchViewModel = hiltViewModel()
    val state = viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onStart() }

    SearchScreen(
        state = state.value,
        onQueryChanged = viewModel::onQueryChanged,
        onSearchTriggered = viewModel::onSearchTriggered,
        onRetry = viewModel::onRetry,
        onOpenItem = onOpenItem,
    )
}
