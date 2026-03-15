package com.kraat.lostfilmnewtv.data.model

data class ReleaseDetails(
    val detailsUrl: String,
    val kind: ReleaseKind,
    val titleRu: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val releaseDateRu: String,
    val posterUrl: String,
    val fetchedAt: Long,
)
