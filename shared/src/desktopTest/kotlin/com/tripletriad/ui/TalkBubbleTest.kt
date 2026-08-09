package com.tripletriad.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `TalkAnim`, which has no caller in this port yet.
 *
 * It is built ahead of one deliberately: `TutorialScreen` and `TutorialRematchPanel` are the two
 * Phase 4 screens listed as blocked on it, and the tutorial *is* a sequence of these over a
 * scripted match. So the component is the dependency, and its contract is fully specified by the
 * AS3 — a line, a speaker, five seconds, gone.
 *
 * Which makes these tests the whole of its verification: there is no screen to see it on, so the
 * behaviour a caller will depend on has to be stated here.
 */
@OptIn(ExperimentalTestApi::class)
class TalkBubbleTest {

    /** A line is spoken, and then it is over. */
    @Test
    fun aLineIsShownAndThenClears() = runComposeUiTest {
        var finished = false
        setContent { TalkBubble(message = LINE, speaker = SPEAKER) { finished = true } }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible(LINE) }
        assertTrue(isVisible(SPEAKER), "the line should say who is speaking")

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { finished }
        assertFalse(isVisible(LINE), "the line outlived its bubble")
    }

    /**
     * The text arrives **after** the bubble, not with it.
     *
     * `predispose()` — the entry tween's `onComplete` — is what builds both `TextField`s, so a
     * line is never readable while its frame is still flying in at 1.5×. Easy to lose by drawing
     * the text unconditionally, and the loss is invisible in a screenshot.
     *
     * Driven off a **stopped clock**, because the window being asserted is the 0.4s entry and
     * `waitUntil` cannot be relied on to look inside it — it advances the frame clock in order to
     * settle, so by the time an ordinary assertion runs the entry may already be over. A test
     * that passes because it happened to look early is worse than none.
     */
    @Test
    fun theTextWaitsForTheBubbleToArrive() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { TalkBubble(message = LINE, speaker = SPEAKER) {} }

        mainClock.advanceTimeByFrame()
        assertTrue(exists(TALK_BUBBLE_TEST_TAG), "the bubble should be up from the first frame")
        assertFalse(isVisible(LINE), "the line was readable before its bubble had landed")

        mainClock.advanceTimeBy(SETTLED_MS)
        assertTrue(isVisible(LINE), "the line should be up once the bubble has landed")
    }

    /**
     * A second line replaces the first rather than being swallowed.
     *
     * The tutorial plays these back to back — `setTimeout(talk, 6100, 6)` against a 5.8s line —
     * so a bubble keyed on first composition alone would speak once and then go quiet for the
     * rest of the lesson.
     */
    @Test
    fun aSecondLineSpeaksInTurn() = runComposeUiTest {
        var line by mutableStateOf(LINE)
        setContent { TalkBubble(message = line, speaker = SPEAKER) {} }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible(LINE) }

        line = SECOND

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible(SECOND) }
        assertFalse(isVisible(LINE), "both lines were up at once")
    }

    /**
     * Without the artwork the line is still readable.
     *
     * The frame is decoration around text that carries the meaning, so a missing texture must
     * cost the frame and not the sentence — which is the opposite of the rule captions, where the
     * picture *is* the sentence and a missing one is skipped entirely.
     */
    @Test
    fun theLineSurvivesAMissingFrame() = runComposeUiTest {
        setContent { TalkBubble(message = LINE, speaker = SPEAKER) {} }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isVisible(LINE) }
    }

    private companion object {
        const val LINE = "Place a card on any free cell."
        const val SECOND = "Now capture one of mine."
        const val SPEAKER = "Triple Triad Master"

        /** Past the 0.4s entry and comfortably inside the 5s hold. */
        const val SETTLED_MS = 1_000L
    }
}
