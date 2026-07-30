package com.tripletriad.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Which side owns a card.
 *
 * The AS3 original stores this as a String on `tto.display.Card._color`
 * (`"GREY"` / `"BLUE"` / `"RED"`) and looks the background colour up by name via
 * `Card[_color + '_COLOR']`; see `sources/src/tto/display/Card.as:336`. `GREY` is
 * the unowned state used in the collection and deck-builder screens, which this
 * PoC does not have, so only the two playing sides are modelled here.
 */
enum class CardColor {
    BLUE,
    RED,
    ;

    fun opposite(): CardColor = if (this == BLUE) RED else BLUE
}

/**
 * The card's `type` field.
 *
 * One AS3 field, two meanings, depending on which collection the card belongs to:
 *
 * - the **ff14_** collection uses four FFXIV tribes ([BEAST], [GARLEAN],
 *   [PRIMALS], [SCIONS]), which drive `RULE_TYPE`;
 * - the **ff8_** collection uses the eight FF8 elements, which drive
 *   `RULE_ELEMENTAL`.
 *
 * Both are compared against `tile.element` by the same two lines of
 * `TTOCore.as:48-49`, so they share one field in the data and one enum here. The
 * serial names are the raw AS3 strings, which are also the texture names
 * (`type-beast`, `type-fire`, … in `sources/assets/card_types/`).
 */
@Serializable
enum class CardType {
    @SerialName("beast")
    BEAST,

    @SerialName("garlean")
    GARLEAN,

    @SerialName("primals")
    PRIMALS,

    @SerialName("scions")
    SCIONS,

    @SerialName("earth")
    EARTH,

    @SerialName("fire")
    FIRE,

    @SerialName("holy")
    HOLY,

    @SerialName("ice")
    ICE,

    @SerialName("lightning")
    LIGHTNING,

    @SerialName("poison")
    POISON,

    @SerialName("water")
    WATER,

    @SerialName("wind")
    WIND,
}

/**
 * Which of the two card tables a card belongs to, and which a profile plays with.
 *
 * The serial names are the raw AS3 strings, trailing underscore included, because they are three
 * things at once: the texture-name prefix (`ff14_card_62`), the key `cards.as` selects a table with
 * (`cards[MODE.toUpperCase() + "DATAS"]`), and the value stored as `Save.DATAS.MODE`. A save
 * written by the original must still parse.
 *
 * [Card.collection] remains a `String` rather than this enum: it is populated by
 * `tools/extract_cards.py` straight from the AS3 texture prefix, and `CardCatalog` keys its two
 * lists by the same string. [prefix] is the bridge, and [forPrefix] the way back.
 */
@Serializable
enum class CardCollection(val prefix: String) {
    @SerialName("ff14_")
    FF14("ff14_"),

    @SerialName("ff8_")
    FF8("ff8_"),
    ;

    companion object {
        /** The collection for a `"ff14_"`-style prefix, or null if it names neither. */
        fun forPrefix(prefix: String): CardCollection? = entries.firstOrNull { it.prefix == prefix }
    }
}

/**
 * A Triple Triad card.
 *
 * Field-for-field the AS3 record in `sources/src/tto/datas/cards.as`, which stores
 * each card as `{name, power, rarity, type}` and identifies it by its **index in
 * that array** — the same integer used by `Card.draw()`, `CardItem` and the save
 * file. That index is [id] here; there is no separate key.
 *
 * [top], [right], [bottom], [left] are `power[0..3]`, in that order, as stated by
 * the comment in `CardDigits.display()`. Note the AS3 reads each element with
 * `uint("0x" + power[i])` (`Card.as:316-330`) — a *hexadecimal* parse, which is how
 * the literal `'A'` in the data means 10.
 */
@Serializable
data class Card(
    val id: Int,
    /** `ff14_` or `ff8_`, matching the AS3 texture-name prefix. */
    val collection: String,
    /** The i18n key, e.g. `STR_FF14_CARD_1`. Kept so the real name is derivable. */
    val nameKey: String,
    /** The `en_US` name, resolved at extraction time from `datas/locales`. */
    val name: String,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val left: Int,
    /** Star count, 1..5. Drawn as the `{rarity}stars` texture at (9, 6). */
    val rarity: Int,
    val type: CardType? = null,
    val owner: CardColor = CardColor.BLUE,
) {
    init {
        require(id > 0) { "card id must be positive, was $id" }
        require(rarity in RARITY_RANGE) { "rarity must be in $RARITY_RANGE, was $rarity" }
        val sides = listOf(
            "top" to top,
            "right" to right,
            "bottom" to bottom,
            "left" to left,
        )
        for ((side, power) in sides) {
            require(power in POWER_RANGE) { "$side power must be in $POWER_RANGE, was $power" }
        }
    }

    /** Returns the same card owned by the other side — what a capture produces. */
    fun captured(): Card = copy(owner = owner.opposite())

    companion object {
        /**
         * Every edge power is one hex digit, 1..A. There is no `cd0` in play (the
         * texture exists but no card has a 0) and no value above A.
         */
        val POWER_RANGE = 1..ACE_POWER

        /** Star counts run 1..5; `{rarity}stars` textures exist for exactly those. */
        val RARITY_RANGE = 1..5
    }
}

/** The highest edge power, written `'A'` in `cards.as` and drawn with the `cdA` texture. */
const val ACE_POWER: Int = 10

/**
 * Triple Triad renders a power of 10 as "A" (ace). The AS3 original does this in
 * `CardDigits.as` by picking the `cdA` texture instead of `cd10`, which does not
 * exist — see `sources/assets/digits/digits.xml`.
 */
fun powerLabel(power: Int): String = if (power >= ACE_POWER) "A" else power.toString()
