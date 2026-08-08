package com.tripletriad.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.intl.Locale
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import tripletriad.shared.generated.resources.Res

/**
 * The four locales the original supports.
 *
 * `utils/conf.as:11`:
 * ```actionscript
 * public static const supportedLanguages:Object =
 *     {en_US:'English', fr_FR:'Français', de_DE:'Deutsch', ja_JA:'日本語'};
 * ```
 * [displayName] is that map's value, which is deliberately written in the language it names —
 * a language picker must be readable by someone who cannot read the current language.
 *
 * `application.xml` declared the same four:
 * `<supportedLanguages>de en fr ja</supportedLanguages>`. An earlier revision of the migration
 * plan listed only English and French, which would have silently dropped two thirds of the
 * translated text that already exists.
 */
enum class AppLocale(val tag: String, val displayName: String) {
    EN_US("en_US", "English"),
    FR_FR("fr_FR", "Français"),
    DE_DE("de_DE", "Deutsch"),
    JA_JA("ja_JA", "日本語"),
    ;

    companion object {
        /**
         * The locale every other one falls back to, and the only one guaranteed complete.
         *
         * `conf.as:35` does the same — `DATAS.language = 'en_US'` when the device language is
         * not one of the four.
         */
        val Default: AppLocale = EN_US

        /** The locale with exactly this tag, or null. */
        fun forTag(tag: String): AppLocale? = entries.firstOrNull { it.tag == tag }

        /**
         * Best match for a platform language tag, [Default] if there is none.
         *
         * Accepts what the platforms actually hand over: `fr-FR`, `fr_FR` and bare `fr` all
         * resolve to [FR_FR]. The language alone is enough to decide here because no two of the
         * four share one — matching on region would reject `fr-CA` for no benefit.
         *
         * Japanese is the one to watch: its tag is `ja_JA`, which is **not** a real region code
         * (Japan is `JP`). The original's typo is preserved because it is also the file name, so
         * a device reporting the correct `ja-JP` has to be matched by language.
         */
        fun match(languageTag: String): AppLocale {
            val language = languageTag.replace('_', '-').substringBefore('-').lowercase()
            return entries.firstOrNull { it.tag.substringBefore('_') == language } ?: Default
        }
    }
}

/**
 * A resolved string table: one locale, with [AppLocale.Default] behind it.
 *
 * Lookup order is chosen locale, then fallback, then the key itself. Returning the key rather
 * than throwing or returning empty is deliberate: a missing string should show up on screen as
 * `STR_SOMETHING` — obvious, greppable, and it does not take the screen down with it. The tests
 * assert on the *absence* of that shape, which is what stops it becoming normal.
 */
class Strings internal constructor(
    val locale: AppLocale,
    private val values: Map<String, String>,
    private val fallback: Map<String, String>,
) {
    /** The string for [key], or the key when no locale has one. */
    operator fun get(key: String): String = values[key] ?: fallback[key] ?: key

    /** True when [key] resolves to a real string in either table. */
    fun has(key: String): Boolean = key in values || key in fallback

    /** True when [key] is translated in this locale rather than served by the fallback. */
    fun isTranslated(key: String): Boolean = key in values

    /**
     * The keys this locale translates itself, fallback excluded.
     *
     * Exposed for tooling and tests — a coverage report between locales needs to enumerate, not
     * just look up. `de_DE` is 44 keys short of `en_US`, and that is the number that says so.
     */
    val translatedKeys: Set<String> get() = values.keys

    /** Every key that resolves to something: this locale's, plus the fallback's. */
    val keys: Set<String> get() = values.keys + fallback.keys

    /**
     * [get] with `{0}`, `{1}`… replaced by [args].
     *
     * None of the 691 imported strings contains a placeholder — the AS3 built its sentences by
     * concatenation — so this exists for the `APP_*` strings, where the whole point is that word
     * order is the translator's to choose. French wants "au bleu de jouer" where English wants
     * "blue to play"; a concatenation cannot express that and a positional placeholder can.
     */
    fun format(key: String, vararg args: String): String {
        var text = get(key)
        for ((index, arg) in args.withIndex()) {
            text = text.replace("{$index}", arg)
        }
        return text
    }
}

