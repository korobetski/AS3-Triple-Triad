package com.tripletriad.protocol

import com.tripletriad.data.CardCatalog
import com.tripletriad.data.NpcCatalog
import com.tripletriad.data.PveMatches
import com.tripletriad.model.Card
import com.tripletriad.model.CardCollection
import com.tripletriad.model.CardColor
import com.tripletriad.model.Deck
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchAi
import com.tripletriad.model.Npc
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/**
 * A transcript is checkable, and only a real match passes.
 *
 * ### What these tests are actually about
 *
 * Not "does the function work". The claim under test is the one the whole network design rests on:
 * **a player who controls their own device cannot invent a result.** Each rejection case below is a
 * specific way somebody would try — play a card they do not hold, stop before a losing position,
 * append a move after the board is full, name an opponent that does not exist — and the point is
 * that all of them fail without the server having watched anything.
 *
 * The acceptance case matters just as much and for the opposite reason: an honest offline match
 * must still count, or the design has bought integrity by taking the game away.
 */
class TranscriptVerifierTest {

    // ---- The honest case --------------------------------------------------

    @Test
    fun anHonestMatchIsAcceptedAndScoredByTheServer() {
        val transcript = playHonestly(SEED)

        val verdict = TranscriptVerifier.verify(transcript, cards, npcs)

        val accepted = assertIs<MatchVerdict.Accepted>(verdict, "rejected: $verdict")
        assertEquals(
            TOTAL_CARDS,
            accepted.blue + accepted.red,
            "the two sides must account for all nine cards plus the one in hand",
        )
    }

    /**
     * The property that makes the server's answer worth anything: it is a function of the
     * transcript alone, so two servers — or the same one after a restart — agree.
     */
    @Test
    fun verifyingTheSameTranscriptTwiceGivesTheSameVerdict() {
        val transcript = playHonestly(SEED)

        assertEquals(
            TranscriptVerifier.verify(transcript, cards, npcs),
            TranscriptVerifier.verify(transcript, cards, npcs),
        )
    }

    /**
     * The seed is load-bearing, not decoration.
     *
     * Without this the suite would pass just as happily if the verifier ignored the seed and
     * accepted any legal-looking sequence — which would accept a transcript replayed against a
     * friendlier deal than the one that was played.
     */
    @Test
    fun theSameMovesAgainstADifferentSeedDoNotReplay() {
        val honest = playHonestly(SEED)
        val relabelled = honest.copy(seed = SEED + 1)

        assertNotEquals(
            TranscriptVerifier.verify(honest, cards, npcs),
            TranscriptVerifier.verify(relabelled, cards, npcs),
            "the deal is different, so the same nine placements cannot reach the same result",
        )
    }

    // ---- The ways somebody would try it on --------------------------------

    @Test
    fun playingACardThatWasNeverInHandIsRejected() {
        val honest = playHonestly(SEED)
        val forged = honest.copy(
            moves = honest.moves.mapIndexed { index, move ->
                if (index == 0) move.copy(cardId = CARD_NOT_IN_ANY_HAND) else move
            },
        )

        assertRejected(RejectionReason.ILLEGAL_MOVE, forged)
    }

    @Test
    fun playingTwiceIntoTheSameCellIsRejected() {
        val honest = playHonestly(SEED)
        val forged = honest.copy(
            moves = honest.moves.mapIndexed { index, move ->
                if (index == 1) move.copy(position = honest.moves[0].position) else move
            },
        )

        assertRejected(RejectionReason.ILLEGAL_MOVE, forged)
    }

    /** Abandoning a match that is going badly must not read as a match. */
    @Test
    fun stoppingBeforeTheBoardIsFullIsRejected() {
        val honest = playHonestly(SEED)

        assertRejected(RejectionReason.TRUNCATED, honest.copy(moves = honest.moves.dropLast(1)))
    }

    @Test
    fun appendingAMoveAfterTheBoardIsFullIsRejected() {
        val honest = playHonestly(SEED)
        val padded = honest.copy(moves = honest.moves + honest.moves.last())

        assertRejected(RejectionReason.TRAILING_MOVES, padded)
    }

    @Test
    fun anOpponentThatDoesNotExistIsRejected() {
        val honest = playHonestly(SEED)

        assertRejected(
            RejectionReason.UNKNOWN_OPPONENT,
            honest.copy(opponentIconId = "not-an-opponent"),
        )
    }

    /**
     * The check a peer-to-peer design could not make at all, and that a server-held profile makes
     * free — see [MatchTranscript.ownedCards] for why today's version of it is still weak.
     */
    @Test
    fun aDeckHoldingCardsTheProfileDoesNotOwnIsRejected() {
        val honest = playHonestly(SEED)

        assertRejected(
            RejectionReason.DECK_NOT_OWNED,
            honest.copy(ownedCards = honest.ownedCards - honest.deck.first()),
        )
    }

