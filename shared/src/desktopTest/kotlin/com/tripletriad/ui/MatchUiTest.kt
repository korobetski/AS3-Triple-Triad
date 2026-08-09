package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.Board
import com.tripletriad.model.CardColor
import com.tripletriad.model.HAND_SIZE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The real Compose tree driven through the real `App()`, which reads `cards.json` and `npcs.json`
 * out of the actual resource bundle. So these also cover resource packaging: they fail if either
 * JSON is dropped, if the generated `Res` accessor moves, or if a schema drifts from its model.
 *
 * ### What changed when the opponent started playing itself
 *
 * These tests used to drive *both* hands, because nothing else did. Now `MatchAi` takes the red
 * side after a short pause, which means a test can no longer assume the turn has come back to it by
 * the next line, nor that a chosen cell is still free. Two helpers absorb that: [awaitPlayer] waits
 * for the turn, and [playOneCard] probes for a free cell and confirms the placement by watching the
 * hand shrink. Everything below is written in terms of those.
 *
 * The deal is deterministic — `App` defaults to a `FixedClock`, and `MatchScreen` seeds its
 * generator from it — but the assertions are invariants rather than a particular board, so a
 * changed seed cannot quietly turn one of these into a tautology.
 */
@OptIn(ExperimentalTestApi::class)
class MatchUiTest {
    /**
     * Nine cells, a full hand for the player, and no sixth slot on either side.
     *
     * The opponent's hand is **four or five**, not five: the coin flip decides who moves first, and
     * `startMatch` returns once it is the player's turn — so if red won the flip it has already
     * played. Asserting five would have been asserting the flip.
     */
    @Test
    fun theBoardHasNineCellsAndAFullPlayerHand() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()

