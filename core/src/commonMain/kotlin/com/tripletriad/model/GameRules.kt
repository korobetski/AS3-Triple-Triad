package com.tripletriad.model

import kotlinx.serialization.Serializable

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
 *
 * `@Serializable` so a finished match can be recorded with the rules it was played under
 * ([MatchRecord]). The property names are the wire format here, not the AS3 `SCREAMING_CASE` keys:
 * nothing in the original persisted a rule set — `NPC.gameRules` rebuilt one from a list of rule
 * constants on every match — so there is no on-disk shape to stay compatible with.
 */
@Serializable
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

    /**
     * The AS3 rule constants that are active, as `RULES_W` keys them.
     *
     * `Save.DATAS.RULES_W` counts wins per rule, and the Wheel-of-Fortune achievements read it by
     * constant name (`RULES_W['RULE_ROULETTE']`, `Achievements.as:52-57`). So a win has to be
     * attributed to the *strings* `tripleTriadRules.as` declares, not to this class's properties —
     * which is why the mapping lives here, once, instead of at each call site.
     *
     * The three enum slots contribute their active member only: `RULE_DEFAULT_OPEN`,
     * `RULE_DEFAULT_ORDER` and `RULE_DEFAULT_TYPE` are the *absence* of a rule and are not counted,
     * matching the original, which only ever incremented the named rules.
     *
     * [comboEnabled] contributes nothing: `RULE_COMBO` is a dead constant (see above), and the
     * original never wrote it either.
     */
    fun activeRuleKeys(): List<String> = buildList {
        for ((key, slot) in RuleKeys.slots) {
            if (slot.isActiveIn(this@GameRules)) add(key)
        }
        for ((key, flag) in RuleKeys.flags) {
            if (flag.read(this@GameRules)) add(key)
        }
    }

    /**
     * The rule set with [key] applied, or this one unchanged if it names no rule.
     *
     * The inverse of [activeRuleKeys], used by [Npc.gameRules] to build a rule set from the list of
     * constants an opponent declares. Unknown keys are ignored, matching the AS3 `switch` that has
     * no `default`.
     */
    fun withRuleKey(key: String): GameRules =
        RuleKeys.slots[key]?.applyTo(this)
            ?: RuleKeys.flags[key]?.set(this, true)
            ?: this
}

/**
 * The correspondence between the AS3 rule constants and [GameRules]' twelve slots — **once**.
 *
 * There are two directions to this mapping and the original writes both out longhand:
 * `NPC.gameRules` (`NPC.as:71-127`) is a 16-case `switch` from constant to rule, and each
 * `RULES_W` increment site goes the other way by hand. Two 16-case transcriptions of one table,
 * which is how they get to disagree — and a rule in one but not the other is silently dropped
 * rather than reported.
 *
 * One table here, read in both directions, so a rule cannot be settable but uncountable or vice
 * versa. `NpcTest.everyMappedRuleKeyIsAlsoAWinCounterKey` asserts the round trip for all sixteen.
 *
 * `RULE_DEFAULT_OPEN`, `RULE_DEFAULT_ORDER` and `RULE_DEFAULT_TYPE` are deliberately absent: they
 * are the *absence* of a rule, and neither direction should name them. `RULE_COMBO` is absent
 * because it is dead (see [GameRules.comboEnabled]), and `RULE_OPEN` / `RULE_TYPE` because they are
 * i18n keys for UI headings rather than rules.
 */
internal object RuleKeys {
    /** One of the three mutually exclusive enum slots, at a particular value. */
    class Slot(
        private val read: (GameRules) -> Boolean,
        private val write: (GameRules) -> GameRules,
    ) {
        fun isActiveIn(rules: GameRules): Boolean = read(rules)

        fun applyTo(rules: GameRules): GameRules = write(rules)
    }

    /** One of the nine independent booleans. */
    class Flag(
        val read: (GameRules) -> Boolean,
        private val write: (GameRules, Boolean) -> GameRules,
    ) {
        fun set(rules: GameRules, value: Boolean): GameRules = write(rules, value)
    }

    val slots: Map<String, Slot> = mapOf(
        "RULE_ALL_OPEN" to Slot(
            { it.open == OpenRule.ALL_OPEN },
            { it.copy(open = OpenRule.ALL_OPEN) },
        ),
        "RULE_THREE_OPEN" to Slot(
            { it.open == OpenRule.THREE_OPEN },
            { it.copy(open = OpenRule.THREE_OPEN) },
        ),
        "RULE_ORDER" to Slot({ it.order == OrderRule.ORDER }, { it.copy(order = OrderRule.ORDER) }),
        "RULE_CHAOS" to Slot({ it.order == OrderRule.CHAOS }, { it.copy(order = OrderRule.CHAOS) }),
        "RULE_ASCENSION" to Slot(
            { it.typeRule == TypeRule.ASCENSION },
            { it.copy(typeRule = TypeRule.ASCENSION) },
        ),
        "RULE_DESCENSION" to Slot(
            { it.typeRule == TypeRule.DESCENSION },
            { it.copy(typeRule = TypeRule.DESCENSION) },
        ),
        "RULE_ELEMENTAL" to Slot(
            { it.typeRule == TypeRule.ELEMENTAL },
            { it.copy(typeRule = TypeRule.ELEMENTAL) },
        ),
    )

    val flags: Map<String, Flag> = mapOf(
        "RULE_SUDDEN_DEATH" to Flag(
            { it.suddenDeath },
            { r, v -> r.copy(suddenDeath = v) },
        ),
        "RULE_RANDOM" to Flag({ it.random }, { r, v -> r.copy(random = v) }),
        "RULE_REVERSE" to Flag({ it.reverse }, { r, v -> r.copy(reverse = v) }),
        "RULE_FALLEN_ACE" to Flag({ it.fallenAce }, { r, v -> r.copy(fallenAce = v) }),
        "RULE_SAME" to Flag({ it.same }, { r, v -> r.copy(same = v) }),
        "RULE_SAME_WALL" to Flag({ it.sameWall }, { r, v -> r.copy(sameWall = v) }),
        "RULE_PLUS" to Flag({ it.plus }, { r, v -> r.copy(plus = v) }),
        "RULE_SWAP" to Flag({ it.swap }, { r, v -> r.copy(swap = v) }),
        "RULE_ROULETTE" to Flag({ it.roulette }, { r, v -> r.copy(roulette = v) }),
    )

    /** Every constant the table knows, for a test that wants to sweep them. */
    val all: Set<String> get() = slots.keys + flags.keys
}
