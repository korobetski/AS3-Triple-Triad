package com.tripletriad.data

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the **real** `cards.json` in the Compose resource bundle, as opposed to
 * [CardCatalogTest], which parses a hand-written fragment.
 *
 * These counts used to be asserted through the UI, off a debug line the app printed above the
 * board. That line is gone — a title bar is 20 dp the board wants more — so the coverage moved
 * here, where it belongs: nothing about "the bundle is packaged and parses" needs a composition.
 */
class CardBundleTest {
    private val catalog = runBlocking { loadCardCatalog() }

    @Test
    fun theBundledCatalogHoldsBothCollectionsInFull() {
        assertEquals(FF14_CARDS, catalog.ff14.size, "the FF14 collection")
        assertEquals(FF8_CARDS, catalog.ff8.size, "the FF8 collection")
        assertEquals(FF14_CARDS + FF8_CARDS, catalog.all.size, "both collections together")
    }

    @Test
    fun everyBundledCardIsPlayable() {
        assertTrue(catalog.all.all { it.name.isNotBlank() }, "every card needs a name to draw")
        val powers = catalog.all.flatMap { listOf(it.top, it.right, it.bottom, it.left) }
        assertTrue(
            powers.all { it in PRINTED_POWERS },
            "printed powers are $PRINTED_POWERS — a 0 means a parse failure, not a weak card",
        )
    }

    private companion object {
        const val FF14_CARDS = 153
        const val FF8_CARDS = 110

        /** Hex digits `1`..`A` in `cards.json`, never `0`: that range is the tile power. */
        val PRINTED_POWERS = 1..10
    }
}
