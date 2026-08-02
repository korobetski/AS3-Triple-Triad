package com.tripletriad.data

import com.tripletriad.model.Boons
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameRules
import com.tripletriad.model.GameSave
import com.tripletriad.model.ItemReward
import com.tripletriad.model.MatchResult
import com.tripletriad.model.MgpReward
import com.tripletriad.model.Npc
import com.tripletriad.model.NpcLevel
import com.tripletriad.model.OrderRule
import com.tripletriad.model.XpTable
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [MatchRewards] — `PVEMatchScreen.endGame`, whose three near-identical branches disagree with each
 * other on purpose.
 *
 * The MGP payout carries a random top-up, so nothing here asserts an exact figure without also
 * bounding it: what is pinned is the **range**, which is what the formula actually says, plus every
 * asymmetry between the three results.
 */
class MatchRewardsTest {
    private val opponent = Npc(
        id = 1,
        nameKey = "STR_NPC_Test",
        iconId = "test-npc",
        level = NpcLevel.EXPERT,
        matchFee = 30,
        mgpReward = MgpReward(win = 100, draw = 40, lose = 10),
    )

    private val profile = GameSave.new(username = "Tester", createdAt = AT)

    private val seeds = 0 until 60

    private fun credit(
        result: MatchResult,
        save: GameSave = profile,
        npc: Npc = opponent,
        rules: GameRules = GameRules(),
        seed: Int = 1,
    ) = MatchRewards.credit(save, npc, result, rules, AT, Random(seed))

    // ---- MGP -------------------------------------------------------------

    /**
     * `MGPReward.w + rand(20)`, `.d + rand(10)`, `.l + rand(5)` — `:114`, `:76`, `:148`.
     *
     * The bonus ranges are the one thing the three branches vary that is not a counter, so all
     * three are swept: a copy-paste that gave a loss the win's `rand(20)` would pass any single-
     * result test.
     */
    @Test
    fun theMgpPayoutIsTheBaseRewardPlusAResultSpecificBonus() {
        val expected = mapOf(
            MatchResult.WIN to (100..120),
            MatchResult.DRAW to (40..50),
            MatchResult.LOSE to (10..15),
        )
        for ((result, range) in expected) {
            val paid = seeds.map { credit(result, seed = it).reward.mgp }
            val outside = paid.filterNot { it in range }
            assertTrue(outside.isEmpty(), "$result paid $outside, outside $range")
            assertTrue(paid.min() < paid.max(), "$result never varied, so the bonus is not rolled")
        }
    }

    /** The fee is not deducted, so every result is a net gain. See [Npc.mgpFor]. */
    @Test
    fun everyResultPaysSomethingAndTheFeeIsNeverCharged() {
        for (result in MatchResult.entries) {
            val credited = credit(result)

            assertTrue(credited.reward.mgp > 0, "$result paid ${credited.reward.mgp}")
            assertEquals(
                profile.mgp + credited.reward.mgp,
                credited.save.mgp,
                "$result should add exactly what it reports",
            )
        }
    }

    // ---- XP --------------------------------------------------------------

    /** XP has no random component — `XPReward.w` is taken as it stands. */
    @Test
    fun theXpPayoutIsExactAndFollowsTheLevelBand() {
        assertEquals(35, credit(MatchResult.WIN).reward.xp, "EXPERT wins 25 + 5*2")
        assertEquals(18, credit(MatchResult.DRAW).reward.xp)
        assertEquals(10, credit(MatchResult.LOSE).reward.xp)
        assertTrue(seeds.map { credit(MatchResult.WIN, seed = it).reward.xp }.distinct().size == 1)
    }

    @Test
    fun anUnlevelledOpponentPaysNoXpButStillPaysMgp() {
        val dummy = opponent.copy(level = NpcLevel.NONE)

        val credited = credit(MatchResult.WIN, npc = dummy)

        assertEquals(0, credited.reward.xp)
        assertTrue(credited.reward.mgp > 0)
    }

    @Test
    fun xpRaisesTheLevel() {
        val nearly = profile.withXp(XP_NEAR_LEVEL_2)
        assertEquals(1, nearly.level, "the fixture should be one XP short of level 2")

        val credited = credit(MatchResult.WIN, save = nearly)

        assertEquals(2, credited.save.level, "35 XP on top of $XP_NEAR_LEVEL_2 should level up")
    }

