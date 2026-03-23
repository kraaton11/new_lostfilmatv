package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.updates.SavedAppUpdate
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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

        composeRule.onNodeWithTag("home-action-auth").assertExists()
        composeRule.onNodeWithTag("home-action-settings").assertExists()
        composeRule.onNodeWithTag("home-action-update").assertExists()
        composeRule.onNodeWithTag("home-bottom-stage").assertExists()
        composeRule.onNodeWithText("0.1.0").assertExists()
    }

    @Test
    fun homeScreen_bottomStage_showsSelectedReleaseContext() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(state = seededState())
            }
        }

        composeRule.onNodeWithTag("home-bottom-stage").assertExists()
        composeRule.onNodeWithText("9-1-1").assertExists()
        composeRule.onNodeWithText("Маменькин сынок").assertExists()
        composeRule.onNodeWithText("14.03.2026").assertExists()
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
}

private const val firstDetailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/"
private const val secondDetailsUrl = "https://www.lostfilm.today/movies/Irreversible"

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
