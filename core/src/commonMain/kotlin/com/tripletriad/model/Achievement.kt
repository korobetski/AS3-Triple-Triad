package com.tripletriad.model

/**
 * What a profile must do to earn an achievement.
 *
 * ### Why this is data and not a lambda
 *
 * `datas/Achievements.as` evaluates all 22 conditions **in its constructor**, against the global
 * `Game.PROFILE_DATAS`:
 *
 * ```actionscript
 * LIST = [
 *     { id:"ac-tt1", ..., condition:(Game.PROFILE_DATAS.NPC_W_TOTAL >= 1) },
 *     ...
 * ];
 * ```
 *
 * `condition` is therefore a **Boolean already computed at construction time**, not a test that can
 * be re-run — so an `Achievements` object is a snapshot of one instant, and `check()` on a stale
 * one silently reports stale results. Constructing it, reading `check()` and discarding it is the
 * only correct usage, which is exactly what the AS3 screens do.
 *
 * Modelling the requirement as a sealed type instead means the catalogue is a `val` that can be
 * declared once, evaluated against any [GameSave] at any time, and — the reason that matters —
 * asked
 * *how close* the profile is, which is what a progress bar in the achievements screen needs and
 * which a pre-computed Boolean cannot answer.
 */
sealed interface Requirement {
    /** How far along [save] is, and what it is aiming at. */
    fun progress(save: GameSave): Progress

    /** [current] out of [target]; earned once `current >= target`. */
    data class Progress(val current: Int, val target: Int) {
        val isMet: Boolean get() = current >= target

        /** 0f..1f, for a progress bar. */
        val fraction: Float get() = if (target <= 0) {
            1f
        } else {
            (current.toFloat() / target).coerceIn(
                0f,
                1f,
            )
        }
    }

    /** Defeat [count] NPCs in total. `NPC_W_TOTAL` — the Triple Team tier. */
    data class NpcWins(val count: Int) : Requirement {
        override fun progress(save: GameSave) = Progress(save.npcWinsTotal, count)
    }

    /**
     * Win [count] matches with [ruleKey] active. `RULES_W[ruleKey]` — the Wheel of Fortune tier,
     * which uses `RULE_ROULETTE`.
     *
     * The key is an AS3 rule constant; [GameRules.activeRuleKeys] is what produces them.
     */
    data class RuleWins(val ruleKey: String, val count: Int) : Requirement {
        override fun progress(save: GameSave) = Progress(save.rulesWins[ruleKey] ?: 0, count)
    }

    /** Own [count] cards. `CARDS.length` — the Triple-decker tier. */
    data class CardsOwned(val count: Int) : Requirement {
        override fun progress(save: GameSave) = Progress(save.cards.size, count)
    }

    /** Hold [amount] MGP at once. `MGP` — the MGP Pot tier. */
    data class MgpHeld(val amount: Int) : Requirement {
        override fun progress(save: GameSave) = Progress(save.mgp, amount)
    }

    /**
     * Own every card in [cardIds], on the [collection] profile only.
     *
     * `ac-fob` (`Achievements.as:71`) is the sole instance: the thirteen beast cards, gated on
     * `MODE == 'ff14_'`. The mode gate is part of the requirement rather than a separate concept
     * because the ids mean different cards in the other collection — see [BoosterType].
     */
    data class CardSetOwned(
        val collection: CardCollection,
        val cardIds: List<Int>,
    ) : Requirement {
        override fun progress(save: GameSave): Progress {
            if (save.mode != collection) return Progress(0, cardIds.size)
            return Progress(cardIds.count { save.ownsCard(it) }, cardIds.size)
        }
    }
}

/**
 * One achievement definition. `Achievements.list` and `Achievements.LIST` merged.
 *
 * The AS3 keeps these as **two parallel structures**: a static `list` of `{label, iconID}` for
 * display and an instance `LIST` of `{id, label, iconID, condition, item}` for checking, with the
 * id, label and icon duplicated between them (`Achievements.as:16-39` and `:45-72`). They agree
 * today by hand. One record here.
 *
 * @property labelKey i18n key, e.g. `STR_Triple_Team_I`. Mixed case in the original, kept verbatim
 *   because it is a lookup key in the locale bundles.
 * @property iconId texture name. Note the tiers reuse `card_r{n}_icon` to signal difficulty, and
 *   `ac-fob` uses a card thumbnail (`ff14_thumb_37`).
 * @property reward granted into the bag on earning it. Only three achievements have one.
 */
data class Achievement(
    val id: String,
    val labelKey: String,
    val iconId: String,
    val requirement: Requirement,
    val reward: Item? = null,
) {
    /** Whether [save] has met the requirement, whether or not it has been recorded yet. */
    fun isEarnedBy(save: GameSave): Boolean = requirement.progress(save).isMet

    /** Progress towards it, for the achievements screen. */
    fun progressFor(save: GameSave): Requirement.Progress = requirement.progress(save)
}

