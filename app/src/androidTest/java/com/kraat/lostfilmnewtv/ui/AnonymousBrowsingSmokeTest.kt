package com.kraat.lostfilmnewtv.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import com.kraat.lostfilmnewtv.MainActivity
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.LostFilmSearchItem
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.repository.DetailsResult
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.data.repository.SearchResultsResult
import com.kraat.lostfilmnewtv.di.TestFakeRepository
import com.kraat.lostfilmnewtv.ui.home.posterTag
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Smoke-тест сквозного флоу анонимного пользователя через реальный граф навигации.
 *
 * Зависимости подменяются через @TestInstallIn (TestDataModule, TestNetworkModule)
 * — реального HTTP-трафика нет. Настройка фейка происходит через @Inject TestFakeRepository
 * прямо в тесте.
 */
@HiltAndroidTest
class AnonymousBrowsingSmokeTest {

    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @get:Rule
    val ruleChain = RuleChain.outerRule(hiltRule).around(composeRule)

    // Hilt инжектирует тот же синглтон TestFakeRepository, что и в граф приложения
    @Inject
    lateinit var fakeRepository: LostFilmRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        // Настраиваем фейк через cast — TestDataModule предоставляет TestFakeRepository
        (fakeRepository as TestFakeRepository).apply {
            pageState = PageState.Content(
                pageNumber = 1,
                items = listOf(SMOKE_SUMMARY),
                hasNextPage = false,
                isStale = false,
            )
            detailsResult = DetailsResult.Success(details = SMOKE_DETAILS, isStale = false)
            searchResult = SearchResultsResult.Success(
                query = "dragon",
                items = listOf(
                    LostFilmSearchItem(
                        titleRu = "Путь дракона",
                        titleEn = "The Way of the Dragon",
                        subtitle = "Год выхода: 1972 • Жанр: Боевик",
                        posterUrl = null,
                        targetUrl = "https://www.lostfilm.today/movies/The_Way_of_the_Dragon",
                        kind = ReleaseKind.MOVIE,
                    ),
                ),
            )
            favoriteReleasesResult = FavoriteReleasesResult.Unavailable()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun anonymousHome_launchesThroughRealAppWiring() {
        waitForFocusedPoster()

        composeRule.onNodeWithText("Новинки").assertIsDisplayed()
        composeRule.onNodeWithTag(posterTag(SMOKE_SUMMARY.detailsUrl)).assertIsDisplayed()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun anonymousDetails_opensFromHomeThroughRealNavGraph() {
        waitForFocusedPoster()

        composeRule.onNodeWithTag(posterTag(SMOKE_SUMMARY.detailsUrl)).performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }

        waitForText("Smoke Series Details")
        assertTrue(composeRule.onAllNodesWithText("Smoke Series Details").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Pilot").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("Сезон 1").assertIsDisplayed()
        composeRule.onNodeWithText("Серия 1").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("TorrServe").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("details-primary-action").assertIsEnabled()
        composeRule.onNodeWithText("Войти в LostFilm").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("details-back").fetchSemanticsNodes().isEmpty())

        scenario?.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForFocusedPoster()
    }

    @Test
    fun anonymousSearch_opensFromHomeThroughRealNavGraph() {
        waitForFocusedPoster()

        composeRule.onNodeWithTag("home-action-search").performClick()

        composeRule.onNodeWithTag("search-query-input").assertIsDisplayed()
        composeRule.onNodeWithTag("search-query-input").performTextInput("dragon")
        composeRule.onNodeWithTag("search-submit").performClick()

        waitForText("Путь дракона")
        composeRule.onNodeWithText("The Way of the Dragon").assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForFocusedPoster() {
        val tag = posterTag(SMOKE_SUMMARY.detailsUrl)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().any {
                SemanticsProperties.Focused in it.config && it.config[SemanticsProperties.Focused]
            }
        }
    }
}

private val SMOKE_SUMMARY = ReleaseSummary(
    id = "https://example.com/series/smoke-series/season_1/episode_1/",
    kind = ReleaseKind.SERIES,
    titleRu = "Smoke Series",
    episodeTitleRu = "Pilot",
    seasonNumber = 1,
    episodeNumber = 1,
    releaseDateRu = "14.03.2026",
    posterUrl = "https://example.com/posters/smoke-series.jpg",
    detailsUrl = "https://example.com/series/smoke-series/season_1/episode_1/",
    pageNumber = 1,
    positionInPage = 0,
    fetchedAt = 0L,
)

private val SMOKE_DETAILS = ReleaseDetails(
    detailsUrl = SMOKE_SUMMARY.detailsUrl,
    kind = ReleaseKind.SERIES,
    titleRu = "Smoke Series Details",
    seasonNumber = 1,
    episodeNumber = 1,
    releaseDateRu = "14 марта 2026",
    posterUrl = "https://example.com/posters/smoke-series-details.jpg",
    fetchedAt = 0L,
    episodeTitleRu = "Pilot",
)
