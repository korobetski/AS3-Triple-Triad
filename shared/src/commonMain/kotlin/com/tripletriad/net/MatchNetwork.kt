package com.tripletriad.net

import com.tripletriad.protocol.PlayerState
import com.tripletriad.storage.DocumentStore
import com.tripletriad.time.Clock
import io.ktor.client.engine.HttpClientEngineFactory

/**
 * The transport this platform can actually use.
 *
 * Ktor's *API* is multiplatform; its transport is not, so this is the one thing the shared network
 * layer cannot decide for itself — OkHttp on Android, CIO on the desktop JVM, Darwin on iOS.
 *
 * An `expect` here rather than a parameter the hosts pass, because the alternative was every app
 * module depending on Ktor directly and naming an engine, which would have put the transport back
 * in the place `:shared` keeps it out of. A host says *where* the server is; it should not have to
 * know *how* to reach it.
 */
internal expect fun defaultHttpEngineFactory(): HttpClientEngineFactory<*>

/**
 * Everything the app needs to talk to a server, built once and shared.
 *
 * ### One client, one directory, one session store, one queue
 *
 * They are assembled together because they are only correct together. The submitter has to send the
 * token the session store currently holds **for the server the directory currently points at**, and
 * the queue has to be drained again whenever either changes. Handing the hosts four constructors
 * and a note about how to wire them would be handing them four ways to get it wrong — and the way
 * that matters is the one where a request carries the wrong server's token.
 *
 * The [io.ktor.client.HttpClient] is created once here and shared. That matters more than it looks:
 * a client owns a connection pool and a thread pool, and building one per request is the classic
 * way to exhaust both. It is shared across *servers* too, which is fine — a pool is keyed by host.
 *
 * @property directory the servers this build knows about, and which one is in play. The single
 *   source for the address; nothing else holds one.
 * @property accounts registering, signing in, and fetching the server-held profile.
 * @property session where bearer tokens survive a relaunch, one per server.
 * @property probe what asks a server whether it is there, and whether this build may use it.
 * @property reporter the durable queue in front of match submission.
 */
class ServerConnection internal constructor(
    val directory: ServerDirectory,
    val accounts: AccountClient,
    val session: SessionStore,
    val probe: ServerProbe,
    val reporter: MatchReporter,
) {
    /** The server in play. Shorthand for the thing almost every caller wants from [directory]. */
    val server: ServerEntry get() = directory.selected
}

/**
 * The three places on disk a connection writes, kept together.
 *
 * Each is its own collection and none of them is the saves directory — `DocumentStore.keys`
 * enumerates a collection, and any of these among the profiles would appear wherever profiles are
 * listed. Grouped into one type rather than passed as three parameters because they are one
 * decision ("where does this host keep its network state"), and because a host is far more likely
 * to swap two adjacent `DocumentStore` arguments than two fields it had to name.
 *
 * @property queue where unjudged transcripts wait, in [TranscriptQueue.COLLECTION].
 * @property session where tokens wait, in [SessionStore.COLLECTION].
 * @property directory where the chosen server is remembered, in [ServerDirectory.COLLECTION].
 */
data class ServerStores(
    val queue: DocumentStore,
    val session: DocumentStore,
    val directory: DocumentStore,
)

/**
 * Assembles a [ServerConnection] over [servers].
 *
 * The one entry point the app modules call, so adding an engine, a JSON configuration or a retry
 * policy stays inside this package. `:androidApp` and `:desktopApp` supply the two things only they
 * can know: somewhere to write and a list of addresses.
 *
 * @param stores where this host keeps the queue, the session and the chosen server.
 * @param servers every server this build offers, in the order they should be listed. The first is
 *   the default until the player has chosen; an empty list is not a configuration but the absence
 *   of one, and a host with nothing to put here passes no connection at all.
 * @param clock the wall clock, for token expiry, and the monotonic-enough reading a probe measures
 *   its round trip with.
 * @param onCredited what to do with a profile the server credited during a drain.
 */
fun serverConnection(
    stores: ServerStores,
    servers: List<ServerEntry>,
    clock: Clock,
    onCredited: suspend (PlayerState) -> Unit = {},
): ServerConnection {
    val http = matchSubmitterHttpClient(defaultHttpEngineFactory())
    val directory = ServerDirectory(stores.directory, servers)
    val sessions = SessionStore(stores.session)

    // Read per request rather than captured. Both of these change while the app is running — the
    // player switches servers, the player signs in — and a value read once would keep sending the
    // address or the token that was current when this was built.
    val address: suspend () -> String = { directory.selected.baseUrl }
    val token: suspend () -> String? = {
        sessions.load(directory.selected.id, clock.nowMillis())?.token
    }

    return ServerConnection(
        directory = directory,
        accounts = AccountClient(http, address),
        session = sessions,
        probe = ServerProbe(http, elapsed = clock::nowMillis),
        reporter = QueuedMatchReporter(
            queue = TranscriptQueue(stores.queue),
            submitter = KtorMatchSubmitter(client = http, baseUrl = address, token = token),
            onCredited = onCredited,
        ),
    )
}
