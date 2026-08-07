package com.tripletriad.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [GameSave] against `datas/Save.as`: the defaults, the on-disk key names, and the derived fields.
 *
 * The key names are pinned deliberately. Every field has a default and `ignoreUnknownKeys` is on,
 * so a renamed `@SerialName` would not fail to parse — it would silently read as its default and
 * orphan the data behind it. That is exactly the failure this file exists to catch.
 */
class GameSaveTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** `Save.setToDefaultValues()` (`Save.as:21-48`), field for field. */
    @Test
    fun aNewProfileMatchesSetToDefaultValues() {
        val save = GameSave.new(createdAt = 1_700_000_000_000)

        assertEquals("Kuplu Kopo", save.username)
        assertEquals(CardCollection.FF14, save.mode)
        assertEquals(0, save.admin)
        assertEquals(listOf(1, 3, 6, 7, 10), save.cards)
        assertEquals(1, save.decks.size)
        assertEquals("Starter deck", save.decks.first().name)
        assertEquals(listOf(1, 3, 6, 7, 10), save.decks.first().cards)
        assertEquals(Stats(), save.stats)
        assertTrue(save.bag.isEmpty())
        assertEquals(Boons(), save.boons)
        assertEquals(100, save.mgp)
        assertEquals(0L, save.xp)
        assertEquals(1, save.level)
        assertEquals(0L, save.pvpXp)
        assertEquals(1, save.rank)
        assertEquals("ffxiv_twi03005", save.avatarId)
        assertEquals(0, save.startedMatches)
        assertEquals(0, save.endedMatches)
        assertTrue(save.achievements.isEmpty())
        assertTrue(save.npcWins.isEmpty())
        assertTrue(save.rulesWins.isEmpty())
        assertEquals(1_700_000_000_000, save.creationDate)
        assertEquals(save.creationDate, save.lastSave, "a new profile has never been saved since")
    }

    /** The starter deck must be playable, or a new profile cannot start a match. */
    @Test
    fun theStarterDeckIsComplete() {
        assertTrue(GameSave.new(createdAt = 0).decks.first().isComplete)
        assertEquals(HAND_SIZE, GameSave.DEFAULT_CARDS.size)
    }

    @Test
    fun theOnDiskKeysAreTheAs3Ones() {
        val encoded = json.encodeToString(GameSave.new(createdAt = 1))

        for (key in listOf(
            "USERNAME", "CREATION_DATE", "LAST_SAVE", "SAVE_NUMBER", "MODE", "ADMIN", "CARDS",
            "DECKS", "STATS", "BAG", "BOONS", "MGP", "XP", "LEVEL", "PVP_XP", "RANK", "AVATAR_ID",
            "STARTED_MATCHES", "ENDED_MATCHES", "PVE_MATCHES", "PVP_MATCHES", "ACHIEVEMENTS",
            "NPC_W", "RULES_W",
        )) {
            assertTrue(encoded.contains("\"$key\""), "$key is missing from $encoded")
        }
        assertTrue(encoded.contains("\"ff14_\""), "MODE is the AS3 prefix string")
        assertTrue(encoded.contains("\"WINS\""), "STATS keeps its AS3 keys too")
        assertFalse(encoded.contains("FORFEITS"), "FORFEITS is derived, never stored")
    }

    /**
     * A profile as the original writes it: `JSON.stringify(DATAS)`, so unordered, with
     * `NPC_W_TOTAL` present (the AS3 assigns it onto `PROFILE_DATAS`, so it lands in the file) and
     * `FORFEITS` inside `STATS`. Both are ignored on the way in.
     */
    @Test
    fun aProfileWrittenByTheAs3BuildStillParses() {
        val legacy = """
            {"CREATION_DATE":1500000000000,"LAST_SAVE":1500000100000,"SAVE_NUMBER":7,
             "USERNAME":"Mao","MODE":"ff8_","ADMIN":1,"CARDS":[1,3,6,7,10,42],
             "DECKS":[{"name":"Starter deck","cards":[1,3,6,7,10]}],
             "STATS":{"WINS":12,"DEFEATS":3,"DRAWS":1,"FORFEITS":99},
             "BAG":[{"type":"item-type-card","card":42,"stack":1}],
             "BOONS":{"MGP":5,"XP":0,"LUCK":0},"MGP":1250,"XP":900,"LEVEL":3,
             "PVP_XP":0,"RANK":1,"AVATAR_ID":"ffxiv_twi03005",
             "STARTED_MATCHES":20,"ENDED_MATCHES":16,"PVE_MATCHES":18,"PVP_MATCHES":2,
             "ACHIEVEMENTS":{"ac-tt1":1500000050000},"NPC_W":{"jonas":4},
             "RULES_W":{"RULE_PLUS":2},"NPC_W_TOTAL":4}
        """.trimIndent()

        val save = json.decodeFromString<GameSave>(legacy)

        assertEquals("Mao", save.username)
        assertEquals(CardCollection.FF8, save.mode)
        assertEquals(1, save.admin)
        assertEquals(7, save.saveNumber)
        assertEquals(12, save.stats.wins)
        assertEquals(listOf(CardItem(42, 1)), save.bag)
        assertEquals(mapOf("ac-tt1" to 1_500_000_050_000L), save.achievements)
        assertEquals(4, save.npcWinsTotal)
        // FORFEITS in the file said 99; it is STARTED - ENDED, as `Save.as:59` recomputes.
        assertEquals(4, save.forfeits)
    }

    /**
     * A file from an older build with fields missing: `Save.as:55-58` defaults four, this defaults
     * all.
     */
    @Test
    fun aProfileMissingFieldsLoadsWithDefaults() {
        val save = json.decodeFromString<GameSave>("""{"USERNAME":"Sparse"}""")

        assertEquals("Sparse", save.username)
        assertEquals(CardCollection.FF14, save.mode)
        assertEquals(listOf(1, 3, 6, 7, 10), save.cards)
        assertTrue(save.achievements.isEmpty())
    }

    @Test
    fun forfeitsCannotGoNegative() {
        val save = GameSave(startedMatches = 3, endedMatches = 9)

        assertEquals(0, save.forfeits)
    }

    @Test
    fun saneRecomputesLevelAndRankFromXp() {
        // A file claiming level 20 on 250 XP: the XP wins.
        val save = GameSave(xp = 250, level = 20, pvpXp = 450, rank = 1).sane()

        assertEquals(2, save.level)
        assertEquals(3, save.rank)
    }

    @Test
    fun saneClampsNegativesAndTidiesCollections() {
        val save = GameSave(
            mgp = -50,
            xp = -1,
            pvpXp = -1,
            cards = listOf(7, 3, 3, 0, -2, 1),
            bag = listOf(CardItem(1, 0), CardItem(2, 3)),
            decks = List(9) { Deck("deck $it") },
        ).sane()

        assertEquals(0, save.mgp)
        assertEquals(0L, save.xp)
        assertEquals(0L, save.pvpXp)
        assertEquals(listOf(1, 3, 7), save.cards, "distinct, ascending, positive")
        assertEquals(listOf(CardItem(2, 3)), save.bag, "an empty stack is not an item")
        assertEquals(GameSave.MAX_DECKS, save.decks.size, "Save.as:31 caps decks at five")
    }

    @Test
    fun matchCountersMoveTogether() {
        val save = GameSave()
            .startingMatch(againstNpc = true)
            .startingMatch(againstNpc = false)
            .endingMatch()

        assertEquals(2, save.startedMatches)
        assertEquals(1, save.endedMatches)
        assertEquals(1, save.pveMatches)
        assertEquals(1, save.pvpMatches)
        assertEquals(1, save.forfeits)
    }

    @Test
    fun mgpNeverGoesNegative() {
        assertEquals(0, GameSave(mgp = 10).withMgp(-100).mgp)
        assertEquals(60, GameSave(mgp = 10).withMgp(50).mgp)
    }

    @Test
    fun gainingXpRaisesTheLevelWithIt() {
        val save = GameSave().withXp(450).withPvpXp(250)

        assertEquals(450L, save.xp)
        assertEquals(3, save.level)
        assertEquals(2, save.rank)
    }

    @Test
    fun cardsAreASet() {
        val save = GameSave(cards = listOf(1, 3)).withCard(2).withCard(2).withCard(1)

        assertEquals(listOf(1, 2, 3), save.cards)
        assertTrue(save.ownsCard(2))
        assertFalse(save.ownsCard(99))
    }

    /** `PVEMatchScreen.as:110` keys `NPC_W` by the NPC's `iconID`, not by its id. */
    @Test
    fun npcWinsAreKeyedByIcon() {
        val save = GameSave().withNpcWin("jonas").withNpcWin("jonas").withNpcWin("tt-master")

        assertEquals(mapOf("jonas" to 2, "tt-master" to 1), save.npcWins)
        assertEquals(3, save.npcWinsTotal)
    }

    @Test
    fun ruleWinsCountEveryActiveRule() {
        val rules = GameRules(open = OpenRule.ALL_OPEN, plus = true, roulette = true)

        val save = GameSave().withRulesWin(rules).withRulesWin(rules)

        assertEquals(
            mapOf("RULE_ALL_OPEN" to 2, "RULE_PLUS" to 2, "RULE_ROULETTE" to 2),
            save.rulesWins,
        )
    }

    /** The absence of a rule is not a rule: default slots must not be counted. */
    @Test
    fun ruleWinsIgnoreTheDefaultSlots() {
        assertTrue(GameSave().withRulesWin(GameRules()).rulesWins.isEmpty())
    }

    @Test
    fun anAchievementKeepsTheFirstTimeItWasEarned() {
        val save = GameSave().withAchievement("ac-tt1", 100).withAchievement("ac-tt1", 200)

        assertEquals(mapOf("ac-tt1" to 100L), save.achievements)
        assertTrue(save.hasAchievement("ac-tt1"))
    }

    @Test
    fun statsRecordOutcomes() {
        val score = MatchScore(6, 4)
        val stats = Stats()
            .recording(MatchOutcome.Win(CardColor.BLUE, score))
            .recording(MatchOutcome.Draw(MatchScore(5, 5)))
            .recording(MatchOutcome.SuddenDeath(MatchScore(5, 5)))
            .recordingDefeat()

        assertEquals(1, stats.wins)
        assertEquals(1, stats.draws)
        assertEquals(1, stats.defeats)
        assertEquals(3, stats.played, "an unresolved sudden death is not a finished match")
        assertEquals(1f / 3f, stats.winRate)
    }

    @Test
    fun winRateIsZeroBeforeAnyMatch() {
        assertEquals(0f, Stats().winRate)
    }

    @Test
    fun potionsRaiseTheMatchingBoon() {
        val boons = Boons().raised(PotionType.XP.modifier).raised(PotionType.BIG_MGP.modifier)

        assertEquals(5, boons.xp)
        assertEquals(10, boons.mgp)
        assertEquals(0, boons.luck, "no potion grants LUCK")
    }

    @Test
    fun collectionPrefixesRoundTrip() {
        assertEquals(CardCollection.FF14, CardCollection.forPrefix("ff14_"))
        assertEquals(CardCollection.FF8, CardCollection.forPrefix("ff8_"))
        assertEquals(null, CardCollection.forPrefix("ff7_"))
        assertEquals("ff8_", CardCollection.FF8.prefix)
    }

    // ---- Decks -----------------------------------------------------------

    @Test
    fun addingCardsFillsADeckAndStopsAtFive() {
        var deck = Deck("Test", emptyList())
        for (id in 1..7) deck = deck.plusCard(id)

        assertEquals(listOf(1, 2, 3, 4, 5), deck.cards, "a deck holds $HAND_SIZE and no more")
        assertTrue(deck.isComplete)
    }

    @Test
    fun removingACardLeavesTheRestInOrder() {
        val deck = Deck("Test", listOf(10, 20, 30, 40, 50)).minusCardAt(1)

        assertEquals(listOf(10, 30, 40, 50), deck.cards)
        assertFalse(deck.isComplete)
        assertEquals(deck, deck.minusCardAt(9), "removing what is not there changes nothing")
    }

    /** The same card twice is allowed — see [Deck.plusCard], and `RULE_SWAP`. */
    @Test
    fun aDeckMayHoldTheSameCardTwice() {
        assertEquals(listOf(3, 3), Deck("Test", listOf(3)).plusCard(3).cards)
    }

    @Test
    fun writingASlotBeyondTheEndFillsTheOnesBefore() {
        val deck = Deck("Third", listOf(1, 2, 3, 4, 5))

        val save = GameSave.new(createdAt = 0L).withDeck(2, deck)

        assertEquals(3, save.decks.size)
        assertEquals(deck, save.decks[2])
        assertEquals("", save.decks[1].name, "the filled slot is unnamed — the label is the UI's")
        assertTrue(save.decks[1].cards.isEmpty())
        assertEquals(GameSave.DEFAULT_DECK_NAME, save.decks[0].name, "slot 0 is untouched")
    }

    @Test
    fun aSlotOutsideTheFiveIsAProgrammingError() {
        val save = GameSave.new(createdAt = 0L)

        assertFailsWith<IllegalArgumentException> { save.withDeck(GameSave.MAX_DECKS, Deck("x")) }
        assertFailsWith<IllegalArgumentException> { save.withDeck(-1, Deck("x")) }
    }

    /**
     * Clearing empties the cards and keeps the slot — which is what the button claims and what the
     * original does not do. See [GameSave.clearingDeck].
     */
    @Test
    fun clearingADeckEmptiesItAndKeepsItsName() {
        val save = GameSave.new(createdAt = 0L).clearingDeck(0)

        assertEquals(1, save.decks.size)
        assertEquals(GameSave.DEFAULT_DECK_NAME, save.decks[0].name)
        assertTrue(save.decks[0].cards.isEmpty())
        assertFalse(save.decks[0].isComplete)
    }

    @Test
    fun clearingASlotThatDoesNotExistChangesNothing() {
        val save = GameSave.new(createdAt = 0L)

        assertEquals(save, save.clearingDeck(3))
    }

    @Test
    fun deckSlotsSurviveARoundTrip() {
        val save = GameSave.new(createdAt = 1L)
            .withDeck(1, Deck("Second", listOf(2, 4, 6, 8, 10)))
            .withDeck(3, Deck("Fourth", listOf(1, 2)))

        val decoded = json.decodeFromString<GameSave>(json.encodeToString(save))

        assertEquals(save.decks, decoded.decks)
        assertEquals(4, decoded.decks.size, "the unnamed filler slot is on disk too")
    }
}
