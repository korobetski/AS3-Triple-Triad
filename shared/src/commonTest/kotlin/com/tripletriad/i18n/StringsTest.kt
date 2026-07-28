package com.tripletriad.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [Strings] and [AppLocale] as pure logic — no resource bundle, so this runs on every target.
 *
 * What the real bundles contain is
 * [`StringsBundleTest`](../../../../../desktopTest/kotlin/com/tripletriad/i18n/StringsBundleTest.kt)
 * covers.
 */
class StringsTest {
    private val strings = Strings(
        locale = AppLocale.FR_FR,
        values = mapOf(
            "BOTH" to "les deux",
            "ARGS" to "{1} et {0}",
        ),
        fallback = mapOf(
            "BOTH" to "both",
            "ONLY_FALLBACK" to "fallback only",
            "ARGS" to "{0} and {1}",
        ),
    )

    @Test
    fun theChosenLocaleWinsOverTheFallback() {
        assertEquals("les deux", strings["BOTH"])
    }

    @Test
    fun theFallbackCoversWhatTheLocaleDoesNotTranslate() {
        assertEquals("fallback only", strings["ONLY_FALLBACK"])
        assertFalse(strings.isTranslated("ONLY_FALLBACK"), "it came from the fallback")
        assertTrue(strings.has("ONLY_FALLBACK"), "but it did resolve")
    }

    /**
     * The deliberate last resort. A missing string must be *visible* — `STR_WHATEVER` on screen is
     * a bug report anyone can read — rather than an empty label or a crash on a screen that is
     * otherwise fine.
     */
    @Test
    fun anUnknownKeyResolvesToItself() {
        assertEquals("STR_NOT_A_KEY", strings["STR_NOT_A_KEY"])
        assertFalse(strings.has("STR_NOT_A_KEY"))
    }

    @Test
    fun formatSubstitutesByPositionSoTranslatorsCanReorder() {
        assertEquals("second et first", strings.format("ARGS", "first", "second"))
        val english = Strings(AppLocale.EN_US, emptyMap(), mapOf("ARGS" to "{0} and {1}"))
        assertEquals("first and second", english.format("ARGS", "first", "second"))
    }

    @Test
    fun formatLeavesUnsuppliedPlaceholdersAloneRatherThanBlankingThem() {
        assertEquals("{1} et missing", strings.format("ARGS", "missing"))
    }

    @Test
    fun aPlatformTagIsNarrowedToASupportedLocale() {
        // The three shapes the platforms actually produce, plus the region-only-differs case.
        for (tag in listOf("fr", "fr-FR", "fr_FR", "fr-CA")) {
            assertEquals(AppLocale.FR_FR, AppLocale.match(tag), tag)
        }
        assertEquals(AppLocale.DE_DE, AppLocale.match("de-AT"))
        // `ja_JA` is the original's typo for a region that does not exist; a device says `ja-JP`.
        assertEquals(AppLocale.JA_JA, AppLocale.match("ja-JP"))
        assertEquals(AppLocale.EN_US, AppLocale.match("en-GB"))
    }

    @Test
    fun anUnsupportedLanguageFallsBackRatherThanFailing() {
        for (tag in listOf("es-ES", "zh-Hans-CN", "", "-", "xx")) {
            assertSame(AppLocale.Default, AppLocale.match(tag), tag)
        }
    }

    @Test
    fun tagsRoundTripThroughForTag() {
        for (locale in AppLocale.entries) {
            assertSame(locale, AppLocale.forTag(locale.tag), locale.tag)
        }
        assertNull(AppLocale.forTag("fr"), "forTag is exact; match() is the lenient one")
    }

    /**
     * The display names are each written in the language they name, so a picker is readable by
     * someone who cannot read the language currently selected. `conf.as:11` does the same.
     */
    @Test
    fun everyLocaleHasATagAndANativeDisplayName() {
        assertEquals(EXPECTED_LOCALES, AppLocale.entries.size)
        assertEquals(
            listOf("English", "Français", "Deutsch", "日本語"),
            AppLocale.entries.map { it.displayName },
        )
        assertEquals(AppLocale.entries.size, AppLocale.entries.map { it.tag }.toSet().size)
    }

    private companion object {
        /** `application.xml`: `<supportedLanguages>de en fr ja</supportedLanguages>`. */
        const val EXPECTED_LOCALES = 4
    }
}
