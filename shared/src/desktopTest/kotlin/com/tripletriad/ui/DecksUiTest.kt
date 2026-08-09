package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.GameSave
import com.tripletriad.model.HAND_SIZE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The deck slots and the editor.
 *
 * A fresh character is the fixture: five owned cards, one complete starter deck in slot 0 and four
 * empty slots — `Save.as:30-31`, where the same five ids seed both the collection and the deck.
 */
@OptIn(ExperimentalTestApi::class)
class DecksUiTest {
    private fun ComposeUiTest.openDecks() {
        newCharacter()
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)
    }

    /** Five slots are always drawn, empty ones included — `DecksScreen.as:128-157`. */
    @Test
    fun allFiveSlotsAreListedIncludingTheEmptyOnes() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openDecks()

        for (slot in 0 until GameSave.MAX_DECKS) {
            onNodeWithTag(deckSlotTestTag(slot)).assertExists()
        }
        assertTrue(isVisible(GameSave.DEFAULT_DECK_NAME), "slot 0 holds the starter deck")
        // An unnamed slot is labelled by its 1-based number rather than left blank.
        assertTrue(isVisible("Deck 5"), "an empty slot should still be named")
    }

    @Test
    fun openingASlotShowsItsCardsAndBackReturnsToTheSlots() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openDecks()

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }

        for (position in 0 until HAND_SIZE) {
            onNodeWithTag(deckPositionTestTag(position)).assertExists()
        }

        // Back inside the editor returns to the slot list rather than leaving the screen.
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }
    }

    /** Tap a card in the deck to take it out; Save is what writes it. */
    @Test
    fun removingACardAndSavingWritesTheShorterDeck() = runComposeUiTest {
        val documents = seeded(GameSave.new(createdAt = 0L))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
        onNodeWithTag(deckPositionTestTag(0)).performClick()
        onNodeWithTag(DECK_SAVE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            storedSave(documents).decks.first().cards.size == HAND_SIZE - 1
        }

        val deck = storedSave(documents).decks.first()
        assertEquals(GameSave.DEFAULT_CARDS.drop(1), deck.cards, "the first position was removed")
        assertEquals(GameSave.DEFAULT_DECK_NAME, deck.name, "and the name is kept")
    }

    /**
     * Backing out of the editor abandons the edit.
     *
     * The editor holds its own copy and nothing reaches the profile until Save — which is also the
     * half of `resetDeckHandler` the original got wrong: it saved from Reset, having built a
     * ten-entry card list and then called `slice` where `splice` was meant, so the deck came back
     * on the next load. See [GameSave.clearingDeck].
     */
    @Test
    fun leavingTheEditorWithoutSavingChangesNothing() = runComposeUiTest {
        val documents = seeded(GameSave.new(createdAt = 0L))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)
        val before = storedSave(documents)

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
        onNodeWithTag(DECK_RESET_TEST_TAG).performClick()
        waitForIdle()
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }

        assertEquals(before.decks, storedSave(documents).decks, "Reset alone must not persist")
    }

    /** A second slot can be built from the owned cards and saved beside the first. */
    @Test
    fun anEmptySlotCanBeFilledFromTheCollection() = runComposeUiTest {
        val documents = seeded(GameSave.new(createdAt = 0L))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        onNodeWithTag(deckSlotTestTag(1)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
        for (cardId in GameSave.DEFAULT_CARDS) {
            onNodeWithTag(deckPickTestTag(cardId)).performClick()
        }
        onNodeWithTag(DECK_NAME_TEST_TAG).performTextClearance()
        onNodeWithTag(DECK_NAME_TEST_TAG).performTextInput(SECOND_DECK)
        onNodeWithTag(DECK_SAVE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).decks.size == 2 }

        val second = storedSave(documents).decks[1]
        assertEquals(SECOND_DECK, second.name)
        assertEquals(GameSave.DEFAULT_CARDS, second.cards)
        assertTrue(second.isComplete, "five cards is a playable deck")
    }

    /** The grid stops accepting cards at five — [com.tripletriad.model.Deck.plusCard]. */
    @Test
    fun aDeckCannotGrowPastFive() = runComposeUiTest {
        val extra = GameSave.new(createdAt = 0L)
            .copy(cards = GameSave.DEFAULT_CARDS + SIXTH_CARD)
        val documents = seeded(extra)
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)

        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }
        // Slot 0 is already the complete starter deck, so this tap has nothing to add to.
        onNodeWithTag(deckPickTestTag(SIXTH_CARD)).performClick()
        onNodeWithTag(DECK_SAVE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_LIST_TEST_TAG) }

        val deck = storedSave(documents).decks.first()
        assertEquals(HAND_SIZE, deck.cards.size)
        assertFalse(SIXTH_CARD in deck.cards, "a full deck should not have taken a sixth card")
    }

    private companion object {
        const val SECOND_DECK = "Second"

        /** An ff14 card outside the starter five. */
        const val SIXTH_CARD = 44
    }
}
