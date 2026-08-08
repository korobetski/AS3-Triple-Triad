package com.tripletriad.protocol

/**
 * Sends a finished match to whatever decides whether it happened.
 *
 * ### Why an interface, in `:core`, with no transport in sight
 *
 * The migration document asks for the protocol to be separable from the transport, so that the rest
 * of Phase 5 — local PvP in particular — can be built and tested against an in-memory pair of
 * endpoints with no Ktor and no network at all. This is where that separation starts: `:core`
 * states what submitting means, `:shared` supplies an implementation that speaks HTTP, and a test
 * supplies one that speaks nothing.
 *
 * It also keeps Ktor out of the app modules' sight. They depend on this; only `:shared` depends on
 * a transport.
 */
fun interface MatchSubmitter {
    /**
     * Submits [transcript] and reports what came back.
     *
     * Implementations **do not throw** for an unreachable server — see [SubmissionResult.Offline]
     * for why that is a result and not an exception.
     */
    suspend fun submit(transcript: MatchTranscript): SubmissionResult
}

/**
 * What happened when a transcript was submitted.
 *
 * ### Why this is not just [MatchVerdict]
 *
 * Because a verdict is what the server says about the *match*, and there are three things that can
 * happen before it gets to say anything. Collapsing them — returning a null verdict, or throwing —
 * would lose the distinction the caller most needs: whether it is worth trying again.
 */
sealed interface SubmissionResult {

    /**
     * The server replayed the match, reached a conclusion, and said what it credited.
     *
     * Note that a *rejected* transcript arrives here rather than in [Failed]: the server worked
     * exactly as intended and answered the question it was asked. Nothing is worth retrying.
     *
     * The whole [MatchReceipt] is carried rather than the verdict alone, because with progression
     * held server-side the interesting part of the answer is no longer the score — it is the
     * profile that came back. A caller that only wants the score reads [verdict].
     */
    data class Judged(val receipt: MatchReceipt) : SubmissionResult {
        val verdict: MatchVerdict get() = receipt.verdict
    }

    /**
     * The server does not accept this session, so nothing was credited.
     *
     * Kept apart from [Failed] because the transcript must **survive** it. An expired token, a
     * player who signed out on another device, a session swept by a redeploy — all of them are
     * fixed by signing in again, after which the same bytes are worth crediting. Discarding the
     * match instead would punish the player for a session's lifetime, which is not something they
     * chose or can see.
     *
     * The caller's job is therefore to sign in and drain again, not to retry immediately: retrying
     * with the same rejected token is the one thing that certainly will not work.
     */
    data object Unauthenticated : SubmissionResult

    /**
     * The server could not be reached.
     *
     * The single most important case in this file, and the reason submission has a result type at
     * all. The design's central claim is that **an honest match played offline still counts** —
     * the transcript is checkable later, because it is a recomputation and not a recording. So this
     * is not an error to surface as one: it means *hold the transcript and submit it later*, and a
     * caller that treated it as a failure would throw away a match the player really played.
     *
     * @property cause for a log. Not for a player, who cannot act on it.
     */
    data class Offline(val cause: String) : SubmissionResult

    /**
     * The server is a newer major version and refused to talk. The player must update.
     *
     * Distinct from [Failed] because retrying is pointless and holding the transcript is not
     * obviously right either: this build's replay may reach a different answer from the server's,
     * which is exactly what a major bump declares. See [AppVersion].
     *
     * @property serverVersion what the server said it was, when it said.
     */
    data class UpdateRequired(val serverVersion: AppVersion?) : SubmissionResult

    /**
     * The server was reached, understood the request, and something went wrong anyway.
     *
     * A bug, a bad deploy, a transcript this build serialises in a way the server cannot read.
     * Worth retrying once and worth reporting; not worth silently discarding the match.
     */
    data class Failed(val status: Int, val detail: String) : SubmissionResult
}
