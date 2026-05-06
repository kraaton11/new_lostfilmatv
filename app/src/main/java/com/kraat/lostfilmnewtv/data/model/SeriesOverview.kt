package com.kraat.lostfilmnewtv.data.model

data class SeriesOverview(
    val seriesUrl: String,
    val titleRu: String,
    val titleEn: String? = null,
    val statusRu: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val premiereDateRu: String? = null,
    val channelCountryRu: String? = null,
    val imdbRating: String? = null,
    val tmdbRating: String? = null,
    val genresRu: String? = null,
    val typesRu: String? = null,
    val officialSiteUrl: String? = null,
    val descriptionRu: String? = null,
    val plotRu: String? = null,
)
