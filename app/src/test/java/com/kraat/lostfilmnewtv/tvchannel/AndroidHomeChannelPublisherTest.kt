package com.kraat.lostfilmnewtv.tvchannel

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.tvprovider.media.tv.TvContractCompat
import com.kraat.lostfilmnewtv.navigation.AppLaunchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidHomeChannelPublisherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val testLogo = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

    @Test
    fun reconcile_missingChannel_publishesDefaultChannel_andAddsPrograms() = runTestPublisher {
        val facade = RecordingPreviewFacade()
        val publisher = AndroidHomeChannelPublisher(
            appContext = context,
            helperFacade = facade,
            channelLogoProvider = { testLogo },
        )

        val result = publisher.reconcile(
            mode = AndroidTvChannelMode.ALL_NEW,
            existingChannelId = null,
            programs = listOf(program("https://example.com/1")),
        )

        assertEquals(42L, result.channelId)
        assertEquals(1, facade.publishDefaultChannelCalls)
        assertEquals(1, facade.publishedChannels.size)
        assertEquals(listOf(42L), facade.requestedBrowsableChannelIds)
        assertEquals(testLogo, facade.publishedChannels.single().logoBitmap)
        assertEquals("LostFilm: Новые релизы", facade.publishedChannels.single().displayName)
        assertEquals("Все новые релизы LostFilm", facade.publishedChannels.single().description)
        assertEquals(listOf("https://example.com/1"), facade.upsertedPrograms.map { it.internalProviderId })
        assertEquals(
            listOf("https://example.com/poster.jpg"),
            facade.upsertedPrograms.map { it.thumbnailUrl },
        )
        assertEquals(
            listOf(TvContractCompat.PreviewProgramColumns.TYPE_CLIP),
            facade.upsertedPrograms.map { it.type },
        )
        assertEquals(
            "https://example.com/1",
            AppLaunchTarget.parseDetailsUrl(facade.upsertedPrograms.single().launchIntent),
        )
    }

    @Test
    fun reconcile_existingChannel_republishesWholeRowFromScratch() = runTestPublisher {
        val facade = RecordingPreviewFacade(
            existingPrograms = listOf(
                PreviewProgramRecord(
                    programId = 7L,
                    internalProviderId = "https://example.com/keep",
                    channelId = 42L,
                    title = "Keep",
                    description = "Keep",
                    posterUrl = "https://example.com/keep.jpg",
                    launchIntent = AppLaunchTarget.createDetailsIntent(context, "https://example.com/keep"),
                ),
                PreviewProgramRecord(
                    programId = 8L,
                    internalProviderId = "https://example.com/remove",
                    channelId = 42L,
                    title = "Remove",
                    description = "Remove",
                    posterUrl = "https://example.com/remove.jpg",
                    launchIntent = AppLaunchTarget.createDetailsIntent(context, "https://example.com/remove"),
                ),
            ),
        )
        val publisher = AndroidHomeChannelPublisher(
            appContext = context,
            helperFacade = facade,
            channelLogoProvider = { testLogo },
        )

        publisher.reconcile(
            mode = AndroidTvChannelMode.UNWATCHED,
            existingChannelId = 42L,
            programs = listOf(
                program("https://example.com/keep"),
                program("https://example.com/add"),
            ),
        )

        assertEquals(0, facade.publishDefaultChannelCalls)
        assertEquals(listOf(42L), facade.updatedChannelIds)
        assertEquals("LostFilm: Непросмотренные", facade.updatedChannels.single().displayName)
        assertEquals("Непросмотренные релизы LostFilm", facade.updatedChannels.single().description)
        assertEquals(listOf(42L), facade.requestedBrowsableChannelIds)
        assertEquals(listOf(7L, 8L), facade.deletedProgramIds)
        assertEquals(
            listOf("https://example.com/keep", "https://example.com/add"),
            facade.upsertedPrograms.map { it.internalProviderId },
        )
        assertEquals(
            listOf(null, null),
            facade.upsertedPrograms.map { it.programId },
        )
    }

    @Test
    fun reconcile_missingStoredChannelId_reusesPublishedChannelWithSameProviderId() = runTestPublisher {
        val facade = RecordingPreviewFacade(
            knownChannelIdsByProviderId = mapOf("lostfilm-home-channel" to listOf(42L, 99L)),
        )
        val publisher = AndroidHomeChannelPublisher(
            appContext = context,
            helperFacade = facade,
            channelLogoProvider = { testLogo },
        )

        val result = publisher.reconcile(
            mode = AndroidTvChannelMode.ALL_NEW,
            existingChannelId = null,
            programs = listOf(program("https://example.com/1")),
        )

        assertEquals(42L, result.channelId)
        assertEquals(0, facade.publishDefaultChannelCalls)
        assertEquals(listOf(42L), facade.updatedChannelIds)
        assertEquals(listOf(99L), facade.deletedChannelIds)
    }

    @Test
    fun reconcile_assignsDescendingWeights_soNewestProgramStaysFirst() = runTestPublisher {
        val facade = RecordingPreviewFacade()
        val publisher = AndroidHomeChannelPublisher(
            appContext = context,
            helperFacade = facade,
            channelLogoProvider = { testLogo },
        )

        publisher.reconcile(
            mode = AndroidTvChannelMode.ALL_NEW,
            existingChannelId = 42L,
            programs = listOf(
                program("https://example.com/1"),
                program("https://example.com/2"),
                program("https://example.com/3"),
            ),
        )

        assertEquals(
            listOf(3, 2, 1),
            facade.upsertedPrograms.map { it.weight },
        )
    }

    @Test
    fun reconcile_skipsProgramsWithoutUsableImages() = runTestPublisher {
        val facade = RecordingPreviewFacade()
        val publisher = AndroidHomeChannelPublisher(
            appContext = context,
            helperFacade = facade,
            channelLogoProvider = { testLogo },
        )

        publisher.reconcile(
            mode = AndroidTvChannelMode.ALL_NEW,
            existingChannelId = 42L,
            programs = listOf(
                program("https://example.com/1", posterUrl = ""),
                program(
                    "https://example.com/2",
                    posterUrl = "",
                    backdropUrl = "https://example.com/backdrop.jpg",
                ),
            ),
        )

        assertEquals(listOf("https://example.com/2"), facade.upsertedPrograms.map { it.internalProviderId })
        assertEquals(listOf("https://example.com/backdrop.jpg"), facade.upsertedPrograms.map { it.thumbnailUrl })
        assertTrue(facade.upsertedPrograms.single().posterUrl.isBlank())
    }

    @Test
    fun reconcile_usesBackdropAsThumbnail_whenAvailable() = runTestPublisher {
        val facade = RecordingPreviewFacade()
        val publisher = AndroidHomeChannelPublisher(
            appContext = context,
            helperFacade = facade,
            channelLogoProvider = { testLogo },
        )

        publisher.reconcile(
            mode = AndroidTvChannelMode.ALL_NEW,
            existingChannelId = 42L,
            programs = listOf(
                program(
                    "https://example.com/1",
                    backdropUrl = "https://example.com/backdrop.jpg",
                ),
            ),
        )

        assertEquals("https://example.com/poster.jpg", facade.upsertedPrograms.single().posterUrl)
        assertEquals("https://example.com/backdrop.jpg", facade.upsertedPrograms.single().thumbnailUrl)
    }
}

