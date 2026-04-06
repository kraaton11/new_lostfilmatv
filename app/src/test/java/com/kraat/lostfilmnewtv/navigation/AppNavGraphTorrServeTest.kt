package com.kraat.lostfilmnewtv.navigation

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import com.kraat.lostfilmnewtv.LostFilmApplication
import com.kraat.lostfilmnewtv.data.auth.AuthCompletionResult
import com.kraat.lostfilmnewtv.data.auth.AuthRepositoryContract
import com.kraat.lostfilmnewtv.data.model.AuthState
import com.kraat.lostfilmnewtv.data.model.FavoriteMutationResult
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.PageState
import com.kraat.lostfilmnewtv.data.model.PairingSession
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.model.SeriesGuide
import com.kraat.lostfilmnewtv.data.model.SeriesGuideEpisode
import com.kraat.lostfilmnewtv.data.model.SeriesGuideSeason
import com.kraat.lostfilmnewtv.data.model.TorrentLink
import com.kraat.lostfilmnewtv.data.repository.DetailsResult
import com.kraat.lostfilmnewtv.data.repository.LostFilmRepository
import com.kraat.lostfilmnewtv.data.repository.SeriesGuideResult
import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeActionHandler
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeAvailabilityChecker
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeSourceBuilder
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeUrlLauncher
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundRefreshRunner
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundScheduler
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPreferences
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelProgram
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelProgramSource
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPublisher
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPublisherResult
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelSyncManager
import com.kraat.lostfilmnewtv.updates.AppUpdateAvailabilityStore
import com.kraat.lostfilmnewtv.updates.AppUpdateBackgroundScheduler as QuietAppUpdateBackgroundScheduler
import com.kraat.lostfilmnewtv.updates.AppUpdateCoordinator
import com.kraat.lostfilmnewtv.updates.AppUpdateInfo
import com.kraat.lostfilmnewtv.updates.AppUpdateRepository
import com.kraat.lostfilmnewtv.updates.GitHubRelease
import com.kraat.lostfilmnewtv.updates.GitHubReleaseClient
import com.kraat.lostfilmnewtv.updates.ReleaseApkLauncher
import com.kraat.lostfilmnewtv.updates.SavedAppUpdate
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode
import com.kraat.lostfilmnewtv.ui.home.posterTag
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import androidx.work.WorkManager
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestLostFilmApplication::class)
class AppNavGraphTorrServeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        TestLostFilmApplication.repositoryOverride = FakeAppNavGraphRepository()
        TestLostFilmApplication.authRepositoryOverride = FakeAuthRepository()
        TestLostFilmApplication.appUpdateRepositoryOverride = null
        TestLostFilmApplication.appUpdateCoordinatorOverride = null
        TestLostFilmApplication.releaseApkLauncherOverride = null
        TestLostFilmApplication.homeChannelSyncManagerOverride = testHomeChannelSyncManager()
        TestLostFilmApplication.homeChannelBackgroundSchedulerOverride = testHomeChannelBackgroundScheduler()
        TestLostFilmApplication.appUpdateBackgroundSchedulerOverride = testAppUpdateBackgroundScheduler()
        TestLostFilmApplication.homeChannelBackgroundRefreshRunnerOverride = null
        torrServeOpenCalls.set(0)
        TestLostFilmApplication.torrServeActionHandlerOverride = unavailableTorrServeActionHandler()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.browser"
                name = "BrowserActivity"
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_VIEW, Uri.parse(TEST_TORRENT_URL)),
            resolveInfo,
        )
    }

    @After
    fun tearDown() {
        TestLostFilmApplication.repositoryOverride = null
        TestLostFilmApplication.authRepositoryOverride = null
        TestLostFilmApplication.torrServeActionHandlerOverride = null
        TestLostFilmApplication.playbackPreferencesStoreOverride = null
        TestLostFilmApplication.appUpdateRepositoryOverride = null
        TestLostFilmApplication.appUpdateCoordinatorOverride = null
        TestLostFilmApplication.releaseApkLauncherOverride = null
        TestLostFilmApplication.homeChannelSyncManagerOverride = null
        TestLostFilmApplication.homeChannelBackgroundSchedulerOverride = null
        TestLostFilmApplication.appUpdateBackgroundSchedulerOverride = null
        TestLostFilmApplication.homeChannelBackgroundRefreshRunnerOverride = null
    }

    @Test
    fun details_quality_pill_shows_torrserve_feedback_when_opened_from_real_nav_graph() {
        composeRule.setContent {
            LostFilmTheme {
                AppNavGraph()
            }
        }

        composeRule.waitForTag(posterTag(TEST_SUMMARY.detailsUrl))
        composeRule.onNodeWithTag(posterTag(TEST_SUMMARY.detailsUrl))
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitForText(TEST_DETAILS.titleRu)
        val clickedTag = composeRule.clickExistingTorrServeButton(
            preferredTag = routeTorrServeTag(TEST_SUMMARY.detailsUrl, 0),
            fallbackTag = legacyTorrServeTag(0),
        )
        assertEquals(routeTorrServeTag(TEST_SUMMARY.detailsUrl, 0), clickedTag)

        composeRule.waitUntil(timeoutMillis = 5_000) { torrServeOpenCalls.get() == 1 }
        assertEquals(1, torrServeOpenCalls.get())
    }

    @Test
    fun initialDetailsUrl_opens_details_without_home_click() {
        composeRule.setContent {
            LostFilmTheme {
                AppNavGraph(initialDetailsUrl = TEST_SUMMARY.detailsUrl)
            }
        }

        composeRule.waitForText(TEST_DETAILS.titleRu)
        assertEquals(1, composeRule.onAllNodesWithText(TEST_DETAILS.titleRu).fetchSemanticsNodes().size)
    }

    @Test
    fun detailsGuideAction_opensGuide_andGuideRowNavigatesToEpisodeDetails() {
        val targetEpisodeUrl = "https://www.lostfilm.today/series/Ted/season_2/episode_7/"
        val targetDetails = TEST_DETAILS.copy(
            detailsUrl = targetEpisodeUrl,
            titleRu = "Ted Episode 7 Details",
            seasonNumber = 2,
            episodeNumber = 7,
            episodeTitleRu = "Сьюзен мотает срок",
        )
        TestLostFilmApplication.repositoryOverride = FakeAppNavGraphRepository().apply {
            guideResult = SeriesGuideResult.Success(testSeriesGuide(selectedEpisodeUrl = TEST_SUMMARY.detailsUrl))
            detailsByUrl[targetEpisodeUrl] = DetailsResult.Success(targetDetails, false)
        }

        composeRule.setContent {
            LostFilmTheme {
                AppNavGraph()
            }
        }

        composeRule.waitForTag(posterTag(TEST_SUMMARY.detailsUrl))
        composeRule.onNodeWithTag(posterTag(TEST_SUMMARY.detailsUrl))
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitForTag("details-series-guide-action")
        composeRule.onNodeWithTag("details-series-guide-action")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitForText("Третий лишний")
        composeRule.onNodeWithTag("series-guide-row-$targetEpisodeUrl")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitForText(targetDetails.titleRu)
    }

    @Test
    fun startup_composition_triggers_single_channel_sync_before_home_content_finishes_loading() {
        val pageResult = CompletableDeferred<PageState>()
        val workManager = mock(WorkManager::class.java)
        TestLostFilmApplication.repositoryOverride = BlockingAppNavGraphRepository(pageResult)
        TestLostFilmApplication.homeChannelBackgroundSchedulerOverride = HomeChannelBackgroundScheduler(
            readMode = { AndroidTvChannelMode.ALL_NEW },
            workManager = workManager,
        )

        composeRule.setContent {
            LostFilmTheme {
                AppNavGraph()
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            mockingDetails(workManager).invocations.count {
                it.method.name == "enqueueUniquePeriodicWork"
            } == 1
        }

        pageResult.complete(
            PageState.Content(
                pageNumber = 1,
                items = listOf(TEST_SUMMARY),
                hasNextPage = false,
                isStale = false,
            ),
        )
        composeRule.waitForText("Новые релизы")
    }

    @Test
    fun startup_composition_schedules_background_channel_refresh_once() {
        val pageResult = CompletableDeferred<PageState>()
        val workManager = mock(WorkManager::class.java)
        TestLostFilmApplication.repositoryOverride = BlockingAppNavGraphRepository(pageResult)
        TestLostFilmApplication.homeChannelBackgroundSchedulerOverride = HomeChannelBackgroundScheduler(
            readMode = { AndroidTvChannelMode.ALL_NEW },
            workManager = workManager,
        )

        composeRule.setContent {
            LostFilmTheme {
                AppNavGraph()
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            mockingDetails(workManager).invocations.count {
                it.method.name == "enqueueUniquePeriodicWork"
            } == 1
        }
        assertEquals(
            1,
            mockingDetails(workManager).invocations.count {
                it.method.name == "enqueueUniquePeriodicWork"
            },
        )

        pageResult.complete(
            PageState.Content(
                pageNumber = 1,
                items = listOf(TEST_SUMMARY),
                hasNextPage = false,
                isStale = false,
            ),
        )
        composeRule.waitForText("Новые релизы")
    }

    @Test
    fun startup_composition_schedules_background_update_refresh_once() {
        val pageResult = CompletableDeferred<PageState>()
        val workManager = mock(WorkManager::class.java)
        TestLostFilmApplication.repositoryOverride = BlockingAppNavGraphRepository(pageResult)
        TestLostFilmApplication.appUpdateBackgroundSchedulerOverride = QuietAppUpdateBackgroundScheduler(
            readMode = { UpdateCheckMode.QUIET_CHECK },
            workManager = workManager,
        )

        composeRule.setContent {
            LostFilmTheme {
                AppNavGraph()
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            mockingDetails(workManager).invocations.count {
                it.method.name == "enqueueUniquePeriodicWork"
            } == 1
        }
        assertEquals(
            1,
            mockingDetails(workManager).invocations.count {
                it.method.name == "enqueueUniquePeriodicWork"
            },
        )

        pageResult.complete(
            PageState.Content(
                pageNumber = 1,
                items = listOf(TEST_SUMMARY),
                hasNextPage = false,
                isStale = false,
            ),
        )
        composeRule.waitForText("Новые релизы")
    }

    @Test
    fun settings_screen_opens_from_home_nav_graph() {
        composeRule.setContent {
            LostFilmTheme {
                AppNavGraph()
            }
        }

        composeRule.openSettingsSection(
            sectionTag = "settings-section-quality",
            readyText = "Качество по умолчанию",
        )

        composeRule.onNodeWithTag("settings-section-quality").assertIsSelected()
        composeRule.onNodeWithTag("settings-quality-1080").assertIsSelected()
    }

    @Test
    fun home_shows_update_text_in_service_panel_via_nav_graph() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TestLostFilmApplication.appUpdateCoordinatorOverride = testAppUpdateCoordinator(
            context = context,
            prefsName = "app-nav-home-saved-update",
            savedUpdate = SavedAppUpdate(
                latestVersion = "0.2.0",
                apkUrl = TEST_APK_URL,
            ),
        )

        composeRule.setContent {
            LostFilmTheme {
                AppNavGraph()
            }
        }

        composeRule.waitForText("Новые релизы")
        composeRule.waitForText("Можно обновить")
        composeRule.onNodeWithText("Можно обновить").assertExists()
    }

    // Note: home_install_failure test removed — "Обновить" button no longer on home screen.
    // Install flow is now handled through Settings screen only.

    @Test
    fun settings_playback_quality_persists_and_applies_to_details_via_nav_graph() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "app-nav-playback-quality-settings"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)
        TestLostFilmApplication.repositoryOverride = FakeAppNavGraphRepository(
            details = TEST_DETAILS_WITH_MULTIPLE_QUALITIES,
        )
        TestLostFilmApplication.playbackPreferencesStoreOverride = store
        var graphInstance by mutableIntStateOf(0)

        composeRule.setContent {
            LostFilmTheme {
                key(graphInstance) {
                    AppNavGraph()
                }
            }
        }

        composeRule.waitForText("Новые релизы")
        composeRule.onNodeWithTag("home-action-settings")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("settings-section-quality")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForText("Качество по умолчанию")
        composeRule.onNodeWithTag("settings-quality-720")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            store.readDefaultQuality() == PlaybackQualityPreference.Q720
        }
        assertEquals(PlaybackQualityPreference.Q720, store.readDefaultQuality())

        composeRule.runOnIdle {
            graphInstance += 1
        }

        composeRule.waitForTag(posterTag(TEST_SUMMARY.detailsUrl))
        composeRule.onNodeWithTag(posterTag(TEST_SUMMARY.detailsUrl))
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitForText(TEST_DETAILS_WITH_MULTIPLE_QUALITIES.titleRu)
        val clickedTag = composeRule.clickExistingTorrServeButton(
            preferredTag = routeTorrServeTag(TEST_SUMMARY.detailsUrl, 1),
            fallbackTag = legacyTorrServeTag(0),
        )

        assertEquals(routeTorrServeTag(TEST_SUMMARY.detailsUrl, 1), clickedTag)
    }

    @Test
    fun settings_check_for_updates_shows_update_data_and_launches_install_via_nav_graph() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "app-nav-update-settings"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)
        TestLostFilmApplication.playbackPreferencesStoreOverride = store
        TestLostFilmApplication.appUpdateRepositoryOverride = fakeAppUpdateRepository(
            installedVersion = "0.1.0",
            latestVersion = "0.2.0",
            apkUrl = TEST_APK_URL,
        )
        val launcher = RecordingReleaseApkLauncher()
        TestLostFilmApplication.releaseApkLauncherOverride = launcher

        composeRule.setContent {
            LostFilmTheme {
                AppNavGraph()
            }
        }

        composeRule.openSettingsSection(
            sectionTag = "settings-section-updates",
            readyText = "Проверить обновления",
        )
        composeRule.onNodeWithText("Проверить обновления")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Последняя версия: 0.2.0").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, composeRule.onAllNodesWithText("Последняя версия: 0.2.0").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("Скачать и установить").fetchSemanticsNodes().size)

        composeRule.onNodeWithText("Скачать и установить")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            launcher.launchedUrls == listOf(TEST_APK_URL)
        }
        assertEquals(listOf(TEST_APK_URL), launcher.launchedUrls)
    }

    @Test
    fun settings_install_failure_shows_user_facing_message_via_nav_graph() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "app-nav-update-failure-settings"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)
        TestLostFilmApplication.playbackPreferencesStoreOverride = store
        TestLostFilmApplication.appUpdateRepositoryOverride = fakeAppUpdateRepository(
            installedVersion = "0.1.0",
            latestVersion = "0.2.0",
            apkUrl = TEST_APK_URL,
        )
        val launcher = RecordingReleaseApkLauncher(launchResult = false)
        TestLostFilmApplication.releaseApkLauncherOverride = launcher

        composeRule.setContent {
            LostFilmTheme {
                AppNavGraph()
            }
        }

        composeRule.openSettingsSection(
            sectionTag = "settings-section-updates",
            readyText = "Проверить обновления",
        )
        composeRule.onNodeWithText("Проверить обновления")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForText("Скачать и установить")
        composeRule.onNodeWithText("Скачать и установить")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitForText("Не удалось открыть обновление.")
        assertEquals(listOf(TEST_APK_URL), launcher.launchedUrls)
    }

    @Test
    fun settings_update_mode_persists_to_application_store_and_restores_through_nav_graph() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "app-nav-playback-settings"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)
        TestLostFilmApplication.playbackPreferencesStoreOverride = store
        TestLostFilmApplication.appUpdateRepositoryOverride = fakeAppUpdateRepository(
            installedVersion = "0.1.0",
            latestVersion = "0.2.0",
            apkUrl = "https://example.test/app.apk",
        )
        var graphInstance by mutableIntStateOf(0)

        composeRule.setContent {
            LostFilmTheme {
                key(graphInstance) {
                    AppNavGraph()
                }
            }
        }

        composeRule.openSettingsSection(
            sectionTag = "settings-section-updates",
            readyText = "Проверить обновления",
        )
        composeRule.onNodeWithTag("settings-update-mode-quiet")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            store.readUpdateCheckMode() == UpdateCheckMode.QUIET_CHECK
        }
        assertEquals(UpdateCheckMode.QUIET_CHECK, store.readUpdateCheckMode())

        composeRule.runOnIdle {
            graphInstance += 1
        }

        composeRule.openSettingsSection(
            sectionTag = "settings-section-updates",
            readyText = "Проверить обновления",
        )
        composeRule.onNodeWithTag("settings-update-mode-quiet").assertIsSelected()
        composeRule.waitForText("Последняя версия: 0.2.0")
    }

    @Test
    fun settings_android_tv_channel_mode_persists_to_application_store_and_restores_through_nav_graph() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "app-nav-tv-channel-settings"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)
        TestLostFilmApplication.playbackPreferencesStoreOverride = store
        var graphInstance by mutableIntStateOf(0)

        composeRule.setContent {
            LostFilmTheme {
                key(graphInstance) {
                    AppNavGraph()
                }
            }
        }

        composeRule.waitForText("Новые релизы")
        composeRule.onNodeWithTag("home-action-settings")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("settings-section-channel")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForText("Канал Android TV")
        composeRule.onNodeWithTag("settings-tv-channel-unwatched")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            store.readAndroidTvChannelMode() == AndroidTvChannelMode.UNWATCHED
        }
        assertEquals(AndroidTvChannelMode.UNWATCHED, store.readAndroidTvChannelMode())

        composeRule.runOnIdle {
            graphInstance += 1
        }

        composeRule.waitForText("Новые релизы")
        composeRule.onNodeWithTag("home-action-settings")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("settings-section-channel")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForText("Канал Android TV")
        composeRule.onNodeWithTag("settings-tv-channel-unwatched").assertIsSelected()
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

