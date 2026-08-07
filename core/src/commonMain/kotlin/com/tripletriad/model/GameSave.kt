package com.tripletriad.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Win/loss record. `Save.DATAS.STATS` (`Save.as:32`). */
@Serializable
data class Stats(
    @SerialName("WINS") val wins: Int = 0,
    @SerialName("DEFEATS") val defeats: Int = 0,
    @SerialName("DRAWS") val draws: Int = 0,
) {
    val played: Int get() = wins + defeats + draws

    /** Wins as a fraction of matches finished, 0f when none have been. */
    val winRate: Float get() = if (played == 0) 0f else wins.toFloat() / played

    fun recording(outcome: MatchOutcome): Stats = when (outcome) {
        is MatchOutcome.Win -> copy(wins = wins + 1)
        is MatchOutcome.Draw -> copy(draws = draws + 1)
        // A sudden-death draw is not an outcome yet — the rematch decides it — so it counts as
        // nothing. `PVEMatchScreen.as:175` dispatches the rematch instead of touching STATS.
        is MatchOutcome.SuddenDeath -> this
    }

    /**
     * Records a loss. Separate from [recording] because [MatchOutcome.Win] carries the winner and
     * only the caller knows which side the profile was on.
     */
    fun recordingDefeat(): Stats = copy(defeats = defeats + 1)

    /**
     * Records [result] from the profile's point of view — the counter each `endGame` branch bumps
     * (`PVEMatchScreen.as:71`, `:100`, `:141`).
     *
     * [recording] and [recordingDefeat] take an outcome and cannot tell a win from a loss without
     * knowing which side the profile played; this takes the already-resolved result, which is what
     * a caller holding a [MatchResult] has. Both are kept: the outcome pair is what a PvP screen
     * has, and the sudden-death case has to be unrepresentable in this one.
     */
    fun recordingStats(result: MatchResult): Stats = when (result) {
        MatchResult.WIN -> copy(wins = wins + 1)
        MatchResult.DRAW -> copy(draws = draws + 1)
        MatchResult.LOSE -> copy(defeats = defeats + 1)
    }
}

/**
 * The three multipliers a potion can raise. `Save.DATAS.BOONS` (`Save.as:34`).
 *
 * [luck] is in the save file and in nothing else: no potion grants it ([PotionType] has no LUCK
 * member) and no rule reads it. Carried so a profile round-trips, and no further.
 */
@Serializable
data class Boons(
    @SerialName("MGP") val mgp: Int = 0,
    @SerialName("XP") val xp: Int = 0,
    @SerialName("LUCK") val luck: Int = 0,
) {
    /** Applies a potion's effect. `PotionItem.modifier` is the only producer. */
    fun raised(modifier: BoonModifier): Boons = when (modifier.type) {
        BoonType.XP -> copy(xp = xp + modifier.value)
        BoonType.MGP -> copy(mgp = mgp + modifier.value)
    }

    /**
     * Consumes one boon of [type] — `Game.PROFILE_DATAS.BOONS.MGP -= 1` (`PVEMatchScreen.as:76`).
     *
     * So a boon is a **count of boosted matches**, not a permanent multiplier: a small MGP potion
     * ([PotionType.SMALL_MGP], value 2) buys two matches paying 20% more. Clamped at zero, because
     * the AS3 stores these as `uint` arithmetic on an untyped object and a negative count has no
     * meaning either way.
     */
    fun spending(type: BoonType): Boons = when (type) {
        BoonType.XP -> copy(xp = (xp - 1).coerceAtLeast(0))
        BoonType.MGP -> copy(mgp = (mgp - 1).coerceAtLeast(0))
    }
}

/**
 * A saved five-card deck. `Save.DATAS.DECKS` (`Save.as:31`), whose comment caps it at 5 decks.
 *
 * The card list is **not** validated to five here: the AS3 deck builder allows a partial deck to be
 * saved and `DeckSelector` refuses to start a match with one. Validating at construction would make
 * a legitimately half-built deck unloadable.
 */
