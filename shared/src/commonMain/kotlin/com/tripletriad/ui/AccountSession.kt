package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.log.Log
import com.tripletriad.model.GameSave
import com.tripletriad.net.AccountResult
import com.tripletriad.net.ServerConnection
import com.tripletriad.net.ServerEntry
import com.tripletriad.net.StoredSession
import com.tripletriad.net.accountQueueKey
import com.tripletriad.net.isUnauthenticated
import com.tripletriad.protocol.AccountError
import com.tripletriad.protocol.Credentials
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.Session
import com.tripletriad.time.Clock

/**
 * The signed-in player: the account, the profile the server holds for it, and the stats.
 *
 * ### Why this and not [ProfileSession]
 *
 * They are the same idea against two different sources of truth, and the difference is not
 * cosmetic. [ProfileSession] owns its profiles — it creates them, writes them and deletes them,
 * and what is on disk is whatever it last wrote. This one owns nothing: the profile is the
 * server's, and every field here is a *copy* of something that was true when it was fetched.
 *
 * That is why there is no `create`. An account is registered, not created locally, and the
 * character comes back with it.
 *
 * ### Failures are state, not exceptions
 *
 * A sign-in form has to be able to say "that name is taken" and "the server is unreachable" in the
 * same place, so both arrive as [failure] and the screen renders them. Nothing here throws.
 */
class AccountSession internal constructor(
    private val server: ServerConnection,
    private val clock: Clock,
) {
    /** The player the server holds, or null when nobody is signed in. */
    var player: PlayerState? by mutableStateOf(null)
        private set

    /** True while a request is in flight, so a form can disable its button. */
    var isBusy: Boolean by mutableStateOf(false)
        private set

    /** What the last request refused with, or null. Cleared when the next one starts. */
    var failure: AccountResult<*>? by mutableStateOf(null)
        private set

    /**
     * True once [restore] has run, whatever it found.
     *
     * The difference between "nobody is signed in" and "we have not looked yet", which is what
     * stops the sign-in form from flashing up in front of a player who has a perfectly good stored
     * session.
     */
    var isRestored: Boolean by mutableStateOf(false)
        private set

    /**
     * The name last signed in with on this server, or null.
     *
     * Offered back by the sign-in form, so a returning player whose token has lapsed types a
     * password and not both. Read on [restore] rather than on demand because the form is composed
     * synchronously and a field that fills in a frame later is a field the player has already
     * started typing into.
     *
     * Survives expiry, and does **not** survive [signOut] — that is a deliberate asymmetry. An
     * expired token is the app forgetting; signing out is the player asking to be forgotten, and on
     * a device that gets handed around, honouring that is the whole reason the button exists.
     */
    var lastUsername: String? by mutableStateOf(null)
        private set

    /** The profile, for the screens that only want that. */
    val save: GameSave? get() = player?.save

    /** Which server this session is against — the other half of every key this class derives. */
    val serverId: String get() = server.server.id

    /**
     * Where this account's unsubmitted matches wait, or null when nobody is signed in.
     *
     * Derived from the account name rather than from the profile, unlike the local
     * `SaveRepository.keyFor`: a server-held profile has no creation-date-and-name identity of its
     * own, and the account name is the thing that is stable and unique.
     *
     * And from the **server** as well, because the same name on two hosts is two players — see
     * [accountQueueKey]. This is why the key is read through this property rather than cached: it
     * changes under a switch, and a stale one would drain one server's matches into another.
     */
    val queueKey: String? get() = save?.username?.let { accountQueueKey(server.server.id, it) }

    /**
     * Signs in with a stored token, if there is one that has not expired.
     *
     * Called once on launch. A token the server no longer honours is **cleared** rather than kept
     * to be tried again: it will not start working, and the player is going to have to sign in
     * either way — so the alternative is showing them the form after a needless round trip on every
     * subsequent launch too.
     */
    suspend fun restore() {
        val entry = server.server
        // Before the expiry check below, and read even when there is no usable token, because the
        // expired case is the one where the form is about to be shown and the name is worth most.
        lastUsername = server.session.lastUsername(entry.id)

        val stored = server.session.load(entry.id, clock.nowMillis())
        if (stored == null) {
            isRestored = true
            return
        }

        isBusy = true
        when (val result = server.accounts.me(stored.token)) {
            is AccountResult.Ok -> {
                player = result.value
                Log.i(TAG) { "restored the session for '${stored.username}'" }
            }

            else -> {
                if (result.isUnauthenticated()) server.session.clear(entry.id)
                // Not published as a failure: nobody asked for this, and an "offline" banner in
                // front of a player who has simply not signed in yet would be a message about a
                // request they did not make.
                Log.i(TAG) { "could not restore a stored session: $result" }
            }
        }
        isBusy = false
        isRestored = true
    }

    /** Creates an account and signs into it. */
    suspend fun register(username: String, password: String) =
        authenticate { server.accounts.register(Credentials(username, password)) }

    /** Signs in to an existing account. */
    suspend fun signIn(username: String, password: String) =
        authenticate { server.accounts.signIn(Credentials(username, password)) }

    /**
     * Signs out, locally first.
     *
     * The stored token is cleared and the player is signed out of the app **before** the server is
     * told, and the server's answer is ignored. A sign-out that a dead network could refuse would
     * be a button that sometimes does nothing, and the session expiring server-side on its own is a
     * far smaller problem than that.
     */
    suspend fun signOut() {
        val entry = server.server
        val stored = server.session.load(entry.id, clock.nowMillis())
        player = null
        failure = null
        // Cleared with the token: signing out is the player asking to be forgotten, and leaving
        // their name in the form for whoever picks the device up next answers the wrong question.
        lastUsername = null
        server.session.clear(entry.id)
        stored?.let { server.accounts.signOut(it.token) }
    }

    /**
     * Moves to another server, if it is not the one already in play.
     *
     * ### Why this signs the player out
     *
     * A bearer token means nothing to a host that did not issue it, and the character it names does
     * not exist there. Carrying either across would produce a dashboard showing one server's
     * profile while every request went to another — which is worse than being signed out, because
     * it looks like it is working.
     *
     * What is **not** thrown away is the session left behind: sessions are stored per server, so
     * coming back finds the old token still there and still valid if it has not expired. That is
     * what makes switching cheap enough to do, and it is why [restore] follows.
     *
     * @return whether anything changed.
     */
    suspend fun useServer(entry: ServerEntry): Boolean {
        if (!server.directory.select(entry)) return false

        player = null
        failure = null
        isRestored = false
        // Dropped rather than left for `restore` to overwrite: it suspends, so a recomposition in
        // between would offer the previous host's name on this one's form — the same cross-server
        // bleed the token storage is keyed to prevent, in the one place a player would believe it.
        lastUsername = null
        // The new server may already know us — sessions outlive a switch — so this is a restore and
        // not a sign-out. It sets `isRestored` whatever it finds.
        restore()
        return true
    }

    /**
     * Adopts a profile the server wrote — what a credited match sends back.
     *
     * Deliberately not a merge. The server's copy already includes everything the client knew about
     * plus what the match paid, and reconciling two profiles field by field is how a duplicated
     * reward or a vanished card gets introduced.
     */
    fun adopt(credited: PlayerState) {
        player = credited
    }

    /**
     * Sends a profile the player changed outside a match, and adopts it locally either way.
     *
     * Local first, on purpose: the shop screen has already told the player they bought the card,
     * and a network round trip is not something to make them watch. A failed write is logged and
     * left — the next successful save carries the same state, because this profile is the one the
     * client keeps working from.
     */
    suspend fun persist(save: GameSave) {
        val current = player ?: return
        player = current.copy(save = save)

        val stored = server.session.load(server.server.id, clock.nowMillis())
        if (stored == null) {
            Log.w(TAG) { "not signed in; a profile change was not stored" }
            return
        }
        val result = server.accounts.saveProfile(stored.token, save)
        if (result !is AccountResult.Ok) {
            Log.w(TAG) { "a profile change was not stored: $result" }
        }
    }

    /**
     * Runs a sign-in or a registration, stores what came back, and drains what was waiting.
     *
     * The drain is the part worth pointing at. A player who finished matches offline has them
     * queued and unsubmitted; signing in is precisely the moment they become creditable, and doing
     * it here means the dashboard they land on already shows what those matches paid.
     */
    private suspend fun authenticate(request: suspend () -> AccountResult<Session>) {
        isBusy = true
        failure = null

        when (val result = request()) {
            is AccountResult.Ok -> {
                val session = result.value
                val entry = server.server
                server.session.save(
                    entry.id,
                    StoredSession(
                        token = session.token,
                        expiresAt = session.expiresAt,
                        username = session.player.save.username,
                    ),
                )
                player = session.player
                lastUsername = session.player.save.username
                isBusy = false
                // After `isBusy = false` and after the profile is showing: a queue of twenty
                // transcripts is twenty round trips, and holding the sign-in button down for them
                // would make signing in look slow in proportion to how long the player was offline.
                server.reporter.drain(accountQueueKey(entry.id, session.player.save.username))
            }

            else -> {
                failure = result
                isBusy = false
            }
        }
    }

    private companion object {
        const val TAG = "Account"
    }
}

