package com.tripletriad.model

/**
 * The lowest power a card can *show* once modifiers apply.
 *
 * Card data is 1..10 ([Card.POWER_RANGE]) but effective power is **0..10**: the AS3
 * clamp is `Math.min(10, Math.max(0, value))` (`tools.as:74-76`), Fallen Ace
 * produces 0 directly, and Descension can drive a 1 down to 0. Confusing the two
 * ranges is the easiest way to get this wrong.
 */
const val MIN_EFFECTIVE_POWER: Int = 0

/** Clamps to 0..10. The AS3 `tools.madmax`. */
fun clampPower(value: Int): Int = value.coerceIn(MIN_EFFECTIVE_POWER, ACE_POWER)

/**
 * The per-type tally that drives Ascension and Descension.
 *
 * `BaseMatchScreen.ascensionByType` (`:350`) is a board-wide counter incremented
 * (Ascension) or decremented (Descension) each time a **typed** card is placed
 * (`:339`, `:346`). Every card of that type then gains the tally as a modifier —
 * including cards still in hand, because `playerPanel.applyAscension` (`:153-178`)
 * walks all five cards and only touches tile powers when the card is on the board.
 */
data class AscensionTally(val counts: Map<CardType, Int> = emptyMap()) {
    operator fun get(type: CardType?): Int = if (type == null) 0 else counts[type] ?: 0

    /** Records a placement of [type] under [rule]. Untyped cards change nothing. */
    fun record(type: CardType?, rule: TypeRule): AscensionTally {
        val delta = when (rule) {
            TypeRule.ASCENSION -> 1
            TypeRule.DESCENSION -> -1
            else -> 0
        }
        return if (type == null || delta == 0) {
            this
        } else {
            AscensionTally(counts + (type to this[type] + delta))
        }
    }

    companion object {
        val EMPTY = AscensionTally()
    }
}

/**
 * The modifier the Elemental rule applies to a card sitting on [element].
 *
 * `TTOCore.as:47-56`, exactly:
 *
 * | condition | modifier |
 * |---|---|
 * | `card.type == tile.element` | +1 |
 * | tile has an element and the types differ | −1 |
 * | otherwise | 0 |
 *
 * **An untyped card takes −1 on any elemental tile.** It fails the first test and
 * passes the second, so it is penalised — and that is deliberate, not an accident of
 * the AS3 code: a card with no element is levelled down on an elemental tile exactly
 * like a card of the wrong element. Only a match gains +1. Confirmed against the
 * intended ruleset, see
 * [game-rules.md](../../../../../../../docs/analysis/game-rules.md) § 15.5.
 */
fun elementalModifier(type: CardType?, element: CardType?): Int = when {
    element == null -> 0
    type == element -> 1
    else -> -1
}

/**
 * The power a placed card actually fights with on [side].
 *
 * Order of operations is taken from `TTOCore.applyRules:27-56` and matters:
 *
 * 1. start from the printed power;
 * 2. Fallen Ace turns a 10 into 0 — **before** any modifier, so an ace on a +1
 *    Ascension board reads 1, not 0;
 * 3. add the single active [TypeRule] modifier;
 * 4. clamp to 0..10.
 *
 * Unlike the original, this is a pure function rather than mutable state on a
 * display object. That removes the three-independent-writers hazard described in
 * [game-rules.md](../../../../../../../docs/analysis/game-rules.md) § 15.8.
 */
fun effectivePower(
    card: Card,
    side: Side,
    rules: GameRules,
    element: CardType? = null,
    tally: AscensionTally = AscensionTally.EMPTY,
): Int {
    val printed = card.power(side)
    val base = if (rules.fallenAce && printed == ACE_POWER) MIN_EFFECTIVE_POWER else printed
    val modifier = when (rules.typeRule) {
        TypeRule.NONE -> 0
        TypeRule.ASCENSION, TypeRule.DESCENSION -> tally[card.type]
        TypeRule.ELEMENTAL -> elementalModifier(card.type, element)
    }
    return clampPower(base + modifier)
}
