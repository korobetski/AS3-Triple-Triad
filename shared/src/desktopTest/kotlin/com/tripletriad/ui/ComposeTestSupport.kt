package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.tripletriad.data.SaveRepository
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.CardCollection
import com.tripletriad.model.GameSave
import com.tripletriad.settings.InMemorySettingsStore
import com.tripletriad.settings.SettingsStore
import com.tripletriad.storage.InMemoryDocumentStore
import kotlinx.coroutines.runBlocking

/** How long to allow for an animation, a resource load or the opponent's turn before failing. */
internal const val UI_TIMEOUT_MS = 10_000L

/**
 * The opponent every match test challenges unless it says otherwise.
 *
 * `tt-master` is the first row of the `ff14` list at the default clock's hour 12 — difficulty 1,
 * 5 MGP fee — and it imposes **`RULE_ALL_OPEN` and nothing else**. Both halves of that matter: only
 * the basic capture rule is in force, so a test reasoning about which card beats which does not
 * have to account for Same or Reverse; and All Open means the opponent's hand is face up, so a test
 * can still find `hand-red-<n>` by tag. A rule-bearing opponent is chosen explicitly where that is
 * the point.
 */
internal const val TEST_OPPONENT = "tt-master"

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.isVisible(text: String): Boolean =
    onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.assertVisible(text: String, message: String) {
    check(isVisible(text)) { "$message (no node containing \"$text\")" }
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.exists(tag: String): Boolean =
    onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

/**
 * [exists], reading the unmerged tree.
 *
 * A `clickable` row **merges its descendants' semantics**, so a tag on something inside one is
 * absorbed into the row's own node and invisible to an ordinary finder. Where the tagged thing is a
 * click target that has to be fixed in the composable — see `deckPositionTestTag` — but where it is
 * only content the test wants to observe, reading unmerged is the honest way to ask.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.existsUnmerged(tag: String): Boolean =
    onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

/**
 * A store that pins the language, so a test never inherits the machine's own locale.
 *
 * Going through the settings *file* rather than a parameter is strictly better: it is the path the
 * app really takes, so these tests also cover `UserSettingsRepository` reading a language and the
 * whole tree rendering in it.
 */
internal fun settingsFor(locale: AppLocale): SettingsStore =
    InMemorySettingsStore("""{"language":"${locale.tag}"}""")

/**
 * Blocks until the splash finishes and the main menu is up.
 *
 * The menu appearing is the signal that every startup phase completed — settings, `cards.json`, the
 * nineteen shared textures and `npcs.json` — so any test that calls this also covers resource
 * packaging: it times out here if any of them is dropped from the bundle. What the bundles
 * *contain* is `CardBundleTest`'s and `NpcBundleTest`'s business.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitMenu() {
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(MENU_PLAY_TEST_TAG) }
}

/**
 * Creates a character and lands on its **dashboard**.
 *
 * Goes through the real screens — menu → characters → new → create — rather than seeding a store
 * with a pre-made `.sav`. That is deliberate: a seeded store would skip the two screens most likely
 * to break, and the write it performs is what proves creation persists at all.
 *
 * The dashboard, and not the opponent list, because that is where creation now leads: every screen
 * a loaded character can reach hangs off it. [openOpponents] is the next hop for a test that wants
 * to play.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.newCharacter(collection: CardCollection = CardCollection.FF14) {
    awaitMenu()
    onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_NEW_TEST_TAG) }
    onNodeWithTag(PROFILE_NEW_TEST_TAG).performClick()
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_CREATE_TEST_TAG) }
    onNodeWithTag(collectionChoiceTestTag(collection)).performClick()
    onNodeWithTag(PROFILE_CREATE_TEST_TAG).performClick()
    awaitDashboard()
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitDashboard() {
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DASHBOARD_PLAY_TEST_TAG) }
}

/**
 * Writes [save] to [documents] before the app reads it. Returns the store, so a test can seed and
 * hand it to `App` in one expression.
 *
 * The bag, the collection and the purse are what the four dashboard screens are *about*, and a
 * character created through the UI has one of each fixed by `setToDefaultValues()`. Playing matches
 * until a profile happens to hold a booster pack is not a test, so these seed the file directly —
 * through the real [SaveRepository], so what lands on "disk" is a real obfuscated `.sav`.
 */
internal fun seeded(save: GameSave): InMemoryDocumentStore {
    val documents = InMemoryDocumentStore()
    runBlocking { SaveRepository(documents).save(save, at = 0L) }
    return documents
}

/** Opens the dashboard of the one character in [documents]. Pairs with [seeded]. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.loadCharacter(documents: InMemoryDocumentStore) {
    awaitMenu()
    onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }
    onNodeWithTag(profileRowTestTag(documents.stored.keys.single())).performClick()
    awaitDashboard()
}

/** The one profile on "disk", decoded — so a test asserts the file and not the screen's copy. */
internal fun storedSave(documents: InMemoryDocumentStore): GameSave =
    runBlocking { SaveRepository(documents).list().single().save }

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitOpponents() {
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(OPPONENT_LIST_TEST_TAG) }
}

