package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import com.tripletriad.i18n.AppLocale
import com.tripletriad.log.Log
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import tripletriad.shared.generated.resources.Res

/**
 * The rule captions, in the language being played in.
 *
 * ### Why these are loaded on demand and not at boot
 *
 * [CardArt] loads its nineteen shared textures up front because every card needs some of
 * them from the first frame. These are the opposite case: twenty 600x90 pictures of which
 * a given match shows perhaps four, and none before the first placement resolves. Loading
 * the set would add ~300 KB of decode to a launch that has nothing to draw it on.
 *
 * They are cached once decoded, because the *second* Same of a match must not blink.
 *
 * ### Why the locale is a constructor argument
 *
 * Because a banner is a picture of a word, so the cache is only valid for one language —
 * see `tools/import_rule_banners.py`. Changing the language has to produce a different
 * [BannerArt] rather than reuse this one, which is what keying [rememberBannerArt] on the
 * locale does. Keeping one cache across a language change would show the old language's
 * captions until the app restarted, which is exactly the kind of bug nobody reports
 * because nobody changes language mid-match.
 */
class BannerArt internal constructor(private val locale: AppLocale) {

    // Unsynchronised for [CardArt]'s reason: a race costs one redundant decode.
    private val decoded = mutableMapOf<MatchBanner, ImageBitmap>()

    /** The already-decoded caption, or null if it has not been asked for yet. */
    fun cached(banner: MatchBanner): ImageBitmap? = decoded[banner]

    /**
     * Decodes [banner], or returns the cached copy.
     *
     * Falls back to [AppLocale.Default] rather than throwing, and then gives up rather
     * than throwing again. A missing caption should cost the player a caption, not the
     * match they were in the middle of.
     */
    suspend fun caption(banner: MatchBanner): ImageBitmap? =
        decoded[banner]
            ?: (read(locale, banner) ?: read(AppLocale.Default, banner))
                ?.also { decoded[banner] = it }

    @OptIn(ExperimentalResourceApi::class)
    @Suppress("TooGenericExceptionCaught")
    private suspend fun read(locale: AppLocale, banner: MatchBanner): ImageBitmap? = try {
        Res.readBytes("$BANNER_PATH/${locale.tag}/${banner.textureId}.png")
            .decodeToImageBitmap()
    } catch (failure: Exception) {
        Log.w(TAG, failure) { "no ${banner.name} caption for ${locale.tag}" }
        null
    }

    private companion object {
        const val TAG = "Banners"

        /**
         * Where [`import_rule_banners.py`](../../../../../../../tools/import_rule_banners.py)
         * writes.
         */
        const val BANNER_PATH = "files/banners"
    }
}

/**
 * Makes [BannerArt] ambient, as [LocalCardArt] does for the card textures.
 *
 * Null is a working state: [MatchBannerOverlay] then draws nothing at all, so every
 * existing UI test composes a match without needing the artwork or the resource bundle.
 */
val LocalBannerArt = staticCompositionLocalOf<BannerArt?> { null }

/** One [BannerArt] per language, rebuilt when the language changes and not before. */
@Composable
fun rememberBannerArt(locale: AppLocale): BannerArt = remember(locale) { BannerArt(locale) }

/**
 * [banner]'s picture, decoding it if this is the first time it has been seen.
 *
 * The same shape as [rememberCardFace], and for the same reason its KDoc gives: seeded
 * from the cache so a repeat does not blink, and keyed on the banner so a slot reused for
 * a different caption cannot keep showing the previous one.
 */
@Composable
internal fun rememberCaption(art: BannerArt?, banner: MatchBanner?): ImageBitmap? {
    if (art == null || banner == null) return null
    var image by remember(art, banner) { mutableStateOf(art.cached(banner)) }
    LaunchedEffect(art, banner) {
        if (image == null) image = art.caption(banner)
    }
    return image
}
