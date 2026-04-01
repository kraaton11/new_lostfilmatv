package com.kraat.lostfilmnewtv.data.model

enum class TmdbMediaType {
    TV,
    MOVIE,
}

data class TmdbSearchResult(
    val id: Int,
    val name: String,
    val popularity: Double,
)

data class TmdbImageUrls(
    val posterUrl: String,
    val backdropUrl: String,
)
