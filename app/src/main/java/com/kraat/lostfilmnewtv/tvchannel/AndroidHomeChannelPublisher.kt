package com.kraat.lostfilmnewtv.tvchannel

import android.content.Context
import android.content.Intent
import com.kraat.lostfilmnewtv.MainActivity
import com.kraat.lostfilmnewtv.navigation.AppLaunchTarget

class AndroidHomeChannelPublisher(
    private val appContext: Context,
    private val helperFacade: PreviewChannelHelperFacade = AndroidXPreviewChannelHelperFacade(appContext),
) : HomeChannelPublisher {
    override suspend fun reconcile(
        mode: AndroidTvChannelMode,
        existingChannelId: Long?,
        programs: List<HomeChannelProgram>,
    ): HomeChannelPublisherResult {
        val channelId = resolveChannelId(existingChannelId)
        val existingPrograms = helperFacade.getPrograms(channelId).associateBy { it.internalProviderId }
        val desiredInternalProviderIds = linkedSetOf<String>()

        programs.forEachIndexed { index, program ->
            desiredInternalProviderIds += program.internalProviderId
            helperFacade.upsertProgram(
                PreviewProgramRecord(
                    programId = existingPrograms[program.internalProviderId]?.programId,
                    internalProviderId = program.internalProviderId,
                    channelId = channelId,
                    title = program.title,
                    description = program.description,
                    posterUrl = program.posterUrl,
                    launchIntent = AppLaunchTarget.createDetailsIntent(appContext, program.detailsUrl),
                    weight = index,
                ),
            )
        }

        existingPrograms.values
            .filterNot { it.internalProviderId in desiredInternalProviderIds }
            .mapNotNull { it.programId }
            .forEach { helperFacade.deleteProgram(it) }

        return HomeChannelPublisherResult(channelId)
    }

    override suspend fun deleteChannel(channelId: Long) {
        helperFacade.deleteChannel(channelId)
    }

    private suspend fun resolveChannelId(existingChannelId: Long?): Long {
        if (existingChannelId != null && helperFacade.channelExists(existingChannelId)) {
            return existingChannelId
        }

        return helperFacade.publishDefaultChannel(
            PreviewChannelRecord(
                displayName = appContext.applicationInfo.loadLabel(appContext.packageManager).toString(),
                description = CHANNEL_DESCRIPTION,
                appLinkIntent = Intent(appContext, MainActivity::class.java),
                internalProviderId = CHANNEL_INTERNAL_PROVIDER_ID,
            ),
        )
    }

    private companion object {
        const val CHANNEL_INTERNAL_PROVIDER_ID = "lostfilm-home-channel"
        const val CHANNEL_DESCRIPTION = "LostFilm releases"
    }
}
