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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.updates.SavedAppUpdate
import com.kraat.lostfilmnewtv.ui.home.HOME_RAIL_ALL_NEW
import com.kraat.lostfilmnewtv.ui.home.HOME_RAIL_FAVORITES
import com.kraat.lostfilmnewtv.ui.home.HomeContentRail
import com.kraat.lostfilmnewtv.ui.home.posterTag
import com.kraat.lostfilmnewtv.ui.home.homeItemKey
import com.kraat.lostfilmnewtv.ui.home.HomeScreen
import com.kraat.lostfilmnewtv.ui.home.HomeUiState
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeScreen_initialFocus_landsOnFirstRailCard() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(state = seededState())
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(posterTag(firstDetailsUrl)).assertIsFocused()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(posterTag(firstDetailsUrl)).assertIsFocused()
    }

    @Test
    fun railFocus_movesUpToUtilityRow_andDownBackToSelectedCard() {
        composeRule.setContent {
            LostFilmTheme {
                var state by remember { mutableStateOf(seededState()) }
                HomeScreen(
                    state = state,
                    savedAppUpdate = SavedAppUpdate(
                        latestVersion = "0.2.0",
                        apkUrl = "https://example.test/app.apk",
                    ),
                    onItemFocused = { focusedKey ->
                        state = state.copy(
                            selectedItemKey = focusedKey,
                            selectedItem = state.items.find { it.detailsUrl == focusedKey },
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithTag(posterTag(firstDetailsUrl)).performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(posterTag(firstDetailsUrl)).assertIsFocused()

        composeRule.onNodeWithTag(posterTag(firstDetailsUrl)).performKeyInput {
            keyDown(Key.DirectionUp)
            keyUp(Key.DirectionUp)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home-action-update").assertIsFocused()

        composeRule.onNodeWithTag("home-action-update").performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(posterTag(firstDetailsUrl)).assertIsFocused()
    }

    @Test
    fun movingFocus_updatesBottomStage() {
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

        composeRule.onNodeWithTag("home-bottom-stage").assertExists()
        composeRule.onNodeWithText("9-1-1").assertExists()

        composeRule.onNodeWithTag(posterTag(firstDetailsUrl)).performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(posterTag(firstDetailsUrl)).assertIsFocused()

        composeRule.onNodeWithTag(posterTag(firstDetailsUrl)).performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(posterTag(secondDetailsUrl)).assertIsFocused()
        composeRule.onNodeWithTag("home-bottom-stage").assertExists()
        composeRule.onNodeWithText("Необратимость").assertExists()
    }

    @Test
    fun favoritesRail_rendersBelowMainRail_andMovesFocusDownWithoutBreakingStage() {
        composeRule.setContent {
            LostFilmTheme {
                var state by remember { mutableStateOf(seededMultiRailState()) }
                HomeScreen(
                    state = state,
                    onItemFocused = { focusedKey ->
                        state = state.copy(selectedItemKey = focusedKey)
                    },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(posterTag(HOME_RAIL_ALL_NEW, firstDetailsUrl)).assertIsFocused()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithText("Избранное").assertExists()
        composeRule.onNodeWithTag(posterTag(HOME_RAIL_ALL_NEW, firstDetailsUrl)).performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(posterTag(HOME_RAIL_FAVORITES, favoriteDetailsUrl)).assertIsFocused()
        composeRule.onNodeWithText("Любимчики").assertExists()
    }
}

private const val firstDetailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"
private const val secondDetailsUrl = "https://www.lostfilm.today/movies/Irreversible"
private const val favoriteDetailsUrl = "https://www.lostfilm.today/series/favorites/season_4/episode_7/"

private fun seededState(): HomeUiState {
    val first = ReleaseSummary(
        id = firstDetailsUrl,
        kind = ReleaseKind.SERIES,
        titleRu = "9-1-1",
        episodeTitleRu = "Маменькин сынок",
        seasonNumber = 9,
        episodeNumber = 13,
        releaseDateRu = "14.03.2026",
        posterUrl = "https://www.lostfilm.today/Static/Images/362/Posters/image_s9.jpg",
        detailsUrl = firstDetailsUrl,
        pageNumber = 1,
        positionInPage = 0,
        fetchedAt = 0L,
    )
    val second = ReleaseSummary(
        id = secondDetailsUrl,
        kind = ReleaseKind.MOVIE,
        titleRu = "Необратимость",
        episodeTitleRu = null,
        seasonNumber = null,
        episodeNumber = null,
        releaseDateRu = "13.03.2026",
        posterUrl = "https://www.lostfilm.today/Static/Images/1080/Posters/image.jpg",
        detailsUrl = secondDetailsUrl,
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

private fun seededMultiRailState(): HomeUiState {
    val first = ReleaseSummary(
        id = firstDetailsUrl,
        kind = ReleaseKind.SERIES,
        titleRu = "9-1-1",
        episodeTitleRu = "Маменькин сынок",
        seasonNumber = 9,
        episodeNumber = 13,
        releaseDateRu = "14.03.2026",
        posterUrl = "https://www.lostfilm.today/Static/Images/362/Posters/image_s9.jpg",
        detailsUrl = firstDetailsUrl,
        pageNumber = 1,
        positionInPage = 0,
        fetchedAt = 0L,
    )
    val favorite = ReleaseSummary(
        id = favoriteDetailsUrl,
        kind = ReleaseKind.SERIES,
        titleRu = "Любимчики",
        episodeTitleRu = "Новая серия",
        seasonNumber = 4,
        episodeNumber = 7,
        releaseDateRu = "14.03.2026",
        posterUrl = "https://www.lostfilm.today/Static/Images/777/Posters/favorites.jpg",
        detailsUrl = favoriteDetailsUrl,
        pageNumber = 1,
        positionInPage = 0,
        fetchedAt = 0L,
    )

    return HomeUiState(
        items = listOf(first),
        rails = listOf(
            HomeContentRail(
                id = HOME_RAIL_ALL_NEW,
                title = "Новые релизы",
                items = listOf(first),
            ),
            HomeContentRail(
                id = HOME_RAIL_FAVORITES,
                title = "Избранное",
                items = listOf(favorite),
            ),
        ),
        selectedItem = first,
        selectedItemKey = homeItemKey(HOME_RAIL_ALL_NEW, first.detailsUrl),
        hasNextPage = false,
    )
}
