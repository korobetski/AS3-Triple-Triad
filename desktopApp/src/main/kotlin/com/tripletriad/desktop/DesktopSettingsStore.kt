package com.tripletriad.desktop

import com.tripletriad.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * `UserSettings.json` under the user's home directory.
 *
 * This is the one host where the AS3 path can be kept almost verbatim — `conf.as:14` wrote
 * `My Games/Triple Triad Online/UserSettings.json` into AIR's `documentsDirectory`, which on
 * Windows is `%USERPROFILE%\Documents`. Reproducing that exactly would need a per-OS notion of
 * "Documents" that the JDK does not offer, so this uses `user.home` and keeps the two path
 * segments, giving `~/My Games/Triple Triad Online/UserSettings.json`.
 *
 * The desktop host exists to run the UI without an emulator, so the value here is mostly that a
 * second implementation of [SettingsStore] keeps the interface honest — anything that only ever has
 * one implementation is not really an abstraction.
 */
class DesktopSettingsStore(home: File = File(System.getProperty("user.home"))) : SettingsStore {
    private val file = File(home, SETTINGS_PATH)

    override suspend fun read(): String? =
        withContext(Dispatchers.IO) {
            if (file.isFile) file.readText() else null
        }

    override suspend fun write(text: String) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.writeText(text)
        }
    }

    private companion object {
        /** `conf.as:14`. `File(File, String)` splits the separators for the host OS. */
        const val SETTINGS_PATH = "My Games/Triple Triad Online/UserSettings.json"
    }
}
