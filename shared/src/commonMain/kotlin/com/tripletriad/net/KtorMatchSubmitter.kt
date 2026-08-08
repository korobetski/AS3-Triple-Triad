package com.tripletriad.net

import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.MatchReceipt
import com.tripletriad.protocol.MatchSubmitter
import com.tripletriad.protocol.MatchTranscript
import com.tripletriad.protocol.SubmissionResult
import com.tripletriad.protocol.VERSION_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * Submits a transcript to the server over HTTP.
 *
 * The whole client half of match verification, and it is deliberately this small: the transcript
 * is built by `:core` and judged by `:core` on the other end, so there is nothing here but a POST,
 * a version header, a bearer token and the careful translation of everything that can go wrong into
 * [SubmissionResult].
 *
 * ### It submits for credit, not for an opinion
 *
 * The endpoint is `/matches/submit`, which is authenticated and which *writes* — the server credits
 * the match against the profile it holds and sends the profile back. `/matches/verify` still exists
 * and is still useful (a client can check its own transcript before claiming anything), but it is
 * not what a finished match goes to: an answer that changed nothing would leave the client to
 * credit itself, which is the arrangement accounts exist to end.
 *
 * @property client the transport. Injected rather than built here so the engine stays a
 *   per-platform choice and so tests can pass Ktor's `MockEngine` — see [matchSubmitterHttpClient]
 *   for the configuration a real one needs.
 * @property baseUrl where the server is, without a trailing slash — `http://127.0.0.1:8080` from an
 *   Android emulator, `http://127.0.0.1:8080` from the desktop. A function for the same reason as
 *   [token]: the player can switch servers between a queue being written and it being drained.
 * @property token the current session's bearer token, read at submission time rather than held.
 *   A function because a queue drained on launch may be drained again after a sign-in, and a
 *   submitter constructed with a token would be submitting with the one that was current when the
 *   screen was built. Returns null when nobody is signed in, which is [SubmissionResult]'s
 *   `Unauthenticated` without a round trip to discover it.
 */
class KtorMatchSubmitter(
    private val client: HttpClient,
    private val baseUrl: suspend () -> String,
    private val token: suspend () -> String?,
    private val version: AppVersion = CURRENT_VERSION,
) : MatchSubmitter {

    // The broad catch is the feature, not an oversight: no network failure may lose a match the
    // player really played. Enumerating Ktor's exception types would be a list that goes stale
    // without anything failing to tell us. `CancellationException` is rethrown first, which is the
    // one case a blanket catch must never swallow.
    // The early returns are the point too: "nobody is signed in" and "the transport failed" are
    // answers, and threading them through a single exit would mean a nullable holding the result
    // of a call that was never made.
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    override suspend fun submit(transcript: MatchTranscript): SubmissionResult {
        // Not a round trip to be told what is already known. The transcript stays queued — see
        // `TranscriptQueue.drain` — so signing in later still collects it.
        val bearer = token() ?: return SubmissionResult.Unauthenticated

        val response = try {
            client.post("${baseUrl()}/matches/submit") {
                contentType(ContentType.Application.Json)
                header(VERSION_HEADER, version.toString())
                bearer(bearer)
                setBody(transcript)
            }
        } catch (cancellation: CancellationException) {
            // Never swallowed: the caller navigated away or the scope died, and reporting that as
            // "the server is offline" would queue a transcript nobody asked to submit.
            throw cancellation
        } catch (failure: Exception) {
            // Everything else the transport can raise — DNS, refused connection, timeout, TLS.
            // Deliberately broad: the point is that *no* network failure loses the match, and
            // enumerating Ktor's exception types would be a list that goes stale silently.
            return SubmissionResult.Offline(failure.message ?: failure.toString())
        }

        return response.toResult()
    }

    private suspend fun HttpResponse.toResult(): SubmissionResult = when (status.value) {
        HTTP_OK -> readReceipt()

        // The session is gone: expired, signed out elsewhere, or swept by a redeploy. Not a verdict
        // about the match, so the transcript survives it and a sign-in makes it creditable again.
        HTTP_UNAUTHORIZED -> SubmissionResult.Unauthenticated

        // The server said this build is too old. Its own version comes back in the same header it
        // rejected ours by, so the log can say which two numbers disagreed rather than only that
        // they did.
        HTTP_UPGRADE_REQUIRED -> SubmissionResult.UpdateRequired(
            headers[VERSION_HEADER]?.let(AppVersion::parse),
        )

        else -> SubmissionResult.Failed(status.value, bodyAsText().take(DETAIL_LIMIT))
    }

    /**
     * Reads the receipt out of a 200.
     *
     * The decode is guarded because a 200 whose body will not parse is not a receipt — it is a
     * proxy's error page, a captive portal, or a server new enough to answer in a shape this build
     * does not know. Letting the exception out would crash a caller that has already handled every
     * *documented* outcome.
     */
    // Same reasoning as `submit`: a 200 that will not decode is a captive portal or a server this
    // build does not understand, and both are results rather than crashes.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun HttpResponse.readReceipt(): SubmissionResult = try {
        SubmissionResult.Judged(body<MatchReceipt>())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        SubmissionResult.Failed(status.value, "unreadable receipt: ${failure.message}")
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_UPGRADE_REQUIRED = 426

        /** Enough of a server error to identify it in a log, not enough to be a payload. */
        const val DETAIL_LIMIT = 500
    }
}

/**
 * The JSON configuration both ends must agree on.
 *
 * `ignoreUnknownKeys` is the one that matters, and it is what makes a **minor** server version
 * survivable: a server that adds a field to a verdict must not break every client that has not
 * shipped yet. The major gate handles the changes that genuinely cannot be tolerated; this handles
 * the ones that can, and without it the distinction between major and minor would buy nothing.
 */
val matchProtocolJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * Configures an [HttpClient] for talking to the server.
 *
 * A function rather than a constructed client, because the engine is chosen per platform and this
 * module has no business picking one — `:androidApp` has OkHttp, `:desktopApp` has CIO. Call it
 * with the engine the host supplies.
 */
fun matchSubmitterHttpClient(engineFactory: HttpClientEngineFactory<*>): HttpClient =
    HttpClient(engineFactory) {
        // Not `expectSuccess = true`: a non-2xx here is an answer to translate, not an exception
        // to throw. 426 in particular is a documented outcome of a healthy exchange.
        expectSuccess = false
        install(ContentNegotiation) {
            json(matchProtocolJson)
        }
    }
