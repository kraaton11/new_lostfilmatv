package com.kraat.lostfilmnewtv.ui.search

import com.kraat.lostfilmnewtv.data.model.LostFilmSearchItem

data class SearchUiState(
    val query: String = "",
    val submittedQuery: String? = null,
    val items: List<LostFilmSearchItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
