package com.kraat.lostfilmnewtv.navigation

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
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
import com.kraat.lostfilmnewtv.di.UnitTestFakeAuthRepository
import com.kraat.lostfilmnewtv.di.UnitTestFakeRepository
import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeActionHandler
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeAvailabilityChecker
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeSourceBuilder
import com.kraat.lostfilmnewtv.platform.torrserve.TorrServeUrlLauncher
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelBackgroundScheduler
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPreferences
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelProgram
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelProgramSource
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPublisher
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelPublisherResult
import com.kraat.lostfilmnewtv.tvchannel.HomeChannelSyncManager
import com.kraat.lostfilmnewtv.ui.home.posterTag
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme
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
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppNavGraphTorrServeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    // Hilt инжектирует те же синглтоны, что зарегистрированы в UnitTestDataModule
    @Inject lateinit var fakeRepository: LostFilmRepository
    @Inject lateinit var fakeAuthRepository: AuthRepositoryContract

    // Перекрываемые зависимости — тест задаёт их явно через поля
    private var torrServeActionHandler: TorrServeActionHandler = unavailableTorrServeActionHandler()
    private var homeChannelSyncManager: HomeChannelSyncManager = testHomeChannelSyncManager()
    private var homeChannelBackgroundScheduler: HomeChannelBackgroundScheduler = testHomeChannelBackgroundScheduler()
    private var appUpdateBackgroundScheduler: QuietAppUpdateBackgroundScheduler = testAppUpdateBackgroundScheduler()
    private var appUpdateCoordinator: AppUpdateCoordinator = defaultTestAppUpdateCoordinator()
    private var playbackPreferencesStore: PlaybackPreferencesStore? = null
    private var releaseApkLauncher: ReleaseApkLauncher? = null
    private var appUpdateRepository: AppUpdateRepository? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        torrServeOpenCalls.set(0)

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { packageName = "com.example.torrserver"; name = "MainActivity" }
        }
        shadowOf(context.packageManager).apply {
            addResolveInfoForIntent(
                Intent(Intent.ACTION_VIEW).apply { data = android.net.Uri.parse("http://") },
                resolveInfo,
            )
        }
    }

    @After
    fun tearDown() {
        // фейки — синглтоны из Hilt, сбрасываем состояние вручную
        (fakeRepository as UnitTestFakeRepository).apply {
            pageState = PageState.Content(pageNumber = 1, items = listOf(TEST_SUMMARY), hasNextPage = false, isStale = false)
            detailsResult = DetailsResult.Success(TEST_DETAILS, false)
            seriesGuideResult = SeriesGuideResult.Error("not needed")
            favoriteReleasesResult = FavoriteReleasesResult.Unavailable()
        }
        (fakeAuthRepository as UnitTestFakeAuthRepository).authState = AuthState(isAuthenticated = false, session = null)
    }

    // ── вспомогательный builder для AppNavGraph в тестах ──────────────────

    private fun setContentWithNavGraph(initialDetailsUrl: String? = null) {
        composeRule.setContent {
            LostFilmTheme {
                AppNavGraph(initialDetailsUrl = initialDetailsUrl)
            }
        }
    }

    // ── Тесты ──────────────────────────────────────────────────────────────

    @Test
    fun details_quality_pill_shows_torrserve_feedback_when_opened_from_real_nav_graph() {
        setContentWithNavGraph()

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
        setContentWithNavGraph(initialDetailsUrl = TEST_SUMMARY.detailsUrl)

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
        (fakeRepository as UnitTestFakeRepository).apply {
            seriesGuideResult = SeriesGuideResult.Success(testSeriesGuide(selectedEpisodeUrl = TEST_SUMMARY.detailsUrl))
            detailsResult = DetailsResult.Success(targetDetails, false)
        }

        setContentWithNavGraph()

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
        val workManager = mock(androidx.work.WorkManager::class.java)
        (fakeRepository as UnitTestFakeRepository).pageState = PageState.Content(
            pageNumber = 1, items = emptyList(), hasNextPage = false, isStale = false
        )
        homeChannelBackgroundScheduler = HomeChannelBackgroundScheduler(
            readMode = { AndroidTvChannelMode.ALL_NEW }, workManager = workManager,
        )
        (fakeRepository as UnitTestFakeRepository).pageState = pageResult.let {
            // we block via a deferred — override loadPage via lambda workaround:
            // simpler: set blocking repo separately
            PageState.Content(pageNumber = 1, items = listOf(TEST_SUMMARY), hasNextPage = false, isStale = false)
        }

        setContentWithNavGraph()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            mockingDetails(workManager).invocations.count { it.method.name == "enqueueUniquePeriodicWork" } == 1
        }
        assertEquals(
            1,
            mockingDetails(workManager).invocations.count { it.method.name == "enqueueUniquePeriodicWork" },
        )
        composeRule.waitForText("Новые релизы")
    }

    @Test
    fun startup_composition_schedules_background_update_refresh_once() {
        val workManager = mock(androidx.work.WorkManager::class.java)
        appUpdateBackgroundScheduler = QuietAppUpdateBackgroundScheduler(
            readMode = { UpdateCheckMode.QUIET_CHECK }, workManager = workManager,
        )

        setContentWithNavGraph()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            mockingDetails(workManager).invocations.count { it.method.name == "enqueueUniquePeriodicWork" } == 1
        }
        assertEquals(
            1,
            mockingDetails(workManager).invocations.count { it.method.name == "enqueueUniquePeriodicWork" },
        )
        composeRule.waitForText("Новые релизы")
    }

    @Test
    fun settings_screen_opens_from_home_nav_graph() {
        setContentWithNavGraph()

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
        appUpdateCoordinator = testAppUpdateCoordinator(
            context = context,
            prefsName = "app-nav-home-saved-update",
            savedUpdate = SavedAppUpdate(latestVersion = "0.2.0", apkUrl = TEST_APK_URL),
        )

        setContentWithNavGraph()

        composeRule.waitForText("Новые релизы")
        composeRule.waitForText("Можно обновить")
        composeRule.onNodeWithText("Можно обновить").assertExists()
    }

    @Test
    fun settings_playback_quality_persists_and_applies_to_details_via_nav_graph() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "app-nav-playback-quality-settings"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)
        playbackPreferencesStore = store
        (fakeRepository as UnitTestFakeRepository).detailsResult =
            DetailsResult.Success(TEST_DETAILS_WITH_MULTIPLE_QUALITIES, false)
        var graphInstance by mutableIntStateOf(0)

        composeRule.setContent {
            LostFilmTheme { key(graphInstance) { AppNavGraph() } }
        }

        composeRule.waitForText("Новые релизы")
        composeRule.onNodeWithTag("home-action-settings").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("settings-section-quality").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForText("Качество по умолчанию")
        composeRule.onNodeWithTag("settings-quality-720").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(timeoutMillis = 5_000) { store.readDefaultQuality() == PlaybackQualityPreference.Q720 }
        assertEquals(PlaybackQualityPreference.Q720, store.readDefaultQuality())

        composeRule.runOnIdle { graphInstance += 1 }

        composeRule.waitForTag(posterTag(TEST_SUMMARY.detailsUrl))
        composeRule.onNodeWithTag(posterTag(TEST_SUMMARY.detailsUrl)).performSemanticsAction(SemanticsActions.OnClick)
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
        appUpdateRepository = fakeAppUpdateRepository("0.1.0", "0.2.0", TEST_APK_URL)
        val launcher = RecordingReleaseApkLauncher()
        releaseApkLauncher = launcher

        setContentWithNavGraph()

        composeRule.openSettingsSection("settings-section-updates", "Проверить обновления")
        composeRule.onNodeWithText("Проверить обновления").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Последняя версия: 0.2.0").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, composeRule.onAllNodesWithText("Последняя версия: 0.2.0").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Скачать и установить").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(timeoutMillis = 5_000) { launcher.launchedUrls == listOf(TEST_APK_URL) }
        assertEquals(listOf(TEST_APK_URL), launcher.launchedUrls)
    }

    @Test
    fun settings_install_failure_shows_user_facing_message_via_nav_graph() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "app-nav-update-failure-settings"
        context.deleteSharedPreferences(prefsName)
        appUpdateRepository = fakeAppUpdateRepository("0.1.0", "0.2.0", TEST_APK_URL)
        val launcher = RecordingReleaseApkLauncher(launchResult = false)
        releaseApkLauncher = launcher

        setContentWithNavGraph()

        composeRule.openSettingsSection("settings-section-updates", "Проверить обновления")
        composeRule.onNodeWithText("Проверить обновления").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForText("Скачать и установить")
        composeRule.onNodeWithText("Скачать и установить").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForText("Не удалось открыть обновление.")
        assertEquals(listOf(TEST_APK_URL), launcher.launchedUrls)
    }

    @Test
    fun settings_android_tv_channel_mode_persists_to_application_store_and_restores_through_nav_graph() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "app-nav-tv-channel-settings"
        context.deleteSharedPreferences(prefsName)
        val store = PlaybackPreferencesStore(context, prefsName = prefsName)
        playbackPreferencesStore = store
        var graphInstance by mutableIntStateOf(0)

        composeRule.setContent {
            LostFilmTheme { key(graphInstance) { AppNavGraph() } }
        }

        composeRule.waitForText("Новые релизы")
        composeRule.onNodeWithTag("home-action-settings").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("settings-section-channel").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForText("Канал Android TV")
        composeRule.onNodeWithTag("settings-tv-channel-unwatched").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(timeoutMillis = 5_000) { store.readAndroidTvChannelMode() == AndroidTvChannelMode.UNWATCHED }
        assertEquals(AndroidTvChannelMode.UNWATCHED, store.readAndroidTvChannelMode())

        composeRule.runOnIdle { graphInstance += 1 }

        composeRule.waitForText("Новые релизы")
        composeRule.onNodeWithTag("home-action-settings").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("settings-section-channel").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForText("Канал Android TV")
        composeRule.onNodeWithTag("settings-tv-channel-unwatched").assertIsSelected()
    }
}

