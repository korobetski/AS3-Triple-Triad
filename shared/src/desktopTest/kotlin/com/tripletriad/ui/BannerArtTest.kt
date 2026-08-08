package com.tripletriad.ui

import com.tripletriad.i18n.AppLocale
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * That the captions are actually in the bundle, under the names the code asks for.
 *
 * This is the test the importer needs and cannot provide for itself. `import_rule_banners.py`
 * checks that it *copied* eighty files; it cannot check that [MatchBanner] spells the ids
 * the same way, that the Compose resource path matches where they landed, or that a
 * language the app offers has a set at all. Each of those failures is silent — a caption
 * that simply never appears mid-match — and none would fail a compile.
 */
class BannerArtTest {

    /**
     * Every caption, in every language the app offers.
     *
     * Eighty decodes, which is the point: a per-locale gap is exactly the bug that ships,
     * because whoever adds a language will test in their own.
     */
    @Test
    fun everyCaptionDecodesInEveryLanguage() = runTest {
        AppLocale.entries.forEach { locale ->
            val art = BannerArt(locale)
            MatchBanner.entries.forEach { banner ->
                val image = art.caption(banner)
                assertNotNull(image, "${banner.name} is missing for ${locale.tag}")
                assertTrue(
                    image.width > 0 && image.height > 0,
                    "${banner.name} for ${locale.tag} decoded to nothing",
                )
            }
        }
    }

    /**
     * The captions are all one size, which is what lets the overlay centre them blindly.
     *
     * 600x90 in the AS3 asset tree. If a re-export ever changed one, a caption would sit
     * at a different scale from its neighbours and the slide distance — one caption width
     * — would differ per rule.
     */
    @Test
    fun everyCaptionIsTheSameSize() = runTest {
        val art = BannerArt(AppLocale.EN_US)
        val sizes = MatchBanner.entries.mapNotNull { art.caption(it) }
            .map { it.width to it.height }
            .toSet()

        assertEquals(setOf(BANNER_WIDTH to BANNER_HEIGHT), sizes)
    }

    /** Decoded once and kept: the second Same of a match must not blink. */
    @Test
    fun aCaptionIsDecodedOnceAndCached() = runTest {
        val art = BannerArt(AppLocale.EN_US)

        val first = assertNotNull(art.caption(MatchBanner.SAME))

        assertSame(first, art.cached(MatchBanner.SAME))
        assertSame(first, art.caption(MatchBanner.SAME))
    }

    /** And nothing is decoded before it is asked for. */
    @Test
    fun nothingIsLoadedUntilItIsNeeded() {
        val art = BannerArt(AppLocale.EN_US)

        MatchBanner.entries.forEach {
            assertEquals(null, art.cached(it), "${it.name} was loaded eagerly")
        }
    }

    /**
     * Two languages are two caches, because a caption is a picture of a word.
     *
     * Sharing one would show the previous language's captions until the app restarted,
     * which nobody would report because nobody changes language mid-match.
     *
     * French rather than Japanese, and the reason is worth writing down: eleven of the
     * twenty Japanese captions are byte-identical to the English ones. START, PLUS, SAME,
     * COMBO, the turn and outcome captions — the original's Japanese UI keeps those words
     * in Latin script, so the "translation" is the same file. Asserting against Japanese
     * therefore proves nothing about the cache and fails on assets that are correct. The
     * French set is the only one that differs on all twenty.
     */
    @Test
    fun eachLanguageDecodesItsOwnCaption() = runTest {
        val english = assertNotNull(BannerArt(AppLocale.EN_US).caption(MatchBanner.SAME))
        val french = assertNotNull(BannerArt(AppLocale.FR_FR).caption(MatchBanner.SAME))

        assertTrue(
            english.readPixels() != french.readPixels(),
            "the English and French captions are the same picture",
        )
    }

    private companion object {
        const val BANNER_WIDTH = 600
        const val BANNER_HEIGHT = 90
    }
}

/** The decoded bytes, for comparing two captions without caring how they are encoded. */
private fun androidx.compose.ui.graphics.ImageBitmap.readPixels(): List<Int> {
    val buffer = IntArray(width * height)
    readPixels(buffer)
    return buffer.toList()
}