private fun ComposeContentTestRule.openSettingsSection(
    sectionTag: String,
    readyText: String,
) {
    waitForText("Новые релизы")
    onNodeWithTag("home-action-settings")
        .performSemanticsAction(SemanticsActions.OnClick)
    onNodeWithTag(sectionTag)
        .performSemanticsAction(SemanticsActions.OnClick)
    waitForText(readyText)
}

private fun ComposeContentTestRule.clickExistingTorrServeButton(
    preferredTag: String,
    fallbackTag: String,
): String {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithTag(preferredTag).fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithTag(fallbackTag).fetchSemanticsNodes().isNotEmpty()
    }

    val tag = if (onAllNodesWithTag(preferredTag).fetchSemanticsNodes().isNotEmpty()) {
        preferredTag
    } else {
        fallbackTag
    }
    onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
    return tag
}

private class FakeAppNavGraphRepository(
    private val details: ReleaseDetails = TEST_DETAILS,
) : LostFilmRepository {
    val detailsByUrl = linkedMapOf<String, DetailsResult>(
        TEST_SUMMARY.detailsUrl to DetailsResult.Success(
            details = details,
            isStale = false,
        ),
    )
    var guideResult: SeriesGuideResult = SeriesGuideResult.Error("not needed")

    override suspend fun loadPage(pageNumber: Int): PageState {
        return PageState.Content(
            pageNumber = 1,
            items = listOf(TEST_SUMMARY),
            hasNextPage = false,
            isStale = false,
        )
    }

    override suspend fun loadDetails(detailsUrl: String): DetailsResult {
        return detailsByUrl[detailsUrl]
            ?: DetailsResult.Error(
                detailsUrl = detailsUrl,
                message = "Unexpected details URL: $detailsUrl",
            )
    }

    override suspend fun loadSeriesGuide(detailsUrl: String): SeriesGuideResult {
        return guideResult
    }

    override suspend fun markEpisodeWatched(detailsUrl: String, playEpisodeId: String): Boolean = false

    override suspend fun setFavorite(detailsUrl: String, targetFavorite: Boolean): FavoriteMutationResult {
        return FavoriteMutationResult.RequiresLogin()
    }

    override suspend fun loadFavoriteReleases(): FavoriteReleasesResult {
        return FavoriteReleasesResult.Unavailable()
    }
}

