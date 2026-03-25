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
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = seededState(),
                    isAuthenticated = false,
                    appVersionText = "0.1.0",
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
        composeRule.onNodeWithText("0.1.0").assertExists()
    }

    @Test
    fun homeScreen_bottomStage_showsSelectedReleaseContext_withoutReleaseDate() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(state = seededState())
            }
        }

        composeRule.onNodeWithTag("home-bottom-stage").assertExists()
        composeRule.onNodeWithText("9-1-1").assertExists()
        composeRule.onNodeWithText("Маменькин сынок").assertExists()
        composeRule.onNodeWithText("14.03.2026").assertDoesNotExist()
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

    @Test
    fun homeScreen_serviceInfo_staysAboveBottomEdge() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = seededState(),
                    appVersionText = "v2026.03.23",
                    savedAppUpdate = SavedAppUpdate(
                        latestVersion = "0.2.0",
                        apkUrl = "https://example.test/app.apk",
                    ),
                )
            }
        }

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val stageBounds = composeRule.onNodeWithTag("home-bottom-stage").fetchSemanticsNode().boundsInRoot
        val versionBounds = composeRule.onNodeWithText("v2026.03.23").fetchSemanticsNode().boundsInRoot

        assertTrue(stageBounds.bottom < rootBounds.bottom - 24f)
        assertTrue(versionBounds.bottom < rootBounds.bottom - 16f)
    }

    @Test
    @Config(qualifiers = "w1366dp-h768dp-land")
    fun homeScreen_updateAction_staysHorizontalInTvViewport_andKeepsBottomStageVisible() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = seededState(),
                    isAuthenticated = true,
                    appVersionText = "v2026.03.23",
                    savedAppUpdate = SavedAppUpdate(
                        latestVersion = "0.2.0",
                        apkUrl = "https://example.test/app.apk",
                    ),
                )
            }
        }

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val updateBounds = composeRule.onNodeWithTag("home-action-update").fetchSemanticsNode().boundsInRoot
        val stageBounds = composeRule.onNodeWithTag("home-bottom-stage").fetchSemanticsNode().boundsInRoot

        assertTrue("updateBounds=$updateBounds", updateBounds.width > 140f)
        assertTrue("updateBounds=$updateBounds", updateBounds.height < 120f)
        assertTrue("rootBounds=$rootBounds stageBounds=$stageBounds", stageBounds.bottom < rootBounds.bottom - 16f)
    }

    @Test
    @Config(qualifiers = "w1366dp-h768dp-land")
    fun homeScreen_longEpisodeTitle_wrapsReadable_andHidesReleaseDate() {
        val longEpisodeTitle = "Когда слишком много правды, она перестает помещаться в одну строку и начинает бороться за место на экране"

        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = seededState(episodeTitleRu = longEpisodeTitle),
                    appVersionText = "v2026.03.23",
                    savedAppUpdate = SavedAppUpdate(
                        latestVersion = "0.2.0",
                        apkUrl = "https://example.test/app.apk",
                    ),
                )
            }
        }

        val episodeBounds = composeRule.onNodeWithText(longEpisodeTitle).fetchSemanticsNode().boundsInRoot
        val textLayouts = mutableListOf<TextLayoutResult>()

        composeRule.onNodeWithText(longEpisodeTitle).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            action(textLayouts)
        }

        composeRule.onNodeWithText("14.03.2026").assertDoesNotExist()
        assertTrue("episodeBounds=$episodeBounds", episodeBounds.height > 30f)
        assertFalse("textLayouts=$textLayouts", textLayouts.first().hasVisualOverflow)
    }

    @Test
    @Config(qualifiers = "w1366dp-h768dp-land")
    fun homeScreen_shortEpisodeTitle_keepsComfortableBottomInsetInsideStage() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    state = seededState(episodeTitleRu = "17:00"),
                    appVersionText = "v2026.03.23",
                    savedAppUpdate = SavedAppUpdate(
                        latestVersion = "0.2.0",
                        apkUrl = "https://example.test/app.apk",
                    ),
                )
            }
        }

        val stageBounds = composeRule.onNodeWithTag("home-bottom-stage").fetchSemanticsNode().boundsInRoot
        val episodeBounds = composeRule.onNodeWithText("17:00").fetchSemanticsNode().boundsInRoot
        val textLayouts = mutableListOf<TextLayoutResult>()

        composeRule.onNodeWithText("17:00").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            action(textLayouts)
        }

        assertTrue("stageBounds=$stageBounds episodeBounds=$episodeBounds", stageBounds.bottom - episodeBounds.bottom > 56f)
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
    fun homeScreen_switchingFromFavoritesToAllNew_focusesFirstAllNewPoster() {
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

        composeRule.onNodeWithTag("home-mode-tab-all-new")
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
    fun homeScreen_dpadLeftFromFavoritesMode_movesFocusToAllNewPoster() {
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

        composeRule.onNodeWithTag("home-mode-tab-favorites")
            .performSemanticsAction(SemanticsActions.RequestFocus)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag("home-mode-tab-favorites")
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag("home-mode-tab-favorites")
            .performKeyInput {
                keyDown(Key.DirectionLeft)
                keyUp(Key.DirectionLeft)
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
): HomeUiState {
    val selectedItem = when (selectedMode) {
        HomeFeedMode.AllNew -> allNewItems.first()
        HomeFeedMode.Favorites -> favoriteItems.first()
    }
    return HomeUiState(
        items = allNewItems,
        favoriteItems = favoriteItems,
        rails = buildHomeRails(
            items = allNewItems,
            favoriteItems = favoriteItems,
            isFavoritesRailVisible = true,
        ),
        selectedMode = selectedMode,
        availableModes = listOf(HomeFeedMode.AllNew, HomeFeedMode.Favorites),
        allNewModeState = HomeModeContentState.Content(allNewItems),
        favoritesModeState = HomeModeContentState.Content(favoriteItems),
        rememberedItemKeyByMode = mapOf(
            HomeFeedMode.AllNew to allNewItems.first().detailsUrl,
            HomeFeedMode.Favorites to favoriteItems.first().detailsUrl,
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
