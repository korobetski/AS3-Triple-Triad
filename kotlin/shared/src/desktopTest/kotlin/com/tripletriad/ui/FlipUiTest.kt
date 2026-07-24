package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * Runs the real Compose tree on the desktop target. This is the check that the PoC
 * actually *works* rather than merely compiling: the card must be on screen, a tap
 * must run the flip, and the card must end up owned by the other side.
 */
@OptIn(ExperimentalTestApi::class)
class FlipUiTest {

    @Test
    fun cardIsDisplayedOwnedByBlue() = runComposeUiTest {
        setContent { App() }

        onNodeWithTag(CardTestTag).assertExists()
        assertVisible("Geezard", "card name is not rendered")
        assertVisible("owner: blue", "card did not start owned by blue")
    }

    @Test
    fun tappingTheCardFlipsItAndHandsItToTheOtherSide() = runComposeUiTest {
        setContent { App() }

        onNodeWithTag(CardTestTag).performClick()
        waitUntil(timeoutMillis = 10_000) { isVisible("owner: red") }

        assertVisible("flips: 1", "flip counter did not advance")
    }

    @Test
    fun flippingTwiceReturnsTheCardToBlue() = runComposeUiTest {
        setContent { App() }

        onNodeWithTag(CardTestTag).performClick()
        waitUntil(timeoutMillis = 10_000) { isVisible("owner: red") }

        onNodeWithTag(CardTestTag).performClick()
        waitUntil(timeoutMillis = 10_000) { isVisible("owner: blue") }

        assertVisible("flips: 2", "second flip was not counted")
    }
}

@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.ComposeUiTest.isVisible(text: String): Boolean =
    onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.ComposeUiTest.assertVisible(text: String, message: String) {
    check(isVisible(text)) { "$message (no node containing \"$text\")" }
}
