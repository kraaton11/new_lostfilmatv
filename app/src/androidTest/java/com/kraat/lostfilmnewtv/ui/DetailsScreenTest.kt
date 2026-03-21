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
    fun seriesDetails_showSeasonEpisodeAndRuDate() {
        composeRule.setDetailsContent(state = DetailsUiState(details = seriesDetails()))

        assertTrue(composeRule.onAllNodesWithText("9-1-1").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Сезон 9, серия 13").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("14 марта 2026").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun seriesDetails_displaysTorrentRowsWithExpectedActions() {
        val rows = listOf(
            row("supported", "Поддерживается", "https://example.com/a.torrent", true),
            row("unsupported", "Без TorrServe", "magnet:?xt=urn:btih:test", false),
        )

        composeRule.setDetailsContent(
            state = DetailsUiState(details = detailsWithRows(rows)),
            torrentRows = rows,
        )

        composeRule.onNodeWithText("Сигнал релиза").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Поддерживается").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag(torrServeTag("supported")).assertIsDisplayed()
        composeRule.onNodeWithTag(openTag("unsupported")).assertExists()
        composeRule.onNodeWithTag("details-open-link").assertExists()
        composeRule.onNodeWithTag(torrServeTag("unsupported")).assertDoesNotExist()
    }

    @Test
    fun torrentSection_usesTorrentRowsAsSingleSourceOfTruth() {
        val rows = listOf(
            row("rendered", "Из torrentRows", "https://example.com/rendered.torrent", true),
        )

        composeRule.setDetailsContent(
            state = DetailsUiState(details = seriesDetails().copy(torrentLinks = emptyList())),
            torrentRows = rows,
        )

        composeRule.onNodeWithText("Сигнал релиза").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Из torrentRows").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag(torrServeTag("rendered")).assertIsDisplayed()
    }

    @Test
    fun movieDetails_hideSeasonEpisode() {
        composeRule.setDetailsContent(state = DetailsUiState(details = movieDetails()))

        assertTrue(composeRule.onAllNodesWithText("Необратимость").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Сезон 9, серия 13").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun cinematicDetails_displaysReleaseSignalAndSecondaryOpenLinkAction() {
        val rows = listOf(
            row("first", "1080p", "https://example.com/1.torrent", true),
            row("second", "720p", "https://example.com/2.torrent", true),
        )

        composeRule.setDetailsContent(
            state = DetailsUiState(details = detailsWithRows(rows)),
            torrentRows = rows,
        )

        composeRule.onNodeWithText("Сигнал релиза").assertIsDisplayed()
        composeRule.onNodeWithTag(torrServeTag("first")).assertIsDisplayed()
        composeRule.onNodeWithTag("details-open-link").assertIsDisplayed()
        composeRule.onNodeWithTag("details-tech-quality").assertIsDisplayed()
    }

    @Test
    fun torrentFocusTraversal_matchesTvExpectations() {
        val rows = listOf(
            row("first", "Первый", "https://example.com/1.torrent", true),
            row("second", "Второй", "magnet:?xt=urn:btih:2", false),
            row("third", "Третий", "https://example.com/3.torrent", true),
        )

        composeRule.setDetailsContent(
            state = DetailsUiState(details = detailsWithRows(rows)),
            torrentRows = rows,
            torrServeMessage = TorrServeMessage(rowId = "second", text = "Ошибка TorrServe"),
        )

        composeRule.onNodeWithTag(torrServeTag("first")).performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithTag(torrServeTag("first")).assertIsFocused()

        composeRule.pressKey(torrServeTag("first"), Key.DirectionLeft)
        composeRule.onNodeWithTag("details-tech-quality").assertIsFocused()

        composeRule.pressKey("details-tech-quality", Key.DirectionUp)
        composeRule.onNodeWithTag(torrServeTag("first")).assertIsFocused()

        composeRule.pressKey(torrServeTag("first"), Key.DirectionDown)
        composeRule.onNodeWithTag(openTag("second")).assertIsFocused()

        composeRule.pressKey(openTag("second"), Key.DirectionDown)
        composeRule.onNodeWithTag(torrServeTag("third")).assertIsFocused()

        composeRule.pressKey(torrServeTag("third"), Key.DirectionUp)
        composeRule.onNodeWithTag(openTag("second")).assertIsFocused()

        composeRule.onNodeWithTag(torrServeTag("first")).performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.pressKey(torrServeTag("first"), Key.DirectionUp)
        composeRule.onNodeWithText("Назад").assertIsFocused()
    }

    @Test
    fun busyState_disablesOnlyTorrServeButtonsAndShowsRowScopedFeedback() {
        val rows = listOf(
            row("active", "Активный", "https://example.com/active.torrent", true),
            row("other", "Другой", "https://example.com/other.torrent", true),
            row("unsupported", "Без TorrServe", "magnet:?xt=urn:btih:unsupported", false),
        )
        val openedLinks = mutableListOf<String>()
        val openedTorrServe = mutableListOf<Pair<String, String>>()

        composeRule.setDetailsContent(
            state = DetailsUiState(details = detailsWithRows(rows)),
            torrentRows = rows,
            torrServeMessage = TorrServeMessage(rowId = "other", text = "Не удалось открыть TorrServe"),
            activeTorrServeRowId = "active",
            isTorrServeBusy = true,
            onOpenLink = openedLinks::add,
            onOpenTorrServe = { rowId, url -> openedTorrServe += rowId to url },
        )

        composeRule.onNodeWithTag("details-open-link").assertIsEnabled().performClick()
        composeRule.onNodeWithTag(torrServeTag("active")).assertIsNotEnabled()
        composeRule.onNodeWithTag(torrServeTag("other")).assertIsNotEnabled()
        composeRule.onNodeWithTag(torrServeTag("unsupported")).assertDoesNotExist()
        composeRule.onNodeWithTag(openTag("unsupported")).assertIsEnabled()
        assertTrue(composeRule.onAllNodesWithText("Открывается...").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("Не удалось открыть TorrServe").assertExists()
        assertTrue(composeRule.onAllNodesWithText("TorrServe").fetchSemanticsNodes().isNotEmpty())
        assertEquals(listOf("https://example.com/active.torrent"), openedLinks)
        assertTrue(openedTorrServe.isEmpty())
    }

    @Test
    fun secondaryOpenLinkAction_opensActiveQualityLink() {
        val rows = listOf(
            row("supported", "Поддерживается", "https://example.com/file.torrent", true),
        )
        var openCount = 0

        composeRule.setContent {
            var isBusy by remember { mutableStateOf(false) }
            LostFilmTheme {
                DetailsScreen(
                    state = DetailsUiState(details = detailsWithRows(rows)),
                    isAuthenticated = true,
                    torrentRows = rows,
                    torrServeMessage = null,
                    activeTorrServeRowId = null,
                    isTorrServeBusy = isBusy,
                    onBack = {},
                    onRetry = {},
                    onOpenLink = {
                        openCount += 1
                        isBusy = true
                    },
                    onOpenTorrServe = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("details-open-link").performClick()
        assertEquals(1, openCount)
    }

    @Test
    fun longTorrentList_keepsFocusedRowVisibleWhileMovingDown() {
        val rows = (0 until 16).map { index ->
            row(
                rowId = "row-$index",
                label = "Вариант ${index + 1}",
                url = "https://example.com/$index.torrent",
                isSupported = index % 2 == 0,
            )
        }

        composeRule.setDetailsContent(
            state = DetailsUiState(details = detailsWithRows(rows)),
            torrentRows = rows,
        )

        composeRule.onNodeWithTag(torrServeTag("row-0")).performSemanticsAction(SemanticsActions.RequestFocus)
        for (index in 0 until rows.lastIndex) {
            composeRule.pressKey(primaryActionTag("row-$index", index % 2 == 0), Key.DirectionDown)
        }

        composeRule.onNodeWithTag(primaryActionTag("row-15", false)).assertIsFocused()
        composeRule.onNodeWithTag(primaryActionTag("row-15", false)).assertIsDisplayed()
    }

    @Test
    fun backFromDetails_restoresFocusedPosterAfterDpadSelection() {
        val movieRows = listOf(row("movie", "Torrent", "https://example.com/movie.torrent", true))

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
                            state = DetailsUiState(details = detailsWithRows(movieRows, movieDetails())),
                            isAuthenticated = true,
                            torrentRows = movieRows,
                            torrServeMessage = null,
                            activeTorrServeRowId = null,
                            isTorrServeBusy = false,
                            onBack = { navController.popBackStack() },
                            onRetry = {},
                            onOpenLink = {},
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

        assertTrue(composeRule.onAllNodesWithText("Назад").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("Назад").performClick()
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithText("Необратимость").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag(posterTag(movieDetailsUrl)).assertIsFocused()
    }
}

private fun AndroidComposeTestRule<*, *>.setDetailsContent(
    state: DetailsUiState,
    isAuthenticated: Boolean = true,
    torrentRows: List<DetailsTorrentRowUiModel> = state.details?.torrentLinks.orEmpty().mapIndexed { index, link ->
        row(
            rowId = "row-$index",
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
    onOpenLink: (String) -> Unit = {},
    onOpenTorrServe: (String, String) -> Unit = { _, _ -> },
) {
    setContent {
        LostFilmTheme {
            DetailsScreen(
                state = state,
                isAuthenticated = isAuthenticated,
                torrentRows = torrentRows,
                torrServeMessage = torrServeMessage,
                activeTorrServeRowId = activeTorrServeRowId,
                isTorrServeBusy = isTorrServeBusy,
                onBack = onBack,
                onRetry = onRetry,
                onOpenLink = onOpenLink,
                onOpenTorrServe = onOpenTorrServe,
            )
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun AndroidComposeTestRule<*, *>.pressKey(tag: String, key: Key) {
    onNodeWithTag(tag).performKeyInput {
        keyDown(key)
        keyUp(key)
    }
    waitForIdle()
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

private fun openTag(rowId: String) = "torrent-open-$rowId"

private fun torrServeTag(rowId: String) = "torrent-torrserve-$rowId"

private fun primaryActionTag(rowId: String, isSupported: Boolean) =
    if (isSupported) torrServeTag(rowId) else openTag(rowId)

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
    torrentLinks = listOf(
        TorrentLink(
            label = "Вариант 1",
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
            label = "Torrent",
            url = "https://example.com/movie.torrent",
        ),
    ),
)
