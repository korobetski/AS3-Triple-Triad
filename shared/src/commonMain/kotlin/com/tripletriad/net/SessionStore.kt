package com.tripletriad.net

import com.tripletriad.log.Log
import com.tripletriad.storage.DocumentStore
import com.tripletriad.storage.sanitizeKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The stored shape. Versioned, like every other document this app writes.
 *
 * The username is kept alongside the token so a returning player can be greeted, and so the sign-in
 * form can offer their name back, without a round trip to find out who they are.
 */
@Serializable
private data class SessionDocument(
    val version: Int = SessionStore.VERSION,
    val token: String = "",
    val expiresAt: Long = 0L,
    val username: String = "",
)

/**
 * The signed-in session, kept across launches.
 *
 * ### This is the credential, and it is stored in the clear
 *
 * A bearer token is exactly as good as the password for as long as it lives, and this writes it to
 * the same ordinary application storage the save files use. That is a deliberate, bounded decision
 * rather than an oversight:
 *
 * - On Android and iOS, application storage is already private to the app and unreadable by other
 *   apps on a device whose owner has not deliberately opened it up. The Keystore/Keychain would add
 *   protection against an attacker with the device unlocked in their hand, which is not the threat
 *   this account faces — and it would add two more platform implementations of something the game
 *   currently has one of.
 * - On the desktop there is no meaningful boundary to hide behind: anything that can read the
 *   token can read the save file next to it, and a token in a "protected" store on a machine the
 *   attacker already controls is theatre.
 *
 * What that buys, and what it costs, is written here so the decision can be revisited rather than
 * rediscovered. The moment an account holds anything a person would mind losing, this is the file
 * to change.
 *
 * ### Why the password is not kept here, and will not be
 *
 * "Remember my credentials" is a reasonable thing to want and this is already the answer to it: the
 * token *is* the remembered credential. It signs the player in with no typing for thirty days, and
 * it is strictly better than the password at that job in three ways — the server can revoke it, it
 * expires on its own, and losing it exposes one session on one host rather than an account and
 * whatever else that password opens.
 *
 * Storing the password would add a secret that never expires, cannot be revoked, and replays
 * everywhere the player reused it — in exchange for nothing, because the token already removes the
 * typing. What is stored instead is [lastUsername], and the password field is left to the
 * platform's own password manager, which is built for this and keeps the value out of this app
 * entirely. See `AccountScreen`.
 *
 * ### One session per server, and not one per profile
 *
 * With the account replacing the local profile there is nothing about the *player* to key by: the
 * token **is** which player this is. What there is to key by is the **server**, because a token is
 * only meaningful to the host that issued it. Keying by server is what lets a player move between
 * two of them and find their session still there on the way back, and — the part that matters more
 * — it is what makes it impossible to send one server's token to another.
 */
class SessionStore(private val store: DocumentStore) {

    /**
     * The stored session, or null if there is none or it has expired.
     *
     * Expiry is checked here rather than left to the server so a relaunch after a month goes
     * straight to the sign-in form instead of showing a dashboard that fails its first request.
     * The server checks it too, and its answer is the one that counts — this is a courtesy, not a
     * security boundary, which is why a clock skewed backwards costs a needless sign-in and
     * nothing worse.
     */
    // Three returns for three outcomes — nothing stored, stored but spent, usable. Collapsing them
    // costs the log line that says which of the two failures happened.
    @Suppress("ReturnCount")
    suspend fun load(serverId: String, now: Long): StoredSession? {
        val document = read(serverId) ?: return null
        if (document.token.isEmpty() || document.expiresAt <= now) {
            Log.i(TAG) { "the stored session has expired" }
            return null
        }
        return StoredSession(document.token, document.expiresAt, document.username)
    }

