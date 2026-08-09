package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.data.loadNpcCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.CardCollection
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameSave
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.time.FixedClock
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The turn limit — `playerPanel`'s `ProgressBar`, and what happens when it runs out.
 *
 * ### This is a game mechanic, not decoration
 *
 * `playerPanel._timer = 30` and `BaseMatchScreen.as:377-387` arms it on whichever side is to move.
 * `:93` listens for `TIME_UP_EVENT` on the blue player and `timeUp_play` calls `autoPlay()` — which
 * plays a **random** remaining card on a **random** free cell. So letting the clock run out does
 * not pass the turn; it plays a move you did not choose.
 *
 * ### Driven through `MatchScreen` rather than `App`
 *
 * The limit is a parameter of the match, and reaching its expiry through the whole app would mean
 * either waiting thirty seconds or threading a test-only argument down four screens. Composing the
 * screen directly is the smaller lie: everything it needs is already an argument, which is what
 * `MatchScreen`'s own KDoc says that design is for.
 */
@OptIn(ExperimentalTestApi::class)
class TurnTimerTest {
    private val cards = runBlocking { loadCardCatalog() }
    private val npcs = runBlocking { loadNpcCatalog() }
    private val english = runBlocking { loadStrings(AppLocale.EN_US) }

    /** `tt-master` again: All Open and nothing else, so no rule narrows what may be played. */
    private val opponent = npcs
        .available(CardCollection.FF14, FixedClock.DEFAULT_HOUR, ANY_LEVEL)
        .first { it.iconId == "tt-master" }

    private fun ComposeUiTest.openMatch(limit: kotlin.time.Duration) {
        setContent {
            TripleTriadTheme {
                CompositionLocalProvider(LocalStrings provides english) {
                    MatchScreen(
                        catalog = cards,
                        profile = GameSave.new(createdAt = 0L),
                        npc = opponent,
                        clock = FixedClock(),
                        onPersist = {},
                        onExit = {},
                        turnLimit = limit,
                    )
                }
            }
        }
        // The match opens on the deck selector, as it does through the app — see there for why it
        // is a step inside the match rather than a destination ahead of it.
        settleDeck()
        awaitPlayer()
    }

    /**
     * An unattended turn plays itself.
     *
     * The card that lands is not asserted, because [autoPlay] draws it at random — that randomness
     * *is* the penalty (`BaseMatchScreen.as:427-428`), and pinning which card came out would be
     * pinning the generator rather than the rule.
     */
    @Test
    fun aTurnThatRunsOutPlaysACardByItself() = runComposeUiTest {
        openMatch(limit = SHORT)
        val before = handSize(CardColor.BLUE)

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { handSize(CardColor.BLUE) < before }

        assertEquals(before - 1, handSize(CardColor.BLUE), "one card, not the whole hand")
    }

    /**
     * The bar is up while the player may move and gone while they may not.
     *
     * `razTimer()` on the side that is *not* to play is what the absence stands for. Red's own bar
     * is not drawn at all — see [TurnTimerBar] for why the original's second one is decoration.
     *
     * The track is up from the first frame; the **fill** waits for the pre-match announcements, so
     * this waits for it. That gap is the mechanic rather than a delay to work around: the original
     * arms the clock in `nextTurn`, which runs after the whole cascade, so the player's turn does
     * not start counting down behind the Start banner.
     */
    @Test
    fun theBarIsUpOnlyOnThePlayersTurn() = runComposeUiTest {
        openMatch(limit = 1.seconds)

        onNodeWithTag(TURN_TIMER_TEST_TAG).assertExists()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            exists(TURN_TIMER_FILL_TEST_TAG) || !isPlayerTurn() || isFinished()
        }
        assertTrue(exists(TURN_TIMER_FILL_TEST_TAG), "the player is to move, so it should run")

        // Play, and the turn passes to an opponent that thinks for `OPPONENT_PAUSE_MS`.
        playOneCard()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            !isPlayerTurn() || isFinished() || !exists(TURN_TIMER_FILL_TEST_TAG)
        }

        if (!isPlayerTurn() && !isFinished()) {
            assertFalse(exists(TURN_TIMER_FILL_TEST_TAG), "the clock should not run on red's turn")
        }
        onNodeWithTag(TURN_TIMER_TEST_TAG).assertExists("the track stays, so the bar does not jump")
    }

    /** A generous limit is not reached by a player who is playing, so nothing is auto-played. */
    @Test
    fun aPlayerWhoMovesInTimeKeepsTheirChoice() = runComposeUiTest {
        openMatch(limit = 1.seconds)

        val played = playOneCard()

        assertEquals(HAND_SIZE - 1, handSize(CardColor.BLUE), "exactly the card the player chose")
        assertTrue(played in 0 until com.tripletriad.model.Board.SIZE)
    }

    /**
     * The clock stops when the match does.
     *
     * Otherwise the last expiry would fire against a finished board and try to play a tenth card —
     * which `canPlay` would refuse, but only after the effect had counted a whole turn down against
     * a result panel.
     */
    @Test
    fun aFinishedMatchHasNoClockRunning() = runComposeUiTest {
        openMatch(limit = 1.seconds)

        playOut()

        assertTrue(isFinished())
        assertFalse(exists(TURN_TIMER_FILL_TEST_TAG), "the clock should be stopped")
    }

    private companion object {
        /** Long enough to compose a frame, short enough that a test does not wait for it. */
        val SHORT = 300.milliseconds
    }
}
