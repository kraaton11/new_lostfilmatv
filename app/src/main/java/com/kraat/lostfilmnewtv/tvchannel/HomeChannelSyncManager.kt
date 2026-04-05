package com.kraat.lostfilmnewtv.tvchannel

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HomeChannelSyncManager(
    private val programSource: HomeChannelProgramSource,
    private val preferences: HomeChannelPreferences,
    private val publisher: HomeChannelPublisher,
    private val programLimit: Int = DEFAULT_PROGRAM_LIMIT,
    private val logger: ChannelLogger = AndroidChannelLogger(),
    private val onSyncFailure: (Throwable) -> Unit = {},
) {
    private val syncMutex = Mutex()

    suspend fun syncNow() {
        syncMutex.withLock {
            logger.d(TAG, "syncNow() started")
            try {
                when (val mode = preferences.readMode()) {
                    AndroidTvChannelMode.DISABLED -> {
                        logger.d(TAG, "Channel disabled, deleting channel")
                        preferences.readChannelId()?.let { publisher.deleteChannel(it) }
                        preferences.clearChannelId()
                        logger.d(TAG, "Channel deleted successfully")
                    }

                    AndroidTvChannelMode.ALL_NEW,
                    AndroidTvChannelMode.UNWATCHED,
                    AndroidTvChannelMode.FAVORITES,
                    -> {
                        val existingChannelId = preferences.readChannelId()
                        logger.d(TAG, "Syncing channel in mode: $mode, existingChannelId: $existingChannelId")

                        val programs = programSource.loadPrograms(mode, programLimit)
                        logger.d(TAG, "Loaded ${programs.size} programs from database")

                        if (programs.isEmpty()) {
                            logger.w(TAG, "No programs loaded, channel will be empty!")
                        }

                        val result = publisher.reconcile(
                            mode = mode,
                            existingChannelId = existingChannelId,
                            programs = programs,
                        )
                        preferences.writeChannelId(result.channelId)
                        logger.d(TAG, "Channel synced successfully, channelId: ${result.channelId}")
                    }
                }
                logger.d(TAG, "syncNow() completed successfully")
            } catch (error: Throwable) {
                logger.e(TAG, "Channel sync failed", error)
                onSyncFailure(error)
            }
        }
    }

    private companion object {
        const val DEFAULT_PROGRAM_LIMIT = 30
        const val TAG = "HomeChannelSyncManager"
    }
}

interface HomeChannelPreferences {
    fun readMode(): AndroidTvChannelMode

    fun readChannelId(): Long?

    fun writeChannelId(channelId: Long)

    fun clearChannelId()
}