/**
 * The 22 achievements of `datas/Achievements.as`, in its order.
 *
 * Transcribed from `LIST` (`:45-72`), whose inline comments state each threshold. Two things in the
 * original that are *not* reproduced, both bugs rather than behaviour:
 *
 * - **`ac-wof*` compares `undefined >= n`.** `RULES_W` starts as `{}`, so before a single roulette
 *   win `Game.PROFILE_DATAS.RULES_W['RULE_ROULETTE']` is `undefined` and every comparison is
 *   false — which happens to be right. [Requirement.RuleWins] reads a missing key as 0, the
 *   same answer by a defined route.
 * - **`ac-td5` wants 110 cards**, but the ff14 collection has 153 and the ff8 one 110. On an ff8
 *   profile it therefore means "own every card", and on an ff14 profile it does not. Transcribed as
 *   written; the threshold is the original's design decision, not an error to fix silently.
 */
object AchievementCatalog {
    /** The thirteen beast cards, `Achievements.as:71`. Also [BoosterType.BEAST]'s pool. */
    val BEAST_CARDS: List<Int> = listOf(14, 15, 16, 17, 18, 27, 35, 36, 37, 82, 83, 117, 128)

    val all: List<Achievement> = listOf(
        // Triple Team — defeat n NPCs.
        tripleTeam("ac-tt1", "STR_Triple_Team_I", 1),
        tripleTeam("ac-tt2", "STR_Triple_Team_II", 30),
        tripleTeam("ac-tt3", "STR_Triple_Team_III", 300, reward = CardItem(75)),
        tripleTeam("ac-tt4", "STR_Triple_Team_IV", 3_000),
        tripleTeam("ac-tt5", "STR_Triple_Team_V", 7_777),

        // Wheel of Fortune — win n Roulette matches.
        roulette("ac-wof1", "STR_Wheel_Of_Fortune_I", 1, "card_r1_icon"),
        roulette("ac-wof2", "STR_Wheel_Of_Fortune_II", 10, "card_r2_icon"),
        roulette("ac-wof3", "STR_Wheel_Of_Fortune_III", 30, "card_r2_icon"),
        roulette("ac-wof4", "STR_Wheel_Of_Fortune_IV", 100, "card_r3_icon"),
        roulette("ac-wof5", "STR_Wheel_Of_Fortune_V", 300, "card_r4_icon", reward = CardItem(79)),
        roulette("ac-wof6", "STR_Always_Bet_On_Me", 1_000, "card_r5_icon"),

        // Triple-decker — collect n cards.
        collector("ac-td1", "STR_Triple_decker_I", 10, "card_r1_icon"),
        collector("ac-td2", "STR_Triple_decker_II", 30, "card_r2_icon"),
        collector("ac-td3", "STR_Triple_decker_III", 60, "card_r3_icon"),
        collector("ac-td4", "STR_Triple_decker_IV", 80, "card_r4_icon"),
        collector("ac-td5", "STR_Triple_decker_V", 110, "card_r5_icon"),

        // MGP Pot — hold n MGP.
        hoard("ac-mp1", "STR_MGP_POT_I", 1_000, "card_r1_icon"),
        hoard("ac-mp2", "STR_MGP_POT_II", 10_000, "card_r2_icon"),
        hoard("ac-mp3", "STR_MGP_POT_III", 100_000, "card_r3_icon"),
        hoard("ac-mp4", "STR_MGP_POT_IV", 400_000, "card_r4_icon"),
        hoard("ac-mp5", "STR_MGP_POT_V", 1_000_000, "card_r5_icon"),

        Achievement(
            id = "ac-fob",
            labelKey = "STR_FRIEND_OF_BEASTS",
            iconId = "ff14_thumb_37",
            requirement = Requirement.CardSetOwned(CardCollection.FF14, BEAST_CARDS),
        ),
    )

    private val byId: Map<String, Achievement> = all.associateBy { it.id }

    operator fun get(id: String): Achievement? = byId[id]

    /**
     * Achievements [save] has now met but not yet been credited with.
     *
     * `Achievements.check()` (`:75-91`) without its side effects: the AS3 version writes the
     * timestamp into `PROFILE_DATAS`, pushes the reward into `BAG`, builds a Starling `Image` and
     * re-sorts the inventory, all from inside what reads like a query. Here the query returns what
     * changed and [com.tripletriad.data.AchievementRepository] decides what to do with it, which is
     * what makes the rule testable without a profile, a texture atlas and an inventory screen.
     */
    fun newlyEarned(save: GameSave): List<Achievement> =
        all.filter { !save.hasAchievement(it.id) && it.isEarnedBy(save) }

    private fun tripleTeam(id: String, labelKey: String, wins: Int, reward: Item? = null) =
        Achievement(id, labelKey, NPC_ICON, Requirement.NpcWins(wins), reward)

    private fun roulette(
        id: String,
        labelKey: String,
        wins: Int,
        iconId: String,
        reward: Item? = null,
    ) = Achievement(id, labelKey, iconId, Requirement.RuleWins(ROULETTE_KEY, wins), reward)

    private fun collector(id: String, labelKey: String, cards: Int, iconId: String) =
        Achievement(id, labelKey, iconId, Requirement.CardsOwned(cards))

    private fun hoard(id: String, labelKey: String, mgp: Int, iconId: String) =
        Achievement(id, labelKey, iconId, Requirement.MgpHeld(mgp))

    /** `Achievements.as:17` — a numeric FFXIV icon id, not a descriptive name. */
    private const val NPC_ICON = "000713"

    /** The `tripleTriadRules.as` constant, as `RULES_W` keys it. */
    private const val ROULETTE_KEY = "RULE_ROULETTE"
}
