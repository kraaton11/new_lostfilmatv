package com.kraat.lostfilmnewtv.tvchannel

data class HomeChannelPublisherResult(
    val channelId: Long,
)

interface HomeChannelPublisher {
    suspend fun reconcile(
        mode: AndroidTvChannelMode,
        existingChannelId: Long?,
        programs: List<HomeChannelProgram>,
    ): HomeChannelPublisherResult

    suspend fun deleteChannel(channelId: Long)
}
