package com.kraat.lostfilmnewtv.ui.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme
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
    fun settingsScreen_rendersUpdatesBlockAndInvokesCallbacks() {
        val clicked = mutableListOf<PlaybackQualityPreference>()
        val selectedModes = mutableListOf<UpdateCheckMode>()
        var checkClicks = 0
        var installClicks = 0

        composeRule.setContent {
            LostFilmTheme {
                SettingsScreen(
                    selectedQuality = PlaybackQualityPreference.Q1080,
                    onQualitySelected = { clicked += it },
                    selectedUpdateMode = UpdateCheckMode.MANUAL,
                    installedVersionText = "0.1.0",
                    latestVersionText = "0.2.0",
                    statusText = "Доступно обновление",
                    isCheckingForUpdates = false,
                    installUrl = "https://example.test/app.apk",
                    onUpdateModeSelected = { selectedModes += it },
                    onCheckForUpdatesClick = { checkClicks += 1 },
                    onInstallUpdateClick = { installClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("settings-quality-1080").assertIsSelected()
        composeRule.onNodeWithTag("settings-update-mode-manual").assertIsSelected()
        assertEquals(1, composeRule.onAllNodesWithText("Установлена версия: 0.1.0").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("Последняя версия: 0.2.0").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("Доступно обновление").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("Проверить обновления").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("Скачать и установить").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("settings-quality-720")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("settings-update-mode-quiet")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Проверить обновления")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Скачать и установить")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(PlaybackQualityPreference.Q720), clicked)
        assertEquals(listOf(UpdateCheckMode.QUIET_CHECK), selectedModes)
        assertEquals(1, checkClicks)
        assertEquals(1, installClicks)
    }

    @Test
    fun settingsScreen_hidesInstallButtonWhenNoApkUrlIsAvailable() {
        composeRule.setContent {
            LostFilmTheme {
                SettingsScreen(
                    selectedQuality = PlaybackQualityPreference.Q1080,
                    onQualitySelected = {},
                    selectedUpdateMode = UpdateCheckMode.MANUAL,
                    installedVersionText = "0.1.0",
                    latestVersionText = null,
                    statusText = null,
                    isCheckingForUpdates = false,
                    installUrl = null,
                    onUpdateModeSelected = {},
                    onCheckForUpdatesClick = {},
                    onInstallUpdateClick = {},
                )
            }
        }

        assertEquals(1, composeRule.onAllNodesWithText("Проверить обновления").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Скачать и установить").fetchSemanticsNodes().size)
    }

    @Test
    fun settingsScreen_showsLoadingMessage_andDisablesCheckButton() {
        composeRule.setContent {
            LostFilmTheme {
                SettingsScreen(
                    selectedQuality = PlaybackQualityPreference.Q1080,
                    onQualitySelected = {},
                    selectedUpdateMode = UpdateCheckMode.MANUAL,
                    installedVersionText = "0.1.0",
                    latestVersionText = null,
                    statusText = "Проверяем обновления...",
                    isCheckingForUpdates = true,
                    installUrl = null,
                    onUpdateModeSelected = {},
                    onCheckForUpdatesClick = {},
                    onInstallUpdateClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Проверяем обновления...").assertExists()
        composeRule.onNodeWithText("Проверяем...").assertIsNotEnabled()
    }
}