    /**
     * The name last signed in with on [serverId], **whether or not the session is still valid**.
     *
     * Deliberately not derived from [load], which is the whole point: an expired session is exactly
     * when the name is worth having. Thirty days after signing in, the token is gone and the player
     * is back at the form — and the one thing the app can still say for certain is who they were.
     * Reusing [load] here would throw that away at precisely the moment it becomes useful.
     *
     * Only ever the *name*. There is no equivalent for the password, and adding one is not a
     * shortcut left undone — see the class comment.
     */
    suspend fun lastUsername(serverId: String): String? =
        read(serverId)?.username?.takeIf { it.isNotEmpty() }

    /** The stored document, or null if there is none or it cannot be read. Never throws. */
    // The store is implemented per host, so a failed read throws whatever that platform's file API
    // throws; and an unreadable session is a session to sign in for, not a crash on launch.
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private suspend fun read(serverId: String): SessionDocument? {
        val text = try {
            store.read(serverId)
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "could not read the stored session" }
            return null
        } ?: return null

        return try {
            Format.decodeFromString<SessionDocument>(text)
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "the stored session is unreadable; dropping it" }
            null
        }
    }

    /** Replaces the session stored for [serverId]. Throws only if the host's storage does. */
    suspend fun save(serverId: String, session: StoredSession) {
        store.write(
            serverId,
            Format.encodeToString(
                SessionDocument(
                    token = session.token,
                    expiresAt = session.expiresAt,
                    username = session.username,
                ),
            ),
        )
    }

    /**
     * Forgets the session — signing out, or being told the token is no longer accepted.
     *
     * Never throws. This runs on the path where something has *already* gone wrong, and a failure
     * to delete a token that the server has stopped honouring is not worth propagating.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun clear(serverId: String) {
        try {
            store.delete(serverId)
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "could not clear the stored session" }
        }
    }

    companion object {
        private const val TAG = "Session"

        /** Its own collection, for the reason [TranscriptQueue] gives: it is not a profile. */
        const val COLLECTION = "session"

        /** Bumped if [SessionDocument]'s layout ever changes incompatibly. */
        const val VERSION = 1

        private val Format = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

/**
 * The [TranscriptQueue] key for an account on a server.
 *
 * ### Why the server is part of it
 *
 * Because a queued transcript is a claim against one server's copy of one account, and the two are
 * not interchangeable. `kuplu` on the local container and `kuplu` on a public host are different
 * players with different collections; draining one queue into the other would submit matches whose
 * decks the receiving server has never issued — and it would do it in the form that looks exactly
 * like cheating.
 *
 * ### Why the username is not used directly
 *
 * A document key must survive being a filename — [sanitizeKey] rejects path separators, and the
 * server accepts a username containing one — and the account's identity is case-insensitive on the
 * server (`username_key`), so `Kuplu` and `kuplu` are one player and must not get two queues. Hence
 * [stableKey], which lowercases, replaces everything unsafe, and appends a hash of the original so
 * that `a/b` and `a_b` do not collapse into one queue.
 */
fun accountQueueKey(serverId: String, username: String): String =
    "$serverId.${stableKey(username)}"

/**
 * A session as the client holds it: the token, when it stops working, and whose it is.
 *
 * Not `protocol.Session`, which also carries the whole [com.tripletriad.protocol.PlayerState] —
 * that is the *answer to signing in*, valid at the instant it was given, and storing it would be
 * keeping a copy of a profile the server owns. What survives a launch is the credential; the
 * profile is fetched with it.
 */
data class StoredSession(
    val token: String,
    val expiresAt: Long,
    val username: String,
) {
    /** Whether this is still worth sending. */
    fun isValidAt(now: Long): Boolean = token.isNotEmpty() && expiresAt > now

    /**
     * Deliberately hides the token.
     *
     * A credential that prints itself ends up in a log, a crash report and a bug ticket, and the
     * only reliable way to stop that is for there to be no code path that formats it. Everything
     * that needs the value reads [token] and says so.
     */
    override fun toString(): String = "StoredSession(username=$username, expiresAt=$expiresAt)"
}
