package com.kraat.lostfilmnewtv.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_release_cache_metadata")
data class FavoriteReleaseCacheMetadataEntity(
    @PrimaryKey val id: Int = 1,
    val fetchedAt: Long,
    val favoriteSeriesCount: Int,
    val itemCount: Int,
)
