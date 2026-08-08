package com.tripletriad.model

import kotlin.random.Random

/** The best [cover] a placement can have: four sides, each contributing at most [ACE_POWER]. */
const val MAX_COVER: Int = 4 * ACE_POWER

/**
 * One candidate placement, with the two numbers the AI ranks by.
 *
 * @property captures how many cards the placement would flip, combos included. The AS3
 *   `applyRules(tile, color, checking = true)` return value.
 * @property cover how well the placed card defends itself, [0][MIN_EFFECTIVE_POWER]..[MAX_COVER].
 *   Higher is safer.
 */
data class ScoredMove(
    val card: Card,
    val position: Int,
    val captures: Int,
    val cover: Int,
)

/**
 * The one place this AI departs from `PVEMatchScreen.AI`.
 *
 * @property coverBreaksTies whether a tie on [ScoredMove.captures] is settled by [ScoredMove.cover]
 *   before the random pick. The original sorts by both — `powers.sortOn(['power', 'cover'],
 *   [DESCENDING, DESCENDING])` (`:218`) — and then builds `bestMoves` from every entry whose
 *   `power` equals the best (`:224-227`) and picks one at random, so **the cover sort has no
 *   effect on the move actually chosen**. It is dead work: the AI computes a defensive score for
 *   45 candidate placements and then throws it away except in the branch where nothing captures.
 *
 *   Set to `false` to reproduce that. The default settles the tie, which is what sorting by cover
 *   was evidently meant to do and makes the opponent stop volunteering an ace into an open corner
 *   when a safer square captures the same card.
 */
data class MatchAiOptions(
    val coverBreaksTies: Boolean = true,
    val playsWorst: Boolean = false,
) {
    companion object {
        /** Reproduces `PVEMatchScreen.AI` exactly, dead cover sort included. */
        val FAITHFUL = MatchAiOptions(coverBreaksTies = false)

        /**
         * The tutorial opponent, which loses on purpose — `TutorialScreen.AI` (`:246-249`).
         *
         * It scores every placement exactly as the real AI does and then takes
         * `powers[powers.length - 1]`, the **last** entry of a list sorted best first:
         *
         * ```actionscript
         * // c'est le tuto, le pnj jour toujours la pire solution
         * card = powers[powers.length -1].card;
         * ```
         *
         * So the lesson is winnable by a player who has just been told what a card is. Note it is
         * the *worst* move rather than a random one: a random opponent would occasionally capture
         * three cards in a row and teach the opposite of what the script is saying at the time.
         */
        val TUTOR = MatchAiOptions(playsWorst = true)
    }
}

/**
 * The opponent — `PVEMatchScreen.AI` (`:182-254`), as a pure function of the match state.
 *
 * Two nested loops over every remaining card and every free cell, scoring each pair by how many
 * cards it captures and how exposed it leaves the placed card, then a random pick among the best.
 * It looks exactly one move ahead and models nothing the player might do next.
 *
 * **Evaluation is now side-effect free, which it was not.** The original scores a candidate by
 * assigning `tile.card = card` and calling `applyRules(…, checking = true)`, which overwrites
 * `tile.color`, recomputes the tile's powers and — under Elemental — mutates `card.modifier`. It
 * then has to undo the damage by hand: `if (RULES.TYPE_RULE == RULE_ELEMENTAL) tile.card.modifier =
 * 0;` and a `tile.card = null; // VERY IMPORTANT` (`:212-213`). Here [RulesEngine.resolve] returns
 * a new board and touches nothing, so there is no damage and no reset
 * ([game-rules.md](../../../../../../../docs/analysis/game-rules.md) § 14).
 *
 * **There are no difficulty levels.** `NPC.difficulty` is read only to order the opponent list
 * (`NpcCatalog.available`); every opponent from the tutorial dummy to the Queen of Cards plays with
 * this same function. Giving the field its apparent meaning would be new game design rather than
 * migration, so it is left alone.
 */
class MatchAi(private val options: MatchAiOptions = MatchAiOptions()) {
    /**
     * How exposed [card] would be at [position] — `PVEMatchScreen.as:202-210`.
     *
     * Each of the four sides contributes:
     *
     * - a neighbouring cell that exists and is **empty**: the placed card's own effective power on
     *   that side, or `10 - power` under Reverse, since Reverse makes a low edge the strong one;
     * - a wall, or a cell already occupied: [ACE_POWER], the maximum. Neither can attack — a wall
     *   has nothing to place on it, and a card already on the board never initiates a capture.
     *
     * So cover measures only the *open* flanks, and a placement walled in on three sides scores
     * high whatever it holds.
     */
    fun cover(state: MatchState, card: Card, position: Int): Int = Side.entries.sumOf { side ->
        val neighbour = state.board.neighbour(position, side)
        if (neighbour == null || state.board[neighbour] != null) {
            ACE_POWER
        } else {
            val power = effectivePower(
                card,
                side,
                state.rules,
                state.board.elements[position],
                state.tally,
            )
            if (state.rules.reverse) ACE_POWER - power else power
        }
    }

