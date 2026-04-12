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

/** Список всех миграций для передачи в Room.databaseBuilder. */
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_5_6,
    MIGRATION_6_7,
)
