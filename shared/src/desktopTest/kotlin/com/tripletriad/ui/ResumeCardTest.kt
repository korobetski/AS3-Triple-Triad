package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.loadStrings
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The menu's resume card — the visible half of a feature that has always worked silently.
 *
 * A stored token is restored on launch and the sign-in form is simply never shown, which from the
 * outside is indistinguishable from the form being broken. These assert the three things the card
 * exists to make distinguishable: signed in, still asking, and out of the thirty days.
 *
 * Driven against [MainMenuScreen] directly rather than through [App], because a `RememberedAccount`
 * needs a server and the full-app tests deliberately run on an offline build — there is no way to
 * reach a lapsed session through the real launch path without standing up a host that refuses a
 * token. What the card does with each state is the part with decisions in it, and it is all here;
 * where the states come from is `rememberedAccount`, three lines of `when`.
 */
@OptIn(ExperimentalTestApi::class)
class ResumeCardTest {
    @Test
    fun aRestoredSessionSaysSoAndOffersToContinue() = runComposeUiTest {
        var went = 0
        setContent { Menu(account("Kaelith", SessionState.RESTORED, onGo = { went += 1 })) }

        onNodeWithTag(MENU_RESUME_STATE_TEST_TAG).assertTextEquals("Signed in")
        onNodeWithTag(MENU_RESUME_GO_TEST_TAG).assertTextEquals("Continue")
        onNodeWithTag(MENU_RESUME_GO_TEST_TAG).performClick()
        assertEquals(1, went, "continuing should be one action, not a sign-in")
    }

    /**
     * The lapsed card offers a sign-in and not a "continue", because there is nothing to continue:
     * the app stores no password, so a token past its thirty days leaves it with a name and no way
     * to prove it. Saying so is the whole point — silence here reads as the app having forgotten.
     */
    @Test
    fun aLapsedSessionSaysSoAndOffersToSignInAgain() = runComposeUiTest {
        setContent { Menu(account("Kaelith", SessionState.LAPSED)) }

        onNodeWithTag(MENU_RESUME_STATE_TEST_TAG).assertTextEquals("Session expired")
        onNodeWithTag(MENU_RESUME_GO_TEST_TAG).assertTextEquals("Sign in again")
    }

    /** Nothing to press while the round trip is out — disabled, not absent. See `ResumeCard`. */
    @Test
    fun aSessionStillBeingRestoredSaysSoAndCannotBePressed() = runComposeUiTest {
        setContent { Menu(account("Kaelith", SessionState.CONNECTING)) }

        onNodeWithTag(MENU_RESUME_STATE_TEST_TAG).assertTextEquals("Reconnecting…")
        onNodeWithTag(MENU_RESUME_GO_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun switchingAccountIsOfferedBesideContinuing() = runComposeUiTest {
        var switched = 0
        setContent {
            Menu(account("Kaelith", SessionState.RESTORED, onSwitch = { switched += 1 }))
        }

        onNodeWithTag(MENU_RESUME_SWITCH_TEST_TAG).performClick()
        assertEquals(1, switched, "the card should offer a way out of this account")
    }

    /** No card at all when the app remembers nobody — an offline build, or a fresh install. */
    @Test
    fun rememberingNobodyDrawsNoCard() = runComposeUiTest {
        setContent { Menu(remembered = null) }

        assertFalse(exists(MENU_RESUME_TEST_TAG), "there is nobody to resume")
        assertTrue(exists(MENU_PLAY_TEST_TAG), "and the menu is otherwise itself")
    }

    private fun account(
        username: String,
        state: SessionState,
        onGo: () -> Unit = {},
        onSwitch: () -> Unit = {},
    ) = RememberedAccount(username, state, onGo, onSwitch)
}

/**
 * The menu with everything but the card stubbed out.
 *
 * `connectivity = null` removes the server line, which needs a live directory — the card is what is
 * under test and it does not read one.
 */
@Composable
private fun Menu(remembered: RememberedAccount?) {
    val strings = remember { runBlocking { loadStrings(AppLocale.EN_US) } }
    TripleTriadTheme {
        CompositionLocalProvider(LocalStrings provides strings) {
            MainMenuScreen(
                active = null,
                remembered = remembered,
                connectivity = null,
                onPlay = {},
                onProfiles = {},
                onServers = {},
                onOptions = {},
                onQuit = {},
            )
        }
    }
}
