package com.kraat.lostfilmnewtv.data.model

sealed interface PageState {
    data class Content(
        val pageNumber: Int,
        val items: List<ReleaseSummary>,
        val hasNextPage: Boolean,
        val isStale: Boolean,
        val pagingErrorMessage: String? = null,
        val isAppend: Boolean = false,
    ) : PageState

    data class Error(
        val pageNumber: Int,
        val message: String,
        val retryable: Boolean = true,
    ) : PageState
}
