package com.tripletriad.data

import com.tripletriad.model.Achievement
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.GameSave
import com.tripletriad.model.Requirement

/** An achievement and where the profile stands on it, for the achievements screen. */
data class AchievementStatus(
    val achievement: Achievement,
    val progress: Requirement.Progress,
    /** Epoch millis it was earned at, or null if it has not been. */
    val earnedAt: Long?,
) {
    val isEarned: Boolean get() = earnedAt != null
}

/** The result of crediting a profile with what it has newly earned. */
data class AchievementAward(
    /** The profile with the achievements recorded and any rewards in the bag. */
    val save: GameSave,
    /** What was just earned, in catalogue order. Empty when nothing was. */
    val earned: List<Achievement>,
) {
    val hasAwards: Boolean get() = earned.isNotEmpty()
}

/**
 * Evaluates achievements against a profile and credits what it has earned.
 *
 * ### What this replaces
 *
 * `Achievements.check()` (`Achievements.as:75-91`) does five things in eleven lines: tests each
 * condition, writes the timestamp into the global profile, constructs a Starling `Image`, pushes
 * the reward object into `BAG`, and calls `InventoryScreen.sortBag()` — a static method on a
 * *screen*, so checking achievements requires the inventory screen to be loaded. It also cannot be
 * re-run, because its conditions were evaluated in the constructor (see [Requirement]).
 *
 * The split here: [AchievementCatalog] holds the rules, [statuses] answers questions about them,
 * and [credit] is the only thing that changes a profile. No texture is touched, nothing global is
 * mutated, and the whole thing is a pure function of a [GameSave].
 */
class AchievementRepository(
    private val catalog: List<Achievement> = AchievementCatalog.all,
) {
    /** Every achievement with the profile's progress, in catalogue order. */
    fun statuses(save: GameSave): List<AchievementStatus> = catalog.map { achievement ->
        AchievementStatus(
            achievement = achievement,
            progress = achievement.progressFor(save),
            earnedAt = save.achievements[achievement.id],
        )
    }

    /** Those already earned, newest first — what a profile screen shows. */
    fun earned(save: GameSave): List<AchievementStatus> =
        statuses(save).filter { it.isEarned }.sortedByDescending { it.earnedAt }

    /** Those not yet earned, closest to completion first. */
    fun pending(save: GameSave): List<AchievementStatus> =
        statuses(save).filterNot { it.isEarned }.sortedByDescending { it.progress.fraction }

    /**
     * Credits [save] with everything it now qualifies for.
     *
     * Rewards go into the bag through [Inventory.add], so a second copy of a reward card stacks
     * rather than becoming a second row — which is what the AS3 `BAG.push` produced.
     *
     * Idempotent by construction, not by accident: anything already recorded is filtered out, so
     * calling this after every match is correct and cheap.
     *
     * ### One round is enough, and that is a property of the data
     *
     * Crediting cannot enable a further achievement, so this does not need to iterate to a fixed
     * point. The reasoning: the only rewards in the catalogue are `CardItem`s, [Inventory.add] puts
     * them in the **bag**, and no [Requirement] reads the bag — the closest, `CardsOwned`, counts
     * [GameSave.cards], which a card item only joins when [Inventory.use] consumes it. So a reward
     * can influence an achievement one player action later, never within a credit.
     *
     * If a future reward ever *did* satisfy a requirement directly, this would need a second pass —
     * and `AchievementRepositoryTest` pins the current behaviour so that change is visible rather
     * than silent. A loop here would have been the wrong answer regardless: it invites a cycle
     * between an achievement and its own reward.
     *
     * @param at epoch millis to record as the moment each was earned, as the AS3
     *   `new Date().getTime()` does. Injected because `commonMain` has no clock.
     */
    fun credit(save: GameSave, at: Long): AchievementAward {
        val newly = catalog.filter { !save.hasAchievement(it.id) && it.isEarnedBy(save) }
        if (newly.isEmpty()) return AchievementAward(save, emptyList())

        var updated = save
        for (achievement in newly) {
            updated = updated.withAchievement(achievement.id, at)
            achievement.reward?.let { updated = Inventory.add(updated, it) }
        }
        return AchievementAward(updated, newly)
    }
}
