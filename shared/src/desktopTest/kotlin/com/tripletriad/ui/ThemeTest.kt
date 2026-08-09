package com.tripletriad.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.tripletriad.i18n.AppLocale
import com.tripletriad.ui.theme.LocalTtoColors
import com.tripletriad.ui.theme.TripleTriadTheme
import com.tripletriad.ui.theme.TtoColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The theme, read the way a screen reads it.
 *
 * Asserted through a real composition rather than against the private scheme object, because what
 * matters is what `MaterialTheme.colorScheme` hands a composable — a scheme that were built
 * correctly and then not provided would pass any test that only inspected the value.
 */
@OptIn(ExperimentalTestApi::class)
class ThemeTest {
    private fun read(): Triple<ColorScheme, Typography, TtoColors> {
        var captured: Triple<ColorScheme, Typography, TtoColors>? = null
        runComposeUiTest {
            setContent {
                TripleTriadTheme {
                    captured = Triple(
                        MaterialTheme.colorScheme,
                        MaterialTheme.typography,
                        LocalTtoColors.current,
                    )
                }
            }
            waitForIdle()
        }
        return assertNotNull(captured, "the theme provided nothing")
    }

    /**
     * The scheme's values are `BaseTTOTheme`'s constants.
     *
     * A regression fence rather than a tautology: these are transcribed from a file that is not
     * compiled, so nothing but this notices if one is mistyped. Task 4.1's own snippet had two
     * (`0xFF1a1a1a`, `0xFF2a2a2a`) that appear in no AS3 file at all.
     */
    @Test
    fun theSchemeCarriesTheAs3Constants() {
        val (scheme, _, _) = read()

        assertEquals(PRIMARY_BACKGROUND, scheme.background, "PRIMARY_BACKGROUND_COLOR")
        assertEquals(LIST_BACKGROUND, scheme.surface, "LIST_BACKGROUND_COLOR")
        assertEquals(GROUPED_LIST_HEADER, scheme.surfaceVariant, "GROUPED_LIST_HEADER_BACKGROUND")
        assertEquals(LIGHT_TEXT, scheme.onSurface, "LIGHT_TEXT_COLOR")
        assertEquals(LIGHT_TEXT, scheme.onBackground, "LIGHT_TEXT_COLOR")
        assertEquals(SELECTED_TEXT, scheme.secondary, "SELECTED_TEXT_COLOR")
    }

    /** The two card quads and the two side text colours, from `Card.as` and the theme. */
    @Test
    fun theGameColoursAreTheCardsOwn() {
        val (_, _, game) = read()

        assertEquals(CARD_BLUE, game.cardBlue, "Card.BLUE_COLOR")
        assertEquals(CARD_RED, game.cardRed, "Card.RED_COLOR")
        assertEquals(CARD_GREY, game.cardGrey, "Card.GREY_COLOR")
        assertEquals(LARGE_BLUE_ELEMENT, game.cardBlueEdge, "largeBlueElementFormat")
        assertEquals(LARGE_RED_ELEMENT, game.cardRedEdge, "largeRedElementFormat")
    }

    /**
     * `primary` is the card blue and not Material's default purple.
     *
     * Which is the whole point of having a scheme: before it existed, every Material control in the
     * tree had to be hand-coloured at its call site to hide that default, and the next one added
     * would have been the one that forgot.
     */
    @Test
    fun theSchemeIsNotMaterialsDefault() {
        val (scheme, _, game) = read()

        assertEquals(game.cardBlue, scheme.primary, "primary should be the card blue")
        assertEquals(game.cardBlueEdge, scheme.tertiary, "tertiary is the affirmative accent")
    }