private class BlockingAppNavGraphRepository(
    private val firstPage: CompletableDeferred<PageState>,
) : LostFilmRepository {
    override suspend fun loadPage(pageNumber: Int): PageState = firstPage.await()

    override suspend fun loadDetails(detailsUrl: String): DetailsResult {
        error("Details loading is not used in blocking app nav graph test")
    }

    override suspend fun loadSeriesGuide(detailsUrl: String): com.kraat.lostfilmnewtv.data.repository.SeriesGuideResult {
        return com.kraat.lostfilmnewtv.data.repository.SeriesGuideResult.Error("not needed")
    }

    override suspend fun markEpisodeWatched(detailsUrl: String, playEpisodeId: String): Boolean = false

    override suspend fun setFavorite(detailsUrl: String, targetFavorite: Boolean): FavoriteMutationResult {
        return FavoriteMutationResult.RequiresLogin()
    }

    override suspend fun loadFavoriteReleases(): FavoriteReleasesResult {
        return FavoriteReleasesResult.Unavailable()
    }
}

private class RecordingAppNavHomeChannelPublisher : HomeChannelPublisher {
    val reconcileCalls = AtomicInteger(0)

    override suspend fun reconcile(
        mode: AndroidTvChannelMode,
        existingChannelId: Long?,
        programs: List<HomeChannelProgram>,
    ): HomeChannelPublisherResult {
        reconcileCalls.incrementAndGet()
        return HomeChannelPublisherResult(channelId = existingChannelId ?: 1L)
    }