    /** Scores playing [card] at [position], without touching [state]. */
    fun evaluate(state: MatchState, card: Card, position: Int): ScoredMove {
        val player = checkNotNull(state.currentPlayer) { "the match is over" }
        val resolution = RulesEngine(state.rules, state.options)
            .resolve(state.board, position, card, player, state.tally)
        return ScoredMove(
            card = card,
            position = position,
            captures = resolution.captures.size,
            cover = cover(state, card, position),
        )
    }

    /**
     * Every legal placement, best first.
     *
     * Ranked by captures then cover, descending on both, as `sortOn` does. Order and Chaos are
     * applied first through [MatchState.playableCards], matching the original, which narrows its
     * own candidate list before scoring (`:185-188`) — so under Order the AI considers only its
     * first card, and under Chaos only the one the rule picked.
     */
    fun candidates(state: MatchState, random: Random = Random.Default): List<ScoredMove> {
        if (state.isFinished) return emptyList()
        val cards = state.playableCards(random)
        val positions = state.playablePositions()
        val ranking = compareByDescending<ScoredMove> { it.captures }
            .thenByDescending { it.cover }
        return cards
            .flatMap { card -> positions.map { position -> evaluate(state, card, position) } }
            .sortedWith(ranking)
    }

    /**
     * The move to play, or `null` when the match is over.
     *
     * Two branches, as in the original:
     *
     * - **something captures.** Pick at random among the placements that capture the most
     *   (`:221-230`), narrowed by cover unless [MatchAiOptions.coverBreaksTies] says otherwise.
     * - **nothing captures.** Then every candidate scores 0 and the list is ordered by cover alone.
     *   From the sixth placement on, take the safest square (`:232-234`). Before that, toss a coin
     *   between the safest and the *most* exposed (`:236-242`) — an even chance of walking into the
     *   worst square on the board. Reproduced: it is the only thing that stops the opening from
     *   being deterministic, and a one-move-lookahead opponent that always plays safe early is both
     *   duller and not much stronger.
     *
     * The original's threshold is `this.turn > 5` on a counter incremented before use, so it is the
     * sixth placement onward — placement index 5 and up. See [TurnOrder].
     *
     * Under [MatchAiOptions.playsWorst] neither branch is taken and the bottom of the same ranking
     * is played instead — see [MatchAiOptions.TUTOR].
     */
    fun choose(state: MatchState, random: Random = Random.Default): ScoredMove? {
        val ranked = candidates(state, random)
        val best = ranked.firstOrNull() ?: return null
        return when {
            options.playsWorst -> ranked.last()
            best.captures > 0 -> bestCapture(ranked, best, random)
            else -> bestPosition(ranked, state.placement, random)
        }
    }

    /** One of the placements capturing the most, at random. */
    private fun bestCapture(
        ranked: List<ScoredMove>,
        best: ScoredMove,
        random: Random,
    ): ScoredMove {
        val tied = ranked.filter {
            it.captures == best.captures && (!options.coverBreaksTies || it.cover == best.cover)
        }
        return tied[random.nextInt(tied.size)]
    }

    /** Nothing captures, so [ranked] is ordered by cover alone and only the extremes are played. */
    private fun bestPosition(
        ranked: List<ScoredMove>,
        placement: Int,
        random: Random,
    ): ScoredMove = when {
        placement >= CAUTIOUS_FROM_PLACEMENT -> ranked.first()
        random.nextBoolean() -> ranked.first()
        else -> ranked.last()
    }

    /** Plays [choose]'s move. Returns [state] unchanged when there is nothing to play. */
    fun play(state: MatchState, random: Random = Random.Default): MatchState {
        val move = choose(state, random) ?: return state
        return state.play(move.card, move.position)
    }

    private companion object {
        /**
         * `if (this.turn > 5)` (`PVEMatchScreen.as:232`), translated out of the AS3's 1-based
         * pre-incremented turn counter: its turn 6 is placement index 5.
         */
        const val CAUTIOUS_FROM_PLACEMENT = 5
    }
}
