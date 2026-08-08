package com.tripletriad.ui

import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.PLACEMENTS_PER_MATCH
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which of the nine lines is spoken when — [tutorialLines].
 *
 * Worth its own tests because it is the part of the tutorial that is a *decision* rather than a
 * transcription: the original's own schedule leaves three of its nine lines unreachable, and this
 * table is where that was corrected. A UI test can see that a bubble appeared; only this can say
 * that every line the author wrote is reachable.
 */
class TutorialLessonTest {

    /** All nine, and no line twice. */
    @Test
    fun everyLineIsSpokenExactlyOnce() {
        val spoken = (0 until PLACEMENTS_PER_MATCH).flatMap { tutorialLines(it, CAPTURED) }

        assertEquals(EXPECTED_LINES, spoken.size, "the lesson should say all nine of its lines")
        assertEquals(spoken.size, spoken.toSet().size, "a line was scheduled twice")
    }

    /**
     * Three of them fall on the player's turn, which is what the original could not do.
     *
     * `opponentPhase` is reached only from the red branch of `nextTurn` (`BaseMatchScreen.as:380`),
     * so `TutorialScreen`'s `turn == 2` and `turn == 4` branches never ran. Red opens here, so red
     * holds the even placements — the odd ones are the player's, and they must not be empty.
     */
    @Test
    fun thePlayersOwnTurnsAreSpokenTo() {
        val onPlayerTurns = (0 until PLACEMENTS_PER_MATCH)
            .filter { it % 2 == 1 }
            .flatMap { tutorialLines(it, CAPTURED) }

        assertEquals(
            listOf(StringKeys.TUTORIAL_4, StringKeys.TUTORIAL_5, StringKeys.TUTORIAL_8),
            onPlayerTurns,
            "the three instructional lines belong on the player's first two turns",
        )
    }

    /**
     * The line congratulating a capture is not spoken when nothing was captured.
     *
     * `if (scores.BLUE > 5) talk(5)` — the score opens at five all, so anything above it means the
     * player has flipped one of the opponent's cards. "See how my card changed color?" over an
     * unchanged board would be the tutorial teaching something false.
     */
    @Test
    fun theCaptureLineWaitsForACapture() {
        val without = tutorialLines(CAPTURE_PLACEMENT, blueScore = OPENING)
        val with = tutorialLines(CAPTURE_PLACEMENT, blueScore = OPENING + 1)

        assertTrue(
            StringKeys.TUTORIAL_6 !in without,
            "nothing was captured, so nothing changed colour",
        )
        assertEquals(listOf(StringKeys.TUTORIAL_6, StringKeys.TUTORIAL_7), with)
    }

    /** The middle of the match is silent — the AS3's `else` branch says nothing at all. */
    @Test
    fun theMiddleOfTheMatchIsQuiet() {
        for (placement in QUIET_FROM..QUIET_TO) {
            assertEquals(
                emptyList(),
                tutorialLines(placement, CAPTURED),
                "placement $placement should have no line",
            )
        }
    }

    private companion object {
        const val EXPECTED_LINES = 9

        /** Five all is the opening score; above it, the player has captured something. */
        const val OPENING = 5
        const val CAPTURED = OPENING + 1

        /** The AS3's `turn == 3`, where the capture line is decided. */
        const val CAPTURE_PLACEMENT = 2

        /** Its `else` branch: turns 5 to 8, which are placements 4 to 7. */
        const val QUIET_FROM = 4
        const val QUIET_TO = 7
    }
}
