package com.tripletriad.data

import com.tripletriad.model.CardCollection
import com.tripletriad.model.MatchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ladder model against a hand-written bundle — the shipped one is [CampaignBundleTest]'s job.
 *
 * Two files for the same reason [NpcCatalogTest] and `NpcBundleTest` are two: this one states what
 * the *code* does with a ladder, and needs a fixture it controls; that one states what the shipped
 * data contains, and must not have one.
 */
class CampaignCatalogTest {
    private val catalog = CampaignCatalogParser.parse(BUNDLE)

    @Test
    fun aLadderParsesWithItsRungsInOrder() {
        val campaign = assertNotNull(catalog.byKey("test"))

        assertEquals(CardCollection.FF14, campaign.collection)
        assertEquals(500, campaign.fee)
        assertEquals(listOf("first", "second"), campaign.opponents.map { it.iconId })
    }

    /** `messages` is absent on the second rung, and an absent one is silence, not a fault. */
    @Test
    fun aRungWithNoDialogueIsSilent() {
        val campaign = assertNotNull(catalog.byKey("test"))

        assertFalse(campaign.steps[0].messages.isSilent)
        assertEquals("Good luck.", campaign.steps[0].messages.start)
        assertTrue(campaign.steps[1].messages.isSilent)
    }

    /** What is said depends on the result, and a draw has no line in any shipped rung. */
    @Test
    fun theLineFollowsTheResult() {
        val messages = assertNotNull(catalog.byKey("test")).steps[0].messages

        assertEquals("Well played.", messages.forResult(MatchResult.WIN))
        assertEquals("Better luck next time.", messages.forResult(MatchResult.LOSE))
        assertNull(messages.forResult(MatchResult.DRAW))
    }

    /** Win to advance, lose to start over, draw to replay the same rung — `endGame`'s three. */
    @Test
    fun theLadderIsWalkedByWinning() {
        val campaign = assertNotNull(catalog.byKey("test"))

        assertEquals(1, campaign.nextStep(0, MatchResult.WIN))
        assertEquals(Campaign.FIRST_STEP, campaign.nextStep(1, MatchResult.LOSE))
        assertEquals(1, campaign.nextStep(1, MatchResult.DRAW))
    }

    /** Past the last rung there is no opponent, which is how the ladder knows it is over. */
    @Test
    fun pastTheLastRungThereIsNothing() {
        val campaign = assertNotNull(catalog.byKey("test"))

        assertNotNull(campaign.stepAt(1))
        assertNull(campaign.stepAt(2))
        assertNull(campaign.stepAt(campaign.nextStep(1, MatchResult.WIN)))
    }

    @Test
    fun aLadderWithNoOpponentsIsRefused() {
        val empty = BUNDLE.replaceAfter("\"steps\": [", "]}]}")

        assertFailsWith<IllegalArgumentException> { CampaignCatalogParser.parse(empty) }
    }

    /** Only the ladders of the character's own collection, as `PVEScreen` gates them. */
    @Test
    fun laddersAreSelectedByCollection() {
        assertEquals(listOf("test"), catalog.forCollection(CardCollection.FF14).map { it.key })
        assertEquals(emptyList(), catalog.forCollection(CardCollection.FF8).map { it.key })
        assertNull(catalog.byKey("absent"))
    }

    private companion object {
        val BUNDLE = """
            {"campaigns": [{
              "key": "test",
              "nameKey": "STR_TEST_LADDER",
              "collection": "ff14_",
              "fee": 500,
              "steps": [
                {
                  "npc": {
                    "id": 1, "name": "STR_FIRST", "iconID": "first",
                    "rules": ["RULE_ALL_OPEN"], "fetishesCards": [1, 2, 3, 4, 5], "cards": [],
                    "level": "STR_NPC_LEVEL_NOVICE", "matchFee": 0,
                    "MGPReward": {"w": 10, "d": 4, "l": 1}, "itemRewards": [], "difficulty": 1
                  },
                  "messages": {
                    "start": "Good luck.",
                    "win": "Well played.",
                    "lose": "Better luck next time."
                  }
                },
                {
                  "npc": {
                    "id": 2, "name": "STR_SECOND", "iconID": "second",
                    "rules": [], "fetishesCards": [1, 2, 3, 4, 5], "cards": [],
                    "level": "STR_NPC_LEVEL_EXPERT", "matchFee": 0,
                    "MGPReward": {"w": 20, "d": 8, "l": 2}, "itemRewards": [], "difficulty": 5
                  }
                }
              ]
            }]}
        """.trimIndent()
    }
}
