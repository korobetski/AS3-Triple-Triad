package com.tripletriad.model

/**
 * Which side owns a card.
 *
 * The AS3 original stores this as a String on `tto.display.Card._color`
 * (`"blue"` / `"red"`) and derives the card background from it; see
 * `sources/src/tto/display/Card.as`.
 */
enum class CardColor {
    BLUE,
    RED;

    fun opposite(): CardColor = if (this == BLUE) RED else BLUE
}

/**
 * Card element. Only used for display in this PoC — the elemental power
 * modifier belongs to the rules engine (Phase 3).
 */
enum class Element {
    NONE,
    FIRE,
    ICE,
    THUNDER,
    EARTH,
    POISON,
    WIND,
    WATER,
    HOLY,
}

/**
 * Minimum viable card. Powers are the four edge values in AS3 order:
 * top, right, bottom, left — matching `tto.display.CardDigits` which lays them
 * out clockwise from the top of a 36x24 diamond badge.
 */
data class Card(
    val id: Int,
    val name: String,
    val level: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val left: Int,
    val element: Element = Element.NONE,
    val owner: CardColor = CardColor.BLUE,
) {
    init {
        require(id > 0) { "card id must be positive, was $id" }
        require(level in 1..10) { "level must be in 1..10, was $level" }
        for ((side, power) in listOf("top" to top, "right" to right, "bottom" to bottom, "left" to left)) {
            require(power in 1..10) { "$side power must be in 1..10, was $power" }
        }
    }

    /** Returns the same card owned by the other side — what a capture produces. */
    fun captured(): Card = copy(owner = owner.opposite())
}

/**
 * Triple Triad renders a power of 10 as "A" (ace). The AS3 original does this in
 * `CardDigits.as` by swapping the digit texture rather than formatting a string.
 */
fun powerLabel(power: Int): String = if (power >= 10) "A" else power.toString()