    // ---- Boons -----------------------------------------------------------

    /**
     * `Math.round(reward * 20 / 100)` on top, and one boon consumed — `:74-77`.
     *
     * A boon is a **count of boosted matches**, not a permanent multiplier: the AS3 does
     * `BOONS.MGP -= 1` in every branch that used it.
     */
    @Test
    fun anMgpBoonAddsAFifthAndIsSpent() {
        val boosted = profile.copy(boons = Boons(mgp = 2))

        val credited = credit(MatchResult.WIN, save = boosted, seed = 0)

        assertTrue(credited.reward.mgpBoonSpent)
        assertEquals(1, credited.save.boons.mgp, "one boon should be consumed")
        // 100 + rand(0..20) + round(100 * 20/100) = 120..140
        assertTrue(credited.reward.mgp in 120..140, "paid ${credited.reward.mgp}")
    }

    @Test
    fun anXpBoonAddsAFifthAndIsSpent() {
        val boosted = profile.copy(boons = Boons(xp = 1))

        val credited = credit(MatchResult.WIN, save = boosted)

        assertTrue(credited.reward.xpBoonSpent)
        assertEquals(0, credited.save.boons.xp)
        assertEquals(35 + 7, credited.reward.xp, "35 + round(35 * 20/100)")
    }

    @Test
    fun theBoonsAreIndependent() {
        val credited = credit(MatchResult.WIN, save = profile.copy(boons = Boons(mgp = 1)))

        assertTrue(credited.reward.mgpBoonSpent)
        assertFalse(credited.reward.xpBoonSpent, "no XP boon was held")
    }

    /** A boon is spent whatever the result, not only on a win — every branch decrements it. */
    @Test
    fun aBoonIsSpentOnALossToo() {
        val credited = credit(MatchResult.LOSE, save = profile.copy(boons = Boons(mgp = 1)))

        assertTrue(credited.reward.mgpBoonSpent)
        assertEquals(0, credited.save.boons.mgp)
    }

    @Test
    fun noBoonMeansNoBoost() {
        val credited = credit(MatchResult.WIN, seed = 0)

        assertFalse(credited.reward.mgpBoonSpent)
        assertTrue(credited.reward.mgp in 100..120)
    }

    // ---- Counters --------------------------------------------------------

    @Test
    fun eachResultBumpsItsOwnStatAndTheEndedCounter() {
        val expected = mapOf(
            MatchResult.WIN to Triple(1, 0, 0),
            MatchResult.DRAW to Triple(0, 1, 0),
            MatchResult.LOSE to Triple(0, 0, 1),
        )
        for ((result, counts) in expected) {
            val stats = credit(result).save.stats

            assertEquals(counts.first, stats.wins, "$result wins")
            assertEquals(counts.second, stats.draws, "$result draws")
            assertEquals(counts.third, stats.defeats, "$result defeats")
            assertEquals(1, credit(result).save.endedMatches, "$result should end a match")
        }
    }

    /**
     * **Only a win records the opponent and the rules**, which is the asymmetry the original's
     * three duplicated branches encode: `NPC_W` and the `RULES_W` loop appear in the win branch
     * alone (`:100-127`). It matters because `RULES_W` is what the Wheel-of-Fortune achievements
     * count.
     */
    @Test
    fun onlyAWinRecordsTheOpponentAndTheRules() {
        val rules = GameRules(same = true, order = OrderRule.ORDER)

        val won = credit(MatchResult.WIN, rules = rules).save
        assertEquals(1, won.npcWins["test-npc"])
        assertEquals(1, won.rulesWins["RULE_SAME"])
        assertEquals(1, won.rulesWins["RULE_ORDER"])

        for (result in listOf(MatchResult.DRAW, MatchResult.LOSE)) {
            val other = credit(result, rules = rules).save
            assertTrue(other.npcWins.isEmpty(), "$result recorded an NPC win")
            assertTrue(other.rulesWins.isEmpty(), "$result recorded a rule win")
        }
    }

    /** Wins are keyed by `iconID`, not by id — ids are not unique. See [GameSave.npcWins]. */
    @Test
    fun theOpponentIsRecordedByIconId() {
        val credited = credit(MatchResult.WIN, npc = opponent.copy(id = 99))

        assertEquals(mapOf("test-npc" to 1), credited.save.npcWins)
    }

