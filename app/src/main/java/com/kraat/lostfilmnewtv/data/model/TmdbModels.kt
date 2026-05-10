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
    val rating: String? = null,
)

enum class TmdbEpisodeOverviewSource {
    TMDB_RU,
    TMDB_EN,
    MACHINE_TRANSLATED,
}

data class TmdbEpisodeOverview(
    val text: String,
    val source: TmdbEpisodeOverviewSource,
)

data class TmdbImageUrls(
    val posterUrl: String,
    val backdropUrl: String,
    val episodeOverviewRu: String? = null,
    val episodeOverviewSource: String? = null,
    val seriesOverviewRu: String? = null,
    val movieOverviewRu: String? = null,
    val rating: String? = null,
)
