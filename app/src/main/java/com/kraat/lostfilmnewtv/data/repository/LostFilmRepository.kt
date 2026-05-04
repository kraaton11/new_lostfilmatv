package com.kraat.lostfilmnewtv.data.repository

import com.kraat.lostfilmnewtv.data.model.FavoriteMutationResult
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.LostFilmSearchItem
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.SeriesGuide
import com.kraat.lostfilmnewtv.data.model.SeriesOverview

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

sealed interface SeriesOverviewResult {
    data class Success(
        val overview: SeriesOverview,
    ) : SeriesOverviewResult

    data class Error(
        val message: String,
    ) : SeriesOverviewResult
}

sealed interface SearchResultsResult {
    data class Success(
        val query: String,
        val items: List<LostFilmSearchItem>,
    ) : SearchResultsResult

    data class Error(
        val query: String,
        val message: String,
    ) : SearchResultsResult
}

interface LostFilmRepository {
    suspend fun loadPage(pageNumber: Int): PageState

    suspend fun loadMovies(pageNumber: Int = 1): PageState =
        PageState.Error(pageNumber = pageNumber, message = "Фильмы недоступны")

    suspend fun loadDetails(detailsUrl: String): DetailsResult

    suspend fun loadSeriesGuide(detailsUrl: String): SeriesGuideResult

    suspend fun loadSeriesOverview(detailsUrl: String): SeriesOverviewResult =
        SeriesOverviewResult.Error("Обзор недоступен")

    suspend fun search(query: String): SearchResultsResult =
        SearchResultsResult.Error(query, "Поиск недоступен")

    suspend fun loadWatchedState(detailsUrl: String): Boolean?

    suspend fun setEpisodeWatched(detailsUrl: String, playEpisodeId: String, targetWatched: Boolean): Boolean?

    suspend fun setFavorite(detailsUrl: String, targetFavorite: Boolean): FavoriteMutationResult

    suspend fun loadFavoriteReleases(): FavoriteReleasesResult
}
