package com.tripletriad.data

import com.tripletriad.model.BoosterItem
import com.tripletriad.model.BoosterType
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameSave
import com.tripletriad.model.MiscItem
import com.tripletriad.model.PotionItem
import com.tripletriad.model.PotionType
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [Inventory]: stacking, selling, using, and the ordering the bag is kept in.
 *
 * The behaviour worth being careful about is stacking, because the AS3 does not do it — every site
 * `push`es — and the duplicate-row state that produces is what this layer exists to make
 * unreachable.
 */
class InventoryTest {
    private val empty = GameSave.new(createdAt = 0)

    @Test
    fun addingAnItemPutsItInTheBag() {
        val save = Inventory.add(empty, CardItem(13))

        assertEquals(listOf(CardItem(13, 1)), save.bag)
    }

    /** The AS3 would have produced two rows of "1" here. */
    @Test
    fun addingTheSameStackableItemTwiceMergesTheStack() {
        val save = Inventory.add(Inventory.add(empty, CardItem(13)), CardItem(13, 3))

        assertEquals(listOf(CardItem(13, 4)), save.bag)
        assertEquals(4, Inventory.count(save, CardItem(13)))
    }

    @Test
    fun differentItemsDoNotMerge() {
        val save = listOf(CardItem(13), CardItem(14), PotionItem(PotionType.XP))
            .fold(empty) { current, item -> Inventory.add(current, item) }

        assertEquals(3, save.bag.size)
        assertEquals(1, Inventory.count(save, CardItem(13)))
        assertEquals(0, Inventory.count(save, CardItem(15)))
    }

    @Test
    fun addingAnEmptyStackDoesNothing() {
        assertEquals(empty, Inventory.add(empty, CardItem(13, 0)))
    }

    @Test
    fun addAllMergesEachItemIndependently() {
        val save = Inventory.addAll(
            empty,
            listOf(CardItem(13), CardItem(13), BoosterItem(BoosterType.BRONZE)),
        )

        assertEquals(2, save.bag.size)
        assertEquals(2, Inventory.count(save, CardItem(13)))
    }

    @Test
    fun removingReducesTheStackAndThenDropsTheRow() {
        var save = Inventory.add(empty, CardItem(13, 3))

        save = Inventory.remove(save, CardItem(13), count = 2)
        assertEquals(listOf(CardItem(13, 1)), save.bag)

        save = Inventory.remove(save, CardItem(13))
        assertTrue(save.bag.isEmpty())
    }

    /**
     * Partially fulfilling a removal is how an inventory ends up disagreeing with its transaction.
     */
    @Test
    fun removingMoreThanIsHeldChangesNothing() {
        val save = Inventory.add(empty, CardItem(13, 2))

        assertEquals(save, Inventory.remove(save, CardItem(13), count = 3))
        assertEquals(save, Inventory.remove(save, CardItem(99)))
    }

    @Test
    fun sellingACardPaysItsValueAndRemovesIt() {
        val save = Inventory.add(empty.copy(mgp = 0), CardItem(50, 2))

        val sold = Inventory.sell(save, CardItem(50))

        assertEquals(200, sold.mgp, "CardItem.as:25 — value is id * 4")
        assertEquals(1, Inventory.count(sold, CardItem(50)))
    }

    @Test
    fun sellingSeveralAtOncePaysForEach() {
        val save = Inventory.add(empty.copy(mgp = 0), CardItem(10, 3))

        assertEquals(120, Inventory.sell(save, CardItem(10), count = 3).mgp)
    }

    @Test
    fun whatIsNotSellableCannotBeSold() {
        val booster = BoosterItem(BoosterType.BRONZE)
        val save = Inventory.add(empty.copy(mgp = 0), booster)

        val attempted = Inventory.sell(save, booster)

        assertEquals(save, attempted)
        assertEquals(0, attempted.mgp)
    }

    @Test
    fun sellingWhatIsNotHeldChangesNothing() {
        assertEquals(empty, Inventory.sell(empty, CardItem(1)))
    }

    /**
     * A pack yields a **card item in the bag**, not a card in the collection.
     *
     * `InventoryScreen.as:252-258` pushes `new CardItem(cardId)` onto `BAG` and leaves `CARDS`
     * alone, so the drawn card is something the player then chooses to use or to sell — the only
     * sink for a duplicate the game has. See [ItemUse.PackOpened].
     */
    @Test
    fun usingABoosterPutsACardItemInTheBagAndConsumesThePack() {
        val booster = BoosterItem(BoosterType.BRONZE)
        val save = Inventory.add(empty, booster)

        val used = Inventory.use(save, booster, Random(1))

        val opened = assertIs<ItemUse.PackOpened>(used)
        assertTrue(opened.cardId in BoosterType.BRONZE.pool)
        assertEquals(0, Inventory.count(opened.save, booster), "the pack is consumed")
        assertEquals(
            1,
            Inventory.count(opened.save, CardItem(opened.cardId)),
            "the drawn card should be in the bag: ${opened.save.bag}",
        )
        assertEquals(
            empty.cards,
            opened.save.cards,
            "opening a pack must not add to the collection on its own",
        )
    }

