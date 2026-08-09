package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two screens that hold two things each: the cards screen and the store.
 *
 * What is asserted here is the **merge**, not either half — the collection, the decks, the shelf
 * and the bag each keep their own test, and those pass unchanged because nothing inside them moved.
 * What is new is that reaching one puts the other a tab away, that the tab is where the dashboard
 * entry says it should be, and that back still leaves the deck editor before it leaves the screen.
 */
@OptIn(ExperimentalTestApi::class)
class TabsUiTest {
    @Test
    fun theCardsEntryOpensTheCollectionAndItsOtherTabIsTheDecks() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromBar("cards", CARD_GRID_TEST_TAG)

        onNodeWithTag(screenTabTestTag("decks")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }
        assertFalse(exists(CARD_GRID_TEST_TAG), "both tabs were showing at once")

        onNodeWithTag(screenTabTestTag("cards")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_GRID_TEST_TAG) }
    }

    /** The decks entry opens the same screen, already on the tab it names. */
    @Test
    fun theDecksEntryOpensTheSameScreenOnTheOtherTab() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        assertTrue(exists(COLLECTION_TABS_TEST_TAG), "it should be the tabbed cards screen")
        onNodeWithTag(screenTabTestTag("cards")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_GRID_TEST_TAG) }
    }

    /**
     * Back leaves the editor first, then the screen.
     *
     * The check that decides this moved out of the decks body and into the screen that now owns the
     * app bar, which is exactly the kind of move that silently drops a rule — so it is asserted
     * from the outside: two backs, two different destinations.
     */
    @Test
    fun backLeavesTheDeckEditorBeforeItLeavesTheScreen() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }

        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }

        backToDashboard()
    }

    /**
     * The shelf and the bag, and the Buy button that belongs to only one of them.
     *
     * Buy is in the screen's bottom bar rather than in the shelf — see [StoreScreen] — so the tab
     * switch has to take it away with the shelf. A committing button left over a list it cannot act
     * on is worse than one that scrolls off.
     */
    @Test
    fun theShopEntryOpensTheShelfAndTheBagIsOneTabAway() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromBar("store", SHOP_LIST_TEST_TAG)
        assertTrue(exists(SHOP_BUY_TEST_TAG), "the shelf should carry its Buy button")

        onNodeWithTag(screenTabTestTag("bag")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(INVENTORY_EMPTY_TEST_TAG) }

        assertFalse(exists(SHOP_LIST_TEST_TAG), "both tabs were showing at once")
        assertFalse(exists(SHOP_BUY_TEST_TAG), "Buy stayed over the bag")
    }

    /** And the bag entry opens the same screen on the bag. */
    @Test
    fun theBagEntryOpensTheSameScreenOnTheOtherTab() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromDashboard(DASHBOARD_INVENTORY_TEST_TAG, INVENTORY_EMPTY_TEST_TAG)

        assertTrue(exists(STORE_TABS_TEST_TAG), "it should be the tabbed store screen")
        onNodeWithTag(screenTabTestTag("shop")).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(SHOP_LIST_TEST_TAG) }
    }
}
