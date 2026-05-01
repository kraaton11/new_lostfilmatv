package com.kraat.lostfilmnewtv.ui.details

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails

data class DetailsUiState(
    val details: ReleaseDetails? = null,
    val isLoading: Boolean = false,
    val showStaleBanner: Boolean = false,
    val errorMessage: String? = null,
    val isWatched: Boolean? = null,
    val isWatchedStateLoading: Boolean = false,
    val isWatchedMutationInFlight: Boolean = false,
    val watchedStatusMessage: String? = null,
    val watchedActionLabel: String = "",
    val isWatchedActionEnabled: Boolean = false,
    val watchedContentVersion: Int = 0,
    val isFavoriteMutationInFlight: Boolean = false,
    val favoriteStatusMessage: String? = null,
    val favoriteActionLabel: String = "",
    val isFavoriteActionEnabled: Boolean = false,
    val favoriteContentVersion: Int = 0,
)