    @Test
    fun repeatedWinsAccumulate() {
        var save = profile
        repeat(3) {
            save = MatchRewards.credit(save, opponent, MatchResult.WIN, GameRules(), AT).save
        }

        assertEquals(3, save.npcWins["test-npc"])
        assertEquals(3, save.stats.wins)
        assertEquals(3, save.endedMatches)
    }

    // ---- Items -----------------------------------------------------------

    /** `getRewardItems()` is called in the win branch only (`:126`). */
    @Test
    fun onlyAWinRollsTheDropTable() {
        val generous = opponent.copy(itemRewards = listOf(ItemReward("card", 1.0, cardId = 7)))

        assertEquals(listOf(CardItem(7)), credit(MatchResult.WIN, npc = generous).reward.items)
        for (result in listOf(MatchResult.DRAW, MatchResult.LOSE)) {
            assertTrue(credit(result, npc = generous).reward.items.isEmpty(), result.name)
        }
    }

    /** Dropped items reach the bag, and a second copy stacks rather than becoming a second row. */
    @Test
    fun droppedItemsGoIntoTheBagAndStack() {
        val generous = opponent.copy(itemRewards = listOf(ItemReward("card", 1.0, cardId = 7)))

        var save = credit(MatchResult.WIN, npc = generous).save
        assertEquals(1, save.bag.size)
        save = MatchRewards.credit(save, generous, MatchResult.WIN, GameRules(), AT).save

        assertEquals(1, save.bag.size, "the second copy should stack")
        assertEquals(2, save.bag.single().stack)
    }

    @Test
    fun aRateOfZeroNeverDrops() {
        val stingy = opponent.copy(itemRewards = listOf(ItemReward("card", 0.0, cardId = 7)))

        assertTrue(
            seeds.all { credit(MatchResult.WIN, npc = stingy, seed = it).reward.items.isEmpty() },
        )
    }

    // ---- Achievements ----------------------------------------------------

    /** `Achievements.check()` runs in all three branches (`:88`, `:135`, `:161`). */
    @Test
    fun achievementsAreCheckedWhateverTheResult() {
        // `MGP_POT_I` wants 1,000 MGP held; a loss still pays, so a profile just short of it earns
        // the achievement by losing.
        val nearlyRich = profile.withMgp(MGP_JUST_SHORT)

        for (result in MatchResult.entries) {
            val credited = credit(result, save = nearlyRich)

            assertTrue(
                credited.reward.achievements.isNotEmpty(),
                "$result should have crossed the MGP threshold: ${credited.save.mgp}",
            )
            assertTrue(credited.save.achievements.isNotEmpty())
        }
    }

    @Test
    fun anAchievementIsRecordedAtTheGivenInstant() {
        val credited = credit(MatchResult.WIN, save = profile.withMgp(MGP_JUST_SHORT))

        for (id in credited.save.achievements.keys) {
            assertEquals(AT, credited.save.achievements[id], id)
        }
    }

    @Test
    fun nothingIsEarnedTwice() {
        val nearlyRich = profile.withMgp(MGP_JUST_SHORT)

        val first = credit(MatchResult.WIN, save = nearlyRich)
        val second = MatchRewards.credit(first.save, opponent, MatchResult.WIN, GameRules(), AT)

        assertTrue(first.reward.achievements.isNotEmpty())
        assertTrue(
            second.reward.achievements.none { it in first.reward.achievements },
            "an achievement was reported twice",
        )
    }

    // ---- Reproducibility -------------------------------------------------

    @Test
    fun aCreditIsReproducibleForAGivenSeed() {
        assertEquals(credit(MatchResult.WIN, seed = 5), credit(MatchResult.WIN, seed = 5))
    }

    /**
     * The boon flags say what happened, so the panel can explain an unexpected payout.
     */
    @Test
    fun theRewardReportsTheResultItWasGiven() {
        for (result in MatchResult.entries) {
            assertEquals(result, credit(result).reward.result)
        }
    }

    private companion object {
        const val AT = 1_767_268_800_000L

        /** One XP short of level 2, which [XpTable.steps] puts at 250. */
        const val XP_NEAR_LEVEL_2 = 249L

        /** One MGP short of the first `MGP_POT` achievement's 1,000. */
        const val MGP_JUST_SHORT = 1_000
    }
}
