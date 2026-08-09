package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.CardCollection
import com.tripletriad.model.GameSave
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The collection browser: the whole card table, owned and not.
 *
 * The point of the screen is the second half of that. `cardListScreen.as:101-106` walks the whole
 * table and dims what the profile does not have, and a browser that showed only what you own would
 * not tell you what there is to get.
 */
@OptIn(ExperimentalTestApi::class)
class CollectionUiTest {
    private fun ComposeUiTest.openCards(collection: CardCollection = CardCollection.FF14) {
        newCharacter(collection)
        openFromBar("cards", CARD_GRID_TEST_TAG)
    }

    /** Counted over the table, so an id outside it cannot push the total past the table's size. */
    @Test
    fun theTotalCountsWhatIsOwnedAgainstTheWholeTable() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR${GameSave.DEFAULT_CARDS.size} / $FF14_CARDS",
        )
    }

    /** The detail panel says what to do rather than sitting blank, which is what the AS3 did. */
    @Test
    fun theDetailPanelIsEmptyUntilACardIsPicked() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        onNodeWithTag(CARD_DETAIL_EMPTY_TEST_TAG).assertExists()
        assertFalse(exists(CARD_DETAIL_TEST_TAG))

        onNodeWithTag(cardCellTestTag(GameSave.DEFAULT_CARDS.first())).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }

        assertTrue(isVisible("Sides"), "the detail should state the four sides")
        assertTrue(isVisible("Rarity"), "and the rarity")
    }

    /** Tapping the selected card again closes the detail. */
    @Test
    fun tappingTheSameCardTwiceClosesTheDetail() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openCards()
        val card = GameSave.DEFAULT_CARDS.first()

        onNodeWithTag(cardCellTestTag(card)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }
        onNodeWithTag(cardCellTestTag(card)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_EMPTY_TEST_TAG) }
    }

    /**
     * An unowned card is drawn and is tappable.
     *
     * `CardThumb.enabled = false` made unowned thumbs untouchable in the original, so the
     * description of the card you were hunting for was the one thing you could not read.
     */
    @Test
    fun anUnownedCardIsStillListedAndStillReadable() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openCards()

        assertFalse(UNOWNED_CARD in GameSave.DEFAULT_CARDS, "the fixture assumes this is unowned")
        onNodeWithTag(CARD_GRID_TEST_TAG)
            .performScrollToNode(hasTestTag(cardCellTestTag(UNOWNED_CARD)))
        onNodeWithTag(cardCellTestTag(UNOWNED_CARD)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CARD_DETAIL_TEST_TAG) }
    }

    /**
     * An ff8 profile browses the ff8 table.
     *
     * Card ids index whichever table `MODE` names, so showing the other collection's card for an id
     * would be showing a different card entirely.
     */
    @Test
    fun anFf8CharacterBrowsesTheFf8Table() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openCards(CardCollection.FF8)

        onNodeWithTag(CARD_TOTAL_TEST_TAG).assertTextEquals(
            "Owned$DOT_SEPARATOR${GameSave.DEFAULT_CARDS.size} / $FF8_CARDS",
        )
        // Past the end of the ff8 table, so an ff8 profile must not be shown it.
        val found = runCatching {
            onNodeWithTag(CARD_GRID_TEST_TAG)
                .performScrollToNode(hasTestTag(cardCellTestTag(FF14_ONLY_CARD)))
        }
        assertTrue(found.isFailure, "card $FF14_ONLY_CARD is outside the ff8 table")
    }

    private companion object {
        /** `CardBundleTest`'s counts, which is where the two tables' sizes are pinned. */
        const val FF14_CARDS = 153
        const val FF8_CARDS = 110

        /** An ff14 card outside [GameSave.DEFAULT_CARDS]. */
        const val UNOWNED_CARD = 44

        /** An id the ff14 table has and the 110-card ff8 table does not. */
        const val FF14_ONLY_CARD = 138
    }
}
