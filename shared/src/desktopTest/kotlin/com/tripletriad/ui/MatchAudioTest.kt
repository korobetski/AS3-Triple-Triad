package com.tripletriad.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import com.tripletriad.audio.RecordingAudioPlayer
import com.tripletriad.audio.Sound
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.CardColor
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

        onNodeWithTag(MENU_PLAY_TEST_TAG).performClick()
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
     * Each placement plays the sound that matches **what it did**, not merely one of the two.
     *
     * Whether a capture happened is read off the score rather than trusted: the side that played
     * gains one for its own card plus one per capture, so the *other* side's score falling is
     * proof. An earlier version asserted only "exactly one of the two played", and swapping the
     * two sounds in the source did not fail it — a mutation check caught that, so the score
     * comparison is here because the weaker assertion was shown to be worthless.
     *
     * The whole match is played out, so both branches are covered in whatever order this deal
     * produces them rather than by contriving the case that was easy to set up.
     */
    @Test
    fun eachPlacementPlaysTheSoundThatMatchesWhatItDid() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), audio = audio) }
        startMatch()
        waitForIdle()

        var withCaptures = 0
        var without = 0
        for (position in 0 until PLACEMENTS_PER_MATCH) {
            audio.clear()
            val mover = sideToPlay()
            val before = score()
            onNodeWithTag(handCardTestTag(mover, 0)).performClick()
            onNodeWithTag(tileTestTag(position)).performClick()
            waitForIdle()
            val after = score()

            val opponentLost = if (mover == CardColor.BLUE) {
                before.second - after.second
            } else {
                before.first - after.first
            }
            val captures = opponentLost > 0
            if (captures) withCaptures++ else without++

            assertEquals(
                captures,
                Sound.CARD_CAPTURED in audio,
                "placement $position captured=$captures but played ${audio.played} " +
                    "(score $before -> $after, $mover to play)",
            )
            assertEquals(!captures, Sound.CARD_PLACED in audio, "placement $position")
        }

        assertEquals(PLACEMENTS_PER_MATCH, withCaptures + without)
        assertTrue(withCaptures > 0, "no capture in a whole match — this deal exercises one branch")
        assertTrue(without > 0, "every placement captured — this deal exercises one branch")
    }

    /**
     * The last placement ends the match, so it plays a winner rather than a turn change.
     *
     * A draw plays neither, matching the original: `PVEMatchScreen.as`'s draw branch is silent.
     */
    @Test
    fun theLastPlacementPlaysTheOutcomeInsteadOfATurnChange() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), audio = audio) }
        startMatch()
        waitForIdle()

        for (position in 0 until PLACEMENTS_PER_MATCH - 1) {
            onNodeWithTag(handCardTestTag(sideToPlay(), 0)).performClick()
            onNodeWithTag(tileTestTag(position)).performClick()
            waitForIdle()
        }
        audio.clear()
        onNodeWithTag(handCardTestTag(sideToPlay(), 0)).performClick()
        onNodeWithTag(tileTestTag(PLACEMENTS_PER_MATCH - 1)).performClick()
        waitForIdle()

        assertFalse(Sound.TURN_CHANGE in audio, "the match was over: ${audio.played}")
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
        waitForIdle()

        audio.clear()
        onNodeWithTag(handCardTestTag(sideToPlay(), 0)).performClick()
        onNodeWithTag(tileTestTag(0)).performClick()
        waitForIdle()

        assertTrue(Sound.TURN_CHANGE in audio, "played: ${audio.played}")
        assertFalse(Sound.BLUE_WINS in audio)
        assertFalse(Sound.RED_WINS in audio)
    }

    @Test
    fun theNewMatchControlSoundsAndDealsAgain() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US), audio = audio) }
        startMatch()
        waitForIdle()
        audio.clear()

        onNodeWithTag(NEW_MATCH_TEST_TAG).performClick()
        waitForIdle()

        assertTrue(Sound.NEW_MATCH in audio, "played: ${audio.played}")
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
        const val STORED_BACKGROUND = 0.25f
        const val STORED_NOISE = 0.5f

        /** Deliberately not both 1.0, so a player that ignored the file would read as correct. */
        val STORED_VOLUMES = """
            {"language":"en_US","background_volume":$STORED_BACKGROUND,"noise_volume":$STORED_NOISE}
        """.trimIndent()
    }
}
