package com.kraat.lostfilmnewtv.tvchannel

import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.LostFilmSession
import com.kraat.lostfilmnewtv.data.model.PageState

class HomeChannelBackgroundRefreshRunner(
    private val readMode: () -> AndroidTvChannelMode,
    private val readSession: suspend () -> LostFilmSession?,
    private val isSessionExpired: suspend () -> Boolean,
    private val refreshFirstPage: suspend () -> PageState,
    private val syncChannel: suspend () -> Unit,
    private val readFirstPageFetchedAt: suspend () -> Long? = { null },
    private val refreshFavorites: suspend () -> Boolean = { false },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val minRefreshIntervalMs: Long = DEFAULT_MIN_REFRESH_INTERVAL_MS,
) {
    suspend fun run(): HomeChannelBackgroundRefreshOutcome {
        val mode = readMode()
        when (mode) {
            AndroidTvChannelMode.DISABLED -> {
                return HomeChannelBackgroundRefreshOutcome.SKIPPED_DISABLED
            }

            AndroidTvChannelMode.UNWATCHED,
            AndroidTvChannelMode.FAVORITES,
            -> {
                val session = readSession()
                if (session == null || isSessionExpired()) {
                    return HomeChannelBackgroundRefreshOutcome.SKIPPED_UNAUTHENTICATED
                }
            }

            AndroidTvChannelMode.ALL_NEW -> Unit
        }

        val firstPageFetchedAt = readFirstPageFetchedAt()
        if (firstPageFetchedAt != null && clock() - firstPageFetchedAt < minRefreshIntervalMs) {
            return HomeChannelBackgroundRefreshOutcome.SKIPPED_RECENTLY_REFRESHED
        }

        return when (mode) {
            AndroidTvChannelMode.FAVORITES -> {
                if (refreshFavorites()) {
                    syncChannel()
                    HomeChannelBackgroundRefreshOutcome.REFRESHED
                } else {
                    HomeChannelBackgroundRefreshOutcome.FAILED_RETRYABLE
                }
            }

            AndroidTvChannelMode.ALL_NEW,
            AndroidTvChannelMode.UNWATCHED,
            -> {
                when (refreshFirstPage()) {
                    is PageState.Content -> {
                        syncChannel()
                        HomeChannelBackgroundRefreshOutcome.REFRESHED
                    }

                    is PageState.Error -> HomeChannelBackgroundRefreshOutcome.FAILED_RETRYABLE
                }
            }

            AndroidTvChannelMode.DISABLED -> HomeChannelBackgroundRefreshOutcome.SKIPPED_DISABLED
        }
    }

    private companion object {
        const val DEFAULT_MIN_REFRESH_INTERVAL_MS = 30 * 60 * 1000L
    }
}

enum class HomeChannelBackgroundRefreshOutcome {
    SKIPPED_DISABLED,
    SKIPPED_UNAUTHENTICATED,
    SKIPPED_RECENTLY_REFRESHED,
    REFRESHED,
    FAILED_RETRYABLE,
}
