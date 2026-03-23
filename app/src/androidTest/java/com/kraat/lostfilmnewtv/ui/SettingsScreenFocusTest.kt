package com.kraat.lostfilmnewtv.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.ui.settings.SettingsScreen
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SettingsScreenFocusTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun initialFocus_landsOnQualityRailItem_andMovesBetweenZones() {
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

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("settings-section-quality").assertIsFocused()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag("settings-section-quality").assertIsFocused()
        composeRule.onNodeWithTag("settings-section-quality").performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings-quality-1080").assertIsFocused()
        composeRule.onNodeWithTag("settings-quality-1080").performKeyInput {
            keyDown(Key.DirectionLeft)
            keyUp(Key.DirectionLeft)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings-section-quality").assertIsFocused()
    }

    @Test
    fun returningToQualitySection_restoresLastFocusedValue() {
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
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithTag("settings-section-quality").performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings-quality-720")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithTag("settings-quality-720").assertIsFocused()

        composeRule.onNodeWithTag("settings-section-channel")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("settings-section-quality")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("settings-section-quality")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithTag("settings-section-quality").performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings-quality-720").assertIsFocused()
    }
}
