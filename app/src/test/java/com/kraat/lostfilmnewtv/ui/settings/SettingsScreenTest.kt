package com.kraat.lostfilmnewtv.ui.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsScreen_defaultsToQualitySection_showsRailSummaries_andSwitchesVisiblePanel() {
        composeRule.setContent {
            LostFilmTheme {
                SettingsScreen(
                    selectedQuality = PlaybackQualityPreference.Q720,
                    onQualitySelected = {},
                    selectedUpdateMode = UpdateCheckMode.MANUAL,
                    selectedChannelMode = AndroidTvChannelMode.UNWATCHED,
                    installedVersionText = "0.1.0",
                    latestVersionText = "0.2.0",
                    statusText = "Доступно обновление",
                    isCheckingForUpdates = false,
                    installUrl = "https://example.test/app.apk",
                    onUpdateModeSelected = {},
                    onChannelModeSelected = {},
                    onCheckForUpdatesClick = {},
                    onInstallUpdateClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings-section-quality").assertIsSelected()
        composeRule.onNodeWithTag("settings-section-quality-summary", useUnmergedTree = true).assertTextEquals("720p")
        composeRule.onNodeWithTag("settings-section-updates-summary", useUnmergedTree = true).assertTextEquals("Доступно обновление")
        composeRule.onNodeWithTag("settings-section-channel-summary", useUnmergedTree = true).assertTextEquals("Только непросмотренные")
        composeRule.onNodeWithTag("settings-overview-card").assertExists()
        composeRule.onNodeWithText("Качество видео").assertExists()
        composeRule.onNodeWithTag("settings-quality-720").assertExists()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("settings-update-mode-manual").fetchSemanticsNodes().size,
        )

        composeRule.onNodeWithTag("settings-section-updates")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithTag("settings-section-updates").assertIsSelected()
        composeRule.onNodeWithTag("settings-overview-card").assertExists()
        composeRule.onNodeWithTag("settings-update-mode-manual").assertExists()
        composeRule.onNodeWithText("Установлена версия: 0.1.0").assertExists()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("settings-quality-720").fetchSemanticsNodes().size,
        )

        composeRule.onNodeWithTag("settings-section-channel")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithTag("settings-section-channel").assertIsSelected()
        composeRule.onNodeWithTag("settings-overview-card").assertExists()
        composeRule.onNodeWithTag("settings-tv-channel-all-new").assertExists()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("settings-update-mode-manual").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun settingsScreen_updatesSection_showsStatusHeader_andInvokesCallbacks() {
        val selectedModes = mutableListOf<UpdateCheckMode>()
        var checkClicks = 0
        var installClicks = 0

        composeRule.setContent {
            LostFilmTheme {
                SettingsScreen(
                    selectedQuality = PlaybackQualityPreference.Q1080,
                    onQualitySelected = {},
                    selectedUpdateMode = UpdateCheckMode.MANUAL,
                    selectedChannelMode = AndroidTvChannelMode.ALL_NEW,
                    installedVersionText = "0.1.0",
                    latestVersionText = "0.2.0",
                    statusText = "Доступно обновление",
                    isCheckingForUpdates = false,
                    installUrl = "https://example.test/app.apk",
                    onUpdateModeSelected = { selectedModes += it },
                    onChannelModeSelected = {},
                    onCheckForUpdatesClick = { checkClicks += 1 },
                    onInstallUpdateClick = { installClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("settings-section-updates")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithTag("settings-section-updates").assertIsSelected()
        composeRule.onNodeWithTag("settings-overview-card").assertExists()
        composeRule.onNodeWithText("Установлена версия: 0.1.0").assertExists()
        composeRule.onNodeWithText("Последняя версия: 0.2.0").assertExists()
        assertEquals(
            2,
            composeRule.onAllNodesWithText("Доступно обновление").fetchSemanticsNodes().size,
        )
        composeRule.onNodeWithTag("settings-action-check-updates")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Скачать и установить")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("settings-update-mode-quiet")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(UpdateCheckMode.QUIET_CHECK), selectedModes)
        assertEquals(1, checkClicks)
        assertEquals(1, installClicks)
    }

    @Test
    fun settingsScreen_qualityAndChannelSections_stillInvokeCallbacks() {
        val selectedQualities = mutableListOf<PlaybackQualityPreference>()
        val selectedChannelModes = mutableListOf<AndroidTvChannelMode>()

        composeRule.setContent {
            LostFilmTheme {
                SettingsScreen(
                    selectedQuality = PlaybackQualityPreference.Q1080,
                    onQualitySelected = { selectedQualities += it },
                    selectedUpdateMode = UpdateCheckMode.MANUAL,
                    selectedChannelMode = AndroidTvChannelMode.ALL_NEW,
                    installedVersionText = "0.1.0",
                    latestVersionText = null,
                    statusText = null,
                    isCheckingForUpdates = false,
                    installUrl = null,
                    onUpdateModeSelected = {},
                    onChannelModeSelected = { selectedChannelModes += it },
                    onCheckForUpdatesClick = {},
                    onInstallUpdateClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings-section-quality")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("settings-quality-720")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithTag("settings-section-channel")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("settings-tv-channel-unwatched")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(PlaybackQualityPreference.Q720), selectedQualities)
        assertEquals(listOf(AndroidTvChannelMode.UNWATCHED), selectedChannelModes)
    }

    @Test
    fun settingsScreen_updatesSection_hidesInstallButtonWhenNoApkUrlIsAvailable() {
        composeRule.setContent {
            LostFilmTheme {
                SettingsScreen(
                    selectedQuality = PlaybackQualityPreference.Q1080,
                    onQualitySelected = {},
                    selectedUpdateMode = UpdateCheckMode.MANUAL,
                    selectedChannelMode = AndroidTvChannelMode.ALL_NEW,
                    installedVersionText = "0.1.0",
                    latestVersionText = null,
                    statusText = null,
                    isCheckingForUpdates = false,
                    installUrl = null,
                    onUpdateModeSelected = {},
                    onChannelModeSelected = {},
                    onCheckForUpdatesClick = {},
                    onInstallUpdateClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings-section-updates")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithTag("settings-section-updates").assertIsSelected()
        assertEquals(1, composeRule.onAllNodesWithTag("settings-action-check-updates").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Скачать и установить").fetchSemanticsNodes().size)
    }

    @Test
    fun settingsScreen_updatesSection_showsLoadingMessage_insideOverviewCard_andDisablesCheckButton() {
        composeRule.setContent {
            LostFilmTheme {
                SettingsScreen(
                    selectedQuality = PlaybackQualityPreference.Q1080,
                    onQualitySelected = {},
                    selectedUpdateMode = UpdateCheckMode.MANUAL,
                    selectedChannelMode = AndroidTvChannelMode.ALL_NEW,
                    installedVersionText = "0.1.0",
                    latestVersionText = null,
                    statusText = "Проверяем обновления...",
                    isCheckingForUpdates = true,
                    installUrl = null,
                    onUpdateModeSelected = {},
                    onChannelModeSelected = {},
                    onCheckForUpdatesClick = {},
                    onInstallUpdateClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings-section-updates")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithTag("settings-overview-card").assertExists()
        composeRule.onNodeWithText("Проверяем обновления...").assertExists()
        composeRule.onNodeWithTag("settings-action-check-updates").assertIsNotEnabled()
    }

    @Test
    fun settingsScreen_updatesSection_showsDownloadMessage_andDisablesInstallButton() {
        composeRule.setContent {
            LostFilmTheme {
                SettingsScreen(
                    selectedQuality = PlaybackQualityPreference.Q1080,
                    onQualitySelected = {},
                    selectedUpdateMode = UpdateCheckMode.MANUAL,
                    selectedChannelMode = AndroidTvChannelMode.ALL_NEW,
                    installedVersionText = "0.1.0",
                    latestVersionText = "0.2.0",
                    statusText = "Доступно обновление",
                    isCheckingForUpdates = false,
                    isDownloadingUpdate = true,
                    installUrl = "https://example.test/app.apk",
                    onUpdateModeSelected = {},
                    onChannelModeSelected = {},
                    onCheckForUpdatesClick = {},
                    onInstallUpdateClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings-section-updates")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithTag("settings-overview-card").assertExists()
        composeRule.onNodeWithText("Скачивание обновления…").assertExists()
        composeRule.onNodeWithTag("settings-install-update").assertIsNotEnabled()
        composeRule.onNodeWithText("Скачивание…").assertExists()
    }

    @Test
    fun settingsScreen_channelSection_onlyShowsChannelModes_noHomeFavorites() {
        composeRule.setContent {
            LostFilmTheme {
                SettingsScreen(
                    selectedQuality = PlaybackQualityPreference.Q1080,
                    onQualitySelected = {},
                    selectedUpdateMode = UpdateCheckMode.MANUAL,
                    selectedChannelMode = AndroidTvChannelMode.ALL_NEW,
                    isHomeFavoritesRailEnabled = false,
                    installedVersionText = "0.1.0",
                    latestVersionText = null,
                    statusText = null,
                    isCheckingForUpdates = false,
                    installUrl = null,
                    onUpdateModeSelected = {},
                    onChannelModeSelected = {},
                    onHomeFavoritesRailVisibilitySelected = {},
                    onCheckForUpdatesClick = {},
                    onInstallUpdateClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings-section-channel")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithTag("settings-tv-channel-all-new").assertExists()
        composeRule.onNodeWithTag("settings-tv-channel-unwatched").assertExists()
        composeRule.onNodeWithTag("settings-tv-channel-disabled").assertExists()

        assertEquals(
            0,
            composeRule.onAllNodesWithTag("settings-home-favorites-show").fetchSemanticsNodes().size,
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("settings-home-favorites-hide").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun settingsScreen_homeScreenSection_showsRailSummary_andToggles() {
        val selectedValues = mutableListOf<Boolean>()

        composeRule.setContent {
            LostFilmTheme {
                SettingsScreen(
                    selectedQuality = PlaybackQualityPreference.Q1080,
                    onQualitySelected = {},
                    selectedUpdateMode = UpdateCheckMode.MANUAL,
                    selectedChannelMode = AndroidTvChannelMode.ALL_NEW,
                    isHomeFavoritesRailEnabled = false,
                    installedVersionText = "0.1.0",
                    latestVersionText = null,
                    statusText = null,
                    isCheckingForUpdates = false,
                    installUrl = null,
                    onUpdateModeSelected = {},
                    onChannelModeSelected = {},
                    onHomeFavoritesRailVisibilitySelected = { selectedValues += it },
                    onCheckForUpdatesClick = {},
                    onInstallUpdateClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings-section-home-screen").assertExists()
        composeRule.onNodeWithTag("settings-section-home-screen-summary", useUnmergedTree = true)
            .assertTextEquals("Избранное: скрыто")

        composeRule.onNodeWithTag("settings-section-home-screen")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithText("Сейчас: вкладка Избранное скрыта").assertExists()
        composeRule.onNodeWithTag("settings-home-favorites-hide").assertIsSelected()
        composeRule.onNodeWithTag("settings-home-favorites-show")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(true), selectedValues)
    }

    @Test
    fun settingsScreen_homeScreenSection_showsShowState_whenEnabled() {
        composeRule.setContent {
            LostFilmTheme {
                SettingsScreen(
                    selectedQuality = PlaybackQualityPreference.Q1080,
                    onQualitySelected = {},
                    selectedUpdateMode = UpdateCheckMode.MANUAL,
                    selectedChannelMode = AndroidTvChannelMode.ALL_NEW,
                    isHomeFavoritesRailEnabled = true,
                    installedVersionText = "0.1.0",
                    latestVersionText = null,
                    statusText = null,
                    isCheckingForUpdates = false,
                    installUrl = null,
                    onUpdateModeSelected = {},
                    onChannelModeSelected = {},
                    onHomeFavoritesRailVisibilitySelected = {},
                    onCheckForUpdatesClick = {},
                    onInstallUpdateClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings-section-home-screen-summary", useUnmergedTree = true)
            .assertTextEquals("Избранное: показывается")

        composeRule.onNodeWithTag("settings-section-home-screen")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithTag("settings-home-favorites-show").assertIsSelected()
        composeRule.onNodeWithTag("settings-home-favorites-hide").assertExists()
    }

    @Test
    fun settingsScreen_aboutSection_showsVersionAndBuildInfo() {
        composeRule.setContent {
            LostFilmTheme {
                SettingsScreen(
                    selectedQuality = PlaybackQualityPreference.Q1080,
                    onQualitySelected = {},
                    selectedUpdateMode = UpdateCheckMode.MANUAL,
                    selectedChannelMode = AndroidTvChannelMode.ALL_NEW,
                    installedVersionText = "0.1.0",
                    latestVersionText = null,
                    statusText = null,
                    isCheckingForUpdates = false,
                    installUrl = null,
                    onUpdateModeSelected = {},
                    onChannelModeSelected = {},
                    onCheckForUpdatesClick = {},
                    onInstallUpdateClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings-section-about").assertExists()

        composeRule.onNodeWithTag("settings-section-about")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithText("Неофициальный клиент LostFilm для Android TV.").assertExists()
        composeRule.onNodeWithText("Версия: 0.1.0 (сборка 1)").assertExists()
    }

    @Test
    fun settingsScreen_qualitySection_hasFocusableControls_withCorrectTags() {
        composeRule.setContent {
            LostFilmTheme {
                SettingsScreen(
                    selectedQuality = PlaybackQualityPreference.Q1080,
                    onQualitySelected = {},
                    selectedUpdateMode = UpdateCheckMode.MANUAL,
                    selectedChannelMode = AndroidTvChannelMode.ALL_NEW,
                    installedVersionText = "0.1.0",
                    latestVersionText = null,
                    statusText = null,
                    isCheckingForUpdates = false,
                    installUrl = null,
                    onUpdateModeSelected = {},
                    onChannelModeSelected = {},
                    onCheckForUpdatesClick = {},
                    onInstallUpdateClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings-section-quality").assertIsSelected()
        composeRule.onNodeWithTag("settings-quality-1080").assertExists()
        composeRule.onNodeWithTag("settings-quality-720").assertExists()
        composeRule.onNodeWithTag("settings-quality-480").assertExists()
    }

    @Test
    fun settingsScreen_railItems_haveUpDownNavigationConfigured() {
        composeRule.setContent {
            LostFilmTheme {
                SettingsScreen(
                    selectedQuality = PlaybackQualityPreference.Q1080,
                    onQualitySelected = {},
                    selectedUpdateMode = UpdateCheckMode.MANUAL,
                    selectedChannelMode = AndroidTvChannelMode.ALL_NEW,
                    installedVersionText = "0.1.0",
                    latestVersionText = null,
                    statusText = null,
                    isCheckingForUpdates = false,
                    installUrl = null,
                    onUpdateModeSelected = {},
                    onChannelModeSelected = {},
                    onCheckForUpdatesClick = {},
                    onInstallUpdateClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings-section-quality").assertExists()
        composeRule.onNodeWithTag("settings-section-updates").assertExists()
        composeRule.onNodeWithTag("settings-section-channel").assertExists()
        composeRule.onNodeWithTag("settings-section-home-screen").assertExists()
        composeRule.onNodeWithTag("settings-section-account").assertExists()
        composeRule.onNodeWithTag("settings-section-about").assertExists()
    }

    @Test
    fun settingsScreen_contentButtons_haveLeftFocusRoutingToRail() {
        composeRule.setContent {
            LostFilmTheme {
                SettingsScreen(
                    selectedQuality = PlaybackQualityPreference.Q1080,
                    onQualitySelected = {},
                    selectedUpdateMode = UpdateCheckMode.MANUAL,
                    selectedChannelMode = AndroidTvChannelMode.ALL_NEW,
                    installedVersionText = "0.1.0",
                    latestVersionText = null,
                    statusText = null,
                    isCheckingForUpdates = false,
                    installUrl = null,
                    onUpdateModeSelected = {},
                    onChannelModeSelected = {},
                    onCheckForUpdatesClick = {},
                    onInstallUpdateClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings-section-quality")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithTag("settings-quality-1080").assertExists()
        composeRule.onNodeWithTag("settings-quality-720").assertExists()
        composeRule.onNodeWithTag("settings-quality-480").assertExists()
    }
}
