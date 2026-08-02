package com.tripletriad.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [Npc], its rule mapping, its reward formulas and its hand builder.
 *
 * The bundled `npcs.json` is covered separately by `NpcBundleTest`, which needs the resource
 * loader; this file uses hand-built opponents so it runs on every target.
 */
class NpcTest {
    /**
     * An opponent with the four things most tests vary. Fees, rewards and availability are set with
     * `.copy()` by the two or three tests that need them, rather than by widening this.
     */
    private fun npc(
        ruleKeys: List<String> = emptyList(),
        fetish: List<Int> = emptyList(),
        pool: List<Int> = emptyList(),
        level: NpcLevel = NpcLevel.NONE,
    ) = Npc(
        id = 1,
        nameKey = "STR_NPC_Test",
        iconId = "test",
        ruleKeys = ruleKeys,
        fetishCards = fetish,
        cards = pool,
        level = level,
    )

    /** `NPC.get gameRules` (`:71-127`): each constant sets one slot or one flag. */
    @Test
    fun ruleKeysMapOntoGameRules() {
        val rules = npc(
            listOf("RULE_ALL_OPEN", "RULE_SAME", "RULE_PLUS", "RULE_ASCENSION"),
        ).gameRules()

        assertEquals(OpenRule.ALL_OPEN, rules.open)
        assertEquals(TypeRule.ASCENSION, rules.typeRule)
        assertTrue(rules.same)
        assertTrue(rules.plus)
        assertEquals(OrderRule.FREE, rules.order, "no order rule listed")
        assertFalse(rules.reverse)
    }

    @Test
    fun anEmptyRuleListIsTheDefaultRuleSet() {
        assertEquals(GameRules(), npc().gameRules())
    }

    /** No `default` case in the AS3 switch, so an unrecognised constant changes nothing. */
    @Test
    fun anUnknownRuleKeyIsIgnored() {
        assertEquals(GameRules(same = true), npc(listOf("RULE_NONSENSE", "RULE_SAME")).gameRules())
    }

    /** The AS3 switch assigns over a previous value, so the last entry for a slot wins. */
    @Test
    fun theLastRuleForASlotWins() {
        val rules = npc(listOf("RULE_ALL_OPEN", "RULE_THREE_OPEN")).gameRules()

        assertEquals(OpenRule.THREE_OPEN, rules.open)
    }

    /**
     * Every key the table maps must round-trip: settable by an NPC *and* countable as a win.
     *
     * This is the assertion that justifies [RuleKeys] existing. The AS3 writes the two directions
     * out separately — a 16-case `switch` in `NPC.gameRules` and a `RULES_W` increment per site —
     * so a rule present in one and absent from the other is silently dropped. Swept over
     * [RuleKeys.all] rather than a hand-written list, so adding a rule to the table without making
     * it work in both directions fails here.
     */
    @Test
    fun everyMappedRuleKeyIsAlsoAWinCounterKey() {
        assertEquals(16, RuleKeys.all.size, "tripleTriadRules.as declares 16 real rules")

        for (key in RuleKeys.all) {
            assertEquals(
                listOf(key),
                npc(listOf(key)).gameRules().activeRuleKeys(),
                "$key does not survive gameRules() -> activeRuleKeys()",
            )
        }
    }

    /** The three `RULE_DEFAULT_*` constants are the absence of a rule, so the table omits them. */
    @Test
    fun theDefaultSlotConstantsAreNotRules() {
        for (key in listOf("RULE_DEFAULT_OPEN", "RULE_DEFAULT_ORDER", "RULE_DEFAULT_TYPE")) {
            assertFalse(key in RuleKeys.all, key)
            assertEquals(GameRules(), GameRules().withRuleKey(key), "$key must change nothing")
        }
    }

    /** `NPC.set level` (`:215`): `{w: 25 + m*2, d: 10 + round(m*1.5), l: 5 + m}`. */
    @Test
    fun xpRewardsFollowTheAs3Formula() {
        assertEquals(XpReward(win = 27, draw = 12, lose = 6), NpcLevel.NOVICE.xpReward)
        assertEquals(XpReward(win = 29, draw = 13, lose = 7), NpcLevel.INITIATE.xpReward)
        assertEquals(XpReward(win = 31, draw = 15, lose = 8), NpcLevel.AVERAGE.xpReward)
        assertEquals(XpReward(win = 33, draw = 16, lose = 9), NpcLevel.ADVANCED.xpReward)
        assertEquals(XpReward(win = 35, draw = 18, lose = 10), NpcLevel.EXPERT.xpReward)
    }

    @Test
    fun levelModifiersAreTheAs3Ones() {
        assertEquals(0, NpcLevel.NONE.modifier)
        assertEquals(5, NpcLevel.EXPERT.modifier)
        assertEquals("STR_NPC_LEVEL_EXPERT", NpcLevel.EXPERT.labelKey)
    }

    /**
     * `NPC.get XPReward` can only return null for an unlevelled NPC, and callers treat that as
     * none.
     */
    @Test
    fun anUnlevelledNpcPaysNoXp() {
        val unlevelled = npc(level = NpcLevel.NONE)

        for (result in MatchResult.entries) {
            assertEquals(0, unlevelled.xpFor(result), result.name)
        }
    }

    @Test
    fun xpForReadsTheRowForTheResult() {
        val expert = npc(level = NpcLevel.EXPERT)

        assertEquals(35, expert.xpFor(MatchResult.WIN))
        assertEquals(18, expert.xpFor(MatchResult.DRAW))
        assertEquals(10, expert.xpFor(MatchResult.LOSE))
    }

