package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText

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
 * Blocks until `cards.json` has been read out of the Compose resource bundle and
 * parsed. Every test needs this: [App] starts on a "loading cards…" placeholder and
 * there is no card to tap until the load completes.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitCatalog() {
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible("catalog:") }
}
