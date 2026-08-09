package com.tripletriad.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import com.tripletriad.net.StoredSession
import com.tripletriad.net.matchProtocolJson
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.PlayerState
import com.tripletriad.protocol.ServerInfo
import com.tripletriad.protocol.Session
import com.tripletriad.storage.InMemoryDocumentStore
import com.tripletriad.time.FixedClock
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
import androidx.compose.ui.autofill.ContentType as AutofillType

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

    /**
     * When the form *is* reached, half of it is already filled in.
     *
     * An expired token is the ordinary way back here — thirty days pass — and the app still knows
     * who this was. Asking them to type it again would be asking for something it has on disk.
     */
    @Test
    fun anExpiredSessionLeavesTheNameInTheForm() = runComposeUiTest {
        val documents = InMemoryDocumentStore()
        runBlocking {
            SessionStore(documents).save(
                home.id,
                StoredSession(token = TOKEN, expiresAt = EXPIRED, username = "kuplu"),
            )
        }

        setContent { App(store = english(), server = connection(sessions = documents)) }

        // The form, and not the dashboard: the token is dead, so this is a sign-in and not a
        // restore. Both halves matter — a passing assertion below with a *live* token would only
        // prove the screen was never reached.
        openForm()
        onNodeWithTag(ACCOUNT_NAME_TEST_TAG).assertTextContains("kuplu")
    }

    /** And with nothing stored, the field is empty rather than holding somebody else's name. */
    @Test
    fun aFirstRunLeavesTheFormBlank() = runComposeUiTest {
        setContent { App(store = english(), server = connection()) }

        openForm()
        // The label, then the empty value — `assertTextEquals` reads both, and the label is stable
        // because these tests fix the locale.
        onNodeWithTag(ACCOUNT_NAME_TEST_TAG).assertTextEquals("Character Name", "")
    }

    /**
     * The other half of not typing this: the fields tell the platform what they are.
     *
     * This is what lets the OS password manager offer to save the password and fill it back in —
     * and it is the whole reason the app stores no password of its own. Worth a test because the
     * hint is invisible: nothing on screen changes if it is dropped, and the failure is silent and
     * only reproducible on a device.
     */
    @Test
    fun theFieldsTellThePasswordManagerWhatTheyAre() = runComposeUiTest {
        setContent { App(store = english(), server = connection()) }

        openForm()
        onNodeWithTag(ACCOUNT_NAME_TEST_TAG).assertContentType(AutofillType.Username)
        onNodeWithTag(ACCOUNT_PASSWORD_TEST_TAG).assertContentType(AutofillType.Password)
    }

    /**
     * And registering asks for a *new* one, which is a different request.
     *
     * `Password` asks the manager to fill what it has; `NewPassword` asks it to offer to generate
     * and save. Getting these the wrong way round is how a password manager ends up either silent
     * on the form that needs it or overwriting a stored entry on the form that does not.
     */
    @Test
    fun creatingAnAccountAsksForANewPasswordInstead() = runComposeUiTest {
        setContent { App(store = english(), server = connection()) }

        openForm()
        onNodeWithTag(ACCOUNT_TOGGLE_TEST_TAG).performClick()

        onNodeWithTag(ACCOUNT_NAME_TEST_TAG).assertContentType(AutofillType.NewUsername)
        onNodeWithTag(ACCOUNT_PASSWORD_TEST_TAG).assertContentType(AutofillType.NewPassword)
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
     * The refusal is a sentence in the player's language, not a key and not English.
     *
     * This screen's strings were hard-coded English until the sign-in form was the only part of the
     * app that could not be read in French. `INVALID_CREDENTIALS` is asserted specifically because
     * it is the refusal a real player meets — a typed password — and because its wording comes from
     * a bundle now rather than from a `when` branch.
     */
    @Test
    fun aRefusalIsWordedInThePlayersLanguage() = runComposeUiTest {
        val refusing = MockEngine {
            respondJson(
                HttpStatusCode.Unauthorized,
                """{"error":"INVALID_CREDENTIALS","detail":"no"}""",
            )
        }
        setContent {
            App(store = settingsFor(AppLocale.FR_FR), server = connection(engine = refusing))
        }

        openForm()
        submitCredentials()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(ACCOUNT_ERROR_TEST_TAG) }
        assertVisible(
            "Ce nom et ce mot de passe ne correspondent à aucun compte.",
            "the refusal should be in French",
        )
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

    /**
     * The autofill hint this node declares. Not a text assertion — the value is not rendered.
     *
     * `AutofillType` is `androidx.compose.ui.autofill.ContentType`, aliased because Ktor's
     * `ContentType` is a MIME type and this file needs both.
     */
    private fun SemanticsNodeInteraction.assertContentType(expected: AutofillType) =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentType, expected))

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

        /**
         * A moment [App]'s clock has already passed.
         *
         * Anchored to [FixedClock.DEFAULT_MILLIS] rather than to [NOW], because the clock is what
         * decides expiry and `App`'s default is the one these tests run against. An `EXPIRED`
         * derived from [NOW] would be a number that happens to work until somebody changes either.
         */
        const val EXPIRED = FixedClock.DEFAULT_MILLIS - 1

        /** Never a real one, and never printed. */
        const val PASSWORD = "not-a-real-password"

        val player = PlayerState(save = GameSave(username = "kuplu", mgp = 4200))
        val session = Session(token = TOKEN, expiresAt = LATER, player = player)
    }
}
