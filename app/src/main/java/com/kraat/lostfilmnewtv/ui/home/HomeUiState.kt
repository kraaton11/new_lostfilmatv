package com.kraat.lostfilmnewtv.ui.home

import com.kraat.lostfilmnewtv.data.model.ReleaseSummary

data class HomeUiState(
    val items: List<ReleaseSummary> = emptyList(),
    val favoriteItems: List<ReleaseSummary> = emptyList(),
    val movieItems: List<ReleaseSummary> = emptyList(),
    val seriesItems: List<ReleaseSummary> = emptyList(),
    val rails: List<HomeContentRail> = fallbackHomeRails(items),
    val selectedMode: HomeFeedMode = HomeFeedMode.AllNew,
    val availableModes: List<HomeFeedMode> = listOf(HomeFeedMode.AllNew),
    val allNewModeState: HomeModeContentState = HomeModeContentState.Loading,
    val favoritesModeState: HomeModeContentState = HomeModeContentState.Loading,
    val moviesModeState: HomeModeContentState = HomeModeContentState.Loading,
    val seriesModeState: HomeModeContentState = HomeModeContentState.Loading,
    val rememberedItemKeyByMode: Map<HomeFeedMode, String> = emptyMap(),
    val selectedItem: ReleaseSummary? = null,
    val selectedItemKey: String? = null,
    val isInitialLoading: Boolean = false,
    val isPaging: Boolean = false,
    val isMoviesPaging: Boolean = false,
    val isSeriesPaging: Boolean = false,
    val fullScreenErrorMessage: String? = null,
    val pagingErrorMessage: String? = null,
    val moviesPagingErrorMessage: String? = null,
    val seriesPagingErrorMessage: String? = null,
    val nextPage: Int = 1,
    val hasNextPage: Boolean = true,
    val moviesNextPage: Int = 1,
    val moviesHasNextPage: Boolean = true,
    val seriesNextPage: Int = 1,
    val seriesHasNextPage: Boolean = true,
    val isFavoritesRailVisible: Boolean = false,
)

internal fun HomeUiState.itemsForMode(mode: HomeFeedMode): List<ReleaseSummary> {
    return when (mode) {
        HomeFeedMode.AllNew -> items
        HomeFeedMode.Favorites -> favoriteItems
        HomeFeedMode.Movies -> movieItems
        HomeFeedMode.Series -> seriesItems
    }
}
