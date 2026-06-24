package com.kraat.lostfilmnewtv.ui.home

import com.kraat.lostfilmnewtv.data.model.ReleaseSummary

/**
 * Per-mode state that holds items, content state, pagination and visibility for a single
 * [HomeFeedMode]. Replaces the previously duplicated per-mode fields in [HomeUiState].
 */
data class HomeModeData(
    val items: List<ReleaseSummary> = emptyList(),
    val contentState: HomeModeContentState = HomeModeContentState.Loading,
    val isPaging: Boolean = false,
    val pagingErrorMessage: String? = null,
    val nextPage: Int = 1,
    val hasNextPage: Boolean = true,
    val isVisible: Boolean = true,
)

data class HomeUiState(
    val modeStates: Map<HomeFeedMode, HomeModeData> = defaultModeStates(),
    val rails: List<HomeContentRail> = fallbackHomeRails(
        modeStates[HomeFeedMode.AllNew]?.items ?: emptyList(),
    ),
    val selectedMode: HomeFeedMode = HomeFeedMode.AllNew,
    val availableModes: List<HomeFeedMode> = listOf(HomeFeedMode.AllNew),
    val favoriteSeriesCount: Int? = null,
    val rememberedItemKeyByMode: Map<HomeFeedMode, String> = emptyMap(),
    val selectedItem: ReleaseSummary? = null,
    val selectedItemKey: String? = null,
    val isInitialLoading: Boolean = false,
    val fullScreenErrorMessage: String? = null,
    val isHomeMenuLabelsEnabled: Boolean = true,
) {
    // Convenience read accessors — keep existing call sites compiling unchanged
    val items: List<ReleaseSummary> get() = modeData(HomeFeedMode.AllNew).items
    val favoriteItems: List<ReleaseSummary> get() = modeData(HomeFeedMode.Favorites).items
    val favoriteSeriesItems: List<ReleaseSummary> get() = modeData(HomeFeedMode.FavoriteSeries).items
    val movieItems: List<ReleaseSummary> get() = modeData(HomeFeedMode.Movies).items
    val seriesItems: List<ReleaseSummary> get() = modeData(HomeFeedMode.Series).items

    val allNewModeState: HomeModeContentState get() = modeData(HomeFeedMode.AllNew).contentState
    val favoritesModeState: HomeModeContentState get() = modeData(HomeFeedMode.Favorites).contentState
    val favoriteSeriesModeState: HomeModeContentState get() = modeData(HomeFeedMode.FavoriteSeries).contentState
    val moviesModeState: HomeModeContentState get() = modeData(HomeFeedMode.Movies).contentState
    val seriesModeState: HomeModeContentState get() = modeData(HomeFeedMode.Series).contentState

    val isPaging: Boolean get() = modeData(HomeFeedMode.AllNew).isPaging
    val isFavoritesPaging: Boolean get() = modeData(HomeFeedMode.Favorites).isPaging
    val isMoviesPaging: Boolean get() = modeData(HomeFeedMode.Movies).isPaging
    val isSeriesPaging: Boolean get() = modeData(HomeFeedMode.Series).isPaging

    val pagingErrorMessage: String? get() = modeData(HomeFeedMode.AllNew).pagingErrorMessage
    val favoritesPagingErrorMessage: String? get() = modeData(HomeFeedMode.Favorites).pagingErrorMessage
    val moviesPagingErrorMessage: String? get() = modeData(HomeFeedMode.Movies).pagingErrorMessage
    val seriesPagingErrorMessage: String? get() = modeData(HomeFeedMode.Series).pagingErrorMessage

    val nextPage: Int get() = modeData(HomeFeedMode.AllNew).nextPage
    val favoritesNextPage: Int get() = modeData(HomeFeedMode.Favorites).nextPage
    val moviesNextPage: Int get() = modeData(HomeFeedMode.Movies).nextPage
    val seriesNextPage: Int get() = modeData(HomeFeedMode.Series).nextPage

    val hasNextPage: Boolean get() = modeData(HomeFeedMode.AllNew).hasNextPage
    val favoritesHasNextPage: Boolean get() = modeData(HomeFeedMode.Favorites).hasNextPage
    val moviesHasNextPage: Boolean get() = modeData(HomeFeedMode.Movies).hasNextPage
    val seriesHasNextPage: Boolean get() = modeData(HomeFeedMode.Series).hasNextPage

    val isFavoritesRailVisible: Boolean get() = modeData(HomeFeedMode.Favorites).isVisible
    val isFavoriteSeriesModeVisible: Boolean get() = modeData(HomeFeedMode.FavoriteSeries).isVisible
    val isMoviesModeVisible: Boolean get() = modeData(HomeFeedMode.Movies).isVisible
    val isSeriesModeVisible: Boolean get() = modeData(HomeFeedMode.Series).isVisible

    fun modeData(mode: HomeFeedMode): HomeModeData =
        modeStates[mode] ?: HomeModeData()

    /** Update a single mode's data without touching any other mode. */
    fun updateMode(mode: HomeFeedMode, transform: (HomeModeData) -> HomeModeData): HomeUiState =
        copy(modeStates = modeStates + (mode to transform(modeData(mode))))

    /** Update multiple modes at once. */
    fun updateModes(vararg updates: Pair<HomeFeedMode, (HomeModeData) -> HomeModeData>): HomeUiState {
        val newMap = modeStates.toMutableMap()
        for ((mode, transform) in updates) {
            newMap[mode] = transform(newMap[mode] ?: HomeModeData())
        }
        return copy(modeStates = newMap)
    }
}

