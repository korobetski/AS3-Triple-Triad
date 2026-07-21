package com.tripletriad.poc.data

import kotlinx.serialization.Serializable

/**
 * Represents a Triple Triad card with its properties.
 * Each card has 4 numeric values (top, right, bottom, left) and belongs to an element.
 */
@Serializable
data class Card(
    val id: String,
    val name: String,
    val image: String,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val left: Int,
    val element: Element? = null,
    val rarity: Rarity = Rarity.COMMON
) {
    /**
     * Calculate the total power of the card (sum of all sides).
     */
    val power: Int get() = top + right + bottom + left

    /**
     * Check if this card can capture another card on a specific side.
     */
    fun canCapture(other: Card, side: Side): Boolean {
        return when (side) {
            Side.TOP -> this.top > other.top
            Side.RIGHT -> this.right > other.right
            Side.BOTTOM -> this.bottom > other.bottom
            Side.LEFT -> this.left > other.left
        }
    }

    /**
     * Get the value for a specific side.
     */
    fun getValue(side: Side): Int {
        return when (side) {
            Side.TOP -> top
            Side.RIGHT -> right
            Side.BOTTOM -> bottom
            Side.LEFT -> left
        }
    }

    override fun toString(): String {
        return "$name [$top, $right, $bottom, $left]"
    }
}

/**
 * Card elements that affect gameplay in special rules.
 */
@Serializable
enum class Element {
    FIRE,
    ICE,
    LIGHTNING,
    EARTH,
    WIND,
    WATER,
    LIGHT,
    DARK,
    NONE
}

/**
 * Card rarity levels.
 */
@Serializable
enum class Rarity {
    COMMON,
    UNCOMMON,
    RARE,
    LEGENDARY
}

/**
 * Card sides for direction reference.
 */
enum class Side {
    TOP,
    RIGHT,
    BOTTOM,
    LEFT
}
