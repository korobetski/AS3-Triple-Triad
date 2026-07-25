package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * End-to-end cover for PoC requirement 2, "load card data from JSON file".
 *
 * These go through the real Compose resource bundle rather than a string literal, so
 * they fail if `cards.json` is missing from the packaged resources, if the generated
 * `Res` accessor moves, or if the schema drifts from the model. The parser itself is
 * covered separately in `commonTest/CardCatalogTest`.
 */
@OptIn(ExperimentalTestApi::class)
class CatalogUiTest {

    @Test
    fun theWholeCatalogIsReadOutOfTheResourceBundle() = runComposeUiTest {
        setContent { App() }
        awaitCatalog()

        // 153 + 110, the entry counts of FF14_DATAS and FF8_DATAS in cards.as minus the
        // "Back" sentinel each table starts with.
        assertVisible("catalog: 263 cards", "wrong number of cards loaded")
        assertVisible("ff14 153 / ff8 110", "wrong per-collection counts")
    }

    @Test
    fun theFirstCardIsFf8CardOneWithItsRealPowers() = runComposeUiTest {
        setContent { App() }
        awaitCatalog()

        // cards.as FF8_DATAS[1]: {name:"STR_FF8_CARD_1", power:[1,4,1,5], rarity:1}
        assertVisible("#1 ff8_Geezard", "first card is not FF8 #1")
        assertVisible("1/4/1/5", "Geezard's powers are wrong")
    }

    @Test
    fun steppingForwardShowsTheNextCardFromTheData() = runComposeUiTest {
        setContent { App() }
        awaitCatalog()

        onNodeWithTag(NEXT_CARD_TEST_TAG).performClick()
        // cards.as FF8_DATAS[2]: {name:"STR_FF8_CARD_2", power:[5,1,1,3], rarity:1}
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible("#2 ff8_Funguar") }
        assertVisible("5/1/1/3", "Funguar's powers are wrong")
    }

    @Test
    fun steppingForwardResetsTheOwnerToBlue() = runComposeUiTest {
        setContent { App() }
        awaitCatalog()

        onNodeWithTag(CARD_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible("owner: red") }

        onNodeWithTag(NEXT_CARD_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible("owner: blue") }
    }

    @Test
    fun catalogSummaryIsTagged() = runComposeUiTest {
        setContent { App() }
        awaitCatalog()

        onNodeWithTag(CATALOG_SUMMARY_TEST_TAG).assertExists()
    }
}