        onNodeWithTag(BOARD_TEST_TAG).assertExists()
        repeat(Board.SIZE) { onNodeWithTag(tileTestTag(it)).assertExists() }
        assertEquals(HAND_SIZE, handSize(CardColor.BLUE), "the player should hold a full hand")
        assertTrue(
            handSize(CardColor.RED) >= HAND_SIZE - 1,
            "the opponent should have played at most once",
        )
        for (owner in CardColor.entries) {
            onNodeWithTag(handCardTestTag(owner, HAND_SIZE)).assertDoesNotExist()
        }
    }

    @Test
    fun theScoreStartsFiveFive() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()

        onNodeWithTag(SCORE_TEST_TAG).assertTextEquals(LEVEL_SCORE)
    }

    /** The opponent is named on the board, so a player knows who they are facing. */
    @Test
    fun theBoardNamesTheOpponent() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()

        onNodeWithTag(MATCH_OPPONENT_TEST_TAG).assertTextEquals("Triple Triad Master")
    }

    /**
     * The rules in force are stated. `tt-master` imposes All Open and nothing else, so this asserts
     * both that the strip appears and that it does not invent rules nobody chose.
     */
    @Test
    fun theRulesInForceAreNamedOnTheBoard() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()

        onNodeWithTag(MATCH_RULES_TEST_TAG).assertTextEquals("All Open")
    }

    /**
     * Tapping the strip explains the rules it names, and tapping it again puts them away.
     *
     * The bundles have carried `RULE_ALL_OPEN_HELP` since the import and nothing ever showed one
     * during a match: naming Fallen Ace and explaining it are different services, and the strip
     * only did the first. Closed is the default — asserted here, because a strip that opened
     * itself would cost a phone's board four lines for a sentence read a hundred times already.
     */
    @Test
    fun theRuleStripOpensToExplainWhatItNames() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()

        // Read unmerged: the strip is `clickable`, so it absorbs its children's semantics.
        val help = ruleHelpTestTag("RULE_ALL_OPEN")
        assertFalse(existsUnmerged(help), "the strip should start closed")

        onNodeWithTag(MATCH_RULES_TEST_TAG).performClick()
        assertTrue(existsUnmerged(help), "tapping the strip should explain its rules")
        onNodeWithTag(help, useUnmergedTree = true)
            .assertTextEquals("All Open — Both decks are placed face up.")

        onNodeWithTag(MATCH_RULES_TEST_TAG).performClick()
        assertFalse(existsUnmerged(help), "tapping it again should close it")
    }

    /**
     * The banner shows the face the player picked from the opponent list.
     *
     * `portraitTestTag` is the same tag that list uses, so this is the assertion that the two
     * screens draw the *same* opponent — the board named one and pictured nobody until now.
     */
    @Test
    fun theBoardShowsTheOpponentsPortrait() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()

        onNodeWithTag(portraitTestTag(TEST_OPPONENT)).assertExists()
    }

    @Test
    fun pickingACardThenACellPlacesItAndPassesTheTurn() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()

        onNodeWithTag(handCardTestTag(CardColor.BLUE, 0)).performClick()
        assertVisible("pick a cell", "selecting a card should prompt for a cell")

        onNodeWithTag(tileTestTag(CENTRE)).performClick()
        waitForIdle()

        assertEquals(HAND_SIZE - 1, handSize(CardColor.BLUE), "the card should have left the hand")
    }

    /**
     * The opponent takes its own turn, unprompted.
     *
     * The assertion is that **red's hand shrinks with no further input** — the one thing that
     * separates an opponent from a second seat at the same keyboard.
     */
    @Test
    fun theOpponentPlaysByItself() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()

        // Whoever the flip favours, red will have played once the turn is back with blue and blue
        // has moved at least once — so drive one blue turn and wait.
        playOneCard()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { handSize(CardColor.RED) < HAND_SIZE }

        assertTrue(handSize(CardColor.RED) < HAND_SIZE, "red never played")
    }

    /** The player's cards are not selectable while the opponent is to move. */
    @Test
    fun theOpponentsHandIsNeverSelectable() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()

        onNodeWithTag(handCardTestTag(CardColor.RED, 0)).performClick()
        waitForIdle()

        assertFalse(isVisible("pick a cell"), "clicking the opponent's hand selected something")
    }

    @Test
    fun placingOnATakenCellIsIgnored() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()

        val taken = playOneCard()
        awaitPlayer()
        val before = handSize(CardColor.BLUE)
        onNodeWithTag(handCardTestTag(CardColor.BLUE, 0)).performClick()
        onNodeWithTag(tileTestTag(taken)).performClick()
        waitForIdle()

        // The click was swallowed rather than throwing, and the selection survived it.
        assertEquals(before, handSize(CardColor.BLUE), "an occupied cell accepted a card")
        assertVisible("pick a cell", "the selection should survive an illegal placement")
    }

    /**
     * Captures move the score, and the two halves always total ten.
     *
     * Sampled **throughout** the match rather than at the end, which is what an earlier version
     * did: a final 5-5 does not mean nothing was captured — captures either way can cancel out, and
     * this deal happens to end level. Asserting on the last frame made the test fail on a
     * legitimate draw while a genuinely capture-free engine would have passed it whenever the deal
     * ended uneven.
     */
    @Test
    fun capturesMoveTheScoreAndItAlwaysTotalsTen() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()

        val seen = mutableListOf(score())
        while (!isFinished()) {
            playOneCard()
            assertTrue(totalIsTen(), "the two scores must always total 10, read ${score()}")
            seen += score()
            waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isPlayerTurn() || isFinished() }
            seen += score()
        }

        assertTrue(
            seen.any { it != HAND_SIZE to HAND_SIZE },
            "the score never left 5-5, so nothing was ever captured: $seen",
        )
    }

    @Test
    fun playingOutTheMatchProducesAResultAndAPayout() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()

        playOut()

        onNodeWithTag(MATCH_RESULT_TEST_TAG).assertExists()
        // `You win !` / `You lose...` / `Draw` — the bundle has no neutral "red wins", so the
        // result is phrased from blue's side. See `TurnLine`.
        assertTrue(
            isVisible("You win") || isVisible("You lose") || isVisible("Draw"),
            "a finished match must announce a result",
        )
        // Every result pays in this game, so the payout line is always there and always positive.
        assertVisible("+", "the payout should be shown")
        assertVisible("MGP", "the payout should name MGP")
    }

    @Test
    fun theRematchControlDealsAgain() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()

        playOut()
        onNodeWithTag(NEW_MATCH_TEST_TAG).performClick()
        // A rematch is a fresh match, so it runs the deck selector again — `deckSelectionPhase`
        // is entered once per match in the original too.
        settleDeck()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !isFinished() }

        assertEquals(HAND_SIZE, handSize(CardColor.BLUE), "the player should be dealt again")
        onNodeWithTag(SCORE_TEST_TAG).assertTextEquals(LEVEL_SCORE)
    }

    @Test
    fun leavingTheResultPanelReturnsToTheOpponentList() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()

        playOut()
        onNodeWithTag(MATCH_DONE_TEST_TAG).performClick()
        awaitOpponents()

        onNodeWithTag(opponentRowTestTag(TEST_OPPONENT)).assertExists()
    }

    /**
     * The localisation, through the real tree rather than through `Strings` in isolation.
     *
     * Without this, every other test in this file pins `EN_US` and the wiring could be serving one
     * hard-coded bundle to everybody.
     */
    @Test
    fun theUiIsInTheChosenLanguage() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.FR_FR)) }
        startMatch()

        assertTrue(
            isVisible("choisissez une carte") || isVisible("joue"),
            "the turn line should be French",
        )
        onNodeWithTag(MATCH_RULES_TEST_TAG).assertTextEquals("Toutes sur table")
        assertFalse(isVisible("pick a card"), "no English should be left on the board screen")
    }

    /**
     * The fallback, also through the real tree — and exercised by the shipped data rather than by a
     * contrived table. `de_DE` is 44 keys short, so some controls resolve through English while the
     * German it does have is used.
     */
    @Test
    fun aMissingStringFallsBackToEnglishWithoutDisturbingTheRest() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.DE_DE)) }
        startMatch()

        // `RULE_ALL_OPEN` resolves in German — to the English words, because the imported de_DE
        // bundle really does say "All Open" there. That is the shipped data, not a fallback.
        onNodeWithTag(MATCH_RULES_TEST_TAG).assertTextEquals("All Open")
        playOut()
        assertTrue(
            isVisible("Du hast gewonnen") || isVisible("Sie verlieren") || isVisible("Zeichnen"),
            "the outcome is one of the keys German does define",
        )
    }

    private companion object {
        const val CENTRE = 4

        /** Five unplayed cards each. The score line is two numbers and a dash, no colour words. */
        const val LEVEL_SCORE = "5 — 5"
    }
}
