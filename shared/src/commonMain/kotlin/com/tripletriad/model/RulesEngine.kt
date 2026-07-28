package com.tripletriad.model

/** Why a card was flipped. Drives which animation plays, and combo propagation. */
enum class CaptureKind {
    /** Won on raw power. `type:'ZZ'` in the AS3 result objects. */
    BASIC,

    /** Two neighbours matched the placed card's facing powers exactly. */
    SAME,

    /** One neighbour matched, and a wall counted as an ace. */
    SAME_WALL,

    /** Two neighbours produced the same sum with the placed card. */
    PLUS,

    /** Propagated from a special capture. Never triggered by [BASIC]. */
    COMBO,
}

/**
 * Which capture kind wins when a card qualifies under more than one.
 *
 * The AS3 does this with `cardToFlip.sortOn(['type'])` (`TTOCore.as:304`) followed
 * by a first-wins de-duplication in `animate` (`:112`). Sorting *alphabetically*
 * over the literals `PLUS`, `SAME`, `SAME_WALL` and `ZZ` yields this order, which
 * is why the basic case is tagged `'ZZ'` — a string chosen to sort last.
 *
 * Reproduced as an explicit list because an alphabetical accident is not a rule
 * anyone should have to re-derive.
 */
private val CAPTURE_PRECEDENCE = listOf(
    CaptureKind.PLUS,
    CaptureKind.SAME,
    CaptureKind.SAME_WALL,
    CaptureKind.BASIC,
    CaptureKind.COMBO,
)

/** Which powers Same and Plus compare. See [RulesEngineOptions]. */
enum class SpecialPowerBasis {
    /** Printed card values, ignoring Elemental and Ascension. What the AS3 does. */
    PRINTED,

    /** Effective values after modifiers. What FF14 does. */
    EFFECTIVE,
}

/**
 * The two places where this engine deliberately departs from the AS3 source.
 *
 * Both are recorded in
 * [game-rules.md](../../../../../../../docs/analysis/game-rules.md) § 15, and both
 * change the outcome of real games, so neither is a silent choice. Defaults are the
 * *corrected* behaviour, not the original: the AIR client is abandoned and
 * unrunnable, so bit-for-bit fidelity is unverifiable anyway, while FF14 remains a
 * reference anyone can check by playing it.
 */
data class RulesEngineOptions(
    /**
     * § 15.4. `TTOCore.specialRule` computes its differences and sums from raw card
     * powers (`:217-251`) while `basicRule` uses modified tile powers — so under
     * Elemental or Ascension the two disagree, and a card whose effective power is 6
     * is treated as its printed 5 for Same and Plus. The author's own comment at
     * `:215` reads as uncertainty rather than a decision. FF14 uses the modified
     * values.
     *
     * Set to [SpecialPowerBasis.PRINTED] to reproduce the original exactly.
     */
    val specialPowerBasis: SpecialPowerBasis = SpecialPowerBasis.EFFECTIVE,
    /**
     * § 15.2. `TTOCore.as:261` gates Same, Plus **and Same Wall** behind
     * `same.length > 1` — at least two occupied neighbours. Same and Plus need two
     * matches so the gate is right for them, but Same Wall exists precisely so a
     * wall can be the second match, and the gate makes it inoperative in exactly the
     * board states it is for.
     *
     * Set to `false` to reproduce the original's behaviour.
     */
    val sameWallNeedsOnlyOneNeighbour: Boolean = true,
) {
    companion object {
        /** Reproduces the AS3 behaviour, defects included. For comparison tests. */
        val FAITHFUL = RulesEngineOptions(
            specialPowerBasis = SpecialPowerBasis.PRINTED,
            sameWallNeedsOnlyOneNeighbour = false,
        )
    }
}

/** One flipped card. [wave] is 0 for the direct captures, 1+ for combo generations. */
data class Capture(val position: Int, val kind: CaptureKind, val wave: Int)

/** The outcome of one placement: the resulting board and every card it flipped. */
data class Resolution(val board: Board, val captures: List<Capture>) {
    val capturedPositions: List<Int> get() = captures.map { it.position }
}

/**
 * Resolves a single placement — the whole of `TTOCore.applyRules` → `animate`, as a
 * pure function.
 *
 * The original mutates display objects and defers the result through `setTimeout`
 * chains, which is why it cannot be unit-tested and why its AI dry run corrupts the
 * board it is evaluating
 * ([data-flow.md](../../../../../../../docs/analysis/data-flow.md) § 1.1, § 4.3).
 * Here the placement, the captures and the resulting board are one value.
 *
 * @param tally the Ascension/Descension state *before* this placement. The tally is
 *   updated by the caller after resolution, mirroring `ascensionPhase` running after
 *   the flips (`TTOCore.as:171`).
 */
