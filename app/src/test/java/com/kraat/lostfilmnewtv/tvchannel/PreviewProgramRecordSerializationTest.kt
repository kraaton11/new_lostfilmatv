package com.kraat.lostfilmnewtv.tvchannel

import android.content.Context
import android.content.Intent
import android.database.MatrixCursor
import android.provider.BaseColumns
import androidx.test.core.app.ApplicationProvider
import androidx.tvprovider.media.tv.TvContractCompat
import com.kraat.lostfilmnewtv.MainActivity
import com.kraat.lostfilmnewtv.navigation.AppLaunchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewProgramRecordSerializationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun toContentValues_writesPublicPreviewProgramColumns() {
        val launchIntent = AppLaunchTarget.createDetailsIntent(context, "https://example.com/show")
        val record = PreviewProgramRecord(
            programId = 7L,
            internalProviderId = "provider-id",
            channelId = 42L,
            title = "Title",
            description = "Description",
            posterUrl = "https://example.com/poster.jpg",
            launchIntent = launchIntent,
            type = TvContractCompat.PreviewProgramColumns.TYPE_CLIP,
            weight = 9,
        )

        val values = record.toContentValues()

        assertEquals(42L, values.getAsLong(TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID)?.toLong())
        assertEquals(9, values.getAsInteger(TvContractCompat.PreviewPrograms.COLUMN_WEIGHT)?.toInt())
        assertEquals(
            TvContractCompat.PreviewProgramColumns.TYPE_CLIP,
            values.getAsInteger(TvContractCompat.PreviewProgramColumns.COLUMN_TYPE)?.toInt(),
        )
        assertEquals("provider-id", values.getAsString(TvContractCompat.PreviewProgramColumns.COLUMN_INTERNAL_PROVIDER_ID))
        assertEquals("Title", values.getAsString("title"))
        assertEquals("Description", values.getAsString("short_description"))
        assertEquals("https://example.com/poster.jpg", values.getAsString("poster_art_uri"))
        assertEquals(launchIntent.toUri(Intent.URI_INTENT_SCHEME), values.getAsString(TvContractCompat.PreviewProgramColumns.COLUMN_INTENT_URI))
        assertEquals(1, values.getAsInteger(TvContractCompat.PreviewProgramColumns.COLUMN_BROWSABLE)?.toInt())
    }

    @Test
    fun toPreviewProgramRecord_readsStoredColumns_andFallsBackForBrokenIntentUri() {
        val cursor = MatrixCursor(
            arrayOf(
                BaseColumns._ID,
                TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID,
                TvContractCompat.PreviewPrograms.COLUMN_WEIGHT,
                TvContractCompat.PreviewProgramColumns.COLUMN_TYPE,
                TvContractCompat.PreviewProgramColumns.COLUMN_INTERNAL_PROVIDER_ID,
                "title",
                "short_description",
                "poster_art_uri",
                TvContractCompat.PreviewProgramColumns.COLUMN_INTENT_URI,
            ),
        )
        cursor.addRow(
            arrayOf<Any?>(
                7L,
                42L,
                9,
                TvContractCompat.PreviewProgramColumns.TYPE_CLIP,
                "provider-id",
                "Title",
                "Description",
                "https://example.com/poster.jpg",
                null,
            ),
        )
        cursor.use {
            it.moveToFirst()

            val record = it.toPreviewProgramRecord(context)

            assertEquals(7L, record.programId)
            assertEquals(42L, record.channelId)
            assertEquals(9, record.weight)
            assertEquals(TvContractCompat.PreviewProgramColumns.TYPE_CLIP, record.type)
            assertEquals("provider-id", record.internalProviderId)
            assertEquals("Title", record.title)
            assertEquals("Description", record.description)
            assertEquals("https://example.com/poster.jpg", record.posterUrl)
            assertNotNull(record.launchIntent.component)
            assertEquals(MainActivity::class.java.name, record.launchIntent.component?.className)
        }
    }
}
