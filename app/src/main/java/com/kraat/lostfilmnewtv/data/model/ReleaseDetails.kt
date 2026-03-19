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
    val fetchedAt: Long,
    val playEpisodeId: String? = null,
    val torrentLinks: List<TorrentLink> = emptyList(),
)

@Serializable
data class TorrentLink(
    val label: String,
    val url: String,
)
