package com.kraat.lostfilmnewtv.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.ui.home.HomeScreen
import com.kraat.lostfilmnewtv.ui.home.HomeUiState
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeScreen_showsTitle() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(state = seededState())
            }
        }

        val titleNodes = composeRule.onAllNodesWithText("Новые релизы").fetchSemanticsNodes()

        assertTrue(titleNodes.isNotEmpty())
    }

    @Test
    fun movingFocus_updatesBottomInfoPanel() {
        composeRule.setContent {
            LostFilmTheme {
                var state by remember { mutableStateOf(seededState()) }
                HomeScreen(
                    state = state,
                    onItemFocused = { focusedKey ->
                        state = state.copy(
                            selectedItemKey = focusedKey,
                            selectedItem = state.items.find { it.detailsUrl == focusedKey },
                        )
                    },
                )
            }
        }

        val initialTitleNodes = composeRule.onAllNodesWithText("9-1-1").fetchSemanticsNodes()
        assertTrue(initialTitleNodes.isNotEmpty())

        composeRule.onNodeWithTag("poster-0").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("poster-0").assertIsFocused()

        composeRule.onNodeWithTag("poster-0").performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("poster-1").assertIsFocused()

        val updatedTitleNodes = composeRule.onAllNodesWithText("Необратимость").fetchSemanticsNodes()
        assertTrue(updatedTitleNodes.isNotEmpty())
    }
}

private fun seededState(): HomeUiState {
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

    return HomeUiState(
        items = listOf(first, second),
        selectedItem = first,
        selectedItemKey = first.detailsUrl,
        hasNextPage = true,
    )
}
