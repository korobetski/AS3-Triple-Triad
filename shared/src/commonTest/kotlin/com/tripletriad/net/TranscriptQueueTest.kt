package com.tripletriad.net

import com.tripletriad.model.CardCollection
import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.MatchReceipt
import com.tripletriad.protocol.MatchSubmitter
import com.tripletriad.protocol.MatchTranscript
import com.tripletriad.protocol.MatchVerdict
import com.tripletriad.protocol.RejectionReason
import com.tripletriad.protocol.SubmissionResult
import com.tripletriad.protocol.TranscriptMove
import com.tripletriad.storage.InMemoryDocumentStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The offline half of match verification.
 *
 * What these are really testing is the promise in [MatchTranscript]'s own documentation — that a
 * match played with no connection still counts. Everything here is a way of asking "under what
 * circumstances would a match the player actually played be lost?"
 */
class TranscriptQueueTest {

    // ---- Keeping matches --------------------------------------------------

    @Test
    fun aQueuedTranscriptSurvivesInTheStore() = runTest {
        val store = InMemoryDocumentStore()

        TranscriptQueue(store).add(PROFILE, transcript(seed = 1))

        // A fresh instance, because the point is that it outlives this object and not merely
        // this test: the process the player was in has ended.
        assertEquals(listOf(1), TranscriptQueue(store).pending(PROFILE).map { it.seed })
    }

    @Test
    fun transcriptsComeBackOldestFirst() = runTest {
        val queue = TranscriptQueue(InMemoryDocumentStore())

        queue.add(PROFILE, transcript(seed = 1))
        queue.add(PROFILE, transcript(seed = 2))
        queue.add(PROFILE, transcript(seed = 3))

        assertEquals(listOf(1, 2, 3), queue.pending(PROFILE).map { it.seed })
    }

    @Test
    fun profilesDoNotShareAQueue() = runTest {
        val queue = TranscriptQueue(InMemoryDocumentStore())

        queue.add(PROFILE, transcript(seed = 1))
        queue.add("other", transcript(seed = 2))

        assertEquals(listOf(1), queue.pending(PROFILE).map { it.seed })
        assertEquals(listOf(2), queue.pending("other").map { it.seed })
    }

    @Test
    fun anEmptyQueueReadsAsEmptyRatherThanFailing() = runTest {
        assertTrue(TranscriptQueue(InMemoryDocumentStore()).pending(PROFILE).isEmpty())
    }

    @Test
    fun clearForgetsEverythingForThatProfile() = runTest {
        val queue = TranscriptQueue(InMemoryDocumentStore())
        queue.add(PROFILE, transcript(seed = 1))

        queue.clear(PROFILE)

        assertTrue(queue.pending(PROFILE).isEmpty())
    }

    // ---- Refusing to break the game ---------------------------------------

    /**
     * A corrupt queue must not stop the game starting.
     *
     * The matches in it are lost whatever happens; the only question is whether the player can
     * still play. Throwing here would answer "no" on a launch path.
     */
    @Test
    fun anUnreadableQueueDegradesToEmpty() = runTest {
        val store = InMemoryDocumentStore(mapOf(PROFILE to "not json at all"))

        assertTrue(TranscriptQueue(store).pending(PROFILE).isEmpty())
    }

    @Test
    fun anUnreadableStoreDegradesToEmpty() = runTest {
        val store = InMemoryDocumentStore(failure = IllegalStateException("permission denied"))

        assertTrue(TranscriptQueue(store).pending(PROFILE).isEmpty())
    }

    // ---- The bound --------------------------------------------------------

    /**
     * At the limit the **oldest** go, which is the opposite of what a queue usually drops.
     *
     * Deliberate: the recent matches are the ones whose rewards the player is still waiting for.
     */
    @Test
    fun theOldestAreDroppedOnceTheLimitIsReached() = runTest {
        val queue = TranscriptQueue(InMemoryDocumentStore(), limit = 2)

        queue.add(PROFILE, transcript(seed = 1))
        queue.add(PROFILE, transcript(seed = 2))
        val dropped = queue.add(PROFILE, transcript(seed = 3))

        assertEquals(1, dropped)
        assertEquals(listOf(2, 3), queue.pending(PROFILE).map { it.seed })
    }

    // ---- Draining ---------------------------------------------------------

    @Test
    fun drainingSubmitsEverythingOldestFirstAndEmptiesTheQueue() = runTest {
        val queue = TranscriptQueue(InMemoryDocumentStore())
        queue.add(PROFILE, transcript(seed = 1))
        queue.add(PROFILE, transcript(seed = 2))
        val submitter = RecordingSubmitter { judged(accepted) }

        val results = queue.drain(PROFILE, submitter)

        assertEquals(listOf(1, 2), submitter.seen.map { it.seed })
        assertEquals(2, results.size)
        assertTrue(queue.pending(PROFILE).isEmpty())
    }

    @Test
    fun drainingAnEmptyQueueSubmitsNothing() = runTest {
        val submitter = RecordingSubmitter { judged(accepted) }

        assertTrue(TranscriptQueue(InMemoryDocumentStore()).drain(PROFILE, submitter).isEmpty())
        assertTrue(submitter.seen.isEmpty())
    }

