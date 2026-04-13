package com.kraat.lostfilmnewtv.tvchannel

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeChannelSyncManagerTest {
    @Test
    fun syncNow_activeMode_createsPrimaryChannelWhenIdMissing_andPersistsReturnedId() = runTest {
        val preferences = FakeChannelPreferences(mode = AndroidTvChannelMode.ALL_NEW, channelId = null)
        val publisher = RecordingHomeChannelPublisher(primaryChannelId = 42L)
        val manager = HomeChannelSyncManager(
            programSource = FakeProgramSource(
                programs = listOf(program(detailsUrl = "https://example.com/1")),
            ),
            preferences = preferences,
            publisher = publisher,
            logger = NoOpChannelLogger(),
        )

        manager.syncNow()

        assertEquals(listOf(AndroidTvChannelMode.ALL_NEW), publisher.reconciledModes)
        assertEquals(listOf(listOf("https://example.com/1")), publisher.publishedDetailsUrls)
        assertEquals(listOf(42L), preferences.writtenChannelIds)
    }

    @Test
    fun syncNow_activeMode_syncsFavoritesChannelSeparately_whenFavoriteProgramsExist() = runTest {
        val preferences = FakeChannelPreferences(mode = AndroidTvChannelMode.ALL_NEW, channelId = 42L)
        val publisher = RecordingHomeChannelPublisher(primaryChannelId = 42L, favoritesChannelId = 99L)
        val manager = HomeChannelSyncManager(
            programSource = FakeProgramSource(
                programs = listOf(program(detailsUrl = "https://example.com/1")),
                favoritePrograms = listOf(program(detailsUrl = "https://example.com/favorite")),
            ),
            preferences = preferences,
            publisher = publisher,
            logger = NoOpChannelLogger(),
        )

        manager.syncNow()

        assertEquals(listOf(AndroidTvChannelMode.ALL_NEW), publisher.reconciledModes)
        assertEquals(listOf(listOf("https://example.com/favorite")), publisher.publishedFavoriteDetailsUrls)
        assertEquals(listOf(99L), preferences.writtenFavoriteChannelIds)
    }

    @Test
    fun syncNow_activeMode_deletesFavoritesChannel_whenFavoriteProgramsAreEmpty() = runTest {
        val preferences = FakeChannelPreferences(
            mode = AndroidTvChannelMode.ALL_NEW,
            channelId = 42L,
            favoriteChannelId = 77L,
        )
        val publisher = RecordingHomeChannelPublisher(primaryChannelId = 42L, favoritesChannelId = 99L)
        val manager = HomeChannelSyncManager(
            programSource = FakeProgramSource(
                programs = listOf(program(detailsUrl = "https://example.com/1")),
                favoritePrograms = emptyList(),
            ),
            preferences = preferences,
            publisher = publisher,
            logger = NoOpChannelLogger(),
        )

        manager.syncNow()

        assertEquals(listOf(77L), publisher.deletedChannelIds)
        assertTrue(preferences.clearFavoriteChannelIdCalled)
    }

    @Test
    fun syncNow_disabled_deletesBothChannelsAndClearsStoredIds() = runTest {
        val preferences = FakeChannelPreferences(
            mode = AndroidTvChannelMode.DISABLED,
            channelId = 42L,
            favoriteChannelId = 77L,
        )
        val publisher = RecordingHomeChannelPublisher(primaryChannelId = 99L, favoritesChannelId = 199L)
        val manager = HomeChannelSyncManager(
            programSource = FakeProgramSource(emptyList()),
            preferences = preferences,
            publisher = publisher,
            logger = NoOpChannelLogger(),
        )

        manager.syncNow()

        assertEquals(listOf(42L, 77L), publisher.deletedChannelIds)
        assertTrue(preferences.clearChannelIdCalled)
        assertTrue(preferences.clearFavoriteChannelIdCalled)
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
                primaryChannelId = 7L,
                favoritesChannelId = 17L,
                failure = IllegalStateException("launcher unavailable"),
            ),
            logger = NoOpChannelLogger(),
            onSyncFailure = {},
        )

        manager.syncNow()

        assertTrue(true)
    }
}

private class FakeProgramSource(
    private val programs: List<HomeChannelProgram>,
    private val favoritePrograms: List<HomeChannelProgram> = emptyList(),
) : HomeChannelProgramSource {
    override suspend fun loadPrograms(
        mode: AndroidTvChannelMode,
        limit: Int,
    ): List<HomeChannelProgram> = programs.take(limit)

    override suspend fun loadFavoritePrograms(limit: Int): List<HomeChannelProgram> = favoritePrograms.take(limit)
}

private class FakeChannelPreferences(
    private val mode: AndroidTvChannelMode,
    private val channelId: Long?,
    private val favoriteChannelId: Long? = null,
) : HomeChannelPreferences {
    val writtenChannelIds = mutableListOf<Long>()
    val writtenFavoriteChannelIds = mutableListOf<Long>()
    var clearChannelIdCalled: Boolean = false
        private set
    var clearFavoriteChannelIdCalled: Boolean = false
        private set

    override fun readMode(): AndroidTvChannelMode = mode

    override fun readChannelId(): Long? = channelId

    override fun writeChannelId(channelId: Long) {
        writtenChannelIds += channelId
    }

    override fun clearChannelId() {
        clearChannelIdCalled = true
    }

    override fun readFavoriteChannelId(): Long? = favoriteChannelId

    override fun writeFavoriteChannelId(channelId: Long) {
        writtenFavoriteChannelIds += channelId
    }

    override fun clearFavoriteChannelId() {
        clearFavoriteChannelIdCalled = true
    }
}

private class RecordingHomeChannelPublisher(
    private val primaryChannelId: Long,
    private val favoritesChannelId: Long = 100L,
    private val failure: Throwable? = null,
) : HomeChannelPublisher {
    val reconciledModes = mutableListOf<AndroidTvChannelMode>()
    val publishedDetailsUrls = mutableListOf<List<String>>()
    val publishedFavoriteDetailsUrls = mutableListOf<List<String>>()
    val deletedChannelIds = mutableListOf<Long>()

    override suspend fun reconcile(
        mode: AndroidTvChannelMode,
        existingChannelId: Long?,
        programs: List<HomeChannelProgram>,
    ): HomeChannelPublisherResult {
        failure?.let { throw it }
        reconciledModes += mode
        publishedDetailsUrls += programs.map { it.detailsUrl }
        return HomeChannelPublisherResult(channelId = existingChannelId ?: primaryChannelId)
    }

    override suspend fun reconcileFavorites(
        existingChannelId: Long?,
        programs: List<HomeChannelProgram>,
    ): HomeChannelPublisherResult {
        failure?.let { throw it }
        publishedFavoriteDetailsUrls += programs.map { it.detailsUrl }
        return HomeChannelPublisherResult(channelId = existingChannelId ?: favoritesChannelId)
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