    override suspend fun deleteChannel(channelId: Long) = Unit
}

private class FakeAuthRepository(
    private val authState: AuthState = AuthState(),
) : AuthRepositoryContract {
    override suspend fun getAuthState(): AuthState = authState

    override suspend fun startPairing(): PairingSession {
        error("Auth flow is not used in AppNavGraph torrserve regression test")
    }

    override suspend fun pollPairingStatus(): PairingSession? {
        error("Auth flow is not used in AppNavGraph torrserve regression test")
    }

    override suspend fun claimAndPersistSession(): AuthCompletionResult {
        error("Auth flow is not used in AppNavGraph torrserve regression test")
    }

    override suspend fun logout() = Unit
}

class TestLostFilmApplication : LostFilmApplication() {
    override val repository: LostFilmRepository
        get() = checkNotNull(repositoryOverride) {
            "repositoryOverride must be set before composing AppNavGraph in tests"
        }

    override val authRepository: AuthRepositoryContract
        get() = checkNotNull(authRepositoryOverride) {
            "authRepositoryOverride must be set before composing AppNavGraph in tests"
        }

    override val torrServeActionHandler: TorrServeActionHandler
        get() = checkNotNull(torrServeActionHandlerOverride) {
            "torrServeActionHandlerOverride must be set before composing AppNavGraph in tests"
        }