    /**
     * The test the whole class is for.
     *
     * An unreachable server must leave the queue exactly as it was, so the next launch tries again.
     */
    @Test
    fun anOfflineServerLeavesTheQueueIntact() = runTest {
        val store = InMemoryDocumentStore()
        val queue = TranscriptQueue(store)
        queue.add(PROFILE, transcript(seed = 1))
        queue.add(PROFILE, transcript(seed = 2))

        val offline = RecordingSubmitter { SubmissionResult.Offline("refused") }

        val results = queue.drain(PROFILE, offline)

        assertTrue(results.isEmpty(), "nothing was judged, so nothing is reported")
        assertEquals(listOf(1, 2), queue.pending(PROFILE).map { it.seed })
    }

    /** Going offline halfway keeps the rest and only the rest. */
    @Test
    fun goingOfflineHalfwayKeepsWhatWasNotJudged() = runTest {
        val queue = TranscriptQueue(InMemoryDocumentStore())
        queue.add(PROFILE, transcript(seed = 1))
        queue.add(PROFILE, transcript(seed = 2))
        queue.add(PROFILE, transcript(seed = 3))
        var calls = 0
        val submitter = RecordingSubmitter {
            calls++
            if (calls == 1) judged(accepted) else SubmissionResult.Offline("gone")
        }

        val results = queue.drain(PROFILE, submitter)

        assertEquals(1, results.size)
        assertEquals(listOf(2, 3), queue.pending(PROFILE).map { it.seed })
    }

    /** And it stops there rather than spending a timeout on each of the rest. */
    @Test
    fun drainingStopsAtTheFirstOfflineResult() = runTest {
        val queue = TranscriptQueue(InMemoryDocumentStore())
        repeat(5) { queue.add(PROFILE, transcript(seed = it)) }
        val submitter = RecordingSubmitter { SubmissionResult.Offline("refused") }

        queue.drain(PROFILE, submitter)

        assertEquals(1, submitter.seen.size, "the other four would be four more timeouts")
    }

    /**
     * A rejection is an **answer**, so the transcript goes.
     *
     * Keeping it would have the client resubmit a transcript the server has already refused, on
     * every launch, forever — for a verdict that cannot change.
     */
    @Test
    fun aRejectedTranscriptIsConsumedRatherThanRetried() = runTest {
        val queue = TranscriptQueue(InMemoryDocumentStore())
        queue.add(PROFILE, transcript(seed = 1))
        val rejected = MatchVerdict.Rejected(RejectionReason.ILLEGAL_MOVE, "cell was taken")

        val results = queue.drain(PROFILE, RecordingSubmitter { judged(rejected) })

        assertIs<SubmissionResult.Judged>(results.single())
        assertTrue(queue.pending(PROFILE).isEmpty(), "the server has answered")
    }

    @Test
    fun anUpdateRequiredTranscriptIsConsumed() = runTest {
        val queue = TranscriptQueue(InMemoryDocumentStore())
        queue.add(PROFILE, transcript(seed = 1))
        val outdated = SubmissionResult.UpdateRequired(AppVersion(2, 0, 0))

        val results = queue.drain(PROFILE, RecordingSubmitter { outdated })

        assertEquals(listOf(outdated), results)
        assertTrue(queue.pending(PROFILE).isEmpty(), "the same bytes cannot start working")
    }

    @Test
    fun aFailedTranscriptIsConsumed() = runTest {
        val queue = TranscriptQueue(InMemoryDocumentStore())
        queue.add(PROFILE, transcript(seed = 1))
        val failed = SubmissionResult.Failed(status = 400, detail = "malformed_request")

        queue.drain(PROFILE, RecordingSubmitter { failed })

        assertTrue(queue.pending(PROFILE).isEmpty(), "it will not become readable")
    }

    /**
     * No session is a **hold**, not an answer — the other half of "an honest match still counts".
     *
     * A player who plays offline and signs in afterwards is the ordinary case, not an edge one:
     * the transcript was made before there was anyone to credit it to. Consuming it here would
     * throw away exactly the matches accounts were added to keep.
     */
    @Test
    fun anUnauthenticatedDrainLeavesTheQueueIntact() = runTest {
        val queue = TranscriptQueue(InMemoryDocumentStore())
        queue.add(PROFILE, transcript(seed = 1))
        queue.add(PROFILE, transcript(seed = 2))

        val results = queue.drain(PROFILE, RecordingSubmitter { SubmissionResult.Unauthenticated })

        assertTrue(results.isEmpty(), "nothing was judged")
        assertEquals(listOf(1, 2), queue.pending(PROFILE).map { it.seed })
    }

    // ---- Fixtures ---------------------------------------------------------

    /**
     * A verdict as it now arrives: inside the receipt the server credits it with.
     *
     * The queue does not read past the verdict — what it decides is whether the transcript has been
     * *answered* — so an otherwise empty receipt is the honest fixture here. What the credited
     * profile does next belongs to `MatchReporter`.
     */
    private fun judged(verdict: MatchVerdict) =
        SubmissionResult.Judged(MatchReceipt(verdict = verdict))

    private class RecordingSubmitter(
        private val answer: () -> SubmissionResult,
    ) : MatchSubmitter {
        val seen = mutableListOf<MatchTranscript>()

        override suspend fun submit(transcript: MatchTranscript): SubmissionResult {
            seen += transcript
            return answer()
        }
    }

    private val accepted = MatchVerdict.Accepted(blue = 6, red = 4, winner = "BLUE")

    private fun transcript(seed: Int) = MatchTranscript(
        seed = seed,
        collection = CardCollection.FF14,
        opponentIconId = "tt-master",
        deck = listOf(1, 2, 3, 4, 5),
        ownedCards = listOf(1, 2, 3, 4, 5),
        moves = listOf(TranscriptMove(cardId = 1, position = 0)),
    )

    private companion object {
        const val PROFILE = "cid"
    }
}