/** The dashboard's Play, which is the only way to the opponent list. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.openOpponents() {
    onNodeWithTag(DASHBOARD_PLAY_TEST_TAG).performClick()
    awaitOpponents()
}

/**
 * Opens one of the dashboard's own cards and waits for something on the screen behind it.
 *
 * Four things: the decks, the bag, the record and the rules. The collection and the shelf are
 * **not** among them — they are navigation-bar destinations now, and [openFromBar] is how a test
 * reaches one. See `DashboardScreen` for why the home screen stopped listing them.
 *
 * @param entry the dashboard card's tag, @param landmark a tag only the screen behind it has.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.openFromDashboard(entry: String, landmark: String) {
    onNodeWithTag(entry).performClick()
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(landmark) }
}

/**
 * Taps a navigation-bar entry and waits for something on the screen it leads to.
 *
 * @param tab the lower-cased [Tab] name — `home`, `play`, `cards`, `store`.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.openFromBar(tab: String, landmark: String) {
    onNodeWithTag(navTestTag(tab)).performClick()
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(landmark) }
}

/** Back to the dashboard from any screen it opened. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.backToDashboard() {
    onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
    awaitDashboard()
}

/**
 * Creates a character, challenges [iconId], and waits for the board.
 *
 * Every match test starts here and goes through the menu rather than around it — a shortcut
 * straight to `MatchScreen` would stop the tests noticing if Play ever stopped leading anywhere.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.startMatch(
    iconId: String = TEST_OPPONENT,
    collection: CardCollection = CardCollection.FF14,
) {
    newCharacter(collection)
    openOpponents()
    challenge(iconId)
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.challenge(iconId: String = TEST_OPPONENT) {
    onNodeWithTag(opponentRowTestTag(iconId)).performClick()
    settleDeck()
    awaitPlayer()
}

/**
 * Gets past the deck selector, however it happens to be resolved, and waits for the board.
 *
 * Three outcomes have to be tolerated because all three are real: the selector is up with a deck to
 * confirm, it is up with **no complete deck** — so only Random works — or it never appears at all,
 * which is what `RULE_RANDOM` does. A test that is not about deck selection should not have to know
 * which of the three its opponent produces.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.settleDeck() {
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
        exists(DECK_SELECT_CHOOSE_TEST_TAG) || exists(BOARD_TEST_TAG)
    }
    if (exists(DECK_SELECT_EMPTY_TEST_TAG)) {
        onNodeWithTag(DECK_SELECT_RANDOM_TEST_TAG).performClick()
    } else if (exists(DECK_SELECT_CHOOSE_TEST_TAG)) {
        onNodeWithTag(DECK_SELECT_CHOOSE_TEST_TAG).performClick()
    }
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
}

/**
 * A level high enough that [com.tripletriad.data.NpcCatalog.available]'s gate cannot bite.
 *
 * The tests that pass it are about the **hour** window or about a named opponent, and would
 * otherwise be asserting the level rule by accident. `OpponentUiTest` tests the gate itself.
 */
internal const val ANY_LEVEL: Int = 99