/**
 * Makes [Strings] ambient.
 *
 * The default is an empty table, so every lookup returns its key. That is a working state for a
 * preview or a unit test — text renders, layout is exercised — and it is loud enough that nobody
 * mistakes it for a translation.
 */
val LocalStrings: androidx.compose.runtime.ProvidableCompositionLocal<Strings> =
    staticCompositionLocalOf { Strings(AppLocale.Default, emptyMap(), emptyMap()) }

/**
 * The device's locale, as one of the four.
 *
 * `androidx.compose.ui.text.intl.Locale.current` is Compose's own multiplatform accessor, so
 * this needs no `expect`/`actual` — the AS3 read `Capabilities.languages[0]` (`conf.as:31`).
 */
@Composable
fun rememberDeviceLocale(): AppLocale = AppLocale.match(Locale.current.toLanguageTag())

/**
 * Loads [locale] and republishes when it arrives; the empty table until then.
 *
 * Separate from the card catalog on purpose: this is ~130 KB of two JSON files and the catalog is
 * 60 KB of records, and neither should wait for the other.
 */
@Composable
fun rememberStrings(locale: AppLocale): Strings {
    val strings by produceState(EmptyStrings, locale) { value = loadStrings(locale) }
    return strings
}

/**
 * Reads [locale] plus the fallback out of the resource bundle.
 *
 * Two files per locale, merged with the app-owned one on top:
 *
 * | File | Origin | Editable |
 * |---|---|---|
 * | `tto-<tag>.json` | the AS3 bundles, via `tools/import_locales.py` | no — regenerate it |
 * | `app-<tag>.json` | this port, for text the original never had | yes |
 *
 * The split is about provenance. `tto-*` is 687 keys of Square Enix wording that must stay
 * exactly what the original displayed; `app-*` is 41 keys this port needed — mostly because the
 * AS3 showed something graphically and never wrote the sentence, and in the tutorial's case
 * because it wrote the sentences as Flash literals with no key at all. Keeping them apart means a
 * re-import cannot quietly revert hand-written text, and `APP_` on a key says at a glance which
 * side of that line it came from.
 *
 * `app-de_DE.json` and `app-ja_JA.json` are `{}` — deliberately present and deliberately empty.
 * Those sentences are not translated into German or Japanese, so they resolve through the
 * fallback to English while the other 647/680 keys stay in the device's language. An empty file
 * states that; a missing file would just look like an oversight.
 */
suspend fun loadStrings(locale: AppLocale): Strings {
    val fallback = readLocale(AppLocale.Default)
    val values = if (locale == AppLocale.Default) fallback else readLocale(locale)
    return Strings(locale, values, fallback)
}

private suspend fun readLocale(locale: AppLocale): Map<String, String> =
    readBundle("tto-${locale.tag}") + readBundle("app-${locale.tag}")

@OptIn(ExperimentalResourceApi::class)
private suspend fun readBundle(name: String): Map<String, String> =
    Bundles.decodeFromString(Res.readBytes("$LOCALE_PATH/$name.json").decodeToString())

/**
 * `isLenient` is off and `ignoreUnknownKeys` is irrelevant for a map: a bundle that is not a flat
 * object of strings should fail loudly at import time, and `CardBundleTest` reads all four so it
 * fails at test time rather than on a device.
 */
private val Bundles = Json

private val EmptyStrings = Strings(AppLocale.Default, emptyMap(), emptyMap())

/** Where [`import_locales.py`](../../../../../../../tools/import_locales.py) writes. */
private const val LOCALE_PATH = "files/locales"
