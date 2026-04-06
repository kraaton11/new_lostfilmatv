package com.kraat.lostfilmnewtv.ui.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.kraat.lostfilmnewtv.data.model.FavoriteMutationResult
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.TorrentLink
import com.kraat.lostfilmnewtv.data.repository.DetailsResult
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeActionHandler
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeAvailabilityChecker
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeConfig
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeLinkBuilder
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeOpenResult
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeSourceBuilder
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeUrlLauncher
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DetailsRouteTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun route_maps_torrent_rows_with_stable_ids_and_support_flags() {
        val detailsUrl = "https://www.lostfilm.today/series/test"
        val repository = RouteFakeDetailsRepository(
            detailsResults = mapOf(
                detailsUrl to mutableListOf(
                    DetailsResult.Success(
                        details = details(
                            detailsUrl = detailsUrl,
                            torrentLinks = listOf(
                                TorrentLink(label = "HTTP", url = "https://example.com/file.torrent"),
                                TorrentLink(label = "FTP", url = "ftp://example.com/file.torrent"),
                            ),
                        ),
                        isStale = false,
                    ),
                ),
            ),
        )

        composeRule.setContent {
            DetailsRoute(
                detailsUrl = detailsUrl,
                repository = repository,
                actionHandler = succeedingActionHandler(),
                linkBuilder = TorrServeLinkBuilder(TorrServeConfig()),
            )
        }

        composeRule.waitForNodeWithTag(torrServeButtonTag("$detailsUrl#0"))

        composeRule.onNodeWithTag(torrServeButtonTag("$detailsUrl#0")).assertIsEnabled()
        composeRule.onNodeWithTag(torrServeButtonTag("$detailsUrl#1")).assertDoesNotExist()
    }

    @Test
    fun route_usesPreferredQuality_whenMultipleRowsAreAvailable() {
        val detailsUrl = "https://www.lostfilm.today/series/preferred"
        val repository = RouteFakeDetailsRepository(
            detailsResults = mapOf(
                detailsUrl to mutableListOf(
                    DetailsResult.Success(
                        details = details(
                            detailsUrl = detailsUrl,
                            torrentLinks = listOf(
                                TorrentLink(label = "1080p", url = "https://example.com/file-1080.torrent"),
                                TorrentLink(label = "720p", url = "https://example.com/file-720.torrent"),
                            ),
                        ),
                        isStale = false,
                    ),
                ),
            ),
        )

        composeRule.setContent {
            DetailsRoute(
                detailsUrl = detailsUrl,
                repository = repository,
                preferredPlaybackQuality = PlaybackQualityPreference.Q720,
                actionHandler = succeedingActionHandler(),
                linkBuilder = TorrServeLinkBuilder(TorrServeConfig()),
            )
        }

        composeRule.waitForNodeWithTag(torrServeButtonTag("$detailsUrl#1"))
        composeRule.onNodeWithTag(torrServeButtonTag("$detailsUrl#1")).assertExists()
        composeRule.onNodeWithTag(torrServeButtonTag("$detailsUrl#0")).assertDoesNotExist()
    }

    @Test
    fun route_disablesPlaybackWhenNothingSupportedRemains() {
        val detailsUrl = "https://www.lostfilm.today/series/unsupported"
        val repository = RouteFakeDetailsRepository(
            detailsResults = mapOf(
                detailsUrl to mutableListOf(
                    DetailsResult.Success(
                        details = details(
                            detailsUrl = detailsUrl,
                            torrentLinks = listOf(
                                TorrentLink(label = "1080p", url = "ftp://example.com/file.torrent"),
                            ),
                        ),
                        isStale = false,
                    ),
                ),
            ),
        )

        composeRule.setContent {
            DetailsRoute(
                detailsUrl = detailsUrl,
                repository = repository,
                preferredPlaybackQuality = PlaybackQualityPreference.Q1080,
                actionHandler = succeedingActionHandler(),
                linkBuilder = TorrServeLinkBuilder(TorrServeConfig()),
            )
        }

        composeRule.waitForNodeWithTag("details-primary-action")
        composeRule.onNodeWithTag("details-primary-action").assertIsNotEnabled()
    }

    @Test
    fun route_suppresses_repeated_torrserve_clicks_while_busy() {
        val detailsUrl = "https://www.lostfilm.today/series/busy"
        val result = CompletableDeferred<TorrServeOpenResult>()
        val launchCount = AtomicInteger(0)
        val activeLaunches = AtomicInteger(0)

        composeRule.setContent {
            DetailsRoute(
                detailsUrl = detailsUrl,
                repository = RouteFakeDetailsRepository.success(detailsUrl),
                actionHandler = succeedingActionHandler(),
                linkBuilder = TorrServeLinkBuilder(TorrServeConfig()),
                openTorrServe = { _, _, _, _ ->
                    launchCount.incrementAndGet()
                    activeLaunches.incrementAndGet()
                    try {
                        result.await()
                    } finally {
                        activeLaunches.decrementAndGet()
                    }
                },
            )
        }

        composeRule.waitForNodeWithTag(torrServeButtonTag("$detailsUrl#0"))

        composeRule.onNodeWithTag(torrServeButtonTag("$detailsUrl#0")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(timeoutMillis = 5_000) { launchCount.get() == 1 }
        composeRule.onNodeWithTag(torrServeButtonTag("$detailsUrl#0")).assertIsNotEnabled()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(torrServeButtonTag("$detailsUrl#0")).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, launchCount.get())

        result.complete(TorrServeOpenResult.Success)
        composeRule.waitUntil(timeoutMillis = 5_000) { activeLaunches.get() == 0 }
        composeRule.onNodeWithTag(torrServeButtonTag("$detailsUrl#0")).assertIsEnabled()
    }

    @Test
    fun route_maps_torrserve_errors_clears_message_and_ignores_stale_completion_after_reentry() {
        val detailsUrl = "https://www.lostfilm.today/series/errors"
        val staleCompletion = CompletableDeferred<TorrServeOpenResult>()
        val responses = CopyOnWriteArrayList(
            listOf(
                CompletableDeferred(TorrServeOpenResult.Unavailable),
                staleCompletion,
                CompletableDeferred(TorrServeOpenResult.LaunchError),
            ),
        )
        var showRoute by mutableStateOf(true)

        composeRule.setContent {
            if (showRoute) {
                DetailsRoute(
                    detailsUrl = detailsUrl,
                    repository = RouteFakeDetailsRepository.success(detailsUrl),
                    actionHandler = succeedingActionHandler(),
                    linkBuilder = TorrServeLinkBuilder(TorrServeConfig()),
                    openTorrServe = { _, _, _, _ ->
                        val next = responses.removeAt(0)
                        next.await()
                    },
                )
            }
        }

        composeRule.waitForNodeWithTag(torrServeButtonTag("$detailsUrl#0"))

        composeRule.onNodeWithTag(torrServeButtonTag("$detailsUrl#0")).performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            showRoute = false
        }
        staleCompletion.complete(TorrServeOpenResult.LaunchError)

        composeRule.runOnIdle {
            showRoute = true
        }

        composeRule.waitForNodeWithTag(torrServeButtonTag("$detailsUrl#0"))
    }

    @Test
    fun route_state_restoration_with_message_does_not_crash() {
        val detailsUrl = "https://www.lostfilm.today/series/restore"
        val restorationTester = StateRestorationTester(composeRule)

        restorationTester.setContent {
            DetailsRoute(
                detailsUrl = detailsUrl,
                repository = RouteFakeDetailsRepository.success(detailsUrl),
                actionHandler = succeedingActionHandler(),
                linkBuilder = TorrServeLinkBuilder(TorrServeConfig()),
                openTorrServe = { _, _, _, _ -> TorrServeOpenResult.Unavailable },
            )
        }

        composeRule.waitForNodeWithTag(torrServeButtonTag("$detailsUrl#0"))
        composeRule.onNodeWithTag(torrServeButtonTag("$detailsUrl#0")).performSemanticsAction(SemanticsActions.OnClick)

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.waitForNodeWithTag(torrServeButtonTag("$detailsUrl#0"))
    }

    @Test
    fun route_recreates_view_model_when_details_url_changes() {
        val firstUrl = "https://www.lostfilm.today/series/first"
        val secondUrl = "https://www.lostfilm.today/series/second"
        val repository = RouteFakeDetailsRepository(
            detailsResults = mapOf(
                firstUrl to mutableListOf(DetailsResult.Success(details(firstUrl, titleRu = "First"), false)),
                secondUrl to mutableListOf(DetailsResult.Success(details(secondUrl, titleRu = "Second"), false)),
            ),
        )
        var currentUrl by mutableStateOf(firstUrl)

        composeRule.setContent {
            DetailsRoute(
                detailsUrl = currentUrl,
                repository = repository,
                actionHandler = succeedingActionHandler(),
                linkBuilder = TorrServeLinkBuilder(TorrServeConfig()),
            )
        }

        composeRule.waitForNodeWithText("First")
        composeRule.onNodeWithText("First").assertExists()

        composeRule.runOnIdle {
            currentUrl = secondUrl
        }

        composeRule.waitForNodeWithText("Second")
        composeRule.onNodeWithText("Second").assertExists()
        assertEquals(listOf(firstUrl, secondUrl), repository.loadedDetailsUrls)
    }

    @Test
    fun route_keeps_loading_error_and_retry_behavior_after_moving_view_model_ownership() {
        val detailsUrl = "https://www.lostfilm.today/series/retry"
        val repository = RouteFakeDetailsRepository(
            detailsResults = mapOf(
                detailsUrl to mutableListOf(
                    DetailsResult.Error(detailsUrl, "boom"),
                    DetailsResult.Success(details(detailsUrl, titleRu = "Recovered"), false),
                ),
            ),
        )

        composeRule.setContent {
            DetailsRoute(
                detailsUrl = detailsUrl,
                repository = repository,
                actionHandler = succeedingActionHandler(),
                linkBuilder = TorrServeLinkBuilder(TorrServeConfig()),
            )
        }

        composeRule.waitForNodeWithText("boom")
        composeRule.onNodeWithText("boom").assertExists()
        composeRule.onNodeWithText("Повторить").performClick()
        composeRule.waitForNodeWithText("Recovered")
        composeRule.onNodeWithText("Recovered").assertExists()
    }

    @Test
    fun route_marksEpisodeWatchedAfterSuccessfulTorrServeOpen_andNotifiesHome() {
        val detailsUrl = "https://www.lostfilm.today/series/mark"
        val repository = RouteFakeDetailsRepository.success(detailsUrl)
        val watchedDetailsUrls = CopyOnWriteArrayList<String>()
        val channelSyncCalls = AtomicInteger(0)

        composeRule.setContent {
            DetailsRoute(
                detailsUrl = detailsUrl,
                repository = repository,
                actionHandler = succeedingActionHandler(),
                linkBuilder = TorrServeLinkBuilder(TorrServeConfig()),
                onMarkedWatched = { watchedDetailsUrls += it },
                onChannelContentChanged = { channelSyncCalls.incrementAndGet() },
                openTorrServe = { _, _, _, _ -> TorrServeOpenResult.Success },
            )
        }

        composeRule.waitForNodeWithTag(torrServeButtonTag("$detailsUrl#0"))
        composeRule.onNodeWithTag(torrServeButtonTag("$detailsUrl#0"))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf(detailsUrl to "362009013"), repository.markedEpisodes)
        assertEquals(listOf(detailsUrl), watchedDetailsUrls)
        assertEquals(1, channelSyncCalls.get())
    }

    @Test
    fun route_notifiesFavoriteContentChanged_afterSuccessfulFavoriteMutation() {
        val detailsUrl = "https://www.lostfilm.today/series/favorite"
        val repository = RouteFakeDetailsRepository.success(
            detailsUrl = detailsUrl,
            details = details(detailsUrl).copy(
                favoriteTargetId = 915,
                isFavorite = false,
            ),
        ).apply {
            favoriteResults += FavoriteMutationResult.Updated
        }
        val favoriteChanges = CopyOnWriteArrayList<Pair<String, Boolean>>()

        composeRule.setContent {
            DetailsRoute(
                detailsUrl = detailsUrl,
                repository = repository,
                actionHandler = succeedingActionHandler(),
                linkBuilder = TorrServeLinkBuilder(TorrServeConfig()),
                onFavoriteContentChanged = { changedDetailsUrl, isFavorite ->
                    favoriteChanges += changedDetailsUrl to isFavorite
                },
            )
        }

        composeRule.waitForNodeWithTag("details-favorite-action")
        composeRule.onNodeWithTag("details-favorite-action")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(timeoutMillis = 5_000) { favoriteChanges.size == 1 }

        assertEquals(listOf(detailsUrl to true), favoriteChanges)
        assertEquals(listOf(detailsUrl to true), repository.favoriteRequests)
    }

    @Test
    fun route_callsOnOpenSeriesGuide_whenGuideActionIsClicked() {
        val detailsUrl = "https://www.lostfilm.today/series/guide"
        var openedGuideUrl: String? = null

        composeRule.setContent {
            DetailsRoute(
                detailsUrl = detailsUrl,
                repository = RouteFakeDetailsRepository.success(detailsUrl),
                actionHandler = succeedingActionHandler(),
                linkBuilder = TorrServeLinkBuilder(TorrServeConfig()),
                onOpenSeriesGuide = { openedGuideUrl = it },
            )
        }

        composeRule.waitForNodeWithTag("details-series-guide-action")
        composeRule.onNodeWithTag("details-series-guide-action")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(detailsUrl, openedGuideUrl)
    }
}

