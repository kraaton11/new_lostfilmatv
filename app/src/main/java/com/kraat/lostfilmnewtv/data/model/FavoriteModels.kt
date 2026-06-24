package com.kraat.lostfilmnewtv.data.model

enum class FavoriteTargetKind {
    SERIES,
    MOVIE,
}

data class FavoriteMetadata(
    val targetId: Int,
    val targetKind: FavoriteTargetKind,
    val isFavorite: Boolean?,
)

sealed interface FavoriteToggleNetworkResult {
    data object ToggledOn : FavoriteToggleNetworkResult
    data object ToggledOff : FavoriteToggleNetworkResult
    data object Unknown : FavoriteToggleNetworkResult
}

sealed interface FavoriteMutationResult {
    data object Updated : FavoriteMutationResult
    data object NoOp : FavoriteMutationResult
    data class RequiresLogin(
        val message: String = "Войдите в LostFilm",
    ) : FavoriteMutationResult
    data class Error(
        val message: String,
    ) : FavoriteMutationResult
}

sealed interface FavoriteReleasesResult {
    data class Success(
        val items: List<ReleaseSummary>,
        val pageNumber: Int = 1,
        val hasNextPage: Boolean = false,
        val favoriteSeriesCount: Int? = null,
    ) : FavoriteReleasesResult

    /**
     * Emitted during the fan-out phase (page 1 only) as each favorite series finishes loading.
     * Items are unenriched (no TMDB posters yet). Final enriched results arrive with [Success].
     */
    data class Partial(
        val items: List<ReleaseSummary>,
        val favoriteSeriesCount: Int,
    ) : FavoriteReleasesResult

    data class Unavailable(
        val message: String? = null,
    ) : FavoriteReleasesResult
}

sealed interface FavoriteSeriesResult {
    data class Success(
        val items: List<ReleaseSummary>,
    ) : FavoriteSeriesResult

    data class Unavailable(
        val message: String? = null,
    ) : FavoriteSeriesResult
}
