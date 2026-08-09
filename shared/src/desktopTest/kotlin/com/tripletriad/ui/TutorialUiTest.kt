package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.CardColor
import com.tripletriad.model.HAND_SIZE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The lesson — `TutorialScreen`, through the real app.
 * It is [MatchScreen] with a [MatchScript] on it, so what is worth asserting is exactly the five
 * things the script changes: the deal, who starts, how the opponent plays, what is said, and where
 * the end panel goes. The match underneath is already covered by [MatchUiTest] and is not re-tested
 * here.
 */
@OptIn(ExperimentalTestApi::class)
class TutorialUiTest {

    /** The campaign entry sits above the opponent list and opens a board with no deck to choose. */
    @Test
    fun theLessonOpensStraightOntoABoard() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()

        onNodeWithTag(TUTORIAL_ROW_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
        assertFalse(
            exists(DECK_SELECT_CHOOSE_TEST_TAG),
            "the lesson deals a fixed hand its lines are written around; nothing is chosen",
        )
    }

    /**
     * The opponent moves first, and the player still holds five cards when it is their turn.
     *
     * `pof.rolls = [0,1,0]` (`TutorialScreen.as:64`) rigs the flip to red — 0 is red in
     * `PileOuFace` — so that the first thing the lesson does is demonstrate a placement rather than
     * ask for one. Read off the hands rather than off a turn tag, because "red has played" is the
     * claim and the turn has already passed back by the time it can be asserted.
     */
    @Test
    fun theTutorMovesFirst() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openLesson()

        awaitPlayer()
        assertEquals(HAND_SIZE, handSize(CardColor.BLUE), "the player has not played yet")
        assertEquals(
            HAND_SIZE - 1,
            handSize(CardColor.RED),
            "the opponent should have opened the match",
        )
    }

    /**
     * The first line is spoken before the opponent moves.
     * The whole of the pacing in one assertion: the lines play after the pre-match captions and the
     * opponent waits behind them ([lessonPause]), which is `setTimeout(AI, 18300)` behind three
     * lines at 6.1s. If the AI were not held back, the lesson would be explaining a board that had
     * already changed twice.
     */
    @Test
    fun theTutorSpeaksBeforePlaying() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openLesson()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(TALK_BUBBLE_TEST_TAG) }
        assertTrue(
            isVisible(FIRST_LINE),
            "the opening line should be the one about the three-by-three grid",
        )
        assertEquals(HAND_SIZE, handSize(CardColor.RED), "the opponent spoke before it played")
    }

    /**
     * The opponent loses on purpose — `MatchAiOptions.TUTOR`.
     * Asserted as an outcome rather than as a move: what the lesson promises is a match the player
     * can win while being told what a card is, and the way to check that is to play it out badly
     * (first card, first free cell, every turn) and still come out ahead. A test that pinned the
     * exact cell would break on any tie-break change and would not be saying anything about the
     * lesson.
     */
    @Test
    fun theLessonIsWinnableByPlayingBadly() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openLesson()

        playOut()

        val (blue, red) = score()
        assertTrue(
            blue > red,
            "the tutor plays its worst move; the lesson should be won, was $blue-$red",
        )
    }

    /**
     * The end panel offers the rule book where a match offers a rematch.
     *
     * `TutorialRematchPanel.rematchFooter` overrides the footer with Help and Quit
     * (`:19-33`), and `nextLesson` dispatches `NEXT_SCREEN`, which `TutorialScreen.endGame` sets to
     * `HELP_SCREEN` on all three results. Replacing the control rather than adding one is also what
     * keeps the lesson from being a repeatable source of MGP.
     */
    @Test
    fun theEndOfTheLessonLeadsToTheRuleBook() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openLesson()

        playOut()

        assertTrue(isVisible("Help"), "the first action should be the rule book, not a rematch")
        onNodeWithTag(NEW_MATCH_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(HELP_LIST_TEST_TAG) }
    }

    /** Creates a character and opens the lesson, waiting for the board. */
    private fun ComposeUiTest.openLesson() {
        newCharacter()
        openOpponents()
        onNodeWithTag(TUTORIAL_ROW_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
    }

    private companion object {
        /** `APP_TUTORIAL_1`, first clause — enough to identify, short enough to survive a wrap. */
        const val FIRST_LINE = "Triple Triad is played by placing cards"
    }
}
