package com.tripletriad.net

import com.tripletriad.storage.InMemoryDocumentStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The stored credential.
 *
 * Two things are being defended here and they pull in opposite directions: a returning player must
 * not be asked to sign in again, and a session that has stopped being usable must not survive to
 * make the first request of the next launch fail.
 */
class SessionStoreTest {

    // ---- Surviving a launch -----------------------------------------------

    @Test
    fun aStoredSessionComesBackAfterARelaunch() = runTest {
        val documents = InMemoryDocumentStore()
        SessionStore(documents).save(SERVER, session(expiresAt = LATER))

        // A fresh instance over the same storage: the process the player was in has ended.
        val restored = SessionStore(documents).load(SERVER, NOW)

        assertEquals(session(expiresAt = LATER), restored)
    }

    @Test
    fun nothingStoredIsNoSession() = runTest {
        assertNull(SessionStore(InMemoryDocumentStore()).load(SERVER, NOW))
    }

    @Test
    fun clearForgetsIt() = runTest {
        val store = SessionStore(InMemoryDocumentStore())
        store.save(SERVER, session(expiresAt = LATER))

        store.clear(SERVER)

        assertNull(store.load(SERVER, NOW))
    }

    // ---- Refusing to hand back something unusable -------------------------

    /**
     * An expired token is the same as none.
     *
     * Checked here rather than left to the server so a relaunch after a month goes straight to the
     * sign-in form, instead of showing a dashboard whose first request comes back 401.
     */
    @Test
    fun anExpiredSessionIsNotReturned() = runTest {
        val store = SessionStore(InMemoryDocumentStore())
        store.save(SERVER, session(expiresAt = NOW - 1))

        assertNull(store.load(SERVER, NOW))
    }

    /** The boundary is exclusive: a token expiring this millisecond is already gone. */
    @Test
    fun aSessionExpiringExactlyNowIsNotReturned() = runTest {
        val store = SessionStore(InMemoryDocumentStore())
        store.save(SERVER, session(expiresAt = NOW))

        assertNull(store.load(SERVER, NOW))
    }

    @Test
    fun anEmptyTokenIsNotASession() = runTest {
        val store = SessionStore(InMemoryDocumentStore())
        store.save(SERVER, session(expiresAt = LATER).copy(token = ""))

        assertNull(store.load(SERVER, NOW))
    }

    // ---- Never breaking the launch ----------------------------------------

    /**
     * A corrupt session file costs a sign-in, not a crash.
     *
     * This is read before anything is rendered past the splash, so throwing here would be a launch
     * that fails for a player whose only problem is that they have to type their password again.
     */
    @Test
    fun anUnreadableDocumentDegradesToNoSession() = runTest {
        val store = InMemoryDocumentStore(mapOf(SERVER to "not json at all"))

        assertNull(SessionStore(store).load(SERVER, NOW))
    }

    @Test
    fun anUnreadableStoreDegradesToNoSession() = runTest {
        val store = InMemoryDocumentStore(failure = IllegalStateException("permission denied"))

        assertNull(SessionStore(store).load(SERVER, NOW))
    }

    /** And clearing one that cannot be written is not worth propagating either. */
    @Test
    fun clearingAFailingStoreDoesNotThrow() = runTest {
        SessionStore(
            InMemoryDocumentStore(failure = IllegalStateException("read-only")),
        ).clear(SERVER)
    }

    // ---- The name outlives the token ---------------------------------------

    /**
     * The whole reason this is not `load()?.username`.
     *
     * A token lasts thirty days; the account name lasts as long as the account. When the first has
     * lapsed the player is looking at a sign-in form, and that is precisely the moment the second
     * saves them half the typing. Deriving this from [SessionStore.load] would throw the name away
     * on the only occasion it is worth anything.
     */
    @Test
    fun theNameSurvivesTheTokenItWasStoredWith() = runTest {
        val store = InMemoryDocumentStore()
        SessionStore(store).save(SERVER, session(expiresAt = NOW - 1))

        assertNull(SessionStore(store).load(SERVER, NOW), "the token has expired")
        assertEquals("kuplu", SessionStore(store).lastUsername(SERVER))
    }

    @Test
    fun thereIsNoNameWhereThereIsNoSession() = runTest {
        assertNull(SessionStore(InMemoryDocumentStore()).lastUsername(SERVER))
    }

    /** Signing out takes the name with it — the document is gone, not just the token. */
    @Test
    fun clearingForgetsTheNameAsWell() = runTest {
        val store = InMemoryDocumentStore()
        SessionStore(store).save(SERVER, session(expiresAt = LATER))

        SessionStore(store).clear(SERVER)

        assertNull(SessionStore(store).lastUsername(SERVER))
    }

    /** One server's name is not another's, for the reason the tokens are kept apart. */
    @Test
    fun eachServerRemembersItsOwnName() = runTest {
        val store = InMemoryDocumentStore()
        SessionStore(store).save(SERVER, session(expiresAt = LATER))

        assertNull(SessionStore(store).lastUsername(OTHER_SERVER))
    }

