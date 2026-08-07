package com.tripletriad.data

import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameSave
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [AchievementRepository]: crediting, rewards, idempotence, and the screens' orderings. */
class AchievementRepositoryTest {
    private val repository = AchievementRepository()

    @Test
    fun aFreshProfileHasEarnedNothing() {
        val award = repository.credit(GameSave.new(createdAt = 0), at = 100)

        assertFalse(award.hasAwards)
        assertTrue(award.earned.isEmpty())
    }

    @Test
    fun creditingRecordsTheAchievementWithTheGivenInstant() {
        val save = GameSave(npcWins = mapOf("jonas" to 1))

        val award = repository.credit(save, at = 1_700_000_000_000)

        assertEquals(listOf("ac-tt1"), award.earned.map { it.id })
        assertEquals(1_700_000_000_000, award.save.achievements["ac-tt1"])
    }

    /** Calling this after every match must be safe, which means the second call awards nothing. */
    @Test
    fun creditingTwiceAwardsNothingTheSecondTime() {
        val save = GameSave(npcWins = mapOf("jonas" to 1))

        val first = repository.credit(save, at = 100)
        val second = repository.credit(first.save, at = 200)

        assertFalse(second.hasAwards)
        assertEquals(first.save, second.save)
        assertEquals(100L, second.save.achievements["ac-tt1"], "the original instant is kept")
    }

    @Test
    fun severalTiersCanBeEarnedAtOnce() {
        val save = GameSave(npcWins = mapOf("jonas" to 30))

        val award = repository.credit(save, at = 100)

        assertEquals(listOf("ac-tt1", "ac-tt2"), award.earned.map { it.id })
    }

    /** `ac-tt3` grants `CardItem(75)`, which `Achievements.check()` pushes into `BAG`. */
    @Test
    fun aRewardLandsInTheBag() {
        val save = GameSave(npcWins = mapOf("jonas" to 300))

        val award = repository.credit(save, at = 100)

        assertTrue(award.earned.any { it.id == "ac-tt3" })
        assertEquals(1, Inventory.count(award.save, CardItem(75)))
    }

    /** Through [Inventory.add], so a second copy stacks rather than becoming a second row. */
    @Test
    fun aRewardAlreadyHeldStacksRatherThanDuplicating() {
        val save = Inventory.add(GameSave(npcWins = mapOf("jonas" to 300)), CardItem(75))

        val award = repository.credit(save, at = 100)

        assertEquals(1, award.save.bag.size)
        assertEquals(2, Inventory.count(award.save, CardItem(75)))
    }

    /**
     * Crediting cannot cascade, and that is why [AchievementRepository.credit] does not iterate.
     *
     * A reward is a `CardItem` and goes into the **bag**; `ac-td1` counts [GameSave.cards], the
     * collection. The two only meet when [Inventory.use] consumes the item, which is a player
     * action. So one round is provably enough — and if a future reward ever changed that, this test
     * fails rather than the behaviour changing quietly.
     */
    @Test
    fun aRewardDoesNotEnableAFurtherAchievement() {
        // Nine cards owned: ac-td1 wants ten. ac-tt3's reward would be the tenth *if* rewards
        // landed in the collection rather than the bag.
        val save = GameSave(cards = (1..9).toList(), npcWins = mapOf("jonas" to 300))

        val first = repository.credit(save, at = 100)
        assertTrue(first.earned.any { it.id == "ac-tt3" })
        assertEquals(1, Inventory.count(first.save, CardItem(75)), "the reward is in the bag")
        assertEquals(9, first.save.cards.size, "and not in the collection")

        val second = repository.credit(first.save, at = 200)
        assertFalse(second.hasAwards, "nothing new — a reward cannot satisfy a requirement")

        // Using it is what moves the card across, and *then* ac-td1 is reachable.
        val used = Inventory.use(first.save, CardItem(75)).save
        assertEquals(10, used.cards.size)
        assertEquals(listOf("ac-td1"), repository.credit(used, at = 300).earned.map { it.id })
    }

    @Test
    fun statusesCoverTheWholeCatalogueInOrder() {
        val statuses = repository.statuses(GameSave.new(createdAt = 0))

        assertEquals(AchievementCatalog.all.size, statuses.size)
        assertEquals(AchievementCatalog.all.map { it.id }, statuses.map { it.achievement.id })
        assertTrue(statuses.none { it.isEarned })
        assertTrue(statuses.all { it.earnedAt == null })
    }

    @Test
    fun statusesCarryProgress() {
        val save = GameSave(npcWins = mapOf("jonas" to 15))

        val status = repository.statuses(save).single { it.achievement.id == "ac-tt2" }

        assertEquals(15, status.progress.current)
        assertEquals(30, status.progress.target)
        assertFalse(status.progress.isMet)
    }

    @Test
    fun earnedIsNewestFirst() {
        val save = GameSave(achievements = mapOf("ac-tt1" to 100L, "ac-td1" to 500L))

        val earned = repository.earned(save)

        assertEquals(listOf("ac-td1", "ac-tt1"), earned.map { it.achievement.id })
        assertNotNull(earned.first().earnedAt)
    }

    @Test
    fun pendingIsClosestToCompletionFirst() {
        // 15 of 30 NPC wins is halfway on ac-tt2; 1 of 1 is complete but unrecorded, so ac-tt1
        // leads.
        val save = GameSave(npcWins = mapOf("jonas" to 15))

        val pending = repository.pending(save)

        assertEquals("ac-tt1", pending.first().achievement.id)
        assertTrue(pending.none { it.isEarned })
        assertNull(pending.first().earnedAt)
        val fractions = pending.map { it.progress.fraction }
        assertEquals(fractions.sortedDescending(), fractions)
    }

    @Test
    fun aCustomCatalogueIsHonoured() {
        val only = AchievementRepository(listOf(AchievementCatalog.all.first()))

        assertEquals(1, only.statuses(GameSave.new(createdAt = 0)).size)
    }
}
