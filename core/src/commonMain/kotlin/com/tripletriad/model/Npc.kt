package com.tripletriad.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * An NPC's skill band, which sets its XP payout. `NPC.LEVEL_*` (`NPC.as:13-18`).
 *
 * The serial names are the i18n keys the AS3 uses as the *values* of the constants, so an extracted
 * `npcs.json` names a level exactly as `NPCs.as` does.
 *
 * @property modifier `NPC.LEVELS_MODIFIER` (`:19`), 0..5, the multiplier the XP formula scales by.
 */
@Serializable
enum class NpcLevel(val modifier: Int) {
    @SerialName("STR_NPC_LEVEL_NONE")
    NONE(0),

    @SerialName("STR_NPC_LEVEL_NOVICE")
    NOVICE(1),

    @SerialName("STR_NPC_LEVEL_INITIATE")
    INITIATE(2),

    @SerialName("STR_NPC_LEVEL_AVERAGE")
    AVERAGE(3),

    @SerialName("STR_NPC_LEVEL_ADVANCED")
    ADVANCED(4),

    @SerialName("STR_NPC_LEVEL_EXPERT")
    EXPERT(5),
    ;

    /** i18n key for the label shown in the opponent list. */
    val labelKey: String get() = "STR_NPC_LEVEL_$name"

    /**
     * XP for a win, a draw and a loss.
     *
     * `NPC.set level` (`:212-219`) verbatim:
     * ```actionscript
     * _XPReward = {w: 25 + Math.round(m * 2), d: 10 + Math.round(m * 1.5), l: 5 + Math.round(m)};
     * ```
     * where `m` is [modifier]. Note [NONE] never reaches that branch in the original — the setter
     * rejects it and leaves `_XPReward` null, and `get XPReward` then re-enters the setter with
     * `LEVEL_NONE` and returns null again. So an unlevelled NPC pays **no XP at all**, which is
     * reproduced here as the modifier-0 row (25/10/5) *only* if it is asked for; [Npc.xpFor]
     * returns 0 for [NONE] instead. That is the behaviour, oddly expressed as it is.
     */
    val xpReward: XpReward
        get() = XpReward(
            win = 25 + (modifier * 2.0).roundToInt(),
            draw = 10 + (modifier * 1.5).roundToInt(),
            lose = 5 + modifier,
        )
}

/** XP paid out by result. `NPC._XPReward`, whose AS3 keys are `w` / `d` / `l`. */
@Serializable
data class XpReward(
    @SerialName("w") val win: Int = 0,
    @SerialName("d") val draw: Int = 0,
    @SerialName("l") val lose: Int = 0,
) {
    operator fun get(result: MatchResult): Int = when (result) {
        MatchResult.WIN -> win
        MatchResult.DRAW -> draw
        MatchResult.LOSE -> lose
    }
}

/** MGP paid out by result. `NPC._MGPReward` (`NPC.as:30`), same `w`/`d`/`l` keys. */
@Serializable
data class MgpReward(
    @SerialName("w") val win: Int = 0,
    @SerialName("d") val draw: Int = 0,
    @SerialName("l") val lose: Int = 0,
) {
    operator fun get(result: MatchResult): Int = when (result) {
        MatchResult.WIN -> win
        MatchResult.DRAW -> draw
        MatchResult.LOSE -> lose
    }
}

/**
 * One entry in an NPC's drop table. `NPC._itemRewards`.
 *
 * The AS3 objects are `{type:"card"|"potion"|"booster", card|potion|booster:…, rate:0.25}`.
 * Modelled as one record with three optional payload fields rather than a sealed hierarchy because
 * that is the shape in `NPCs.as` and the extractor copies it across unchanged; [item] is where it
 * becomes typed.
 *
 * @property rate drop probability, compared against a uniform draw — see [Npc.rollRewards].
 */
@Serializable
data class ItemReward(
    val type: String,
    val rate: Double,
    @SerialName("card") val cardId: Int? = null,
    @SerialName("potion") val potion: PotionType? = null,
    @SerialName("booster") val booster: BoosterType? = null,
) {
    /** The [Item] this entry grants, or null if it names none this build understands. */
    fun item(): Item? = when (type) {
        "card" -> cardId?.let { CardItem(it) }
        "potion" -> potion?.let { PotionItem(it) }
        "booster" -> booster?.let { BoosterItem(it) }
        else -> null
    }
}

