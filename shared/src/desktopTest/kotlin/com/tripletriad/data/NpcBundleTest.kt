package com.tripletriad.data

import com.tripletriad.model.CardCollection
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.NpcLevel
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the **real** `npcs.json` in the Compose resource bundle, as [NpcCatalogTest] does not.
 *
 * The counts and invariants here are the ones `tools/extract_npcs.py` asserts on the way out.
 * Stated again on the way in, because the extractor is run by hand: a stale `npcs.json` in the
 * bundle is exactly the failure this catches, and the extractor cannot.
 */
class NpcBundleTest {
    private val catalog = runBlocking { loadNpcCatalog() }

    @Test
    fun theBundledCatalogHoldsBothTablesInFull() {
        assertEquals(FF14_NPCS, catalog.ff14.size, "the FF14 opponents")
        assertEquals(FF8_NPCS, catalog.ff8.size, "the FF8 opponents")
        assertEquals(FF14_NPCS + FF8_NPCS, catalog.all.size)
    }

    /**
     * `NPC_W` is keyed by icon, so a collision inside one table would merge two opponents' records.
     */
    @Test
    fun iconIdsAreUniqueWithinEachTable() {
        for (collection in CardCollection.entries) {
            val icons = catalog.collection(collection).map { it.iconId }
            assertEquals(icons.size, icons.distinct().size, "$collection has a duplicate iconID")
        }
    }

    @Test
    fun everyOpponentIsUsable() {
        for (npc in catalog.all) {
            assertTrue(npc.id > 0, "${npc.iconId} has no id")
            assertTrue(npc.nameKey.startsWith("STR_"), "${npc.iconId} name is not an i18n key")
            assertTrue(npc.iconId.isNotBlank())
            assertTrue(npc.matchFee >= 0, "${npc.iconId} has a negative fee")
            assertTrue(
                npc.fetishCards.isNotEmpty() || npc.cards.isNotEmpty(),
                "${npc.iconId} has no cards at all",
            )
        }
    }

    /**
     * Every opponent must be able to field five cards.
     *
     * This is the invariant that makes the AS3 `getRandomCards()` infinite loop unreachable with
     * the shipped data: fifteen entries have an empty pool, and all of them have five fetish cards.
     */
    @Test
    fun everyOpponentCanFieldAFullHand() {
        for (npc in catalog.all) {
            assertTrue(
                npc.fetishCards.size + npc.cards.size >= HAND_SIZE,
                "${npc.iconId} can only field ${npc.fetishCards.size + npc.cards.size} cards",
            )
            assertEquals(HAND_SIZE, npc.randomHand(Random(1)).size, npc.iconId)
        }
    }

    /**
     * Every rule key in the data must be one `gameRules()` maps; an unmapped one is silently
     * dropped.
     */
    /**
     * Every card an opponent can field exists in that opponent's own collection.
     *
     * `PveMatches.assemble` refuses a hand it cannot resolve rather than quietly playing four
     * cards, so this is what keeps that refusal unreachable by playing. It also catches the cross-
     * collection mistake the data invites: card ids are per-table indices, so an `ff8` opponent
     * listing an `ff14` id would resolve to the wrong card rather than to none.
     */
    @Test
    fun everyOpponentCardExistsInItsOwnCollection() {
        val cards = runBlocking { loadCardCatalog() }
        for (collection in CardCollection.entries) {
            val ids = cards.collection(collection.prefix).map { it.id }.toSet()
            for (npc in catalog.collection(collection)) {
                val missing = (npc.fetishCards + npc.cards).filterNot { it in ids }
                assertTrue(
                    missing.isEmpty(),
                    "${npc.iconId} names $missing, absent from $collection",
                )
            }
        }
    }

    /** And a full hand resolves to five real cards, which is what a match actually needs. */
    @Test
    fun everyOpponentResolvesToAFullHandOfRealCards() {
        val cards = runBlocking { loadCardCatalog() }
        for (collection in CardCollection.entries) {
            val ids = cards.collection(collection.prefix).map { it.id }.toSet()
            for (npc in catalog.collection(collection)) {
                val hand = npc.randomHand(Random(1)).filter { it in ids }
                assertEquals(
                    HAND_SIZE,
                    hand.size,
                    "${npc.iconId} cannot field a hand from $collection",
                )
            }
        }
    }

