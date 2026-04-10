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
    ) : FavoriteReleasesResult

    data class Unavailable(
        val message: String? = null,
    ) : FavoriteReleasesResult
}
