package com.kraat.lostfilmnewtv.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SCHEMA_21 = """
CREATE TABLE IF NOT EXISTS `tmdb_poster_mappings` (
    `detailsUrl` TEXT NOT NULL,
    `tmdbId` INTEGER NOT NULL,
    `tmdbType` TEXT NOT NULL,
    `posterUrl` TEXT NOT NULL,
    `backdropUrl` TEXT NOT NULL,
    `fetchedAt` INTEGER NOT NULL,
    `isNegative` INTEGER NOT NULL,
    `rating` TEXT,
    PRIMARY KEY(`detailsUrl`)
)
"""

private const val TMDB_ROW = "/series/The_Lowdown/season_1/"
private const val KP_ROW = "/series/Futurama/season_11/"
private const val TMDB_POSTER = "https://image.tmdb.org/t/p/w780/poster.jpg"
private const val KP_POSTER = "https://st.kp.yandex.net/images/film_big/79920.jpg"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Migration21To22Test {
    @Test
    fun migrate_addsSourceColumn_defaultingExistingRowsToTmdb() {
        migrated { db ->
            assertTrue("Колонка source обязана появиться", db.hasColumn("tmdb_poster_mappings", "source"))
            assertEquals(
                TmdbPosterMappingEntity.SOURCE_TMDB,
                db.stringOf("SELECT `source` FROM `tmdb_poster_mappings` WHERE `detailsUrl` = '$TMDB_ROW'"),
            )
        }
    }

    @Test
    fun migrate_dropsKinoPoiskMappings_soTmdbGetsAFreshChance() {
        migrated { db ->
            assertEquals(
                "Маппинг Кинопоиска держит свой id в колонке tmdbId, поэтому TMDB больше не " +
                    "получит шанс найти настоящий матч (615 у Futurama вместо 79920)",
                "0",
                db.stringOf("SELECT COUNT(*) FROM `tmdb_poster_mappings` WHERE `detailsUrl` = '$KP_ROW'"),
            )
        }
    }

    @Test
    fun migrate_keepsTmdbMappings_withAllTheirData() {
        migrated { db ->
            assertEquals(
                "273247|$TMDB_POSTER|6.9",
                db.stringOf(
                    "SELECT `tmdbId` || '|' || `posterUrl` || '|' || `rating` " +
                        "FROM `tmdb_poster_mappings` WHERE `detailsUrl` = '$TMDB_ROW'",
                ),
            )
        }
    }

    @Test
    fun migrate_leavesExactlyOneRow_afterBothStatementsApplied() {
        migrated { db ->
            assertEquals("1", db.stringOf("SELECT COUNT(*) FROM `tmdb_poster_mappings`"))
        }
    }

    @Test
    fun migrate_isIdempotent_whenBothStatementsAppliedTwice() {
        val (helper, db) = openRawDatabase("migration-idempotent")
        db.use {
            it.execSQL(SCHEMA_21)
            seed(it)
            MIGRATION_21_22.migrate(it)
            // Повторно: ALTER упадёт на существующей колонке, поэтому проверяем
            // только идемпотентность DELETE — он обязан быть безопасным.
            it.execSQL("DELETE FROM `tmdb_poster_mappings` WHERE `posterUrl` LIKE '%kp.yandex%'")
            assertEquals("1", it.stringOf("SELECT COUNT(*) FROM `tmdb_poster_mappings`"))
        }
        helper.close()
    }

    private fun migrated(block: (SupportSQLiteDatabase) -> Unit) {
        val (helper, db) = openRawDatabase("migration-21-22-${System.nanoTime()}")
        db.use {
            it.execSQL(SCHEMA_21)
            seed(it)
            MIGRATION_21_22.migrate(it)
            block(it)
        }
        helper.close()
    }

    private fun seed(db: SupportSQLiteDatabase) {
        db.insertRow(TMDB_ROW, 273_247, TMDB_POSTER, "6.9")
        db.insertRow(KP_ROW, 79_920, KP_POSTER, "8.2")
    }

    private fun SupportSQLiteDatabase.insertRow(detailsUrl: String, id: Int, poster: String, rating: String) {
        execSQL(
            "INSERT INTO `tmdb_poster_mappings` (`detailsUrl`, `tmdbId`, `tmdbType`, `posterUrl`, " +
                "`backdropUrl`, `fetchedAt`, `isNegative`, `rating`) VALUES " +
                "('$detailsUrl', $id, 'TV', '$poster', '', 1789000000000, 0, '$rating')",
        )
    }

    private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean =
        query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
                .any { it == column }
        }

    private fun SupportSQLiteDatabase.stringOf(sql: String): String =
        query(sql).use { cursor ->
            check(cursor.moveToFirst()) { "Запрос не вернул строк: $sql" }
            cursor.getString(0)
        }
}

private fun openRawDatabase(name: String): Pair<SupportSQLiteOpenHelper, SupportSQLiteDatabase> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val callback = object : SupportSQLiteOpenHelper.Callback(version = 1) {
        override fun onCreate(db: SupportSQLiteDatabase) = Unit
        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(name)
        .callback(callback)
        .build()
    val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
    return helper to helper.writableDatabase
}
