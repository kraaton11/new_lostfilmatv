package com.kraat.lostfilmnewtv.tvchannel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import com.kraat.lostfilmnewtv.MainActivity
import com.kraat.lostfilmnewtv.navigation.AppLaunchTarget

class AndroidHomeChannelPublisher(
    private val appContext: Context,
    private val helperFacade: PreviewChannelHelperFacade = AndroidXPreviewChannelHelperFacade(appContext),
    private val channelLogoProvider: () -> Bitmap = {
        appContext.packageManager
            .getApplicationIcon(appContext.packageName)
            .toBitmap()
    },
) : HomeChannelPublisher {
    override suspend fun reconcile(
        mode: AndroidTvChannelMode,
        existingChannelId: Long?,
        programs: List<HomeChannelProgram>,
    ): HomeChannelPublisherResult {
        val channel = buildChannelRecord(mode)
        val channelId = resolveChannelId(existingChannelId, channel)
        helperFacade.requestChannelBrowsable(channelId)
        val existingProgramIds = helperFacade.getPrograms(channelId)
            .mapNotNull { it.programId }

        // Re-publish the whole row to force launcher refresh on devices that keep
        // stale card metadata or horizontal position across update-in-place calls.
        for (programId in existingProgramIds) {
            helperFacade.deleteProgram(programId)
        }

        programs.forEachIndexed { index, program ->
            helperFacade.upsertProgram(
                PreviewProgramRecord(
                    internalProviderId = program.internalProviderId,
                    channelId = channelId,
                    title = program.title,
                    description = program.description,
                    posterUrl = program.posterUrl,
                    launchIntent = AppLaunchTarget.createDetailsIntent(appContext, program.detailsUrl),
                    weight = programs.size - index,
                ),
            )
        }

        return HomeChannelPublisherResult(channelId)
    }

    override suspend fun deleteChannel(channelId: Long) {
        helperFacade.deleteChannel(channelId)
    }

    private suspend fun resolveChannelId(
        existingChannelId: Long?,
        channel: PreviewChannelRecord,
    ): Long {
        val validExistingChannelId = existingChannelId
            ?.takeIf { helperFacade.channelExists(it) }
        val matchingChannelIds = helperFacade.findChannelIdsByInternalProviderId(channel.internalProviderId)
        val channelIdToReuse = validExistingChannelId ?: matchingChannelIds.firstOrNull()

        matchingChannelIds
            .filterNot { it == channelIdToReuse }
            .forEach { duplicateChannelId ->
                helperFacade.deleteChannel(duplicateChannelId)
            }

        if (channelIdToReuse != null) {
            helperFacade.updateChannel(channelIdToReuse, channel)
            return channelIdToReuse
        }

        return helperFacade.publishDefaultChannel(channel)
    }

    private fun buildChannelRecord(mode: AndroidTvChannelMode): PreviewChannelRecord {
        return PreviewChannelRecord(
            displayName = mode.channelDisplayName(),
            description = mode.channelDescription(),
            appLinkIntent = Intent(appContext, MainActivity::class.java),
            internalProviderId = CHANNEL_INTERNAL_PROVIDER_ID,
            logoBitmap = channelLogoProvider(),
        )
    }

    private fun AndroidTvChannelMode.channelDisplayName(): String {
        return when (this) {
            AndroidTvChannelMode.ALL_NEW -> "LostFilm: Новые релизы"
            AndroidTvChannelMode.UNWATCHED -> "LostFilm: Непросмотренные"
            AndroidTvChannelMode.FAVORITES -> "LostFilm: Избранное"
            AndroidTvChannelMode.DISABLED -> appContext.applicationInfo.loadLabel(appContext.packageManager).toString()
        }
    }

    private fun AndroidTvChannelMode.channelDescription(): String {
        return when (this) {
            AndroidTvChannelMode.ALL_NEW -> "Все новые релизы LostFilm"
            AndroidTvChannelMode.UNWATCHED -> "Непросмотренные релизы LostFilm"
            AndroidTvChannelMode.FAVORITES -> "Избранные релизы LostFilm"
            AndroidTvChannelMode.DISABLED -> CHANNEL_DESCRIPTION
        }
    }

    private companion object {
        const val CHANNEL_INTERNAL_PROVIDER_ID = "lostfilm-home-channel"
        const val CHANNEL_DESCRIPTION = "LostFilm releases"
    }
}
