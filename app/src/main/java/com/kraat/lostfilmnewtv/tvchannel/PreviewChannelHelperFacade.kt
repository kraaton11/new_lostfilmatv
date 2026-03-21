package com.kraat.lostfilmnewtv.tvchannel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.kraat.lostfilmnewtv.MainActivity

interface PreviewChannelHelperFacade {
    suspend fun channelExists(channelId: Long): Boolean

    suspend fun publishDefaultChannel(channel: PreviewChannelRecord): Long

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

    override suspend fun publishDefaultChannel(channel: PreviewChannelRecord): Long {
        return helper.publishDefaultChannel(channel.toPreviewChannel())
    }

    override suspend fun getPrograms(channelId: Long): List<PreviewProgramRecord> {
        val uri = TvContractCompat.buildPreviewProgramsUriForChannel(channelId)
        return appContext.contentResolver.query(
            uri,
            PreviewProgram.PROJECTION,
            null,
            null,
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(PreviewProgram.fromCursor(cursor).toRecord(appContext))
                }
            }
        } ?: emptyList()
    }

    override suspend fun upsertProgram(program: PreviewProgramRecord): Long {
        val previewProgram = program.toPreviewProgram()
        return if (program.programId == null) {
            helper.publishPreviewProgram(previewProgram)
        } else {
            helper.updatePreviewProgram(program.programId, previewProgram)
            program.programId
        }
    }

    override suspend fun deleteProgram(programId: Long) {
        helper.deletePreviewProgram(programId)
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

private fun PreviewProgramRecord.toPreviewProgram(): PreviewProgram {
    return PreviewProgram.Builder()
        .setChannelId(channelId)
        .setWeight(weight)
        .setType(type)
        .setTitle(title)
        .setDescription(description)
        .setPosterArtUri(Uri.parse(posterUrl))
        .setIntent(launchIntent)
        .setInternalProviderId(internalProviderId)
        .setBrowsable(true)
        .build()
}

private fun PreviewProgram.toRecord(context: Context): PreviewProgramRecord {
    val fallbackIntent = Intent(context, MainActivity::class.java)
    return PreviewProgramRecord(
        programId = id,
        internalProviderId = internalProviderId.orEmpty(),
        channelId = channelId,
        title = title.orEmpty(),
        description = description.orEmpty(),
        posterUrl = posterArtUri?.toString().orEmpty(),
        launchIntent = runCatching { intent }.getOrElse { fallbackIntent },
        type = type,
        weight = weight,
    )
}
