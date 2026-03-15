package com.kraat.lostfilmnewtv.ui

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kraat.lostfilmnewtv.MainActivity
import com.kraat.lostfilmnewtv.LostFilmDebugHooks
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.repository.DetailsResult
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.ui.home.posterTag
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

class AnonymousBrowsingSmokeTest {
    private val repositoryOverrideRule = object : ExternalResource() {
        override fun before() {
            LostFilmDebugHooks.installRepositoryOverride(FakeAnonymousBrowsingRepository())
        }

        override fun after() {
            LostFilmDebugHooks.clearRepositoryOverride()
        }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: TestRule = RuleChain
        .outerRule(repositoryOverrideRule)
        .around(composeRule)

    @Test
    fun anonymousHome_launchesThroughRealAppWiring() {
        waitForText("Новые релизы")
        waitForFocusedPoster()

        composeRule.onNodeWithText("Новые релизы").assertExists()
        composeRule.onNodeWithTag(posterTag(SMOKE_SUMMARY.detailsUrl)).assertIsFocused()
    }

    @Test
    fun anonymousDetails_opensFromHomeThroughRealNavGraph() {
        waitForFocusedPoster()

        composeRule.onNodeWithTag(posterTag(SMOKE_SUMMARY.detailsUrl)).performClick()

        waitForText("Smoke Series Details")
        composeRule.onNodeWithText("Smoke Series Details").assertExists()
        composeRule.onNodeWithText("14 марта 2026").assertExists()

        composeRule.onNodeWithText("Назад").performClick()
        waitForText("Новые релизы")
        waitForFocusedPoster()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForFocusedPoster() {
        val tag = posterTag(SMOKE_SUMMARY.detailsUrl)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(tag)
                .fetchSemanticsNodes()
                .any { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Focused) == true }
        }
    }
}

private class FakeAnonymousBrowsingRepository : LostFilmRepository {
    override suspend fun loadPage(pageNumber: Int): PageState {
        return PageState.Content(
            pageNumber = 1,
            items = listOf(SMOKE_SUMMARY),
            hasNextPage = false,
            isStale = false,
        )
    }

    override suspend fun loadDetails(detailsUrl: String): DetailsResult {
        if (detailsUrl != SMOKE_SUMMARY.detailsUrl) {
            return DetailsResult.Error(
                detailsUrl = detailsUrl,
                message = "Unexpected details URL: $detailsUrl",
            )
        }

        return DetailsResult.Success(
            details = SMOKE_DETAILS,
            isStale = false,
        )
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
)
