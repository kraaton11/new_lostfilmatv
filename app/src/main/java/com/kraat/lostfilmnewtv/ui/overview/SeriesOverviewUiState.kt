package com.kraat.lostfilmnewtv.ui.overview

import com.kraat.lostfilmnewtv.data.model.SeriesOverview

data class SeriesOverviewUiState(
    val overview: SeriesOverview? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