/**
 * The message to show for a failed account request.
 *
 * A function rather than a field on [AccountResult] because the wording is the UI's business and
 * the result type lives in the network layer.
 *
 * ### The one message that is not translated
 *
 * [AccountError.MALFORMED_CREDENTIALS] shows `failure.detail`, which is a sentence the **server**
 * wrote — it is the only failure whose reason the client cannot know (which rule, and how it was
 * broken). Passing it through is honest; inventing a generic replacement would tell the player less
 * than the server already told them. It is in the server's locale rather than the player's, which
 * is a real limitation and one the protocol would have to grow a key for to fix.
 */
internal fun AccountResult<*>.message(strings: Strings): String = when (this) {
    is AccountResult.Ok -> ""
    is AccountResult.Offline -> strings[StringKeys.ERROR_OFFLINE]
    is AccountResult.UpdateRequired -> strings[StringKeys.ERROR_UPDATE]
    is AccountResult.Failed -> strings.format(StringKeys.ERROR_STATUS, status.toString())
    is AccountResult.Refused -> when (failure.error) {
        AccountError.USERNAME_TAKEN -> strings[StringKeys.ERROR_NAME_TAKEN]
        AccountError.INVALID_CREDENTIALS -> strings[StringKeys.ERROR_BAD_CREDENTIALS]
        AccountError.MALFORMED_CREDENTIALS -> failure.detail
        AccountError.UNAUTHENTICATED -> strings[StringKeys.ERROR_EXPIRED]
    }
}

/**
 * One [AccountSession] for the life of the composition.
 *
 * Keyed on the connection, so it survives every recomposition and every navigation — the same
 * reasoning as [rememberProfileSession], and for a worse failure: losing the session mid-match
 * would mean the finished match had nowhere to be credited.
 */
@Composable
internal fun rememberAccountSession(server: ServerConnection, clock: Clock): AccountSession =
    remember(server, clock) { AccountSession(server, clock) }
