package com.kraat.lostfilmnewtv.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface FavoriteReleaseDao {
    @Query(
        """
        SELECT * FROM favorite_releases
        ORDER BY fetchedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun getLatestFavorites(limit: Int): List<FavoriteReleaseEntity>

    @Query("SELECT * FROM favorite_releases WHERE detailsUrl = :detailsUrl LIMIT 1")
    suspend fun getFavorite(detailsUrl: String): FavoriteReleaseEntity?

    @Query("UPDATE favorite_releases SET isWatched = :isWatched WHERE detailsUrl = :detailsUrl")
    suspend fun updateWatched(detailsUrl: String, isWatched: Boolean)

    @Upsert
    suspend fun upsertFavorites(favorites: List<FavoriteReleaseEntity>)

    @Query("DELETE FROM favorite_releases")
    suspend fun deleteAllFavorites()

    @Query("DELETE FROM favorite_releases WHERE fetchedAt < :threshold")
    suspend fun deleteExpiredFavorites(threshold: Long)

    @Transaction
    suspend fun replaceAllFavorites(
        favorites: List<FavoriteReleaseEntity>,
    ) {
        deleteAllFavorites()
        upsertFavorites(favorites)
    }
}
