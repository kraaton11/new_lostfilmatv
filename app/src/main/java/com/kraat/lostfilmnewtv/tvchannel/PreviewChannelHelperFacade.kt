package com.kraat.lostfilmnewtv.tvchannel

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.provider.BaseColumns
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.TvContractCompat
import com.kraat.lostfilmnewtv.MainActivity

interface PreviewChannelHelperFacade {
    suspend fun channelExists(channelId: Long): Boolean

    suspend fun findChannelIdsByInternalProviderId(internalProviderId: String): List<Long>

    suspend fun publishDefaultChannel(channel: PreviewChannelRecord): Long

    suspend fun updateChannel(channelId: Long, channel: PreviewChannelRecord)

    suspend fun requestChannelBrowsable(channelId: Long)

    suspend fun getPrograms(channelId: Long): List<PreviewProgramRecord>

    suspend fun upsertProgram(program: PreviewProgramRecord): Long

    suspend fun deleteProgram(programId: Long)

    suspend fun deleteChannel(channelId: Long)
}

data class PreviewChannelRecord(
    val displayName: String,
    val description: String,
    val appLinkIntent: Intent,
    val internalProviderId: String,
    val logoBitmap: Bitmap? = null,
)

data class PreviewProgramRecord(
    val programId: Long? = null,
    val internalProviderId: String,
    val channelId: Long,
    val title: String,
    val description: String,
    val posterUrl: String,
    val thumbnailUrl: String = "",
    val launchIntent: Intent,
    val type: Int = TvContractCompat.PreviewProgramColumns.TYPE_CLIP,
    val weight: Int = 0,
)

class AndroidXPreviewChannelHelperFacade(
    private val appContext: Context,
    private val helper: PreviewChannelHelper = PreviewChannelHelper(appContext),
) : PreviewChannelHelperFacade {
    override suspend fun channelExists(channelId: Long): Boolean {
        return helper.getAllChannels().any { it.id == channelId }
    }

    override suspend fun findChannelIdsByInternalProviderId(internalProviderId: String): List<Long> {
        return helper.getAllChannels()
            .filter { it.internalProviderId == internalProviderId }
            .map { it.id }
    }

    override suspend fun publishDefaultChannel(channel: PreviewChannelRecord): Long {
        return helper.publishDefaultChannel(channel.toPreviewChannel())
    }

    override suspend fun updateChannel(channelId: Long, channel: PreviewChannelRecord) {
        helper.updatePreviewChannel(channelId, channel.toPreviewChannel())
    }

    override suspend fun requestChannelBrowsable(channelId: Long) {
        TvContractCompat.requestChannelBrowsable(appContext, channelId)
    }

    override suspend fun getPrograms(channelId: Long): List<PreviewProgramRecord> {
        val uri = TvContractCompat.buildPreviewProgramsUriForChannel(channelId)
        return appContext.contentResolver.query(
            uri,
            PREVIEW_PROGRAM_PROJECTION,
            null,
            null,
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toPreviewProgramRecord(appContext))
                }
            }
        } ?: emptyList()
    }

    override suspend fun upsertProgram(program: PreviewProgramRecord): Long {
        return if (program.programId == null) {
            val uri = appContext.contentResolver.insert(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                program.toContentValues(),
            ) ?: error("Preview program insertion failed")
            ContentUris.parseId(uri)
        } else {
            appContext.contentResolver.update(
                TvContractCompat.buildPreviewProgramUri(program.programId),
                program.toContentValues(),
                null,
                null,
            )
            program.programId
        }
    }

    override suspend fun deleteProgram(programId: Long) {
        appContext.contentResolver.delete(
            TvContractCompat.buildPreviewProgramUri(programId),
            null,
            null,
        )
    }

    override suspend fun deleteChannel(channelId: Long) {
        helper.deletePreviewChannel(channelId)
    }
}

private fun PreviewChannelRecord.toPreviewChannel(): PreviewChannel {
    val builder = PreviewChannel.Builder()
        .setDisplayName(displayName)
        .setDescription(description)
        .setAppLinkIntent(appLinkIntent)
        .setInternalProviderId(internalProviderId)
    logoBitmap?.let(builder::setLogo)
    return builder.build()
}

