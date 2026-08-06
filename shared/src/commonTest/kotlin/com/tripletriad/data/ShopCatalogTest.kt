package com.tripletriad.data

import com.tripletriad.model.BoosterItem
import com.tripletriad.model.BoosterType
import com.tripletriad.model.CardCollection
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameSave
import com.tripletriad.model.PotionItem
import com.tripletriad.model.PotionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ShopCatalog] against `screens/shopScreen.as:42-70`.
 *
 * The prices are transcribed and follow no formula, so the tests that matter here are the ones that
 * pin the *shape* — that the two tables differ, that ff8 sells no packs, that a purchase is atomic
 * — plus spot checks on numbers a re-derivation would get wrong.
 */
class ShopCatalogTest {
    private fun profile(mgp: Int, mode: CardCollection = CardCollection.FF14) =
        GameSave.new(createdAt = 0L, mode = mode).copy(mgp = mgp)

    @Test
    fun theTwoTablesAreTheSizesTheAs3Declares() {
        assertEquals(20, ShopCatalog.ff14.size, "FF14_SHOP has 20 entries")
        assertEquals(5, ShopCatalog.ff8.size, "FF8_SHOP has 5")
    }

    @Test
    fun offersAreSelectedByTheProfilesCollection() {
        assertEquals(ShopCatalog.ff14, ShopCatalog.offers(CardCollection.FF14))
        assertEquals(ShopCatalog.ff8, ShopCatalog.offers(CardCollection.FF8))
    }

    /**
     * The ff8 shelf holds no packs, and that is data rather than an omission: every
     * `*_BOOSTER_CARDS` pool names ff14 ids. See [ShopCatalog].
     */
    @Test
    fun theFf8ShopSellsNoBoosterPacks() {
        assertTrue(ShopCatalog.ff8.none { it.item is BoosterItem })
        assertTrue(
            ShopCatalog.ff14.any { it.item is BoosterItem },
            "the ff14 shelf is where the packs are",
        )
    }

    /** Both shelves open with the same two 50-MGP potions. */
    @Test
    fun bothShopsSellTheTwoFiftyMgpPotions() {
        for (offers in listOf(ShopCatalog.ff14, ShopCatalog.ff8)) {
            assertEquals(ShopOffer(PotionItem(PotionType.MGP), 50), offers[0])
            assertEquals(ShopOffer(PotionItem(PotionType.XP), 50), offers[1])
        }
    }

    /** Prices a formula would not produce: two pairs collide, and card 118 undercuts card 63. */
    @Test
    fun thePricesAreTheHandSetOnes() {
        fun priceOf(id: Int) = ShopCatalog.ff14.first { it.item == CardItem(id) }.price
        fun priceOf(pack: BoosterType) = ShopCatalog.ff14.first {
            it.item == BoosterItem(pack)
        }.price

        assertEquals(priceOf(BoosterType.SILVER), priceOf(BoosterType.SCION), "both 1152")
        assertEquals(priceOf(BoosterType.GOLD), priceOf(BoosterType.GARLEAN), "both 2160")
        assertEquals(1_000_000, priceOf(74))
        assertTrue(priceOf(118) < priceOf(63), "the later id is the cheaper card")
    }

    /** Declared, in the pack table, and sold nowhere. See [ShopCatalog]. */
    @Test
    fun thePlatinumPackIsOnNeitherShelf() {
        val platinum = BoosterItem(BoosterType.PLATINUM)

        assertTrue((ShopCatalog.ff14 + ShopCatalog.ff8).none { it.item == platinum })
    }

    @Test
    fun aFreeOfferIsAProgrammingError() {
        assertFailsWith<IllegalArgumentException> { ShopOffer(CardItem(1), price = 0) }
    }

    // ---- Buying ----------------------------------------------------------

    @Test
    fun buyingTakesTheMgpAndGivesTheItem() {
        val offer = ShopOffer(PotionItem(PotionType.MGP), price = 50)

        val bought = ShopCatalog.buy(profile(mgp = 120), offer)

        assertEquals(70, bought.mgp)
        assertEquals(1, Inventory.count(bought, offer.item))
    }

    /**
     * Neither half happens when the profile cannot pay — the AS3 subtracted first and checked
     * afterwards (`:144-146`). See [ShopCatalog.buy].
     */
    @Test
    fun anUnaffordableOfferChangesNothingAtAll() {
        val save = profile(mgp = 49)
        val offer = ShopOffer(PotionItem(PotionType.MGP), price = 50)

        assertFalse(offer.isAffordableBy(save))
        assertEquals(save, ShopCatalog.buy(save, offer))
    }

    @Test
    fun exactlyEnoughIsEnough() {
        val offer = ShopOffer(CardItem(2), price = 120)

        val bought = ShopCatalog.buy(profile(mgp = 120), offer)

        assertEquals(0, bought.mgp)
        assertEquals(1, Inventory.count(bought, CardItem(2)))
    }

    /** Two of the same offer stack rather than adding a second row — the AS3 `push`ed. */
    @Test
    fun buyingTwiceStacksInTheBag() {
        val offer = ShopOffer(PotionItem(PotionType.XP), price = 50)

        val twice = ShopCatalog.buy(ShopCatalog.buy(profile(mgp = 200), offer), offer)

        assertEquals(100, twice.mgp)
        assertEquals(1, twice.bag.size, "one row: ${twice.bag}")
        assertEquals(2, Inventory.count(twice, offer.item))
    }

    /** A bought card is a bag item, not a collection entry — using it is a separate step. */
    @Test
    fun aBoughtCardDoesNotEnterTheCollectionByItself() {
        val offer = ShopOffer(CardItem(44), price = 1_000)

        val bought = ShopCatalog.buy(profile(mgp = 1_000), offer)

        assertFalse(bought.ownsCard(44))
        assertEquals(1, Inventory.count(bought, CardItem(44)))
    }
}
