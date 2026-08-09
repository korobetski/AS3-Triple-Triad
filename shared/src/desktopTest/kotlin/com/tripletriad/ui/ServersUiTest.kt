package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.GameSave
import com.tripletriad.net.AccountClient
import com.tripletriad.net.MatchReporter
import com.tripletriad.net.ServerConnection
import com.tripletriad.net.ServerDirectory
import com.tripletriad.net.ServerEntry
import com.tripletriad.net.ServerProbe
import com.tripletriad.net.SessionStore
import com.tripletriad.net.matchProtocolJson
import com.tripletriad.net.serverEntries
import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.ClientPlatform
import com.tripletriad.protocol.ClientRelease
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.ServerInfo
import com.tripletriad.protocol.Session
import com.tripletriad.storage.InMemoryDocumentStore
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
import kotlinx.io.IOException
import kotlin.test.Test

/**
 * Choosing a server, and being told to update.
 *
 * These are the two things the player can actually *do* about connectivity, so they are what is
 * tested through the real screens rather than through [Connectivity] — which
 * `ConnectivityTest` already covers. What is being pinned down here is the wiring: that the menu
 * leads to the list, that the list can move the account, and that a build the server will not serve
 * is told so instead of being shown a form that cannot work.
 */
@OptIn(ExperimentalTestApi::class)
class ServersUiTest {

