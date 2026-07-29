package com.tripletriad.android

import android.content.Context
import com.tripletriad.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * `UserSettings.json` in the app's own private storage.
 *
 * ### Not where the AS3 put it
 *
 * `conf.as:14` writes `My Games/Triple Triad Online/UserSettings.json` under AIR's
 * `File.documentsDirectory` — on Android of that era, shared external storage. That path is wrong
 * three times over now: scoped storage (API 29+) stopped apps writing arbitrary shared
 * directories, `minSdk` is 24 so a legacy path would need a permission prompt and a runtime branch,
 * and a settings file has no business being visible to other apps or to a file manager.
 *
 * `Context.filesDir` is the correct target: no permission, survives updates, removed on uninstall.
 * The file name is kept so the contents remain recognisable, and the two `My Games` path segments
 * are dropped rather than recreated — they were an AIR-on-Windows convention.
 *
 * ### Threading
 *
 * `withContext(Dispatchers.IO)` on both sides. The file is a few hundred bytes, but it is read
 * during composition and written from a click, and neither is a place to touch a filesystem
 * synchronously.
 */
class AndroidSettingsStore(context: Context) : SettingsStore {
    private val file = File(context.filesDir, SETTINGS_FILE)

    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        if (file.isFile) file.readText() else null
    }

    override suspend fun write(text: String) {
        withContext(Dispatchers.IO) {
            // Write beside it and rename, so a kill mid-write leaves the old file rather than a
            // truncated one. `File.renameTo` is atomic within a directory on every Android
            // filesystem, and the settings are worth exactly this much care and no more.
            val temporary = File(file.parentFile, "$SETTINGS_FILE.tmp")
            temporary.writeText(text)
            if (!temporary.renameTo(file)) {
                file.writeText(text)
                temporary.delete()
            }
        }
    }

    private companion object {
        /** `conf.as:14`, minus the AIR-on-Windows `My Games/Triple Triad Online/` prefix. */
        const val SETTINGS_FILE = "UserSettings.json"
    }
}
