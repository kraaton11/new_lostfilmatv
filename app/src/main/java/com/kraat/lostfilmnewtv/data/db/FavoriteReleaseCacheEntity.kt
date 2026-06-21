package com.kraat.lostfilmnewtv.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary

@Entity(
    tableName = "favorite_release_cache",
    indices = [Index(value = ["fetchedAt"])],
)
data class FavoriteReleaseCacheEntity(
    @PrimaryKey val detailsUrl: String,
    val kind: String,
    val titleRu: String,
    val episodeTitleRu: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val releaseDateRu: String,
    val posterUrl: String,
    val backdropUrl: String? = null,
    val positionInList: Int,
    val fetchedAt: Long,
    val isWatched: Boolean,
    val episodeOverviewRu: String? = null,
    val episodeOverviewSource: String? = null,
    val seriesOverviewRu: String? = null,
    val movieOverviewRu: String? = null,
    val tmdbRating: String? = null,
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
        backdropUrl = backdropUrl,
        detailsUrl = detailsUrl,
        pageNumber = 0,
        positionInPage = positionInList,
        fetchedAt = fetchedAt,
        isWatched = isWatched,
        episodeOverviewRu = episodeOverviewRu,
        episodeOverviewSource = episodeOverviewSource,
        seriesOverviewRu = seriesOverviewRu,
        movieOverviewRu = movieOverviewRu,
        tmdbRating = tmdbRating,
    )

    companion object {
        fun fromModel(model: ReleaseSummary, positionInList: Int): FavoriteReleaseCacheEntity =
            FavoriteReleaseCacheEntity(
                detailsUrl = model.detailsUrl,
                kind = model.kind.name,
                titleRu = model.titleRu,
                episodeTitleRu = model.episodeTitleRu,
                seasonNumber = model.seasonNumber,
                episodeNumber = model.episodeNumber,
                releaseDateRu = model.releaseDateRu,
                posterUrl = model.posterUrl,
                backdropUrl = model.backdropUrl,
                positionInList = positionInList,
                fetchedAt = model.fetchedAt,
                isWatched = model.isWatched,
                episodeOverviewRu = model.episodeOverviewRu,
                episodeOverviewSource = model.episodeOverviewSource,
                seriesOverviewRu = model.seriesOverviewRu,
                movieOverviewRu = model.movieOverviewRu,
                tmdbRating = model.tmdbRating,
            )
    }
}
