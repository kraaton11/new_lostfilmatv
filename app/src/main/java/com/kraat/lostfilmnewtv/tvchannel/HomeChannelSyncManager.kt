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
                        logger.d(TAG, "Channels disabled, deleting stored channels")
                        deleteChannelIfPresent(preferences.readChannelId())
                        deleteChannelIfPresent(preferences.readFavoriteChannelId())
                        preferences.clearChannelId()
                        preferences.clearFavoriteChannelId()
                        logger.d(TAG, "Stored channels deleted successfully")
                    }

                    AndroidTvChannelMode.ALL_NEW,
                    AndroidTvChannelMode.UNWATCHED,
                    -> {
                        syncPrimaryChannel(mode)
                        syncFavoriteChannel()
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

    private suspend fun syncPrimaryChannel(mode: AndroidTvChannelMode) {
        val existingChannelId = preferences.readChannelId()
        logger.d(TAG, "Syncing primary channel in mode: $mode, existingChannelId: $existingChannelId")

        val programs = programSource.loadPrograms(mode, programLimit)
        logger.d(TAG, "Loaded ${programs.size} programs for primary channel")

        if (programs.isEmpty()) {
            logger.w(TAG, "No programs loaded for primary channel, channel will be empty!")
        }

        val result = publisher.reconcile(
            mode = mode,
            existingChannelId = existingChannelId,
            programs = programs,
        )
        preferences.writeChannelId(result.channelId)
        logger.d(TAG, "Primary channel synced successfully, channelId: ${result.channelId}")
    }

    private suspend fun syncFavoriteChannel() {
        val existingChannelId = preferences.readFavoriteChannelId()
        logger.d(TAG, "Syncing favorites channel, existingChannelId: $existingChannelId")

        val programs = programSource.loadFavoritePrograms(programLimit)
        logger.d(TAG, "Loaded ${programs.size} programs for favorites channel")

        if (programs.isEmpty()) {
            logger.d(TAG, "Favorites channel has no content, deleting stale channel if needed")
            deleteChannelIfPresent(existingChannelId)
            preferences.clearFavoriteChannelId()
            return
        }

        val result = publisher.reconcileFavorites(
            existingChannelId = existingChannelId,
            programs = programs,
        )
        preferences.writeFavoriteChannelId(result.channelId)
        logger.d(TAG, "Favorites channel synced successfully, channelId: ${result.channelId}")
    }

    private suspend fun deleteChannelIfPresent(channelId: Long?) {
        channelId?.let { publisher.deleteChannel(it) }
    }
}

interface HomeChannelPreferences {
    fun readMode(): AndroidTvChannelMode

    fun readChannelId(): Long?

    fun writeChannelId(channelId: Long)

    fun clearChannelId()

    fun readFavoriteChannelId(): Long? = null

    fun writeFavoriteChannelId(channelId: Long) = Unit

    fun clearFavoriteChannelId() = Unit
}
