package com.kraat.lostfilmnewtv.ui.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
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
    fun settingsScreen_marksCurrentQualityAndInvokesCallback() {
        val clicked = mutableListOf<PlaybackQualityPreference>()

        composeRule.setContent {
            LostFilmTheme {
                SettingsScreen(
                    selectedQuality = PlaybackQualityPreference.Q1080,
                    onQualitySelected = { clicked += it },
                )
            }
        }

        composeRule.onNodeWithTag("settings-quality-1080").assertIsSelected()
        composeRule.onNodeWithTag("settings-quality-720")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(PlaybackQualityPreference.Q720), clicked)
    }
}
