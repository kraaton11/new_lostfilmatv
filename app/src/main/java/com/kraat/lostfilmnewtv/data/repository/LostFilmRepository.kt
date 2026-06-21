package com.kraat.lostfilmnewtv.data.repository

import com.kraat.lostfilmnewtv.data.model.FavoriteMutationResult
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.FavoriteSeriesResult
import com.kraat.lostfilmnewtv.data.model.LostFilmSearchItem
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ScheduleMonth
import com.kraat.lostfilmnewtv.data.model.SeriesGuide
import com.kraat.lostfilmnewtv.data.model.SeriesOverview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow

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

sealed interface ScheduleResult {
    data class Success(
        val schedule: ScheduleMonth,
    ) : ScheduleResult

    data class Error(
        val message: String,
    ) : ScheduleResult
}

interface LostFilmRepository {
    suspend fun loadPage(pageNumber: Int): PageState

    /**
     * Stale-while-revalidate для главного экрана: первая эмиссия — кэш из Room
     * (если он есть, с `isStale=true`), вторая — свежий результат [loadPage].
     * Реализация по умолчанию эмитит только свежий результат — перекрывается в
     * реальной реализации.
     */
    fun observeNewReleases(pageNumber: Int = 1): Flow<PageState> = flow {
        emit(loadPage(pageNumber))
    }

    suspend fun loadMovies(pageNumber: Int = 1): PageState =
        PageState.Error(pageNumber = pageNumber, message = "Фильмы недоступны")

    suspend fun loadSeriesCatalog(pageNumber: Int = 1): PageState =
        PageState.Error(pageNumber = pageNumber, message = "Каталог сериалов недоступен")

    suspend fun loadDetails(detailsUrl: String): DetailsResult

    suspend fun loadDetailsPreview(detailsUrl: String): DetailsResult =
        loadDetails(detailsUrl)

    suspend fun refreshDetailsExtras(details: ReleaseDetails): DetailsResult =
        DetailsResult.Success(details = details, isStale = false)

    suspend fun loadSeriesGuide(detailsUrl: String): SeriesGuideResult

    suspend fun loadSeriesOverview(detailsUrl: String): SeriesOverviewResult =
        SeriesOverviewResult.Error("Обзор недоступен")

    suspend fun search(query: String): SearchResultsResult =
        SearchResultsResult.Error(query, "Поиск недоступен")

    suspend fun loadSchedule(): ScheduleResult =
        ScheduleResult.Error("Расписание недоступно")

    suspend fun loadWatchedState(detailsUrl: String): Boolean?

    suspend fun setEpisodeWatched(detailsUrl: String, playEpisodeId: String, targetWatched: Boolean): Boolean?

    suspend fun setFavorite(detailsUrl: String, targetFavorite: Boolean): FavoriteMutationResult

    fun observeFavoriteReleases(pageNumber: Int = 1): Flow<FavoriteReleasesResult> =
        channelFlow { send(FavoriteReleasesResult.Unavailable()) }

    suspend fun loadFavoriteSeries(): FavoriteSeriesResult =
        FavoriteSeriesResult.Unavailable()
}
