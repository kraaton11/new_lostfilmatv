package com.kraat.lostfilmnewtv.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

private const val TMDB_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
private const val TMDB_NEGATIVE_CACHE_TTL_MS = 24L * 60 * 60 * 1000

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
    val isNegative: Boolean = false,
) {
    fun isExpired(clock: () -> Long = { System.currentTimeMillis() }): Boolean {
        val ttl = if (isNegative) TMDB_NEGATIVE_CACHE_TTL_MS else TMDB_CACHE_TTL_MS
        return clock() - fetchedAt > ttl
    }

    companion object {
        fun create(
            detailsUrl: String,
            tmdbId: Int,
            tmdbType: String,
            posterUrl: String,
            backdropUrl: String,
            fetchedAt: Long = System.currentTimeMillis(),
            isNegative: Boolean = false,
        ) = TmdbPosterMappingEntity(
            detailsUrl = detailsUrl,
            tmdbId = tmdbId,
            tmdbType = tmdbType,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            fetchedAt = fetchedAt,
            isNegative = isNegative,
        )

        fun negative(
            detailsUrl: String,
            tmdbType: String,
            fetchedAt: Long = System.currentTimeMillis(),
        ) = create(
            detailsUrl = detailsUrl,
            tmdbId = 0,
            tmdbType = tmdbType,
            posterUrl = "",
            backdropUrl = "",
            fetchedAt = fetchedAt,
            isNegative = true,
        )
    }
}
