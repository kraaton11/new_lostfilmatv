package com.kraat.lostfilmnewtv.ui.home

import com.kraat.lostfilmnewtv.data.model.ReleaseSummary

data class HomeUiState(
    val items: List<ReleaseSummary> = emptyList(),
    val favoriteItems: List<ReleaseSummary> = emptyList(),
    val rails: List<HomeContentRail> = fallbackHomeRails(items),
    val selectedMode: HomeFeedMode = HomeFeedMode.AllNew,
    val availableModes: List<HomeFeedMode> = listOf(HomeFeedMode.AllNew),
    val allNewModeState: HomeModeContentState = HomeModeContentState.Loading,
    val favoritesModeState: HomeModeContentState = HomeModeContentState.Loading,
    val rememberedItemKeyByMode: Map<HomeFeedMode, String> = emptyMap(),
    val selectedItem: ReleaseSummary? = null,
    val selectedItemKey: String? = null,
    val showStaleBanner: Boolean = false,
    val isInitialLoading: Boolean = false,
    val isPaging: Boolean = false,
    val fullScreenErrorMessage: String? = null,
    val pagingErrorMessage: String? = null,
    val nextPage: Int = 1,
    val hasNextPage: Boolean = true,
    val isFavoritesRailVisible: Boolean = false,
)

internal fun HomeUiState.itemsForMode(mode: HomeFeedMode): List<ReleaseSummary> {
    return when (mode) {
        HomeFeedMode.AllNew -> items
        HomeFeedMode.Favorites -> favoriteItems
    }
}
