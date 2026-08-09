package com.tripletriad.i18n

import com.tripletriad.model.BoosterType
import com.tripletriad.model.CardItem
import com.tripletriad.model.NpcLevel
import com.tripletriad.model.PotionType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Keys that are **derived** rather than declared, and therefore invisible to [StringKeys.all].
 *
 * `StringsBundleTest` walks the list of constants a screen names directly. It cannot see a key that
 * a model composes from its own enum — `"APP_NPC_LEVEL_$name"`, `"APP_${as3Name}_DESC"` — and that
 * blind spot was not theoretical: **none** of these resolved in any of the four bundles, so the
 * opponent list drew `STR_NPC_LEVEL_AVERAGE` on 25 of its 60 rows and every row of the shop drew a
 * raw `_DESC` key as its description. The original never defined them either, which is why nothing
 * flagged it during the import.
 *
 * English is the only locale asserted, for the same reason `StringsBundleTest` allows a gap: the
 * app-owned strings are written in English and French and fall through for German and Japanese.
 */
class DerivedKeysTest {
    private val strings = runBlocking { loadStrings(AppLocale.EN_US) }

    @Test
    fun everyNpcLevelHasALabel() {
        for (level in NpcLevel.entries) {
            assertTrue(strings.has(level.labelKey), "no label for ${level.labelKey}")
        }
    }

    /**
     * Every booster and potion the shop can sell, plus the card item.
     *
     * `BoosterType.PLATINUM` is included although `ShopCatalog` sells none: it is reachable as a
     * match reward and therefore reachable in the bag, which draws the same description.
     */
    @Test
    fun everySellableItemDescribesItself() {
        for (booster in BoosterType.entries) {
            assertTrue(strings.has(booster.descriptionKey), "no text for ${booster.descriptionKey}")
        }
        for (potion in PotionType.entries) {
            assertTrue(strings.has(potion.descriptionKey), "no text for ${potion.descriptionKey}")
        }
        assertTrue(strings.has(CardItem(1).descriptionKey), "no text for a card item")
    }

    /** And their names, which the bundles do carry — asserted so a rename cannot lose one. */
    @Test
    fun everySellableItemNamesItself() {
        for (booster in BoosterType.entries) {
            assertTrue(strings.has(booster.nameKey), "no name for ${booster.nameKey}")
        }
        for (potion in PotionType.entries) {
            assertTrue(strings.has(potion.nameKey), "no name for ${potion.nameKey}")
        }
    }
}