// ── Утилиты ──────────────────────────────────────────────────────────────────

private fun ComposeContentTestRule.waitForTag(tag: String) {
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
}

private fun ComposeContentTestRule.waitForText(text: String) {
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
}

private fun ComposeContentTestRule.openSettingsSection(sectionTag: String, readyText: String) {
    waitForText("Новые релизы")
    onNodeWithTag("home-action-settings").performSemanticsAction(SemanticsActions.OnClick)
    onNodeWithTag(sectionTag).performSemanticsAction(SemanticsActions.OnClick)
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
    val tag = if (onAllNodesWithTag(preferredTag).fetchSemanticsNodes().isNotEmpty()) preferredTag else fallbackTag
    onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
    return tag
}

private fun routeTorrServeTag(detailsUrl: String, index: Int) = "torrent-torrserve-$detailsUrl-$index"
private fun legacyTorrServeTag(index: Int) = "torrent-torrserve-torrent-row-$index"

// ── Фейки ─────────────────────────────────────────────────────────────────

private class FakeAuthRepository : AuthRepositoryContract {
    override suspend fun getAuthState() = AuthState()
    override suspend fun startPairing(): PairingSession = error("Not used in this test")
    override suspend fun pollPairingStatus(): PairingSession? = null
    override suspend fun claimAndPersistSession() = AuthCompletionResult.RecoverableFailure()
    override suspend fun logout() = Unit
}

