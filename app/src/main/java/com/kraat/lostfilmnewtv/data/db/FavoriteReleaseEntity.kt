package com.kraat.lostfilmnewtv.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary

@Entity(
    tableName = "favorite_releases",
    indices = [
        Index(value = ["fetchedAt"]),
    ],
)
data class FavoriteReleaseEntity(
    @PrimaryKey val detailsUrl: String,
    val kind: String,
    val titleRu: String,
    val episodeTitleRu: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val releaseDateRu: String,
    val posterUrl: String,
    val fetchedAt: Long,
    val isWatched: Boolean,
) {
    fun toModel(): ReleaseSummary = ReleaseSummary(
        id = detailsUrl,
        kind = ReleaseKind.valueOf(kind),
        titleRu = titleRu,
        episodeTitleRu = episodeTitleRu,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        releaseDateRu = releaseDateRu,
        posterUrl = posterUrl,
        detailsUrl = detailsUrl,
        pageNumber = 0,
        positionInPage = 0,
        fetchedAt = fetchedAt,
        isWatched = isWatched,
    )

    companion object {
        fun fromModel(model: ReleaseSummary): FavoriteReleaseEntity = FavoriteReleaseEntity(
            detailsUrl = model.detailsUrl,
            kind = model.kind.name,
            titleRu = model.titleRu,
            episodeTitleRu = model.episodeTitleRu,
            seasonNumber = model.seasonNumber,
            episodeNumber = model.episodeNumber,
            releaseDateRu = model.releaseDateRu,
            posterUrl = model.posterUrl,
            fetchedAt = model.fetchedAt,
            isWatched = model.isWatched,
        )
    }
}