    @Test
    fun aTranscriptFromAFormatThisBuildDoesNotReadIsRefusedRatherThanMisread() {
        val honest = playHonestly(SEED)

        assertRejected(
            RejectionReason.UNSUPPORTED_VERSION,
            honest.copy(version = TRANSCRIPT_VERSION + 1),
        )
    }

    // ---- The wire ---------------------------------------------------------

    /**
     * The transcript survives a round trip through JSON.
     *
     * Cheap, and it is the one thing between the client and the server that nothing else covers:
     * every test above verifies an object that never left the process.
     */
    @Test
    fun aTranscriptSurvivesJson() {
        val honest = playHonestly(SEED)

        val restored = json.decodeFromString<MatchTranscript>(json.encodeToString(honest))

        assertEquals(honest, restored)
        assertEquals(
            TranscriptVerifier.verify(honest, cards, npcs),
            TranscriptVerifier.verify(restored, cards, npcs),
        )
    }

    @Test
    fun aVerdictSurvivesJson() {
        val verdict = TranscriptVerifier.verify(playHonestly(SEED), cards, npcs)

        assertEquals(verdict, json.decodeFromString<MatchVerdict>(json.encodeToString(verdict)))
    }

    /**
     * A draw survives an encoder that drops nulls.
     *
     * Regression, and a real one: the server encodes with `explicitNulls = false`, so a drawn match
     * put no `winner` on the wire at all and every client failed to decode a legitimate verdict.
     * A draw is not an edge case — it is one of three outcomes — and it was the only one nothing
     * exercised.
     */
    @Test
    fun aDrawnVerdictSurvivesAnEncoderThatOmitsNulls() {
        val terse = Json { explicitNulls = false }
        val draw = MatchVerdict.Accepted(blue = DRAWN_SCORE, red = DRAWN_SCORE, winner = null)

        val encoded = terse.encodeToString<MatchVerdict>(draw)

        assertEquals(draw, json.decodeFromString<MatchVerdict>(encoded))
    }

    // ---- Fixtures ---------------------------------------------------------

    private fun assertRejected(expected: RejectionReason, transcript: MatchTranscript) {
        val verdict = TranscriptVerifier.verify(transcript, cards, npcs)

        val rejected = assertIs<MatchVerdict.Rejected>(verdict, "accepted: $verdict")
        assertEquals(expected, rejected.reason, rejected.detail)
    }

    /**
     * Plays a whole match honestly and writes down what the player did.
     *
     * This is the client's half of the protocol, in miniature — and it has to obey the invariant
     * documented on [TranscriptVerifier]: **the player's turn may not draw from the match
     * generator.** So the moves here are chosen by taking the first card in hand and the first
     * empty cell, which needs no randomness at all. Using [MatchAi] for the player as well would be
     * the natural-looking shortcut and would consume the stream on blue's turns, making every
     * honest transcript fail to replay.
     */
    private fun playHonestly(seed: Int): MatchTranscript {
        val random = Random(seed)
        val profile = GameSave(
            mode = CardCollection.FF14,
            cards = OWNED,
            decks = listOf(Deck("test", DECK)),
        )
        val match = PveMatches.assemble(profile, opponent, cards, random)

        val ai = MatchAi()
        var state = match.setup.state
        val moves = mutableListOf<TranscriptMove>()

        while (!state.isFinished) {
            state = if (state.currentPlayer == CardColor.BLUE) {
                val card = state.currentHand.first()
                val position = state.playablePositions().first()
                moves += TranscriptMove(card.id, position)
                state.play(card, position)
            } else {
                ai.play(state, random)
            }
        }

        return MatchTranscript(
            seed = seed,
            collection = CardCollection.FF14,
            opponentIconId = opponent.iconId,
            deck = DECK,
            ownedCards = OWNED,
            moves = moves,
        )
    }

    private fun card(id: Int) = Card(
        id = id,
        collection = "ff14_",
        nameKey = "STR_TEST_$id",
        name = "Test $id",
        top = (id % 9) + 1,
        right = ((id + 3) % 9) + 1,
        bottom = ((id + 5) % 9) + 1,
        left = ((id + 7) % 9) + 1,
        rarity = 1,
    )

    private val cards = CardCatalog(
        ff14 = (1..40).map { card(it) },
        ff8 = emptyList(),
    )

    private val opponent = Npc(
        id = 1,
        nameKey = "STR_NPC_Test",
        iconId = "test-npc",
        fetishCards = listOf(11, 12, 13),
        cards = listOf(20, 21, 22, 23),
    )

    private val npcs = NpcCatalog(ff14 = listOf(opponent), ff8 = emptyList())

    private val json = Json

    private companion object {
        const val SEED = 20260807

        val DECK = listOf(1, 2, 3, 4, 5)
        val OWNED = (1..12).toList()

        /** Outside every hand: the deck is 1..5 and the opponent draws from 11..13 and 20..23. */
        const val CARD_NOT_IN_ANY_HAND = 39

        /** Nine cells, plus the one card left in the winner's hand. */
        const val TOTAL_CARDS = 10

        /** Five apiece. */
        const val DRAWN_SCORE = 5
    }
}
