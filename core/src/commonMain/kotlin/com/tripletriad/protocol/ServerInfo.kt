package com.tripletriad.protocol

import kotlinx.serialization.Serializable

/**
 * What a server says about itself, before anything is asked of it.
 *
 * ### Why this exists at all
 *
 * Everything else in this protocol is gated: a client whose major disagrees with the server's is
 * refused with a 426 before its request body is read, which is exactly right for a request that
 * would otherwise be misjudged. But it leaves the client with a status code and no way to tell the
 * player anything useful — "the server refused you" is not a sentence anyone can act on, and it is
 * indistinguishable from the server being down to a player who only sees a spinner.
 *
 * So there is one endpoint that answers *everyone*, including the builds this server will not
 * otherwise talk to. That is the whole point of it and the one property that must not be lost:
 * **`GET /server` is never behind the version gate.** An endpoint that refuses incompatible clients
 * cannot be the endpoint that tells them they are incompatible.
 *
 * ### Why it also carries readiness
 *
 * `/health/live` and `/health/ready` exist and are not this. They are an orchestrator's questions —
 * "kill this process", "route traffic here" — answered in an orchestrator's shape and on an
 * orchestrator's schedule. This is the *player's* question: can I sign in right now, and if not,
 * why. Answering both in one round trip is deliberate, because the alternative is two requests on a
 * mobile connection to draw one status dot.
 *
 * @property name what this deployment calls itself, for a client that lists more than one. Free
 *   text from configuration: it is a label, never an identifier.
 * @property version the `:core` this server links — see [AppVersion] for why that is the number
 *   that matters rather than a build number.
 * @property minimumClient the oldest client it will accept. Currently always its own [version],
 *   since [AppVersion.acceptsPeer] compares majors, but sent separately so a deployment can one day
 *   accept older clients than it is without every client having to infer the policy.
 * @property ready whether it can serve a request this instant. False means reachable but not usable
 *   — the database is down — which is a different thing to tell a player than "unreachable", and
 *   they are different things to do about it.
 * @property release the client build this deployment publishes, when it publishes one.
 */
@Serializable
data class ServerInfo(
    val name: String,
    val version: AppVersion,
    val minimumClient: AppVersion,
    val ready: Boolean = true,
    val release: ClientRelease? = null,
) {
    /**
     * Whether [client] may talk to this server.
     *
     * The same asymmetry as [AppVersion.acceptsPeer], stated once here so a client can reach the
     * answer *before* being refused rather than by being refused. A client newer than the server
     * passes: during a rollout one side always ships first, and the newer side is the one equipped
     * to be careful.
     */
    fun accepts(client: AppVersion): Boolean = minimumClient.acceptsPeer(client)
}

/**
 * The client build a deployment says it wants people on.
 *
 * ### What this is not
 *
 * Not an updater. Nothing in this protocol downloads or runs anything, and that is a decision
 * rather than an omission — see `docs/migration/09-PHASE-5-NETWORK.md`. Shipping code that fetches
 * a binary and executes it means owning signed artifacts, a verified release channel and a
 * per-platform installer handoff, and it is impossible on two of the three targets anyway: an app
 * store owns updates on Android and iOS, and an app that worked around that would be removed from
 * it. So this carries **where to get it**, and the player decides.
 *
 * @property version the newest client this deployment knows of. A client on this version or newer
 *   has nothing to do; one behind it can be told so without being blocked, which is the difference
 *   between a suggestion and [ServerInfo.accepts] returning false.
 * @property downloads where to get it, keyed by [ClientPlatform]. A map rather than one URL because
 *   the answer genuinely differs per platform — a store listing on the phones, a file on the
 *   desktop — and a client must never be offered a download meant for a different one. Empty is
 *   legitimate: a deployment that announces a version without hosting it is telling a player their
 *   build is old, which is still worth knowing.
 * @property notes one line the deployment may want to show. Displayed as given and never parsed.
 */
@Serializable
data class ClientRelease(
    val version: AppVersion,
    val downloads: Map<ClientPlatform, String> = emptyMap(),
    val notes: String? = null,
)

/**
 * Which build of the client is asking.
 *
 * In `:core` and not in `:shared` because the server keys [ClientRelease.downloads] by it, so the
 * two ends have to spell the platforms the same way — the same argument that puts every other
 * shared shape here.
 */
@Serializable
enum class ClientPlatform {
    ANDROID,
    DESKTOP,
    IOS,
}
