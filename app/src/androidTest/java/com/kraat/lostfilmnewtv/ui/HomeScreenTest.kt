package com.kraat.lostfilmnewtv.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.kraat.lostfilmnewtv.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreen_showsTitle() {
        val matchingNodes = composeRule.onAllNodesWithText("Новые релизы").fetchSemanticsNodes()

        assertTrue(matchingNodes.isNotEmpty())
    }
}
