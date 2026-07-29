package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.CardColor
import com.tripletriad.model.PLACEMENTS_PER_MATCH
import com.tripletriad.model.TOTAL_CARDS
import com.tripletriad.settings.InMemorySettingsStore
import com.tripletriad.settings.SettingsStore

/** How long to allow for an animation or a resource load before failing a test. */
internal const val UI_TIMEOUT_MS = 10_000L

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.isVisible(text: String): Boolean =
    onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.assertVisible(text: String, message: String) {
    check(isVisible(text)) { "$message (no node containing \"$text\")" }
}

/**
 * A store that pins the language, so a test never inherits the machine's own locale.
 *
 * This is what `App`'s old `locale` parameter was for. Going through the settings *file* instead
 * is strictly better: it is the path the app really takes, so these tests now also cover
 * `UserSettingsRepository` reading a language and the whole tree rendering in it.
 */
internal fun settingsFor(locale: AppLocale): SettingsStore =
    InMemorySettingsStore("""{"language":"${locale.tag}"}""")

/**
 * Blocks until the splash finishes and the main menu is up.
 *
 * The menu appearing is the signal that every startup phase completed — settings, `cards.json`
 * and the nineteen shared textures — so any test that calls this also covers resource packaging:
 * it times out here if the JSON or the art is dropped from the bundle. What the bundle *contains*
 * is `CardBundleTest`'s business.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitMenu() {
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
        onAllNodesWithTag(MENU_PLAY_TEST_TAG).fetchSemanticsNodes().isNotEmpty()
    }
}

/**
 * Waits out the splash, presses Play, and waits for the board.
 *
 * Every match test starts here now, and goes through the menu rather than around it — a shortcut
 * that skipped straight to `MatchScreen` would stop the tests noticing if Play ever stopped
 * leading anywhere.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.startMatch() {
    awaitMenu()
    onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
        onAllNodesWithTag(BOARD_TEST_TAG).fetchSemanticsNodes().isNotEmpty()
    }
}

/**
 * Which side is to play, read off the turn line rather than the model.
 *
 * The line reads either "blue to play — pick a card" or "blue: <card> — pick a cell", so the
 * colour word plus a following ":" or " to play" identifies it unambiguously.
 *
 * That is `app-en_US.json` wording, which is why every test pins `AppLocale.EN_US`. Reading the
 * side off the screen rather than off the model is deliberate — it is the only way a test can
 * catch the turn line and the turn disagreeing — and the cost is this coupling to one locale.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.sideToPlay(): CardColor =
    if (isVisible("blue to play") || isVisible("blue:")) CardColor.BLUE else CardColor.RED

/** True while the match is still running. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.inProgress(): Boolean =
    isVisible("pick a card") || isVisible("pick a cell")

/**
 * Plays the match to the end: always the leftmost remaining card onto the lowest free cell.
 *
 * Cell `n` is free at placement `n` because placements go in order, so no board introspection
 * is needed.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.playOut() {
    for (position in 0 until PLACEMENTS_PER_MATCH) {
        check(inProgress()) { "the match ended after $position placements" }
        onNodeWithTag(handCardTestTag(sideToPlay(), 0)).performClick()
        onNodeWithTag(tileTestTag(position)).performClick()
        waitForIdle()
    }
}

/**
 * True when the score line sums to [TOTAL_CARDS].
 *
 * Matched on the score node with an **exact** text comparison, not a substring anywhere on
 * screen: the line now reads `5 — 5`, and `"0 — 0"` is a substring of `"10 — 0"`.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.totalIsTen(): Boolean =
    (0..TOTAL_CARDS).any { blue ->
        onAllNodes(hasTestTag(SCORE_TEST_TAG) and hasText("$blue — ${TOTAL_CARDS - blue}"))
            .fetchSemanticsNodes().isNotEmpty()
    }
