package com.kraat.lostfilmnewtv.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Migration22To23Test {
    @Test
    fun migration_removesPosterCacheAndPreservesPageCache() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-22-to-23.db"
        fun withDatabase(block: (SupportSQLiteDatabase) -> Unit) {
            val database = Room.databaseBuilder(context, LostFilmDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
            try {
                block(database.openHelper.writableDatabase)
            } finally {
                database.close()
            }
        }

        try {
            withDatabase { db ->
                // Only the legacy poster table differs from the current schema.
                db.execSQL("CREATE TABLE IF NOT EXISTS tmdb_poster_mappings (detailsUrl TEXT PRIMARY KEY)")
                db.execSQL(
                    "INSERT INTO page_cache_metadata (pageNumber, fetchedAt, itemCount, hasNextPage) VALUES (1, 123, 5, 1)",
                )
                db.version = 22
            }

            withDatabase { db ->
                assertEquals(23, db.version)
                db.query("SELECT name FROM sqlite_master WHERE name = 'tmdb_poster_mappings'").use {
                    assertFalse(it.moveToFirst())
                }
                db.query("SELECT itemCount FROM page_cache_metadata WHERE pageNumber = 1").use {
                    assertEquals(1, it.count)
                    it.moveToFirst()
                    assertEquals(5, it.getInt(0))
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