internal fun PreviewProgramRecord.toContentValues(): ContentValues {
    return ContentValues().apply {
        put(TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID, channelId)
        put(TvContractCompat.PreviewPrograms.COLUMN_WEIGHT, weight)
        put(TvContractCompat.PreviewProgramColumns.COLUMN_TYPE, type)
        put(COLUMN_TITLE, title)
        put(COLUMN_SHORT_DESCRIPTION, description)
        put(COLUMN_POSTER_ART_URI, posterUrl.asUriOrNull()?.toString())
        put(COLUMN_THUMBNAIL_URI, thumbnailUrl.asUriOrNull()?.toString())
        put(
            TvContractCompat.PreviewProgramColumns.COLUMN_POSTER_ART_ASPECT_RATIO,
            TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3,
        )
        put(
            TvContractCompat.PreviewProgramColumns.COLUMN_THUMBNAIL_ASPECT_RATIO,
            TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9,
        )
        put(TvContractCompat.PreviewProgramColumns.COLUMN_INTENT_URI, launchIntent.toUri(Intent.URI_INTENT_SCHEME))
        put(TvContractCompat.PreviewProgramColumns.COLUMN_INTERNAL_PROVIDER_ID, internalProviderId)
    }
}

internal fun Cursor.toPreviewProgramRecord(context: Context): PreviewProgramRecord {
    val fallbackIntent = Intent(context, MainActivity::class.java)
    val intentUri = getStringOrNull(TvContractCompat.PreviewProgramColumns.COLUMN_INTENT_URI)
    return PreviewProgramRecord(
        programId = getLong(BaseColumns._ID),
        internalProviderId = getStringOrNull(TvContractCompat.PreviewProgramColumns.COLUMN_INTERNAL_PROVIDER_ID).orEmpty(),
        channelId = getLong(TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID),
        title = getStringOrNull(COLUMN_TITLE).orEmpty(),
        description = getStringOrNull(COLUMN_SHORT_DESCRIPTION).orEmpty(),
        posterUrl = getStringOrNull(COLUMN_POSTER_ART_URI).orEmpty(),
        thumbnailUrl = getStringOrNull(COLUMN_THUMBNAIL_URI).orEmpty(),
        launchIntent = runCatching {
            Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
        }.getOrElse { fallbackIntent },
        type = getInt(TvContractCompat.PreviewProgramColumns.COLUMN_TYPE),
        weight = getInt(TvContractCompat.PreviewPrograms.COLUMN_WEIGHT),
    )
}

private fun Cursor.getStringOrNull(columnName: String): String? {
    return if (isNull(getColumnIndexOrThrow(columnName))) {
        null
    } else {
        getString(getColumnIndexOrThrow(columnName))
    }
}

private fun Cursor.getInt(columnName: String): Int {
    return getInt(getColumnIndexOrThrow(columnName))
}

private fun Cursor.getLong(columnName: String): Long {
    return getLong(getColumnIndexOrThrow(columnName))
}

private val PREVIEW_PROGRAM_PROJECTION = arrayOf(
    BaseColumns._ID,
    TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID,
    TvContractCompat.PreviewPrograms.COLUMN_WEIGHT,
    TvContractCompat.PreviewProgramColumns.COLUMN_TYPE,
    TvContractCompat.PreviewProgramColumns.COLUMN_INTERNAL_PROVIDER_ID,
    COLUMN_TITLE,
    COLUMN_SHORT_DESCRIPTION,
    COLUMN_POSTER_ART_URI,
    COLUMN_THUMBNAIL_URI,
    TvContractCompat.PreviewProgramColumns.COLUMN_INTENT_URI,
)

private const val COLUMN_TITLE = "title"
private const val COLUMN_SHORT_DESCRIPTION = "short_description"
private const val COLUMN_POSTER_ART_URI = "poster_art_uri"
private const val COLUMN_THUMBNAIL_URI = "thumbnail_uri"

private fun String.asUriOrNull(): Uri? = takeIf { it.isNotBlank() }?.let(Uri::parse)
