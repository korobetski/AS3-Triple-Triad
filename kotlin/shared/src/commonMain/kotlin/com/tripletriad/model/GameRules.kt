package com.tripletriad.model

/**
 * How much of each hand the opponent may see.
 *
 * `RULE_DEFAULT_OPEN` / `RULE_ALL_OPEN` / `RULE_THREE_OPEN` in
 * `sources/src/tto/datas/tripleTriadRules.as:10-12`.
 *
 * This is presentation only — `BaseMatchScreen.openPhase` (`:156-178`) sets a
 * visibility flag and nothing else. Note the original always reveals the *local*
 * player's own hand regardless of the rule (`:172` and `:176` both assign
 * `RULE_ALL_OPEN` to `bluePlayer`), so Open is only ever about the opponent.
 */
enum class OpenRule { NONE, ALL_OPEN, THREE_OPEN }

/**
 * Whether the player chooses which card to play.
 *
 * `RULE_DEFAULT_ORDER` / `RULE_ORDER` / `RULE_CHAOS`
 * (`tripleTriadRules.as:15-17`), enforced at turn start rather than up front:
 * [ORDER] forces the first remaining card, [CHAOS] a random one — see
 * `BaseMatchScreen.as:388-393` and `:427`.
 */
enum class OrderRule { FREE, ORDER, CHAOS }

/**
 * The `TYPE_RULE` slot: at most one of these can be active.
 *
 * `RULE_DEFAULT_TYPE` / `RULE_ASCENSION` / `RULE_DESCENSION` / `RULE_ELEMENTAL`
 * (`tripleTriadRules.as:25-28`). Modelling this as an enum rather than three
 * booleans is not a simplification — the AS3 rules object has a single
 * `TYPE_RULE` field, so Ascension and Elemental are mutually exclusive by
 * construction and a flat flag set would allow a state the original cannot reach.
 */
enum class TypeRule { NONE, ASCENSION, DESCENSION, ELEMENTAL }

/**
 * The active rule set for a match.
 *
 * Mirrors the object built by `tripleTriadRules.roulette` (`:38-51`): **three
 * enumerations and nine booleans**, twelve slots in total. The 20 constants in
 * `tripleTriadRules.as:9-30` do not map one-to-one onto rules — two are i18n keys
 * for UI section headings (`RULE_OPEN`, `RULE_TYPE`) and one is dead
 * ([combo][comboEnabled]).
 *
 * See [docs/analysis/game-rules.md](../../../../../../../docs/analysis/game-rules.md) § 2.
 */
data class GameRules(
    val open: OpenRule = OpenRule.NONE,
    val order: OrderRule = OrderRule.FREE,
    val typeRule: TypeRule = TypeRule.NONE,
    val suddenDeath: Boolean = false,
    val random: Boolean = false,
    val reverse: Boolean = false,
    val fallenAce: Boolean = false,
    val same: Boolean = false,
    val sameWall: Boolean = false,
    val plus: Boolean = false,
    val swap: Boolean = false,
    val roulette: Boolean = false,
) {
    /**
     * Whether any rule that triggers a special capture is active.
     *
     * `TTOCore.animate` (`:100-101`) branches on exactly this: when true it calls
     * `specialRule`, which performs the basic comparison itself; when false it
     * calls `basicRule`. The two are never combined.
     */
    val hasSpecialRule: Boolean get() = same || sameWall || plus

    /**
     * **Always true.** Combo is not a rule in the original.
     *
     * `RULE_COMBO` (`tripleTriadRules.as:23`) is a dead constant: it appears
     * nowhere except its own declaration and one help-screen entry
     * (`HelpScreen.as:83`). No `_RULES.COMBO` is ever written or read, and
     * `roulette()` has no case for it. Combo therefore fires unconditionally
     * whenever Same, Same Wall or Plus captures a card.
     *
     * Kept as a named property so the fact is discoverable at the call site rather
     * than implied by its absence.
     */
    val comboEnabled: Boolean get() = true
}
