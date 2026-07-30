package com.tripletriad.storage

/**
 * A small keyed collection of text documents.
 *
 * ### Why this and not SQLDelight
 *
 * `docs/migration/06-PHASE-2-DATA-LAYER.md` Task 2.3 asks for SQLDelight with a `GameSave` table
 * of 23 columns, eight of which hold JSON. That is a document store with extra steps: nothing in
 * it is ever queried by column, because the AS3 original reads a whole profile or none of it
 * (`Save.load()` → `JSON.parse` of one file). A relational schema buys indexes and partial reads
 * that no caller wants, and costs a plugin, a code generator and one driver per platform — with the
 * iOS driver unbuildable from the Windows host this project develops on.
 *
 * The same reasoning as [com.tripletriad.settings.SettingsStore], one level up: **the shared module
 * declares what it needs, each host supplies it.** `:androidApp` has a `Context` and `filesDir`,
 * `:desktopApp` has `user.home`, and `:shared` needs neither. If a query ever appears that
 * genuinely wants SQL — match-history aggregates over thousands of rows is the plausible one — it
 * goes behind [com.tripletriad.data.MatchHistoryRepository] without touching a caller.
 *
 * ### Why it is separate from `SettingsStore`
 *
 * [com.tripletriad.settings.SettingsStore] holds exactly **one** unnamed blob, because there is one
 * settings file. Profiles are many and are enumerated (the AS3 load screen lists the `.sav`
 * files in `saves/`), so this adds [keys] and [delete]. Making `SettingsStore` a one-key case of it
 * would have forced every settings caller to invent a key, and widened an interface that is
 * deliberately two methods.
 *
 * ### Contract
 *
 * - [read] returns null when the key has never been written. That is distinct from a stored empty
 *   string, and it is the "no such profile" signal.
 * - [keys] returns the keys [read] would answer for, in unspecified order. Callers sort.
 * - [delete] on an absent key succeeds silently.
 * - A missing document is never an exception. Anything else — permission denied, disk full, a
 *   corrupt filesystem — may throw, and the caller decides. For saves that means surfacing the
 *   failure, because a save that silently did not happen is worse than one that reports it.
 *
 * Keys are treated as opaque names by callers but are used as **filenames** by host
 * implementations, so they must not contain path separators. [sanitizeKey] is the shared guard.
 */
interface DocumentStore {
    /** The document stored under [key], or null if there is none. */
    suspend fun read(key: String): String?

    /** Replaces the document under [key], creating it if needed. */
    suspend fun write(key: String, text: String)

    /** Every key with a stored document. */
    suspend fun keys(): List<String>

    /** Removes [key]'s document if it exists. */
    suspend fun delete(key: String)
}

/**
 * Rejects keys that could escape the store's directory or collide once written to a filesystem.
 *
 * Called by every implementation rather than by the interface (Kotlin has no way to enforce a
 * precondition on an interface method) so that the in-memory store rejects exactly what the file
 * stores would. A test that passes against [InMemoryDocumentStore] then means something.
 *
 * Profile keys come from a user-supplied profile name, so this is reachable input, not a
 * programming error — but it is caught at the boundary where the caller can still report it.
 */
fun sanitizeKey(key: String): String {
    require(key.isNotBlank()) { "document key must not be blank" }
    require(key.none { it == '/' || it == '\\' || it == ':' }) {
        "document key must not contain a path separator, was '$key'"
    }
    require(key != "." && key != "..") { "document key must not be '$key'" }
    return key
}

/**
 * A [DocumentStore] that forgets everything when the process ends.
 *
 * The default for tests and previews. Like [com.tripletriad.settings.InMemorySettingsStore] this is
 * a working implementation rather than a stub: a repository under test behaves normally for one
 * session, so the save/load cycle is exercisable without a filesystem.
 */
class InMemoryDocumentStore(
    initial: Map<String, String> = emptyMap(),
    /**
     * Makes every operation throw, so a caller's failure path is reachable from a test. The
     * interface contract allows it, and a full disk on a real device is exactly this.
     */
    private val failure: Throwable? = null,
) : DocumentStore {
    private val documents: MutableMap<String, String> = initial.toMutableMap()

    /** How many times [write] has been called, so a test can assert it was *not*. */
    var writes: Int = 0
        private set

    /** What is stored, without suspending — for assertions from a non-coroutine test body. */
    val stored: Map<String, String> get() = documents.toMap()

    override suspend fun read(key: String): String? {
        failure?.let { throw it }
        return documents[sanitizeKey(key)]
    }

    override suspend fun write(key: String, text: String) {
        failure?.let { throw it }
        documents[sanitizeKey(key)] = text
        writes++
    }

    override suspend fun keys(): List<String> {
        failure?.let { throw it }
        return documents.keys.toList()
    }

    override suspend fun delete(key: String) {
        failure?.let { throw it }
        documents.remove(sanitizeKey(key))
    }
}
