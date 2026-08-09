package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.Inventory
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.BoosterItem
import com.tripletriad.model.BoosterType
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameSave
import com.tripletriad.model.Item
import com.tripletriad.model.PotionItem
import com.tripletriad.model.PotionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The bag: what is in it, and what Use, Sell and Discard do to the file.
 *
 * Every assertion is against the decoded `.sav` rather than the screen, because the AS3's bag is
 * exactly where screen and file came apart — `useBtnHandler` mutated `Game.PROFILE_DATAS` and saved
 * only as a side effect of the `sortBag()` call at its end. A test that read the list back off the
 * screen would have passed against that.
 */
@OptIn(ExperimentalTestApi::class)
class InventoryUiTest {
    /** A character whose bag holds one of each kind that behaves differently. */
    private fun withBag(): GameSave = Inventory.addAll(
        GameSave.new(createdAt = 0L),
        listOf(
            // Not one of the starter five, so Use is offered.
            CardItem(SELLABLE_CARD, stack = 2),
            // One of them, so Use is refused — `InventoryScreen.as:111`.
            CardItem(GameSave.DEFAULT_CARDS.first()),
            BoosterItem(BoosterType.BRONZE),
            PotionItem(PotionType.MGP),
        ),
    )

    private fun ComposeUiTest.openBag(documents: com.tripletriad.storage.InMemoryDocumentStore) {
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_INVENTORY_TEST_TAG, INVENTORY_LIST_TEST_TAG)
    }

    private fun ComposeUiTest.select(item: Item) {
        onNodeWithTag(inventoryRowTestTag(item)).performClick()
        waitForIdle()
    }

    @Test
    fun aFreshCharactersBagSaysItIsEmpty() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        openFromDashboard(DASHBOARD_INVENTORY_TEST_TAG, INVENTORY_EMPTY_TEST_TAG)

        assertFalse(exists(INVENTORY_LIST_TEST_TAG), "an empty bag should not draw a list")
    }

    /** Nothing is actionable until a row is picked — the three buttons are not even drawn. */
    @Test
    fun theActionsAppearOnlyOnceSomethingIsSelected() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        assertFalse(exists(INVENTORY_USE_TEST_TAG), "the footer is up with nothing selected")

        select(CardItem(SELLABLE_CARD))

        onNodeWithTag(INVENTORY_USE_TEST_TAG).assertIsEnabled()
    }

    /** Selling pays [CardItem.value] — `id × 4` — and takes one off the stack. */
    @Test
    fun sellingACardPaysForItAndLeavesTheRest() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)
        val before = storedSave(documents).mgp

        select(CardItem(SELLABLE_CARD))
        onNodeWithTag(INVENTORY_SELL_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).mgp > before }

        val save = storedSave(documents)
        assertEquals(before + SELLABLE_CARD * MGP_PER_ID, save.mgp, "the card's own resale value")
        assertEquals(1, Inventory.count(save, CardItem(SELLABLE_CARD)), "one of the two sold")
    }

    /** Only a card item is sellable, so Sell is dead on a pack — [BoosterItem.sellable]. */
    @Test
    fun aPackCannotBeSoldAndCannotBeDiscarded() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        select(BoosterItem(BoosterType.BRONZE))

        onNodeWithTag(INVENTORY_SELL_TEST_TAG).assertIsNotEnabled()
        // `dropable = false` on a pack, which the AS3 declares and this reproduces.
        onNodeWithTag(INVENTORY_DISCARD_TEST_TAG).assertIsNotEnabled()
        onNodeWithTag(INVENTORY_USE_TEST_TAG).assertIsEnabled()
    }

    /** Using a card item is how a card enters the collection. */
    @Test
    fun usingACardAddsItToTheCollection() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        assertFalse(storedSave(documents).ownsCard(SELLABLE_CARD))

        select(CardItem(SELLABLE_CARD))
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).ownsCard(SELLABLE_CARD) }

        assertEquals(
            1,
            Inventory.count(storedSave(documents), CardItem(SELLABLE_CARD)),
            "the used copy should be consumed",
        )
    }

    /**
     * The card is **shown**, not merely named.
     *
     * `UnlockCardAnim` is the one thing the original does on this screen that a line of text
     * cannot. The player has usually never seen the card — that is what makes it worth having —
     * and the note beside the button gives them its name and nothing else.
     *
     * The reveal clears itself, which is the half worth asserting: it is drawn over the whole
     * screen, so one that never left would cover the bag for the rest of the session.
     */
    @Test
    fun usingACardShowsIt() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        select(CardItem(SELLABLE_CARD))
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(UNLOCKED_CARD_TEST_TAG) }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(UNLOCKED_CARD_TEST_TAG) }
    }

    /**
     * Opening a pack reveals nothing, because nothing was unlocked.
     *
     * `useBtnHandler` plays the animation in the card branch alone (`:236-245`). A pack yields
     * another **bag** item, so revealing it here would show off a card the player does not own —
     * and the obvious implementation, "play it whenever Use produces a card id", does exactly
     * that: `PackOpened` carries one too.
     */
    @Test
    fun openingAPackRevealsNothing() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        select(BoosterItem(BoosterType.BRONZE))
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()
        // The pack leaving the bag is the signal the use went through. Its own size is not: a
        // pack out and a card in leaves it unchanged.
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            Inventory.count(storedSave(documents), BoosterItem(BoosterType.BRONZE)) == 0
        }

        assertFalse(exists(UNLOCKED_CARD_TEST_TAG), "a pack unlocked nothing to show")
    }

    /**
     * Use is refused for a card the profile already owns.
     *
     * The one case where the item's own [Item.useable] says yes and the screen says no — the AS3
     * enables the button from the flag and then disables it again two lines later
     * (`InventoryScreen.as:107-113`). Without it, [Inventory.use] would consume the card to grant
     * something the profile already has.
     */
    @Test
    fun useIsRefusedForACardAlreadyInTheCollection() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        select(CardItem(GameSave.DEFAULT_CARDS.first()))

        onNodeWithTag(INVENTORY_USE_TEST_TAG).assertIsNotEnabled()
        assertTrue(isVisible("already owned"), "the row should say why, not just grey the button")
    }

    /**
     * Opening a pack yields a **bag entry**, not a collection card.
     *
     * That is the original's behaviour and the whole point of a pack: what comes out can be used or
     * sold. See `ItemUse.PackOpened`. The drawn id is not pinned — `App` uses the default random —
     * so what is asserted is the shape: the pack is gone, a card item took its place, and the
     * screen says which.
     */
    @Test
    fun openingAPackPutsACardInTheBagAndSaysWhich() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)
        val cardsBefore = storedSave(documents).cards

        select(BoosterItem(BoosterType.BRONZE))
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            Inventory.count(storedSave(documents), BoosterItem(BoosterType.BRONZE)) == 0
        }

        val save = storedSave(documents)
        assertEquals(cardsBefore, save.cards, "a pack must not add to the collection directly")
        val drawn = save.bag.filterIsInstance<CardItem>()
            .filter { it.cardId in BoosterType.BRONZE.pool }
        assertTrue(drawn.isNotEmpty(), "the pack should have yielded one of its pool: ${save.bag}")
        onNodeWithTag(INVENTORY_NOTE_TEST_TAG).assertExists()
    }

    /** A potion raises its boon rather than doing anything to the collection. */
    @Test
    fun drinkingAPotionRaisesTheBoon() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        assertEquals(0, storedSave(documents).boons.mgp)

        select(PotionItem(PotionType.MGP))
        onNodeWithTag(INVENTORY_USE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).boons.mgp > 0 }

        val save = storedSave(documents)
        assertEquals(PotionType.MGP.modifier.value, save.boons.mgp, "the potion's own value")
        assertEquals(0, Inventory.count(save, PotionItem(PotionType.MGP)), "and it is consumed")
    }

    /**
     * Discard asks twice.
     *
     * `dropBtnHandler` opens on `// TODO : afficher une Alert` and then destroys the item on the
     * first tap. Both halves are asserted, because a control that discarded immediately would pass
     * a test that only checked the item was gone at the end.
     */
    @Test
    fun discardingTakesTwoTaps() = runComposeUiTest {
        val documents = seeded(withBag())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openBag(documents)

        select(CardItem(SELLABLE_CARD))
        onNodeWithTag(INVENTORY_DISCARD_TEST_TAG).performClick()
        waitForIdle()

        assertEquals(
            2,
            Inventory.count(storedSave(documents), CardItem(SELLABLE_CARD)),
            "the first tap must only arm the control",
        )

        onNodeWithTag(INVENTORY_DISCARD_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            Inventory.count(storedSave(documents), CardItem(SELLABLE_CARD)) == 1
        }
    }

    private companion object {
        /** An ff14 card outside [GameSave.DEFAULT_CARDS], so it is neither owned nor a starter. */
        const val SELLABLE_CARD = 44

        /** `CardItem.as:25` — `value = _cardId * 4`. */
        const val MGP_PER_ID = 4
    }
}
