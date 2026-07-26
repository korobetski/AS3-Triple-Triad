package com.tripletriad.data

import com.tripletriad.model.Card
import com.tripletriad.model.CardType
import com.tripletriad.ui.loadCardArt
import com.tripletriad.ui.textureId
import com.tripletriad.ui.textureName
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    /**
     * The check that a card can never ship without its picture.
     *
     * `import_card_art.py` refuses to finish if a record has no source file, but nothing stops
     * someone adding a card to `cards.json` afterwards and not re-running it. This reads all 263
     * through the resource loader, which is also the only way to know the images were *packaged*
     * and not merely copied into the source tree.
     */
    @Test
    fun everyCardInTheCatalogHasArtworkInTheBundle() = runBlocking {
        val art = loadCardArt()
        val missing = catalog.all.filter { card ->
            runCatching { art.face(card) }.isFailure
        }
        assertTrue(
            missing.isEmpty(),
            "${missing.size} cards have no artwork: " +
                missing.take(MISSING_TO_REPORT).joinToString { "${it.textureId} (${it.name})" },
        )
    }

    /** The nineteen shared textures: the back, the digit atlas, five rarity rows, twelve types. */
    @Test
    fun everySharedTextureLoads() = runBlocking {
        val art = loadCardArt()

        for (rarity in Card.RARITY_RANGE) {
            assertNotNull(art.starsFor(rarity), "no $rarity-star row")
        }
        for (type in CardType.entries) {
            assertNotNull(art.typeIcon(type), "no icon for ${type.textureName}")
        }
        assertNotNull(art.digitPlate, "no cdbg plate")
        for (power in Card.POWER_RANGE) {
            assertNotNull(art.digit(power), "no glyph for power $power")
        }
    }

    private companion object {
        const val FF14_CARDS = 153
        const val FF8_CARDS = 110
        const val MISSING_TO_REPORT = 10

        /** Hex digits `1`..`A` in `cards.json`, never `0`: that range is the tile power. */
        val PRINTED_POWERS = 1..10
    }
}