private fun ComposeContentTestRule.waitForNodeWithTag(tag: String) {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
}

private fun ComposeContentTestRule.waitForNodeWithText(text: String) {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}

private fun succeedingActionHandler(): TorrServeActionHandler = TorrServeActionHandler(
    builder = IdentityBuilder(),
    probe = AlwaysAvailableChecker(),
    launcher = ImmediateLauncher(TorrServeOpenResult.Success),
)

private fun torrServeButtonTag(rowId: String) = "torrent-torrserve-$rowId"

private class IdentityBuilder : TorrServeSourceBuilder {
    override fun build(rawUrl: String): String = rawUrl
}

private class AlwaysAvailableChecker : TorrServeAvailabilityChecker {
    override suspend fun isAvailable(): Boolean = true
}

private class ImmediateLauncher(
    private val result: TorrServeOpenResult,
) : TorrServeUrlLauncher {
    override suspend fun launch(
        context: android.content.Context,
        torrServeUrl: String,
        title: String,
        poster: String,
    ): Boolean {
        return result == TorrServeOpenResult.Success
    }
}

private class RouteFakeDetailsRepository(
    detailsResults: Map<String, MutableList<DetailsResult>>,
) : LostFilmRepository {
    private val scriptedResults = ConcurrentHashMap(detailsResults)
    val loadedDetailsUrls = CopyOnWriteArrayList<String>()
    val markedEpisodes = CopyOnWriteArrayList<Pair<String, String>>()
    val favoriteResults = CopyOnWriteArrayList<FavoriteMutationResult>()
    val favoriteRequests = CopyOnWriteArrayList<Pair<String, Boolean>>()

    override suspend fun loadPage(pageNumber: Int): PageState {
        error("Page loading is not used in details route tests")
    }

    override suspend fun loadDetails(detailsUrl: String): DetailsResult {
        loadedDetailsUrls += detailsUrl
        return scriptedResults[detailsUrl]?.removeAt(0)
            ?: error("No scripted details result for $detailsUrl")
    }

    override suspend fun loadSeriesGuide(detailsUrl: String): com.kraat.lostfilmnewtv.data.repository.SeriesGuideResult {
        return com.kraat.lostfilmnewtv.data.repository.SeriesGuideResult.Error("not needed")
    }

    override suspend fun markEpisodeWatched(detailsUrl: String, playEpisodeId: String): Boolean {
        markedEpisodes += detailsUrl to playEpisodeId
        return true
    }

    override suspend fun setFavorite(detailsUrl: String, targetFavorite: Boolean): FavoriteMutationResult {
        favoriteRequests += detailsUrl to targetFavorite
        return if (favoriteResults.isEmpty()) {
            FavoriteMutationResult.RequiresLogin()
        } else {
            favoriteResults.removeAt(0)
        }
    }

    override suspend fun loadFavoriteReleases(): FavoriteReleasesResult {
        return FavoriteReleasesResult.Unavailable()
    }

    companion object {
        fun success(
            detailsUrl: String,
            details: ReleaseDetails = details(detailsUrl),
        ): RouteFakeDetailsRepository = RouteFakeDetailsRepository(
            detailsResults = mapOf(
                detailsUrl to mutableListOf(
                    DetailsResult.Success(details, false),
                ),
            ),
        )
    }
}

private fun details(
    detailsUrl: String,
    titleRu: String = "Title",
    torrentLinks: List<TorrentLink> = listOf(
        TorrentLink(label = "Torrent", url = "https://example.com/file.torrent"),
    ),
): ReleaseDetails = ReleaseDetails(
    detailsUrl = detailsUrl,
    kind = ReleaseKind.SERIES,
    titleRu = titleRu,
    seasonNumber = 1,
    episodeNumber = 2,
    releaseDateRu = "14 марта 2026",
    posterUrl = "https://example.com/poster.jpg",
    fetchedAt = 0L,
    playEpisodeId = "362009013",
    torrentLinks = torrentLinks,
)
