package com.kraat.lostfilmnewtv.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.model.TorrentLink
import com.kraat.lostfilmnewtv.ui.details.DetailsScreen
import com.kraat.lostfilmnewtv.ui.details.DetailsTorrentRowUiModel
import com.kraat.lostfilmnewtv.ui.details.DetailsUiState
import com.kraat.lostfilmnewtv.ui.details.TorrServeMessage
import com.kraat.lostfilmnewtv.ui.home.HomeScreen
import com.kraat.lostfilmnewtv.ui.home.HomeUiState
import com.kraat.lostfilmnewtv.ui.home.posterTag
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class DetailsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun seriesDetails_showSeparatedHeroMetaAndBottomStageContext() {
        composeRule.setDetailsContent(state = DetailsUiState(details = seriesDetails()))

        assertTrue(composeRule.onAllNodesWithText("9-1-1").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Маменькин сынок").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag("details-hero-meta").assertExists()
        assertTrue(composeRule.onAllNodesWithText("Сезон 9 • Серия 13").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("1080p • TorrServe").fetchSemanticsNodes().isNotEmpty())
        assertEquals(1, composeRule.onAllNodesWithText("14 марта 2026").fetchSemanticsNodes().size)
    }

    @Test
    fun movieDetails_showMovieMetaAndBottomStageSupport() {
        composeRule.setDetailsContent(state = DetailsUiState(details = movieDetails()))

        assertTrue(composeRule.onAllNodesWithText("Необратимость").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Маменькин сынок").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Фильм").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("1080p • TorrServe").fetchSemanticsNodes().isNotEmpty())
        assertEquals(1, composeRule.onAllNodesWithText("13 марта 2026").fetchSemanticsNodes().size)
    }

    @Test
    fun detailsScreen_showsSingleResolvedWatchAction() {
        val playbackRow = row("preferred", "720p", "https://example.com/720.torrent", true)

        composeRule.setDetailsContent(
            state = DetailsUiState(details = detailsWithRows(listOf(playbackRow))),
            availableTorrentRowsCount = 2,
            playbackRow = playbackRow,
        )

        composeRule.onNodeWithTag(torrServeTag("preferred")).assertIsDisplayed()
        composeRule.onNodeWithText("Сезон 9 • Серия 13").assertExists()
        composeRule.onNodeWithText("720p • TorrServe").assertExists()
    }

    @Test
    fun detailsScreen_clicksResolvedWatchAction() {
        val playbackRow = row("preferred", "1080p", "https://example.com/1080.torrent", true)
        val openedTorrServe = mutableListOf<Pair<String, String>>()

        composeRule.setDetailsContent(
            state = DetailsUiState(details = detailsWithRows(listOf(playbackRow))),
            availableTorrentRowsCount = 1,
            playbackRow = playbackRow,
            onOpenTorrServe = { rowId, url -> openedTorrServe += rowId to url },
        )

        composeRule.onNodeWithTag(torrServeTag("preferred")).assertIsEnabled()
        composeRule.onNodeWithTag(torrServeTag("preferred")).performClick()

        assertEquals(listOf("preferred" to "https://example.com/1080.torrent"), openedTorrServe)
    }

    @Test
    fun detailsScreen_placesBottomStageBelowHero_andAboveBottomEdge() {
        val playbackRow = row("preferred", "1080p", "https://example.com/1080.torrent", true)

        composeRule.setDetailsContent(
            state = DetailsUiState(details = detailsWithRows(listOf(playbackRow))),
            availableTorrentRowsCount = 1,
            playbackRow = playbackRow,
        )

        composeRule.waitForIdle()

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val buttonBounds = composeRule.onNodeWithTag(torrServeTag("preferred")).fetchSemanticsNode().boundsInRoot
        val stageBounds = composeRule.onNodeWithTag("details-bottom-stage").fetchSemanticsNode().boundsInRoot

        assertTrue(stageBounds.top > buttonBounds.bottom)
        assertTrue(stageBounds.bottom < rootBounds.bottom - 16f)
    }

    @Test
    fun busyState_disablesSingleWatchButton_andShowsStatusFeedback() {
        val playbackRow = row("preferred", "1080p", "https://example.com/1080.torrent", true)

        composeRule.setDetailsContent(
            state = DetailsUiState(details = detailsWithRows(listOf(playbackRow))),
            availableTorrentRowsCount = 1,
            playbackRow = playbackRow,
            torrServeMessage = TorrServeMessage(rowId = "preferred", text = "Не удалось открыть TorrServe"),
            activeTorrServeRowId = "preferred",
            isTorrServeBusy = true,
        )

        composeRule.onNodeWithTag(torrServeTag("preferred")).assertIsNotEnabled()
        composeRule.onNodeWithText("Не удалось открыть TorrServe").assertExists()
    }

    @Test
    fun detailsScreen_showsDisabledPrimaryActionWhenNoPlaybackRowExists() {
        composeRule.setDetailsContent(
            state = DetailsUiState(details = seriesDetails().copy(torrentLinks = emptyList())),
            availableTorrentRowsCount = 0,
            playbackRow = null,
        )

        composeRule.onNodeWithTag("details-primary-action").assertIsNotEnabled()
        composeRule.onNodeWithText("Видео недоступно").assertExists()
    }

    @Test
    fun detailsScreen_unavailableStatus_keepsBottomInsetInsideStage() {
        composeRule.setDetailsContent(
            state = DetailsUiState(details = seriesDetails().copy(torrentLinks = emptyList())),
            availableTorrentRowsCount = 0,
            playbackRow = null,
        )

        composeRule.waitForIdle()

        val stageBounds = composeRule.onNodeWithTag("details-bottom-stage").fetchSemanticsNode().boundsInRoot
        val statusBounds = composeRule.onNodeWithText("Видео недоступно").fetchSemanticsNode().boundsInRoot

        assertTrue("stageBounds=$stageBounds statusBounds=$statusBounds", stageBounds.bottom - statusBounds.bottom > 18f)
    }

    @Test
    fun detailsScreen_directionalInput_keepsFocusOnPrimaryWatchAction() {
        val playbackRow = row("preferred", "1080p", "https://example.com/1080.torrent", true)

        composeRule.setDetailsContent(
            state = DetailsUiState(details = detailsWithRows(listOf(playbackRow))),
            availableTorrentRowsCount = 1,
            playbackRow = playbackRow,
        )

        composeRule.onNodeWithTag(torrServeTag("preferred")).assertIsFocused()
        composeRule.onNodeWithTag(torrServeTag("preferred")).performKeyInput {
            keyDown(Key.DirectionUp)
            keyUp(Key.DirectionUp)
            keyDown(Key.DirectionLeft)
            keyUp(Key.DirectionLeft)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(torrServeTag("preferred")).assertIsFocused()
    }

    @Test
    fun detailsScreen_rendersFavoriteAction_withoutStealingInitialFocus() {
        val playbackRow = row("preferred", "1080p", "https://example.com/1080.torrent", true)

        composeRule.setDetailsContent(
            state = DetailsUiState(
                details = detailsWithRows(listOf(playbackRow)).copy(
                    favoriteTargetId = 915,
                    isFavorite = false,
                ),
                favoriteActionLabel = "Добавить в избранное",
                isFavoriteActionEnabled = true,
            ),
            availableTorrentRowsCount = 1,
            playbackRow = playbackRow,
        )

        composeRule.onNodeWithTag("details-favorite-action").assertIsDisplayed()
        composeRule.onNodeWithTag(torrServeTag("preferred")).assertIsFocused()
    }

    @Test
    fun backFromDetails_restoresFocusedPosterAfterSystemBack() {
        val movieRow = row("movie", "1080p", "https://example.com/movie.torrent", true)

        composeRule.setContent {
            LostFilmTheme {
                val navController = rememberNavController()
                var homeState by remember { mutableStateOf(seededHomeState()) }
                NavHost(
                    navController = navController,
                    startDestination = "home",
                ) {
                    composable("home") {
                        HomeScreen(
                            state = homeState,
                            onItemFocused = { focusedKey ->
                                homeState = homeState.copy(
                                    selectedItemKey = focusedKey,
                                    selectedItem = homeState.items.find { it.detailsUrl == focusedKey },
                                )
                            },
                            onOpenDetails = { navController.navigate("details") },
                        )
                    }
                    composable("details") {
                        DetailsScreen(
                            state = DetailsUiState(details = detailsWithRows(listOf(movieRow), movieDetails())),
                            isAuthenticated = true,
                            availableTorrentRowsCount = 1,
                            playbackRow = movieRow,
                            torrServeMessage = null,
                            activeTorrServeRowId = null,
                            isTorrServeBusy = false,
                            onRetry = {},
                            onOpenTorrServe = { _, _ -> },
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(posterTag(seriesDetailsUrl)).performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(posterTag(seriesDetailsUrl)).assertIsFocused()

        composeRule.onNodeWithTag(posterTag(seriesDetailsUrl)).performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(posterTag(movieDetailsUrl)).assertIsFocused()
        assertTrue(composeRule.onAllNodesWithText("Необратимость").fetchSemanticsNodes().isNotEmpty())

        composeRule.onNodeWithTag(posterTag(movieDetailsUrl)).performClick()
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithText("Необратимость").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag(posterTag(movieDetailsUrl)).assertIsFocused()
    }
}

private fun AndroidComposeTestRule<*, *>.setDetailsContent(
    state: DetailsUiState,
    isAuthenticated: Boolean = true,
    availableTorrentRowsCount: Int = state.details?.torrentLinks?.size ?: 0,
    playbackRow: DetailsTorrentRowUiModel? = state.details?.torrentLinks?.firstOrNull()?.let { link ->
        row(
            rowId = "row-0",
            label = link.label,
            url = link.url,
            isSupported = true,
        )
    },
    torrServeMessage: TorrServeMessage? = null,
    activeTorrServeRowId: String? = null,
    isTorrServeBusy: Boolean = false,
    onRetry: () -> Unit = {},
    onOpenTorrServe: (String, String) -> Unit = { _, _ -> },
) {
    setContent {
        LostFilmTheme {
            DetailsScreen(
                state = state,
                isAuthenticated = isAuthenticated,
                availableTorrentRowsCount = availableTorrentRowsCount,
                playbackRow = playbackRow,
                torrServeMessage = torrServeMessage,
                activeTorrServeRowId = activeTorrServeRowId,
                isTorrServeBusy = isTorrServeBusy,
                onRetry = onRetry,
                onOpenTorrServe = onOpenTorrServe,
            )
        }
    }
}

private fun row(
    rowId: String,
    label: String,
    url: String,
    isSupported: Boolean,
): DetailsTorrentRowUiModel = DetailsTorrentRowUiModel(
    rowId = rowId,
    label = label,
    url = url,
    isTorrServeSupported = isSupported,
)

private fun detailsWithRows(
    rows: List<DetailsTorrentRowUiModel>,
    base: ReleaseDetails = seriesDetails(),
): ReleaseDetails = base.copy(
    torrentLinks = rows.map { TorrentLink(label = it.label, url = it.url) },
)

private fun torrServeTag(rowId: String) = "torrent-torrserve-$rowId"

private const val seriesDetailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"
private const val movieDetailsUrl = "https://www.lostfilm.today/movies/Irreversible"

private fun seededHomeState(selectedSecond: Boolean = false): HomeUiState {
    val first = ReleaseSummary(
        id = seriesDetailsUrl,
        kind = ReleaseKind.SERIES,
        titleRu = "9-1-1",
        episodeTitleRu = "Маменькин сынок",
        seasonNumber = 9,
        episodeNumber = 13,
        releaseDateRu = "14.03.2026",
        posterUrl = "https://www.lostfilm.today/Static/Images/362/Posters/image_s9.jpg",
        detailsUrl = seriesDetailsUrl,
        pageNumber = 1,
        positionInPage = 0,
        fetchedAt = 0L,
    )
    val second = ReleaseSummary(
        id = movieDetailsUrl,
        kind = ReleaseKind.MOVIE,
        titleRu = "Необратимость",
        episodeTitleRu = null,
        seasonNumber = null,
        episodeNumber = null,
        releaseDateRu = "13.03.2026",
        posterUrl = "https://www.lostfilm.today/Static/Images/1080/Posters/image.jpg",
        detailsUrl = movieDetailsUrl,
        pageNumber = 1,
        positionInPage = 1,
        fetchedAt = 0L,
    )

    val selectedItem = if (selectedSecond) second else first

    return HomeUiState(
        items = listOf(first, second),
        selectedItem = selectedItem,
        selectedItemKey = selectedItem.detailsUrl,
        hasNextPage = true,
    )
}

private fun seriesDetails(): ReleaseDetails = ReleaseDetails(
    detailsUrl = seriesDetailsUrl,
    kind = ReleaseKind.SERIES,
    titleRu = "9-1-1",
    seasonNumber = 9,
    episodeNumber = 13,
    releaseDateRu = "14 марта 2026",
    posterUrl = "https://www.lostfilm.today/Static/Images/362/Posters/e_9_13.jpg",
    fetchedAt = 0L,
    episodeTitleRu = "Маменькин сынок",
    torrentLinks = listOf(
        TorrentLink(
            label = "1080p",
            url = "https://www.lostfilm.today/V/?fixture=1",
        ),
    ),
)

private fun movieDetails(): ReleaseDetails = ReleaseDetails(
    detailsUrl = movieDetailsUrl,
    kind = ReleaseKind.MOVIE,
    titleRu = "Необратимость",
    seasonNumber = null,
    episodeNumber = null,
    releaseDateRu = "13 марта 2026",
    posterUrl = "https://www.lostfilm.today/Static/Images/1080/Posters/poster.jpg",
    fetchedAt = 0L,
    torrentLinks = listOf(
        TorrentLink(
            label = "1080p",
            url = "https://example.com/movie.torrent",
        ),
    ),
)
