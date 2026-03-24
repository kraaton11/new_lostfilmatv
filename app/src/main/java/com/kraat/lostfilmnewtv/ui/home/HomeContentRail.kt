package com.kraat.lostfilmnewtv.ui.home

import com.kraat.lostfilmnewtv.data.model.ReleaseSummary

const val HOME_RAIL_ALL_NEW = "all-new"
const val HOME_RAIL_FAVORITES = "favorites"

private const val HOME_ITEM_KEY_SEPARATOR = "::"

data class HomeContentRail(
    val id: String,
    val title: String,
    val items: List<ReleaseSummary>,
)

internal data class HomeRailItemMatch(
    val rail: HomeContentRail,
    val item: ReleaseSummary,
    val key: String,
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
    isFavoritesRailVisible: Boolean,
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
    return rails
}

internal fun normalizeHomeItemKey(
    rails: List<HomeContentRail>,
    preferredKey: String?,
): String? {
    return findHomeRailItem(rails, preferredKey)?.key
}

internal fun findHomeRailItem(
    rails: List<HomeContentRail>,
    preferredKey: String?,
): HomeRailItemMatch? {
    if (rails.isEmpty()) return null

    if (!preferredKey.isNullOrBlank() && preferredKey.contains(HOME_ITEM_KEY_SEPARATOR)) {
        val preferredRailId = preferredKey.substringBefore(HOME_ITEM_KEY_SEPARATOR)
        val preferredDetailsUrl = preferredKey.substringAfter(HOME_ITEM_KEY_SEPARATOR, "")
        val exactMatch = rails.firstNotNullOfOrNull { rail ->
            if (rail.id != preferredRailId) {
                null
            } else {
                rail.items.firstOrNull { it.detailsUrl == preferredDetailsUrl }?.let { item ->
                    HomeRailItemMatch(
                        rail = rail,
                        item = item,
                        key = homeItemKey(rail.id, item.detailsUrl),
                    )
                }
            }
        }
        if (exactMatch != null) {
            return exactMatch
        }
    }

    val preferredDetailsUrl = preferredDetailsUrl(preferredKey)
    if (!preferredDetailsUrl.isNullOrBlank()) {
        val detailsMatch = rails.firstNotNullOfOrNull { rail ->
            rail.items.firstOrNull { it.detailsUrl == preferredDetailsUrl }?.let { item ->
                HomeRailItemMatch(
                    rail = rail,
                    item = item,
                    key = homeItemKey(rail.id, item.detailsUrl),
                )
            }
        }
        if (detailsMatch != null) {
            return detailsMatch
        }
    }

    val firstRail = rails.firstOrNull() ?: return null
    val firstItem = firstRail.items.firstOrNull() ?: return null
    return HomeRailItemMatch(
        rail = firstRail,
        item = firstItem,
        key = homeItemKey(firstRail.id, firstItem.detailsUrl),
    )
}

internal fun railIdFromItemKey(itemKey: String?): String? {
    if (itemKey.isNullOrBlank() || !itemKey.contains(HOME_ITEM_KEY_SEPARATOR)) return null
    return itemKey.substringBefore(HOME_ITEM_KEY_SEPARATOR)
}

private fun preferredDetailsUrl(itemKey: String?): String? {
    if (itemKey.isNullOrBlank()) return null
    return if (itemKey.contains(HOME_ITEM_KEY_SEPARATOR)) {
        itemKey.substringAfter(HOME_ITEM_KEY_SEPARATOR, "")
    } else {
        itemKey
    }
}
