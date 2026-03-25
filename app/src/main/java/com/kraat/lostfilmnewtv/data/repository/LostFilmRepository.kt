package com.kraat.lostfilmnewtv.data.repository

import com.kraat.lostfilmnewtv.data.model.FavoriteMutationResult
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.SeriesGuide

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

sealed interface SeriesGuideResult {
    data class Success(
        val guide: SeriesGuide,
    ) : SeriesGuideResult

    data class Error(
        val message: String,
    ) : SeriesGuideResult
}

interface LostFilmRepository {
    suspend fun loadPage(pageNumber: Int): PageState

    suspend fun loadDetails(detailsUrl: String): DetailsResult

    suspend fun loadSeriesGuide(detailsUrl: String): SeriesGuideResult

    suspend fun markEpisodeWatched(detailsUrl: String, playEpisodeId: String): Boolean

    suspend fun setFavorite(detailsUrl: String, targetFavorite: Boolean): FavoriteMutationResult

    suspend fun loadFavoriteReleases(): FavoriteReleasesResult
}