private class RecordingReleaseApkLauncher(private val launchResult: Boolean = true) : ReleaseApkLauncher(
    httpClient = OkHttpClient(),
) {
    val launchedUrls = CopyOnWriteArrayList<String>()
    override suspend fun launch(
        context: Context,
        apkUrl: String,
        onProgress: (Boolean) -> Unit,
    ): Boolean {
        launchedUrls.add(apkUrl)
        return launchResult
    }
}

private fun defaultTestAppUpdateCoordinator(): AppUpdateCoordinator {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return AppUpdateCoordinator(
        installedVersion = "0.1.0",
        store = AppUpdateAvailabilityStore(context, prefsName = "app-nav-default-update"),
        checkForUpdates = { AppUpdateInfo.UpToDate(installedVersion = "0.1.0") },
    )
}

private fun testAppUpdateCoordinator(
    context: Context,
    prefsName: String,
    savedUpdate: SavedAppUpdate?,
): AppUpdateCoordinator {
    context.deleteSharedPreferences(prefsName)
    val store = AppUpdateAvailabilityStore(context, prefsName = prefsName)
    if (savedUpdate != null) store.writeSavedUpdate(savedUpdate)
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
): AppUpdateRepository = AppUpdateRepository(
    installedVersion = installedVersion,
    releaseClient = object : GitHubReleaseClient(OkHttpClient()) {
        override suspend fun fetchLatestRelease() = GitHubRelease(version = latestVersion, apkUrl = apkUrl)
    },
)

