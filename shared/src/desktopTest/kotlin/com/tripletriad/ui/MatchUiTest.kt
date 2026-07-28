package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.tripletriad.model.Board
import com.tripletriad.model.CardColor
import com.tripletriad.model.HAND_SIZE
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The real Compose tree driven through the real `App()`, which reads `cards.json` out of the
 * actual Compose resource bundle. So these also cover resource packaging: they fail if the
 * JSON is dropped from the bundle, if the generated `Res` accessor moves, or if the schema
 * drifts from the model.
 *
 * They replace `FlipUiTest` and `CatalogUiTest`, whose subject — one card that flipped on tap
 * — no longer exists now that the app shows a playable board.
 *
 * Everything is driven by test tag and visible text; nothing scrapes the semantics tree. The
 * deal is seeded, so the sequence below is deterministic, but the assertions are invariants
 * rather than a particular board.
 *
 * Resource packaging is covered by [awaitCatalog], which every test calls and which cannot
 * succeed unless `cards.json` was read from the bundle. The catalog's *contents* are
 * `CardBundleTest`'s business, and the arrangement — which hand goes where at which size — is
 * [MatchLayoutTest]'s.
 */
@OptIn(ExperimentalTestApi::class)
class MatchUiTest {
    @Test
    fun theBoardHasNineCellsAndBothHandsHaveFive() = runComposeUiTest {
        setContent { App() }
        awaitCatalog()

        onNodeWithTag(BOARD_TEST_TAG).assertExists()
        repeat(Board.SIZE) { onNodeWithTag(tileTestTag(it)).assertExists() }
        for (owner in CardColor.entries) {
            repeat(HAND_SIZE) { slot ->
                onNodeWithTag(handCardTestTag(owner, slot)).assertExists()
            }
            onNodeWithTag(handCardTestTag(owner, HAND_SIZE)).assertDoesNotExist()
        }
    }

    @Test
    fun theScoreStartsFiveFive() = runComposeUiTest {
        setContent { App() }
        awaitCatalog()

        assertVisible("blue 5 — 5 red", "unplayed cards count for their owner")
    }

    @Test
    fun pickingACardThenACellPlacesItAndPassesTheTurn() = runComposeUiTest {
        setContent { App() }
        awaitCatalog()

        val first = sideToPlay()
        onNodeWithTag(handCardTestTag(first, 0)).performClick()
        assertVisible("pick a cell", "selecting a card should prompt for a cell")

        onNodeWithTag(tileTestTag(CENTRE)).performClick()
        waitForIdle()

        // That side is down to four cards …
        onNodeWithTag(handCardTestTag(first, HAND_SIZE - 1)).assertDoesNotExist()
        // … and the turn passed.
        assertTrue(
            isVisible("${first.opposite().name.lowercase()} to play"),
            "the turn should pass to ${first.opposite()}",
        )
    }

    @Test
    fun onlyTheSideToPlayCanSelect() = runComposeUiTest {
        setContent { App() }
        awaitCatalog()

        val waiting = sideToPlay().opposite()
        onNodeWithTag(handCardTestTag(waiting, 0)).performClick()

        assertVisible("pick a card", "the idle side's cards must not become selectable")
        assertFalse(isVisible("pick a cell"), "no selection should have been made")
    }

    @Test
    fun placingOnATakenCellIsIgnored() = runComposeUiTest {
        setContent { App() }
        awaitCatalog()

        onNodeWithTag(handCardTestTag(sideToPlay(), 0)).performClick()
        onNodeWithTag(tileTestTag(CENTRE)).performClick()
        waitForIdle()

        val second = sideToPlay()
        onNodeWithTag(handCardTestTag(second, 0)).performClick()
        onNodeWithTag(tileTestTag(CENTRE)).performClick()
        waitForIdle()

        // The click was swallowed rather than throwing, and it is still that side's turn
        // with a card selected.
        assertVisible("pick a cell", "the selection should survive an illegal placement")
        assertTrue(isVisible("${second.name.lowercase()}:"), "still ${second.name}'s turn")
    }

    @Test
    fun capturesMoveTheScoreAndItAlwaysTotalsTen() = runComposeUiTest {
        setContent { App() }
        awaitCatalog()

        playOut()

        // Nine placements from a 263-card deal ending exactly level would mean no capture
        // ever happened, which is not credible.
        assertFalse(
            isVisible("blue 5 — 5 red"),
            "the score should have moved: a capture must have occurred",
        )
        assertTrue(totalIsTen(), "the two scores must always total 10")
    }

    @Test
    fun playingOutTheMatchProducesAResult() = runComposeUiTest {
        setContent { App() }
        awaitCatalog()

        playOut()

        onNodeWithTag(OUTCOME_TEST_TAG).assertExists()
        onNodeWithTag(TURN_TEST_TAG).assertDoesNotExist()
        assertTrue(
            isVisible("wins") || isVisible("draw"),
            "a finished match must announce a winner or a draw",
        )
    }

    @Test
    fun newMatchResetsTheBoard() = runComposeUiTest {
        setContent { App() }
        awaitCatalog()

        playOut()
        onNodeWithTag(NEW_MATCH_TEST_TAG).performClick()
        waitForIdle()

        for (owner in CardColor.entries) {
            repeat(HAND_SIZE) { slot ->
                onNodeWithTag(handCardTestTag(owner, slot)).assertExists()
            }
        }
        assertVisible("blue 5 — 5 red", "a new match starts level")
        onNodeWithTag(OUTCOME_TEST_TAG).assertDoesNotExist()
    }

    private companion object {
        const val CENTRE = 4
    }
}