    /** The indicator is on the menu from the first frame, not only when something is wrong. */
    @Test
    fun theMenuNamesTheServerInPlay() = runComposeUiTest {
        setContent { App(store = english(), server = connection()) }

        awaitMenu()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible("online") }
        assertVisible("Alpha", "the menu did not name the server it is on")
    }

    /** And it is the way in — the whole reason it is tappable. */
    @Test
    fun theIndicatorOpensTheList() = runComposeUiTest {
        setContent { App(store = english(), server = connection()) }

        awaitMenu()
        onNodeWithTag(MENU_SERVER_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(SERVERS_SCREEN_TEST_TAG) }
        assertVisible("Beta", "the list did not offer the other configured server")
    }

    /**
     * A server that is down is shown as down, next to one that is up.
     *
     * The point of the list: the player is choosing *between* hosts, and both readings have to
     * be on screen at once for that to be a choice.
     */
    @Test
    fun theListShowsEachServersOwnState() = runComposeUiTest {
        setContent { App(store = english(), server = connection(secondIsDown = true)) }

        openServers()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible("unreachable") }
        assertVisible("online", "the healthy server stopped being shown as healthy")
    }

    /**
     * Choosing another server moves the app onto it.
     *
     * The menu is the assertion rather than the row's own highlight, because the menu reads the
     * directory through a different path — the indicator asks [Connectivity], the list asked
     * `AccountSession.useServer` — and agreeing is the property worth having. That the switch also
     * signs the player out is `AccountSessionTest`'s claim; this is the wiring that reaches it.
     */
    @Test
    fun choosingAnotherServerMovesTheAppOntoIt() = runComposeUiTest {
        setContent { App(store = english(), server = connection()) }

        openServers()
        onNodeWithTag(serverRowTestTag(entries[1])).performClick()
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()

        awaitMenu()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible("Beta") }
    }

    /** And with a server in play, Play still leads to that server's sign-in form. */
    @Test
    fun theSignInFormIsReachedOnWhicheverServerIsChosen() = runComposeUiTest {
        setContent { App(store = english(), server = connection()) }

        openServers()
        onNodeWithTag(serverRowTestTag(entries[1])).performClick()
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        awaitMenu()
        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(ACCOUNT_SCREEN_TEST_TAG) }
    }

    // ---- Updates ------------------------------------------------------------

    /**
     * A server that will not serve this build replaces the form rather than annotating it.
     *
     * Leaving the fields there would invite the player to type their password into something
     * guaranteed to fail and then read an error about credentials, which is the wrong diagnosis of
     * a problem the app already knows the answer to.
     */
    @Test
    fun aBuildTheServerWillNotServeIsToldToUpdateInsteadOfSigningIn() = runComposeUiTest {
        setContent { App(store = english(), server = connection(info = tooNewForThisBuild)) }

        awaitMenu()
        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(UPDATE_NOTICE_TEST_TAG) }
        check(!exists(ACCOUNT_SUBMIT_TEST_TAG)) { "the form that cannot work was left on screen" }
    }

    /** With a download published for this platform, there is one tap to it. */
    @Test
    fun aPublishedDownloadForThisPlatformIsOfferedAsAButton() = runComposeUiTest {
        setContent { App(store = english(), server = connection(info = tooNewForThisBuild)) }

        awaitMenu()
        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(UPDATE_DOWNLOAD_TEST_TAG) }
    }

    /**
     * And without one, there is no button at all.
     *
     * Absent rather than disabled: a greyed-out button implies the player is doing something wrong,
     * when the truth is that there is nothing on the other end of it.
     */
    @Test
    fun aDeploymentThatPublishesNothingOffersNoButton() = runComposeUiTest {
        val bare = tooNewForThisBuild.copy(release = null)
        setContent { App(store = english(), server = connection(info = bare)) }

        awaitMenu()
        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(UPDATE_NOTICE_TEST_TAG) }
        check(!exists(UPDATE_DOWNLOAD_TEST_TAG)) { "a download was offered with none published" }
    }

    // ---- Fixtures ------------------------------------------------------------

    /** Menu → the server list, the way the player gets there. */
    private fun ComposeUiTest.openServers() {
        awaitMenu()
        onNodeWithTag(MENU_SERVERS_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(SERVERS_SCREEN_TEST_TAG) }
    }

    private fun english() = settingsFor(AppLocale.EN_US)

    private fun connection(
        info: ServerInfo = healthy,
        secondIsDown: Boolean = false,
    ): ServerConnection {
        val engine = MockEngine { request: HttpRequestData ->
            val isSecond = request.url.port == entries[1].baseUrl.substringAfterLast(':').toInt()
            when {
                isSecond && secondIsDown -> throw IOException("Connection refused")
                request.url.encodedPath == "/server" -> respondJson(encode(info))
                request.url.encodedPath == "/me" -> respondJson(encode(player))
                else -> respondJson(encode(session))
            }
        }
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(matchProtocolJson) }
        }
        val directory = ServerDirectory(InMemoryDocumentStore(), entries)
        return ServerConnection(
            directory = directory,
            accounts = AccountClient(http, baseUrl = { directory.selected.baseUrl }),
            session = SessionStore(InMemoryDocumentStore()),
            probe = ServerProbe(http) { 0L },
            reporter = MatchReporter.None,
        )
    }

    private fun MockRequestHandleScope.respondJson(body: String): HttpResponseData = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
    )

    private inline fun <reified T> encode(value: T) = matchProtocolJson.encodeToString(value)

    private val healthy = ServerInfo(
        name = "Alpha",
        version = CURRENT_VERSION,
        minimumClient = CURRENT_VERSION,
    )

    /** A deployment a major ahead, publishing a build for every platform. */
    private val tooNewForThisBuild: ServerInfo
        get() {
            val next = AppVersion(CURRENT_VERSION.major + 1, 0, 0)
            return healthy.copy(
                minimumClient = next,
                release = ClientRelease(
                    version = next,
                    downloads = ClientPlatform.entries.associateWith { "https://example.org/get" },
                ),
            )
        }

    private val entries: List<ServerEntry> =
        serverEntries("Alpha=http://127.0.0.1:8080, Beta=http://127.0.0.1:9090")

    private companion object {
        const val TOKEN = "test-session"
        const val LATER = 1_770_086_400_000L

        val player = PlayerState(save = GameSave(username = "kuplu", mgp = 4200))
        val session = Session(token = TOKEN, expiresAt = LATER, player = player)
    }
}