private fun runTestPublisher(block: suspend () -> Unit) {
    kotlinx.coroutines.test.runTest {
        block()
    }
}

private class RecordingPreviewFacade(
    existingPrograms: List<PreviewProgramRecord> = emptyList(),
    private val knownChannelIdsByProviderId: Map<String, List<Long>> = emptyMap(),
) : PreviewChannelHelperFacade {
    private var storedPrograms = existingPrograms.associateBy { it.internalProviderId }.toMutableMap()

    var publishDefaultChannelCalls: Int = 0
        private set
    val publishedChannels = mutableListOf<PreviewChannelRecord>()
    val updatedChannels = mutableListOf<PreviewChannelRecord>()
    val upsertedPrograms = mutableListOf<PreviewProgramRecord>()
    val deletedProgramIds = mutableListOf<Long>()
    val deletedChannelIds = mutableListOf<Long>()
    val requestedBrowsableChannelIds = mutableListOf<Long>()
    val updatedChannelIds = mutableListOf<Long>()

    override suspend fun channelExists(channelId: Long): Boolean = channelId == 42L

    override suspend fun findChannelIdsByInternalProviderId(internalProviderId: String): List<Long> {
        return knownChannelIdsByProviderId[internalProviderId].orEmpty()
    }

    override suspend fun publishDefaultChannel(channel: PreviewChannelRecord): Long {
        publishDefaultChannelCalls += 1
        publishedChannels += channel
        return 42L
    }

    override suspend fun updateChannel(channelId: Long, channel: PreviewChannelRecord) {
        updatedChannelIds += channelId
        updatedChannels += channel
    }

    override suspend fun getPrograms(channelId: Long): List<PreviewProgramRecord> {
        return storedPrograms.values.filter { it.channelId == channelId }
    }

    override suspend fun upsertProgram(program: PreviewProgramRecord): Long {
        upsertedPrograms += program
        val assignedId = program.programId ?: (storedPrograms.size + 100L)
        storedPrograms[program.internalProviderId] = program.copy(programId = assignedId)
        return assignedId
    }

    override suspend fun deleteProgram(programId: Long) {
        deletedProgramIds += programId
        storedPrograms = storedPrograms.values
            .filterNot { it.programId == programId }
            .associateBy { it.internalProviderId }
            .toMutableMap()
    }

    override suspend fun requestChannelBrowsable(channelId: Long) {
        requestedBrowsableChannelIds += channelId
    }

    override suspend fun deleteChannel(channelId: Long) {
        deletedChannelIds += channelId
    }
}

private fun program(
    detailsUrl: String,
    posterUrl: String = "https://example.com/poster.jpg",
    backdropUrl: String = "",
): HomeChannelProgram {
    return HomeChannelProgram(
        detailsUrl = detailsUrl,
        title = "Title $detailsUrl",
        description = "Description $detailsUrl",
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        internalProviderId = detailsUrl,
    )
}
