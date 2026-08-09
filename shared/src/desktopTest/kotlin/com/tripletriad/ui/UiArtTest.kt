package com.tripletriad.ui

import com.tripletriad.data.loadCardCatalog
import com.tripletriad.data.loadNpcCatalog
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.BoosterType
import com.tripletriad.model.GameSave
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That the interface artwork is **in the bundle**, and complete.
 *
 * The counterpart of [com.tripletriad.data.CardBundleTest] for everything that is not a card face.
 * `tools/import_ui_art.py` exits non-zero on a missing source file, but nothing stops a card, an
 * opponent or an achievement being added to the data afterwards without the importer being run
 * again — and nothing but a load proves the images were *packaged* rather than merely copied into
 * the source tree.
 *
 * There is no composition here on purpose: every one of these is a question about the resource
 * bundle, and a test that had to lay out a screen to ask it would be slower and would fail for
 * reasons that have nothing to do with the answer.
 */
class UiArtTest {
    private val art = runBlocking { loadUiArt() }

    /** All 263, in three sheets. A card with no frame is drawn as an empty plate. */
    @Test
    fun everyCardInBothCollectionsHasAThumbnail() {
        val cards = runBlocking { loadCardCatalog() }.all
        val missing = cards.filter { art.thumb(it) == null }

        assertTrue(
            missing.isEmpty(),
            "${missing.size} cards have no thumbnail: " +
                missing.take(MISSING_TO_REPORT).joinToString { "${it.textureId} (${it.name})" },
        )
    }

    /**
     * Every badge the record can draw, resolved the way [AchievementIcon] resolves it.
     *
     * Which is the point of asserting it here rather than trusting the icon list: the two spellings
     * — a `misc/` icon for most rows, `ff14_thumb_37` for `ac-fob` — are reconciled in one place,
     * and this is what says that place still covers the whole catalogue.
     */
    @Test
    fun everyAchievementBadgeResolves() {
        val missing = AchievementCatalog.all
            .map { it.iconId }
            .distinct()
            .filter { art.icon(it) == null && art.thumb(thumbTextureId(it)) == null }

        assertTrue(missing.isEmpty(), "no artwork for: ${missing.joinToString()}")
    }

    /** The bag and the shop draw these; a booster with no icon is a row of bare text. */
    @Test
    fun everyBoosterPackHasItsIcon() {
        for (booster in BoosterType.entries) {
            assertNotNull(art.icon(booster.iconId), "no icon for ${booster.name}")
        }
    }

    /** `AVATAR_ID` has named this since Phase 2, and until now nothing read it. */
    @Test
    fun theDefaultAvatarIsInTheBundle() {
        val avatar = runBlocking { art.avatar(GameSave.DEFAULT_AVATAR) }
        assertNotNull(avatar, "the avatar every new character starts with")
    }

    /**
     * The opponents' portraits, and the eleven that are **known to be absent**.
     *
     * Asserted as an exact set rather than as "mostly present", in both directions. A twelfth
     * missing portrait is a bundle that was built short and should fail here; an eleventh that
     * turns up is artwork that was found, and the monogram fallback in [NpcPortrait] should stop
     * being described as covering it.
     */
    @Test
    fun everyOpponentHasAPortraitExceptTheElevenThatShipNone() {
        val ids = runBlocking { loadNpcCatalog() }.all.map { it.iconId }.distinct().sorted()
        val missing = ids.filter { runBlocking { art.portrait(it) } == null }

        assertEquals(WITHOUT_PORTRAITS, missing, "the opponents with no 50px portrait")
    }

    private companion object {
        const val MISSING_TO_REPORT = 8

        /**
         * The FF8 ladder rungs and the card-suit opponents. None of them has a 50px portrait
         * anywhere in the AS3 asset tree — they were drawn for the Card Club, which the original
         * listed by name only.
         */
        val WITHOUT_PORTRAITS = listOf(
            "club", "diamond", "dobe", "flo", "heart", "jack", "jocker",
            "ma-dincht", "piet", "spade", "trepies",
        )
    }
}
