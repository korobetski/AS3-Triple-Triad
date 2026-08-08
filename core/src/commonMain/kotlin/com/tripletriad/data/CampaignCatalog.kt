package com.tripletriad.data

import com.tripletriad.model.CardCollection
import com.tripletriad.model.MatchResult
import com.tripletriad.model.Npc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What an opponent says around a ladder match — the AS3's `messages` object.
 *
 * **Literal English text, not keys.** The original writes these as Flash string literals inside
 * `GSGroupMatchScreen`, with no `i18n.gettext` and no matching key in any of the four bundles, the
 * same way `TutorialScreen.helpTexts` does. They are carried through as the sentences they are
 * rather than as invented keys, because a key no bundle defines renders as the key itself.
 *
 * Every field is optional and most are absent: of the thirteen rungs across both ladders, **three
 * speak** — all in the Gold Saucer — and `draw` is empty on all thirteen.
 *
 * @property start said as the match opens, from `letsGetStarted`.
 * @property win said when the player wins, i.e. when the *opponent* has lost.
 * @property lose said when the player loses.
 * @property draw said on a draw. Never populated in the shipped data, and kept because its absence
 *   is a fact about the data rather than about this model.
 */
@Serializable
data class CampaignMessages(
    val start: String? = null,
    val win: String? = null,
    val lose: String? = null,
    val draw: String? = null,
) {
    /** What is said once [result] is known, or null when this opponent has nothing to say. */
    fun forResult(result: MatchResult): String? = when (result) {
        MatchResult.WIN -> win
        MatchResult.LOSE -> lose
        MatchResult.DRAW -> draw
    }

    /** True when this opponent says nothing at all, which is ten of the thirteen. */
    val isSilent: Boolean get() = start == null && win == null && lose == null && draw == null
}

/**
 * One rung of a ladder: the opponent, and what they say.
 *
 * `{npc: new NPC({...}), messages: {...}}` — the AS3's own shape, kept.
 */
@Serializable
data class CampaignStep(
    val npc: Npc,
    val messages: CampaignMessages = CampaignMessages(),
)

/**
 * A tournament ladder — pay once, then play a fixed sequence of opponents.
 *
 * Two of them ship: the **Card Club** (`ff8_`, seven rungs) and the **Gold Saucer tournament**
 * (`ff14_`, six). Both are filed under "multiplayer" in the migration plan and neither is:
 * `grep -c Socket` is 0 on all six of their screens, and they increment `PVE_MATCHES`.
 *
 * ### The rungs are not the catalogue's opponents
 *
 * All thirteen also exist in `npcs.json` under the same `iconID`, and **every one of them differs**
 * — the ladder versions add `RULE_SUDDEN_DEATH`, waive the per-match fee (the entry fee having been
 * paid up front) and, in the Gold Saucer, carry different card pools and richer rewards. The
 * original constructs them inline and never consults `NPCs.LIST`, so a rung is a whole opponent
 * record rather than an override of one. `tools/extract_campaigns.py` reports the differences on
 * every run.
 *
 * @property key `cc` or `gs`, the ladder's stable identity.
 * @property nameKey `STR_CCGROUP` / `STR_GSGROUP`, the title of its entry screen.
 * @property collection which card collection it belongs to. `PVEScreen.as:84,91` shows the Card
 *   Club only to an `ff8_` character and the Gold Saucer only to an `ff14_` one, so no profile can
 *   see both.
 * @property fee what entering costs, once, for the whole ladder — 500 MGP for both.
 */
@Serializable
data class Campaign(
    val key: String,
    val nameKey: String,
    val collection: CardCollection,
    val fee: Int,
    val steps: List<CampaignStep>,
) {
    init {
        require(steps.isNotEmpty()) { "campaign '$key' has no opponents" }
        require(fee >= 0) { "campaign '$key' has a negative fee: $fee" }
    }

    /** The opponents in ladder order, which is also the order the entry screen lists them. */
    val opponents: List<Npc> get() = steps.map { it.npc }

    /** The rung at [step], or null past the end — which is how the ladder knows it is over. */
    fun stepAt(step: Int): CampaignStep? = steps.getOrNull(step)

    /**
     * Where a player who has just finished [step] with [result] goes next.
     *
     * `CCGroupMatchScreen.endGame` writes `NEXT_STEP` on all three branches and the three answers
     * are all different:
     *
     * - **a win** advances (`this.STEP + 1`), and past the last rung [stepAt] returns null, which
     *   is what makes the panel's Next Match disappear — `if (_params.NEXT_STEP < 7)`.
     * - **a loss** sends the player back to the **start** (`NEXT_STEP: 0`). The whole ladder again,
     *   and another 500 MGP, which is what the entry fee is for.
     * - **a draw** repeats the same rung (`NEXT_STEP: this.STEP`). Note that most rungs declare
     *   `RULE_SUDDEN_DEATH`, so a draw usually never reaches this at all.
     */
    fun nextStep(step: Int, result: MatchResult): Int = when (result) {
        MatchResult.WIN -> step + 1
        MatchResult.LOSE -> FIRST_STEP
        MatchResult.DRAW -> step
    }

    companion object {
        /** Where a ladder starts, and where a defeat returns you to. */
        const val FIRST_STEP: Int = 0
    }
}

/**
 * The ladders, as extracted by `tools/extract_campaigns.py`.
 *
 * Its own bundle rather than more of `npcs.json`, because it is not a catalogue of opponents: it is
 * two ordered sequences with a fee and some dialogue, and thirteen of its records are near-twins of
 * ones already in that file. Keeping them apart is what lets the extractor *report* the differences
 * instead of a re-import quietly flattening them.
 */
@Serializable
data class CampaignCatalog(
    @SerialName("campaigns") val all: List<Campaign>,
) {
    /** The ladders an [collection] character can enter — one each, in the shipped data. */
    fun forCollection(collection: CardCollection): List<Campaign> =
        all.filter { it.collection == collection }

    /** One ladder by [Campaign.key], or null. */
    fun byKey(key: String): Campaign? = all.firstOrNull { it.key == key }
}

/** Parses the campaign catalog. Split from its loader for the same reason [NpcCatalogParser] is. */
object CampaignCatalogParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): CampaignCatalog = json.decodeFromString(text)
}