class RulesEngine(
    private val rules: GameRules = GameRules(),
    private val options: RulesEngineOptions = RulesEngineOptions(),
) {
    fun resolve(
        board: Board,
        position: Int,
        card: Card,
        player: CardColor,
        tally: AscensionTally = AscensionTally.EMPTY,
    ): Resolution {
        val placed = board.place(position, card, player)
        val context = Context(placed, position, card, player, tally)

        val direct =
            if (rules.hasSpecialRule) specialCaptures(context) else basicCaptures(context)
        val deduped = dedupe(direct)
        // Combo chains off Same, Same Wall and Plus only — never off a basic capture.
        val hasSpecialCapture = deduped.any { it.kind != CaptureKind.BASIC }
        val combos = if (hasSpecialCapture) propagate(context, deduped) else emptyList()

        val all = deduped + combos
        return Resolution(placed.capture(all.map { it.position }, player), all)
    }

    /** Everything the capture rules need, gathered once. */
    private inner class Context(
        val board: Board,
        val position: Int,
        val card: Card,
        val player: CardColor,
        val tally: AscensionTally,
    ) {
        fun element(at: Int): CardType? = board.elements[at]

        fun effective(at: Int, of: Card, side: Side): Int =
            effectivePower(of, side, rules, element(at), tally)

        /** The placed card's effective power on [side]. */
        fun mine(side: Side): Int = effective(position, card, side)

        /** The neighbour on [side], if the cell exists and is occupied. */
        fun neighbourAt(side: Side): Neighbour? {
            val at = board.neighbour(position, side) ?: return null
            return board[at]?.let { Neighbour(at, side, it) }
        }

        fun neighbours(): List<Neighbour> = Side.entries.mapNotNull { neighbourAt(it) }
    }

    private data class Neighbour(val position: Int, val side: Side, val occupant: PlacedCard)

    /**
     * `TTOCore.basicRule` (`:174-206`). Capture an enemy neighbour whose facing
     * power loses the comparison.
     *
     * **Both comparisons are strict**, so a tie never captures — normally or under
     * Reverse. Reverse is therefore *not* a logical negation of the normal rule, and
     * the obvious `if (reverse) a > b else a <= b` refactor is wrong
     * ([game-rules.md](../../../../../../../docs/analysis/game-rules.md) § 15.9).
     */
    private fun basicCaptures(context: Context): List<Capture> =
        context.neighbours()
            .filter { it.occupant.owner != context.player }
            .filter { beats(context, it) }
            .map { Capture(it.position, CaptureKind.BASIC, wave = 0) }

    private fun beats(context: Context, neighbour: Neighbour): Boolean {
        val attack = context.mine(neighbour.side)
        val defence = context.effective(
            neighbour.position,
            neighbour.occupant.card,
            neighbour.side.facing(),
        )
        return if (rules.reverse) defence > attack else defence < attack
    }

    /**
     * `TTOCore.specialRule` (`:211-306`). Performs the basic comparison itself, then
     * adds Same, Plus and Same Wall on top — the original never runs both paths.
     */
    private fun specialCaptures(context: Context): List<Capture> {
        val neighbours = context.neighbours()
        val basic = basicCaptures(context)
        if (neighbours.isEmpty()) return basic

        val enemy = { n: Neighbour -> n.occupant.owner != context.player }
        val special = mutableListOf<Capture>()

        if (neighbours.size >= PAIR) {
            special += sameCaptures(context, neighbours, enemy)
            special += plusCaptures(context, neighbours, enemy)
        }
        special += sameWallCaptures(context, neighbours, enemy)

        return special + basic
    }

    /** Difference used by Same: 0 means the facing powers are equal. */
    private fun sameValue(context: Context, neighbour: Neighbour): Int =
        facing(context, neighbour) - own(context, neighbour)

    /** Sum used by Plus. */
    private fun plusValue(context: Context, neighbour: Neighbour): Int =
        facing(context, neighbour) + own(context, neighbour)

    private fun facing(context: Context, neighbour: Neighbour): Int =
        when (options.specialPowerBasis) {
            SpecialPowerBasis.PRINTED -> neighbour.occupant.card.power(neighbour.side.facing())
            SpecialPowerBasis.EFFECTIVE ->
                context.effective(
                    neighbour.position,
                    neighbour.occupant.card,
                    neighbour.side.facing(),
                )
        }

    private fun own(context: Context, neighbour: Neighbour): Int =
        when (options.specialPowerBasis) {
            SpecialPowerBasis.PRINTED -> context.card.power(neighbour.side)
            SpecialPowerBasis.EFFECTIVE -> context.mine(neighbour.side)
        }

    /**
     * `TTOCore.as:271-280`. Two neighbours whose facing powers both equal the placed
     * card's. Each is captured only if it is an enemy, independently — so a Same
     * between one enemy card and one of your own captures just the enemy, and still
     * counts as a Same for combo purposes.
     */
    private fun sameCaptures(
        context: Context,
        neighbours: List<Neighbour>,
        enemy: (Neighbour) -> Boolean,
    ): List<Capture> {
        if (!rules.same) return emptyList()
        val matching = neighbours.filter { sameValue(context, it) == 0 }
        return if (matching.size < PAIR) {
            emptyList()
        } else {
            matching.filter(enemy).map { Capture(it.position, CaptureKind.SAME, wave = 0) }
        }
    }

    /**
     * `TTOCore.as:282-291`. Two neighbours producing equal sums — the individual
     * powers need not match, so 3+7 and 5+5 both give 10 and trigger.
     */
    private fun plusCaptures(
        context: Context,
        neighbours: List<Neighbour>,
        enemy: (Neighbour) -> Boolean,
    ): List<Capture> {
        if (!rules.plus) return emptyList()
        val bySum = neighbours.groupBy { plusValue(context, it) }
        return bySum.values
            .filter { it.size >= PAIR }
            .flatten()
            .filter(enemy)
            .map { Capture(it.position, CaptureKind.PLUS, wave = 0) }
    }

    /**
     * `TTOCore.as:294-300` plus `Tile.onSameWall` (`Tile.as:229-247`).
     *
     * A wall counts as a card showing an ace, so one matching neighbour plus a
     * qualifying wall is logically two "sames". `onSameWall` reads the **tile**
     * power, which means Fallen Ace turns the placed ace into 0 and Same Wall can no
     * longer fire on that side — an interaction that looks intentional.
     *
     * The `>= 2 neighbours` gate is dropped by default; see
     * [RulesEngineOptions.sameWallNeedsOnlyOneNeighbour].
     */
    private fun sameWallCaptures(
        context: Context,
        neighbours: List<Neighbour>,
        enemy: (Neighbour) -> Boolean,
    ): List<Capture> {
        val minimum = if (options.sameWallNeedsOnlyOneNeighbour) 1 else PAIR
        val eligible =
            rules.sameWall && neighbours.size >= minimum && touchesAceWall(context)
        return if (!eligible) {
            emptyList()
        } else {
            neighbours
                .filter { sameValue(context, it) == 0 }
                .filter(enemy)
                .map { Capture(it.position, CaptureKind.SAME_WALL, wave = 0) }
        }
    }

    /** True when some side faces a wall and shows an ace there. */
    private fun touchesAceWall(context: Context): Boolean = Side.entries.any { side ->
        context.board.neighbour(context.position, side) == null &&
            context.mine(side) == ACE_POWER
    }

    /**
     * Combo: breadth-first propagation from the specially-captured cards.
     *
     * `TTOCore.comboRule` (`:308-373`) recurses from each capture and applies the
     * **basic** comparison — respecting Reverse — to that card's own neighbours.
     * Combo never chains off a basic capture, only off Same, Same Wall or Plus.
     *
     * Rewritten rather than ported. The original shares one mutable `enqueue` array
     * across every starting tile and returns it *by reference* as each capture's
     * `waveEffect`, so all captures alias one mutating array; the author's own
     * `// TODO : correct combo` sits at `:310`. The set of captured cards is
     * reproduced faithfully; the wave grouping here is new work
     * ([game-rules.md](../../../../../../../docs/analysis/game-rules.md) § 15.3).
     */
    private fun propagate(context: Context, direct: List<Capture>): List<Capture> {
        val visited = (direct.map { it.position } + context.position).toMutableSet()
        var frontier = direct.filter { it.kind != CaptureKind.BASIC }.map { it.position }
        val result = mutableListOf<Capture>()
        var wave = 1

        while (frontier.isNotEmpty() && wave <= Board.SIZE) {
            val next = mutableListOf<Int>()
            for (from in frontier) {
                for (position in comboTargets(context, from, visited)) {
                    visited += position
                    next += position
                    result += Capture(position, CaptureKind.COMBO, wave)
                }
            }
            frontier = next
            wave++
        }
        return result
    }

    /** Enemy neighbours of [from] that lose to it and have not been captured yet. */
    private fun comboTargets(context: Context, from: Int, visited: Set<Int>): List<Int> {
        val attacker = context.board[from] ?: return emptyList()
        return Side.entries.mapNotNull { side ->
            val at = context.board.neighbour(from, side) ?: return@mapNotNull null
            if (at in visited) return@mapNotNull null
            val target = context.board[at] ?: return@mapNotNull null
            if (target.owner == context.player) return@mapNotNull null
            val attack = context.effective(from, attacker.card, side)
            val defence = context.effective(at, target.card, side.facing())
            val wins = if (rules.reverse) defence > attack else defence < attack
            if (wins) at else null
        }
    }

    /** One capture per position, highest-precedence kind winning. */
    private fun dedupe(captures: List<Capture>): List<Capture> = captures
        .groupBy { it.position }
        .map { (_, candidates) -> candidates.minBy { CAPTURE_PRECEDENCE.indexOf(it.kind) } }
        .sortedBy { it.position }

    private companion object {
        /** Same and Plus both need two matching neighbours. */
        const val PAIR = 2
    }
}
