package com.tripletriad.net

import com.tripletriad.model.CardCollection
import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.MatchTranscript
import com.tripletriad.protocol.MatchVerdict
import com.tripletriad.protocol.RejectionReason
import com.tripletriad.protocol.SubmissionResult
import com.tripletriad.protocol.TranscriptMove
import com.tripletriad.protocol.VERSION_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The client half of match verification, against a fake transport.
 *
 * ### Why `MockEngine` and not a running server
 *
 * Because the interesting cases here are the ones a healthy server never produces: a refused
 * connection, a proxy's HTML error page returned with a 200, a server on a newer major. Those are
 * awkward to provoke against a real process and trivial to state here — and the suite stays in
 * `commonTest`, so it runs on the desktop JVM and Android's alike with no port to bind.
 *
 * The honest path is covered end to end elsewhere, by the server's own `MatchRoutesTest`.
 */
class KtorMatchSubmitterTest {

    // ---- The ordinary outcomes --------------------------------------------

    @Test
    fun anAcceptedVerdictComesBackAsJudged() = runTest {
        val submitter = submitterAnswering(
            status = HttpStatusCode.OK,
            body = receipt("""{"type":"accepted","blue":3,"red":7,"winner":"RED"}"""),
        )

        val result = submitter.submit(transcript)

        val judged = assertIs<SubmissionResult.Judged>(result, "was $result")
        assertEquals(MatchVerdict.Accepted(blue = 3, red = 7, winner = "RED"), judged.verdict)
    }

    /**
     * A rejected transcript is a **result**, not a failure.
     *
     * The server worked and answered the question; there is nothing to retry. Collapsing this into
     * an error would be the bug that makes an honest player's client keep resubmitting.
     */
    @Test
    fun aRejectedVerdictIsAlsoJudgedRatherThanAFailure() = runTest {
        val submitter = submitterAnswering(
            status = HttpStatusCode.OK,
            body = receipt(
                """{"type":"rejected","reason":"TRUNCATED","detail":"ran out of moves"}""",
            ),
        )

        val judged = assertIs<SubmissionResult.Judged>(submitter.submit(transcript))
        val rejected = assertIs<MatchVerdict.Rejected>(judged.verdict)
        assertEquals(RejectionReason.TRUNCATED, rejected.reason)
    }

    /** A draw puts no `winner` on the wire at all, because the server drops nulls. */
    @Test
    fun aDrawnVerdictDecodesWithNoWinnerField() = runTest {
        val submitter = submitterAnswering(
            status = HttpStatusCode.OK,
            body = receipt("""{"type":"accepted","blue":5,"red":5}"""),
        )

        val judged = assertIs<SubmissionResult.Judged>(submitter.submit(transcript))
        assertEquals(MatchVerdict.Accepted(blue = 5, red = 5, winner = null), judged.verdict)
    }

    // ---- The version gate -------------------------------------------------

    @Test
    fun theClientAnnouncesItsVersionOnEveryRequest() = runTest {
        var seen: HttpRequestData? = null
        val submitter = submitterAnswering(
            status = HttpStatusCode.OK,
            body = receipt("""{"type":"accepted","blue":5,"red":5}"""),
            record = { seen = it },
        )

        submitter.submit(transcript)

        assertEquals(CURRENT_VERSION.toString(), seen?.headers?.get(VERSION_HEADER))
    }

    @Test
    fun a426BecomesUpdateRequiredCarryingTheServersVersion() = runTest {
        val submitter = submitterAnswering(
            status = HttpStatusCode.UpgradeRequired,
            body = """{"error":"upgrade_required","server":"2.0.0","client":"1.4.2"}""",
            headers = headersOf(VERSION_HEADER, "2.0.0"),
        )

        val result = submitter.submit(transcript)

        val update = assertIs<SubmissionResult.UpdateRequired>(result, "was $result")
        assertEquals(AppVersion(2, 0, 0), update.serverVersion)
    }

    /** A 426 without a readable header is still an update, just a less informative one. */
    @Test
    fun a426WithNoUsableHeaderIsStillUpdateRequired() = runTest {
        val submitter = submitterAnswering(
            status = HttpStatusCode.UpgradeRequired,
            body = "{}",
            headers = headersOf(VERSION_HEADER, "not-a-version"),
        )

        val update = assertIs<SubmissionResult.UpdateRequired>(submitter.submit(transcript))
        assertNull(update.serverVersion)
    }

    // ---- The cases that must not lose the match ---------------------------

    /**
     * The most important test in the file.
     *
     * "An honest match played offline still counts" is the claim the whole design is built on, and
     * it is only true if an unreachable server produces something the caller can hold and resubmit
     * rather than an exception it has to guess how to handle.
     */
    @Test
    fun anUnreachableServerIsOfflineAndNotAnException() = runTest {
        val submitter = submitterThrowing(IOException("Connection refused"))

        val result = submitter.submit(transcript)

        val offline = assertIs<SubmissionResult.Offline>(result, "was $result")
        assertTrue(offline.cause.isNotBlank())
    }

