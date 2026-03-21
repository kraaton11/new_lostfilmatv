package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.kraat.lostfilmnewtv.ui.theme.LostFilmTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreen_showsAppVersionInBottomRightCorner() {
        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(appVersionText = "0.1.0")
            }
        }

        assertEquals(1, composeRule.onAllNodesWithText("0.1.0").fetchSemanticsNodes().size)

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val versionBounds = composeRule.onNodeWithText("0.1.0").fetchSemanticsNode().boundsInRoot

        assertTrue(versionBounds.right >= rootBounds.right * 0.85f)
        assertTrue(versionBounds.bottom > rootBounds.bottom * 0.85f)
    }
}
