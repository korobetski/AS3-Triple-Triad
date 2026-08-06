package com.tripletriad.data

import com.tripletriad.model.BoosterItem
import com.tripletriad.model.BoosterType
import com.tripletriad.model.CardCollection
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameSave
import com.tripletriad.model.Item
import com.tripletriad.model.PotionItem
import com.tripletriad.model.PotionType

/**
 * One thing the shop sells.
 *
 * @property item what lands in the bag. The [Item] itself carries no price — nothing in the game
 *   sells at what [Item.value] says, which is the *resale* figure — so the price belongs to the
 *   offer and not to the item.
 */
data class ShopOffer(val item: Item, val price: Int) {
    init {
        require(price > 0) { "an offer must cost something, was $price" }
    }

    /** True when [save] can pay for it. */
    fun isAffordableBy(save: GameSave): Boolean = save.mgp >= price
}

/**
 * The two shop tables of `screens/shopScreen.as:42-70`, transcribed verbatim.
 *
 * ### What the transcription preserves
 *
 * - **The prices**, which follow no formula. Silver and Scion are both 1152; Gold and Garlean are
 *   both 2160; card 74 costs a million MGP and card 63 costs 400 000 while card 118 — a later id —
 *   costs 200 000. These are hand-set numbers and re-deriving them from rarity would change the
 *   game's economy.
 * - **The order**, which is the order the list showed: potions, then packs, then cards ascending in
 *   price.
 * - **The asymmetry between the two collections.** The `ff8_` shop has *five* entries against the
 *   `ff14_` shop's twenty, and sells **no booster packs at all** — which is right rather than an
 *   omission: every `*_BOOSTER_CARDS` pool in `BoosterItem.as` names ff14 ids, and an id opened on
 *   an ff8 profile resolves against the ff8 table (see [BoosterType]). A pack on the ff8 shelf
 *   would deal a different card than its own pool describes.
 *
 * ### Two things in the original that are not reproduced
 *
 * - **A purchase was never saved.** `buyButton_triggeredHandler` ends on a commented-out
 *   `//Save.save(Game.PROFILE_DATAS)` (`:149`), so MGP spent and items bought were both lost on
 *   quit. Here the shop screen persists through `ProfileSession`, like every other mutation.
 * - **The bag grew a new row per purchase.** It `push`ed unconditionally, so buying two of the same
 *   potion showed two rows of "1". [Inventory.add] stacks. See there.
 *
 * [BoosterType.PLATINUM] is declared, priced nowhere and sold nowhere — it is in the pack table and
 * absent from both shop tables. Transcribed as such: it can only enter a bag as an achievement or
 * opponent reward, and neither grants one either.
 */
object ShopCatalog {
    /** `FF14_SHOP` (`shopScreen.as:42-63`). */
    val ff14: List<ShopOffer> = listOf(
        ShopOffer(PotionItem(PotionType.MGP), price = 50),
        ShopOffer(PotionItem(PotionType.XP), price = 50),
        ShopOffer(BoosterItem(BoosterType.BRONZE), price = 520),
        ShopOffer(BoosterItem(BoosterType.SILVER), price = 1_152),
        ShopOffer(BoosterItem(BoosterType.GOLD), price = 2_160),
        ShopOffer(BoosterItem(BoosterType.MITHRIL), price = 8_000),
        ShopOffer(BoosterItem(BoosterType.BEAST), price = 360),
        ShopOffer(BoosterItem(BoosterType.SCION), price = 1_152),
        ShopOffer(BoosterItem(BoosterType.PRIMAL), price = 3_280),
        ShopOffer(BoosterItem(BoosterType.GARLEAN), price = 2_160),
        ShopOffer(CardItem(2), price = 120),
        ShopOffer(CardItem(13), price = 150),
        ShopOffer(CardItem(20), price = 200),
        ShopOffer(CardItem(44), price = 1_000),
        ShopOffer(CardItem(45), price = 1_200),
        ShopOffer(CardItem(114), price = 14_400),
        ShopOffer(CardItem(138), price = 20_000),
        ShopOffer(CardItem(63), price = 400_000),
        ShopOffer(CardItem(118), price = 200_000),
        ShopOffer(CardItem(74), price = 1_000_000),
    )

    /** `FF8_SHOP` (`shopScreen.as:64-70`). */
    val ff8: List<ShopOffer> = listOf(
        ShopOffer(PotionItem(PotionType.MGP), price = 50),
        ShopOffer(PotionItem(PotionType.XP), price = 50),
        ShopOffer(CardItem(32), price = 350),
        ShopOffer(CardItem(37), price = 420),
        ShopOffer(CardItem(45), price = 600),
    )

    /**
     * What is on sale to a [collection] profile.
     *
     * `shopScreen[MODE.toUpperCase() + 'SHOP']` (`:100`) — a string-built static lookup in the
     * original, which is why a typo in `MODE` produced an empty list rather than an error.
     */
    fun offers(collection: CardCollection): List<ShopOffer> = when (collection) {
        CardCollection.FF14 -> ff14
        CardCollection.FF8 -> ff8
    }

    /**
     * Buys [offer] for [save], or returns the profile unchanged when it cannot be paid for.
     *
     * One function rather than "deduct, then add" at the call site: those two steps have to either
     * both happen or neither, and the AS3 got that wrong in the direction that matters — it
     * subtracted the price and *then* checked (`:144-146`), so the check only ever decided whether
     * the button stayed enabled.
     */
    fun buy(save: GameSave, offer: ShopOffer): GameSave {
        if (!offer.isAffordableBy(save)) return save
        return Inventory.add(save.withMgp(-offer.price), offer.item)
    }
}
