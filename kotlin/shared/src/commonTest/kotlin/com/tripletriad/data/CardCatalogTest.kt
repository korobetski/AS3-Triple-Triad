package com.tripletriad.data

import com.tripletriad.model.CardColor
import com.tripletriad.model.CardType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parser-level tests. These use inline JSON rather than the shipped resource so they
 * run on every target without a resource loader; the real `cards.json` is exercised
 * end-to-end by `desktopTest/CardBundleTest`.
 */
class CardCatalogTest {

    private val json = """
        {
          "ff14": [
            {
              "id": 62, "collection": "ff14_", "nameKey": "STR_FF14_CARD_62",
              "name": "Ultima Weapon", "top": 1, "right": 8, "bottom": 10, "left": 8,
              "rarity": 5, "type": null
            },
            {
              "id": 64, "collection": "ff14_", "nameKey": "STR_FF14_CARD_64",
              "name": "Gaius van Baelsar", "top": 4, "right": 10, "bottom": 5, "left": 9,
              "rarity": 5, "type": "garlean"
            }
          ],
          "ff8": [
            {
              "id": 1, "collection": "ff8_", "nameKey": "STR_FF8_CARD_1",
              "name": "Geezard", "top": 1, "right": 4, "bottom": 1, "left": 5,
              "rarity": 1, "type": null
            },
            {
              "id": 6, "collection": "ff8_", "nameKey": "STR_FF8_CARD_6",
              "name": "Thrustaevis", "top": 2, "right": 1, "bottom": 4, "left": 4,
              "rarity": 1, "type": "lightning"
            }
          ]
        }
    """.trimIndent()

    private val catalog = CardCatalogParser.parse(json)

    @Test
    fun bothCollectionsAreParsed() {
        assertEquals(2, catalog.ff14.size)
        assertEquals(2, catalog.ff8.size)
        assertEquals(4, catalog.all.size)
    }

    @Test
    fun powersKeepTheAs3TopRightBottomLeftOrder() {
        val geezard = catalog.ff8.first { it.id == 1 }
        // cards.as: power:[1,4,1,5]
        assertEquals(1, geezard.top)
        assertEquals(4, geezard.right)
        assertEquals(1, geezard.bottom)
        assertEquals(5, geezard.left)
    }

    @Test
    fun hexPowerAIsTen() {
        // cards.as line 82: power:[1,8,'A',8] -- read via uint("0x" + ...) in Card.as.
        val ultima = catalog.ff14.first { it.id == 62 }
        assertEquals(10, ultima.bottom)
    }

    @Test
    fun typeCoversBothTheFf14TribesAndTheFf8Elements() {
        assertEquals(CardType.GARLEAN, catalog.ff14.first { it.id == 64 }.type)
        assertEquals(CardType.LIGHTNING, catalog.ff8.first { it.id == 6 }.type)
        assertNull(catalog.ff8.first { it.id == 1 }.type)
    }

    @Test
    fun ownerDefaultsToBlueBecauseTheDataDoesNotStoreIt() {
        assertTrue(catalog.all.all { it.owner == CardColor.BLUE })
    }

    @Test
    fun collectionsAreLookedUpByTheAs3TexturePrefix() {
        assertEquals(catalog.ff14, catalog.collection("ff14_"))
        assertEquals(catalog.ff8, catalog.collection("ff8_"))
        assertFailsWith<IllegalArgumentException> { catalog.collection("ff7_") }
    }

    @Test
    fun unknownFieldsDoNotBreakParsing() {
        val withExtra = json.replace(
            "\"name\": \"Geezard\"",
            "\"name\": \"Geezard\", \"unknownFutureField\": 42",
        )
        assertEquals(4, CardCatalogParser.parse(withExtra).all.size)
    }

    @Test
    fun invalidDataIsRejectedAtConstruction() {
        val broken = json.replace("\"right\": 4", "\"right\": 99")
        assertFailsWith<IllegalArgumentException> { CardCatalogParser.parse(broken) }
    }
}