/**
 * Constructs [HomeUiState] from the flat per-mode field API used in tests and in migration code.
 * Mirrors the old constructor signature, mapping each per-mode field into [HomeModeData] entries.
 */
@Suppress("LongParameterList")
fun HomeUiState(
    items: List<ReleaseSummary> = emptyList(),
    favoriteItems: List<ReleaseSummary> = emptyList(),
    favoriteSeriesItems: List<ReleaseSummary> = emptyList(),
    movieItems: List<ReleaseSummary> = emptyList(),
    seriesItems: List<ReleaseSummary> = emptyList(),
    rails: List<HomeContentRail>? = null,
    selectedMode: HomeFeedMode = HomeFeedMode.AllNew,
    availableModes: List<HomeFeedMode> = listOf(HomeFeedMode.AllNew),
    allNewModeState: HomeModeContentState = HomeModeContentState.Loading,
    favoritesModeState: HomeModeContentState = HomeModeContentState.Loading,
    favoriteSeriesModeState: HomeModeContentState = HomeModeContentState.Loading,
    moviesModeState: HomeModeContentState = HomeModeContentState.Loading,
    seriesModeState: HomeModeContentState = HomeModeContentState.Loading,
    favoriteSeriesCount: Int? = null,
    rememberedItemKeyByMode: Map<HomeFeedMode, String> = emptyMap(),
    selectedItem: ReleaseSummary? = null,
    selectedItemKey: String? = null,
    isInitialLoading: Boolean = false,
    isPaging: Boolean = false,
    isFavoritesPaging: Boolean = false,
    isMoviesPaging: Boolean = false,
    isSeriesPaging: Boolean = false,
    fullScreenErrorMessage: String? = null,
    pagingErrorMessage: String? = null,
    favoritesPagingErrorMessage: String? = null,
    moviesPagingErrorMessage: String? = null,
    seriesPagingErrorMessage: String? = null,
    nextPage: Int = 1,
    hasNextPage: Boolean = true,
    favoritesNextPage: Int = 1,
    favoritesHasNextPage: Boolean = true,
    moviesNextPage: Int = 1,
    moviesHasNextPage: Boolean = true,
    seriesNextPage: Int = 1,
    seriesHasNextPage: Boolean = true,
    isFavoritesRailVisible: Boolean = false,
    isFavoriteSeriesModeVisible: Boolean = true,
    isMoviesModeVisible: Boolean = true,
    isSeriesModeVisible: Boolean = true,
    isHomeMenuLabelsEnabled: Boolean = true,
): HomeUiState {
    val modeStates = mapOf(
        HomeFeedMode.AllNew to HomeModeData(
            items = items,
            contentState = allNewModeState,
            isPaging = isPaging,
            pagingErrorMessage = pagingErrorMessage,
            nextPage = nextPage,
            hasNextPage = hasNextPage,
            isVisible = true,
        ),
        HomeFeedMode.Favorites to HomeModeData(
            items = favoriteItems,
            contentState = favoritesModeState,
            isPaging = isFavoritesPaging,
            pagingErrorMessage = favoritesPagingErrorMessage,
            nextPage = favoritesNextPage,
            hasNextPage = favoritesHasNextPage,
            isVisible = isFavoritesRailVisible,
        ),
        HomeFeedMode.FavoriteSeries to HomeModeData(
            items = favoriteSeriesItems,
            contentState = favoriteSeriesModeState,
            isVisible = isFavoriteSeriesModeVisible,
        ),
        HomeFeedMode.Movies to HomeModeData(
            items = movieItems,
            contentState = moviesModeState,
            isPaging = isMoviesPaging,
            pagingErrorMessage = moviesPagingErrorMessage,
            nextPage = moviesNextPage,
            hasNextPage = moviesHasNextPage,
            isVisible = isMoviesModeVisible,
        ),
        HomeFeedMode.Series to HomeModeData(
            items = seriesItems,
            contentState = seriesModeState,
            isPaging = isSeriesPaging,
            pagingErrorMessage = seriesPagingErrorMessage,
            nextPage = seriesNextPage,
            hasNextPage = seriesHasNextPage,
            isVisible = isSeriesModeVisible,
        ),
    )
    return HomeUiState(
        modeStates = modeStates,
        rails = rails ?: fallbackHomeRails(items),
        selectedMode = selectedMode,
        availableModes = availableModes,
        favoriteSeriesCount = favoriteSeriesCount,
        rememberedItemKeyByMode = rememberedItemKeyByMode,
        selectedItem = selectedItem,
        selectedItemKey = selectedItemKey,
        isInitialLoading = isInitialLoading,
        fullScreenErrorMessage = fullScreenErrorMessage,
        isHomeMenuLabelsEnabled = isHomeMenuLabelsEnabled,
    )
}

data class HomeFocusState(
    val rememberedItemKeyByMode: Map<HomeFeedMode, String> = emptyMap(),
    val selectedItemKey: String? = null,
) {
    companion object {
        fun from(uiState: HomeUiState): HomeFocusState {
            return HomeFocusState(
                rememberedItemKeyByMode = uiState.rememberedItemKeyByMode,
                selectedItemKey = uiState.selectedItemKey,
            )
        }
    }
}

internal fun HomeUiState.itemsForMode(mode: HomeFeedMode): List<ReleaseSummary> =
    modeData(mode).items

private fun defaultModeStates(): Map<HomeFeedMode, HomeModeData> =
    HomeFeedMode.entries.associateWith { HomeModeData() }
