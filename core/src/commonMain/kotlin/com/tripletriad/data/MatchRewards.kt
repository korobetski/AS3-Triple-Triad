package com.tripletriad.data

import com.tripletriad.model.Achievement
import com.tripletriad.model.BoonType
import com.tripletriad.model.GameRules
import com.tripletriad.model.GameSave
import com.tripletriad.model.Item
import com.tripletriad.model.MatchResult
import com.tripletriad.model.Npc
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * What a finished match paid out — the argument to the end-of-match panel.
 *
 * `PVEMatchScreen.endGame` passes the same set through `setTimeout(rematch, …, {label, MGP, XP,
 * items, achievements, NPC, RULES})`, which is why these five fields travel together.
 *
 * @property mgpBoonSpent whether a stored MGP boon was consumed to raise [mgp] by 20%. The panel
 *   says so, because a boon disappearing silently looks like it did nothing.
 */
data class MatchReward(
    val result: MatchResult,
    val mgp: Int,
    val xp: Int,
    val items: List<Item> = emptyList(),
    val achievements: List<Achievement> = emptyList(),
    val mgpBoonSpent: Boolean = false,
    val xpBoonSpent: Boolean = false,
)

/** The profile after a match, and what the match paid. */
data class MatchCredit(val save: GameSave, val reward: MatchReward)

/**
 * End-of-match crediting — `PVEMatchScreen.endGame` (`:45-165`), as a pure function.
 *
 * The original is one 120-line method with the three results as three near-identical branches, each
 * reading and writing the global `Game.PROFILE_DATAS` and each calling `Save.save` itself. The
 * duplication is why the branches disagree: only the **win** branch records rule wins and NPC wins,
 * and only it rolls the drop table. That asymmetry is reproduced — it is the rule, not a slip,
 * since `RULES_W` feeds the "win N matches with rule X" achievements — but it is now stated once
 * instead of being implied by which of three blocks a line sits in.
 *
 * ### The match fee is not charged
 *
 * `NPC.matchFee` is declared for all 85 opponents, exposed by a getter, and **never read**. Nothing
 * in `endGame` or anywhere else deducts it: a loss against a 30 MGP opponent still pays out. It is
 * clearly *meant* to be an entry cost — it rises with difficulty — and charging it would change the
 * economy from one that only ever grows into one with a real risk, which is a game-design change
 * rather than a migration. So the fee is carried as data and displayed in the opponent list, and
 * [Npc.mgpFor] no longer pretends it is deducted.
 */
object MatchRewards {
    /** `tools.rand(20)` / `rand(10)` / `rand(5)` — the random top-up per result (`:114`, `:76`). */
    private const val WIN_BONUS_MAX = 20
    private const val DRAW_BONUS_MAX = 10
    private const val LOSE_BONUS_MAX = 5

    /** `Math.round(reward * 20 / 100)` — what one boon is worth. */
    private const val BOON_PERCENT = 20
    private const val PERCENT = 100

    /**
     * Credits [save] with the result of a match against [npc].
     *
     * @param result the profile's result. There is deliberately no overload taking a
     *   [com.tripletriad.model.MatchOutcome]: a sudden-death draw is *not* a result — the rematch
     *   decides it — and `PVEMatchScreen.as:63-68` likewise dispatches the rematch without touching
     *   stats or rewards. [MatchResult.of] returns null for exactly that case, so a caller that
     *   forwards its null cannot credit an undecided match by accident.
     * @param rules what was in force. Read only on a win, for `RULES_W`.
     * @param at epoch millis for the achievement timestamps.
     * @param random the MGP top-up and the drop table. Uniform, where the original's
     *   `Math.round(Math.random() * n)` gives half weight to 0 and to n.
     */
    @Suppress("LongParameterList")
    fun credit(
        save: GameSave,
        npc: Npc,
        result: MatchResult,
        rules: GameRules,
        at: Long,
        random: Random = Random.Default,
    ): MatchCredit {
        val mgpBoon = save.boons.mgp > 0
        val xpBoon = save.boons.xp > 0

        val baseMgp = npc.mgpFor(result)
        val mgp = baseMgp + random.nextInt(bonusMax(result) + 1) + boost(baseMgp, mgpBoon)
        val xp = npc.xpFor(result) + boost(npc.xpFor(result), xpBoon)
        val items = if (result == MatchResult.WIN) npc.rollRewards(random) else emptyList()

        var updated = save
            .endingMatch()
            .copy(stats = save.stats.recordingStats(result))
            .withMgp(mgp)
            .withXp(xp.toLong())
        if (mgpBoon) updated = updated.copy(boons = updated.boons.spending(BoonType.MGP))
        if (xpBoon) updated = updated.copy(boons = updated.boons.spending(BoonType.XP))

        if (result == MatchResult.WIN) {
            updated = updated.withRulesWin(rules).withNpcWin(npc.iconId)
            updated = Inventory.addAll(updated, items)
        }

        val award = AchievementRepository().credit(updated, at)
        return MatchCredit(
            save = award.save,
            reward = MatchReward(
                result = result,
                mgp = mgp,
                xp = xp,
                items = items,
                achievements = award.earned,
                mgpBoonSpent = mgpBoon,
                xpBoonSpent = xpBoon,
            ),
        )
    }

    private fun bonusMax(result: MatchResult): Int = when (result) {
        MatchResult.WIN -> WIN_BONUS_MAX
        MatchResult.DRAW -> DRAW_BONUS_MAX
        MatchResult.LOSE -> LOSE_BONUS_MAX
    }

    private fun boost(base: Int, active: Boolean): Int =
        if (active) (base * BOON_PERCENT / PERCENT.toDouble()).roundToInt() else 0
}
