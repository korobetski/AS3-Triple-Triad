package com.tripletriad.net

import com.tripletriad.data.SaveRepository
import com.tripletriad.log.Log
import com.tripletriad.model.GameSave
import com.tripletriad.protocol.MatchSubmitter
import com.tripletriad.protocol.MatchTranscript
import com.tripletriad.protocol.MatchVerdict
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.SubmissionResult

/**
 * What the UI calls when a match ends, and when a profile is opened.
 *
 * ### Why an interface with a do-nothing default
 *
 * Because a build with no server is a supported build, and it must not be a build that quietly
 * accumulates transcripts nobody will ever collect. [None] makes "there is no server" an explicit
 * choice at the host, rather than a `null` every call site has to remember to check — and it keeps
 * every preview, test and screenshot free of storage writes they never asked for.
 *
 * ### Why it takes a profile key and not a [GameSave]
 *
 * Because [forget] has to work on the deletion path, where the profile is named by key and the save
 * may no longer be readable at all — a corrupt profile can be deleted, and its queue has to go with
 * it. A save-shaped interface would have made the one case that most needs cleaning up the one case
 * it could not express. Callers derive the key with [SaveRepository.keyFor].
 */
interface MatchReporter {

    /**
     * Records a finished match for later judgement.
     *
     * Deliberately does **not** submit. A player who has just won is looking at a result screen,
     * and a network round trip on that path is a spinner between them and it. The transcript is
     * durable the moment this returns, and [drain] delivers it.
     */
    suspend fun report(profileKey: String, transcript: MatchTranscript)

    /** Submits whatever is waiting for [profileKey]. Safe to call when nothing is. */
    suspend fun drain(profileKey: String)

    /** Forgets what is queued for [profileKey]. Called when the profile itself is deleted. */
    suspend fun forget(profileKey: String)

    /**
     * The reporter for a build with no server: matches are played and nothing is recorded.
     *
     * The default everywhere in `:shared`, so a preview or a UI test exercises the real screens
     * without a queue, a store or a client.
     */
    object None : MatchReporter {
        override suspend fun report(profileKey: String, transcript: MatchTranscript) = Unit
        override suspend fun drain(profileKey: String) = Unit
        override suspend fun forget(profileKey: String) = Unit
    }
}

/**
 * The real one: a durable queue in front of a submitter.
 *
 * The two are separate types because they fail differently and are testable apart —
 * [TranscriptQueue] is about surviving a process, [MatchSubmitter] about surviving a network — and
 * this is the small amount of glue that says when one calls the other.
 *
 * Nothing here throws. A match is already over by the time any of this runs, and neither a full
 * disk nor a dead server is worth taking the result screen down for; the failure is logged and the
 * transcript is either kept or lost, which the player finds out about through their progression
 * rather than through a stack trace.
 */
/**
 * @property onCredited called with the profile the server wrote, whenever a drain credits at least
 *   one match. **The newest one only**, and that is not a shortcut: each receipt's profile already
 *   includes every match credited before it, so applying them in turn would show the player their
 *   progression flickering through states the server considers superseded.
 */
class QueuedMatchReporter(
    private val queue: TranscriptQueue,
    private val submitter: MatchSubmitter,
    private val onCredited: suspend (PlayerState) -> Unit = {},
) : MatchReporter {

    // TooGenericExceptionCaught: the store is implemented per host, so a failed write throws
    // whatever that platform's file API throws — the same reasoning as `ProfileSession.guard`.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun report(profileKey: String, transcript: MatchTranscript) {
        try {
            queue.add(profileKey, transcript)
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "could not queue a finished match for '$profileKey'" }
        }
    }

    /**
     * Drains, and hands the credited profile back.
     *
     * This is the function that used to log the verdicts and throw them away, because the server
     * had nothing to credit them to. It now has: every accepted submission comes back with the
     * profile the server wrote, and [onCredited] is how that reaches the screen the player is
     * looking at. A rejected or duplicate receipt carries no new profile and changes nothing here.
     */
    @Suppress("TooGenericExceptionCaught")
    override suspend fun drain(profileKey: String) {
        val results = try {
            queue.drain(profileKey, submitter)
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "could not drain the pending queue for '$profileKey'" }
            return
        }
        results.forEach { it.log(profileKey) }

        // `lastOrNull` over the credited ones: see `onCredited`. A duplicate is skipped even though
        // it does carry a profile — it is the state the server already had, and adopting it would
        // undo a match credited after it in the same drain.
        results.asSequence()
            .filterIsInstance<SubmissionResult.Judged>()
            .mapNotNull { if (it.receipt.duplicate) null else it.receipt.player }
            .lastOrNull()
            ?.let { credited ->
                try {
                    onCredited(credited)
                } catch (failure: Exception) {
                    Log.w(TAG, failure) { "could not apply the credited profile for '$profileKey'" }
                }
            }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun forget(profileKey: String) {
        try {
            queue.clear(profileKey)
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "could not clear the pending queue for '$profileKey'" }
        }
    }

    private fun SubmissionResult.log(key: String) = when (this) {
        is SubmissionResult.Judged -> when (val v = verdict) {
            is MatchVerdict.Accepted -> if (receipt.duplicate) {
                Log.i(TAG) { "'$key': ${v.blue}-${v.red}, already credited" }
            } else {
                Log.i(TAG) { "'$key': accepted ${v.blue}-${v.red}, ${receipt.reward?.mgp} MGP" }
            }
            // Worth a warning rather than an info: an honest client should not be producing these,
            // so one in a log is either a bug in the transcript or somebody trying it on.
            is MatchVerdict.Rejected ->
                Log.w(TAG) { "'$key': rejected as ${v.reason} — ${v.detail}" }
        }
        is SubmissionResult.UpdateRequired ->
            Log.w(TAG) { "'$key': the server is on $serverVersion and wants a newer client" }
        is SubmissionResult.Failed ->
            Log.w(TAG) { "'$key': the server answered $status — $detail" }
        // Never reached: `drain` stops at the first of these rather than returning it.
        is SubmissionResult.Offline, SubmissionResult.Unauthenticated -> Unit
    }

    private companion object {
        const val TAG = "Pending"
    }
}
