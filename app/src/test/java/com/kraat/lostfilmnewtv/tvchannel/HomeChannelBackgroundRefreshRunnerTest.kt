package com.kraat.lostfilmnewtv.tvchannel

import com.kraat.lostfilmnewtv.data.model.LostFilmCookie
import com.kraat.lostfilmnewtv.data.model.LostFilmSession
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeChannelBackgroundRefreshRunnerTest {
    @Test
    fun recentlyRefreshedFirstPage_skipsWithoutRefreshingOrSyncing() = runTest {
        val calls = RefreshRunnerCalls()
        val runner = HomeChannelBackgroundRefreshRunner(
            readMode = { AndroidTvChannelMode.ALL_NEW },
            readSession = {
                calls.readSessionCalls += 1
                null
            },
            isSessionExpired = {
                calls.isSessionExpiredCalls += 1
                false
            },
            refreshFirstPage = {
                calls.refreshFirstPageCalls += 1
                contentPageState()
            },
            syncChannel = {
                calls.syncChannelCalls += 1
            },
            readFirstPageFetchedAt = { 10_000L },
            clock = { 10_000L + 29 * 60 * 1000L },
            minRefreshIntervalMs = 30 * 60 * 1000L,
        )

        val outcome = runner.run()

        assertEquals(HomeChannelBackgroundRefreshOutcome.SKIPPED_RECENTLY_REFRESHED, outcome)
        assertEquals(0, calls.readSessionCalls)
        assertEquals(0, calls.isSessionExpiredCalls)
        assertEquals(0, calls.refreshFirstPageCalls)
        assertEquals(0, calls.syncChannelCalls)
    }

    @Test
    fun disabledMode_skipsWithoutRefreshingOrSyncing() = runTest {
        val calls = RefreshRunnerCalls()
        val runner = HomeChannelBackgroundRefreshRunner(
            readMode = { AndroidTvChannelMode.DISABLED },
            readSession = {
                calls.readSessionCalls += 1
                testSession()
            },
            isSessionExpired = {
                calls.isSessionExpiredCalls += 1
                false
            },
            refreshFirstPage = {
                calls.refreshFirstPageCalls += 1
                contentPageState()
            },
            syncChannel = {
                calls.syncChannelCalls += 1
            },
        )

        val outcome = runner.run()

        assertEquals(HomeChannelBackgroundRefreshOutcome.SKIPPED_DISABLED, outcome)
        assertEquals(0, calls.readSessionCalls)
        assertEquals(0, calls.isSessionExpiredCalls)
        assertEquals(0, calls.refreshFirstPageCalls)
        assertEquals(0, calls.syncChannelCalls)
    }

    @Test
    fun allNewMode_missingSession_refreshesAndSyncs() = runTest {
        val calls = RefreshRunnerCalls()
        val runner = HomeChannelBackgroundRefreshRunner(
            readMode = { AndroidTvChannelMode.ALL_NEW },
            readSession = {
                calls.readSessionCalls += 1
                null
            },
            isSessionExpired = {
                calls.isSessionExpiredCalls += 1
                false
            },
            refreshFirstPage = {
                calls.refreshFirstPageCalls += 1
                contentPageState()
            },
            syncChannel = {
                calls.syncChannelCalls += 1
            },
        )

        val outcome = runner.run()

        assertEquals(HomeChannelBackgroundRefreshOutcome.REFRESHED, outcome)
        assertEquals(0, calls.readSessionCalls)
        assertEquals(0, calls.isSessionExpiredCalls)
        assertEquals(1, calls.refreshFirstPageCalls)
        assertEquals(1, calls.syncChannelCalls)
    }

    @Test
    fun unwatchedMode_missingSession_skipsWithoutRefreshingOrSyncing() = runTest {
        val calls = RefreshRunnerCalls()
        val runner = HomeChannelBackgroundRefreshRunner(
            readMode = { AndroidTvChannelMode.UNWATCHED },
            readSession = {
                calls.readSessionCalls += 1
                null
            },
            isSessionExpired = {
                calls.isSessionExpiredCalls += 1
                false
            },
            refreshFirstPage = {
                calls.refreshFirstPageCalls += 1
                contentPageState()
            },
            syncChannel = {
                calls.syncChannelCalls += 1
            },
        )

        val outcome = runner.run()

        assertEquals(HomeChannelBackgroundRefreshOutcome.SKIPPED_UNAUTHENTICATED, outcome)
        assertEquals(1, calls.readSessionCalls)
        assertEquals(0, calls.isSessionExpiredCalls)
        assertEquals(0, calls.refreshFirstPageCalls)
        assertEquals(0, calls.syncChannelCalls)
    }

    @Test
    fun allNewMode_expiredSession_refreshesAndSyncs() = runTest {
        val calls = RefreshRunnerCalls()
        val runner = HomeChannelBackgroundRefreshRunner(
            readMode = { AndroidTvChannelMode.ALL_NEW },
            readSession = {
                calls.readSessionCalls += 1
                testSession()
            },
            isSessionExpired = {
                calls.isSessionExpiredCalls += 1
                true
            },
            refreshFirstPage = {
                calls.refreshFirstPageCalls += 1
                contentPageState()
            },
            syncChannel = {
                calls.syncChannelCalls += 1
            },
        )

        val outcome = runner.run()

        assertEquals(HomeChannelBackgroundRefreshOutcome.REFRESHED, outcome)
        assertEquals(0, calls.readSessionCalls)
        assertEquals(0, calls.isSessionExpiredCalls)
        assertEquals(1, calls.refreshFirstPageCalls)
        assertEquals(1, calls.syncChannelCalls)
    }

    @Test
    fun unwatchedMode_expiredSession_skipsWithoutRefreshingOrSyncing() = runTest {
        val calls = RefreshRunnerCalls()
        val runner = HomeChannelBackgroundRefreshRunner(
            readMode = { AndroidTvChannelMode.UNWATCHED },
            readSession = {
                calls.readSessionCalls += 1
                testSession()
            },
            isSessionExpired = {
                calls.isSessionExpiredCalls += 1
                true
            },
            refreshFirstPage = {
                calls.refreshFirstPageCalls += 1
                contentPageState()
            },
            syncChannel = {
                calls.syncChannelCalls += 1
            },
        )

        val outcome = runner.run()

        assertEquals(HomeChannelBackgroundRefreshOutcome.SKIPPED_UNAUTHENTICATED, outcome)
        assertEquals(1, calls.readSessionCalls)
        assertEquals(1, calls.isSessionExpiredCalls)
        assertEquals(0, calls.refreshFirstPageCalls)
        assertEquals(0, calls.syncChannelCalls)
    }

    @Test
    fun contentResult_refreshesPageOneAndSyncsChannel() = runTest {
        val calls = RefreshRunnerCalls()
        val runner = HomeChannelBackgroundRefreshRunner(
            readMode = { AndroidTvChannelMode.UNWATCHED },
            readSession = {
                calls.readSessionCalls += 1
                testSession()
            },
            isSessionExpired = {
                calls.isSessionExpiredCalls += 1
                false
            },
            refreshFirstPage = {
                calls.refreshFirstPageCalls += 1
                contentPageState()
            },
            syncChannel = {
                calls.syncChannelCalls += 1
            },
        )

        val outcome = runner.run()

        assertEquals(HomeChannelBackgroundRefreshOutcome.REFRESHED, outcome)
        assertEquals(1, calls.readSessionCalls)
        assertEquals(1, calls.isSessionExpiredCalls)
        assertEquals(1, calls.refreshFirstPageCalls)
        assertEquals(1, calls.syncChannelCalls)
    }

    @Test
    fun errorResult_returnsRetryableFailureWithoutSyncing() = runTest {
        val calls = RefreshRunnerCalls()
        val runner = HomeChannelBackgroundRefreshRunner(
            readMode = { AndroidTvChannelMode.UNWATCHED },
            readSession = {
                calls.readSessionCalls += 1
                testSession()
            },
            isSessionExpired = {
                calls.isSessionExpiredCalls += 1
                false
            },
            refreshFirstPage = {
                calls.refreshFirstPageCalls += 1
                PageState.Error(pageNumber = 1, message = "network down")
            },
            syncChannel = {
                calls.syncChannelCalls += 1
            },
        )

        val outcome = runner.run()

        assertEquals(HomeChannelBackgroundRefreshOutcome.FAILED_RETRYABLE, outcome)
        assertEquals(1, calls.readSessionCalls)
        assertEquals(1, calls.isSessionExpiredCalls)
        assertEquals(1, calls.refreshFirstPageCalls)
        assertEquals(0, calls.syncChannelCalls)
    }
}

private data class RefreshRunnerCalls(
    var readSessionCalls: Int = 0,
    var isSessionExpiredCalls: Int = 0,
    var refreshFirstPageCalls: Int = 0,
    var syncChannelCalls: Int = 0,
)

private fun testSession(): LostFilmSession {
    return LostFilmSession(
        cookies = listOf(
            LostFilmCookie(
                name = "lf_session",
                value = "cookie",
                domain = "www.lostfilm.tv",
            ),
        ),
    )
}

private fun contentPageState(): PageState.Content {
    return PageState.Content(
        pageNumber = 1,
        items = listOf(
            ReleaseSummary(
                id = "summary-1",
                kind = ReleaseKind.SERIES,
                titleRu = "Захват",
                episodeTitleRu = "Пилот",
                seasonNumber = 1,
                episodeNumber = 1,
                releaseDateRu = "21.03.2026",
                posterUrl = "https://example.test/poster.jpg",
                detailsUrl = "https://example.test/details/1",
                pageNumber = 1,
                positionInPage = 0,
                fetchedAt = 0L,
            ),
        ),
        hasNextPage = false,
        isStale = false,
    )
}
