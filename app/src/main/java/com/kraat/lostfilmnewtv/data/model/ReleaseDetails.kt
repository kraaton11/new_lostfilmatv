package com.kraat.lostfilmnewtv.data.model

import kotlinx.serialization.Serializable

data class ReleaseDetails(
    val detailsUrl: String,
    val kind: ReleaseKind,
    val titleRu: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val releaseDateRu: String,
    val posterUrl: String,
    val backdropUrl: String? = null,
    val fetchedAt: Long,
    val playEpisodeId: String? = null,
    val torrentLinks: List<TorrentLink> = emptyList(),
    val episodeTitleRu: String? = null,
    val seriesStatusRu: String? = null,
    val favoriteTargetId: Int? = null,
    val favoriteTargetKind: FavoriteTargetKind? = null,
    val isFavorite: Boolean? = null,
    val originalReleaseYear: Int? = null,
    val episodeOverviewRu: String? = null,
)

@Serializable
data class TorrentLink(
    val label: String,
    val url: String,
)
