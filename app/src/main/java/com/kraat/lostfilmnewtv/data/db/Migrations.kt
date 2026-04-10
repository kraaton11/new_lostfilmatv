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

/**
 * Миграция 5→6: схема хранения torrent-ссылок изменилась (добавлена колонка).
 * Кэш деталей несущественен — пересоздаём таблицу.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `release_details`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `release_details` (
                `details_url` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `title_ru` TEXT NOT NULL,
                `title_en` TEXT,
                `episode_title_ru` TEXT,
                `release_date_ru` TEXT NOT NULL,
                `lostfilm_poster_url` TEXT,
                `tmdb_poster_url` TEXT,
                `tmdb_backdrop_url` TEXT,
                `play_episode_id` TEXT,
                `torrent_links` TEXT NOT NULL,
                `fetched_at` INTEGER NOT NULL,
                `favorite_target_id` INTEGER,
                `favorite_target_kind` TEXT,
                `is_favorite` INTEGER,
                PRIMARY KEY(`details_url`)
            )
            """.trimIndent()
        )
    }
}

/** Список всех миграций для передачи в Room.databaseBuilder. */
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_5_6,
)
