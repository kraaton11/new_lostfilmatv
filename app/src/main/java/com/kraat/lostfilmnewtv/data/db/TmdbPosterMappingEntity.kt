package com.kraat.lostfilmnewtv.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

private const val TMDB_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000

@Entity(
    tableName = "tmdb_poster_mappings",
    indices = [Index(value = ["fetchedAt"])],
)
data class TmdbPosterMappingEntity(
    @PrimaryKey val detailsUrl: String,
    val tmdbId: Int,
    val tmdbType: String,
    val posterUrl: String,
    val backdropUrl: String,
    val fetchedAt: Long,
) {
    fun isExpired(clock: () -> Long = { System.currentTimeMillis() }): Boolean {
        return clock() - fetchedAt > TMDB_CACHE_TTL_MS
    }

    companion object {
        fun create(
            detailsUrl: String,
            tmdbId: Int,
            tmdbType: String,
            posterUrl: String,
            backdropUrl: String,
            fetchedAt: Long = System.currentTimeMillis(),
        ) = TmdbPosterMappingEntity(
            detailsUrl = detailsUrl,
            tmdbId = tmdbId,
            tmdbType = tmdbType,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            fetchedAt = fetchedAt,
        )
    }
}
