package com.tripletriad.settings

import com.tripletriad.i18n.AppLocale
import com.tripletriad.log.Log
import com.tripletriad.log.LogLevel
import com.tripletriad.log.RecordingSink
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [UserSettings] and [UserSettingsRepository] against an [InMemorySettingsStore], so this runs on
 * every target and needs no filesystem.
 *
 * The platform stores are not covered here and are not covered anywhere: they are twenty lines of
 * `File` calls each, they live in the host modules, and testing them would mean testing
 * `java.io.File`. What *is* worth pinning is the on-disk **shape**, because an existing
 * `UserSettings.json` written by the AS3 build has to keep working.
 */
class UserSettingsTest {
    @Test
    fun firstRunWritesAFileSeededFromTheDeviceLanguage() = runTest {
        val store = InMemorySettingsStore()
        val settings = UserSettingsRepository(store).load(AppLocale.FR_FR)

        assertEquals(AppLocale.FR_FR, settings.locale)
        assertEquals(1f, settings.backgroundVolume)
        assertEquals(1f, settings.noiseVolume)
        assertEquals(1, store.writes, "`conf.as` persists on first run so the file exists to edit")
    }

    @Test
    fun aSecondRunReadsTheFileAndLeavesItAlone() = runTest {
        val store = InMemorySettingsStore()
        UserSettingsRepository(store).load(AppLocale.FR_FR)
        val writesAfterFirstRun = store.writes

        // A different device language must not win over what is stored — `conf.as` seeds the file
        // once and then the file decides.
        val second = UserSettingsRepository(store).load(AppLocale.JA_JA)

        assertEquals(AppLocale.FR_FR, second.locale)
        assertEquals(writesAfterFirstRun, store.writes, "nothing changed, so nothing was written")
    }

    /**
     * A real `UserSettings.json` as the AS3 build writes it: `JSON.stringify` of the `DATAS`
     * object, so compact, unordered, and with integral volumes that are `Number`s.
     */
    @Test
    fun aFileWrittenByTheAs3BuildStillParses() = runTest {
        val store = InMemorySettingsStore(
            """{"background_volume":0.6,"noise_volume":1,"language":"de_DE"}""",
        )

        val settings = UserSettingsRepository(store).load(AppLocale.EN_US)

        assertEquals(AppLocale.DE_DE, settings.locale)
        assertEquals(0.6f, settings.backgroundVolume)
        assertEquals(1f, settings.noiseVolume)
    }

    /** The keys are the AS3's, so a rename cannot happen quietly. */
    @Test
    fun theFileKeepsTheAs3KeyNames() = runTest {
        val store = InMemorySettingsStore()
        UserSettingsRepository(store).save(
            UserSettings(language = "ja_JA", backgroundVolume = 0.25f, noiseVolume = 0f),
        )

        val written = store.read().orEmpty()
        for (key in AS3_KEYS) {
            assertTrue(written.contains("\"$key\""), "$key missing from: $written")
        }
        assertTrue(written.contains("\"ja_JA\""))
    }

    /**
     * `DATAS` in the AS3 is a bare object anything can be assigned to, so a file in the wild may
     * hold keys this build has never heard of. They must not stop it parsing.
     */
    @Test
    fun unknownKeysDoNotBreakParsing() = runTest {
        val store = InMemorySettingsStore(
            """{"language":"fr_FR","noise_volume":1,"background_volume":1,"lastPlayedNpc":42}""",
        )

        assertEquals(AppLocale.FR_FR, UserSettingsRepository(store).load(AppLocale.EN_US).locale)
    }

    /**
     * A corrupt file is repaired, not fatal. `conf.as` would have thrown out of `JSON.parse` and
     * taken the launch with it; there is nothing in this file worth failing to start over.
     */
    @Test
    fun aCorruptFileIsReplacedRatherThanThrown() = runTest {
        val store = InMemorySettingsStore("{ this is not json")

        val settings = UserSettingsRepository(store).load(AppLocale.JA_JA)

        assertEquals(AppLocale.JA_JA, settings.locale, "it fell back to the device language")
        assertEquals(1, store.writes, "and rewrote it, so the next launch does not throw either")
    }

    @Test
    fun anEmptyFileCountsAsNoFile() = runTest {
        // `TTOFiles.createFile` writes an empty string, and `conf.as:23` tests for exactly `''`.
        val store = InMemorySettingsStore("")

        assertEquals(AppLocale.DE_DE, UserSettingsRepository(store).load(AppLocale.DE_DE).locale)
        assertEquals(1, store.writes)
    }

    @Test
    fun aLanguageThisBuildDoesNotHaveFallsBackRatherThanFailing() = runTest {
        val store = InMemorySettingsStore("""{"language":"es_ES"}""")

        assertEquals(AppLocale.Default, UserSettingsRepository(store).load(AppLocale.FR_FR).locale)
    }

    /** A device tag that is not one of the four still resolves, because `match` is lenient. */
    @Test
    fun aRegionalVariantInTheFileStillResolves() = runTest {
        val store = InMemorySettingsStore("""{"language":"fr-CA"}""")

        assertEquals(AppLocale.FR_FR, UserSettingsRepository(store).load(AppLocale.EN_US).locale)
    }

    @Test
    fun volumesOutsideZeroToOneAreClamped() = runTest {
        val store = InMemorySettingsStore(
            """{"language":"en_US","background_volume":2.5,"noise_volume":-1}""",
        )

        val settings = UserSettingsRepository(store).load(AppLocale.EN_US)

        assertEquals(1f, settings.backgroundVolume, "SoundTransform.volume tops out at 1")
        assertEquals(0f, settings.noiseVolume)
    }

    /**
     * The three swallowed failures are the reason [Log] exists. Repairing a settings file
     * silently is how a file that is rewritten on every single launch goes unnoticed.
     */
    @Test
    fun aRepairedFileSaysSoInTheLog() = runTest {
        val sink = RecordingSink()
        Log.install(sink)
        try {
            val store = InMemorySettingsStore("{ this is not json")
            UserSettingsRepository(store).load(AppLocale.FR_FR)

            val warning = sink.at(LogLevel.WARN).single()
            assertTrue(warning.message.contains("JSON"), "unhelpful: ${warning.message}")
            assertNotNull(warning.error, "the parse failure itself was dropped")
        } finally {
            Log.reset()
        }
    }

    @Test
    fun anUnreadableStoreIsReportedAndFallsBackRatherThanThrowing() = runTest {
        val sink = RecordingSink()
        Log.install(sink)
        try {
            val store = InMemorySettingsStore(failure = IllegalStateException("permission denied"))

            val settings = UserSettingsRepository(store).load(AppLocale.JA_JA)

            assertEquals(AppLocale.JA_JA, settings.locale, "it must still start")
            // Two warnings: the read failed, then the first-run write it triggered failed too.
            assertEquals(2, sink.at(LogLevel.WARN).size, sink.lines.toString())
            assertEquals(0, store.writes)
        } finally {
            Log.reset()
        }
    }

    private companion object {
        /** `conf.as:25-26` and `conf.as:33`. */
        val AS3_KEYS = listOf("language", "background_volume", "noise_volume")
    }
}
