package com.tripletriad.net

import com.tripletriad.model.GameSave
import com.tripletriad.protocol.AccountError
import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.Credentials
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.Session
import com.tripletriad.protocol.VERSION_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The client half of accounts, against a fake transport.
 *
 * The same bargain [KtorMatchSubmitterTest] makes, and for the same reason: the cases worth
 * stating are the ones a healthy server never produces. What matters throughout is that **nothing
 * throws** — a sign-in form has to render "the server is unreachable" in the same place it renders
 * "that name is taken", and it cannot do that if one of the two arrives as an exception.
 */
class AccountClientTest {

    // ---- The ordinary outcomes --------------------------------------------

    @Test
    fun registeringReturnsTheSessionTheServerCreated() = runTest {
        val client = clientAnswering(HttpStatusCode.Created, encode(session))

        val result = client.register(credentials)

        assertEquals(session, assertIs<AccountResult.Ok<Session>>(result).value)
    }

    /**
     * A 200 to a registration is not a session.
     *
     * Registration answers 201 and only 201. Accepting any 2xx would mean decoding whatever a proxy
     * or a redirect handler put there as if the account had been made.
     */
    @Test
    fun aRegistrationAnsweredWithTwoHundredIsNotTreatedAsSuccess() = runTest {
        val client = clientAnswering(HttpStatusCode.OK, encode(session))

        assertFalse(client.register(credentials) is AccountResult.Ok)
    }

    @Test
    fun signingInReturnsTheSession() = runTest {
        val client = clientAnswering(HttpStatusCode.OK, encode(session))

        assertEquals(session, client.signIn(credentials).valueOrNull())
    }

    @Test
    fun meReturnsThePlayerTheServerHolds() = runTest {
        val client = clientAnswering(HttpStatusCode.OK, encode(player))

        assertEquals(player, client.me(TOKEN).valueOrNull())
    }

    /** Both of the server's two ways of saying "stored" are success. */
    @Test
    fun savingAProfileAcceptsBothNoContentAndOk() = runTest {
        val save = GameSave(username = "kuplu")

        assertIs<AccountResult.Ok<Unit>>(
            clientAnswering(HttpStatusCode.NoContent, "").saveProfile(TOKEN, save),
        )
        assertIs<AccountResult.Ok<Unit>>(
            clientAnswering(HttpStatusCode.OK, "").saveProfile(TOKEN, save),
        )
    }

    @Test
    fun signingOutIsANoContentAnswer() = runTest {
        var seen: HttpRequestData? = null
        val client = clientAnswering(HttpStatusCode.NoContent, "", record = { seen = it })

        assertIs<AccountResult.Ok<Unit>>(client.signOut(TOKEN))
        assertEquals(HttpMethod.Delete, seen?.method)
    }

    // ---- Refusals are results ---------------------------------------------

    @Test
    fun aTakenNameArrivesAsTheReasonAndNotAsAMessage() = runTest {
        val client = clientAnswering(
            HttpStatusCode.Conflict,
            """{"error":"USERNAME_TAKEN","detail":"that name is taken"}""",
        )

        val refused = assertIs<AccountResult.Refused>(client.register(credentials))
        assertEquals(AccountError.USERNAME_TAKEN, refused.failure.error)
    }

    /**
     * The one refusal that has to be recognisable without reading prose: it is what tells a caller
     * to throw the stored token away rather than to show the player an error.
     */
    @Test
    fun aDeadSessionIsRecognisableAsSuch() = runTest {
        val client = clientAnswering(
            HttpStatusCode.Unauthorized,
            """{"error":"UNAUTHENTICATED","detail":"unknown token"}""",
        )

        val result = client.me(TOKEN)

        assertTrue(result.isUnauthenticated())
        assertNull(result.valueOrNull())
    }

    @Test
    fun aWrongPasswordIsNotUnauthenticated() = runTest {
        val client = clientAnswering(
            HttpStatusCode.Unauthorized,
            """{"error":"INVALID_CREDENTIALS","detail":"no"}""",
        )

        // Same status, different meaning: this one keeps the player on the sign-in form rather
        // than clearing a session they do not have.
        assertFalse(client.signIn(credentials).isUnauthenticated())
    }

