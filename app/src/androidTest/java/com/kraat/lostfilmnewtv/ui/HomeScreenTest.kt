package com.kraat.lostfilmnewtv.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
import com.kraat.lostfilmnewtv.ui.home.HomeFeedMode
import com.kraat.lostfilmnewtv.ui.home.HomeModeContentState
import com.kraat.lostfilmnewtv.ui.home.posterTag
import com.kraat.lostfilmnewtv.ui.home.HomeScreen
import com.kraat.lostfilmnewtv.ui.home.HomeUiState
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme
import org.junit.Assert.assertEquals
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
    fun railFocus_movesUpToActiveModeTab_andDownBackToSelectedCard() {
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

        composeRule.onNodeWithTag("home-mode-tab-all-new").assertIsFocused()

        composeRule.onNodeWithTag("home-mode-tab-all-new").performKeyInput {
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
    fun modeTabs_switchBetweenAllNewAndFavorites_withoutRenderingSecondRail() {
        composeRule.setContent {
            LostFilmTheme {
                var state by remember { mutableStateOf(seededModeState()) }
                HomeScreen(
                    state = state,
                    onItemFocused = { focusedKey ->
                        state = state.copy(
                            selectedItemKey = focusedKey,
                            selectedItem = when (state.selectedMode) {
                                HomeFeedMode.AllNew -> state.items.find { it.detailsUrl == focusedKey }
                                HomeFeedMode.Favorites -> state.favoriteItems.find { it.detailsUrl == focusedKey }
                            },
                        )
                    },
                    onModeSelected = { mode ->
                        state = state.copy(
                            selectedMode = mode,
                            selectedItemKey = when (mode) {
                                HomeFeedMode.AllNew -> firstDetailsUrl
                                HomeFeedMode.Favorites -> favoriteDetailsUrl
                            },
                            selectedItem = when (mode) {
                                HomeFeedMode.AllNew -> state.items.first()
                                HomeFeedMode.Favorites -> state.favoriteItems.first()
                            },
                        )
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

        assertEquals(
            0,
            composeRule.onAllNodesWithTag("home-rail-title-favorites").fetchSemanticsNodes().size,
        )

        composeRule.onNodeWithTag(posterTag(HOME_RAIL_ALL_NEW, firstDetailsUrl)).performKeyInput {
            keyDown(Key.DirectionUp)
            keyUp(Key.DirectionUp)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home-mode-tab-all-new").performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home-mode-tab-favorites").assertIsFocused()

        composeRule.onNodeWithTag("home-mode-tab-favorites").performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(posterTag(HOME_RAIL_FAVORITES, favoriteDetailsUrl)).assertIsFocused()
        composeRule.onNodeWithText("Любимчики").assertExists()
    }

    @Test
    fun focusingNonSelectedModeTab_doesNotSwitchModeWithoutDirectionalInput() {
        composeRule.setContent {
            LostFilmTheme {
                var selectedMode by remember { mutableStateOf(HomeFeedMode.Favorites) }
                HomeScreen(
                    state = seededModeState().copy(
                        selectedMode = selectedMode,
                        selectedItem = seededModeState().favoriteItems.first(),
                        selectedItemKey = favoriteDetailsUrl,
                    ),
                    onModeSelected = { mode -> selectedMode = mode },
                )
            }
        }

        composeRule.onNodeWithTag("home-mode-tab-all-new")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home-mode-tab-all-new").assertIsFocused()
        composeRule.onNodeWithText("Любимчики").assertExists()
    }

    @Test
    fun favoritesContentArrival_movesFocusFromHeaderToFavoriteCardOnStartup() {
        var state by mutableStateOf(
            seededModeState().copy(
                selectedMode = HomeFeedMode.Favorites,
                favoriteItems = emptyList(),
                favoritesModeState = HomeModeContentState.Loading,
                selectedItem = null,
                selectedItemKey = null,
            ),
        )
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = state,
                    onModeSelected = { mode -> state = state.copy(selectedMode = mode) },
                    onItemFocused = { focusedKey ->
                        state = state.copy(
                            selectedItemKey = focusedKey,
                            selectedItem = state.favoriteItems.find { it.detailsUrl == focusedKey },
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithTag("home-mode-tab-favorites")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            state = seededModeState().copy(
                selectedMode = HomeFeedMode.Favorites,
                selectedItem = seededModeState().favoriteItems.first(),
                selectedItemKey = favoriteDetailsUrl,
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(posterTag(HOME_RAIL_FAVORITES, favoriteDetailsUrl)).assertIsFocused()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(posterTag(HOME_RAIL_FAVORITES, favoriteDetailsUrl)).assertIsFocused()
    }

    @Test
    fun movingRightFromFavoritesTab_focusesSettingsAction_withoutSnappingBackToCard() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = seededModeState().copy(
                        selectedMode = HomeFeedMode.Favorites,
                        selectedItem = seededModeState().favoriteItems.first(),
                        selectedItemKey = favoriteDetailsUrl,
                    ),
                    isAuthenticated = true,
                    savedAppUpdate = SavedAppUpdate(
                        latestVersion = "0.2.0",
                        apkUrl = "https://example.test/app.apk",
                    ),
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(posterTag(HOME_RAIL_FAVORITES, favoriteDetailsUrl)).assertIsFocused()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(posterTag(HOME_RAIL_FAVORITES, favoriteDetailsUrl)).performKeyInput {
            keyDown(Key.DirectionUp)
            keyUp(Key.DirectionUp)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home-mode-tab-favorites").assertIsFocused()

        composeRule.onNodeWithTag("home-mode-tab-favorites").performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home-action-settings").assertIsFocused()
    }

    @Test
    fun favoritesContentArrival_doesNotStealFocusFromSettings_afterHeaderNavigation() {
        var state by mutableStateOf(
            seededModeState().copy(
                selectedMode = HomeFeedMode.Favorites,
                favoriteItems = emptyList(),
                favoritesModeState = HomeModeContentState.Loading,
                selectedItem = null,
                selectedItemKey = null,
            ),
        )

        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = state,
                    onModeSelected = { mode -> state = state.copy(selectedMode = mode) },
                )
            }
        }

        composeRule.onNodeWithTag("home-mode-tab-favorites")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home-mode-tab-favorites").assertIsFocused()

        composeRule.onNodeWithTag("home-mode-tab-favorites").performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home-action-settings").assertIsFocused()

        composeRule.runOnIdle {
            state = seededModeState().copy(
                selectedMode = HomeFeedMode.Favorites,
                selectedItem = seededModeState().favoriteItems.first(),
                selectedItemKey = favoriteDetailsUrl,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home-action-settings").assertIsFocused()
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
        allNewModeState = HomeModeContentState.Content(listOf(first, second)),
        selectedItem = first,
        selectedItemKey = first.detailsUrl,
        hasNextPage = true,
    )
}

private fun seededModeState(): HomeUiState {
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
        favoriteItems = listOf(favorite),
        rails = listOf(
            HomeContentRail(
                id = HOME_RAIL_ALL_NEW,
                title = "Новые релизы",
                items = listOf(first),
            ),
        ),
        selectedMode = HomeFeedMode.AllNew,
        availableModes = listOf(HomeFeedMode.AllNew, HomeFeedMode.Favorites),
        allNewModeState = HomeModeContentState.Content(listOf(first)),
        favoritesModeState = HomeModeContentState.Content(listOf(favorite)),
        selectedItem = first,
        selectedItemKey = first.detailsUrl,
        hasNextPage = false,
        isFavoritesRailVisible = true,
    )
}
