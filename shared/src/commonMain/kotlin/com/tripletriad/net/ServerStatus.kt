package com.tripletriad.net

import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.ClientPlatform
import com.tripletriad.protocol.ServerInfo
import com.tripletriad.protocol.VERSION_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException

/**
 * Which platform this build is, so it is offered the right download.
 *
 * The narrowest possible `expect`: one value, needed because [ClientRelease][
 * com.tripletriad.protocol.ClientRelease] publishes a different artifact per platform and offering
 * a player the wrong one is worse than offering them none.
 */
internal expect val clientPlatform: ClientPlatform

/**
 * What is known about a server right now.
 *
 * ### Why there are five of these and not a boolean
 *
 * Because "can I play on this server" has five answers that call for five different things from the
 * player, and a boolean would collapse the four that are actionable into the one that is not.
 * Reachable-but-not-ready means wait; too-old means update; refused means something is wrong with
 * the deployment; unreachable means check the network. A single "offline" would tell somebody whose
 * client is three majors behind to check their wifi.
 *
 * The `Unknown` / `Checking` distinction earns its place the same way [AccountSession][
 * com.tripletriad.ui.AccountSession]'s `isRestored` does: an indicator that shows "offline" for the
 * half second before the first probe returns is an indicator that lies on every launch.
 */
sealed interface ServerStatus {

    /** Not probed yet. The state every server starts in, and never returns to. */
    data object Unknown : ServerStatus

    /** A probe is in flight. */
    data object Checking : ServerStatus

    /**
     * Reachable, compatible and able to serve.
     *
     * @property latencyMillis the round trip this probe took. Not a benchmark and not presented as
     *   one — it is one sample over one request — but it is the difference between a server that is
     *   up and a server that is worth playing on, and the player choosing between two of them is
     *   exactly who should see it.
     */
    data class Online(val info: ServerInfo, val latencyMillis: Long) : ServerStatus

    /**
     * Reachable, and not able to serve: the server answered and said its database is down.
     *
     * Its own state rather than a failure, because the honest advice differs — this one is worth
     * trying again in a minute, and signing in is not going to work in the meantime.
     */
    data class Degraded(val info: ServerInfo, val latencyMillis: Long) : ServerStatus

    /**
     * Reachable, and this build is too old for it.
     *
     * The state the whole `/server` endpoint exists to make expressible. It carries the full
     * [ServerInfo] because everything the player needs next is in it: which version is required,
     * and where to get it.
     */
    data class Outdated(val info: ServerInfo) : ServerStatus

    /** Not reachable at all. [cause] is the transport's own words, for the log and the screen. */
    data class Unreachable(val cause: String) : ServerStatus

    /**
     * Reached, and it did not answer the way this build understands.
     *
     * A proxy, a captive portal, a wrong address, or a server so much newer that its body will not
     * decode. Distinguished from [Unreachable] because "there is something there, and it is not the
     * game" is a different problem from "there is nothing there".
     */
    data class Unusable(val detail: String) : ServerStatus
}

/** Whether an account can be used on this server as things stand. */
val ServerStatus.isUsable: Boolean get() = this is ServerStatus.Online

/**
 * The server's own description, when the probe got far enough to have one.
 *
 * An extension and not a member, so the three states that carry a [ServerInfo] can keep it as a
 * plain constructor property. A member would force all three to `override` it and would put a
 * nullable type on a value that, in those three, is never null.
 */
val ServerStatus.serverInfo: ServerInfo?
    get() = when (this) {
        is ServerStatus.Online -> info
        is ServerStatus.Degraded -> info
        is ServerStatus.Outdated -> info
        else -> null
    }

/** The round trip, for the two states that measured one. */
val ServerStatus.latency: Long?
    get() = when (this) {
        is ServerStatus.Online -> latencyMillis
        is ServerStatus.Degraded -> latencyMillis
        else -> null
    }

/**
 * Where to send a player whose build is too old, or null if this deployment publishes nothing.
 *
 * Keyed by [clientPlatform] and never falling back to another platform's link: an Android player
 * sent to a desktop installer is worse off than one told only that they need to update.
 */
fun ServerInfo.downloadForThisPlatform(): String? = release?.downloads?.get(clientPlatform)

/**
 * Asks a server what it is.
 *
 * Separate from [AccountClient] on purpose, and it is not a stylistic separation: every method
 * there is a request this build may not be allowed to make, and this is the one that is always
 * allowed. Folding it in would put the question "may I talk to you" behind the client that only
 * works if the answer is yes.
 */
class ServerProbe(
    private val client: HttpClient,
    private val clientVersion: AppVersion = CURRENT_VERSION,
    private val elapsed: () -> Long,
) {
    /**
     * Probes [baseUrl] once and reports what it found.
     *
     * Never throws, including for a cancelled scope's sake — that one is rethrown. A probe is
     * housekeeping: it runs on a timer behind a screen the player may leave at any moment, and it
     * must not be able to take anything down.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun probe(baseUrl: String): ServerStatus {
        val startedAt = elapsed()
        return try {
            val response = client.get("${baseUrl.trimEnd('/')}/server") {
                // Sent even though this route does not refuse anyone, so a server's logs can see
                // which builds are asking — and so this request looks like every other one.
                header(VERSION_HEADER, clientVersion.toString())
                header(HttpHeaders.Accept, "application/json")
            }
            response.readStatus(elapsed() - startedAt)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            ServerStatus.Unreachable(failure.message ?: failure.toString())
        }
    }

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private suspend fun HttpResponse.readStatus(latency: Long): ServerStatus {
        if (status.value != HTTP_OK) {
            return ServerStatus.Unusable("the server answered ${status.value}")
        }

        val info = try {
            body<ServerInfo>()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            // A 200 that is not a `ServerInfo`: a captive portal, a proxy, or an address that is
            // not this game at all. Falling back to the header keeps a *newer* server usable — it
            // is the one field this build is certain it can read.
            return headers[VERSION_HEADER]?.let(AppVersion::parse)?.let { theirs ->
                if (theirs.acceptsPeer(clientVersion)) {
                    ServerStatus.Unusable("unreadable answer: ${failure.message}")
                } else {
                    ServerStatus.Outdated(minimalInfo(theirs))
                }
            } ?: ServerStatus.Unusable("unreadable answer: ${failure.message}")
        }

        return when {
            !info.accepts(clientVersion) -> ServerStatus.Outdated(info)
            !info.ready -> ServerStatus.Degraded(info, latency)
            else -> ServerStatus.Online(info, latency)
        }
    }

    /**
     * What is known about a server too new to describe itself to this build.
     *
     * The version came from the header, which is the one thing that cannot stop being readable —
     * see [VERSION_HEADER]. The name is a placeholder because there is nothing honest to put there,
     * and inventing one would be presenting a guess as the server's own words.
     */
    private fun minimalInfo(theirs: AppVersion) = ServerInfo(
        name = "",
        version = theirs,
        minimumClient = theirs,
    )

    private companion object {
        const val HTTP_OK = 200
    }
}
