package com.tripletriad.settings

import com.tripletriad.i18n.AppLocale
import com.tripletriad.log.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `UserSettings.json`, field for field as `utils/conf.as` wrote it.
 *
 * ```actionscript
 * DATAS.background_volume = 1;
 * DATAS.noise_volume = 1;
 * DATAS.language = Capabilities.languages[0];   // or 'en_US'
 * ```
 *
 * The `@SerialName`s are the AS3 keys, deliberately snake_case against Kotlin convention: a file
 * written by the original must still parse, and a file this port writes must still be readable by
 * it. [UserSettingsTest] pins the on-disk shape for that reason — a rename would be silent
 * otherwise, since the fields have defaults and `ignoreUnknownKeys` is on.
 *
 * @property language an [AppLocale.tag]. Written on first run from the device language, exactly as
 *   `conf.as:31-37` does, which also means a later change of *device* language does not follow —
 *   the file wins. That is the original's behaviour, and the settings screen is where it is meant
 *   to be changed.
 * @property backgroundVolume music, 0..1. `SoundTransform.volume`'s range.
 * @property noiseVolume effects, 0..1. Two independent channels because `SoundManager` has two
 *   (`BACKGROUND_CHANNEL`, `NOISE_CHANNEL`) — with one, every card flip would duck the music.
 */
@Serializable
data class UserSettings(
    @SerialName("language") val language: String = AppLocale.Default.tag,
    @SerialName("background_volume") val backgroundVolume: Float = FULL_VOLUME,
    @SerialName("noise_volume") val noiseVolume: Float = FULL_VOLUME,
) {
    /**
     * [language] as a locale, [AppLocale.Default] if the file names one this build does not have.
     *
     * Lenient on purpose: the file is user-writable and outlives the app, so a tag that was valid
     * once may not be now. An unreadable language must not be able to stop the app starting.
     */
    val locale: AppLocale get() = AppLocale.forTag(language) ?: AppLocale.match(language)

    /**
     * The same settings with both volumes brought into 0..1.
     *
     * A **deviation**: `conf.as` reads its two volumes straight into `SoundManager` with no bound,
     * so a hand-edited `2` there is passed to `SoundTransform.volume`. Clamping costs nothing and
     * the alternative is a value that either misbehaves or throws inside a platform audio API.
     */
    fun sane(): UserSettings = copy(
        backgroundVolume = backgroundVolume.coerceIn(0f, FULL_VOLUME),
        noiseVolume = noiseVolume.coerceIn(0f, FULL_VOLUME),
    )

    companion object {
        const val FULL_VOLUME: Float = 1f
    }
}

/**
 * Loads and saves [UserSettings] through a [SettingsStore].
 *
 * The first-run write is `conf.as:22-40`: no file means write one, seeded from the device language,
 * so the app never runs without a settings file to change.
 */
class UserSettingsRepository(private val store: SettingsStore) {
    /**
     * The stored settings, creating them on first run.
     *
     * @param deviceLocale what to record as the language when there is nothing stored.
     */
    suspend fun load(deviceLocale: AppLocale): UserSettings {
        val read = runCatching { store.read() }
        read.exceptionOrNull()?.let { failure ->
            Log.w(TAG, failure) { "could not read the settings file; starting from defaults" }
        }
        val stored = read.getOrNull()
        if (stored.isNullOrBlank()) {
            return UserSettings(language = deviceLocale.tag).also { save(it) }
        }
        // A corrupt file is repaired rather than fatal. `conf.as` would have thrown out of
        // `JSON.parse` here and taken the launch with it; there is nothing in this file worth
        // failing to start over, and leaving it unrepaired would mean throwing on every launch.
        // It is logged, though: repairing silently is how a file that is rewritten on every
        // launch goes unnoticed for months.
        val parsed = runCatching { Format.decodeFromString<UserSettings>(stored) }
        parsed.exceptionOrNull()?.let { failure ->
            Log.w(TAG, failure) { "settings file is not readable JSON; replacing it" }
        }
        return parsed.getOrNull()?.sane()
            ?: UserSettings(language = deviceLocale.tag).also { save(it) }
    }

    /**
     * Writes [settings]. A failure is logged and swallowed — losing a volume change is not worth
     * a crash, but it is worth knowing about, which is the whole reason [Log] exists.
     */
    suspend fun save(settings: UserSettings) {
        runCatching { store.write(Format.encodeToString(settings.sane())) }
            .exceptionOrNull()
            ?.let { failure -> Log.w(TAG, failure) { "could not write the settings file" } }
    }

    private companion object {
        const val TAG = "Settings"

        /**
         * `ignoreUnknownKeys` because the AS3 `DATAS` object is a free-for-all — anything assigned
         * to it lands in the same file — and dropping a key this build does not know about would
         * quietly delete it on the next save. `encodeDefaults` so a fresh file is complete and
         * hand-editable rather than `{}`.
         */
        val Format = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
    }
}
