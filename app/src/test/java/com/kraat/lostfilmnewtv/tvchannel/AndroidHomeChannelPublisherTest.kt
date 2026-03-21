package com.kraat.lostfilmnewtv.tvchannel

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.tvprovider.media.tv.TvContractCompat
import com.kraat.lostfilmnewtv.navigation.AppLaunchTarget
import org.junit.Assert.assertEquals
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
        assertEquals(testLogo, facade.publishedChannels.single().logoBitmap)
        assertEquals(listOf("https://example.com/1"), facade.upsertedPrograms.map { it.internalProviderId })
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
    fun reconcile_existingChannel_updatesProgramsAndDeletesMissingOnes() = runTestPublisher {
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
        assertEquals(listOf(8L), facade.deletedProgramIds)
        assertEquals(
            listOf("https://example.com/keep", "https://example.com/add"),
            facade.upsertedPrograms.map { it.internalProviderId },
        )
    }
}

private fun runTestPublisher(block: suspend () -> Unit) {
    kotlinx.coroutines.test.runTest {
        block()
    }
}

private class RecordingPreviewFacade(
    existingPrograms: List<PreviewProgramRecord> = emptyList(),
) : PreviewChannelHelperFacade {
    private var storedPrograms = existingPrograms.associateBy { it.internalProviderId }.toMutableMap()

    var publishDefaultChannelCalls: Int = 0
        private set
    val publishedChannels = mutableListOf<PreviewChannelRecord>()
    val upsertedPrograms = mutableListOf<PreviewProgramRecord>()
    val deletedProgramIds = mutableListOf<Long>()

    override suspend fun channelExists(channelId: Long): Boolean = channelId == 42L

    override suspend fun publishDefaultChannel(channel: PreviewChannelRecord): Long {
        publishDefaultChannelCalls += 1
        publishedChannels += channel
        return 42L
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

    override suspend fun deleteChannel(channelId: Long) = Unit
}

private fun program(detailsUrl: String): HomeChannelProgram {
    return HomeChannelProgram(
        detailsUrl = detailsUrl,
        title = "Title $detailsUrl",
        description = "Description $detailsUrl",
        posterUrl = "https://example.com/poster.jpg",
        internalProviderId = detailsUrl,
    )
}