    override val playbackPreferencesStore: PlaybackPreferencesStore
        get() = playbackPreferencesStoreOverride ?: super.playbackPreferencesStore

    override val appUpdateRepository: AppUpdateRepository
        get() = appUpdateRepositoryOverride ?: super.appUpdateRepository

    override val appUpdateCoordinator: AppUpdateCoordinator
        get() = appUpdateCoordinatorOverride ?: super.appUpdateCoordinator

    override val releaseApkLauncher: ReleaseApkLauncher
        get() = releaseApkLauncherOverride ?: super.releaseApkLauncher

    override val homeChannelSyncManager: HomeChannelSyncManager
        get() = homeChannelSyncManagerOverride ?: super.homeChannelSyncManager

    override val homeChannelBackgroundScheduler: HomeChannelBackgroundScheduler
        get() = homeChannelBackgroundSchedulerOverride ?: super.homeChannelBackgroundScheduler

    override val appUpdateBackgroundScheduler: QuietAppUpdateBackgroundScheduler
        get() = appUpdateBackgroundSchedulerOverride ?: super.appUpdateBackgroundScheduler

    override val homeChannelBackgroundRefreshRunner: HomeChannelBackgroundRefreshRunner
        get() = homeChannelBackgroundRefreshRunnerOverride ?: super.homeChannelBackgroundRefreshRunner

