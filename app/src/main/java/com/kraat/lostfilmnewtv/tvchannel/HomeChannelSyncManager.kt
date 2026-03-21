package com.kraat.lostfilmnewtv.tvchannel

class HomeChannelSyncManager(
    private val programSource: HomeChannelProgramSource,
    private val preferences: HomeChannelPreferences,
    private val publisher: HomeChannelPublisher,
    private val programLimit: Int = DEFAULT_PROGRAM_LIMIT,
) {
    suspend fun syncNow() {
        try {
            when (val mode = preferences.readMode()) {
                AndroidTvChannelMode.DISABLED -> {
                    preferences.readChannelId()?.let { publisher.deleteChannel(it) }
                    preferences.clearChannelId()
                }

                AndroidTvChannelMode.ALL_NEW,
                AndroidTvChannelMode.UNWATCHED,
                -> {
                    val result = publisher.reconcile(
                        mode = mode,
                        existingChannelId = preferences.readChannelId(),
                        programs = programSource.loadPrograms(mode, programLimit),
                    )
                    preferences.writeChannelId(result.channelId)
                }
            }
        } catch (_: Throwable) {
            // Launcher integration must not crash the app.
        }
    }

    private companion object {
        const val DEFAULT_PROGRAM_LIMIT = 30
    }
}

interface HomeChannelPreferences {
    fun readMode(): AndroidTvChannelMode

    fun readChannelId(): Long?

    fun writeChannelId(channelId: Long)

    fun clearChannelId()
}
