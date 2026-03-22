package com.kraat.lostfilmnewtv.ui.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
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
    fun settingsScreen_defaultsToUpdatesSection_andSwitchesVisiblePanel() {
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
                    onUpdateModeSelected = {},
                    onChannelModeSelected = {},
                    onCheckForUpdatesClick = {},
                    onInstallUpdateClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings-section-updates").assertIsSelected()
        composeRule.onNodeWithText("Установлена версия: 0.1.0").assertExists()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("settings-quality-1080").fetchSemanticsNodes().size,
        )

        composeRule.onNodeWithTag("settings-section-quality")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithTag("settings-section-quality").assertIsSelected()
        composeRule.onNodeWithTag("settings-quality-1080").assertExists()
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Установлена версия: 0.1.0").fetchSemanticsNodes().size,
        )

        composeRule.onNodeWithTag("settings-section-channel")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithTag("settings-section-channel").assertIsSelected()
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

        composeRule.onNodeWithTag("settings-section-updates").assertIsSelected()
        composeRule.onNodeWithText("Установлена версия: 0.1.0").assertExists()
        composeRule.onNodeWithText("Последняя версия: 0.2.0").assertExists()
        composeRule.onNodeWithText("Доступно обновление").assertExists()
        composeRule.onNodeWithText("Проверить обновления")
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

        composeRule.onNodeWithTag("settings-section-updates").assertIsSelected()
        assertEquals(1, composeRule.onAllNodesWithText("Проверить обновления").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Скачать и установить").fetchSemanticsNodes().size)
    }

    @Test
    fun settingsScreen_updatesSection_showsLoadingMessage_andDisablesCheckButton() {
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

        composeRule.onNodeWithText("Проверяем обновления...").assertExists()
        composeRule.onNodeWithText("Проверяем...").assertIsNotEnabled()
    }
}
