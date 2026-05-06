package com.kraat.lostfilmnewtv.ui.overview

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails

data class MovieOverviewUiState(
    val details: ReleaseDetails? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
