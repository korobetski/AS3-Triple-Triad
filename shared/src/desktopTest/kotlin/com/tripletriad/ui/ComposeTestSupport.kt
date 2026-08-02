package com.tripletriad.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.Board
import com.tripletriad.model.CardCollection
import com.tripletriad.model.CardColor
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.PLACEMENTS_PER_MATCH
import com.tripletriad.model.TOTAL_CARDS
import com.tripletriad.settings.InMemorySettingsStore
import com.tripletriad.settings.SettingsStore

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
 * Creates a character and lands on the opponent list.
 *
 * Goes through the real screens — menu → characters → new → create — rather than seeding a store
 * with a pre-made `.sav`. That is deliberate: a seeded store would skip the two screens most likely
 * to break, and the write it performs is what proves creation persists at all.
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
    awaitOpponents()
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitOpponents() {
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(OPPONENT_LIST_TEST_TAG) }
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
    challenge(iconId)
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.challenge(iconId: String = TEST_OPPONENT) {
    onNodeWithTag(opponentRowTestTag(iconId)).performClick()
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
    awaitPlayer()
}

/**
 * The score as (blue, red), read off the status bar.
 *
 * Lets a test work out what a placement actually *did*: the side that played gains one for its own
 * card plus one per capture, and the other side loses one per capture. So the opponent's score
 * falling is proof a capture happened — which is how `MatchAudioTest` can assert *which* placement
 * sound is right rather than only that one of the two played.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.score(): Pair<Int, Int> {
    val node = onNodeWithTag(SCORE_TEST_TAG).fetchSemanticsNode()
    val text = node.config[SemanticsProperties.Text].joinToString("") { it.text }
    val halves = text.split("—").map { it.trim() }
    check(halves.size == 2) { "the score node does not read like a score: \"$text\"" }
    return halves[0].toInt() to halves[1].toInt()
}

/**
 * How many cards are left in [owner]'s hand, counted off the screen.
 *
 * Slots close up as cards are played, so the number of `hand-<owner>-<n>` nodes *is* the hand size.
 * This is what tells a test that a placement landed: with an autonomous opponent choosing its own
 * cells, "the board changed" cannot be inferred from clicking a particular tile any more.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.handSize(owner: CardColor): Int =
    (0 until HAND_SIZE).count { exists(handCardTestTag(owner, it)) }

/**
 * How many cards are on the board, derived from what has left the two hands.
 *
 * There is no tag for "this cell is occupied" — a placed card is drawn, not written — so the hands
 * are the readable side of the same fact.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.placementsMade(): Int =
    CardColor.entries.sumOf { HAND_SIZE - handSize(it) }

/**
 * True while the player can move.
 *
 * Read off `turn-blue` rather than off the wording of the turn line, which is what this used to do:
 * "blue to play" only exists in English, so every match test was pinned to `en_US` and the two that
 * deliberately run in French and German could not ask whose turn it was at all.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.isPlayerTurn(): Boolean = exists(turnTestTag(CardColor.BLUE))

/** True once the end-of-match panel is up, which is also when the profile has been credited. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.isFinished(): Boolean = exists(MATCH_RESULT_TEST_TAG)

/**
 * Waits until it is the player's turn, or the match is over.
 *
 * This is the new shape of every match test: the opponent takes its turn on its own after a pause,
 * so a test cannot assume the turn has passed back by the time its next line runs.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitPlayer() {
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isPlayerTurn() || isFinished() }
}

/**
 * Plays one of the player's cards onto the first cell that accepts it.
 *
 * Tries cells in order and stops when the hand shrinks. Clicking an occupied cell is a no-op rather
 * than an error — `MatchScreen` guards on `board.isEmpty(position)` — which is what makes probing
 * safe, and is itself asserted by `placingOnATakenCellIsIgnored`.
 *
 * @return the cell played on.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.playOneCard(): Int {
    awaitPlayer()
    check(!isFinished()) { "the match is already over" }
    val before = handSize(CardColor.BLUE)
    onNodeWithTag(handCardTestTag(CardColor.BLUE, 0)).performClick()
    for (position in 0 until Board.SIZE) {
        onNodeWithTag(tileTestTag(position)).performClick()
        waitForIdle()
        if (handSize(CardColor.BLUE) < before) return position
    }
    error("no cell accepted a card; the board looks full but the match is not over")
}

/**
 * Plays the match to the end, letting the opponent take its own turns.
 *
 * The player places five cards or four depending on the coin flip, so this loops on "is it over"
 * rather than on a count.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.playOut() {
    var moves = 0
    while (!isFinished()) {
        check(moves <= PLACEMENTS_PER_MATCH) { "played $moves times and the match has not ended" }
        playOneCard()
        moves++
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isPlayerTurn() || isFinished() }
    }
}

/**
 * True when the score line sums to [TOTAL_CARDS].
 *
 * Matched on the score node with an **exact** text comparison, not a substring anywhere on screen:
 * the line reads `5 — 5`, and `"0 — 0"` is a substring of `"10 — 0"`.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.totalIsTen(): Boolean =
    (0..TOTAL_CARDS).any { blue ->
        onAllNodes(hasTestTag(SCORE_TEST_TAG) and hasText("$blue — ${TOTAL_CARDS - blue}"))
            .fetchSemanticsNodes().isNotEmpty()
    }