@Serializable
data class Deck(
    @SerialName("name") val name: String,
    @SerialName("cards") val cards: List<Int> = emptyList(),
) {
    val isComplete: Boolean get() = cards.size == HAND_SIZE

    /** The same deck with no cards in it. Keeps the name, which is what the slot is known by. */
    fun emptied(): Deck = copy(cards = emptyList())

    /**
     * The same deck with [cardId] added, or unchanged when it is already full.
     *
     * ### Why adding appends instead of filling a chosen slot
     *
     * The AS3 editor is slot-addressed: five fixed `Card` clips, tap a card then tap the slot to
     * put it in, and `saveDeck_Handler` pushes `0` for each clip left empty — so `DECKS[n].cards`
     * is always five long with zeroes standing in for holes, and `DeckSelector` counts the non-zero
     * entries to decide whether the deck may be played.
     *
     * A hole is not worth representing. The **only** thing a card's position in a deck decides is
     * the play order under `RULE_ORDER`, and a hand dealt from a list with zeroes in it would be
     * short. So the list here holds cards and nothing else, [isComplete] is a size check, and the
     * editor offers add and remove rather than five addressable slots. A player who wants a
     * different order removes and re-adds, which is the same number of taps as the original's
     * tap-card-then-tap-slot.
     *
     * A duplicate is allowed through: nothing in the original prevents the same card appearing
     * twice in a deck either, and [Card] ids in a hand are not required to be distinct — see
     * `RULE_SWAP`, which can produce a genuine duplicate mid-match.
     */
    fun plusCard(cardId: Int): Deck =
        if (cards.size >= HAND_SIZE) this else copy(cards = cards + cardId)

    /** The same deck without the card at position [at]. Unchanged if there is none. */
    fun minusCardAt(at: Int): Deck =
        if (at !in cards.indices) this else copy(cards = cards.filterIndexed { i, _ -> i != at })
}

/**
 * A whole profile — one `.sav` file.
 *
 * Field-for-field `Save.DATAS` as `setToDefaultValues()` builds it (`Save.as:21-48`), with the AS3
 * `SCREAMING_CASE` keys kept as `@SerialName`s. Those names are load-bearing in the same way
 * [com.tripletriad.settings.UserSettings]' snake_case ones are: they are what is on disk, and
 * renaming a field silently would orphan the data behind it. Every field has a default, so a save
 * written by an older build still loads.
 *
 * ### What is *not* a field
 *
 * - **`STATS.FORFEITS`** is derived, not stored: `Save.load()` overwrites whatever the file said
 *   with `STARTED_MATCHES - ENDED_MATCHES` on every load (`Save.as:59`), so the stored value never
 *   survives a round trip. It is [forfeits] here.
 * - **`NPC_W_TOTAL`**, which `Achievements.as` reads off `Game.PROFILE_DATAS`, is nowhere in
 *   `Save.DATAS` — it is computed elsewhere from `NPC_W`. It is [npcWinsTotal] here.
 * - **`LEVEL` and `RANK` are stored but redundant**: both are pure functions of the XP fields
 *   ([XpTable]). They are kept as fields because they are in the file, and [sane] recomputes them
 *   on load so a hand-edited or stale value cannot disagree with the XP it claims to be.
 *
 * ### Types that differ from the migration plan
 *
 * `docs/migration/13-DATA-MODELS.md` types `achievements` as `Map<String, Boolean>`. It is a
 * **timestamp**: `Achievements.check()` writes `ACHIEVEMENTS[id] = new Date().getTime()`
 * (`Achievements.as:79`), which is what lets the UI say when one was earned. Modelled as
 * `Map<String, Long>`.
 *
 * The same document types card ids as `UInt`. They are `Int` here, matching [Card.id] — see the
 * note there; `UInt` buys a range no card comes near and costs interoperability with every list
 * API.
 */
