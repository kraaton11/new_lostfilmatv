package com.kraat.lostfilmnewtv.ui.guide

import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import com.kraat.lostfilmnewtv.data.model.FavoriteMutationResult
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.SeriesGuide
import com.kraat.lostfilmnewtv.data.model.SeriesGuideEpisode
import com.kraat.lostfilmnewtv.data.model.SeriesGuideSeason
import com.kraat.lostfilmnewtv.data.repository.DetailsResult
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.data.repository.SeriesGuideResult
import com.kraat.lostfilmnewtv.navigation.AppDestination
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SeriesGuideRouteTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun route_showsGuideContent_andMarksCurrentEpisodeSelected() {
        val currentEpisodeUrl = "https://www.lostfilm.today/series/Ted/season_2/episode_8/"

        composeRule.setContent {
            SeriesGuideRoute(
                onOpenDetails = {},
                viewModel = routeViewModel(
                    currentEpisodeUrl,
                    FakeSeriesGuideRepository.success(currentEpisodeUrl),
                ),
            )
        }

        composeRule.waitForText("Третий лишний")
        composeRule.onNodeWithTag("series-guide-row-$currentEpisodeUrl").assertIsSelected()
    }

    @Test
    fun route_focusesCurrentEpisodeRow_afterFirstComposition() {
        val currentEpisodeUrl = "https://www.lostfilm.today/series/Ted/season_2/episode_8/"

        composeRule.setContent {
            SeriesGuideRoute(
                onOpenDetails = {},
                viewModel = routeViewModel(
                    currentEpisodeUrl,
                    FakeSeriesGuideRepository.success(currentEpisodeUrl),
                ),
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            val node = composeRule.onAllNodesWithTag("series-guide-row-$currentEpisodeUrl")
                .fetchSemanticsNodes()
                .singleOrNull()
                ?: return@waitUntil false
            SemanticsProperties.Focused in node.config && node.config[SemanticsProperties.Focused]
        }

        composeRule.onNodeWithTag("series-guide-row-$currentEpisodeUrl").assertIsFocused()
    }

    @Test
    fun route_showsRetryState_whenGuideLoadFails() {
        val currentEpisodeUrl = "https://www.lostfilm.today/series/Ted/season_2/episode_8/"

        composeRule.setContent {
            SeriesGuideRoute(
                onOpenDetails = {},
                viewModel = routeViewModel(
                    currentEpisodeUrl,
                    FakeSeriesGuideRepository.error(currentEpisodeUrl, "offline"),
                ),
            )
        }

        composeRule.waitForText("offline")
        composeRule.onNodeWithText("Повторить").assertExists()
    }

    @Test
    fun route_callsOnOpenDetails_whenEpisodeRowIsClicked() {
        val currentEpisodeUrl = "https://www.lostfilm.today/series/Ted/season_2/episode_8/"
        val targetEpisodeUrl = "https://www.lostfilm.today/series/Ted/season_2/episode_7/"
        var openedUrl: String? = null

        composeRule.setContent {
            SeriesGuideRoute(
                onOpenDetails = { openedUrl = it },
                viewModel = routeViewModel(
                    currentEpisodeUrl,
                    FakeSeriesGuideRepository.success(currentEpisodeUrl),
                ),
            )
        }

        composeRule.waitForTag("series-guide-row-$targetEpisodeUrl")
        composeRule.onNodeWithTag("series-guide-row-$targetEpisodeUrl")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(targetEpisodeUrl, openedUrl)
    }
}

private fun ComposeContentTestRule.waitForTag(tag: String) {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
}

private fun ComposeContentTestRule.waitForText(text: String) {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}

private fun routeViewModel(
    detailsUrl: String,
    repository: LostFilmRepository,
): SeriesGuideViewModel = SeriesGuideViewModel(
    repository = repository,
    savedStateHandle = SavedStateHandle(
        mapOf(AppDestination.SeriesGuide.detailsUrlArg to detailsUrl),
    ),
    ioDispatcher = Dispatchers.Unconfined,
)

private class FakeSeriesGuideRepository(
    private val scriptedGuideResults: ConcurrentHashMap<String, MutableList<SeriesGuideResult>>,
) : LostFilmRepository {
    val loadedGuideUrls = CopyOnWriteArrayList<String>()

    override suspend fun loadPage(pageNumber: Int): PageState {
        error("Page loading is not used in guide route tests")
    }

    override suspend fun loadDetails(detailsUrl: String): DetailsResult {
        error("Details loading is not used in guide route tests")
    }

    override suspend fun loadSeriesGuide(detailsUrl: String): SeriesGuideResult {
        loadedGuideUrls += detailsUrl
        return scriptedGuideResults[detailsUrl]?.removeAt(0)
            ?: error("No scripted guide result for $detailsUrl")
    }

    override suspend fun markEpisodeWatched(detailsUrl: String, playEpisodeId: String): Boolean = false

    override suspend fun setFavorite(detailsUrl: String, targetFavorite: Boolean): FavoriteMutationResult {
        return FavoriteMutationResult.RequiresLogin()
    }

    override suspend fun loadFavoriteReleases(): FavoriteReleasesResult {
        return FavoriteReleasesResult.Unavailable()
    }

    companion object {
        fun success(selectedEpisodeUrl: String): FakeSeriesGuideRepository {
            return FakeSeriesGuideRepository(
                scriptedGuideResults = ConcurrentHashMap(
                    mapOf(
                        selectedEpisodeUrl to mutableListOf(
                            SeriesGuideResult.Success(testGuide(selectedEpisodeUrl)),
                        ),
                    ),
                ),
            )
        }

        fun error(detailsUrl: String, message: String): FakeSeriesGuideRepository {
            return FakeSeriesGuideRepository(
                scriptedGuideResults = ConcurrentHashMap(
                    mapOf(
                        detailsUrl to mutableListOf(
                            SeriesGuideResult.Error(message),
                        ),
                    ),
                ),
            )
        }
    }
}

private fun testGuide(selectedEpisodeUrl: String): SeriesGuide {
    return SeriesGuide(
        seriesTitleRu = "Третий лишний",
        posterUrl = "https://www.lostfilm.today/Static/Images/810/Posters/image.jpg",
        selectedEpisodeDetailsUrl = selectedEpisodeUrl,
        seasons = listOf(
            SeriesGuideSeason(
                seasonNumber = 2,
                episodes = listOf(
                    SeriesGuideEpisode(
                        detailsUrl = "https://www.lostfilm.today/series/Ted/season_2/episode_8/",
                        episodeId = "810002008",
                        seasonNumber = 2,
                        episodeNumber = 8,
                        episodeTitleRu = "Левые новости",
                        releaseDateRu = "24.03.2026",
                        isWatched = false,
                    ),
                    SeriesGuideEpisode(
                        detailsUrl = "https://www.lostfilm.today/series/Ted/season_2/episode_7/",
                        episodeId = "810002007",
                        seasonNumber = 2,
                        episodeNumber = 7,
                        episodeTitleRu = "Сьюзен мотает срок",
                        releaseDateRu = "21.03.2026",
                        isWatched = true,
                    ),
                ),
            ),
        ),
    )
}
