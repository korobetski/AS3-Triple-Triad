package com.tripletriad.net

import com.tripletriad.log.Log
import com.tripletriad.protocol.MatchSubmitter
import com.tripletriad.protocol.MatchTranscript
import com.tripletriad.protocol.SubmissionResult
import com.tripletriad.storage.DocumentStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The stored shape: a versioned envelope around the list, as match history uses. */
@Serializable
private data class QueueDocument(
    val version: Int = TranscriptQueue.VERSION,
    val transcripts: List<MatchTranscript> = emptyList(),
)

/**
 * Transcripts that have been played but not yet judged, one document per profile.
 *
 * ### Why this exists at all
 *
 * Because of the one claim the whole verification design rests on: **an honest match played offline
 * still counts** ([MatchTranscript]). That is only true if a match played with no connection
 * outlives the process rather than merely the screen. A queue in memory would lose the match at
 * exactly the moment the design promises to keep it — the plane, the lift, the dead cell.
 *
 * ### Why a [DocumentStore] and not something new
 *
 * The abstraction exists, both hosts implement it, and [com.tripletriad.storage.InMemoryDocumentStore]
 * is a working double — so this costs no platform code. Its own store, in its own [COLLECTION],
 * rather than sharing the profiles' one: [DocumentStore.keys] enumerates a collection, and a
 * pending transcript sitting next to the `.sav` files would show up wherever profiles are listed.
 * The same reasoning, and the same shape, as match history.
 *
 * ### Why not inside the profile
 *
 * Lifetime. A profile is permanent, a pending transcript is transitory, and folding the second into
 * the first would make every submission rewrite the save — turning a network hiccup into a risk to
 * the player's progression. Apart, losing this document costs some unjudged matches and nothing
 * else.
 *
 * ### Known cost, and the bound
 *
 * [add] reads and rewrites the whole document, as `MatchHistoryRepository.append` does, and for the
 * same reason: it is one write per finished match. [limit] caps the queue, **dropping the oldest**
 * — the opposite of what a queue usually wants, but correct here, because the recent matches are
 * the ones whose rewards the player is still expecting. A queue at its limit means the server has
 * been unreachable for a very long time, which is not a storage problem.
 *
 * ### Not thread-safe
 *
 * Read-modify-write with no lock: two concurrent [add] calls can lose one. Deliberate — every
 * caller today is a UI effect on the main dispatcher, and a mutex for a race that cannot happen
 * would be a guarantee nobody could rely on. Written down so that whoever drains this in the
 * background reads it first.
 *
 * @param limit transcripts kept per profile. A parameter so a test can reach the drop path without
 *   queueing [DEFAULT_LIMIT] matches.
 */
