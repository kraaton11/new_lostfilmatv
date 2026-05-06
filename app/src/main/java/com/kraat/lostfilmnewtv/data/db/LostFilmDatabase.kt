package com.kraat.lostfilmnewtv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ReleaseSummaryEntity::class,
        ReleaseDetailsEntity::class,
        PageCacheMetadataEntity::class,
        TmdbPosterMappingEntity::class,
    ],
    version = 14,
    exportSchema = false,
)
abstract class LostFilmDatabase : RoomDatabase() {
    abstract fun releaseDao(): ReleaseDao
    abstract fun tmdbPosterDao(): TmdbPosterDao
}