    // ---- The cases that must not become exceptions ------------------------

    @Test
    fun anUnreachableServerIsOffline() = runTest {
        val engine = MockEngine { throw IOException("Connection refused") }
        val client = AccountClient(httpClient(engine), address)

        val offline = assertIs<AccountResult.Offline>(client.signIn(credentials))
        assertTrue(offline.cause.isNotBlank())
    }

    /**
     * A captive portal answering 200 with HTML.
     *
     * The body decode is inside the guard for exactly this: without it the exception escapes a
     * caller that has handled every documented outcome.
     */
    @Test
    fun aTwoHundredThatIsNotASessionDoesNotThrow() = runTest {
        val client = clientAnswering(
            HttpStatusCode.OK,
            "<html>Sign in to the network</html>",
            contentType = ContentType.Text.Html,
        )

        assertNull(client.signIn(credentials).valueOrNull())
    }

    /** A failure body that is not an `AccountFailure` degrades to the status it came with. */
    @Test
    fun anUnrecognisableFailureBodyKeepsTheStatus() = runTest {
        val client = clientAnswering(HttpStatusCode.BadGateway, "<html>502</html>")

        val failed = assertIs<AccountResult.Failed>(client.signIn(credentials))
        assertEquals(HttpStatusCode.BadGateway.value, failed.status)
    }

    // ---- The version gate -------------------------------------------------

    @Test
    fun everyRequestAnnouncesTheClientVersion() = runTest {
        var seen: HttpRequestData? = null
        val client = clientAnswering(HttpStatusCode.OK, encode(player), record = { seen = it })

        client.me(TOKEN)

        assertEquals(CURRENT_VERSION.toString(), seen?.headers?.get(VERSION_HEADER))
    }

    @Test
    fun a426IsAnUpdateAndNotARefusal() = runTest {
        val client = clientAnswering(
            HttpStatusCode.UpgradeRequired,
            """{"error":"upgrade_required","server":"2.0.0"}""",
            headers = headersOf(VERSION_HEADER, "2.0.0"),
        )

        val update = assertIs<AccountResult.UpdateRequired>(client.signIn(credentials))
        assertEquals(AppVersion(2, 0, 0), update.serverVersion)
    }

    // ---- The token -------------------------------------------------------

    @Test
    fun theTokenTravelsAsABearerHeader() = runTest {
        var seen: HttpRequestData? = null
        val client = clientAnswering(HttpStatusCode.OK, encode(player), record = { seen = it })

        client.me(TOKEN)

        assertEquals("Bearer $TOKEN", seen?.headers?.get("Authorization"))
    }

    /** Credentials go in the body, never in the path — a URL is logged by every proxy there is. */
    @Test
    fun credentialsAreNotPutInTheUrl() = runTest {
        var seen: HttpRequestData? = null
        val client = clientAnswering(HttpStatusCode.OK, encode(session), record = { seen = it })

        client.signIn(credentials)

        val url = seen?.url.toString()
        assertFalse(url.contains(credentials.password), "the password was in the URL")
        assertFalse(url.contains(credentials.username), "the username was in the URL")
    }

    // ---- Fixtures ---------------------------------------------------------

    private fun httpClient(engine: MockEngine) = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(matchProtocolJson) }
    }

    private fun clientAnswering(
        status: HttpStatusCode,
        body: String,
        contentType: ContentType = ContentType.Application.Json,
        headers: io.ktor.http.Headers = io.ktor.http.Headers.Empty,
        record: (HttpRequestData) -> Unit = {},
    ): AccountClient {
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
        return AccountClient(httpClient(engine), address)
    }

    private inline fun <reified T> encode(value: T) = matchProtocolJson.encodeToString(value)

    private val credentials = Credentials(username = "kuplu", password = "not-a-real-password")

    private val player = PlayerState(save = GameSave(username = "kuplu", mgp = 4200))

    private val session = Session(token = TOKEN, expiresAt = 1_770_086_400_000L, player = player)

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