/**
 * The hours of the day an NPC can be challenged. `NPC._availability` (`NPC.as:34`), whose entries
 * in `NPCs.as` are annotated `// in hours`.
 *
 * **Hours of the day, not a date range** — `docs/migration/06-PHASE-2-DATA-LAYER.md` and the
 * `MatchHistory` schema alongside it use epoch millis for every other timestamp, and this one is
 * not one. Both zero means always available, which is how the majority of the 85 entries leave it;
 * fourteen declare a window, and seven of those wrap past midnight (`{begins:20, ends:8}`).
 *
 * [ends] is **exclusive**, matching the `hour < ends` comparison in the original.
 *
 * ### The original's filter cannot work
 *
 * `NPCs.toListCollection()` gates on:
 *
 * ```actionscript
 * if (uint(npc.availability.begins) > hour < uint(npc.availability.ends)) {
 * ```
 *
 * which AS3 parses as `((begins > hour) < ends)` — a Boolean coerced to 0 or 1 and compared against
 * the *hour* `ends`. For any `ends >= 2`, and every declared window has one, that is **always
 * true**, so the non-wrapping branch below it is dead and every window is evaluated as if it
 * wrapped: `hour >= begins || hour < ends`. An NPC declared available 14:00–19:00 is therefore also
 * available at 20:00, 21:00 and so on to midnight.
 *
 * [isOpenAtHour] implements the window the data plainly intends, wrap included. That is a **fix**:
 * reproducing the chained-comparison bug would mean reproducing an availability filter that does
 * not filter, which is indistinguishable from having no feature.
 *
 * The original also compares against `new Date().getHours() + 1`, putting midnight in hour 1 and
 * shifting every window by one. [isOpenAtHour] takes a plain 0..23 wall-clock hour.
 */
@Serializable
data class Availability(
    val begins: Int = 0,
    val ends: Int = 0,
) {
    /** No window declared — `{begins:0, ends:0}`, or the field absent entirely. */
    val isAlwaysAvailable: Boolean get() = begins == 0 && ends == 0

    /** True when the window runs past midnight, e.g. 20:00–08:00. */
    val wrapsMidnight: Boolean get() = !isAlwaysAvailable && ends <= begins

    /** @param hour wall-clock hour, 0..23. */
    fun isOpenAtHour(hour: Int): Boolean {
        require(hour in 0..HOURS_IN_DAY - 1) { "hour must be in 0..23, was $hour" }
        return when {
            isAlwaysAvailable -> true
            wrapsMidnight -> hour >= begins || hour < ends
            else -> hour in begins until ends
        }
    }

    companion object {
        val Always = Availability()
        private const val HOURS_IN_DAY = 24
    }
}

/**
 * A PvE opponent. `datas/NPC.as`, with its data coming from `datas/NPCs.as` via
 * `tools/extract_npcs.py`.
 *
 * ### Data, not a display object
 *
 * The AS3 `NPC` builds a Starling `Image` in its constructor and exposes it as `icon`, and
 * `toListItemDatas()` returns a Feathers list-item descriptor. Neither is here: [iconId] names the
 * texture and the opponent list composes it.
 *
 * ### The two card lists
 *
 * [fetishCards] are the cards this NPC always plays; [cards] is the pool the rest of the hand is
 * drawn from — see [randomHand]. Several NPCs have five fetish cards and an empty pool, i.e. a
 * fixed deck.
 *
 * @property id the `NPCs.as` id. **Not unique**: the ff8 table declares `id:2` and `id:13` twice
 *   each. Nothing in the original keys anything by it — wins are recorded under [iconId] (see
 *   [GameSave.npcWins]) — so this is a label, and [NpcCatalog][com.tripletriad.data.NpcCatalog]
 *   indexes by [iconId] instead.
 * @property nameKey i18n key, e.g. `STR_NPC_Jonas`.
 * @property ruleKeys the AS3 rule constants this NPC imposes. [gameRules] turns them into a
 *   [GameRules]; they are kept in raw form because that is what `NPCs.as` lists and what the rules
 *   digest screen displays.
 * @property matchFee MGP charged to play. Deducted whatever the result.
 * @property difficulty 1..10, the AI strength dial. Display only in this port — no AI exists yet.
 */
