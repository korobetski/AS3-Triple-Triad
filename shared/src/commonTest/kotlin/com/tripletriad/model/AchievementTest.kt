package com.tripletriad.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [AchievementCatalog] against the 22 entries of `datas/Achievements.as`.
 *
 * The thresholds are pinned from the inline comments in the AS3 `LIST`, which state each one — the
 * only documentation of intent there is.
 */
class AchievementTest {
    @Test
    fun theCatalogueHasTheTwentyTwoAs3Achievements() {
        assertEquals(22, AchievementCatalog.all.size)
        assertEquals(
            AchievementCatalog.all.map { it.id }.distinct().size,
            AchievementCatalog.all.size,
            "ids must be unique — they are the save-file keys",
        )
        // `Achievements.list` (:16-39) and `LIST` (:45-72) are the same 22 ids in the same order.
        assertEquals("ac-tt1", AchievementCatalog.all.first().id)
        assertEquals("ac-fob", AchievementCatalog.all.last().id)
        assertNotNull(AchievementCatalog["ac-wof6"])
        assertEquals(null, AchievementCatalog["ac-nope"])
    }

    @Test
    fun theLabelKeysAreTheAs3I18nKeys() {
        assertEquals("STR_Triple_Team_I", AchievementCatalog["ac-tt1"]!!.labelKey)
        assertEquals("STR_Always_Bet_On_Me", AchievementCatalog["ac-wof6"]!!.labelKey)
        assertEquals("STR_Triple_decker_V", AchievementCatalog["ac-td5"]!!.labelKey)
        assertEquals("STR_FRIEND_OF_BEASTS", AchievementCatalog["ac-fob"]!!.labelKey)
        // `Achievements.as:17` — a numeric FFXIV icon id for the whole Triple Team tier.
        assertEquals("000713", AchievementCatalog["ac-tt3"]!!.iconId)
        assertEquals("ff14_thumb_37", AchievementCatalog["ac-fob"]!!.iconId)
    }

    /** Only three of the 22 carry a reward: `ac-tt3`, `ac-wof5`, and nothing else. */
    @Test
    fun onlyTheTwoAs3RewardsExist() {
        val rewarded = AchievementCatalog.all
            .filter { it.reward != null }
            .associate { it.id to it.reward }

        assertEquals(mapOf("ac-tt3" to CardItem(75), "ac-wof5" to CardItem(79)), rewarded)
    }

    @Test
    fun npcWinTiersMatchTheAs3Thresholds() {
        val save = GameSave(npcWins = mapOf("jonas" to 30))

        assertTrue(AchievementCatalog["ac-tt1"]!!.isEarnedBy(save), "1 win")
        assertTrue(AchievementCatalog["ac-tt2"]!!.isEarnedBy(save), "30 wins")
        assertFalse(AchievementCatalog["ac-tt3"]!!.isEarnedBy(save), "300 wins")
        assertTrue(
            AchievementCatalog["ac-tt5"]!!.isEarnedBy(GameSave(npcWins = mapOf("a" to 7_777))),
        )
    }

    /**
     * `RULES_W` starts empty, so the AS3 compares `undefined >= 1` — false, which is right by
     * accident. A missing key reads as 0 here, which is right on purpose.
     */
    @Test
    fun rouletteTiersReadAMissingKeyAsZero() {
        assertFalse(AchievementCatalog["ac-wof1"]!!.isEarnedBy(GameSave()))

        val save = GameSave(rulesWins = mapOf("RULE_ROULETTE" to 300))

        assertTrue(AchievementCatalog["ac-wof5"]!!.isEarnedBy(save))
        assertFalse(AchievementCatalog["ac-wof6"]!!.isEarnedBy(save), "1000 wins")
    }

    @Test
    fun cardAndMgpTiersMatchTheAs3Thresholds() {
        assertTrue(AchievementCatalog["ac-td1"]!!.isEarnedBy(GameSave(cards = (1..10).toList())))
        assertFalse(AchievementCatalog["ac-td2"]!!.isEarnedBy(GameSave(cards = (1..10).toList())))
        assertTrue(AchievementCatalog["ac-td5"]!!.isEarnedBy(GameSave(cards = (1..110).toList())))
        assertTrue(AchievementCatalog["ac-mp1"]!!.isEarnedBy(GameSave(mgp = 1_000)))
        assertTrue(AchievementCatalog["ac-mp5"]!!.isEarnedBy(GameSave(mgp = 1_000_000)))
        assertFalse(AchievementCatalog["ac-mp5"]!!.isEarnedBy(GameSave(mgp = 999_999)))
    }

    /**
     * `ac-fob` is gated on `MODE == 'ff14_'`: the same ids mean different cards in the other table.
     */
    @Test
    fun friendOfBeastsNeedsAllThirteenBeastCardsOnAnFf14Profile() {
        assertEquals(13, AchievementCatalog.BEAST_CARDS.size)
        assertEquals(BoosterType.BEAST.pool, AchievementCatalog.BEAST_CARDS)

        val complete = GameSave(mode = CardCollection.FF14, cards = AchievementCatalog.BEAST_CARDS)
        assertTrue(AchievementCatalog["ac-fob"]!!.isEarnedBy(complete))

        val oneShort = complete.copy(cards = AchievementCatalog.BEAST_CARDS.dropLast(1))
        assertFalse(AchievementCatalog["ac-fob"]!!.isEarnedBy(oneShort))

        val wrongMode = complete.copy(mode = CardCollection.FF8)
        assertFalse(AchievementCatalog["ac-fob"]!!.isEarnedBy(wrongMode), "ff8 ids are other cards")
    }

    @Test
    fun progressIsReportableBeforeTheThresholdIsMet() {
        val save = GameSave(npcWins = mapOf("jonas" to 15))

        val progress = AchievementCatalog["ac-tt2"]!!.progressFor(save)

        assertEquals(15, progress.current)
        assertEquals(30, progress.target)
        assertEquals(0.5f, progress.fraction)
        assertFalse(progress.isMet)
    }

    @Test
    fun progressForACardSetCountsWhatIsOwned() {
        val save =
            GameSave(mode = CardCollection.FF14, cards = AchievementCatalog.BEAST_CARDS.take(6))

        val progress = AchievementCatalog["ac-fob"]!!.progressFor(save)

        assertEquals(6, progress.current)
        assertEquals(13, progress.target)
    }

    @Test
    fun progressOnTheWrongModeIsZeroRatherThanPartial() {
        val save = GameSave(mode = CardCollection.FF8, cards = AchievementCatalog.BEAST_CARDS)

        assertEquals(0, AchievementCatalog["ac-fob"]!!.progressFor(save).current)
    }

    @Test
    fun fractionIsCappedAtOne() {
        val save = GameSave(npcWins = mapOf("a" to 500))

        assertEquals(1f, AchievementCatalog["ac-tt2"]!!.progressFor(save).fraction)
    }

    @Test
    fun newlyEarnedExcludesWhatIsAlreadyRecorded() {
        val save = GameSave(npcWins = mapOf("jonas" to 1), achievements = mapOf("ac-tt1" to 5L))

        assertTrue(AchievementCatalog.newlyEarned(save).isEmpty())
        assertEquals(
            listOf("ac-tt1"),
            AchievementCatalog.newlyEarned(save.copy(achievements = emptyMap())).map { it.id },
        )
    }

    @Test
    fun aFreshProfileHasEarnedNothing() {
        assertTrue(AchievementCatalog.newlyEarned(GameSave.new(createdAt = 0)).isEmpty())
    }
}