    /**
     * **The match fee is not charged**, and an earlier revision of this test asserted that it was.
     *
     * `NPC.matchFee` is declared for all 85 opponents and read by nothing:
     * `PVEMatchScreen.endGame` pays `MGPReward.w + rand(20)` on a win and `MGPReward.l + rand(5)`
     * on a loss and subtracts nothing, so **every result is a net gain**. See [Npc.mgpFor] for why
     * that is reproduced rather than corrected into the entry cost it looks like.
     */
    @Test
    fun theMatchFeeIsNotDeductedFromTheReward() {
        val reward = MgpReward(win = 47, draw = 18, lose = 7)
        val opponent = npc().copy(matchFee = 20, mgpReward = reward)

        assertEquals(47, opponent.mgpFor(MatchResult.WIN))
        assertEquals(18, opponent.mgpFor(MatchResult.DRAW))
        assertEquals(7, opponent.mgpFor(MatchResult.LOSE))
    }

    /** And a fee-free opponent pays exactly the same, which is the point. */
    @Test
    fun theFeeChangesNothingAboutThePayout() {
        val reward = MgpReward(win = 47, draw = 18, lose = 7)
        val free = npc().copy(matchFee = 0, mgpReward = reward)
        val pricey = npc().copy(matchFee = 30, mgpReward = reward)

        for (result in MatchResult.entries) {
            assertEquals(free.mgpFor(result), pricey.mgpFor(result), result.name)
        }
    }

    @Test
    fun aFixedDeckIsReturnedAsIs() {
        val fixed = npc(fetish = listOf(2, 4, 5, 7, 13))

        assertEquals(listOf(2, 4, 5, 7, 13), fixed.randomHand(Random(1)))
    }

    @Test
    fun theHandIsToppedUpFromThePoolWithoutRepeating() {
        val opponent = npc(fetish = listOf(41, 49, 39), pool = listOf(38, 22, 47, 20))

        val hand = opponent.randomHand(Random(7))

        assertEquals(HAND_SIZE, hand.size)
        assertTrue(hand.containsAll(listOf(41, 49, 39)), "fetish cards are always played")
        assertEquals(hand.size, hand.distinct().size, "the pool is drawn without replacement")
        assertTrue(hand.drop(3).all { it in listOf(38, 22, 47, 20) })
    }

    /**
     * `NPC.getRandomCards()` splices out of the pool without checking it is non-empty, so an NPC
     * with fewer than five fetish cards and no pool spins forever. Unreachable with the shipped
     * data — every empty-pool entry has five fetish cards — and reachable by adding one. Here it
     * returns short instead.
     */
    @Test
    fun anUnderfilledHandIsReturnedShortRatherThanLoopingForever() {
        val hand = npc(fetish = listOf(1, 2), pool = emptyList()).randomHand(Random(1))

        assertEquals(listOf(1, 2), hand)
    }

    @Test
    fun aHandIsReproducibleForAGivenSeed() {
        val opponent = npc(pool = (1..40).toList())

        assertEquals(opponent.randomHand(Random(3)), opponent.randomHand(Random(3)))
    }

    @Test
    fun rewardsAreRolledPerEntry() {
        val opponent = npc().copy(
            itemRewards = listOf(
                ItemReward("card", 1.0, cardId = 13),
                ItemReward("card", 0.0, cardId = 14),
            ),
        )

        assertEquals(listOf(CardItem(13)), opponent.rollRewards(Random(1)))
    }

    @Test
    fun aRateOfZeroNeverDropsAndOneAlwaysDoes() {
        val never = npc().copy(itemRewards = listOf(ItemReward("card", 0.0, cardId = 1)))
        val always = npc().copy(itemRewards = listOf(ItemReward("card", 1.0, cardId = 1)))

        repeat(50) { seed ->
            assertTrue(never.rollRewards(Random(seed)).isEmpty())
            assertEquals(1, always.rollRewards(Random(seed)).size)
        }
    }

    @Test
    fun anNpcWithNoWindowIsAlwaysAvailable() {
        val always = npc()

        for (hour in 0..23) {
            assertTrue(always.availability.isOpenAtHour(hour), "hour $hour")
        }
        assertTrue(always.availability.isAlwaysAvailable)
    }

    /**
     * `{begins:14, ends:19}` means 14:00–18:59, which the AS3's chained comparison could not
     * express.
     */
    @Test
    fun aWindowWithinOneDayIsClosedOutsideIt() {
        val window = Availability(begins = 14, ends = 19)

        assertFalse(window.wrapsMidnight)
        assertFalse(window.isOpenAtHour(13))
        assertTrue(window.isOpenAtHour(14))
        assertTrue(window.isOpenAtHour(18))
        assertFalse(window.isOpenAtHour(19), "ends is exclusive")
        assertFalse(window.isOpenAtHour(20), "the AS3 filter would have said yes here")
    }

    @Test
    fun aWindowThatWrapsMidnightSpansTwoDays() {
        val window = Availability(begins = 20, ends = 8)

        assertTrue(window.wrapsMidnight)
        assertTrue(window.isOpenAtHour(20))
        assertTrue(window.isOpenAtHour(23))
        assertTrue(window.isOpenAtHour(0))
        assertTrue(window.isOpenAtHour(7))
        assertFalse(window.isOpenAtHour(8))
        assertFalse(window.isOpenAtHour(19))
    }

    @Test
    fun anHourOutsideTheDayIsAProgrammingError() {
        for (hour in listOf(-1, 24, 25)) {
            assertFailsWith<IllegalArgumentException>("hour $hour must be refused") {
                Availability(1, 2).isOpenAtHour(hour)
            }
        }
    }
}
