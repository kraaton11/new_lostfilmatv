package com.kraat.lostfilmnewtv.tvchannel

import com.kraat.lostfilmnewtv.data.model.LostFilmSession
import com.kraat.lostfilmnewtv.data.model.PageState

class HomeChannelBackgroundRefreshRunner(
    private val readMode: () -> AndroidTvChannelMode,
    private val readSession: suspend () -> LostFilmSession?,
    private val isSessionExpired: suspend () -> Boolean,
    private val refreshFirstPage: suspend () -> PageState,
    private val syncChannel: suspend () -> Unit,
) {
    suspend fun run(): HomeChannelBackgroundRefreshOutcome {
        if (readMode() == AndroidTvChannelMode.DISABLED) {
            return HomeChannelBackgroundRefreshOutcome.SKIPPED_DISABLED
        }

        val session = readSession()
        if (session == null || isSessionExpired()) {
            return HomeChannelBackgroundRefreshOutcome.SKIPPED_UNAUTHENTICATED
        }

        return when (refreshFirstPage()) {
            is PageState.Content -> {
                syncChannel()
                HomeChannelBackgroundRefreshOutcome.REFRESHED
            }

            is PageState.Error -> HomeChannelBackgroundRefreshOutcome.FAILED_RETRYABLE
        }
    }
}

enum class HomeChannelBackgroundRefreshOutcome {
    SKIPPED_DISABLED,
    SKIPPED_UNAUTHENTICATED,
    REFRESHED,
    FAILED_RETRYABLE,
}
