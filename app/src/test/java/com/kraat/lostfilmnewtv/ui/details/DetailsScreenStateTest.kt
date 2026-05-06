package com.kraat.lostfilmnewtv.ui.details

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsProperties
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.TorrentLink
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DetailsScreenStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun detailsScreen_showsLoadingState_whenLoadingWithoutDetails() {
        composeRule.setContent {
            LostFilmTheme {
                DetailsScreen(
                    state = DetailsUiState(isLoading = true),
                    isAuthenticated = true,
                    availableTorrentRowsCount = 0,
                    playbackRow = null,
                    torrServeMessage = null,
                    activeTorrServeRowId = null,
                    isTorrServeBusy = false,
                    onRetry = {},
                    onOpenTorrServe = { _, _ -> },
                )
            }
        }

        assertEquals(1, composeRule.onAllNodesWithTag("details-loading").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Details").fetchSemanticsNodes().size)
    }

    @Test
    fun detailsScreen_keepsContentVisible_duringBackgroundRefresh() {
        composeRule.setContent {
            LostFilmTheme {
                DetailsScreen(
                    state = DetailsUiState(
                        details = seriesDetails(),
                        isLoading = true,
                    ),
                    isAuthenticated = true,
                    availableTorrentRowsCount = 1,
                    playbackRow = DetailsTorrentRowUiModel(
                        rowId = "row-0",
                        label = "1080p",
                        url = "https://example.com/1080",
                        isTorrServeSupported = true,
                    ),
                    torrServeMessage = null,
                    activeTorrServeRowId = null,
                    isTorrServeBusy = false,
                    onRetry = {},
                    onOpenTorrServe = { _, _ -> },
                )
            }
        }

        assertEquals(1, composeRule.onAllNodesWithText("9-1-1").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("details-loading").fetchSemanticsNodes().size)
    }

    @Test
    fun detailsScreen_showsStaleBanner_whenStateRequestsIt() {
        composeRule.setContent {
            LostFilmTheme {
                DetailsScreen(
                    state = DetailsUiState(
                        details = seriesDetails(),
                        showStaleBanner = true,
                    ),
                    isAuthenticated = true,
                    availableTorrentRowsCount = 1,
                    playbackRow = DetailsTorrentRowUiModel(
                        rowId = "row-0",
                        label = "1080p",
                        url = "https://example.com/1080",
                        isTorrServeSupported = true,
                    ),
                    torrServeMessage = null,
                    activeTorrServeRowId = null,
                    isTorrServeBusy = false,
                    onRetry = {},
                    onOpenTorrServe = { _, _ -> },
                )
            }
        }

        assertEquals(1, composeRule.onAllNodesWithTag("details-stale-banner").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("Данные показаны из кэша и могут быть устаревшими").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("9-1-1").fetchSemanticsNodes().size)
    }

    @Test
    fun detailsScreen_focusesPrimaryAction_whenPlaybackIsAvailable() {
        composeRule.setContent {
            LostFilmTheme {
                DetailsScreen(
                    state = DetailsUiState(
                        details = seriesDetails(),
                    ),
                    isAuthenticated = true,
                    availableTorrentRowsCount = 1,
                    playbackRow = DetailsTorrentRowUiModel(
                        rowId = "row-0",
                        label = "1080p",
                        url = "https://example.com/1080",
                        isTorrServeSupported = true,
                    ),
                    torrServeMessage = null,
                    activeTorrServeRowId = null,
                    isTorrServeBusy = false,
                    onRetry = {},
                    onOpenTorrServe = { _, _ -> },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag("torrent-torrserve-row-0")
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag("torrent-torrserve-row-0").assertIsFocused()
    }

    @Test
    fun detailsScreen_focusesPrimaryAction_whenPlaybackIsUnavailable() {
        composeRule.setContent {
            LostFilmTheme {
                DetailsScreen(
                    state = DetailsUiState(
                        details = seriesDetails(),
                    ),
                    isAuthenticated = true,
                    availableTorrentRowsCount = 0,
                    playbackRow = null,
                    torrServeMessage = null,
                    activeTorrServeRowId = null,
                    isTorrServeBusy = false,
                    onRetry = {},
                    onOpenTorrServe = { _, _ -> },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag("details-primary-action")
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag("details-primary-action").assertIsFocused()
    }

    @Test
    fun detailsScreen_rendersHeroMeta_withSeasonAndEpisode() {
        composeRule.setContent {
            LostFilmTheme {
                DetailsScreen(
                    state = DetailsUiState(
                        details = seriesDetails(),
                    ),
                    isAuthenticated = true,
                    availableTorrentRowsCount = 1,
                    playbackRow = DetailsTorrentRowUiModel(
                        rowId = "row-0",
                        label = "1080p",
                        url = "https://example.com/1080",
                        isTorrServeSupported = true,
                    ),
                    torrServeMessage = null,
                    activeTorrServeRowId = null,
                    isTorrServeBusy = false,
                    onRetry = {},
                    onOpenTorrServe = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("details-hero-meta").assertExists()
    }

    @Test
    fun detailsScreen_rendersSeriesStatus_whenAvailable() {
        composeRule.setContent {
            LostFilmTheme {
                DetailsScreen(
                    state = DetailsUiState(
                        details = seriesDetails(),
                    ),
                    isAuthenticated = true,
                    availableTorrentRowsCount = 1,
                    playbackRow = DetailsTorrentRowUiModel(
                        rowId = "row-0",
                        label = "1080p",
                        url = "https://example.com/1080",
                        isTorrServeSupported = true,
                    ),
                    torrServeMessage = null,
                    activeTorrServeRowId = null,
                    isTorrServeBusy = false,
                    onRetry = {},
                    onOpenTorrServe = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("details-hero-status").assertExists()
        composeRule.onNodeWithText("Статус: Идет 1 сезон").assertExists()
        composeRule.onNodeWithText("Следующая серия: 12 апреля 2026 года").assertExists()
    }

    @Test
    fun detailsScreen_errorState_usesRetryPanelAndHidesHeroContent() {
        composeRule.setContent {
            LostFilmTheme {
                DetailsScreen(
                    state = DetailsUiState(
                        errorMessage = "boom",
                    ),
                    isAuthenticated = true,
                    availableTorrentRowsCount = 0,
                    playbackRow = null,
                    torrServeMessage = null,
                    activeTorrServeRowId = null,
                    isTorrServeBusy = false,
                    onRetry = {},
                    onOpenTorrServe = { _, _ -> },
                )
            }
        }

        assertEquals(1, composeRule.onAllNodesWithText("boom").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("Повторить").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("9-1-1").fetchSemanticsNodes().size)
    }

}

private fun seriesDetails(): ReleaseDetails = ReleaseDetails(
    detailsUrl = "https://example.com/series",
    kind = ReleaseKind.SERIES,
    titleRu = "9-1-1",
    seasonNumber = 9,
    episodeNumber = 13,
    releaseDateRu = "14 марта 2026",
    posterUrl = "https://example.com/poster.jpg",
    fetchedAt = 0L,
    episodeTitleRu = "Маменькин сынок",
    seriesStatusRu = "Идет 1 сезон. Следующая серия: 12 апреля 2026 года",
    torrentLinks = listOf(
        TorrentLink(
            label = "1080p",
            url = "https://example.com/1080",
        ),
    ),
)