    companion object {
        @Volatile
        var repositoryOverride: LostFilmRepository? = null

        @Volatile
        var authRepositoryOverride: AuthRepositoryContract? = null

        @Volatile
        var torrServeActionHandlerOverride: TorrServeActionHandler? = null

        @Volatile
        var playbackPreferencesStoreOverride: PlaybackPreferencesStore? = null

        @Volatile
        var appUpdateRepositoryOverride: AppUpdateRepository? = null

        @Volatile
        var appUpdateCoordinatorOverride: AppUpdateCoordinator? = null

        @Volatile
        var releaseApkLauncherOverride: ReleaseApkLauncher? = null

        @Volatile
        var homeChannelSyncManagerOverride: HomeChannelSyncManager? = null

        @Volatile
        var homeChannelBackgroundSchedulerOverride: HomeChannelBackgroundScheduler? = null

        @Volatile
        var appUpdateBackgroundSchedulerOverride: QuietAppUpdateBackgroundScheduler? = null

        @Volatile
        var homeChannelBackgroundRefreshRunnerOverride: HomeChannelBackgroundRefreshRunner? = null
    }
}

private fun testHomeChannelSyncManager(
    publisher: RecordingAppNavHomeChannelPublisher = RecordingAppNavHomeChannelPublisher(),
): HomeChannelSyncManager {
    return HomeChannelSyncManager(
        programSource = object : HomeChannelProgramSource {
            override suspend fun loadPrograms(
                mode: AndroidTvChannelMode,
                limit: Int,
            ): List<HomeChannelProgram> {
                return listOf(
                    HomeChannelProgram(
                        detailsUrl = TEST_SUMMARY.detailsUrl,
                        title = TEST_SUMMARY.titleRu,
                        description = TEST_SUMMARY.episodeTitleRu.orEmpty(),
                        posterUrl = TEST_SUMMARY.posterUrl,
                        internalProviderId = TEST_SUMMARY.detailsUrl,
                    ),
                )
            }
        },
        preferences = object : HomeChannelPreferences {
            private var storedChannelId: Long? = null

            override fun readMode(): AndroidTvChannelMode = AndroidTvChannelMode.ALL_NEW

            override fun readChannelId(): Long? = storedChannelId

            override fun writeChannelId(channelId: Long) {
                storedChannelId = channelId
            }

            override fun clearChannelId() {
                storedChannelId = null
            }
        },
        publisher = publisher,
    )
}