    /** An unreadable document has no name in it either, and still must not throw. */
    @Test
    fun anUnreadableDocumentHasNoNameToOffer() = runTest {
        val store = InMemoryDocumentStore(mapOf(SERVER to "not json at all"))

        assertNull(SessionStore(store).lastUsername(SERVER))
    }

    // ---- The token is not printable ---------------------------------------

    /**
     * The one guarantee that has to hold everywhere: a credential that formats itself ends up in a
     * log, a crash report and a bug ticket. The only reliable defence is that no code path produces
     * the string, and `toString` is the path that gets taken accidentally.
     */
    @Test
    fun theTokenIsNotInTheStringForm() = runTest {
        val printed = session(expiresAt = LATER).toString()

        assertFalse(printed.contains(TOKEN), "the token was printed")
        assertTrue(printed.contains("kuplu"), "and the useful part was lost with it")
    }

    @Test
    fun validityIsTheSameQuestionLoadAsks() {
        val stored = session(expiresAt = LATER)

        assertTrue(stored.isValidAt(NOW))
        assertFalse(stored.isValidAt(LATER))
        assertFalse(stored.copy(token = "").isValidAt(NOW))
    }

    // ---- The queue key ----------------------------------------------------

    /**
     * The server treats `Kuplu` and `kuplu` as one account, so they must not get two queues — one
     * of which would then be drained under the other's name.
     */
    @Test
    fun theQueueKeyIgnoresCase() {
        assertEquals(accountQueueKey(SERVER, "Kuplu"), accountQueueKey(SERVER, "kuplu"))
    }

    /**
     * The key becomes a filename on both hosts, and the server accepts names this one does not.
     *
     * The hash is what stops the sanitizing from merging them: without it `a/b` and `a_b` would
     * share a queue, and one player's matches would be submitted with the other's token.
     */
    @Test
    fun namesThatSanitizeToTheSameStringStillGetTheirOwnQueue() {
        assertNotEquals(accountQueueKey(SERVER, "a/b"), accountQueueKey(SERVER, "a_b"))
    }

    @Test
    fun theQueueKeyIsSafeToUseAsADocumentKey() {
        val key = accountQueueKey(SERVER, "../../etc/passwd")

        // The dot is the one other character a key may contain: it is the separator this function
        // puts there, between a server id that is already safe and a sanitized username.
        assertTrue(
            key.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' },
            "was $key",
        )
    }

    /** It has to be the same string on the next launch, or the queued matches are orphaned. */
    @Test
    fun theQueueKeyIsStable() {
        assertEquals(accountQueueKey(SERVER, "kuplu"), accountQueueKey(SERVER, "kuplu"))
    }

    /**
     * The same account name on two servers is two players.
     *
     * The failure this prevents is the worst one in the queue: draining server A's matches with
     * server B's token would submit transcripts whose decks B never issued, which is
     * indistinguishable from a forged submission.
     */
    @Test
    fun oneNameOnTwoServersIsTwoQueues() {
        assertNotEquals(accountQueueKey(SERVER, "kuplu"), accountQueueKey(OTHER_SERVER, "kuplu"))
    }

    // ---- One session per server -------------------------------------------

    /**
     * Signing in on one server does not sign you in on another.
     *
     * A token means nothing to a host that did not issue it, so the store must not be able to hand
     * one server's credential to another — see [accountQueueKey] for the same reasoning applied to
     * the queue.
     */
    @Test
    fun aSessionOnOneServerIsNotASessionOnAnother() = runTest {
        val store = SessionStore(InMemoryDocumentStore())
        store.save(SERVER, session(expiresAt = LATER))

        assertNull(store.load(OTHER_SERVER, NOW))
    }

    /** And switching away and back finds the token that was left behind. */
    @Test
    fun switchingAwayLeavesTheSessionWhereItWas() = runTest {
        val store = SessionStore(InMemoryDocumentStore())
        store.save(SERVER, session(expiresAt = LATER))
        store.save(OTHER_SERVER, session(expiresAt = LATER).copy(username = "elsewhere"))

        store.clear(OTHER_SERVER)

        assertEquals(session(expiresAt = LATER), store.load(SERVER, NOW))
        assertNull(store.load(OTHER_SERVER, NOW))
    }

    // ---- Fixtures ---------------------------------------------------------

    private fun session(expiresAt: Long) =
        StoredSession(token = TOKEN, expiresAt = expiresAt, username = "kuplu")

    private companion object {
        const val NOW = 1_770_000_000_000L
        const val LATER = NOW + 86_400_000L

        /** Not a credential — a value that must be absent from a string. */
        const val TOKEN = "opaque-session-value"

        /** Two server ids, of the shape `ServerEntry.of` derives. */
        const val SERVER = "127_0_0_1_8080-abc"
        const val OTHER_SERVER = "example_org-def"
    }
}
