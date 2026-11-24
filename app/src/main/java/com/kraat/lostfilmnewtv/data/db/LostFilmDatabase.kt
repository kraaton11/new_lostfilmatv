package com.kraat.lostfilmnewtv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ReleaseSummaryEntity::class,
        ReleaseDetailsEntity::class,
        PageCacheMetadataEntity::class,
        FavoriteReleaseCacheEntity::class,
        FavoriteReleaseCacheMetadataEntity::class,
    ],
    version = 23,
    exportSchema = false,
)
abstract class LostFilmDatabase : RoomDatabase() {
    abstract fun releaseDao(): ReleaseDao
}
