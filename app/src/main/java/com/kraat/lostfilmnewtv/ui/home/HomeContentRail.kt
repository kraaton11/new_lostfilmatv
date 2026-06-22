package com.kraat.lostfilmnewtv.ui.home

import com.kraat.lostfilmnewtv.data.model.ReleaseSummary

const val HOME_RAIL_ALL_NEW = "all-new"
const val HOME_RAIL_FAVORITES = "favorites"
const val HOME_RAIL_FAVORITE_SERIES = "favorite-series"
const val HOME_RAIL_MOVIES = "movies"
const val HOME_RAIL_SERIES = "series"

private const val HOME_ITEM_KEY_SEPARATOR = "::"

data class HomeContentRail(
    val id: String,
    val title: String,
    val items: List<ReleaseSummary>,
)

fun homeItemKey(railId: String, detailsUrl: String): String {
    return "$railId$HOME_ITEM_KEY_SEPARATOR$detailsUrl"
}

internal fun fallbackHomeRails(items: List<ReleaseSummary>): List<HomeContentRail> {
    if (items.isEmpty()) return emptyList()
    return listOf(
        HomeContentRail(
            id = HOME_RAIL_ALL_NEW,
            title = "Новые релизы",
            items = items,
        ),
    )
}

internal fun buildHomeRails(
    items: List<ReleaseSummary>,
    favoriteItems: List<ReleaseSummary>,
    favoriteSeriesItems: List<ReleaseSummary> = emptyList(),
    movieItems: List<ReleaseSummary> = emptyList(),
    seriesItems: List<ReleaseSummary> = emptyList(),
    isFavoritesRailVisible: Boolean,
    isFavoriteSeriesVisible: Boolean = true,
    isMoviesVisible: Boolean = true,
    isSeriesVisible: Boolean = true,
): List<HomeContentRail> {
    val rails = mutableListOf<HomeContentRail>()
    if (items.isNotEmpty()) {
        rails += HomeContentRail(
            id = HOME_RAIL_ALL_NEW,
            title = "Новые релизы",
            items = items,
        )
    }
    if (isFavoritesRailVisible && favoriteItems.isNotEmpty()) {
        rails += HomeContentRail(
            id = HOME_RAIL_FAVORITES,
            title = "Избранное",
            items = favoriteItems,
        )
    }
    if (isFavoriteSeriesVisible && favoriteSeriesItems.isNotEmpty()) {
        rails += HomeContentRail(
            id = HOME_RAIL_FAVORITE_SERIES,
            title = "Мои сериалы",
            items = favoriteSeriesItems,
        )
    }
    if (isMoviesVisible && movieItems.isNotEmpty()) {
        rails += HomeContentRail(
            id = HOME_RAIL_MOVIES,
            title = "Фильмы",
            items = movieItems,
        )
    }
    if (isSeriesVisible && seriesItems.isNotEmpty()) {
        rails += HomeContentRail(
            id = HOME_RAIL_SERIES,
            title = "Сериалы",
            items = seriesItems,
        )
    }
    return rails
}

internal fun railIdFromItemKey(itemKey: String?): String? {
    if (itemKey.isNullOrBlank() || !itemKey.contains(HOME_ITEM_KEY_SEPARATOR)) return null
    return itemKey.substringBefore(HOME_ITEM_KEY_SEPARATOR)
}
