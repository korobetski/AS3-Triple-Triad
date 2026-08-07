package com.tripletriad.protocol

import com.tripletriad.data.CardCatalog
import com.tripletriad.data.NpcCatalog
import com.tripletriad.data.PveMatches
import com.tripletriad.model.CardColor
import com.tripletriad.model.Deck
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchAi
import com.tripletriad.model.MatchState
import com.tripletriad.model.Npc
import kotlin.random.Random

/**
 * Replays a transcript and says whether it holds up.
 *
 * ### Why this lives in `:core` and not in the server
 *
 * Because then there is exactly one of it. The server runs this to decide whether to credit a
 * match; the client can run the *same function* before submitting, so a bug shows up as a local
 * failure rather than as a rejection the player cannot act on. A verifier written separately in the
 * server would be a second implementation of the rules, which is the one thing this whole design
 * exists to avoid.
 *
 * ### How the replay works
 *
 * One [Random], seeded once, threaded through everything in the order the client used it. That
 * ordering *is* the protocol: [PveMatches.assemble] draws the roulette, the opponent's hand and the
 * coin flip from it, and thereafter [MatchAi] draws from it on the opponent's turns only. Consume
 * it in a different order and every subsequent value differs — which is why the loop below asks
 * `state.currentPlayer` rather than assuming the turns alternate.
 *
 * That yields an invariant the **client** has to honour, and it is easy to break by accident:
 *
 * > On the player's own turn, nothing may draw from the match generator.
 *
 * `MatchState.playableCards(random)` takes one, and the turn timer's auto-play picks a cell at
 * random. Both must be given a *separate* generator, or the transcript will not replay — and it
 * will fail in a way that looks like cheating rather than like a bug.
 *
 * ### What it does not check
 *
 * Signatures, because there are none yet; and whether [MatchTranscript.ownedCards] is true, because
 * the caller states it. Both are named in [MatchTranscript]. What is checked here is that the match
 * is a **legal game**, which is independent of who claims to have played it.
 */
object TranscriptVerifier {

    /**
     * Replays [transcript] against the two catalogs and returns the verdict.
     *
     * Never throws for a bad transcript: an invalid claim is an ordinary answer, not an exception.
     * The one thing that would be a genuine fault — an engine that cannot deal a legal hand — is
     * caught and reported as [RejectionReason.UNDEALABLE] rather than escaping into the caller.
     */
    fun verify(
        transcript: MatchTranscript,
        cards: CardCatalog,
        npcs: NpcCatalog,
    ): MatchVerdict {
        // Version first, before any field is read. A transcript from a format this build does not
        // know might mean something different by the same names.
        if (transcript.version != TRANSCRIPT_VERSION) {
            return rejected(
                RejectionReason.UNSUPPORTED_VERSION,
                "transcript version ${transcript.version}, this build reads $TRANSCRIPT_VERSION",
            )
        }

        val npc = npcs.byIcon(transcript.opponentIconId, transcript.collection)

        // The check that pure peer-to-peer could not make, and that a server-held profile makes
        // free. Today it is weak because the claimant supplies both sides of it; when accounts
        // exist, `ownedCards` comes from the server's own record and this becomes real.
        val unowned = transcript.deck.filterNot { it in transcript.ownedCards }

        return when {
            npc == null -> rejected(
                RejectionReason.UNKNOWN_OPPONENT,
                "no opponent '${transcript.opponentIconId}' in ${transcript.collection}",
            )

            unowned.isNotEmpty() ->
                rejected(RejectionReason.DECK_NOT_OWNED, "deck holds unowned cards $unowned")

            else -> dealAndReplay(transcript, cards, npc)
        }
    }

