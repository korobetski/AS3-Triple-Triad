package com.tripletriad.model

import kotlin.random.Random

/**
 * The rule roulette — `tripleTriadRules.roulette` (`tripleTriadRules.as:36-114`).
 *
 * It **adds** one to three random rules to a rule set rather than generating one from scratch.
 * The only caller passes the opponent's own rules in and reassigns the result
 * (`BaseMatchScreen.as:64-66`):
 *
 * ```actionscript
 * if (RULES.ROULETTE) {
 *     RULES = tripleTriadRules.roulette(Game.PROFILE_DATAS.MODE, RULES);
 * }
 * ```
 *
 * So an opponent that declares `RULE_ROULETTE` plays with everything it already declared *plus*
 * the draw. The roulette can never take a rule away, and eleven of the 85 shipped opponents use
 * it. The signature's `gameRules = null` branch — which would build a default set — is never
 * reached; [augment] covers it anyway, since `augment(GameRules(), …)` is exactly that.
 *
 * See [game-rules.md](../../../../../../../docs/analysis/game-rules.md) § 11.
 */
object Roulette {
    /** `1 + tools.rand(2)` (`:53`), inclusive at both ends. */
    const val MIN_DRAWS: Int = 1
    const val MAX_DRAWS: Int = 3

    /**
     * The rules each collection may draw — `:56` and `:58`, in source order.
     *
     * **These are the legal rule sets per collection, not just roulette candidates.** Same Wall
     * and Elemental are FF8-only; Ascension, Descension, Reverse, Fallen Ace, Order, Chaos and
     * Swap are FF14-only; six are common to both. The shipped opponent data agrees exactly — no
     * `ff14` opponent declares Elemental or Same Wall, and no `ff8` one declares any of the seven
     * — which is what makes this a rule of the game rather than an accident of two array
     * literals. `NpcBundleTest` holds the data to it.
     *
     * Source order is preserved but no longer load-bearing: the AS3 indexes the pool with
     * `tools.rand(length - 1)`, which halves the odds of the first and last entry (§ 15.6), so in
     * both pools **All Open and Three Open were drawn half as often as everything else**. The
     * uniform draw here removes that skew, which does mean generated rule sets will not match the
     * original's distribution.
     */
    val pools: Map<CardCollection, List<String>> = mapOf(
        CardCollection.FF14 to listOf(
            "RULE_ALL_OPEN",
            "RULE_ASCENSION",
            "RULE_CHAOS",
            "RULE_DESCENSION",
            "RULE_FALLEN_ACE",
            "RULE_ORDER",
            "RULE_PLUS",
            "RULE_RANDOM",
            "RULE_REVERSE",
            "RULE_SAME",
            "RULE_SUDDEN_DEATH",
            "RULE_SWAP",
            "RULE_THREE_OPEN",
        ),
        CardCollection.FF8 to listOf(
            "RULE_ALL_OPEN",
            "RULE_ELEMENTAL",
            "RULE_PLUS",
            "RULE_RANDOM",
            "RULE_SAME",
            "RULE_SAME_WALL",
            "RULE_SUDDEN_DEATH",
            "RULE_THREE_OPEN",
        ),
    )

    /** The rules [collection] may be played with, in source order. */
    fun pool(collection: CardCollection): List<String> = pools.getValue(collection)

    /**
     * [rules] plus one to three rules drawn from [collection]'s pool.
     *
     * Drawn **with replacement**, as the original is: the same rule twice yields fewer than three
     * effective additions, and two draws sharing a slot overwrite each other, so `RULE_ORDER` then
     * `RULE_CHAOS` leaves only Chaos. Both follow from [GameRules.withRuleKey] and neither is
     * worked around — the number of *draws* is what the original randomises, not the number of new
     * rules.
     *
     * [GameRules.roulette] is set on the result. The AS3 only sets it on the branch that builds a
     * fresh rule set, because the live path had it set already — that is what gated the call. It
     * matters because `Save.DATAS.RULES_W['RULE_ROULETTE']` is what the Wheel of Fortune
     * achievements count ([AchievementCatalog]), so a set that went through the roulette has to say
     * so or a win under it goes uncounted.
     */
    fun augment(
        rules: GameRules,
        collection: CardCollection,
        random: Random = Random.Default,
    ): GameRules {
        val pool = pool(collection)
        val draws = random.nextInt(MIN_DRAWS, MAX_DRAWS + 1)
        var result = rules.copy(roulette = true)
        repeat(draws) {
            result = result.withRuleKey(pool[random.nextInt(pool.size)])
        }
        return result
    }
}
