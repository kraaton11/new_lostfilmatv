package com.kraat.lostfilmnewtv.data.model

enum class TmdbMediaType {
    TV,
    MOVIE,
}

data class TmdbSearchResult(
    val id: Int,
    val name: String,
    val popularity: Double,
    val originalName: String = name,
    val releaseYear: Int? = null,
)

data class TmdbImageUrls(
    val posterUrl: String,
    val backdropUrl: String,
    val episodeOverviewRu: String? = null,
)