    /**
     * **Every** type slot carries the game font, not only the ones this app names.
     *
     * `Text`'s default style is `typography.bodyLarge`, and a `Text` that sets `fontSize` without
     * setting `style` still takes its *family* from there. So a typography that set the family on
     * seven slots and left the other eight at Material's defaults would leave most of the screen in
     * the platform font while the theme claimed to have set one. This is the assertion that says it
     * does not.
     */
    @Test
    fun everyTypeSlotCarriesTheGameFont() {
        val (_, typography, _) = read()
        val slots: List<Pair<String, TextStyle>> = listOf(
            "displayLarge" to typography.displayLarge,
            "displayMedium" to typography.displayMedium,
            "displaySmall" to typography.displaySmall,
            "headlineLarge" to typography.headlineLarge,
            "headlineMedium" to typography.headlineMedium,
            "headlineSmall" to typography.headlineSmall,
            "titleLarge" to typography.titleLarge,
            "titleMedium" to typography.titleMedium,
            "titleSmall" to typography.titleSmall,
            "bodyLarge" to typography.bodyLarge,
            "bodyMedium" to typography.bodyMedium,
            "bodySmall" to typography.bodySmall,
            "labelLarge" to typography.labelLarge,
            "labelMedium" to typography.labelMedium,
            "labelSmall" to typography.labelSmall,
        )

        val platform = slots.filter { (_, style) ->
            style.fontFamily == null || style.fontFamily == FontFamily.Default
        }
        assertTrue(platform.isEmpty(), "left in the platform font: ${platform.map { it.first }}")
    }

    /**
     * The type scale is the one the fourteen screens were written against.
     *
     * Pinned because re-anchoring the ladder is a design decision — see `appTypography`, which
     * records the AS3's own four sizes and why they are not these numbers — and it should have to
     * come past a failing test rather than happening as a side effect of tidying one screen.
     */
    @Test
    fun theTypeScaleIsTheLadderTheScreensUse() {
        val (_, typography, _) = read()

        val ladder = listOf(
            "screen title" to typography.headlineSmall,
            "button" to typography.titleMedium,
            "row title" to typography.titleSmall,
            "body, and the default" to typography.bodyLarge,
            "secondary line" to typography.bodySmall,
            "metadata" to typography.labelMedium,
            "the smallest" to typography.labelSmall,
        )

        assertEquals(
            LADDER,
            ladder.map { (_, style) -> style.fontSize.value.toInt() },
            "the scale is ${ladder.map { it.first }}",
        )
    }

    /**
     * The two Raleway faces are in the bundle.
     *
     * The same job `CardBundleTest` does for `cards.json`: a font dropped from packaging is a
     * failure no unit test would otherwise see, and the app would go on rendering in the platform
     * font without complaining. Reaching a composed screen at all is what proves it, since
     * `appTypography` resolves both faces before the first frame.
     */
    @Test
    fun theFontResourcesAreShipped() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }

        awaitMenu()
    }

    /**
     * `theme/BaseTTOTheme.as:124-137` and `display/Card.as:29-31`, named as the AS3 names them.
     *
     * Written out here rather than read off `com.tripletriad.ui.theme` so the test is a second,
     * independent transcription: comparing the theme against itself would pass whatever it said.
     */
    private companion object {
        val PRIMARY_BACKGROUND = Color(0xFF202020)
        val LIST_BACKGROUND = Color(0xFF383430)
        val GROUPED_LIST_HEADER = Color(0xFF2E2A26)
        val LIGHT_TEXT = Color(0xFFE5E5E5)
        val SELECTED_TEXT = Color(0xFFFF9900)

        val CARD_BLUE = Color(0xFF2D4660)
        val CARD_RED = Color(0xFF602D2D)
        val CARD_GREY = Color(0xFF5A595A)
        val LARGE_BLUE_ELEMENT = Color(0xFF43A7C8)
        val LARGE_RED_ELEMENT = Color(0xFFBB594F)

        /** Screen title, button, row title, body, secondary, metadata, smallest. */
        val LADDER = listOf(18, 16, 15, 14, 13, 12, 11)
    }
}
