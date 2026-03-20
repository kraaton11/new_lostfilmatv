package com.kraat.lostfilmnewtv.data.repository

import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails

sealed interface DetailsResult {
    data class Success(
        val details: ReleaseDetails,
        val isStale: Boolean,
    ) : DetailsResult

    data class Error(
        val detailsUrl: String,
        val message: String,
    ) : DetailsResult
}

interface LostFilmRepository {
    suspend fun loadPage(pageNumber: Int): PageState

    suspend fun loadDetails(detailsUrl: String): DetailsResult

    suspend fun markEpisodeWatched(detailsUrl: String, playEpisodeId: String): Boolean
}
