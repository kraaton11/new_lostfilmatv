package com.kraat.lostfilmnewtv.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kraat.lostfilmnewtv.data.model.FavoriteTargetKind
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.TorrentLink
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(
    tableName = "release_details",
    indices = [Index(value = ["fetchedAt"])],
)
data class ReleaseDetailsEntity(
    @PrimaryKey val detailsUrl: String,
    val kind: String,
    val titleRu: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val releaseDateRu: String,
    val posterUrl: String,
    val backdropUrl: String?,
    val fetchedAt: Long,
    val playEpisodeId: String?,
    val torrentLinksJson: String?,
    val seriesStatusRu: String?,
    val favoriteTargetId: Int?,
    val favoriteTargetKind: String?,
    val isFavorite: Boolean?,
) {
    fun toModel(): ReleaseDetails = ReleaseDetails(
        detailsUrl = detailsUrl,
        kind = ReleaseKind.valueOf(kind),
        titleRu = titleRu,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        releaseDateRu = releaseDateRu,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        fetchedAt = fetchedAt,
        playEpisodeId = playEpisodeId,
        torrentLinks = torrentLinksJson.decodeTorrentLinks(),
        seriesStatusRu = seriesStatusRu,
        favoriteTargetId = favoriteTargetId,
        favoriteTargetKind = favoriteTargetKind?.let(FavoriteTargetKind::valueOf),
        isFavorite = isFavorite,
    )

    companion object {
        fun fromModel(model: ReleaseDetails): ReleaseDetailsEntity = ReleaseDetailsEntity(
            detailsUrl = model.detailsUrl,
            kind = model.kind.name,
            titleRu = model.titleRu,
            seasonNumber = model.seasonNumber,
            episodeNumber = model.episodeNumber,
            releaseDateRu = model.releaseDateRu,
            posterUrl = model.posterUrl,
            backdropUrl = model.backdropUrl,
            fetchedAt = model.fetchedAt,
            playEpisodeId = model.playEpisodeId,
            torrentLinksJson = model.torrentLinks.encodeTorrentLinks(),
            seriesStatusRu = model.seriesStatusRu,
            favoriteTargetId = model.favoriteTargetId,
            favoriteTargetKind = model.favoriteTargetKind?.name,
            isFavorite = model.isFavorite,
        )
    }
}

private fun String?.decodeTorrentLinks(): List<TorrentLink> {
    if (this.isNullOrBlank()) {
        return emptyList()
    }
    return Json.decodeFromString<List<TorrentLink>>(this)
}

private fun List<TorrentLink>.encodeTorrentLinks(): String? {
    if (isEmpty()) {
        return null
    }
    return Json.encodeToString(this)
}
