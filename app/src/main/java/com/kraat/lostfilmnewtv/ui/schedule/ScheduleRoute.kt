package com.kraat.lostfilmnewtv.ui.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kraat.lostfilmnewtv.data.model.ScheduleItem

@Composable
fun ScheduleRoute(
    onOpenItem: (ScheduleItem) -> Unit,
) {
    val viewModel: ScheduleViewModel = hiltViewModel()
    val state = viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onStart() }

    ScheduleScreen(
        state = state.value,
        onRetry = viewModel::onRetry,
        onOpenItem = onOpenItem,
    )
}
