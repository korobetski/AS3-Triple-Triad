package com.tripletriad.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tripletriad.log.Log
import com.tripletriad.storage.DocumentStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One server the player can play on.
 *
 * @property id derived from [baseUrl] and never configured directly — see [of]. It keys the stored
 *   session and the transcript queue, so it has to be stable across launches and across edits to
 *   the *label*: renaming a server must not sign the player out of it.
 * @property label what the player sees. Free text, and the only field a host may change freely.
 * @property baseUrl without a trailing slash.
 */
data class ServerEntry(
    val id: String,
    val label: String,
    val baseUrl: String,
) {
    companion object {
        /**
         * A server identified by where it is.
         *
         * The address is the identity because it is the only thing that decides which accounts
         * exist: two entries pointing at one host are one server however they are labelled, and
         * one entry re-pointed at a different host is a different server whatever it is called.
         * Deriving the id from anything else would let a config edit silently attach one server's
         * token to another's requests.
         */
        fun of(baseUrl: String, label: String? = null): ServerEntry {
            val address = baseUrl.trim().trimEnd('/')
            return ServerEntry(
                id = stableKey(address),
                label = label?.takeIf { it.isNotBlank() } ?: address.substringAfter("://"),
                baseUrl = address,
            )
        }
    }
}

/**
 * Reads a configured server list.
 *
 * The format is one entry per comma, each either an address or `Label=address`:
 *
 * ```
 * Europe=https://eu.example.org, Japan=https://jp.example.org, http://127.0.0.1:8080
 * ```
 *
 * ### Why the hosts share a parser rather than each reading their own configuration
 *
 * Because the ids derived here key the stored session and the transcript queue, and two hosts that
 * disagreed about whether a trailing slash or a stray space belongs to the address would derive two
 * different ids for one server. That is not a cosmetic difference: it is a player signing in on the
 * desktop and finding themselves signed out of the same server on the phone.
 *
 * Entries that repeat an address are dropped rather than merged — one server listed twice under two
 * labels is one server, and showing it twice would offer the player a switch that does nothing.
 * Order is the configuration's, and the first is the default until a choice is stored.
 */
fun serverEntries(spec: String): List<ServerEntry> =
    spec.split(',')
        .mapNotNull { field ->
            val trimmed = field.trim()
            when {
                trimmed.isEmpty() -> null
                // `substringBefore`/`After` on the *first* `=`, so a label may not contain one but
                // an address may — and query strings do.
                '=' in trimmed -> ServerEntry.of(
                    baseUrl = trimmed.substringAfter('=').trim(),
                    label = trimmed.substringBefore('=').trim(),
                )

                else -> ServerEntry.of(trimmed)
            }
        }
        .filter { it.baseUrl.isNotEmpty() }
        .distinctBy { it.id }

/** The stored shape: which server was chosen. Versioned like every other document. */
@Serializable
private data class SelectionDocument(
    val version: Int = ServerDirectory.VERSION,
    val selectedId: String = "",
)

/**
 * The servers this build knows about, and which one is in play.
 *
 * ### Why the selection is observable state and not just a stored value
 *
 * Because it is network configuration the **player** edits while the app is running, and three
 * things have to move the instant it changes: the address every request goes to, the session that
 * is read (a token is worthless on a server that did not issue it), and the queue that is drained.
 * Splitting "what is stored" from "what is showing" would create two answers to a question that
 * must only ever have one — and the window between them is exactly where a request goes to server A
 * with server B's token.
 *
 * So this is the single source, it is observable, and everything downstream reads [selected] rather
 * than being handed an address.
 *
 * ### Switching servers signs you out of the one you left
 *
 * Not implemented here — see `AccountSession` — but decided here, because the reason is a property
 * of this type: a session belongs to the server that issued it. What is *not* thrown away is the
 * session itself; sessions are stored per [ServerEntry.id], so switching back finds the token that
 * was left behind, still valid if it has not expired.
 */
class ServerDirectory(
    private val store: DocumentStore,
    val entries: List<ServerEntry>,
) {
    init {
        require(entries.isNotEmpty()) { "a directory with no servers is a build with no server" }
    }

    /**
     * The server in play. Never null: a build that has a directory has at least one entry, and
     * "no server configured" is expressed by there being no directory at all.
     */
    var selected: ServerEntry by mutableStateOf(entries.first())
        private set

    /**
     * Restores the stored choice, if it is still one of [entries].
     *
     * A stored id that is no longer configured falls back to the first entry rather than failing:
     * a server removed from a build is not a reason to refuse to start, and the fallback is
     * visible — it is what the servers screen shows as selected.
     */
    suspend fun restore() {
        val storedId = read()?.selectedId ?: return
        val entry = entries.firstOrNull { it.id == storedId }
        if (entry == null) {
            Log.i(TAG) { "the stored server is no longer configured; using ${selected.label}" }
            return
        }
        selected = entry
    }

    /**
     * Chooses [entry], and remembers it.
     *
     * Returns whether this was a change, so a caller can skip the sign-out and the re-probe that
     * follow one — reselecting the server you are already on should do nothing at all.
     */
    suspend fun select(entry: ServerEntry): Boolean {
        require(entry in entries) { "'${entry.label}' is not a configured server" }
        if (entry.id == selected.id) return false

        selected = entry
        write(entry.id)
        Log.i(TAG) { "now using ${entry.label} (${entry.baseUrl})" }
        return true
    }

    // The store is per host, so a failed read throws whatever that platform's file API throws, and
    // a directory that cannot read its own choice still has a working default.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun read(): SelectionDocument? = try {
        store.read(KEY)?.let { Format.decodeFromString<SelectionDocument>(it) }
    } catch (failure: Exception) {
        Log.w(TAG, failure) { "could not read the selected server" }
        null
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun write(id: String) {
        try {
            store.write(KEY, Format.encodeToString(SelectionDocument(selectedId = id)))
        } catch (failure: Exception) {
            // Costs the choice on the next launch and nothing else; the player is on the server
            // they picked for as long as the app is running.
            Log.w(TAG, failure) { "could not remember the selected server" }
        }
    }

    companion object {
        private const val TAG = "Servers"

        /** Its own collection, for the reason [TranscriptQueue] gives: it is not a profile. */
        const val COLLECTION = "servers"

        /** One selection, so one document, so a fixed key. */
        const val KEY = "selected"

        const val VERSION = 1

        private val Format = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

/**
 * A document key derived from arbitrary text, safe on every host and stable across launches.
 *
 * Shared by [ServerEntry.of] and [accountQueueKey] because they need exactly the same thing for
 * exactly the same two reasons: a key becomes a filename, and `hashCode` is not required to be
 * stable across processes for anything but a String — so the mixing is written out here rather than
 * inherited from a platform.
 *
 * The hash is not a security property and does not need to be a good hash. It is what keeps two
 * inputs that sanitize to the same characters apart, which is the difference between two servers
 * having their own sessions and one of them using the other's.
 */
internal fun stableKey(text: String): String {
    val normalized = text.lowercase()
    val safe = normalized.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
    var hash = HASH_SEED
    for (character in normalized) {
        hash = hash * HASH_FACTOR + character.code
    }
    return "${safe.take(KEY_PREFIX_LENGTH)}-${hash.toString(HASH_RADIX)}"
}

/** FNV-ish constants. Any stable mixing would do; these are here so the value never changes. */
private const val HASH_SEED = -2128831035
private const val HASH_FACTOR = 16777619
private const val HASH_RADIX = 36
private const val KEY_PREFIX_LENGTH = 24
