package com.tripletriad.model

/** The four edges of a card, and the direction of a neighbour. */
enum class Side {
    TOP,
    RIGHT,
    BOTTOM,
    LEFT,
    ;

    /** The side of the neighbour that faces this one. */
    fun facing(): Side = when (this) {
        TOP -> BOTTOM
        RIGHT -> LEFT
        BOTTOM -> TOP
        LEFT -> RIGHT
    }
}

/**
 * A placed card together with its board position.
 *
 * The AS3 equivalent is `tto.display.Tile`, a Starling display object that holds
 * both the card and the *effective* powers after modifiers. Here the tile holds
 * only the card and the powers are derived — see [effectivePower]. That is the
 * central difference from the original, where three separate code paths write
 * `tile.topPow` and one of them may double-apply
 * ([game-rules.md](../../../../../../../docs/analysis/game-rules.md) § 15.8).
 */
data class PlacedCard(val card: Card, val owner: CardColor)

/**
 * The 3×3 board.
 *
 * `Board.as` hard-codes `i < 9` in every loop (`:55`, `:70`), and `Tile` reaches
 * its neighbours through `topTile`/`rightTile`/`bottomTile`/`leftTile` references
 * where a **null reference means a wall** (`Tile.as:189-194`). Positions here are
 * indices 0..8 in row-major order, matching `Board.tiles`:
 *
 * ```
 * 0 1 2
 * 3 4 5
 * 6 7 8
 * ```
 *
 * Immutable: [place] returns a new board. The original mutates display objects in
 * place, which is why its rules engine cannot evaluate a hypothetical move without
 * corrupting the real one ([data-flow.md](../../../../../../../docs/analysis/data-flow.md) § 4.3).
 */
data class Board(
    val cells: List<PlacedCard?> = List(SIZE) { null },
    /**
     * Per-cell element for the Elemental rule, or `null` for no element.
     *
     * `Board.elements()` (`:52-65`) assigns one to each of the 9 tiles
     * independently with probability ≈½, drawn from the eight FF8 elements. Tiles
     * never assigned keep the sentinel `"none"`, which is `null` here.
     */
    val elements: List<CardType?> = List(SIZE) { null },
) {
    init {
        require(cells.size == SIZE) { "board must have $SIZE cells, had ${cells.size}" }
        require(elements.size == SIZE) { "board must have $SIZE elements, had ${elements.size}" }
    }

    operator fun get(position: Int): PlacedCard? = cells[position]

    fun isEmpty(position: Int): Boolean = cells[position] == null

    val isFull: Boolean get() = cells.all { it != null }

    val placedCount: Int get() = cells.count { it != null }

    fun emptyPositions(): List<Int> = cells.indices.filter { cells[it] == null }

    /** Returns a new board with [card] owned by [owner] at [position]. */
    fun place(position: Int, card: Card, owner: CardColor): Board {
        require(position in 0 until SIZE) { "position must be in 0..${SIZE - 1}, was $position" }
        require(isEmpty(position)) { "cell $position is already taken" }
        return copy(cells = cells.toMutableList().also { it[position] = PlacedCard(card, owner) })
    }

    /** Returns a new board with the cards at [positions] flipped to [owner]. */
    fun capture(positions: Collection<Int>, owner: CardColor): Board {
        if (positions.isEmpty()) return this
        val next = cells.toMutableList()
        for (position in positions) {
            val occupant = next[position] ?: continue
            next[position] = occupant.copy(owner = owner)
        }
        return copy(cells = next)
    }

    /**
     * The position of the neighbour on [side], or `null` if that side is a wall.
     *
     * Row and column arithmetic replaces the original's explicit neighbour
     * references. The edge checks are what make walls detectable, which Same Wall
     * depends on.
     */
    fun neighbour(position: Int, side: Side): Int? {
        val row = position / WIDTH
        val column = position % WIDTH
        return when (side) {
            Side.TOP -> if (row == 0) null else position - WIDTH
            Side.BOTTOM -> if (row == WIDTH - 1) null else position + WIDTH
            Side.LEFT -> if (column == 0) null else position - 1
            Side.RIGHT -> if (column == WIDTH - 1) null else position + 1
        }
    }

    companion object {
        const val WIDTH = 3
        const val SIZE = WIDTH * WIDTH
    }
}

/** The raw printed power of [side], 1..10. Immutable card data. */
fun Card.power(side: Side): Int = when (side) {
    Side.TOP -> top
    Side.RIGHT -> right
    Side.BOTTOM -> bottom
    Side.LEFT -> left
}
