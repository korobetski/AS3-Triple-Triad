package com.tripletriad.desktop

import com.tripletriad.storage.DocumentStore
import com.tripletriad.storage.sanitizeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One file per document in a directory under the user's home.
 *
 * `~/My Games/Triple Triad Online/<subdirectory>/<key>.<extension>`, which continues the choice
 * [DesktopSettingsStore] made: AIR's `applicationStorageDirectory` — where `TTOFiles.STORAGE_DIR`
 * put `saves/` — has no JDK equivalent, and `user.home` plus the original's two path segments is
 * both predictable and easy for a player to find and back up.
 *
 * @param subdirectory the collection this store holds, e.g. `saves`. Keeps profiles and match
 *   history in separate directories rather than distinguishing them by key prefix, so a stray file
 *   in one cannot show up in the other's [keys].
 * @param extension appended to every key on disk. `sav` keeps the original's file naming.
 */
class DesktopDocumentStore(
    subdirectory: String,
    private val extension: String = "sav",
    home: File = File(System.getProperty("user.home")),
) : DocumentStore {
    private val directory = File(home, "$ROOT/$subdirectory")

    override suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        fileFor(key).let { if (it.isFile) it.readText() else null }
    }

    override suspend fun write(key: String, text: String) {
        val file = fileFor(key)
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            // Write-then-rename, as [DesktopSettingsStore]'s Android counterpart does, and for a
            // stronger reason: a save killed mid-write is a lost profile, not a lost volume
            // setting.
            val temporary = File(directory, "${file.name}.tmp")
            temporary.writeText(text)
            if (!temporary.renameTo(file)) {
                // Windows will not rename onto an existing file. Delete and retry before falling
                // back to a direct write, which is the one path that can leave a truncated save.
                file.delete()
                if (!temporary.renameTo(file)) {
                    file.writeText(text)
                    temporary.delete()
                }
            }
        }
    }

    override suspend fun keys(): List<String> = withContext(Dispatchers.IO) {
        val suffix = ".$extension"
        directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(suffix) }
            .map { it.name.removeSuffix(suffix) }
    }

    override suspend fun delete(key: String) {
        val file = fileFor(key)
        withContext(Dispatchers.IO) { file.delete() }
    }

    private fun fileFor(key: String): File = File(directory, "${sanitizeKey(key)}.$extension")

    private companion object {
        /** Same two segments as `conf.as:14`, so settings and saves sit side by side. */
        const val ROOT = "My Games/Triple Triad Online"
    }
}
