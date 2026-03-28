package com.kraat.lostfilmnewtv.platform.torrserve

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class AlwaysNullBuilder : TorrServeSourceBuilder {
    override fun build(rawUrl: String): String? = null
}

private class FakeBuilder(val result: String?) : TorrServeSourceBuilder {
    override fun build(rawUrl: String): String? = result
}

private class FakeAvailabilityChecker(private val available: Boolean) : TorrServeAvailabilityChecker {
    override suspend fun isAvailable(): Boolean = available
}

private class FakeLauncher(private val success: Boolean) : TorrServeUrlLauncher {
    override suspend fun launch(context: Context, torrServeUrl: String): Boolean = success
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TorrServeActionHandlerTest {

    @Test
    fun returnsLaunchError_whenBuilderReturnsNull() = runBlocking {
        val builder = AlwaysNullBuilder()
        val probe = FakeAvailabilityChecker(true)
        val launcher = FakeLauncher(true)
        val testDispatcher = Dispatchers.Unconfined

        val handler = TorrServeActionHandler(builder, probe, launcher, testDispatcher, testDispatcher)
        val context = Robolectric.buildActivity(android.app.Activity::class.java).get()

        val result = handler.open(context, "http://test.com")

        assertEquals(TorrServeOpenResult.LaunchError, result)
    }

    @Test
    fun returnsUnavailable_whenProbeFails() = runBlocking {
        val builder = FakeBuilder("http://torrserve.com/stream")
        val probe = FakeAvailabilityChecker(false)
        val launcher = FakeLauncher(true)
        val testDispatcher = Dispatchers.Unconfined

        val handler = TorrServeActionHandler(builder, probe, launcher, testDispatcher, testDispatcher)
        val context = Robolectric.buildActivity(android.app.Activity::class.java).get()

        val result = handler.open(context, "http://test.com")

        assertEquals(TorrServeOpenResult.Unavailable, result)
    }

    @Test
    fun returnsSuccess_whenAllStagesPass() = runBlocking {
        val builder = FakeBuilder("http://torrserve.com/stream")
        val probe = FakeAvailabilityChecker(true)
        val launcher = FakeLauncher(true)
        val testDispatcher = Dispatchers.Unconfined

        val handler = TorrServeActionHandler(builder, probe, launcher, testDispatcher, testDispatcher)
        val context = Robolectric.buildActivity(android.app.Activity::class.java).get()

        val result = handler.open(context, "http://test.com")

        assertEquals(TorrServeOpenResult.Success, result)
    }
}
