package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.data.loadNpcCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.CardCollection
import com.tripletriad.model.GameSave
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.TOTAL_CARDS
import com.tripletriad.protocol.MatchTranscript
import com.tripletriad.protocol.MatchVerdict
import com.tripletriad.protocol.TranscriptVerifier
import com.tripletriad.time.FixedClock
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The test the whole of Phase 5 rests on: a match played in the UI must replay in the engine.
 *
 * ### Why it has to be an end-to-end test
 *
 * Every part of this is already unit-tested — [TranscriptVerifier] against hand-written
 * transcripts, `KtorMatchSubmitter` against a mock engine, `TranscriptQueue` against a fake store.
 * None of that can catch the failure that actually matters, which is the client and the server
 * disagreeing about *what happened*. A transcript is only a claim about a replayable game if the
 * screen that produced it drew from the generator in exactly the order the verifier re-runs it.
 *
 * That invariant is fragile in a way nothing else here is: it is broken not by wrong code but by
 * **any** extra draw from the match generator on the player's turn. Three call sites already had to
 * be fixed for this test to pass at all — the deck selector's Random button and the turn timer's
 * auto-play, both moved onto a second generator, and a seed that was derived twice rather than
 * kept. A fourth would break it silently, and would surface in production as honest players'
 * matches being rejected.
 *
 * So: play a whole match through the real screen, take the transcript it emits, and hand it to the
 * same verifier the server runs. If they disagree, this fails — which is the only place they can be
 * made to disagree cheaply.
 */
@OptIn(ExperimentalTestApi::class)
class MatchTranscriptTest {
    private val cards = runBlocking { loadCardCatalog() }
    private val npcs = runBlocking { loadNpcCatalog() }
    private val english = runBlocking { loadStrings(AppLocale.EN_US) }

    /** `tt-master`: All Open and nothing else, so no rule narrows what may be played. */
    private val opponent = npcs
        .available(CardCollection.FF14, FixedClock.DEFAULT_HOUR, ANY_LEVEL)
        .first { it.iconId == "tt-master" }

    /**
     * The whole claim, in one test.
     *
     * The verdict is asserted to be [MatchVerdict.Accepted] and **not** to any particular score:
     * who wins depends on the deal, and pinning it would make this a golden test of the generator
     * instead of a test of the agreement between the two implementations.
     */
    @Test
    fun aMatchPlayedInTheUiReplaysInTheEngine() = runComposeUiTest {
        val transcript = playAMatch()

        val verdict = TranscriptVerifier.verify(transcript, cards, npcs)

        assertIs<MatchVerdict.Accepted>(
            verdict,
            "the engine could not replay a match the UI just played: $verdict",
        )
    }

    /** The score the server recomputes is a real score of a full board, not a partial replay. */
    @Test
    fun theReplayedScoreAccountsForEveryCard() = runComposeUiTest {
        val transcript = playAMatch()

        val accepted = assertIs<MatchVerdict.Accepted>(
            TranscriptVerifier.verify(transcript, cards, npcs),
        )

        assertEquals(TOTAL_CARDS, accepted.blue + accepted.red)
    }

    /** What the player chose, and only that — the opponent's moves are the server's to derive. */
    @Test
    fun theTranscriptCarriesOnlyThePlayersOwnPlacements() = runComposeUiTest {
        val transcript = playAMatch()

        assertTrue(
            transcript.moves.size in PLAYER_MOVES_MIN..PLAYER_MOVES_MAX,
            "four or five placements depending on the coin flip, was ${transcript.moves.size}",
        )
        assertEquals(
            transcript.moves.map { it.position }.distinct().size,
            transcript.moves.size,
            "no cell is played twice",
        )
        assertEquals(HAND_SIZE, transcript.deck.size)
    }

    /**
     * Plays a match through the real screen and returns the transcript it emitted.
     *
     * A `FixedClock` pins the seed, so the same match is played on every run and a failure here is
     * reproducible rather than a coin flip. That is not what makes the test meaningful, though —
     * what does is that the transcript is whatever `MatchScreen` decided to emit, not something
     * assembled by this file.
     */
    private fun ComposeUiTest.playAMatch(): MatchTranscript {
        var emitted: MatchTranscript? = null
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
                        onTranscript = { emitted = it },
                    )
                }
            }
        }
        settleDeck()
        playOut()
        // The transcript is emitted from the same effect that credits the match, after the reward,
        // so it can land a frame after the result panel that `playOut` waits for.
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { emitted != null }
        return assertNotNull(emitted, "the match finished but no transcript was emitted")
    }

    private companion object {
        /** Nine placements, split by the coin flip: whoever moves first plays five. */
        const val PLAYER_MOVES_MIN = 4
        const val PLAYER_MOVES_MAX = 5
    }
}
