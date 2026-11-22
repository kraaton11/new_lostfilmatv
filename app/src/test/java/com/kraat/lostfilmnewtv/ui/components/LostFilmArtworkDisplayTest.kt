package com.kraat.lostfilmnewtv.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kraat.lostfilmnewtv.data.parser.LostFilmBaseUrl
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LostFilmArtworkDisplayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resolvesExistingPosterOnNewMirrorAfterSettingChanges() {
        val originalBaseUrl = LostFilmBaseUrl.get()
        val path = "/Static/Images/362/Posters/image_s9.jpg"
        try {
            LostFilmBaseUrl.set("https://www.lostfilm.tv")
            composeRule.setContent {
                Text(resolveArtworkForDisplay(path).orEmpty())
            }
            composeRule.onNodeWithText("https://www.lostfilm.tv$path").assertExists()

            composeRule.runOnIdle { LostFilmBaseUrl.set("https://www.lostfilm.download") }

            composeRule.onNodeWithText("https://www.lostfilm.download$path").assertExists()
        } finally {
            LostFilmBaseUrl.set(originalBaseUrl)
        }
    }
}
