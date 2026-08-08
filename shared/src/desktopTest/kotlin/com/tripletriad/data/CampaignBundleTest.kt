package com.tripletriad.data

import com.tripletriad.model.CardCollection
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.MatchResult
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the **real** `campaigns.json` in the Compose resource bundle.
 *
 * Same bargain as [NpcBundleTest]: `tools/extract_campaigns.py` asserts these counts on the way
 * out, and they are asserted again on the way in because the extractor is run by hand. A stale
 * bundle is exactly the failure this catches and the extractor cannot.
 */
class CampaignBundleTest {
    private val catalog = runBlocking { loadCampaignCatalog() }
    private val cards = runBlocking { loadCardCatalog() }

    @Test
    fun bothLaddersShipInFull() {
        assertEquals(listOf("cc", "gs"), catalog.all.map { it.key })
        assertEquals(CARD_CLUB_RUNGS, assertNotNull(catalog.byKey("cc")).steps.size)
        assertEquals(GOLD_SAUCER_RUNGS, assertNotNull(catalog.byKey("gs")).steps.size)
    }

    /**
     * One ladder per collection, and never both.
     *
     * `PVEScreen.as:84,91` gates each on `MODE`, so an `ff8_` character sees the Card Club and an
     * `ff14_` one the Gold Saucer. Asserted as a property of the data rather than of the screen,
     * because the screen reading it is what the port replaced.
     */
    @Test
    fun eachCollectionHasExactlyOneLadder() {
        for (collection in CardCollection.entries) {
            assertEquals(
                1,
                catalog.forCollection(collection).size,
                "$collection should have one ladder",
            )
        }
    }

    /** 500 MGP, both of them, which is what makes losing the last rung expensive. */
    @Test
    fun enteringCostsFiveHundred() {
        for (campaign in catalog.all) {
            assertEquals(ENTRY_FEE, campaign.fee, campaign.key)
        }
    }

    /**
     * Every rung can field a hand of five in its own collection.
     *
     * The invariant [NpcBundleTest] holds the catalogue to, restated here because these are
     * *different* opponent records — the ladders declare their own pools, so a pool that has gone
     * stale against `cards.json` would not be caught over there.
     */
    @Test
    fun everyRungCanFieldAHand() {
        for (campaign in catalog.all) {
            val ids = cards.collection(campaign.collection.prefix).mapTo(mutableSetOf()) { it.id }
            for ((step, entry) in campaign.steps.withIndex()) {
                val hand = entry.npc.randomHand(Random(step))
                assertEquals(HAND_SIZE, hand.size, "${campaign.key}/${entry.npc.iconId}")
                assertTrue(
                    ids.containsAll(hand),
                    "${campaign.key}/${entry.npc.iconId} draws cards outside its collection",
                )
            }
        }
    }

    /**
     * The entry fee is the only fee — with one exception, which is kept.
     *
     * Twelve of the thirteen rungs declare `matchFee: 0`, the 500 having been paid up front.
     * `CCGroupMatchScreen.as:70` gives Spade a `matchFee` of 15, and **nothing charges it**: the
     * ladder's own screens never read `matchFee`, only `PVEScreen` does. So it is dead data in the
     * original, carried through rather than tidied away, and pinned here so that porting it into
     * something that *does* charge is a deliberate act.
     */
    @Test
    fun onlyOneRungDeclaresAFeeAndNothingCollectsIt() {
        val charging = catalog.all.flatMap { campaign ->
            campaign.steps.filter { it.npc.matchFee > 0 }.map { "${campaign.key}/${it.npc.iconId}" }
        }
        assertEquals(listOf("cc/spade"), charging)
    }

    /**
     * Three rungs speak, all of them in the Gold Saucer, and no rung has anything to say on a draw.
     *
     * The Card Club is **entirely silent**: all seven of its `messages` are empty strings, which is
     * why the `TalkAnim` import at the top of `CCGroupMatchScreen` is dead.
     */
    @Test
    fun onlyTheGoldSaucerSpeaks() {
        val speaking = catalog.all.flatMap { campaign ->
            campaign.steps.filterNot { it.messages.isSilent }.map { campaign.key }
        }
        assertEquals(List(SPEAKING_RUNGS) { "gs" }, speaking)
        assertTrue(
            catalog.all.flatMap { it.steps }.all { it.messages.draw == null },
            "no shipped rung has a line for a draw",
        )
    }

    /**
     * A ladder is walked by winning, restarted by losing, and repeated by drawing.
     *
     * The three `NEXT_STEP` values of `endGame`, read off the shipped data so that the end of the
     * ladder — the point where the panel's Next Match disappears — is asserted rather than assumed.
     */
    @Test
    fun winningWalksTheLadderAndLosingSendsYouBack() {
        val campaign = assertNotNull(catalog.byKey("gs"))
        val last = campaign.steps.size - 1

        assertEquals(1, campaign.nextStep(0, MatchResult.WIN))
        assertEquals(0, campaign.nextStep(last, MatchResult.LOSE))
        assertEquals(last, campaign.nextStep(last, MatchResult.DRAW))
        assertNull(
            campaign.stepAt(campaign.nextStep(last, MatchResult.WIN)),
            "winning the last rung should end the ladder",
        )
    }

    private companion object {
        const val CARD_CLUB_RUNGS = 7
        const val GOLD_SAUCER_RUNGS = 6
        const val ENTRY_FEE = 500
        const val SPEAKING_RUNGS = 3
    }
}
