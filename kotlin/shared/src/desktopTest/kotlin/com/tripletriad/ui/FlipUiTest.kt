package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
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
        awaitCatalog()

        onNodeWithTag(CARD_TEST_TAG).assertExists()
        assertVisible("Geezard", "card name is not rendered")
        assertVisible("owner: blue", "card did not start owned by blue")
    }

    @Test
    fun tappingTheCardFlipsItAndHandsItToTheOtherSide() = runComposeUiTest {
        setContent { App() }
        awaitCatalog()

        onNodeWithTag(CARD_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible("owner: red") }

        assertVisible("flips: 1", "flip counter did not advance")
    }

    @Test
    fun flippingTwiceReturnsTheCardToBlue() = runComposeUiTest {
        setContent { App() }
        awaitCatalog()

        onNodeWithTag(CARD_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible("owner: red") }

        onNodeWithTag(CARD_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible("owner: blue") }

        assertVisible("flips: 2", "second flip was not counted")
    }
}