    /** Opening then using is the two-step path, and it ends with the card owned. */
    @Test
    fun openingThenUsingTheDrawnCardAddsItToTheCollection() {
        val booster = BoosterItem(BoosterType.BEAST)
        val opened = assertIs<ItemUse.PackOpened>(
            Inventory.use(Inventory.add(empty, booster), booster, Random(7)),
        )

        val used = assertIs<ItemUse.CardDrawn>(
            Inventory.use(opened.save, CardItem(opened.cardId)),
        )

        assertTrue(used.save.ownsCard(opened.cardId))
        assertTrue(used.save.bag.isEmpty(), "both the pack and the card item are gone")
    }

    /** A pack opened into a bag that already holds that card stacks rather than adding a row. */
    @Test
    fun aSecondCopyOfADrawnCardStacks() {
        val booster = BoosterItem(BoosterType.BRONZE, stack = 2)
        val save = Inventory.add(empty, booster)

        val first = assertIs<ItemUse.PackOpened>(Inventory.use(save, booster, Random(2)))
        val again = assertIs<ItemUse.PackOpened>(
            Inventory.use(first.save, BoosterItem(BoosterType.BRONZE), Random(2)),
        )

        assertEquals(first.cardId, again.cardId, "the same seed draws the same card")
        assertEquals(2, Inventory.count(again.save, CardItem(again.cardId)))
        assertEquals(
            1,
            again.save.bag.size,
            "one row, stack of two — not two rows: ${again.save.bag}",
        )
    }

    @Test
    fun usingAPotionRaisesItsBoonAndConsumesIt() {
        val potion = PotionItem(PotionType.BIG_XP)
        val save = Inventory.add(empty, potion)

        val used = Inventory.use(save, potion)

        val raised = assertIs<ItemUse.BoonRaised>(used)
        assertEquals(10, raised.save.boons.xp)
        assertEquals(0, raised.save.boons.mgp)
        assertTrue(raised.save.bag.isEmpty())
    }

    @Test
    fun usingACardItemAddsThatCard() {
        val item = CardItem(75)
        val save = Inventory.add(empty, item)

        val drawn = assertIs<ItemUse.CardDrawn>(Inventory.use(save, item))

        assertEquals(75, drawn.cardId)
        assertTrue(drawn.wasNew)
        assertTrue(drawn.save.ownsCard(75))
    }

    /** A duplicate is still consumed; the flag is how the UI knows to say "already owned". */
    @Test
    fun aDuplicateCardIsStillConsumedAndReportedAsNotNew() {
        val item = CardItem(1)
        val save = Inventory.add(empty, item)

        val drawn = assertIs<ItemUse.CardDrawn>(Inventory.use(save, item))

        assertTrue(1 in empty.cards, "card 1 is in the starter collection")
        assertEquals(false, drawn.wasNew)
        assertTrue(drawn.save.bag.isEmpty())
    }

    @Test
    fun whatIsNotUseableIsNotUsed() {
        val misc = MiscItem()
        val save = Inventory.add(empty, misc)

        val used = Inventory.use(save, misc)

        assertIs<ItemUse.NotUseable>(used)
        assertEquals(save, used.save)
    }

    @Test
    fun usingSomethingNotInTheBagChangesNothing() {
        val used = Inventory.use(empty, PotionItem(PotionType.XP))

        assertIs<ItemUse.NotUseable>(used)
        assertEquals(empty, used.save)
    }

    /** `InventoryScreen.sortBag()`'s job: cards, then packs, then potions, then the rest. */
    @Test
    fun theBagIsKeptInDisplayOrder() {
        val save = Inventory.addAll(
            empty,
            listOf(
                MiscItem(),
                PotionItem(PotionType.MGP),
                CardItem(50),
                BoosterItem(BoosterType.GOLD),
                CardItem(3),
            ),
        )

        assertEquals(
            listOf(
                CardItem(3),
                CardItem(50),
                BoosterItem(BoosterType.GOLD),
                PotionItem(PotionType.MGP),
                MiscItem(),
            ),
            save.bag,
        )
    }

    @Test
    fun sortingIsStableAcrossRepeatedAdds() {
        val once = Inventory.addAll(empty, listOf(CardItem(9), CardItem(2)))
        val again = Inventory.add(once, CardItem(5))

        assertEquals(listOf(2, 5, 9), again.bag.map { (it as CardItem).cardId })
    }
}
