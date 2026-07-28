package com.tripletriad.i18n

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The **shipped** locale bundles, read through the resource loader.
 *
 * So this fails if a bundle is dropped from packaging, if `import_locales.py` writes something
 * that is not a flat object of strings, or if a key the UI names stops existing. The lookup logic
 * itself is [StringsTest]'s business, in `commonTest`, where it needs no resources.
 */
class StringsBundleTest {
    private val loaded = AppLocale.entries.associateWith { runBlocking { loadStrings(it) } }

    @Test
    fun allFourBundlesLoad() {
        assertEquals(AppLocale.entries.size, loaded.size)
        for ((locale, strings) in loaded) {
            assertEquals(locale, strings.locale)
        }
    }

    /**
     * The check that earns the whole file: every key the UI looks up resolves to a real string in
     * every locale, rather than to itself.
     *
     * `Strings[key]` returning the key is a deliberate last resort, which makes a typo in a key
     * constant invisible in review — it renders as `STR_NEXT_MACTH` on a device and nowhere else.
     */
    @Test
    fun everyKeyTheUiUsesResolvesInEveryLocale() {
        val unresolved = mutableListOf<String>()
        for ((locale, strings) in loaded) {
            for (key in StringKeys.all) {
                if (!strings.has(key) || strings[key] == key) {
                    unresolved += "${locale.tag}/$key"
                }
            }
        }
        assertTrue(unresolved.isEmpty(), "unresolved: $unresolved")
    }

    /**
     * `en_US` is what every other locale falls back to, so a key missing *there* is a key that can
     * reach a screen as its own name however many locales define it.
     */
    @Test
    fun theFallbackLocaleDefinesEveryKeyTheUiUses() {
        val fallback = loaded.getValue(AppLocale.Default)
        for (key in StringKeys.all) {
            assertTrue(fallback.isTranslated(key), "$key is absent from ${AppLocale.Default.tag}")
        }
    }

    @Test
    fun theImportedBundlesStillHoldTheirFullKeySets() {
        // Counts measured from `sources/bin/datas/locales/` after duplicate resolution. A change
        // here means a re-import changed the data, which is fine — but it should be deliberate.
        for ((locale, expected) in TRANSLATED_KEYS) {
            assertEquals(expected, loaded.getValue(locale).translatedKeys.size, locale.tag)
        }
    }

    /**
     * How incomplete the non-English bundles are, stated as a number.
     *
     * Not a pass/fail bar — the fallback chain is what makes the gaps harmless — but a regression
     * fence: if a re-import silently halved `de_DE`, nothing else here would notice.
     */
    @Test
    fun theTranslationGapsAreTheKnownOnes() {
        val union = AppLocale.entries.flatMap { loaded.getValue(it).translatedKeys }.toSet()
        assertEquals(UNION_KEYS, union.size, "keys across all four bundles")
        for ((locale, expected) in TRANSLATED_KEYS) {
            val missing = union.size - expected
            assertEquals(EXPECTED_GAPS.getValue(locale), missing, "${locale.tag} missing count")
        }
    }

    /**
     * German and Japanese have no translation for the five `APP_*` strings this port wrote, so
     * they resolve through English. That is the intended behaviour and not an oversight — see
     * [loadStrings] — and it is asserted rather than merely documented so that translating them
     * later has to come past this test and update it.
     */
    @Test
    fun theAppOwnedStringsAreTranslatedInEnglishAndFrenchAndFallBackElsewhere() {
        for (key in StringKeys.appOwned) {
            for (locale in listOf(AppLocale.EN_US, AppLocale.FR_FR)) {
                assertTrue(loaded.getValue(locale).isTranslated(key), "${locale.tag}/$key")
            }
            for (locale in listOf(AppLocale.DE_DE, AppLocale.JA_JA)) {
                assertFalse(
                    loaded.getValue(locale).isTranslated(key),
                    "${locale.tag}/$key is translated now — update this test and the KDoc",
                )
                assertEquals(
                    loaded.getValue(AppLocale.EN_US)[key],
                    loaded.getValue(locale)[key],
                    "${locale.tag}/$key should fall through to English",
                )
            }
        }
    }

    /** French really is French, so the wiring cannot be quietly serving one bundle to everyone. */
    @Test
    fun theBundlesActuallyDiffer() {
        val english = loaded.getValue(AppLocale.EN_US)
        val french = loaded.getValue(AppLocale.FR_FR)
        assertEquals("Continue", english[CONTINUE])
        assertEquals("Continuer", french[CONTINUE])
        assertEquals("chargement des cartes…", french[StringKeys.LOADING_CARDS])
        assertEquals("続行", loaded.getValue(AppLocale.JA_JA)[CONTINUE])
        assertEquals("Weiter", loaded.getValue(AppLocale.DE_DE)[CONTINUE])
    }

    /**
     * `STR_REGISTER_MATCH` is declared twice in both `en_US` and `fr_FR` with **different** values.
     * `import_locales.py` resolves it last-wins, the way AS3's `JSON.parse` did, so the port shows
     * what the original showed. Pinned here because the alternative is a silent product change.
     */
    @Test
    fun theDuplicatedKeyKeepsTheValueTheOriginalDisplayed() {
        assertEquals("Defy", loaded.getValue(AppLocale.EN_US)[REGISTER_MATCH])
        assertEquals("Défier", loaded.getValue(AppLocale.FR_FR)[REGISTER_MATCH])
    }

    private companion object {
        const val CONTINUE = "STR_CONTINUE"
        const val REGISTER_MATCH = "STR_REGISTER_MATCH"

        /** Keys defined by any of the four bundles, `import_locales.py`'s reported union plus 5. */
        const val UNION_KEYS = 696

        /**
         * Imported key count plus however many `APP_*` strings that locale translates: 687 + 5,
         * 688 + 5, then 647 and 680 with no app-owned strings at all.
         */
        val TRANSLATED_KEYS = mapOf(
            AppLocale.EN_US to 692,
            AppLocale.FR_FR to 693,
            AppLocale.DE_DE to 647,
            AppLocale.JA_JA to 680,
        )

        /**
         * What each locale is short of the union.
         *
         * `en_US` is short 4 because of four keys no other locale shares either: `RULE_OPEN`
         * (only `de_DE`, a pre-rename leftover), `STR_GSGROUP` (only `fr_FR`) and two malformed
         * `ja_JA` keys — `STR_SAVES_LISTは` and a `STR_NPC_MA_DINCHT` with two trailing
         * zero-width spaces. All four are unreachable typos in the original data, kept rather
         * than quietly deleted; `tools/import_locales.py` reports them on every run.
         */
        val EXPECTED_GAPS = mapOf(
            AppLocale.EN_US to 4,
            AppLocale.FR_FR to 3,
            // 44 imported keys short, plus the 5 app-owned; and 11 short, plus the 5.
            AppLocale.DE_DE to 49,
            AppLocale.JA_JA to 16,
        )
    }
}
