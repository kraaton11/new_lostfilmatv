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
    fun seriesDetails_showCompactHeroMetaLine() {
        composeRule.setDetailsContent(state = DetailsUiState(details = seriesDetails()))

        assertTrue(composeRule.onAllNodesWithText("9-1-1").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Сезон 9, серия 13").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Маменькин сынок").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("14 марта 2026").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun movieDetails_hideSeasonEpisode() {
        composeRule.setDetailsContent(state = DetailsUiState(details = movieDetails()))

        assertTrue(composeRule.onAllNodesWithText("Необратимость").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Сезон 9, серия 13").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Маменькин сынок").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun detailsScreen_showsSingleResolvedWatchAction() {
        val playbackRow = row("preferred", "720p", "https://example.com/720.torrent", true)

        composeRule.setDetailsContent(
            state = DetailsUiState(details = detailsWithRows(listOf(playbackRow))),
            availableTorrentRowsCount = 2,
            playbackRow = playbackRow,
        )

        composeRule.onNodeWithText("Смотреть").assertExists()
        composeRule.onNodeWithTag(torrServeTag("preferred")).assertIsDisplayed()
        composeRule.onNodeWithText("720p • TorrServe • свежие данные").assertExists()
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
        composeRule.onNodeWithText("Варианты качества не найдены").assertExists()
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
                            onBack = { navController.popBackStack() },
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
    onBack: () -> Unit = {},
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
                onBack = onBack,
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