@Serializable
data class Npc(
    val id: Int,
    @SerialName("name") val nameKey: String,
    @SerialName("iconID") val iconId: String,
    @SerialName("rules") val ruleKeys: List<String> = emptyList(),
    @SerialName("fetishesCards") val fetishCards: List<Int> = emptyList(),
    val cards: List<Int> = emptyList(),
    val level: NpcLevel = NpcLevel.NONE,
    val matchFee: Int = 0,
    @SerialName("MGPReward") val mgpReward: MgpReward = MgpReward(),
    val itemRewards: List<ItemReward> = emptyList(),
    val difficulty: Int = 0,
    val availability: Availability = Availability.Always,
) {
    init {
        require(id > 0) { "npc id must be positive, was $id" }
    }

    /**
     * The rule set this NPC plays under.
     *
     * `NPC.get gameRules` (`:71-127`) as a fold instead of a 16-case switch. The three enum slots
     * take the *last* matching entry in [ruleKeys], exactly as the AS3 switch does by assigning
     * over a previous value — no NPC in `NPCs.as` lists two rules for the same slot, so the
     * tie-break is theoretical, but it is preserved rather than made an error.
     *
     * An unrecognised key is ignored, as the AS3 `switch` ignores it by having no `default`.
     */
    fun gameRules(): GameRules =
        ruleKeys.fold(GameRules()) { rules, key -> rules.withRuleKey(key) }

    /**
     * MGP for [result], before the random top-up and any boon — see
     * [MatchRewards][com.tripletriad.data.MatchRewards].
     *
     * **[matchFee] is not deducted, and an earlier revision of this method wrongly deducted it.**
     * The field is declared for all 85 opponents and exposed by a getter that **nothing calls**:
     * `PVEMatchScreen.endGame` pays `MGPReward.w + rand(20)` on a win and `MGPReward.l + rand(5)`
     * on a loss without subtracting anything, so a loss still pays. It reads like an intended entry
     * cost — it rises with difficulty — but charging it turns an economy that only grows into one
     * with real downside, which is a design change and not a migration. Carried as data and
     * displayed in the opponent list instead.
     */
    fun mgpFor(result: MatchResult): Int = mgpReward[result]

    /**
     * XP for [result].
     *
     * 0 for an unlevelled NPC: `NPC.get XPReward` (`:194-206`) can only ever return null for
     * [NpcLevel.NONE], and the callers treat null as no reward.
     */
    fun xpFor(result: MatchResult): Int =
        if (level == NpcLevel.NONE) 0 else level.xpReward[result]

    /**
     * Five card ids for this NPC's hand: every fetish card, topped up at random from [cards].
     *
     * `NPC.getRandomCards()` (`:280-296`), with two faults corrected:
     *
     * - **It can loop forever.** The `while (npcCards.length < 5)` splices out of a copy of
     *   `_cards` without checking it is non-empty, so an NPC with fewer than five fetish cards and
     *   an empty pool spins with `randomizer.splice` returning `undefined` and pushing it. Fifteen
     *   of the `NPCs.as` entries have an empty `cards` pool; all of those have five fetish cards,
     *   so the bug is unreachable *with the shipped data* — and would be triggered by adding one
     *   entry. Here the top-up stops when the pool runs out and the hand is returned short, which
     *   the caller can detect.
     * - `Math.round(Math.random() * (n - 1))` gives **half weight to index 0 and to index n-1**
     *   (`docs/analysis/game-rules.md` § 15.6). [Random.nextInt] is uniform.
     *
     * @param random injected so a hand is reproducible in a test.
     */
    fun randomHand(random: Random = Random.Default): List<Int> {
        val hand = fetishCards.toMutableList()
        val pool = cards.toMutableList()
        while (hand.size < HAND_SIZE && pool.isNotEmpty()) {
            hand += pool.removeAt(random.nextInt(pool.size))
        }
        return hand
    }

    /**
     * Rolls the whole drop table, keeping each entry that beats its own rate.
     *
     * `NPC.getRewardItems()` (`:257-275`). Note this is **not** `getRewardItem()` (singular,
     * `:237-255`), which picks one entry at random and can return null; the singular version is
     * unused by the match screens and is not ported. The plural one can return several items or
     * none.
     */
    fun rollRewards(random: Random = Random.Default): List<Item> =
        itemRewards.filter { random.nextDouble() < it.rate }.mapNotNull { it.item() }
}
