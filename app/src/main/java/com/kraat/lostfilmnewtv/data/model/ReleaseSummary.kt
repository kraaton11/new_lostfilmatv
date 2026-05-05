package com.kraat.lostfilmnewtv.data.model

data class ReleaseSummary(
    val id: String,
    val kind: ReleaseKind,
    val titleRu: String,
    val episodeTitleRu: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val releaseDateRu: String,
    val posterUrl: String,
    val detailsUrl: String,
    val pageNumber: Int,
    val positionInPage: Int,
    val fetchedAt: Long,
    val isWatched: Boolean = false,
    val availabilityLabel: String? = null,
    val originalReleaseYear: Int? = null,
    val backdropUrl: String? = null,
    val episodeOverviewRu: String? = null,
)
