package com.tripletriad.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [Roulette] — items 33 to 35 of the
 * [game-rules.md](../../../../../../../docs/analysis/game-rules.md) § 16 matrix.
 *
 * Nothing here asserts a *distribution*. The port draws uniformly where the original halved the
 * odds of the first and last entry in each pool (§ 15.6), so the two do not agree on how often any
 * given rule appears and a test that pinned frequencies would only pin this implementation. What is
 * testable is the pool, the count and the slot behaviour — which is what actually decides whether a
 * match is legal.
 */
class RouletteTest {
    /** Sweeps enough seeds that every reachable outcome shows up at least once. */
    private val seeds = 0 until 200

    /**
     * What a draw added, on top of what went in.
     *
     * `RULE_ROULETTE` is excluded because [Roulette.augment] always sets it — it says the match was
     * played under the roulette, not that the roulette drew it.
     */
    private fun drawn(
        collection: CardCollection,
        seed: Int,
        from: GameRules = GameRules(),
    ): Set<String> =
        Roulette.augment(from, collection, Random(seed)).activeRuleKeys().toSet() -
            from.activeRuleKeys().toSet() - ROULETTE_KEY

    // ---- 33: one to three rules from the right pool -----------------------

    @Test
    fun betweenOneAndThreeRulesAreDrawn() {
        val counts = seeds.map { drawn(CardCollection.FF14, it).size }

        assertEquals(
            setOf(1, 2, 3),
            counts.toSet(),
            "1 + rand(2) draws, each adding at most one rule",
        )
    }

    /**
     * Three *draws* is not three rules. The pool is sampled with replacement, so a repeat and a
     * slot collision both reduce the count — which is why the assertion above allows 1 and 2 rather
     * than expecting a uniform 3.
     */
    @Test
    fun drawsWithReplacementCanYieldFewerRulesThanDraws() {
        val single = seeds.count { drawn(CardCollection.FF8, it).size == 1 }

        assertTrue(single > 0, "some seed must draw the same rule twice or collide on a slot")
    }

    @Test
    fun theRouletteFlagIsSetOnTheResult() {
        for (collection in CardCollection.entries) {
            assertTrue(
                Roulette.augment(GameRules(), collection, Random(1)).roulette,
                "$collection: a win under the roulette has to be countable as one",
            )
        }
    }

    // ---- 34: no cross-collection rule ------------------------------------

    @Test
    fun everyDrawnRuleBelongsToTheCollectionsPool() {
        for (collection in CardCollection.entries) {
            val pool = Roulette.pool(collection).toSet()
            for (seed in seeds) {
                val added = drawn(collection, seed)

                assertTrue(
                    added.all { it in pool },
                    "$collection seed $seed drew ${added - pool}",
                )
            }
        }
    }

    /**
     * The two pools are the legal rule sets per collection, so the asymmetry is the assertion.
     * `NpcBundleTest` holds the shipped opponents to the same split.
     */
    @Test
    fun sameWallAndElementalAreFf8Only() {
        val ff14 = Roulette.pool(CardCollection.FF14)
        val ff8 = Roulette.pool(CardCollection.FF8)

        assertFalse("RULE_SAME_WALL" in ff14)
        assertFalse("RULE_ELEMENTAL" in ff14)
        assertTrue("RULE_SAME_WALL" in ff8)
        assertTrue("RULE_ELEMENTAL" in ff8)
    }

    @Test
    fun theSevenTypeAndOrderRulesAreFf14Only() {
        val ff8 = Roulette.pool(CardCollection.FF8)

        for (key in listOf(
            "RULE_ASCENSION",
            "RULE_DESCENSION",
            "RULE_REVERSE",
            "RULE_FALLEN_ACE",
            "RULE_ORDER",
            "RULE_CHAOS",
            "RULE_SWAP",
        )) {
            assertFalse(key in ff8, "$key must not be drawable in ff8_")
            assertTrue(key in Roulette.pool(CardCollection.FF14), key)
        }
    }

    @Test
    fun bothPoolsAreExactlyTheAs3Arrays() {
        assertEquals(13, Roulette.pool(CardCollection.FF14).size, "tripleTriadRules.as:56")
        assertEquals(8, Roulette.pool(CardCollection.FF8).size, "tripleTriadRules.as:58")
    }

    /** Six rules are shared, and they are the ones neither collection reserves. */
    @Test
    fun sixRulesAreCommonToBothPools() {
        val common = Roulette.pool(CardCollection.FF14)
            .intersect(Roulette.pool(CardCollection.FF8).toSet())

        assertEquals(
            setOf(
                "RULE_ALL_OPEN",
                "RULE_THREE_OPEN",
                "RULE_PLUS",
                "RULE_RANDOM",
                "RULE_SAME",
                "RULE_SUDDEN_DEATH",
            ),
            common,
        )
    }

    @Test
    fun everyPooledRuleIsOneTheRuleTableKnows() {
        for (collection in CardCollection.entries) {
            for (key in Roulette.pool(collection)) {
                assertTrue(key in RuleKeys.all, "$key is not a rule GameRules can apply")
            }
        }
    }

    // ---- 35: rules sharing a slot overwrite ------------------------------

    @Test
    fun twoRulesInOneSlotOverwriteRatherThanAccumulate() {
        val rules = GameRules()
            .withRuleKey("RULE_ORDER")
            .withRuleKey("RULE_CHAOS")

        assertEquals(OrderRule.CHAOS, rules.order, "the later draw wins")
        assertEquals(listOf("RULE_CHAOS"), rules.activeRuleKeys())
    }

    @Test
    fun independentFlagsAccumulate() {
        val rules = GameRules()
            .withRuleKey("RULE_SAME")
            .withRuleKey("RULE_PLUS")

        assertTrue(rules.same && rules.plus)
    }

    // ---- augmenting rather than generating -------------------------------

    /**
     * The only live call passes the opponent's own rules in (`BaseMatchScreen.as:64-66`), so the
     * roulette can only ever add.
     */
    @Test
    fun theRulesPassedInAreKept() {
        val declared = GameRules(same = true, suddenDeath = true, order = OrderRule.ORDER)

        for (seed in seeds) {
            val result = Roulette.augment(declared, CardCollection.FF14, Random(seed))

            assertTrue(result.same, "seed $seed dropped Same")
            assertTrue(result.suddenDeath, "seed $seed dropped Sudden Death")
            assertTrue(
                result.order != OrderRule.FREE,
                "seed $seed cleared the Order slot; a draw may replace it but not empty it",
            )
        }
    }

    @Test
    fun aDrawIsReproducibleForAGivenSeed() {
        assertEquals(
            Roulette.augment(GameRules(), CardCollection.FF14, Random(9)),
            Roulette.augment(GameRules(), CardCollection.FF14, Random(9)),
        )
    }

    @Test
    fun theDrawCountBoundsAreTheDocumentedOnes() {
        assertEquals(1, Roulette.MIN_DRAWS)
        assertEquals(3, Roulette.MAX_DRAWS)
    }

    private companion object {
        const val ROULETTE_KEY = "RULE_ROULETTE"
    }
}
