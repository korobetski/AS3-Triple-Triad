package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.loadStrings
import com.tripletriad.settings.InMemorySettingsStore
import com.tripletriad.settings.SettingsStore
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Splash → menu → match / options, driven through the real [App].
 *
 * Everything here is asserted on what is *on screen*, never on the `Screen` value: the enum being
 * right while nothing changed is exactly the failure worth catching.
 */
@OptIn(ExperimentalTestApi::class)
class NavigationTest {
    /**
     * While startup is incomplete, the splash is up and says which phase it is on.
     *
     * Held there by a store that never answers, rather than by asserting on the first frame of a
     * normal start: `runComposeUiTest` drains coroutines around every interaction, so by the time
     * the first assertion runs a healthy startup has already finished and the menu is up. A test
     * that "passed" on timing would prove nothing about the splash.
     */
    @Test
    fun theSplashHoldsWhileStartupIsUnfinishedAndNamesItsPhase() = runComposeUiTest {
        setContent { App(store = NeverAnswers) }
        waitForIdle()

        assertFalse(isVisible("Play"), "the menu was up although startup never finished")
        onNodeWithTag(SPLASH_PHASE_TEST_TAG).assertTextEquals(SPLASH_LINES.first())
    }

    /** Every phase resolves to a real string, not an `APP_STARTUP_*` key leaking through. */
    @Test
    fun everyPhaseHasAStringRatherThanItsKey() {
        val strings = runBlocking { loadStrings(AppLocale.EN_US) }
        assertEquals(SPLASH_LINES.size, StartupPhase.entries.size, "one line per phase")
        for (phase in StartupPhase.entries) {
            val line = strings[phase.labelKey]
            assertTrue(line in SPLASH_LINES, "$phase resolved to \"$line\"")
        }
    }

