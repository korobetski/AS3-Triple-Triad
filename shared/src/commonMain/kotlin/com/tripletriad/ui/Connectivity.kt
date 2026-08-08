package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tripletriad.log.Log
import com.tripletriad.net.ServerConnection
import com.tripletriad.net.ServerEntry
import com.tripletriad.net.ServerStatus
import com.tripletriad.net.isUsable
import com.tripletriad.net.serverInfo
import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.ServerInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * What is known about every configured server, and about this build's standing with them.
 *
 * ### Why one object for all of the servers and not one per server
 *
 * Because the two questions the UI asks are about the *set*: "is the one I am on usable" and "is
 * there a better one". Both need every entry probed, and probing them one screen at a time would
 * mean the menu's indicator and the server list disagreeing about the same host.
 *
 * ### Why probing is explicit and not a timer
 *
 * There is no poll loop here, deliberately. A game that pings three hosts every thirty seconds
 * forever is a game that drains a phone battery to keep a dot green on a screen nobody is looking
 * at. Probes happen when the answer is about to be used or acted on — at startup, on opening the
 * server list, on a pull to refresh, and after a switch — which is every moment the status is worth
 * anything and none of the ones it is not.
 */
class Connectivity internal constructor(
    private val server: ServerConnection,
) {
    private val states = mutableStateMapOf<String, ServerStatus>()

    /** True while any probe is in flight, so a list can show one spinner rather than n. */
    var isProbing: Boolean by mutableStateOf(false)
        private set

    /** The servers this build offers, in configured order. */
    val servers: List<ServerEntry> get() = server.directory.entries

    /** The one in play. */
    val selected: ServerEntry get() = server.directory.selected

    /** What is known about [entry]. [ServerStatus.Unknown] until it has been probed. */
    fun statusOf(entry: ServerEntry): ServerStatus = states[entry.id] ?: ServerStatus.Unknown

    /** What is known about the server in play — the one the indicator and the gate care about. */
    val status: ServerStatus get() = statusOf(selected)

    /**
     * This build's standing with the selected server.
     *
     * Null when there is nothing to say: it is fine, or nothing is known yet. Non-null is the whole
     * of the update story — either the server will not have this build, or it would rather this
     * build were newer — and it is what the screens render.
     */
    val update: UpdateAdvice? get() = adviceFor(status)

    /** Probes the selected server only. What startup and a sign-in failure want. */
    suspend fun refreshSelected() {
        probe(listOf(selected))
    }

    /** Probes every configured server, for the screen that shows them side by side. */
    suspend fun refreshAll() {
        probe(servers)
    }

    /**
     * Probes [entries] concurrently.
     *
     * Concurrently because they are independent and a serial sweep costs the sum of every timeout —
     * with three servers and one black hole among them, a list that takes thirty seconds to draw.
     */
    private suspend fun probe(entries: List<ServerEntry>) {
        if (entries.isEmpty()) return

        isProbing = true
        entries.forEach { states[it.id] = ServerStatus.Checking }
        try {
            coroutineScope {
                entries
                    .map { entry -> async { entry to server.probe.probe(entry.baseUrl) } }
                    .awaitAll()
                    .forEach { (entry, status) ->
                        states[entry.id] = status
                        Log.i(TAG) { "${entry.label}: $status" }
                    }
            }
        } finally {
            // In `finally` because a cancelled scope — the player left the screen — must not leave
            // a spinner running for the rest of the session.
            isProbing = false
        }
    }

    private fun adviceFor(status: ServerStatus): UpdateAdvice? {
        val info = status.serverInfo ?: return null
        val published = info.release?.version

        return when {
            // Blocking: this build cannot be served, and the only remedy is a new one.
            status is ServerStatus.Outdated -> UpdateAdvice(info, isRequired = true)

            // A suggestion. The deployment publishes a newer build than this one and will still
            // talk to us, so this is worth saying once and never worth standing in the way.
            published != null && published > CURRENT_VERSION -> UpdateAdvice(
                info,
                isRequired = false,
            )

            else -> null
        }
    }

    private companion object {
        const val TAG = "Connectivity"
    }
}

/**
 * What to tell the player about their build.
 *
 * ### Why required and suggested are one type with a flag
 *
 * Because they carry exactly the same information and differ only in what the screen may do about
 * it: a required update replaces the sign-in form, a suggested one is a line above it. Two types
 * would be two renderings of the same three fields, and the day a third case appears — a server
 * that is *older* than this build, which is allowed — it would be a third.
 *
 * @property isRequired true when the server will not serve this build at all. False means it will,
 *   and would rather it did not have to.
 */
data class UpdateAdvice(
    val info: ServerInfo,
    val isRequired: Boolean,
) {
    /** The version being asked for: what to update *to*, not what refused us. */
    val target: AppVersion
        get() = info.release?.version ?: info.minimumClient
}

/** Whether the selected server can be signed in to right now. */
val Connectivity.isServerUsable: Boolean get() = status.isUsable

@Composable
internal fun rememberConnectivity(server: ServerConnection): Connectivity =
    remember(server) { Connectivity(server) }