private fun testHomeChannelBackgroundScheduler(): HomeChannelBackgroundScheduler {
    return HomeChannelBackgroundScheduler(
        readMode = { AndroidTvChannelMode.ALL_NEW },
        workManager = mock(WorkManager::class.java),
    )
}

private fun testAppUpdateBackgroundScheduler(): QuietAppUpdateBackgroundScheduler {
    return QuietAppUpdateBackgroundScheduler(
        readMode = { UpdateCheckMode.QUIET_CHECK },
        workManager = mock(WorkManager::class.java),
    )
}

private fun testAppUpdateCoordinator(
    context: Context,
    prefsName: String,
    savedUpdate: SavedAppUpdate?,
): AppUpdateCoordinator {
    context.deleteSharedPreferences(prefsName)
    val store = AppUpdateAvailabilityStore(context, prefsName = prefsName)
    if (savedUpdate != null) {
        store.writeSavedUpdate(savedUpdate)
    }
    return AppUpdateCoordinator(
        installedVersion = "0.1.0",
        store = store,
        checkForUpdates = { AppUpdateInfo.UpToDate(installedVersion = "0.1.0") },
    )
}

private fun fakeAppUpdateRepository(
    installedVersion: String,
    latestVersion: String,
    apkUrl: String,
): AppUpdateRepository {
    return AppUpdateRepository(
        installedVersion = installedVersion,
        releaseClient = object : GitHubReleaseClient(OkHttpClient()) {
            override suspend fun fetchLatestRelease(): GitHubRelease {
                return GitHubRelease(
                    version = latestVersion,
                    apkUrl = apkUrl,
                )
            }
        },
    )
}