    @Test
    fun theSplashGivesWayToTheMenuOnItsOwn() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }

        awaitMenu()

        onNodeWithTag(MENU_PLAY_TEST_TAG).assertTextEquals("Play")
        onNodeWithTag(MENU_OPTIONS_TEST_TAG).assertTextEquals("Options")
        onNodeWithTag(MENU_QUIT_TEST_TAG).assertTextEquals("Quit")
    }

    /**
     * With no character, Play leads to the character list — the original's Load Game.
     *
     * Asserted on the empty-list message rather than on the list node: an empty `LazyColumn`
     * renders nothing, so "the list exists" would be the weaker claim of the two.
     */
    @Test
    fun playWithNoCharacterAsksForOne() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        awaitMenu()
        onNodeWithTag(
            MENU_PROFILE_TEST_TAG,
        ).assertTextEquals("No character yet — create one to play.")

        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_EMPTY_TEST_TAG) }

        onNodeWithTag(PROFILE_NEW_TEST_TAG).assertTextEquals("New Game")
    }

    /**
     * The whole line: menu → characters → new → dashboard → opponents → board, and back out again.
     *
     * Four hops back rather than three, which is the dashboard's whole cost — and the reason it is
     * worth it is that the seven screens hanging off it have somewhere to hang.
     */
    @Test
    fun playReachesABoardAndTheChevronComesBack() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()

        onNodeWithTag(MATCH_EXIT_TEST_TAG).performClick()
        awaitOpponents()

        backToDashboard()

        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }

        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        awaitMenu()

        onNodeWithTag(MENU_PLAY_TEST_TAG).assertTextEquals("Play")
    }

    /** Once a character is loaded, Play skips the list — the original's Continue. */
    @Test
    fun playWithACharacterLoadedGoesStraightToItsDashboard() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        awaitMenu()

        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
        awaitDashboard()
    }

    /**
     * Every card on the home screen opens the screen it names, and its chevron comes back.
     *
     * The routing table's own test: eight destinations that each existed as a file and none of
     * which was reachable until [Screen] grew them. Multiplayer is deliberately absent — it is
     * drawn disabled, and Phase 5 is what turns it on. The collection and the shelf are absent for
     * a different reason: they moved to the navigation bar, and
     * [everyNavigationBarEntryOpensItsScreen] is where they are asserted.
     */
    @Test
    fun everyDashboardEntryOpensItsScreen() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        val entries = listOf(
            DASHBOARD_PLAY_TEST_TAG to OPPONENT_LIST_TEST_TAG,
            DASHBOARD_STATS_TEST_TAG to STATS_TABLE_TEST_TAG,
            DASHBOARD_DECKS_TEST_TAG to DECK_LIST_TEST_TAG,
            DASHBOARD_INVENTORY_TEST_TAG to INVENTORY_EMPTY_TEST_TAG,
            DASHBOARD_HELP_TEST_TAG to HELP_LIST_TEST_TAG,
        )
        for ((entry, landmark) in entries) {
            openFromDashboard(entry, landmark)
            backToDashboard()
        }
    }

    /**
     * The four navigation-bar destinations, and that back from each of them lands on Home.
     *
     * The second half is the claim worth pinning. A tabbed shell normally needs a back stack
     * because "up" stops being a property of a screen; here it does not, because every one of these
     * roots already had the dashboard as its `Screen.up` — see [Tab]. This is what says that is
     * still true, and it would fail the day a tab root is given a different parent.
     */
    @Test
    fun everyNavigationBarEntryOpensItsScreen() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        val entries = listOf(
            "play" to OPPONENT_LIST_TEST_TAG,
            "cards" to CARD_GRID_TEST_TAG,
            "store" to SHOP_LIST_TEST_TAG,
        )
        for ((tab, landmark) in entries) {
            openFromBar(tab, landmark)
            backToDashboard()
        }

        // And Home is on the bar too, from wherever the player happens to be.
        openFromBar("store", SHOP_LIST_TEST_TAG)
        openFromBar("home", DASHBOARD_PLAY_TEST_TAG)
    }

    /** The board is immersive: no bar over it, and none on the menu either. */
    @Test
    fun theNavigationBarIsAbsentOnTheMenuAndDuringAMatch() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        awaitMenu()
        assertFalse(exists(navTestTag("home")), "the menu has no character and so no bar")

        startMatch()
        assertFalse(exists(navTestTag("home")), "a match should not be leavable by a bar entry")
    }

    /** Logout leaves the character behind and lands where another is chosen. */
    @Test
    fun logoutReturnsToTheCharacterList() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        onNodeWithTag(DASHBOARD_LOGOUT_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }
    }

    @Test
    fun optionsOpensAndBackReturnsToTheMenu() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        awaitMenu()

        onNodeWithTag(MENU_OPTIONS_TEST_TAG).performClick()
        waitForIdle()
        assertTrue(isVisible("Language"), "the options screen did not open")

        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitForIdle()

        onNodeWithTag(MENU_PLAY_TEST_TAG).assertTextEquals("Play")
    }

    /**
     * The reason `onQuit` is a parameter at all: `:shared` cannot leave an app, and must not try.
     */
    @Test
    fun quitCallsTheHostRatherThanDoingAnythingItself() = runComposeUiTest {
        var quits = 0
        setContent { App(store = settingsFor(AppLocale.EN_US), onQuit = { quits++ }) }
        awaitMenu()

        onNodeWithTag(MENU_QUIT_TEST_TAG).performClick()
        waitForIdle()

        assertEquals(1, quits)
        onNodeWithTag(MENU_PLAY_TEST_TAG).assertTextEquals("Play")
    }

    /** A menu in the language the settings file names, not the machine's. */
    @Test
    fun theMenuIsInTheStoredLanguage() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.FR_FR)) }
        awaitMenu()

        onNodeWithTag(MENU_PLAY_TEST_TAG).assertTextEquals("Jouer")
        onNodeWithTag(MENU_QUIT_TEST_TAG).assertTextEquals("Quitter")
    }

    /**
     * A first run — no settings file — still reaches the menu.
     *
     * The language then comes from the machine, so nothing here asserts on wording; what is being
     * pinned is that a missing file is not a stall on the splash.
     */
    @Test
    fun aFirstRunWithNoSettingsFileStillStarts() = runComposeUiTest {
        val store = InMemorySettingsStore()
        setContent { App(store = store) }

        awaitMenu()

        assertEquals(1, store.writes, "the first run should have persisted a file")
    }

    /** An unreadable store must not be able to hang the splash. */
    @Test
    fun aStoreThatThrowsDoesNotStrandTheSplash() = runComposeUiTest {
        setContent {
            App(store = InMemorySettingsStore(failure = IllegalStateException("no permission")))
        }

        awaitMenu()
    }

    /**
     * A store stuck on its first read, so the splash cannot leave `StartupPhase.SETTINGS`.
     *
     * Note this is *not* the same as a store that throws — `aStoreThatThrowsDoesNotStrandTheSplash`
     * covers that, and the app must recover from it. This one models a read that simply never
     * returns, which is the only way to observe the splash in a fixed phase.
     */
    private object NeverAnswers : SettingsStore {
        override suspend fun read(): String? = awaitCancellation()

        override suspend fun write(text: String) = awaitCancellation()
    }

    private companion object {
        /** `app-en_US.json`, in `StartupPhase` order. */
        val SPLASH_LINES = listOf(
            "reading settings…",
            "loading cards…",
            "loading artwork…",
            "loading opponents…",
            "ready",
        )
    }
}
