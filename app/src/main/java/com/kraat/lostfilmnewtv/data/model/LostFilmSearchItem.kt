package com.kraat.lostfilmnewtv.data.model

data class LostFilmSearchItem(
    val titleRu: String,
    val titleEn: String? = null,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val targetUrl: String,
    val kind: ReleaseKind,
    val tmdbRating: String? = null,
)
