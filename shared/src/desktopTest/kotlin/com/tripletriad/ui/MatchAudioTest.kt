package com.tripletriad.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.audio.RecordingAudioPlayer
import com.tripletriad.audio.Sound
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.PLACEMENTS_PER_MATCH
import com.tripletriad.settings.InMemorySettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which sound each moment asks for, driven through the real UI with a [RecordingAudioPlayer].
 *
 * No test can assert that a sound was *audible*. What it can assert is the **mapping**, which is
 * where the decisions are: the AS3 played `se_ttriad.scd_1` for a placement that captured nothing
 * and `se_ttriad.scd_157` for one that did, and getting those the wrong way round is a bug that
 * playing the app would only reveal to someone who knew what to listen for.
 */
@OptIn(ExperimentalTestApi::class)
class MatchAudioTest {
    private val audio = RecordingAudioPlayer()

    @Test
    fun theMusicStartsWithTheMatchAndStopsWhenItIsLeft() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), audio = audio) }
        awaitMenu()

        // `MenuScreen` never called `shuffleLoop` — nothing plays on the menu but the tap.
        assertFalse(Sound.MATCH_MUSIC in audio, "the music started before a match")

        // Play now leads to the character list and then to the dashboard, so reaching a board is
        // the whole flow — and the music must not start on any screen along the way.
        newCharacter()
        openOpponents()
        assertFalse(Sound.MATCH_MUSIC in audio, "the music started before a board was up")

        challenge()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { Sound.MATCH_MUSIC in audio }
        val stopsDuringMatch = audio.musicStops

        onNodeWithTag(MATCH_EXIT_TEST_TAG).performClick()
        waitForIdle()

        assertTrue(audio.musicStops > stopsDuringMatch, "leaving the match left the music running")
    }

    @Test
    fun openingAMatchPlaysTheDealSound() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), audio = audio) }
        startMatch()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { Sound.MATCH_OPEN in audio }
    }

    @Test
    fun everyMenuButtonClicks() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), audio = audio) }
        awaitMenu()

        onNodeWithTag(MENU_OPTIONS_TEST_TAG).performClick()
        waitForIdle()

        assertEquals(listOf(Sound.UI_CLICK), audio.played.filter { it == Sound.UI_CLICK })
    }

    /**
     * Each of the player's placements plays the sound that matches **what it did**, not merely one
     * of the two.
     *
     * Whether a capture happened is read off the score rather than trusted: the side that played
     * gains one for its own card plus one per capture, so the *other* side's score falling is
     * proof. An earlier version asserted only "exactly one of the two played", and swapping the two
     * sounds in the source did not fail it — a mutation check caught that, so the score comparison
     * is here because the weaker assertion was shown to be worthless.
     *
     * ### Only the player's placements, now that the opponent plays itself
     *
     * The opponent's turn happens on its own after a pause, so a per-placement window that covered
     * both sides would race: the recorder would sometimes hold two placements' sounds and sometimes
     * one. What is asserted instead is every *blue* placement, which is the whole of the mapping —
     * `sound()` is one function called from one place and does not know whose turn it was.
     * `anOpponentPlacementAlsoSounds` covers that red goes through it too.
     */
    @Test
    fun eachPlayerPlacementPlaysTheSoundThatMatchesWhatItDid() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), audio = audio) }
        startMatch()

        var withCaptures = 0
        var without = 0
        while (!isFinished()) {
            awaitPlayer()
            if (isFinished()) break
            audio.clear()
            val before = score()
            playOneCard()
            val after = score()

            // Blue played, so red losing a card is the capture.
            val captures = before.second - after.second > 0
            if (captures) withCaptures++ else without++

            assertEquals(
                captures,
                Sound.CARD_CAPTURED in audio,
                "a placement that captured=$captures played ${audio.played} " +
                    "(score $before -> $after)",
            )
            assertEquals(!captures, Sound.CARD_PLACED in audio, "played ${audio.played}")

            waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isPlayerTurn() || isFinished() }
        }

        val placements = withCaptures + without
        assertTrue(placements >= PLAYER_PLACEMENTS_MIN, "blue played only $placements times")
        assertTrue(withCaptures > 0, "no capture in a whole match — one branch went unexercised")
    }

    /** The opponent's placements go through the same mapping, unprompted. */
    @Test
    fun anOpponentPlacementAlsoSounds() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), audio = audio) }
        startMatch()

        playOneCard()
        audio.clear()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            Sound.CARD_PLACED in audio || Sound.CARD_CAPTURED in audio
        }

        assertTrue(
            Sound.CARD_PLACED in audio || Sound.CARD_CAPTURED in audio,
            "the opponent's own placement was silent: ${audio.played}",
        )
    }

    /**
     * The last placement ends the match, so it plays a winner rather than a turn change.
     *
     * Stated as a count over the whole match rather than by clearing the recorder before the final
     * move: with an autonomous opponent, which placement is last depends on the coin flip, and a
     * test that had to know would be asserting the flip. Eight turn changes and one winner is the
     * same claim, and a stronger one — it also catches a turn change fired *after* the result.
     *
     * A draw plays neither, matching the original: `PVEMatchScreen.as`'s draw branch is silent.
     */
    @Test
    fun theLastPlacementPlaysTheOutcomeInsteadOfATurnChange() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), audio = audio) }
        startMatch()
        // The opponent may already have played, if the coin flip favoured it — so the window this
        // test measures starts at whatever is on the board, not at zero.
        val alreadyPlaced = placementsMade()
        audio.clear()

        playOut()

        assertEquals(
            PLACEMENTS_PER_MATCH - 1 - alreadyPlaced,
            audio.played.count { it == Sound.TURN_CHANGE },
            "one turn change per placement except the last: ${audio.played}",
        )
        val outcomeSounds = audio.played.filter { it == Sound.BLUE_WINS || it == Sound.RED_WINS }
        val drawn = !isVisible("You win") && !isVisible("You lose")
        if (drawn) {
            assertTrue(outcomeSounds.isEmpty(), "a draw should be silent: $outcomeSounds")
        } else {
            assertEquals(1, outcomeSounds.size, "expected one winner sound: ${audio.played}")
        }
    }

    /** Every placement before the last one hands the turn over. */
    @Test
    fun anUnfinishedMatchPlaysATurnChange() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), audio = audio) }
        startMatch()

        audio.clear()
        playOneCard()

        assertTrue(Sound.TURN_CHANGE in audio, "played: ${audio.played}")
        assertFalse(Sound.BLUE_WINS in audio)
        assertFalse(Sound.RED_WINS in audio)
    }

    /** The rematch control, which now lives in the end-of-match panel rather than on the board. */
    @Test
    fun theRematchControlSoundsAndDealsAgain() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), audio = audio) }
        startMatch()
        playOut()
        audio.clear()

        onNodeWithTag(NEW_MATCH_TEST_TAG).performClick()
        waitForIdle()

        assertTrue(Sound.NEW_MATCH in audio, "played: ${audio.played}")
        // The deal sound comes with the cards, which is now after the deck is settled rather than
        // when the screen opens — the two were the same moment before the selector existed.
        settleDeck()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { Sound.MATCH_OPEN in audio }
    }

    /** The volumes reach the player from the settings, without the player reading settings. */
    @Test
    fun theStoredVolumesAreHandedToThePlayer() = runComposeUiTest {
        setContent {
            App(
                store = InMemorySettingsStore(STORED_VOLUMES),
                audio = audio,
            )
        }
        awaitMenu()

        assertEquals(STORED_BACKGROUND to STORED_NOISE, audio.volumes)
    }

    @Test
    fun changingAVolumeInTheOptionsReachesThePlayer() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), audio = audio) }
        awaitMenu()
        onNodeWithTag(MENU_OPTIONS_TEST_TAG).performClick()
        waitForIdle()

        onNodeWithTag(OPTIONS_NOISE_VOLUME_TEST_TAG)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { audio.volumes?.second == 0f }

        assertEquals(0f, audio.volumes?.second)
    }

    private companion object {
        /**
         * The fewest placements the player makes in a match.
         *
         * Four, not five: `TurnOrder` gives the first mover five of the nine placements and the
         * coin flip decides who that is, so a test that expected five would be asserting the flip.
         */
        const val PLAYER_PLACEMENTS_MIN = 4

        const val STORED_BACKGROUND = 0.25f
        const val STORED_NOISE = 0.5f

        /** Deliberately not both 1.0, so a player that ignored the file would read as correct. */
        val STORED_VOLUMES = """
            {"language":"en_US","background_volume":$STORED_BACKGROUND,"noise_volume":$STORED_NOISE}
        """.trimIndent()
    }
}
