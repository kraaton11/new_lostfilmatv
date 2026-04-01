package com.kraat.lostfilmnewtv.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface TmdbPosterDao {
    @Query("SELECT * FROM tmdb_poster_mappings WHERE detailsUrl = :detailsUrl LIMIT 1")
    suspend fun getByDetailsUrl(detailsUrl: String): TmdbPosterMappingEntity?

    @Upsert
    suspend fun upsert(entity: TmdbPosterMappingEntity)

    @Query("DELETE FROM tmdb_poster_mappings WHERE fetchedAt < :threshold")
    suspend fun deleteExpired(threshold: Long)
}
