package com.kraat.lostfilmnewtv.tvchannel

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeChannelSyncManagerTest {
    @Test
    fun syncNow_activeMode_createsChannelWhenIdMissing_andPersistsReturnedId() = runTest {
        val preferences = FakeChannelPreferences(mode = AndroidTvChannelMode.ALL_NEW, channelId = null)
        val publisher = RecordingHomeChannelPublisher(createdChannelId = 42L)
        val manager = HomeChannelSyncManager(
            programSource = FakeProgramSource(
                programs = listOf(program(detailsUrl = "https://example.com/1")),
            ),
            preferences = preferences,
            publisher = publisher,
        )

        manager.syncNow()

        assertEquals(listOf(AndroidTvChannelMode.ALL_NEW), publisher.reconciledModes)
        assertEquals(listOf(listOf("https://example.com/1")), publisher.publishedDetailsUrls)
        assertEquals(listOf(42L), preferences.writtenChannelIds)
    }

    @Test
    fun syncNow_disabled_deletesChannelAndClearsStoredId() = runTest {
        val preferences = FakeChannelPreferences(mode = AndroidTvChannelMode.DISABLED, channelId = 42L)
        val publisher = RecordingHomeChannelPublisher(createdChannelId = 99L)
        val manager = HomeChannelSyncManager(
            programSource = FakeProgramSource(emptyList()),
            preferences = preferences,
            publisher = publisher,
        )

        manager.syncNow()

        assertEquals(listOf(42L), publisher.deletedChannelIds)
        assertTrue(preferences.clearChannelIdCalled)
    }

    @Test
    fun syncNow_publisherFailure_isSwallowed() = runTest {
        val preferences = FakeChannelPreferences(mode = AndroidTvChannelMode.UNWATCHED, channelId = 7L)
        val manager = HomeChannelSyncManager(
            programSource = FakeProgramSource(
                programs = listOf(program(detailsUrl = "https://example.com/2")),
            ),
            preferences = preferences,
            publisher = RecordingHomeChannelPublisher(
                createdChannelId = 7L,
                failure = IllegalStateException("launcher unavailable"),
            ),
        )

        manager.syncNow()

        assertTrue(true)
    }
}

private class FakeProgramSource(
    private val programs: List<HomeChannelProgram>,
) : HomeChannelProgramSource {
    override suspend fun loadPrograms(
        mode: AndroidTvChannelMode,
        limit: Int,
    ): List<HomeChannelProgram> = programs.take(limit)
}

private class FakeChannelPreferences(
    private val mode: AndroidTvChannelMode,
    private val channelId: Long?,
) : HomeChannelPreferences {
    val writtenChannelIds = mutableListOf<Long>()
    var clearChannelIdCalled: Boolean = false
        private set

    override fun readMode(): AndroidTvChannelMode = mode

    override fun readChannelId(): Long? = channelId

    override fun writeChannelId(channelId: Long) {
        writtenChannelIds += channelId
    }

    override fun clearChannelId() {
        clearChannelIdCalled = true
    }
}

private class RecordingHomeChannelPublisher(
    private val createdChannelId: Long,
    private val failure: Throwable? = null,
) : HomeChannelPublisher {
    val reconciledModes = mutableListOf<AndroidTvChannelMode>()
    val publishedDetailsUrls = mutableListOf<List<String>>()
    val deletedChannelIds = mutableListOf<Long>()

    override suspend fun reconcile(
        mode: AndroidTvChannelMode,
        existingChannelId: Long?,
        programs: List<HomeChannelProgram>,
    ): HomeChannelPublisherResult {
        failure?.let { throw it }
        reconciledModes += mode
        publishedDetailsUrls += programs.map { it.detailsUrl }
        return HomeChannelPublisherResult(channelId = existingChannelId ?: createdChannelId)
    }

    override suspend fun deleteChannel(channelId: Long) {
        deletedChannelIds += channelId
    }
}

private fun program(detailsUrl: String): HomeChannelProgram {
    return HomeChannelProgram(
        detailsUrl = detailsUrl,
        title = "Title",
        description = "Description",
        posterUrl = "https://example.com/poster.jpg",
        internalProviderId = detailsUrl,
    )
}
