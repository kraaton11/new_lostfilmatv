package com.kraat.lostfilmnewtv.ui.details

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails

data class DetailsUiState(
    val details: ReleaseDetails? = null,
    val isLoading: Boolean = false,
    val showStaleBanner: Boolean = false,
    val errorMessage: String? = null,
)