class TranscriptQueue(
    private val store: DocumentStore,
    private val limit: Int = DEFAULT_LIMIT,
) {
    init {
        require(limit > 0) { "limit must be positive, was $limit" }
    }

    /**
     * Everything waiting for [profileKey], oldest first — the order they must be submitted in.
     *
     * A queue that cannot be read or parsed reads as **empty** rather than throwing. It is a cache
     * of things to retry: the matches are lost either way, and the difference is whether the player
     * can still start the game.
     */
    // TooGenericExceptionCaught: as in MatchHistoryRepository.all, the throwable depends on the
    // host's file API and there is no common supertype to name. ReturnCount: one exit per outcome.
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    suspend fun pending(profileKey: String): List<MatchTranscript> {
        val text = try {
            store.read(profileKey)
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "could not read the pending queue for '$profileKey'" }
            return emptyList()
        } ?: return emptyList()

        return try {
            Format.decodeFromString<QueueDocument>(text).transcripts
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "the pending queue for '$profileKey' is unreadable; dropping it" }
            emptyList()
        }
    }

    /**
     * Adds [transcript] to the back of [profileKey]'s queue.
     *
     * @return how many were dropped to stay within [limit], so a caller can say so rather than
     *   discover it.
     */
    suspend fun add(profileKey: String, transcript: MatchTranscript): Int {
        val combined = pending(profileKey) + transcript
        val kept = combined.takeLast(limit)
        val dropped = combined.size - kept.size
        if (dropped > 0) {
            Log.i(TAG) { "the pending queue for '$profileKey' is full; dropped $dropped oldest" }
        }
        write(profileKey, kept)
        return dropped
    }

    /** Forgets everything queued for [profileKey]. Called when its profile is deleted. */
    suspend fun clear(profileKey: String) = store.delete(profileKey)

    /**
     * Submits everything queued, keeping whatever could not be delivered.
     *
     * ### What is kept and what is dropped
     *
     * Two results are retried, and reaching either **stops the drain**:
     *
     * - [SubmissionResult.Offline], because if the server is unreachable it is unreachable for the
     *   rest too, and carrying on would be nine more timeouts on a launch path.
     * - [SubmissionResult.Unauthenticated], because the session is what is wrong rather than the
     *   match. Every remaining transcript would be refused for the same reason, and all of them
     *   become creditable again the moment the player signs in.
     *
     * Everything else is consumed, which is the part worth stating plainly:
     *
     * - a verdict — accepted *or* rejected — means the server has answered. Asking again gets the
     *   same answer forever, and a client that keeps resubmitting a rejection is the bug this
     *   distinction exists to prevent.
     * - [SubmissionResult.UpdateRequired] means this build's replay may not agree with the
     *   server's, so the same bytes cannot start working. The player has to update; the transcript
     *   would not survive that anyway.
     * - [SubmissionResult.Failed] is dropped too. A deliberate simplification: a transcript the
     *   server cannot read will not become readable, and a queue that retries forever is worse than
     *   a lost match. If failures ever need distinguishing — a 500 is transient where a 400 is not
     *   — that judgement belongs here, and this is where to put it.
     *
     * The store is written **once**, at the end, rather than per transcript: a drain interrupted
     * halfway then either happened or did not, instead of leaving a queue that has been partly
     * consumed and partly not.
     *
     * @return what the server said about each transcript it judged, oldest first. Empty means
     *   either nothing was queued or nothing could be delivered — [pending] distinguishes them.
     */
    suspend fun drain(profileKey: String, submitter: MatchSubmitter): List<SubmissionResult> {
        val queued = pending(profileKey)
        if (queued.isEmpty()) return emptyList()

        val judged = mutableListOf<SubmissionResult>()
        for (transcript in queued) {
            val result = submitter.submit(transcript)
            if (result.holdsTheQueue()) {
                val held = queued.size - judged.size
                Log.i(TAG) { "drain stopped on $result: $held still pending" }
                break
            }
            judged += result
        }

        val remaining = queued.drop(judged.size)
        if (remaining.isEmpty()) clear(profileKey) else write(profileKey, remaining)
        return judged
    }

    /**
     * Whether this result means "keep this transcript and stop", rather than "this one is done".
     *
     * A `when` over the sealed type rather than an `is Offline || is Unauthenticated`, so that a
     * result added later fails to compile here instead of being silently consumed — which for this
     * queue means silently discarding a match the player played.
     */
    private fun SubmissionResult.holdsTheQueue(): Boolean = when (this) {
        is SubmissionResult.Offline, SubmissionResult.Unauthenticated -> true
        is SubmissionResult.Judged,
        is SubmissionResult.UpdateRequired,
        is SubmissionResult.Failed,
        -> false
    }

    private suspend fun write(profileKey: String, transcripts: List<MatchTranscript>) {
        store.write(profileKey, Format.encodeToString(QueueDocument(transcripts = transcripts)))
    }

    companion object {
        private const val TAG = "Pending"

        /** The subdirectory a host store uses, as `SaveRepository.COLLECTION` is for saves. */
        const val COLLECTION = "pending"

        /**
         * Transcripts kept per profile.
         *
         * Small on purpose. This is not an archive: it is what has not been judged yet, and a
         * player who has queued two hundred matches has a broken connection, not a full disk.
         */
        const val DEFAULT_LIMIT = 200

        /** Bumped if [QueueDocument]'s layout ever changes incompatibly. */
        const val VERSION = 1

        /**
         * `ignoreUnknownKeys` so a queue written by a newer build — one that added a transcript
         * field — degrades to a readable transcript rather than an unreadable document. Unlike the
         * wire format, this one is read back by a *different version of itself* after an update,
         * which is precisely when tolerance is worth having.
         */
        private val Format = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
