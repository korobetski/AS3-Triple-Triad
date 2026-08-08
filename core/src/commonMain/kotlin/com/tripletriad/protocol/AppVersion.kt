package com.tripletriad.protocol

import kotlinx.serialization.Serializable

/**
 * The version the client and the server both claim to be.
 *
 * ### Why this lives in `:core` and not in either side's build file
 *
 * Because the two must not be able to disagree by accident. `:core` is the artifact they share —
 * the rules engine, the card tables' parsers, the transcript format — so a build of the client and
 * a build of the server that link the *same* `:core` necessarily agree about this number. When they
 * link different ones, they differ, and that is exactly the case the gate below exists to catch.
 *
 * ### What a major bump means, precisely
 *
 * **The replay can reach a different answer.** That is a wider promise than "the wire format
 * changed": it covers the card and opponent tables, and it covers `RulesEngine`, `MatchState`,
 * `Roulette` and `MatchAi`. It is the same event that bumps [TRANSCRIPT_VERSION] and the same event
 * that breaks the goldens in `ReplayDeterminismTest` — three expressions of one decision, and they
 * move together.
 *
 * Without the gate that follows from it, the failure mode is nasty and misdiagnosed: a server
 * dealing from an older card table rejects every transcript from an updated client, and the
 * rejection is indistinguishable from cheating. See
 * `docs/migration/09-PHASE-5-NETWORK.md` § One version, shared.
 *
 * A **minor** bump therefore carries a real obligation: it must mean the replay is unchanged. If
 * that is ever untrue the distinction is decoration, and the goldens are what enforce it.
 */
@Serializable
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<AppVersion> {

    init {
        require(major >= 0 && minor >= 0 && patch >= 0) {
            "a version component may not be negative, was $major.$minor.$patch"
        }
    }

    /**
     * Whether a peer running [other] can be talked to.
     *
     * Deliberately **not** symmetric, and deliberately not "the majors are equal". A client older
     * than the server must update; a client *newer* than the server is the ordinary state of the
     * world during a rollout — the client shipped through a store review while the server had not
     * been deployed yet — and refusing it would take the game away from the people who updated
     * fastest. The newer side is the one that knows how to be careful.
     *
     * Minor and patch are ignored, which is the whole point of separating them: they promise the
     * replay is unchanged, so there is nothing to refuse.
     */
    fun acceptsPeer(other: AppVersion): Boolean = other.major >= major

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    /** `"1.4.2"`. The form that goes on the wire and into a log. */
    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /**
         * Reads `"1.4.2"`, or null if it is not that.
         *
         * Null rather than an exception because the input arrives from the network: a missing or
         * mangled version header is a request to refuse, not a bug to crash on.
         */
        fun parse(text: String): AppVersion? {
            // `mapNotNull` drops anything that is not a non-negative integer, so a bad component
            // shortens the list and the size check below catches it — as does an extra one. That
            // is why the count is verified *after* the mapping rather than before.
            val numbers = text.trim()
                .split('.')
                .mapNotNull { part -> part.toIntOrNull()?.takeIf { it >= 0 } }

            return numbers
                .takeIf { it.size == COMPONENT_COUNT }
                ?.let { AppVersion(it[0], it[1], it[2]) }
        }

        private const val COMPONENT_COUNT = 3
    }
}

/**
 * What this build is.
 *
 * Kept beside the format version it moves with rather than read from a build file, so that a
 * `:core` artifact answers the question by itself — the server has no access to the client's Gradle
 * files, and a number it had to be told would be a number that could be wrong.
 */
val CURRENT_VERSION: AppVersion = AppVersion(0, 1, 0)

/**
 * The header both sides put the version in.
 *
 * A header and not a field in [MatchTranscript], because refusing a stale peer is a **protocol**
 * answer and must be possible before the body is parsed — a body this build may not be able to read
 * correctly is precisely what a major mismatch means. Putting it in the transcript would make the
 * server parse the thing it has already decided to refuse, and would make a rejected client look
 * like a rejected *claim*, which is the confusion the whole design is trying to avoid.
 */
const val VERSION_HEADER: String = "X-TTO-Version"
