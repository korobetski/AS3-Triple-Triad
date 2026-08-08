package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.GameSave
import com.tripletriad.net.AccountClient
import com.tripletriad.net.MatchReporter
import com.tripletriad.net.ServerConnection
import com.tripletriad.net.ServerDirectory
import com.tripletriad.net.ServerEntry
import com.tripletriad.net.ServerProbe
import com.tripletriad.net.SessionStore
import com.tripletriad.net.StoredSession
import com.tripletriad.net.matchProtocolJson
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.ServerInfo
import com.tripletriad.protocol.Session
import com.tripletriad.storage.InMemoryDocumentStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * The flow a build with a server actually has.
 *
 * The claim being tested is the one the whole feature rests on: **with a server, the character is
 * the account's**. So Play must lead to a sign-in form rather than to the local character list,
 * signing in must land on that account's dashboard, and a stored token must skip the form entirely
 * on the next launch. The thirteen screens behind the dashboard are `NavigationTest`'s business and
 * are not re-tested here; what is tested is that they are now reached from somewhere else.
 */
@OptIn(ExperimentalTestApi::class)
class AccountUiTest {

    /**
     * Without a server, nothing about the flow changes.
     *
     * The regression worth guarding: an offline build is a supported build, and none of this work
     * may have turned every preview, screenshot and UI test into a sign-in form.
     */
    @Test
    fun withNoServerPlayStillLeadsToTheLocalCharacterList() = runComposeUiTest {
        setContent { App(store = english()) }

        awaitMenu()
        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_NEW_TEST_TAG) }
    }

    @Test
    fun withAServerPlayLeadsToTheSignInForm() = runComposeUiTest {
        setContent { App(store = english(), server = connection()) }

        openForm()
    }

    /** Typing in the two fields and pressing the button ends on the account's own dashboard. */
    @Test
    fun signingInLandsOnTheAccountsDashboard() = runComposeUiTest {
        setContent { App(store = english(), server = connection()) }

        openForm()
        submitCredentials()

        awaitDashboard()
        assertVisible("kuplu", "the dashboard did not show the account's character")
    }

    /**
     * The reason the token is stored at all.
     *
     * A returning player must not be shown a form they already filled in — which is also why the
     * splash waits for the restore rather than racing it.
     */
    @Test
    fun aStoredSessionSkipsTheFormOnTheNextLaunch() = runComposeUiTest {
        val documents = InMemoryDocumentStore()
        runBlocking {
            SessionStore(documents).save(
                home.id,
                StoredSession(token = TOKEN, expiresAt = LATER, username = "kuplu"),
            )
        }

        setContent { App(store = english(), server = connection(sessions = documents)) }

        awaitMenu()
        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()

        // Straight past `Screen.ACCOUNT`: the profile was restored before the splash ended, so
        // Play is Continue.
        awaitDashboard()
    }

    /** A refusal keeps the player on the form, with the reason on it. */
    @Test
    fun aRefusedSignInStaysOnTheFormAndSaysWhy() = runComposeUiTest {
        val refusing = MockEngine {
            respondJson(
                HttpStatusCode.Unauthorized,
                """{"error":"INVALID_CREDENTIALS","detail":"no"}""",
            )
        }
        setContent { App(store = english(), server = connection(engine = refusing)) }

        openForm()
        submitCredentials()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(ACCOUNT_ERROR_TEST_TAG) }
        check(exists(ACCOUNT_SCREEN_TEST_TAG)) { "a refused sign-in left the form" }
    }

    /**
     * The button stays inert until the two fields could possibly be valid.
     *
     * Not a security measure — the server checks the same rules and is the only check that counts.
     * It is here so a password that is too short is a disabled button rather than a round trip.
     */
    @Test
    fun theFormWillNotSubmitCredentialsTheServerWouldRefuse() = runComposeUiTest {
        var asked = false
        val engine = MockEngine { request ->
            // The probe is not the form. `GET /server` is made by the menu's indicator on every
            // launch and asks nothing about the player, so counting it here would make this test
            // fail for a request that carries no credentials at all.
            if (request.url.encodedPath != "/server") asked = true
            respondJson(HttpStatusCode.OK, encode(session))
        }
        setContent { App(store = english(), server = connection(engine = engine)) }

        openForm()
        onNodeWithTag(ACCOUNT_NAME_TEST_TAG).performTextInput("ku")
        onNodeWithTag(ACCOUNT_PASSWORD_TEST_TAG).performTextInput("short")
        onNodeWithTag(ACCOUNT_SUBMIT_TEST_TAG).performClick()
        waitForIdle()

        check(exists(ACCOUNT_SCREEN_TEST_TAG)) { "the form navigated away" }
        check(!asked) { "the form sent credentials it had already judged invalid" }
    }

    /** Registering is the same form with the toggle flipped, and it reaches the same dashboard. */
    @Test
    fun registeringLandsOnTheDashboardToo() = runComposeUiTest {
        val creating = MockEngine { respondJson(HttpStatusCode.Created, encode(session)) }
        setContent { App(store = english(), server = connection(engine = creating)) }

        openForm()
        onNodeWithTag(ACCOUNT_TOGGLE_TEST_TAG).performClick()
        submitCredentials()

        awaitDashboard()
    }

    // ---- Fixtures ---------------------------------------------------------

    private fun ComposeUiTest.openForm() {
        awaitMenu()
        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(ACCOUNT_SCREEN_TEST_TAG) }
    }

    private fun ComposeUiTest.submitCredentials() {
        onNodeWithTag(ACCOUNT_NAME_TEST_TAG).performTextInput("kuplu")
        onNodeWithTag(ACCOUNT_PASSWORD_TEST_TAG).performTextInput(PASSWORD)
        onNodeWithTag(ACCOUNT_SUBMIT_TEST_TAG).performClick()
    }

    /**
     * A server that says yes: the same account, whether it is signed into or asked about.
     *
     * The two shapes are distinguished because they genuinely differ — `/me` answers a
     * `PlayerState` and the rest answer a `Session` wrapping one — and a fixture that returned the
     * wrong one would fail the restore silently, which is exactly the bug these tests are for.
     * Which endpoint was called is `AccountClientTest`'s question; what these need is a live one.
     */
    private fun connection(
        sessions: InMemoryDocumentStore = InMemoryDocumentStore(),
        engine: MockEngine = MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/server" -> encode(serverInfo)
                "/me" -> encode(player)
                else -> encode(session)
            }
            respondJson(HttpStatusCode.OK, body)
        },
    ): ServerConnection {
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(matchProtocolJson) }
        }
        val directory = ServerDirectory(InMemoryDocumentStore(), listOf(home))
        return ServerConnection(
            directory = directory,
            accounts = AccountClient(http, baseUrl = { directory.selected.baseUrl }),
            session = SessionStore(sessions),
            probe = ServerProbe(http) { 0L },
            reporter = MatchReporter.None,
        )
    }

    private fun english() = settingsFor(AppLocale.EN_US)

    /** The mock engine's `respond` with the one header Ktor's negotiation needs to decode. */
    private fun MockRequestHandleScope.respondJson(
        status: HttpStatusCode,
        body: String,
    ): HttpResponseData = respond(
        content = body,
        status = status,
        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
    )

    private inline fun <reified T> encode(value: T) = matchProtocolJson.encodeToString(value)

    /**
     * A healthy server of exactly this version.
     *
     * Answered for `/server` so the menu's indicator has something real to render and so no update
     * notice appears — a required one replaces the sign-in form, which would break every test in
     * this file for the wrong reason. That it *would* is the point of `AccountScreen`'s branch.
     */
    private val serverInfo = ServerInfo(
        name = "Test server",
        version = CURRENT_VERSION,
        minimumClient = CURRENT_VERSION,
    )

    /** The one server these tests run against. */
    private val home = ServerEntry.of("http://127.0.0.1:8080", label = "Test server")

    private companion object {
        const val TOKEN = "test-session"
        const val NOW = 1_770_000_000_000L
        const val LATER = NOW + 86_400_000L

        /** Never a real one, and never printed. */
        const val PASSWORD = "not-a-real-password"

        val player = PlayerState(save = GameSave(username = "kuplu", mgp = 4200))
        val session = Session(token = TOKEN, expiresAt = LATER, player = player)
    }
}
