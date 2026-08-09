package com.tripletriad.net

import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.ClientRelease
import com.tripletriad.protocol.ServerInfo
import com.tripletriad.protocol.VERSION_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
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
 * Asking a server what it is.
 *
 * The value of this class is entirely in the *distinctions* it draws, so that is what is tested:
 * every case below is one a boolean "online" would have collapsed into another, and each collapse
 * would give the player the wrong advice — telling somebody whose build is three majors behind to
 * check their wifi, or telling somebody behind a captive portal that the game is down.
 *
 * The other guarantee is that none of it throws. A probe runs behind a screen the player may leave
 * at any moment and must not be able to take anything with it.
 */
class ServerProbeTest {

    @Test
    fun aHealthyServerIsOnline() = runTest {
        val status = probeAnswering(HttpStatusCode.OK, encode(healthy))

        val online = assertIs<ServerStatus.Online>(status)
        assertEquals("Test server", online.info.name)
        assertTrue(online.isUsable)
    }

    /** The round trip, which is what a player choosing between two servers is actually after. */
    @Test
    fun theRoundTripIsMeasured() = runTest {
        var reading = 0L
        val probe = ServerProbe(
            client = clientAnswering { respondJson(HttpStatusCode.OK, encode(healthy)) },
            elapsed = { reading.also { reading += ELAPSED } },
        )

        assertEquals(ELAPSED, probe.probe(BASE_URL).latency)
    }

    /**
     * A server that answers and says its database is down is not a server that is unreachable.
     *
     * The advice differs: this one is worth trying again in a minute, and signing in will not work
     * in the meantime. `Unreachable` means check the network, which would be wrong here.
     */
    @Test
    fun aServerWithNoDatabaseIsDegradedAndNotUnreachable() = runTest {
        val status = probeAnswering(HttpStatusCode.OK, encode(healthy.copy(ready = false)))

        val degraded = assertIs<ServerStatus.Degraded>(status)
        assertEquals("Test server", degraded.info.name)
        assertFalse(degraded.isUsable)
    }

    /** The state the whole endpoint exists to make expressible. */
    @Test
    fun aServerThatWillNotServeThisBuildIsOutdated() = runTest {
        val demanding = healthy.copy(minimumClient = AppVersion(CURRENT_VERSION.major + 1, 0, 0))

        val status = probeAnswering(HttpStatusCode.OK, encode(demanding))

        val outdated = assertIs<ServerStatus.Outdated>(status)
        assertEquals(demanding.minimumClient, outdated.info.minimumClient)
        assertFalse(outdated.isUsable)
    }

    @Test
    fun aServerThatIsNotThereIsUnreachable() = runTest {
        val probe = ServerProbe(
            client = clientAnswering { throw IOException("Connection refused") },
            elapsed = { 0L },
        )

        val unreachable = assertIs<ServerStatus.Unreachable>(probe.probe(BASE_URL))
        assertTrue(unreachable.cause.isNotBlank())
        assertNull(unreachable.latency)
    }

    @Test
    fun anErrorStatusIsNotAServerDescription() = runTest {
        val status = probeAnswering(HttpStatusCode.BadGateway, "<html>502</html>")

        assertIs<ServerStatus.Unusable>(status)
    }

    /**
     * A captive portal: a 200, with something that is not this game on the other end.
     *
     * Distinguished from [ServerStatus.Unreachable] because "there is something there and it is not
     * the game" is a different problem from "there is nothing there", and the fix differs too.
     */
    @Test
    fun aTwoHundredThatIsNotAServerInfoIsUnusable() = runTest {
        val status = probeAnswering(HttpStatusCode.OK, """{"welcome":"free airport wifi"}""")

        assertIs<ServerStatus.Unusable>(status)
    }

    /**
     * A server so new that this build cannot read its body still gets the right verdict.
     *
     * The header is the one field that cannot stop being readable, which is the whole reason it
     * exists — so a body this build cannot decode falls back to it rather than to a shrug.
     */
    @Test
    fun aServerTooNewToDecodeIsStillKnownToBeTooNew() = runTest {
        val theirs = AppVersion(CURRENT_VERSION.major + 1, 0, 0)
        val probe = ServerProbe(
            client = clientAnswering {
                respond(
                    content = """{"shape":"from the future"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        "Content-Type" to listOf(ContentType.Application.Json.toString()),
                        VERSION_HEADER to listOf(theirs.toString()),
                    ),
                )
            },
            elapsed = { 0L },
        )

        val outdated = assertIs<ServerStatus.Outdated>(probe.probe(BASE_URL))
        assertEquals(theirs, outdated.info.version)
    }

    /** And without the header there is nothing to conclude but that it is not usable. */
    @Test
    fun anUnreadableBodyWithNoHeaderIsMerelyUnusable() = runTest {
        assertIs<ServerStatus.Unusable>(probeAnswering(HttpStatusCode.OK, "not json at all"))
    }

    // ---- The request itself ------------------------------------------------

    @Test
    fun theProbeAsksTheOneRouteThatIsNeverGated() = runTest {
        var asked: HttpRequestData? = null
        val probe = ServerProbe(
            client = clientAnswering { request ->
                asked = request
                respondJson(HttpStatusCode.OK, encode(healthy))
            },
            elapsed = { 0L },
        )

        probe.probe("$BASE_URL/")

        assertEquals("/server", asked?.url?.encodedPath)
        // Sent even though this route refuses nobody, so a deployment's logs can see which builds
        // are asking — and so this request looks like every other one.
        assertEquals(CURRENT_VERSION.toString(), asked?.headers?.get(VERSION_HEADER))
    }

    // ---- The download offered ----------------------------------------------

    /** An Android player sent to a desktop installer is worse off than one told only to update. */
    @Test
    fun onlyThisPlatformsDownloadIsOffered() {
        val published = healthy.copy(
            release = ClientRelease(
                version = AppVersion(9, 0, 0),
                downloads = mapOf(clientPlatform to "https://example.org/here"),
            ),
        )

        assertEquals("https://example.org/here", published.downloadForThisPlatform())
    }

    @Test
    fun aDeploymentThatPublishesNothingOffersNothing() {
        assertNull(healthy.downloadForThisPlatform())
    }

    // ---- Fixtures ----------------------------------------------------------

    private suspend fun probeAnswering(status: HttpStatusCode, body: String): ServerStatus =
        ServerProbe(
            client = clientAnswering { respondJson(status, body) },
            elapsed = { 0L },
        ).probe(BASE_URL)

    private fun clientAnswering(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = HttpClient(MockEngine(handler)) {
        expectSuccess = false
        install(ContentNegotiation) { json(matchProtocolJson) }
    }

    private fun MockRequestHandleScope.respondJson(
        status: HttpStatusCode,
        body: String,
    ): HttpResponseData = respond(
        content = body,
        status = status,
        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
    )

    private inline fun <reified T> encode(value: T) = matchProtocolJson.encodeToString(value)

    private val healthy = ServerInfo(
        name = "Test server",
        version = CURRENT_VERSION,
        minimumClient = CURRENT_VERSION,
    )

    private companion object {
        const val BASE_URL = "http://127.0.0.1:8080"

        /** Any non-zero reading. The probe reports a difference, not a benchmark. */
        const val ELAPSED = 42L
    }
}
