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

    suspend fun reconcileFavorites(
        existingChannelId: Long?,
        programs: List<HomeChannelProgram>,
    ): HomeChannelPublisherResult {
        throw UnsupportedOperationException("Favorites channel publishing is not implemented")
    }

    suspend fun deleteChannel(channelId: Long)
}