private fun unavailableTorrServeActionHandler(): TorrServeActionHandler = TorrServeActionHandler(
    builder = IdentityTorrServeBuilder(),
    probe = AlwaysUnavailableChecker(),
    launcher = NoOpLauncher(),
)

private class IdentityTorrServeBuilder : TorrServeSourceBuilder {
    override fun build(rawUrl: String): String = rawUrl
}

private class AlwaysUnavailableChecker : TorrServeAvailabilityChecker {
    override suspend fun isAvailable(): Boolean {
        torrServeOpenCalls.incrementAndGet()
        return false
    }
}

private class NoOpLauncher : TorrServeUrlLauncher {
    override suspend fun launch(context: android.content.Context, torrServeUrl: String, title: String, poster: String): Boolean = false
}

private class RecordingReleaseApkLauncher(
    private val launchResult: Boolean = true,
) : ReleaseApkLauncher(okhttp3.OkHttpClient()) {
    val launchedUrls = CopyOnWriteArrayList<String>()

    override suspend fun launch(
        context: Context,
        apkUrl: String,
        onDownloadingChange: (Boolean) -> Unit,
        onDownloadProgress: (Int) -> Unit,
    ): Boolean {
        launchedUrls += apkUrl
        onDownloadingChange(true)
        onDownloadProgress(100)
        onDownloadingChange(false)
        return launchResult
    }
}

private fun routeTorrServeTag(detailsUrl: String, index: Int): String {
    return "torrent-torrserve-$detailsUrl#$index"
}

private fun legacyTorrServeTag(index: Int): String {
    return "torrent-torrserve-torrent-row-$index"
}

private const val TEST_TORRENT_LABEL = "1080p"
private const val TEST_TORRENT_URL = "https://example.com/file.torrent"
private const val TEST_APK_URL = "https://example.test/releases/lostfilm-tv.apk"
private val torrServeOpenCalls = AtomicInteger(0)

private val TEST_SUMMARY = ReleaseSummary(
    id = "https://example.com/series/app-nav-graph/season_1/episode_1/",
    kind = ReleaseKind.SERIES,
    titleRu = "Smoke Series",
    episodeTitleRu = "Pilot",
    seasonNumber = 1,
    episodeNumber = 1,
    releaseDateRu = "14.03.2026",
    posterUrl = "https://example.com/posters/smoke-series.jpg",
    detailsUrl = "https://example.com/series/app-nav-graph/season_1/episode_1/",
    pageNumber = 1,
    positionInPage = 0,
    fetchedAt = 0L,
)

private val TEST_DETAILS = ReleaseDetails(
    detailsUrl = TEST_SUMMARY.detailsUrl,
    kind = ReleaseKind.SERIES,
    titleRu = "Smoke Series Details",
    seasonNumber = 1,
    episodeNumber = 1,
    releaseDateRu = "14 марта 2026",
    posterUrl = "https://example.com/posters/smoke-series-details.jpg",
    fetchedAt = 0L,
    torrentLinks = listOf(
        TorrentLink(
            label = TEST_TORRENT_LABEL,
            url = TEST_TORRENT_URL,
        ),
    ),
)

private val TEST_DETAILS_WITH_MULTIPLE_QUALITIES = TEST_DETAILS.copy(
    titleRu = "Smoke Series Details Multi",
    torrentLinks = listOf(
        TorrentLink(
            label = "1080p",
            url = "https://example.com/file-1080.torrent",
        ),
        TorrentLink(
            label = "720p",
            url = "https://example.com/file-720.torrent",
        ),
    ),
)

private fun testSeriesGuide(selectedEpisodeUrl: String): SeriesGuide {
    return SeriesGuide(
        seriesTitleRu = "Третий лишний",
        posterUrl = "https://www.lostfilm.today/Static/Images/810/Posters/image.jpg",
        selectedEpisodeDetailsUrl = selectedEpisodeUrl,
        seasons = listOf(
            SeriesGuideSeason(
                seasonNumber = 2,
                episodes = listOf(
                    SeriesGuideEpisode(
                        detailsUrl = TEST_SUMMARY.detailsUrl,
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
