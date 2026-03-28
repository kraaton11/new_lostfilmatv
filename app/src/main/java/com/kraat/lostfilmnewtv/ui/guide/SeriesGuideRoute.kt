package com.kraat.lostfilmnewtv.ui.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.navigation.AppDestination

@Composable
fun SeriesGuideRoute(
    detailsUrl: String,
    repository: LostFilmRepository,
    onOpenDetails: (String) -> Unit,
) {
    val viewModel: SeriesGuideViewModel = viewModel(
        key = "series-guide:$detailsUrl",
        factory = seriesGuideViewModelFactory(repository, detailsUrl),
    )
    val state = viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(detailsUrl) {
        viewModel.onStart()
    }

    SeriesGuideScreen(
        state = state.value,
        onRetry = viewModel::onRetry,
        onEpisodeClick = onOpenDetails,
    )
}

private fun seriesGuideViewModelFactory(
    repository: LostFilmRepository,
    detailsUrl: String,
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SeriesGuideViewModel(
                repository = repository,
                savedStateHandle = SavedStateHandle(
                    mapOf(AppDestination.SeriesGuide.detailsUrlArg to detailsUrl),
                ),
            ) as T
        }
    }
}