@Serializable
data class GameSave(
    @SerialName("USERNAME") val username: String = DEFAULT_USERNAME,
    @SerialName("CREATION_DATE") val creationDate: Long = 0L,
    @SerialName("LAST_SAVE") val lastSave: Long = 0L,
    @SerialName("SAVE_NUMBER") val saveNumber: Int = 0,
    /** `"ff14_"` or `"ff8_"` — the collection prefix, as [CardCollection]. */
    @SerialName("MODE") val mode: CardCollection = CardCollection.FF14,
    /** `0` or `1`. An AS3 `uint` used as a flag; kept as written. */
    @SerialName("ADMIN") val admin: Int = 0,
    /** Card ids owned. The starter five are `Save.as:30`. */
    @SerialName("CARDS") val cards: List<Int> = DEFAULT_CARDS,
    @SerialName("DECKS") val decks: List<Deck> = listOf(Deck(DEFAULT_DECK_NAME, DEFAULT_CARDS)),
    @SerialName("STATS") val stats: Stats = Stats(),
    /** The inventory. `Save.as:33` — a list of `Item.__toJSON()` objects. */
    @SerialName("BAG") val bag: List<Item> = emptyList(),
    @SerialName("BOONS") val boons: Boons = Boons(),
    @SerialName("MGP") val mgp: Int = STARTING_MGP,
    @SerialName("XP") val xp: Long = 0L,
    @SerialName("LEVEL") val level: Int = 1,
    @SerialName("PVP_XP") val pvpXp: Long = 0L,
    @SerialName("RANK") val rank: Int = 1,
    @SerialName("AVATAR_ID") val avatarId: String = DEFAULT_AVATAR,
    @SerialName("STARTED_MATCHES") val startedMatches: Int = 0,
    @SerialName("ENDED_MATCHES") val endedMatches: Int = 0,
    @SerialName("PVE_MATCHES") val pveMatches: Int = 0,
    @SerialName("PVP_MATCHES") val pvpMatches: Int = 0,
    /** Achievement id to the epoch-millis instant it was earned. */
    @SerialName("ACHIEVEMENTS") val achievements: Map<String, Long> = emptyMap(),
    /**
     * Wins per NPC, keyed by the NPC's **`iconID`** — `'jonas'`, `'tt-master'`. `NPC_W`.
     *
     * Not by `id`, however much that reads like the natural key: `PVEMatchScreen.as:110` writes
     * `NPC_W[this._NPC.iconID]`, and every other site does the same. That turns out to matter
     * rather than being an eccentricity, because **NPC ids are not unique**: the ff8 table declares
     * `id:2` twice (Chocoboy and UFO) and `id:13` twice, so keying by id would silently merge two
     * opponents' records. Icon ids are unique across both tables.
     */
    @SerialName("NPC_W") val npcWins: Map<String, Int> = emptyMap(),
    /**
     * Rule constant to wins with it active. `RULES_W`, read by the Wheel-of-Fortune achievements.
     */
    @SerialName("RULES_W") val rulesWins: Map<String, Int> = emptyMap(),
) {
    /**
     * Matches begun and abandoned. `Save.as:59`, and the reason `STATS.FORFEITS` is not stored.
     *
     * `@Transient` is valid here — unlike on the body properties `docs/migration/13-DATA-MODELS.md`
     * warned about — because this is a computed property with no backing field, so there is nothing
     * for the serializer to write in the first place. The annotation is redundant and omitted.
     */
    val forfeits: Int get() = (startedMatches - endedMatches).coerceAtLeast(0)

    /**
     * Total NPC defeats, which `Achievements.as` reads as `NPC_W_TOTAL`.
     *
     * The original computes the same sum in `PVEMatchScreen.as:169-172` and then **assigns it back
     * onto `PROFILE_DATAS`**, so `JSON.stringify(DATAS)` writes a `NPC_W_TOTAL` key into the file
     * even though `setToDefaultValues()` never declares one. Derived data in a save file: it is
     * ignored on load here (`ignoreUnknownKeys`) and not written back, so it disappears the first
     * time a legacy-shaped profile is re-saved.
     */
    val npcWinsTotal: Int get() = npcWins.values.sum()

    /** True once the profile has earned [id]. */
    fun hasAchievement(id: String): Boolean = achievements.containsKey(id)

    /** True once the profile owns [cardId]. */
    fun ownsCard(cardId: Int): Boolean = cards.contains(cardId)

    /**
     * The same profile with derived fields brought back in line with their sources, and volumes of
     * nonsense clamped.
     *
     * The counterpart of [com.tripletriad.settings.UserSettings.sane], and for the same reason: a
     * `.sav` is user-writable and outlives the build that wrote it. [SaveRepository] applies this
     * on every load and before every write, so an inconsistent profile cannot reach the game and
     * cannot be created by it.
     */
    fun sane(): GameSave = copy(
        level = XpTable.levelFor(xp.coerceAtLeast(0)),
        rank = XpTable.rankFor(pvpXp.coerceAtLeast(0)),
        xp = xp.coerceAtLeast(0),
        pvpXp = pvpXp.coerceAtLeast(0),
        mgp = mgp.coerceAtLeast(0),
        // Distinct and ascending, so the collection screen cannot show a card twice.
        cards = cards.filter { it > 0 }.distinct().sorted(),
        // Empty stacks are not items. The AS3 leaves them in the bag and draws a "0".
        bag = bag.filter { it.stack > 0 },
        decks = decks.take(MAX_DECKS),
    )

    /**
     * Records that a match has begun: `STARTED_MATCHES` up, and the per-mode counter with it.
     *
     * Kept on the model rather than in the repository because the two counters must move together —
     * that is what makes [forfeits] mean anything.
     */
    fun startingMatch(againstNpc: Boolean): GameSave = copy(
        startedMatches = startedMatches + 1,
        pveMatches = if (againstNpc) pveMatches + 1 else pveMatches,
        pvpMatches = if (againstNpc) pvpMatches else pvpMatches + 1,
    )

    /** Records that a match has finished, whatever its result. */
    fun endingMatch(): GameSave = copy(endedMatches = endedMatches + 1)

    /** Adds [amount] MGP, or removes it. Never goes below zero — the shop checks affordability. */
    fun withMgp(amount: Int): GameSave = copy(mgp = (mgp + amount).coerceAtLeast(0))

    /** Adds PvE experience and recomputes [level]. */
    fun withXp(amount: Long): GameSave =
        copy(xp = xp + amount).let { it.copy(level = XpTable.levelFor(it.xp)) }

    /** Adds PvP experience and recomputes [rank]. */
    fun withPvpXp(amount: Long): GameSave =
        copy(pvpXp = pvpXp + amount).let { it.copy(rank = XpTable.rankFor(it.pvpXp)) }

    /** Adds [cardId] to the collection. A no-op if it is already there — cards are a set. */
    fun withCard(cardId: Int): GameSave =
        if (ownsCard(cardId)) this else copy(cards = (cards + cardId).sorted())

    /**
     * Records a win against an NPC, for the Triple Team achievements.
     *
     * @param npcIconId the NPC's [Npc.iconId] — the key `NPC_W` actually uses. See [npcWins].
     */
    fun withNpcWin(npcIconId: String): GameSave =
        copy(npcWins = npcWins + (npcIconId to (npcWins[npcIconId] ?: 0) + 1))

    /**
     * Records a win with each of [rules]' active rules, for the Wheel-of-Fortune achievements.
     *
     * The keys are the AS3 rule constants (`RULE_ROULETTE`, …) because that is what
     * `Achievements.as:52-57` looks up. [GameRules.activeRuleKeys] is the single place that mapping
     * lives.
     */
    fun withRulesWin(rules: GameRules): GameSave {
        val updated = rulesWins.toMutableMap()
        for (key in rules.activeRuleKeys()) {
            updated[key] = (updated[key] ?: 0) + 1
        }
        return copy(rulesWins = updated)
    }

    /**
     * The same profile with deck slot [index] replaced by [deck].
     *
     * Slots below [index] that do not exist yet are filled with **unnamed empty decks**, because
     * `DECKS` is a fixed five-slot array on screen and a sparse one in the data: the AS3 assigns
     * `DECKS[selectedIndex] = deck` directly (`DecksScreen.as:385`), which on an AS3 array leaves
     * `undefined` holes that `JSON.stringify` writes as `null` and `if (_userDecks[i])` then reads
     * back as "empty deck". A `List<Deck>` cannot hold that hole, and making the list nullable to
     * model it would push the null onto every reader; an empty deck is what the screen draws for a
     * hole anyway, and [Deck.isComplete] keeps it out of every match either way.
     *
     * The name is left blank rather than defaulted to `"Deck 2"`: that label is `STR_DECK` plus a
     * number, and a model that reached into the locale bundles would put the player's language into
     * their save file.
     *
     * @throws IllegalArgumentException if [index] is not a slot — a screen offers exactly
     *   [MAX_DECKS] of them, so an out-of-range index is a programming error and not a user action.
     */
    fun withDeck(index: Int, deck: Deck): GameSave {
        require(index in 0 until MAX_DECKS) { "deck slot must be in 0..<$MAX_DECKS, was $index" }
        val slots = decks.toMutableList()
        while (slots.size <= index) slots += Deck(name = "", cards = emptyList())
        slots[index] = deck
        return copy(decks = slots)
    }

    /**
     * Empties deck slot [index], keeping the slot and its name.
     *
     * `resetDeckHandler` (`DecksScreen.as:342-362`) is the one place the original is not merely
     * awkward but **does not work**: it pushes five zeroes onto the deck's existing card list — so
     * a full deck becomes ten entries rather than none — and then calls
     * `Game.PROFILE_DATAS.DECKS.slice(index, 1)`, which returns a copy and mutates nothing, where
     * `splice` was meant. It then saves. The visual list is rebuilt empty, the file is not, and the
     * deck reappears on the next load.
     *
     * Emptying the cards and keeping the slot is what the button visibly claims to do.
     */
    fun clearingDeck(index: Int): GameSave {
        if (index !in decks.indices) return this
        return copy(
            decks = decks.mapIndexed { at, deck -> if (at == index) deck.emptied() else deck },
        )
    }

    /** Marks [id] earned at [instant], keeping the first time if it was earned already. */
    fun withAchievement(id: String, instant: Long): GameSave =
        if (hasAchievement(id)) this else copy(achievements = achievements + (id to instant))

    companion object {
        /** `Save.as:27`. A moogle name, and the original's default. */
        const val DEFAULT_USERNAME = "Kuplu Kopo"

        /** `Save.as:40`. */
        const val DEFAULT_AVATAR = "ffxiv_twi03005"

        /** `Save.as:31`, and the comment there that caps decks at five. */
        const val DEFAULT_DECK_NAME = "Starter deck"
        const val MAX_DECKS = 5

        /** `Save.as:35`. */
        const val STARTING_MGP = 100

        /** `Save.as:30`. The same five ids seed the collection and the starter deck. */
        val DEFAULT_CARDS: List<Int> = listOf(1, 3, 6, 7, 10)

        /**
         * A brand-new profile.
         *
         * `setToDefaultValues()` (`Save.as:21-48`), with the two timestamps injected rather than
         * read from a clock: `commonMain` has no `System.currentTimeMillis`, and a model whose
         * defaults read the wall clock cannot be tested — the same reasoning
         * `docs/migration/13-DATA-MODELS.md` applies to `GameState`.
         */
        fun new(
            username: String = DEFAULT_USERNAME,
            mode: CardCollection = CardCollection.FF14,
            createdAt: Long,
        ): GameSave = GameSave(
            username = username,
            creationDate = createdAt,
            lastSave = createdAt,
            mode = mode,
        )
    }
}
