package com.kraat.lostfilmnewtv.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграции БД. Весь кэш можно пересоздать из сети — используем DROP+CREATE
 * вместо ALTER TABLE там, где схема изменилась существенно.
 *
 * Добавляйте новую миграцию при каждом изменении version в LostFilmDatabase.
 * Шаблон:
 *   val MIGRATION_X_Y = object : Migration(X, Y) {
 *       override fun migrate(db: SupportSQLiteDatabase) { ... }
 *   }
 */

private const val RELEASE_DETAILS_TABLE_SQL = """
    CREATE TABLE IF NOT EXISTS `release_details` (
        `detailsUrl` TEXT NOT NULL,
        `kind` TEXT NOT NULL,
        `titleRu` TEXT NOT NULL,
        `seasonNumber` INTEGER,
        `episodeNumber` INTEGER,
        `releaseDateRu` TEXT NOT NULL,
        `posterUrl` TEXT NOT NULL,
        `backdropUrl` TEXT,
        `fetchedAt` INTEGER NOT NULL,
        `playEpisodeId` TEXT,
        `torrentLinksJson` TEXT,
        `seriesStatusRu` TEXT,
        `favoriteTargetId` INTEGER,
        `favoriteTargetKind` TEXT,
        `isFavorite` INTEGER,
        `episodeOverviewRu` TEXT,
        `tmdbRating` TEXT,
        PRIMARY KEY(`detailsUrl`)
    )
"""

/**
 * Миграция 5→6: схема хранения torrent-ссылок изменилась.
 * Кэш деталей несущественен — пересоздаём таблицу.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `release_details`")
        db.execSQL(RELEASE_DETAILS_TABLE_SQL.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_release_details_fetchedAt` ON `release_details` (`fetchedAt`)")
    }
}

/**
 * Миграция 6→7: добавлен статус сериала в кэш деталей.
 * Таблица деталей остаётся пересоздаваемым кэшем, поэтому мигрируем через DROP+CREATE.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `release_details`")
        db.execSQL(RELEASE_DETAILS_TABLE_SQL.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_release_details_fetchedAt` ON `release_details` (`fetchedAt`)")
    }
}

/**
 * Миграция 7→8: добавлен negative cache для TMDB, чтобы не повторять пустые search-результаты.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tmdb_poster_mappings` ADD COLUMN `isNegative` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Миграция 8→9: wide backdrop из TMDB для hero-сцены на главном экране.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `release_summaries` ADD COLUMN `backdropUrl` TEXT")
    }
}

/**
 * Миграция 9→10: русское описание эпизода из TMDB для hero-сцены на главном экране.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `release_summaries` ADD COLUMN `episodeOverviewRu` TEXT")
        db.execSQL("ALTER TABLE `release_details` ADD COLUMN `episodeOverviewRu` TEXT")
    }
}

/**
 * Миграция 10→11: поле episodeOverviewRu было добавлено и в кэш деталей.
 * На dev-устройствах могла уже существовать version 10 только с release_summaries.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn(tableName = "release_details", columnName = "episodeOverviewRu")) {
            db.execSQL("ALTER TABLE `release_details` ADD COLUMN `episodeOverviewRu` TEXT")
        }
    }
}

/**
 * Миграция 11→12: русское описание сериала из TMDB для hero-сцены на главном экране.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `release_summaries` ADD COLUMN `seriesOverviewRu` TEXT")
    }
}

/**
 * Миграция 12→13: русское описание фильма из TMDB для hero-сцены на главном экране.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `release_summaries` ADD COLUMN `movieOverviewRu` TEXT")
    }
}

/**
 * Миграция 13→14: рейтинг TMDB для ленты, деталей и TMDB mapping cache.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `release_summaries` ADD COLUMN `tmdbRating` TEXT")
        db.execSQL("ALTER TABLE `release_details` ADD COLUMN `tmdbRating` TEXT")
        db.execSQL("ALTER TABLE `tmdb_poster_mappings` ADD COLUMN `rating` TEXT")
    }
}

/** Список всех миграций для передачи в Room.databaseBuilder. */
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
)

private fun SupportSQLiteDatabase.hasColumn(tableName: String, columnName: String): Boolean {
    query("PRAGMA table_info(`$tableName`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == columnName) {
                return true
            }
        }
    }
    return false
}