    /**
     * Deals the match from the seed, then replays it.
     *
     * Separate from [verify] only so that the generator and the deal cannot be reached before the
     * claims above have been checked: [random] is created here and handed straight to both halves,
     * so there is no window in which one of them could be given a fresh one.
     */
    private fun dealAndReplay(
        transcript: MatchTranscript,
        cards: CardCatalog,
        npc: Npc,
    ): MatchVerdict {
        val random = Random(transcript.seed)

        return runCatching {
            PveMatches.assemble(profileFor(transcript), npc, cards, random)
        }.fold(
            onSuccess = { replay(it.setup.state, transcript.moves, random) },
            onFailure = { failure ->
                rejected(RejectionReason.UNDEALABLE, failure.message ?: "could not deal")
            },
        )
    }

    /**
     * Plays the match out: the player's moves as declared, the opponent's as derived.
     *
     * @param random the *same* generator [PveMatches.assemble] already drew from, at the position
     *   it left it. Passing a fresh one here would replay a different opponent.
     */
    private fun replay(
        start: MatchState,
        moves: List<TranscriptMove>,
        random: Random,
    ): MatchVerdict {
        val ai = MatchAi()
        var state = start
        var next = 0
        // Single exit: the loop stops on the first refusal rather than returning out of it, so the
        // leftover-move check below sees `next` where the replay actually stopped.
        var refused: MatchVerdict? = null

        while (refused == null && !state.isFinished) {
            if (state.currentPlayer != CardColor.BLUE) {
                state = ai.play(state, random)
                continue
            }

            val move = moves.getOrNull(next)
            val illegal = move?.let { reasonMoveIsIllegal(state, it) }

            when {
                move == null -> refused = rejected(
                    RejectionReason.TRUNCATED,
                    "the board still had ${state.playablePositions().size} cells and the moves " +
                        "ran out after $next",
                )

                illegal != null -> refused = rejected(RejectionReason.ILLEGAL_MOVE, illegal)

                else -> {
                    next++
                    state =
                        state.play(state.currentHand.first { it.id == move.cardId }, move.position)
                }
            }
        }

        return refused ?: outcomeOf(state, moves.size - next)
    }

    /**
     * The verdict for a match that played out to the end, with [leftover] moves unconsumed.
     *
     * Leftover moves are refused rather than ignored: they mean the client and the server disagree
     * about how the match went, and a disagreement silently discarded is the one failure mode this
     * design cannot detect later.
     */
    private fun outcomeOf(state: MatchState, leftover: Int): MatchVerdict = when {
        leftover > 0 -> rejected(
            RejectionReason.TRAILING_MOVES,
            "$leftover move(s) left over after the board was full",
        )

        else -> state.score.let {
            MatchVerdict.Accepted(blue = it.blue, red = it.red, winner = it.winner()?.name)
        }
    }

    /**
     * Why [move] cannot be played from [state], or null if it can.
     *
     * Both halves are needed. A card not in hand is the interesting forgery — playing a card the
     * player does not hold — while an occupied or out-of-range cell is what an off-by-one in either
     * implementation produces, and neither may reach [MatchState.play], which throws.
     */
    private fun reasonMoveIsIllegal(state: MatchState, move: TranscriptMove): String? = when {
        state.currentHand.none { it.id == move.cardId } ->
            "card ${move.cardId} is not in hand at placement ${state.placement}"

        move.position !in state.playablePositions() ->
            "cell ${move.position} is not playable at placement ${state.placement}"

        else -> null
    }

    /**
     * The profile the replay deals from.
     *
     * A synthetic one, built from the transcript's own claims, because there are no accounts yet.
     * The single deck is what [PveMatches.playerDeck] picks up — it takes the first *complete*
     * deck, and five cards is complete — so the player's chosen five are the ones dealt.
     *
     * When accounts arrive this function is what disappears: the caller will pass the stored
     * `GameSave`, and the transcript will stop being asked what the player owns.
     */
    private fun profileFor(transcript: MatchTranscript): GameSave = GameSave(
        mode = transcript.collection,
        cards = transcript.ownedCards,
        decks = listOf(Deck(SUBMITTED_DECK_NAME, transcript.deck)),
    )

    private fun rejected(reason: RejectionReason, detail: String): MatchVerdict =
        MatchVerdict.Rejected(reason, detail)

    private const val SUBMITTED_DECK_NAME = "submitted"
}
