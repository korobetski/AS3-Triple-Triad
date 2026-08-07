package com.tripletriad.model

/** Cards per hand. Hard-coded `i < 5` throughout `playerPanel.as` (`:155`, `:250`). */
const val HAND_SIZE: Int = 5

/** Placements in a full match — one per board cell. */
const val PLACEMENTS_PER_MATCH: Int = Board.SIZE

/** Total cards in play, and therefore the sum of the two scores, always. */
const val TOTAL_CARDS: Int = HAND_SIZE * 2

/**
 * Who plays each placement.
 *
 * The AS3 builds a **10-entry** array of colour strings up front
 * (`BaseMatchScreen.as:242`) and indexes it with a `turn` counter that is
 * incremented *before* it is read (`:368-373`). `turn` starts at 0 and the first
 * `nextTurn` places nothing, so `timeline[0]` is never used — it exists only to make
 * the parity work out, and the array is effectively 1-indexed.
 *
 * Modelled here as 9 placements indexed 0..8 with the colour derived from parity.
 * That is behaviour-preserving and deliberate: reading a 10-entry array with a
 * 1-based counter is exactly the kind of detail that gets "fixed" by accident and
 * silently swaps who moves first
 * ([game-rules.md](../../../../../../../docs/analysis/game-rules.md) § 4).
 *
 * Consequence, unchanged from the original: **[first] places 5 cards and the other
 * places 4**, so the second player ends with one card still in hand. That card can
 * never be captured and still counts for its owner, which is the standard Triple
 * Triad asymmetry in favour of moving second.
 */
data class TurnOrder(val first: CardColor) {
    fun colorAt(placement: Int): CardColor {
        require(placement in 0 until PLACEMENTS_PER_MATCH) {
            "placement must be in 0..${PLACEMENTS_PER_MATCH - 1}, was $placement"
        }
        return if (placement % 2 == 0) first else first.opposite()
    }

    /** How many cards [color] places over a full match: 5 for [first], 4 for the other. */
    fun placementsFor(color: CardColor): Int =
        (0 until PLACEMENTS_PER_MATCH).count { colorAt(it) == color }
}

/** The board score. [blue] + [red] is always [TOTAL_CARDS]. */
data class MatchScore(val blue: Int, val red: Int) {
    val isDraw: Boolean get() = blue == red

    /** The winning side, or `null` on a draw. */
    fun winner(): CardColor? = when {
        blue > red -> CardColor.BLUE
        red > blue -> CardColor.RED
        else -> null
    }

    operator fun get(color: CardColor): Int =
        if (color == CardColor.BLUE) blue else red
}

/**
 * Counts cards by colour across **both hands**, played or not.
 *
 * `playerPanel.getScores()` (`:239-245`) tallies the colours of that panel's five
 * cards and `updateScores` (`BaseMatchScreen.as:315-321`) sums the two panels.
 * Nothing looks at the board and nothing caches the result — it is a pure function
 * of card ownership, recomputed every turn.
 *
 * So **unplayed cards count for their owner**, the total is always 10, and a draw is
 * 5-5. This is the one part of the original's match logic that ports unchanged.
 *
 * @param unplayed how many cards each side still holds. Those keep their own colour.
 */
fun score(board: Board, unplayed: Map<CardColor, Int> = emptyMap()): MatchScore {
    var blue = unplayed[CardColor.BLUE] ?: 0
    var red = unplayed[CardColor.RED] ?: 0
    for (cell in board.cells) {
        when (cell?.owner) {
            CardColor.BLUE -> blue++
            CardColor.RED -> red++
            null -> Unit
        }
    }
    return MatchScore(blue, red)
}

/**
 * How many cards a side still holds once [board] has been played into, assuming a
 * full hand of [HAND_SIZE] and that every placement came from the placing side.
 */
fun unplayedCounts(board: Board, order: TurnOrder): Map<CardColor, Int> {
    val placed = board.placedCount
    return CardColor.entries.associateWith { color ->
        val played = (0 until placed).count { order.colorAt(it) == color }
        HAND_SIZE - played
    }
}