    /**
     * A 200 whose body is not a verdict.
     *
     * A captive portal or a corporate proxy answers exactly like this — status 200, content that
     * has nothing to do with the request. Without the guard the decode throws past a caller that
     * has handled every documented outcome.
     */
    @Test
    fun aTwoHundredThatIsNotAVerdictFailsInsteadOfThrowing() = runTest {
        val submitter = submitterAnswering(
            status = HttpStatusCode.OK,
            body = "<html>Sign in to the network</html>",
            contentType = ContentType.Text.Html,
        )

        val failed = assertIs<SubmissionResult.Failed>(submitter.submit(transcript))
        assertTrue(failed.detail.isNotBlank())
    }

    @Test
    fun aServerErrorIsReportedWithItsStatus() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError, "boom") }
        val submitter = KtorMatchSubmitter(httpClient(engine), address, token = { TOKEN })

        val failed = assertIs<SubmissionResult.Failed>(submitter.submit(transcript))
        assertEquals(HttpStatusCode.InternalServerError.value, failed.status)
    }

    /**
     * A 401 is not a verdict about the match.
     *
     * The session expired, or the server was redeployed and swept its sessions. The transcript is
     * still honest, so the answer has to be one `TranscriptQueue` will hold rather than one it
     * treats as judged and drops.
     */
    @Test
    fun anExpiredSessionIsUnauthenticatedRatherThanAFailure() = runTest {
        val submitter = submitterAnswering(
            status = HttpStatusCode.Unauthorized,
            body = """{"error":"unauthorized"}""",
        )

        assertEquals(SubmissionResult.Unauthenticated, submitter.submit(transcript))
    }

    /** Nobody signed in: the same answer, without spending a round trip to be told so. */
    @Test
    fun noTokenIsUnauthenticatedWithoutAskingTheServer() = runTest {
        var asked = false
        val engine = MockEngine {
            asked = true
            respondError(HttpStatusCode.InternalServerError, "should not have been called")
        }
        val submitter = KtorMatchSubmitter(httpClient(engine), address, token = { null })

        assertEquals(SubmissionResult.Unauthenticated, submitter.submit(transcript))
        assertTrue(!asked, "the submitter contacted the server with no session")
    }

    @Test
    fun theSessionTokenTravelsAsABearerHeader() = runTest {
        var seen: HttpRequestData? = null
        val submitter = submitterAnswering(
            status = HttpStatusCode.OK,
            body = receipt("""{"type":"accepted","blue":5,"red":5}"""),
            record = { seen = it },
        )

        submitter.submit(transcript)

        assertEquals("Bearer $TOKEN", seen?.headers?.get("Authorization"))
    }

    // ---- Fixtures ---------------------------------------------------------

    /**
     * The envelope a verdict now travels in.
     *
     * The server answers `/matches/submit` with a receipt — the verdict plus what it credited — so
     * a bare verdict is no longer a body any of these tests could receive. The other three fields
     * are optional, which is what lets these cases stay about the verdict alone.
     */
    private fun receipt(verdict: String) = """{"verdict":$verdict}"""

    private fun httpClient(engine: MockEngine) = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(matchProtocolJson) }
    }

    private fun submitterAnswering(
        status: HttpStatusCode,
        body: String,
        contentType: ContentType = ContentType.Application.Json,
        headers: io.ktor.http.Headers = io.ktor.http.Headers.Empty,
        record: (HttpRequestData) -> Unit = {},
    ): KtorMatchSubmitter {
        val engine = MockEngine { request ->
            record(request)
            respond(
                content = body,
                status = status,
                headers = io.ktor.http.HeadersBuilder().apply {
                    appendAll(headers)
                    append("Content-Type", contentType.toString())
                }.build(),
            )
        }
        return KtorMatchSubmitter(httpClient(engine), address, token = { TOKEN })
    }

    private fun submitterThrowing(failure: Throwable): KtorMatchSubmitter {
        val engine = MockEngine { throw failure }
        return KtorMatchSubmitter(httpClient(engine), address, token = { TOKEN })
    }

    private val transcript = MatchTranscript(
        seed = 20260807,
        collection = CardCollection.FF14,
        opponentIconId = "tt-master",
        deck = listOf(1, 2, 3, 4, 5),
        ownedCards = listOf(1, 2, 3, 4, 5),
        moves = listOf(TranscriptMove(cardId = 1, position = 0)),
    )

    /**
     * The address, read per request.
     *
     * A lambda and not a string because the player can switch servers while the app is running,
     * and a client holding the address it was built with would keep talking to the one they left
     * — see [ServerDirectory]. These tests only need it to be constant.
     */
    private val address: suspend () -> String = { BASE_URL }

    private companion object {
        const val BASE_URL = "http://127.0.0.1:8080"

        /** Opaque to the client and meaningless to the mock — it only has to arrive. */
        const val TOKEN = "test-session"
    }
}
