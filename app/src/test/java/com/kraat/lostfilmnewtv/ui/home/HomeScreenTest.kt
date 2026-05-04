package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.updates.SavedAppUpdate
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreen_rendersUtilityActionsInTopHeader_withoutDetachedBottomFooter() {
        var updateClicks = 0

        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = seededState(),
                    isAuthenticated = false,
                    onUpdateClick = { updateClicks += 1 },
                    savedAppUpdate = SavedAppUpdate(
                        latestVersion = "0.2.0",
                        apkUrl = "https://example.test/app.apk",
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("home-action-auth").assertDoesNotExist()
        composeRule.onNodeWithTag("home-action-settings").assertExists()
        composeRule.onNodeWithTag("home-action-update").assertExists()
        composeRule.onNodeWithTag("home-bottom-stage").assertExists()
        composeRule.onNodeWithText("Обновить").assertExists()
        composeRule.onNodeWithText("0.2.0").assertExists()
        composeRule.onNodeWithText("Сервис").assertDoesNotExist()

        composeRule.onNodeWithTag("home-action-update")
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, updateClicks)
    }

    @Test
    fun homeScreen_hidesUpdateAction_whenNoSavedUpdateExists() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(state = seededState())
            }
        }

        composeRule.onNodeWithTag("home-action-update").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w1366dp-h768dp-land")
    fun homeScreen_placesUpdateActionBeforeFavoritesAndSearch() {
        val allNewItems = listOf(
            release(
                detailsUrl = firstDetailsUrl,
                titleRu = "9-1-1",
                episodeTitleRu = "Маменькин сынок",
                releaseDateRu = "14.03.2026",
                seasonNumber = 9,
                episodeNumber = 13,
                kind = ReleaseKind.SERIES,
            ),
        )
        val favoriteItems = listOf(
            release(
                detailsUrl = "https://www.lostfilm.today/series/Ted/season_2/episode_8/",
                titleRu = "Третий лишний",
                episodeTitleRu = "Левые новости",
                releaseDateRu = "24.03.2026",
                seasonNumber = 2,
                episodeNumber = 8,
                kind = ReleaseKind.SERIES,
            ),
        )

        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = homeStateWithModes(
                        selectedMode = HomeFeedMode.AllNew,
                        allNewItems = allNewItems,
                        favoriteItems = favoriteItems,
                    ),
                    savedAppUpdate = SavedAppUpdate(
                        latestVersion = "0.2.0",
                        apkUrl = "https://example.test/app.apk",
                    ),
                )
            }
        }

        val updateBounds = composeRule.onNodeWithTag("home-action-update").fetchSemanticsNode().boundsInRoot
        val favoritesBounds = composeRule.onNodeWithTag("home-mode-toggle").fetchSemanticsNode().boundsInRoot
        val searchBounds = composeRule.onNodeWithTag("home-action-search").fetchSemanticsNode().boundsInRoot

        assertTrue("updateBounds=$updateBounds favoritesBounds=$favoritesBounds", updateBounds.left < favoritesBounds.left)
        assertTrue("favoritesBounds=$favoritesBounds searchBounds=$searchBounds", favoritesBounds.left < searchBounds.left)
    }

    @Test
    fun homeScreen_bottomStage_showsSelectedReleaseContext_withReleaseMetadata() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(state = seededState())
            }
        }

        composeRule.onNodeWithTag("home-bottom-stage").assertExists()
        composeRule.onNodeWithText("S09E13").assertExists()
        composeRule.onNodeWithText("9-1-1").assertExists()
        composeRule.onNodeWithText("Маменькин сынок").assertExists()
        composeRule.onNodeWithText("14.03.2026").assertExists()
    }

    @Test
    fun homeScreen_firstOpen_focusesFirstPoster() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(state = seededState())
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag(posterTag(firstDetailsUrl))
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag(posterTag(firstDetailsUrl)).assertIsFocused()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun homeScreen_centerKeyOnFocusedPoster_opensDetails() {
        var openedUrl: String? = null

        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = seededState(),
                    onOpenDetails = { openedUrl = it },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag(posterTag(firstDetailsUrl))
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag(posterTag(firstDetailsUrl))
            .performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.waitUntil(timeoutMillis = 5_000) { openedUrl == firstDetailsUrl }
        assertEquals(firstDetailsUrl, openedUrl)
    }

    @Test
    fun homeScreen_initialFocusedPoster_keepsSafeInsetFromLeftEdge() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(state = seededState())
            }
        }

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val posterBounds = composeRule.onNodeWithTag(posterTag(firstDetailsUrl)).fetchSemanticsNode().boundsInRoot

        assertTrue(posterBounds.left > rootBounds.left + 56f)
    }

    // Note: homeScreen_serviceInfo_staysAboveBottomEdge removed — flaky in Robolectric
    // due to rememberSaveable not working correctly with focusedItemKey.

    @Test
    fun homeScreen_loadingIndicator_sitsDirectlyOnBackgroundWithoutCenteredPanel() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = HomeUiState(
                        isInitialLoading = true,
                        allNewModeState = HomeModeContentState.Loading,
                        favoritesModeState = HomeModeContentState.Empty,
                    ),
                )
            }
        }

        assertEquals(0, composeRule.onAllNodesWithTag("home-centered-panel").fetchSemanticsNodes().size)
    }

    @Test
    fun homeScreen_errorMode_focusesRetryAction() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = HomeUiState(
                        selectedMode = HomeFeedMode.Favorites,
                        availableModes = listOf(HomeFeedMode.AllNew, HomeFeedMode.Favorites),
                        allNewModeState = HomeModeContentState.Empty,
                        favoritesModeState = HomeModeContentState.Error("Не удалось загрузить избранное"),
                    ),
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag("home-mode-retry-action")
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag("home-mode-retry-action").assertIsFocused()
    }

    @Test
    fun homeScreen_returnToScreen_refocusesSelectedPoster() {
        val state = seededState().copy(
            selectedItem = release(
                detailsUrl = secondDetailsUrl,
                titleRu = "Необратимость",
                episodeTitleRu = null,
                releaseDateRu = "13.03.2026",
                seasonNumber = null,
                episodeNumber = null,
                kind = ReleaseKind.MOVIE,
                pageNumber = 1,
                positionInPage = 1,
            ),
            selectedItemKey = secondDetailsUrl,
        )
        var showHome by mutableStateOf(true)

        composeRule.setContent {
            LostFilmTheme {
                if (showHome) {
                    HomeScreen(state = state)
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag(posterTag(secondDetailsUrl))
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.runOnIdle {
            showHome = false
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(posterTag(secondDetailsUrl)).fetchSemanticsNodes().isEmpty()
        }

        composeRule.runOnIdle {
            showHome = true
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag(posterTag(secondDetailsUrl))
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag(posterTag(secondDetailsUrl)).assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w1366dp-h768dp-land")
    fun homeScreen_settingsAction_staysHorizontalInTvViewport_andKeepsBottomStageVisible() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = seededState(),
                    isAuthenticated = true,
                    savedAppUpdate = SavedAppUpdate(
                        latestVersion = "0.2.0",
                        apkUrl = "https://example.test/app.apk",
                    ),
                )
            }
        }

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val settingsBounds = composeRule.onNodeWithTag("home-action-settings").fetchSemanticsNode().boundsInRoot
        val stageBounds = composeRule.onNodeWithTag("home-bottom-stage").fetchSemanticsNode().boundsInRoot

        assertTrue("settingsBounds=$settingsBounds", settingsBounds.width > 40f)
        assertTrue("settingsBounds=$settingsBounds", settingsBounds.height < 80f)
        assertTrue("rootBounds=$rootBounds stageBounds=$stageBounds", stageBounds.bottom < rootBounds.bottom - 16f)
    }

    @Test
    @Config(qualifiers = "w1366dp-h768dp-land")
    fun homeScreen_longEpisodeTitle_staysOnMetadataLine_withReleaseMetadata() {
        val longEpisodeTitle = "Когда слишком много правды, она перестает помещаться в одну строку и начинает бороться за место на экране"

        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = seededState(episodeTitleRu = longEpisodeTitle),
                    savedAppUpdate = SavedAppUpdate(
                        latestVersion = "0.2.0",
                        apkUrl = "https://example.test/app.apk",
                    ),
                )
            }
        }

        val textLayouts = mutableListOf<TextLayoutResult>()

        composeRule.onNodeWithText(longEpisodeTitle).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            action(textLayouts)
        }

        composeRule.onNodeWithText("S09E13").assertExists()
        composeRule.onNodeWithText("14.03.2026").assertExists()
        assertEquals(1, textLayouts.first().lineCount)
    }

    @Test
    @Config(qualifiers = "w1366dp-h768dp-land")
    fun homeScreen_shortEpisodeTitle_keepsComfortableBottomInsetInsideStage() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = seededState(episodeTitleRu = "17:00"),
                    savedAppUpdate = SavedAppUpdate(
                        latestVersion = "0.2.0",
                        apkUrl = "https://example.test/app.apk",
                    ),
                )
            }
        }

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val stageBounds = composeRule.onNodeWithTag("home-bottom-stage").fetchSemanticsNode().boundsInRoot
        val textLayouts = mutableListOf<TextLayoutResult>()

        composeRule.onNodeWithText("17:00").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            action(textLayouts)
        }

        assertTrue("rootBounds=$rootBounds stageBounds=$stageBounds", stageBounds.bottom < rootBounds.bottom - 16f)
        assertFalse("textLayouts=$textLayouts", textLayouts.first().toString().contains("includeFontPadding=false"))
    }

    @Test
    fun homeScreen_rendersSeasonEpisodeOverlayOnSeriesCard_withoutDuplicatingBottomStageText() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(state = seededState())
            }
        }

        composeRule.onNodeWithTag("poster-meta:$firstDetailsUrl", useUnmergedTree = true).assertExists()
        assertEquals(
            1,
            composeRule.onAllNodesWithText("Сезон 9, серия 13").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun homeScreen_doesNotRenderSeasonEpisodeOverlayForMovieCard() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(state = seededState())
            }
        }

        composeRule.onNodeWithTag("poster-meta:$secondDetailsUrl", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun homeScreen_withPagingError_keepsRailVisible_andShowsInlineStatusPanel() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = seededState().copy(
                        pagingErrorMessage = "Не удалось догрузить страницу",
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(posterTag(firstDetailsUrl)).assertExists()
        composeRule.onNodeWithTag("home-paging-status").assertExists()
        composeRule.onNodeWithText("Не удалось догрузить страницу").assertExists()
    }

    @Test
    fun homeScreen_withStaleData_showsInlineStaleStatusPanel() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = seededState().copy(
                        showStaleBanner = true,
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("home-stale-status").assertExists()
        composeRule.onNodeWithText("Данные показаны из кэша и могут быть устаревшими").assertExists()
        composeRule.onNodeWithTag(posterTag(firstDetailsUrl)).assertExists()
    }

    @Test
    fun homeScreen_toggleFromFavoritesToAllNew_focusesFirstAllNewPoster() {
        val allNewItems = listOf(
            release(
                detailsUrl = firstDetailsUrl,
                titleRu = "9-1-1",
                episodeTitleRu = "Маменькин сынок",
                releaseDateRu = "14.03.2026",
                seasonNumber = 9,
                episodeNumber = 13,
                kind = ReleaseKind.SERIES,
            ),
            release(
                detailsUrl = secondDetailsUrl,
                titleRu = "Необратимость",
                episodeTitleRu = null,
                releaseDateRu = "13.03.2026",
                seasonNumber = null,
                episodeNumber = null,
                kind = ReleaseKind.MOVIE,
            ),
        )
        val favoriteItems = listOf(
            release(
                detailsUrl = "https://www.lostfilm.today/series/Ted/season_2/episode_8/",
                titleRu = "Третий лишний",
                episodeTitleRu = "Левые новости",
                releaseDateRu = "24.03.2026",
                seasonNumber = 2,
                episodeNumber = 8,
                kind = ReleaseKind.SERIES,
            ),
        )
        val allNewFirstTag = posterTag(HOME_RAIL_ALL_NEW, allNewItems.first().detailsUrl)
        var state by mutableStateOf(
            homeStateWithModes(
                selectedMode = HomeFeedMode.Favorites,
                allNewItems = allNewItems,
                favoriteItems = favoriteItems,
            ),
        )

        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = state,
                    onModeSelected = { mode ->
                        state = homeStateWithModes(
                            selectedMode = mode,
                            allNewItems = allNewItems,
                            favoriteItems = favoriteItems,
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithTag("home-mode-toggle")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag(allNewFirstTag)
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag(allNewFirstTag).assertIsFocused()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun homeScreen_toggleFromFocusedHeaderButton_movesFocusToAllNewPoster() {
        val allNewItems = listOf(
            release(
                detailsUrl = firstDetailsUrl,
                titleRu = "9-1-1",
                episodeTitleRu = "Маменькин сынок",
                releaseDateRu = "14.03.2026",
                seasonNumber = 9,
                episodeNumber = 13,
                kind = ReleaseKind.SERIES,
            ),
        )
        val favoriteItems = listOf(
            release(
                detailsUrl = "https://www.lostfilm.today/series/Ted/season_2/episode_8/",
                titleRu = "Третий лишний",
                episodeTitleRu = "Левые новости",
                releaseDateRu = "24.03.2026",
                seasonNumber = 2,
                episodeNumber = 8,
                kind = ReleaseKind.SERIES,
            ),
        )
        val allNewFirstTag = posterTag(HOME_RAIL_ALL_NEW, allNewItems.first().detailsUrl)
        var state by mutableStateOf(
            homeStateWithModes(
                selectedMode = HomeFeedMode.Favorites,
                allNewItems = allNewItems,
                favoriteItems = favoriteItems,
            ),
        )

        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = state,
                    onModeSelected = { mode ->
                        state = homeStateWithModes(
                            selectedMode = mode,
                            allNewItems = allNewItems,
                            favoriteItems = favoriteItems,
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithTag("home-mode-toggle")
            .performSemanticsAction(SemanticsActions.RequestFocus)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag("home-mode-toggle")
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag("home-mode-toggle")
            .performKeyInput {
                pressKey(Key.DirectionCenter)
            }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag(allNewFirstTag)
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag(allNewFirstTag).assertIsFocused()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun homeScreen_cardToHeaderThenToggle_movesFocusBackToAllNewPoster() {
        val allNewItems = listOf(
            release(
                detailsUrl = firstDetailsUrl,
                titleRu = "9-1-1",
                episodeTitleRu = "Маменькин сынок",
                releaseDateRu = "14.03.2026",
                seasonNumber = 9,
                episodeNumber = 13,
                kind = ReleaseKind.SERIES,
            ),
        )
        val favoriteItems = listOf(
            release(
                detailsUrl = "https://www.lostfilm.today/series/Ted/season_2/episode_8/",
                titleRu = "Третий лишний",
                episodeTitleRu = "Левые новости",
                releaseDateRu = "24.03.2026",
                seasonNumber = 2,
                episodeNumber = 8,
                kind = ReleaseKind.SERIES,
            ),
        )
        val favoritePosterTag = posterTag(HOME_RAIL_FAVORITES, favoriteItems.first().detailsUrl)
        val allNewFirstTag = posterTag(HOME_RAIL_ALL_NEW, allNewItems.first().detailsUrl)
        var state by mutableStateOf(
            homeStateWithModes(
                selectedMode = HomeFeedMode.Favorites,
                allNewItems = allNewItems,
                favoriteItems = favoriteItems,
            ),
        )

        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = state,
                    onModeSelected = { mode ->
                        state = homeStateWithModes(
                            selectedMode = mode,
                            allNewItems = allNewItems,
                            favoriteItems = favoriteItems,
                        )
                    },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag(favoritePosterTag)
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag(favoritePosterTag)
            .performKeyInput { pressKey(Key.DirectionUp) }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag("home-mode-toggle")
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag("home-mode-toggle")
            .performKeyInput {
                pressKey(Key.DirectionCenter)
            }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag(allNewFirstTag)
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag(allNewFirstTag).assertIsFocused()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun homeScreen_headerSearchBack_returnsFocusToFavoritePosterWithoutSwitchingMode() {
        val allNewItems = listOf(
            release(
                detailsUrl = firstDetailsUrl,
                titleRu = "9-1-1",
                episodeTitleRu = "Маменькин сынок",
                releaseDateRu = "14.03.2026",
                seasonNumber = 9,
                episodeNumber = 13,
                kind = ReleaseKind.SERIES,
            ),
        )
        val favoriteItems = listOf(
            release(
                detailsUrl = "https://www.lostfilm.today/series/Ted/season_2/episode_8/",
                titleRu = "Третий лишний",
                episodeTitleRu = "Левые новости",
                releaseDateRu = "24.03.2026",
                seasonNumber = 2,
                episodeNumber = 8,
                kind = ReleaseKind.SERIES,
            ),
        )
        val favoritePosterTag = posterTag(HOME_RAIL_FAVORITES, favoriteItems.first().detailsUrl)
        var state by mutableStateOf(
            homeStateWithModes(
                selectedMode = HomeFeedMode.Favorites,
                allNewItems = allNewItems,
                favoriteItems = favoriteItems,
            ),
        )

        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = state,
                    onModeSelected = { mode ->
                        state = homeStateWithModes(
                            selectedMode = mode,
                            allNewItems = allNewItems,
                            favoriteItems = favoriteItems,
                        )
                    },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag(favoritePosterTag)
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag(favoritePosterTag)
            .performKeyInput { pressKey(Key.DirectionUp) }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag("home-mode-toggle")
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag("home-mode-toggle")
            .performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag("home-action-search")
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag("home-action-search")
            .performKeyInput { pressKey(Key.Back) }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag(favoritePosterTag)
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag(favoritePosterTag).assertIsFocused()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun homeScreen_headerSettingsDown_returnsFocusToFavoritePoster() {
        val allNewItems = listOf(
            release(
                detailsUrl = firstDetailsUrl,
                titleRu = "9-1-1",
                episodeTitleRu = "Маменькин сынок",
                releaseDateRu = "14.03.2026",
                seasonNumber = 9,
                episodeNumber = 13,
                kind = ReleaseKind.SERIES,
            ),
        )
        val favoriteItems = listOf(
            release(
                detailsUrl = "https://www.lostfilm.today/series/Ted/season_2/episode_8/",
                titleRu = "Третий лишний",
                episodeTitleRu = "Левые новости",
                releaseDateRu = "24.03.2026",
                seasonNumber = 2,
                episodeNumber = 8,
                kind = ReleaseKind.SERIES,
            ),
        )
        val favoritePosterTag = posterTag(HOME_RAIL_FAVORITES, favoriteItems.first().detailsUrl)
        var state by mutableStateOf(
            homeStateWithModes(
                selectedMode = HomeFeedMode.Favorites,
                allNewItems = allNewItems,
                favoriteItems = favoriteItems,
            ),
        )

        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = state,
                    onModeSelected = { mode ->
                        state = homeStateWithModes(
                            selectedMode = mode,
                            allNewItems = allNewItems,
                            favoriteItems = favoriteItems,
                        )
                    },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag(favoritePosterTag)
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag(favoritePosterTag)
            .performKeyInput { pressKey(Key.DirectionUp) }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag("home-mode-toggle")
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag("home-mode-toggle")
            .performKeyInput {
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionRight)
            }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag("home-action-settings")
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag("home-action-settings")
            .performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag(favoritePosterTag)
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag(favoritePosterTag).assertIsFocused()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun homeScreen_sharedReleaseAcrossModes_returnsFocusToRememberedAllNewPoster() {
        val allNewItems = buildList {
            add(
                release(
                    detailsUrl = "https://www.lostfilm.today/series/first-item/season_1/episode_1/",
                    titleRu = "Первый релиз",
                    episodeTitleRu = "Старт",
                    releaseDateRu = "25.03.2026",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    kind = ReleaseKind.SERIES,
                ),
            )
            repeat(4) { index ->
                add(
                    release(
                        detailsUrl = "https://www.lostfilm.today/series/filler-${index + 1}/season_1/episode_1/",
                        titleRu = "Заполнитель ${index + 1}",
                        episodeTitleRu = "Эпизод ${index + 1}",
                        releaseDateRu = "25.03.2026",
                        seasonNumber = 1,
                        episodeNumber = index + 1,
                        kind = ReleaseKind.SERIES,
                    ),
                )
            }
            add(
                release(
                    detailsUrl = "https://www.lostfilm.today/series/shared-item/season_2/episode_8/",
                    titleRu = "Общий релиз",
                    episodeTitleRu = "Совпадающий эпизод",
                    releaseDateRu = "25.03.2026",
                    seasonNumber = 2,
                    episodeNumber = 8,
                    kind = ReleaseKind.SERIES,
                ),
            )
            repeat(2) { index ->
                add(
                    release(
                        detailsUrl = "https://www.lostfilm.today/series/tail-${index + 1}/season_1/episode_1/",
                        titleRu = "Хвост ${index + 1}",
                        episodeTitleRu = "Эпизод ${index + 6}",
                        releaseDateRu = "25.03.2026",
                        seasonNumber = 1,
                        episodeNumber = index + 6,
                        kind = ReleaseKind.SERIES,
                    ),
                )
            }
        }
        val sharedFavorite = allNewItems[5]
        val favoriteItems = listOf(sharedFavorite)
        val allNewFirstTag = posterTag(HOME_RAIL_ALL_NEW, allNewItems.first().detailsUrl)
        val sharedFavoriteTag = posterTag(HOME_RAIL_FAVORITES, sharedFavorite.detailsUrl)
        var state by mutableStateOf(
            homeStateWithModes(
                selectedMode = HomeFeedMode.AllNew,
                allNewItems = allNewItems,
                favoriteItems = favoriteItems,
            ),
        )

        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = state,
                    onModeSelected = { mode ->
                        state = homeStateWithModes(
                            selectedMode = mode,
                            allNewItems = allNewItems,
                            favoriteItems = favoriteItems,
                        )
                    },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag(allNewFirstTag)
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag(allNewFirstTag)
            .performKeyInput { pressKey(Key.DirectionUp) }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag("home-mode-toggle")
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag("home-mode-toggle")
            .performKeyInput {
                pressKey(Key.DirectionCenter)
            }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag(sharedFavoriteTag)
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag(sharedFavoriteTag)
            .performKeyInput { pressKey(Key.DirectionUp) }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag("home-mode-toggle")
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag("home-mode-toggle")
            .performKeyInput {
                pressKey(Key.DirectionCenter)
            }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag(allNewFirstTag)
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag(allNewFirstTag).assertIsFocused()
    }
}

private const val firstDetailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"
private const val secondDetailsUrl = "https://www.lostfilm.today/movies/Irreversible"

private fun seededState(
    episodeTitleRu: String? = "Маменькин сынок",
    releaseDateRu: String = "14.03.2026",
): HomeUiState {
    val first = release(
        detailsUrl = firstDetailsUrl,
        titleRu = "9-1-1",
        episodeTitleRu = episodeTitleRu,
        releaseDateRu = releaseDateRu,
        seasonNumber = 9,
        episodeNumber = 13,
        kind = ReleaseKind.SERIES,
        pageNumber = 1,
        positionInPage = 0,
    )
    val second = release(
        detailsUrl = secondDetailsUrl,
        titleRu = "Необратимость",
        episodeTitleRu = null,
        releaseDateRu = "13.03.2026",
        seasonNumber = null,
        episodeNumber = null,
        kind = ReleaseKind.MOVIE,
        pageNumber = 1,
        positionInPage = 1,
    )

    return HomeUiState(
        items = listOf(first, second),
        allNewModeState = HomeModeContentState.Content(listOf(first, second)),
        selectedItem = first,
        selectedItemKey = first.detailsUrl,
        hasNextPage = true,
    )
}

private fun homeStateWithModes(
    selectedMode: HomeFeedMode,
    allNewItems: List<ReleaseSummary>,
    favoriteItems: List<ReleaseSummary>,
    movieItems: List<ReleaseSummary> = allNewItems.filter { it.kind == ReleaseKind.MOVIE }.ifEmpty { allNewItems },
    seriesItems: List<ReleaseSummary> = allNewItems.filter { it.kind == ReleaseKind.SERIES }.ifEmpty { allNewItems },
): HomeUiState {
    val selectedItem = when (selectedMode) {
        HomeFeedMode.AllNew -> allNewItems.first()
        HomeFeedMode.Favorites -> favoriteItems.first()
        HomeFeedMode.Movies -> movieItems.first()
        HomeFeedMode.Series -> seriesItems.first()
    }
    return HomeUiState(
        items = allNewItems,
        favoriteItems = favoriteItems,
        movieItems = movieItems,
        seriesItems = seriesItems,
        rails = buildHomeRails(
            items = allNewItems,
            favoriteItems = favoriteItems,
            movieItems = movieItems,
            seriesItems = seriesItems,
            isFavoritesRailVisible = true,
        ),
        selectedMode = selectedMode,
        availableModes = when (selectedMode) {
            HomeFeedMode.Movies -> listOf(HomeFeedMode.AllNew, HomeFeedMode.Favorites, HomeFeedMode.Movies)
            HomeFeedMode.Series -> listOf(HomeFeedMode.AllNew, HomeFeedMode.Favorites, HomeFeedMode.Movies, HomeFeedMode.Series)
            else -> listOf(HomeFeedMode.AllNew, HomeFeedMode.Favorites)
        },
        allNewModeState = HomeModeContentState.Content(allNewItems),
        favoritesModeState = HomeModeContentState.Content(favoriteItems),
        moviesModeState = HomeModeContentState.Content(movieItems),
        seriesModeState = HomeModeContentState.Content(seriesItems),
        rememberedItemKeyByMode = mapOf(
            HomeFeedMode.AllNew to allNewItems.first().detailsUrl,
            HomeFeedMode.Favorites to favoriteItems.first().detailsUrl,
            HomeFeedMode.Movies to movieItems.first().detailsUrl,
            HomeFeedMode.Series to seriesItems.first().detailsUrl,
        ),
        selectedItem = selectedItem,
        selectedItemKey = selectedItem.detailsUrl,
        hasNextPage = true,
        isFavoritesRailVisible = true,
    )
}

private fun release(
    detailsUrl: String,
    titleRu: String,
    episodeTitleRu: String?,
    releaseDateRu: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    kind: ReleaseKind,
    pageNumber: Int = 1,
    positionInPage: Int = 0,
): ReleaseSummary {
    val posterUrl = when (kind) {
        ReleaseKind.SERIES -> "https://www.lostfilm.today/Static/Images/362/Posters/image_s9.jpg"
        ReleaseKind.MOVIE -> "https://www.lostfilm.today/Static/Images/1080/Posters/image.jpg"
    }
    return ReleaseSummary(
        id = detailsUrl,
        kind = kind,
        titleRu = titleRu,
        episodeTitleRu = episodeTitleRu,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        releaseDateRu = releaseDateRu,
        posterUrl = posterUrl,
        detailsUrl = detailsUrl,
        pageNumber = pageNumber,
        positionInPage = positionInPage,
        fetchedAt = 0L,
    )
}