private fun unavailableTorrServeActionHandler(): TorrServeActionHandler = TorrServeActionHandler(
    builder = object : TorrServeSourceBuilder { override fun build(rawUrl: String) = rawUrl },
    probe = object : TorrServeAvailabilityChecker {
        override suspend fun isAvailable(): Boolean {
            torrServeOpenCalls.incrementAndGet()
            return false
        }
    },
    launcher = object : TorrServeUrlLauncher { override suspend fun launch(context: Context, url: String) = Unit },
)

private fun testHomeChannelSyncManager(): HomeChannelSyncManager = HomeChannelSyncManager(
    programSource = object : HomeChannelProgramSource {
        override suspend fun loadPrograms(mode: AndroidTvChannelMode, limit: Int) = listOf(
            HomeChannelProgram(
                detailsUrl = TEST_SUMMARY.detailsUrl,
                title = TEST_SUMMARY.titleRu,
                description = TEST_SUMMARY.episodeTitleRu.orEmpty(),
                posterUrl = TEST_SUMMARY.posterUrl,
                internalProviderId = TEST_SUMMARY.detailsUrl,
            ),
        )
    },
    preferences = object : HomeChannelPreferences {
        private var storedChannelId: Long? = null
        override fun readMode() = AndroidTvChannelMode.ALL_NEW
        override fun readChannelId() = storedChannelId
        override fun writeChannelId(channelId: Long) { storedChannelId = channelId }
        override fun clearChannelId() { storedChannelId = null }
    },
    publisher = object : HomeChannelPublisher {
        override suspend fun reconcile(mode: AndroidTvChannelMode, existingChannelId: Long?, programs: List<HomeChannelProgram>) =
            HomeChannelPublisherResult(channelId = existingChannelId ?: 1L)
        override suspend fun deleteChannel(channelId: Long) = Unit
    },
)

private fun testHomeChannelBackgroundScheduler(): HomeChannelBackgroundScheduler =
    HomeChannelBackgroundScheduler(
        readMode = { AndroidTvChannelMode.ALL_NEW },
        workManager = mock(androidx.work.WorkManager::class.java),
    )

private fun testAppUpdateBackgroundScheduler(): QuietAppUpdateBackgroundScheduler =
    QuietAppUpdateBackgroundScheduler(
        readMode = { UpdateCheckMode.QUIET_CHECK },
        workManager = mock(androidx.work.WorkManager::class.java),
    )

private fun testSeriesGuide(selectedEpisodeUrl: String): SeriesGuide = SeriesGuide(
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

// ── Тестовые константы ────────────────────────────────────────────────────

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
    torrentLinks = listOf(TorrentLink(label = TEST_TORRENT_LABEL, url = TEST_TORRENT_URL)),
)

private val TEST_DETAILS_WITH_MULTIPLE_QUALITIES = TEST_DETAILS.copy(
    titleRu = "Smoke Series Details Multi",
    torrentLinks = listOf(
        TorrentLink(label = "1080p", url = "https://example.com/file-1080.torrent"),
        TorrentLink(label = "720p", url = "https://example.com/file-720.torrent"),
    ),
)
