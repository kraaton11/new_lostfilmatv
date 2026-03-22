package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import com.kraat.lostfilmnewtv.updates.SavedAppUpdate
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

    @Test
    fun homeScreen_showsUpdateButtonNextToAppVersion_andInvokesCallback() {
        var installClicks = 0

        composeRule.setContent {
            LostFilmTheme {
                HomeScreen(
                    appVersionText = "0.1.0",
                    savedAppUpdate = SavedAppUpdate(
                        latestVersion = "0.2.0",
                        apkUrl = "https://example.test/app.apk",
                    ),
                    onInstallUpdateClick = { installClicks += 1 },
                )
            }
        }

        assertEquals(1, composeRule.onAllNodesWithText("Обновить").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("0.1.0").fetchSemanticsNodes().size)

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val buttonBounds = composeRule.onNodeWithText("Обновить").fetchSemanticsNode().boundsInRoot
        val versionBounds = composeRule.onNodeWithText("0.1.0").fetchSemanticsNode().boundsInRoot

        assertTrue(buttonBounds.right <= versionBounds.left)
        assertTrue(buttonBounds.bottom > rootBounds.bottom * 0.85f)
        assertTrue(versionBounds.bottom > rootBounds.bottom * 0.85f)

        composeRule.onNodeWithText("Обновить")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, installClicks)
    }
}
