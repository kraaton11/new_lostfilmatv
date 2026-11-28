package com.kraat.lostfilmnewtv.ui.settings

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsServerSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setServerSection(
        host: String = "https://www.lostfilm.today",
        status: String? = null,
        onChanged: (String) -> Unit = {},
        onSave: () -> Unit = {},
        onReset: () -> Unit = {},
        onCheck: () -> Unit = {},
        onSelect: (String) -> Unit = {},
        onToggleAutomatic: () -> Unit = {},
    ) {
        composeRule.setContent {
            LostFilmTheme {
                SettingsScreen(
                    currentSection = SettingsSection.SERVER,
                    selectedQuality = PlaybackQualityPreference.Q720,
                    selectedUpdateMode = UpdateCheckMode.MANUAL,
                    selectedChannelMode = AndroidTvChannelMode.UNWATCHED,
                    installedVersionText = "0.1.0",
                    latestVersionText = null,
                    statusText = null,
                    isCheckingForUpdates = false,
                    installUrl = null,
                    lostFilmHost = host,
                    lostFilmHostStatusText = status,
                    actions = SettingsActions(
                        onQualitySelected = {},
                        onUpdateModeSelected = {},
                        onChannelModeSelected = {},
                        onCheckForUpdatesClick = {},
                        onInstallUpdateClick = {},
                        onLostFilmHostChanged = onChanged,
                        onSaveLostFilmHostClick = onSave,
                        onResetLostFilmHostClick = onReset,
                        onCheckLostFilmHostClick = onCheck,
                        onSelectLostFilmMirror = onSelect,
                        onToggleLostFilmAutomaticMirror = onToggleAutomatic,
                    ),
                )
            }
        }
    }

    @Test
    fun serverSection_isSelectableAndShowsHostField() {
        setServerSection()

        composeRule.onNodeWithTag("settings-section-server").assertIsSelected()
        composeRule.onNodeWithTag("settings-lostfilm-host", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("settings-lostfilm-host-save").assertExists()
        composeRule.onNodeWithTag("settings-lostfilm-host-reset").assertExists()
    }

    @Test
    fun serverSection_routesCheckMirrorSelectionAndAutomaticToggle() {
        val actions = mutableListOf<String>()
        setServerSection(
            onCheck = { actions += "check" },
            onSelect = { actions += it },
            onToggleAutomatic = { actions += "toggle" },
        )

        composeRule.onNodeWithTag("settings-lostfilm-host-check").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings-lostfilm-mirror-www.lostfilm.download").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings-lostfilm-automatic-mirror").performScrollTo().performClick()

        assertEquals(listOf("check", "https://www.lostfilm.download", "toggle"), actions)
    }

    @Test
    fun serverSummary_showsCurrentHostByDefault() {
        setServerSection(host = "")

        composeRule.onNodeWithTag("settings-section-server-summary", useUnmergedTree = true)
            .assertTextEquals("По умолчанию")
    }

    @Test
    fun serverSummary_showsSaveStatusWhenProvided() {
        setServerSection(status = "Адрес сохранен")

        composeRule.onNodeWithTag("settings-section-server-summary", useUnmergedTree = true)
            .assertTextEquals("Адрес сохранен")
    }

    @Test
    fun hostField_receivesText_throughCallback() {
        val typed = mutableListOf<String>()
        setServerSection(onChanged = { typed += it })

        composeRule.onNodeWithTag("settings-lostfilm-host", useUnmergedTree = true)
            .performTextInput("https://mirror.example.com")

        assertTrue(typed.isNotEmpty())
        assertTrue(
            "callback should receive typed text, got: ${typed.last()}",
            typed.last().contains("https://mirror.example.com"),
        )
    }
}
