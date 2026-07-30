package com.tripletriad.model

import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The [Item] hierarchy: its wire format, its per-subtype constants, and the booster draw.
 *
 * The wire format is the part that has to be exactly right, because `Save.DATAS.BAG` holds these
 * objects and a bag written by the original must still load.
 */
class ItemTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun aCardItemSerialisesAsTheAs3ToJsonDoes() {
        // `CardItem.__toJSON()` (:33-36) -> {type: 'item-type-card', card: 13, stack: 2}
        val encoded = json.encodeToString<Item>(CardItem(13, stack = 2))

        assertEquals("""{"type":"item-type-card","card":13,"stack":2}""", encoded)
    }

    @Test
    fun aBoosterItemSerialisesAsTheAs3ToJsonDoes() {
        val encoded = json.encodeToString<Item>(BoosterItem(BoosterType.BEAST, stack = 3))

        assertEquals(
            """{"type":"item-type-booster","booster":"BEAST_BOOSTER","stack":3}""",
            encoded,
        )
    }

    @Test
    fun aPotionItemSerialisesAsTheAs3ToJsonDoes() {
        val encoded = json.encodeToString<Item>(PotionItem(PotionType.MGP))

        assertEquals("""{"type":"item-type-potion","potion":"MGP_BOOST","stack":1}""", encoded)
    }

    /** `Item.itemize` (`Item.as:161-178`) is what this replaces: dispatch on the `type` string. */
    @Test
    fun aBagWrittenByTheAs3BuildStillParses() {
        val bag = """
            [
              {"type":"item-type-card","card":13,"stack":2},
              {"type":"item-type-booster","booster":"BRONZE_BOOSTER","stack":1},
              {"type":"item-type-potion","potion":"SMALL_XP_BOOST","stack":5},
              {"type":"item-type-misc","stack":1}
            ]
        """.trimIndent()

        val items = json.decodeFromString<List<Item>>(bag)

        assertEquals(4, items.size)
        assertEquals(CardItem(13, 2), items[0])
        assertEquals(BoosterItem(BoosterType.BRONZE, 1), items[1])
        assertEquals(PotionItem(PotionType.SMALL_XP, 5), items[2])
        assertEquals(MiscItem(1), items[3])
    }

    @Test
    fun everyItemRoundTrips() {
        val items: List<Item> = listOf(
            CardItem(1),
            BoosterItem(BoosterType.GARLEAN, 7),
            PotionItem(PotionType.BIG_MGP, 2),
            MiscItem(4),
        )

        assertEquals(items, json.decodeFromString<List<Item>>(json.encodeToString(items)))
    }

    /** `CardItem.as:25` — value is the card id times four, so later cards sell for more. */
    @Test
    fun cardItemValueIsIdTimesFour() {
        assertEquals(4, CardItem(1).value)
        assertEquals(400, CardItem(100).value)
    }

    /**
     * The flags are assigned in each AS3 constructor and never changed, which is why they are
     * derived here. This pins them against the source so the derivation cannot drift.
     */
    @Test
    fun theFlagsMatchEachAs3Constructor() {
        val card = CardItem(1)
        assertTrue(card.sellable && card.stackable && card.useable && card.dropable)

        val booster = BoosterItem(BoosterType.BRONZE)
        assertFalse(booster.sellable, "BoosterItem.as:54")
        assertTrue(booster.stackable && booster.useable)
        assertFalse(booster.dropable, "BoosterItem.as:57")
        assertEquals(0, booster.value, "BoosterItem.as:52")

        val potion = PotionItem(PotionType.XP)
        assertFalse(potion.sellable, "PotionItem.as:40")
        assertTrue(potion.stackable && potion.useable)
        assertFalse(potion.dropable, "PotionItem.as:43")

        val misc = MiscItem()
        assertFalse(misc.useable, "Item.as:45 — the base class is the one that is not useable")
        assertEquals(1, misc.value, "Item.as:42 — the base default is 1, not 0")
    }

    @Test
    fun iconsAndKeysFollowTheAs3Names() {
        assertEquals("booster_pack_icon", BoosterItem(BoosterType.BRONZE).iconId)
        assertEquals("beast_booster", BoosterItem(BoosterType.BEAST).iconId)
        assertEquals("STR_BEAST_BOOSTER", BoosterType.BEAST.nameKey)
        assertEquals("STR_BEAST_BOOSTER_DESC", BoosterType.BEAST.descriptionKey)
        assertEquals("potionItem", PotionItem(PotionType.XP).iconId)
        assertEquals("STR_XP_BOOST", PotionType.XP.nameKey)
        assertEquals("STR_SMALL_MGP_BOOST_DESC", PotionType.SMALL_MGP.descriptionKey)
        assertEquals("STR_CARD_ITEM_DESC", CardItem(1).descriptionKey)
        // The icon needs the card's rarity, which only the catalog knows.
        val card = Card(1, "ff14_", "STR_FF14_CARD_1", "Dodo", 4, 4, 4, 4, rarity = 3)
        assertEquals("card_r3_icon", CardItem(1).iconFor(card))
    }

    /** `PotionItem.as:18-23`. The three tiers are 2, 5 and 10 on each of two boons. */
    @Test
    fun potionModifiersMatchTheAs3Constants() {
        assertEquals(BoonModifier(BoonType.XP, 2), PotionType.SMALL_XP.modifier)
        assertEquals(BoonModifier(BoonType.MGP, 2), PotionType.SMALL_MGP.modifier)
        assertEquals(BoonModifier(BoonType.XP, 5), PotionType.XP.modifier)
        assertEquals(BoonModifier(BoonType.MGP, 5), PotionType.MGP.modifier)
        assertEquals(BoonModifier(BoonType.XP, 10), PotionType.BIG_XP.modifier)
        assertEquals(BoonModifier(BoonType.MGP, 10), PotionType.BIG_MGP.modifier)
    }

    /** `BoosterItem.as:19-27`, transcribed. Spot-checked rather than restated in full. */
    @Test
    fun boosterPoolsMatchTheAs3Constants() {
        assertEquals(9, BoosterType.entries.size)
        assertEquals(listOf(4, 5, 8, 12, 27, 38), BoosterType.BRONZE.pool)
        assertEquals(listOf(31, 32, 47, 51, 64, 119), BoosterType.GARLEAN.pool)
        assertEquals(13, BoosterType.BEAST.pool.size)
        for (type in BoosterType.entries) {
            assertTrue(type.pool.isNotEmpty(), "$type has no pool")
            assertTrue(type.pool.all { it > 0 }, "$type has a non-positive card id")
        }
    }

    @Test
    fun openingABoosterAlwaysYieldsACardFromItsOwnPool() {
        for (type in BoosterType.entries) {
            val booster = BoosterItem(type)
            repeat(200) { seed ->
                val drawn = booster.open(Random(seed))
                assertTrue(drawn in type.pool, "$type drew $drawn, not in ${type.pool}")
            }
        }
    }

    /**
     * `BoosterItem.open()` multiplies two uniform draws, so the distribution leans hard on index 0
     * and the last entry is nearly unreachable. That is the rarity curve, so it is pinned rather
     * than merely tolerated — a "fix" to uniform would silently change every drop rate in the game.
     */
    @Test
    fun theBoosterDrawIsBiasedTowardsTheStartOfThePool() {
        val booster = BoosterItem(BoosterType.BEAST)
        val pool = BoosterType.BEAST.pool
        val draws = (0 until 4_000).map { booster.open(Random(it)) }

        val firstThird = draws.count { pool.indexOf(it) < pool.size / 3 }
        assertTrue(
            firstThird > draws.size / 2,
            "expected most draws in the first third of the pool, got $firstThird of ${draws.size}",
        )
        assertTrue(
            draws.count { it == pool.last() } * 20 < draws.size,
            "the last entry should be rare",
        )
    }

    @Test
    fun openingIsReproducibleForAGivenSeed() {
        val booster = BoosterItem(BoosterType.PRIMAL)

        assertEquals(booster.open(Random(42)), booster.open(Random(42)))
    }

    @Test
    fun withStackReplacesTheCountAndNothingElse() {
        assertEquals(CardItem(13, 9), CardItem(13, 1).withStack(9))
        assertEquals(
            BoosterItem(BoosterType.GOLD, 2),
            BoosterItem(BoosterType.GOLD, 1).withStack(2),
        )
    }

    @Test
    fun aNegativeStackIsAProgrammingError() {
        assertFailsWith<IllegalArgumentException> { CardItem(1, stack = -1) }
        assertFailsWith<IllegalArgumentException> { CardItem(0) }
    }

    @Test
    fun itemRewardsResolveToItems() {
        assertEquals(CardItem(13), ItemReward("card", 0.25, cardId = 13).item())
        assertEquals(
            PotionItem(PotionType.MGP),
            ItemReward("potion", 0.02, potion = PotionType.MGP).item(),
        )
        assertEquals(
            BoosterItem(BoosterType.BRONZE),
            ItemReward("booster", 0.1, booster = BoosterType.BRONZE).item(),
        )
        assertNotNull(ItemReward("card", 1.0, cardId = 1).item())
        // A type this build does not understand yields nothing rather than throwing.
        assertEquals(null, ItemReward("accessory", 1.0).item())
        assertEquals(null, ItemReward("card", 1.0).item())
    }
}
