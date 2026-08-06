package com.tripletriad.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import tripletriad.shared.generated.resources.Res
import tripletriad.shared.generated.resources.raleway_medium
import tripletriad.shared.generated.resources.raleway_regular

/**
 * Raleway, in the two weights the AS3 theme embeds.
 *
 * `BaseTTOTheme.as:115-116` embeds Regular as `normal` and Medium as `bold` — so the game's "bold"
 * is a medium weight, not a bold one, and reproducing that is why [FontWeight.Bold] maps to Medium
 * here. Compose synthesises the weights in between from these two.
 *
 * **Not Eurostile**, which `docs/migration/08-PHASE-4-UI-LAYER.md` Task 4.1 names. Eurostile
 * appears once in the whole AS3 source, at `display/Card.as:81`, and it draws the `±N` modifier on
 * a card — a field this port does not render. See `tools/import_fonts.py`, which also disposes of
 * the plan's licence caveat: it was raised about Eurostile, and Raleway is under the SIL Open Font
 * License.
 *
 * Raleway has **no CJK coverage**, so `ja_JA` renders through the platform's own per-glyph font
 * fallback. The AS3 needed the two `Noto-ja` bitmap fonts in `sources/bin/assets/fonts` for this;
 * Skia and Android substitute on their own, and the `ja_JA` screens were rendered and read to check
 * it — Latin text takes Raleway, kana and kanji take the system face, in the same line. No test
 * asserts it, because none of them can look at a glyph.
 */
@Composable
internal fun rememberGameFontFamily(): FontFamily = FontFamily(
    Font(Res.font.raleway_regular, FontWeight.Normal),
    Font(Res.font.raleway_medium, FontWeight.Medium),
    Font(Res.font.raleway_medium, FontWeight.Bold),
)

/**
 * The type scale, in Raleway.
 *
 * ### The AS3's own ladder, and why the numbers here are not it
 *
 * `BaseTTOTheme.as:669-672` declares four sizes — 18, 24, 28 and 36 — and multiplies each by
 * `scale`, which is the device's DPI over `ORIGINAL_DPI_IPHONE_RETINA` (326). They are therefore
 * **pixels at 326 DPI**, not density-independent units: converted at 160 dp-per-inch they come out
 * as roughly 9, 12, 14 and 18 dp.
 *
 * Nine dp is too small to read on a phone that is not a 2013 Retina display, and flooring the
 * bottom of the ladder while leaving the top would flatten the ratios that make it a ladder. So
 * what is preserved is the **shape** — four steps, each about a fifth larger than the last, with
 * the two smallest carrying metadata and the largest carrying a screen title — re-anchored for
 * density. This is the same judgement `Task 4.2` records for the card geometry: treat the AS3's
 * numbers as design ratios and scale them to the viewport rather than transcribing pixels.
 *
 * The sizes below are the ones the fourteen screens were already using, now named once instead of
 * written as a literal at each of the ninety-odd call sites. Re-anchoring the ladder is a design
 * decision and this is the one place it would be made.
 *
 * ### Colour is not set here
 *
 * Deliberately, and Task 4.1's own note says why: a `TextStyle` carrying a colour silently
 * overrides the `ColorScheme` everywhere it is used, and then two things claim to decide what
 * colour text is. The scheme decides; these carry family, size and weight only.
 *
 * ### Every slot carries the family, including the ones nothing names
 *
 * `Text`'s default style is `MaterialTheme.typography.bodyLarge`, and a `Text` that sets `fontSize`
 * without setting `style` still inherits its *family* from there. So leaving the slots this app
 * does not name at their Material defaults would leave most of the screen in the platform font
 * while the theme claimed to have set one. The unnamed slots keep Material's own sizes and take the
 * family.
 */
@Composable
internal fun appTypography(): Typography {
    val game = rememberGameFontFamily()
    val base = Typography()

    fun style(size: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = game, fontSize = size.sp, fontWeight = weight)

    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = game),
        displayMedium = base.displayMedium.copy(fontFamily = game),
        displaySmall = base.displaySmall.copy(fontFamily = game),
        headlineLarge = base.headlineLarge.copy(fontFamily = game),
        headlineMedium = base.headlineMedium.copy(fontFamily = game),
        // `extraLargeFontSize` — `Header`'s own format, which is what a screen title is.
        headlineSmall = style(TITLE, FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = game),
        // `largeFontSize` — `largeUILightElementFormat`, the primary control label.
        titleMedium = style(BUTTON),
        titleSmall = style(ROW_TITLE, FontWeight.Bold),
        // `regularFontSize` — the body of every list, and the default every `Text` inherits.
        bodyLarge = style(BODY),
        bodyMedium = style(BODY),
        bodySmall = style(SECONDARY),
        labelLarge = base.labelLarge.copy(fontFamily = game),
        // `smallFontSize` — `detailElementFormat`, the line under a row's name.
        labelMedium = style(META),
        labelSmall = style(FINE),
    )
}

/** Screen titles. */
private const val TITLE = 18

/** The label on a [com.tripletriad.ui.WideButton]. */
private const val BUTTON = 16

/** A list row's name. */
private const val ROW_TITLE = 15

/** Body text, and a card detail's name. */
private const val BODY = 14

/** A row's secondary line. */
private const val SECONDARY = 13

/** The `·`-joined metadata line. */
private const val META = 12

/** The smallest thing on screen: a rules strip, a stack count, a progress figure. */
private const val FINE = 11
