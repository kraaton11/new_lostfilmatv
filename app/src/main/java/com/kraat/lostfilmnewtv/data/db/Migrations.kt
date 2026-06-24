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
        `episodeOverviewSource` TEXT,
        `movieOverviewRu` TEXT,
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

/**
 * Миграция 14→15: индекс для Android TV channel query по непросмотренным релизам.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_release_summaries_isWatched_pageNumber_positionInPage`
            ON `release_summaries` (`isWatched`, `pageNumber`, `positionInPage`)
            """.trimIndent(),
        )
    }
}

/**
 * Миграция 15→16: русское описание фильма из TMDB в кэше деталей.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `release_details` ADD COLUMN `movieOverviewRu` TEXT")
    }
}

/**
 * Миграция 16→17: составной индекс для выборки ленты с сортировкой по странице и позиции.
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_release_summaries_pageNumber`")
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_release_summaries_pageNumber_positionInPage`
            ON `release_summaries` (`pageNumber`, `positionInPage`)
            """.trimIndent(),
        )
    }
}

/**
 * Миграция 17→18: источник описания эпизода для пометок TMDB/автоперевода.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `release_summaries` ADD COLUMN `episodeOverviewSource` TEXT")
        db.execSQL("ALTER TABLE `release_details` ADD COLUMN `episodeOverviewSource` TEXT")
    }
}

/**
 * Миграция 18→19: кэш `hasNextPage` для stale-while-revalidate на главном экране.
 * Колонка нужна, чтобы при показе кэша Room без сетевого запроса сохранить информацию
 * о наличии следующей страницы. Значение по умолчанию 1 (true) — оптимистично,
 * не ломаем существующий кэш, для которого ранее `hasNextPage` не сохранялся.
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `page_cache_metadata` ADD COLUMN `hasNextPage` INTEGER NOT NULL DEFAULT 1",
        )
    }
}

/**
 * Миграция 19→20: добавление originalReleaseYear + Room-кеш для избранных релизов.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `release_summaries` ADD COLUMN `originalReleaseYear` INTEGER")
        db.execSQL("ALTER TABLE `release_details` ADD COLUMN `originalReleaseYear` INTEGER")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `favorite_release_cache` (
                `detailsUrl` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `titleRu` TEXT NOT NULL,
                `episodeTitleRu` TEXT,
                `seasonNumber` INTEGER,
                `episodeNumber` INTEGER,
                `releaseDateRu` TEXT NOT NULL,
                `posterUrl` TEXT NOT NULL,
                `backdropUrl` TEXT,
                `positionInList` INTEGER NOT NULL,
                `fetchedAt` INTEGER NOT NULL,
                `isWatched` INTEGER NOT NULL,
                `episodeOverviewRu` TEXT,
                `episodeOverviewSource` TEXT,
                `seriesOverviewRu` TEXT,
                `movieOverviewRu` TEXT,
                `tmdbRating` TEXT,
                PRIMARY KEY(`detailsUrl`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_favorite_release_cache_fetchedAt`
            ON `favorite_release_cache` (`fetchedAt`)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `favorite_release_cache_metadata` (
                `id` INTEGER NOT NULL,
                `fetchedAt` INTEGER NOT NULL,
                `favoriteSeriesCount` INTEGER NOT NULL,
                `itemCount` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
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
    MIGRATION_14_15,
    MIGRATION_15_16,
    MIGRATION_16_17,
    MIGRATION_17_18,
    MIGRATION_18_19,
    MIGRATION_19_20,
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
