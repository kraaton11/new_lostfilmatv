package com.kraat.lostfilmnewtv.ui.home

import com.kraat.lostfilmnewtv.data.model.ReleaseSummary

data class HomeUiState(
    val items: List<ReleaseSummary> = emptyList(),
    val selectedItem: ReleaseSummary? = null,
    val selectedItemKey: String? = null,
    val showStaleBanner: Boolean = false,
    val isInitialLoading: Boolean = false,
    val isPaging: Boolean = false,
    val fullScreenErrorMessage: String? = null,
    val pagingErrorMessage: String? = null,
    val nextPage: Int = 1,
    val hasNextPage: Boolean = true,
)
