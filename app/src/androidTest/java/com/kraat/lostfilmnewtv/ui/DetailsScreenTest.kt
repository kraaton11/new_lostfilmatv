package com.kraat.lostfilmnewtv.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
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
import com.kraat.lostfilmnewtv.ui.details.DetailsScreen
import com.kraat.lostfilmnewtv.ui.details.DetailsUiState
import com.kraat.lostfilmnewtv.ui.home.HomeScreen
import com.kraat.lostfilmnewtv.ui.home.HomeUiState
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class DetailsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun seriesDetails_showSeasonEpisodeAndRuDate() {
        composeRule.setContent {
            LostFilmTheme {
                DetailsScreen(
                    state = DetailsUiState(
                        details = seriesDetails(),
                    ),
                    onBack = {},
                    onRetry = {},
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithText("9-1-1").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Сезон 9, серия 13").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("14 марта 2026").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun movieDetails_hideSeasonEpisode() {
        composeRule.setContent {
            LostFilmTheme {
                DetailsScreen(
                    state = DetailsUiState(
                        details = movieDetails(),
                    ),
                    onBack = {},
                    onRetry = {},
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithText("Необратимость").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Сезон 9, серия 13").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun backFromDetails_restoresFocusedPosterAfterDpadSelection() {
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
                            state = DetailsUiState(details = movieDetails()),
                            onBack = { navController.popBackStack() },
                            onRetry = {},
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster-0").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster-0").assertIsFocused()

        composeRule.onNodeWithTag("poster-0").performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster-1").assertIsFocused()
        assertTrue(composeRule.onAllNodesWithText("Необратимость").fetchSemanticsNodes().isNotEmpty())

        composeRule.onNodeWithTag("poster-1").performClick()
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithText("Назад").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("Назад").performClick()
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithText("Необратимость").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag("poster-1").assertIsFocused()
    }
}

private fun seededHomeState(selectedSecond: Boolean = false): HomeUiState {
    val first = ReleaseSummary(
        id = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/",
        kind = ReleaseKind.SERIES,
        titleRu = "9-1-1",
        episodeTitleRu = "Маменькин сынок",
        seasonNumber = 9,
        episodeNumber = 13,
        releaseDateRu = "14.03.2026",
        posterUrl = "https://www.lostfilm.today/Static/Images/362/Posters/image_s9.jpg",
        detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/",
        pageNumber = 1,
        positionInPage = 0,
        fetchedAt = 0L,
    )
    val second = ReleaseSummary(
        id = "https://www.lostfilm.today/movies/Irreversible",
        kind = ReleaseKind.MOVIE,
        titleRu = "Необратимость",
        episodeTitleRu = null,
        seasonNumber = null,
        episodeNumber = null,
        releaseDateRu = "13.03.2026",
        posterUrl = "https://www.lostfilm.today/Static/Images/1080/Posters/image.jpg",
        detailsUrl = "https://www.lostfilm.today/movies/Irreversible",
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
    detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/",
    kind = ReleaseKind.SERIES,
    titleRu = "9-1-1",
    seasonNumber = 9,
    episodeNumber = 13,
    releaseDateRu = "14 марта 2026",
    posterUrl = "https://www.lostfilm.today/Static/Images/362/Posters/e_9_13.jpg",
    fetchedAt = 0L,
)

private fun movieDetails(): ReleaseDetails = ReleaseDetails(
    detailsUrl = "https://www.lostfilm.today/movies/Irreversible",
    kind = ReleaseKind.MOVIE,
    titleRu = "Необратимость",
    seasonNumber = null,
    episodeNumber = null,
    releaseDateRu = "13 марта 2026",
    posterUrl = "https://www.lostfilm.today/Static/Images/1080/Posters/poster.jpg",
    fetchedAt = 0L,
)
