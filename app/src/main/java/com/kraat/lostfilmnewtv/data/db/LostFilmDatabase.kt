package com.kraat.lostfilmnewtv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ReleaseSummaryEntity::class,
        ReleaseDetailsEntity::class,
        PageCacheMetadataEntity::class,
        TmdbPosterMappingEntity::class,
        FavoriteReleaseEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class LostFilmDatabase : RoomDatabase() {
    abstract fun releaseDao(): ReleaseDao
    abstract fun tmdbPosterDao(): TmdbPosterDao
    abstract fun favoriteReleaseDao(): FavoriteReleaseDao
}