    @Test
    fun everyRuleKeyInTheDataIsMapped() {
        for (npc in catalog.all) {
            val mapped = npc.gameRules().activeRuleKeys()
            for (key in npc.ruleKeys) {
                assertTrue(key in mapped, "${npc.iconId} declares $key, which gameRules() ignores")
            }
        }
    }

    @Test
    fun everyLevelIsOneTheXpFormulaKnows() {
        for (npc in catalog.all) {
            assertTrue(npc.level in NpcLevel.entries, npc.iconId)
        }
        // The shipped data uses all five real bands; NONE would mean an opponent that pays no XP.
        assertEquals(
            setOf(
                NpcLevel.NOVICE,
                NpcLevel.INITIATE,
                NpcLevel.AVERAGE,
                NpcLevel.ADVANCED,
                NpcLevel.EXPERT,
            ),
            catalog.all.map { it.level }.toSet(),
        )
    }

    /** A rate of 0 would be a drop that can never happen — a transcription slip, not a design. */
    @Test
    fun everyItemRewardResolvesAndHasAUsableRate() {
        for (npc in catalog.all) {
            for (reward in npc.itemRewards) {
                assertTrue(
                    reward.item() != null,
                    "${npc.iconId} has a reward this build cannot resolve: $reward",
                )
                assertTrue(
                    reward.rate > 0.0 && reward.rate <= 1.0,
                    "${npc.iconId}: rate ${reward.rate}",
                )
            }
        }
    }

    @Test
    fun availabilityWindowsAreValidHours() {
        val windowed = catalog.all.filterNot { it.availability.isAlwaysAvailable }

        assertTrue(windowed.isNotEmpty(), "some opponents declare a window")
        for (npc in windowed) {
            assertTrue(npc.availability.begins in 0..HOURS, "${npc.iconId}")
            assertTrue(npc.availability.ends in 0..HOURS, "${npc.iconId}")
        }
    }

    /** Whatever the hour, the opponent list is non-empty — otherwise PvE would be unreachable. */
    @Test
    fun someOpponentIsAvailableAtEveryHour() {
        for (hour in 0 until HOURS) {
            for (collection in CardCollection.entries) {
                assertTrue(
                    catalog.available(collection, hour, level = ANY_LEVEL).isNotEmpty(),
                    "$collection has no opponent at $hour:00",
                )
            }
        }
    }

    /**
     * The two entries whose pool is `cards.getCardsByRarities(...)` must have been resolved to ids.
     */
    @Test
    fun theQueenOfCardsPoolWasResolvedAtExtractionTime() {
        val ff14Queen = catalog.byIcon("queen-of-cards", CardCollection.FF14)!!
        val ff8Queen = catalog.byIcon("queen-of-cards", CardCollection.FF8)!!

        assertEquals(FF14_CARDS, ff14Queen.cards.size, "every ff14 card, rarities 1-5")
        assertTrue(
            ff8Queen.cards.isNotEmpty() && ff8Queen.cards.size < FF8_CARDS,
            "ff8 rarities 1-4",
        )
        assertTrue(ff14Queen.cards.all { it > 0 }, "index 0 is the Back sentinel and is not a card")
    }

    private companion object {
        const val FF14_NPCS = 60
        const val FF8_NPCS = 25
        const val FF14_CARDS = 153
        const val FF8_CARDS = 110
        const val HOURS = 24
    }
}

/**
 * A level high enough that [com.tripletriad.data.NpcCatalog.available]'s gate cannot bite.
 *
 * The tests that pass it are about the **hour** window or about a named opponent, and would
 * otherwise be asserting the level rule by accident. `OpponentUiTest` tests the gate itself.
 */
private const val ANY_LEVEL: Int = 99
