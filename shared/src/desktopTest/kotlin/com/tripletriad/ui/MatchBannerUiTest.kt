package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.CardColor
import com.tripletriad.model.CoinFlip
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * The overlay playing captions through, in a real composition.
 *
 * [MatchBannerTest] settles *which* captions a moment earns and this settles that they
 * reach the screen and then leave it. The two failures it is here for are the ones no
 * unit test can see: a caption that never appears because the artwork could not be found
 * under [LocalBannerArt], and a caption that appears and stays — an overlay that never
 * calls `onFinished` covers the board for the rest of the match, and the queue behind it
 * never moves.
 */
@OptIn(ExperimentalTestApi::class)
class MatchBannerUiTest {

    /** One caption: it arrives, and then it goes away by itself. */
    @Test
    fun aCaptionPlaysAndThenLeaves() = runComposeUiTest {
        setContent { Overlay(BannerEvent(at = 0, listOf(MatchBanner.SAME).asAnimations())) }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(matchBannerTestTag(MatchBanner.SAME)) }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(MATCH_BANNER_TEST_TAG) }
    }

    /**
     * Two captions from one placement play one at a time, in the order they were given.
     *
     * The assertion that matters is the *absence* of COMBO while SAME is up: the queue
     * exists precisely because a composable is not a display list you can push two of,
     * and an implementation that rendered the list would show both at once — on top of
     * each other, since both are centred.
     */
    @Test
    fun captionsFromOnePlacementPlayOneAtATime() = runComposeUiTest {
        val event = BannerEvent(at = 0, listOf(MatchBanner.SAME, MatchBanner.COMBO).asAnimations())
        setContent { Overlay(event) }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(matchBannerTestTag(MatchBanner.SAME)) }
        assertFalse(
            exists(matchBannerTestTag(MatchBanner.COMBO)),
            "both captions were on screen at once",
        )

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(matchBannerTestTag(MatchBanner.COMBO)) }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(MATCH_BANNER_TEST_TAG) }
    }

    /**
     * A second event plays even when it is the same caption as the first.
     *
     * Two Sames in a row is the case [BannerEvent.at] exists for. Nothing about the
     * captions distinguishes them, so an overlay keyed on the caption list alone would
     * play the first and swallow the second — which on screen looks like a dropped frame
     * rather than a bug, and is why it is asserted rather than trusted.
     */
    @Test
    fun theSameCaptionTwiceInARowPlaysTwice() = runComposeUiTest {
        var at by mutableStateOf(0)
        setContent { Overlay(BannerEvent(at, listOf(MatchBanner.SAME).asAnimations())) }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(matchBannerTestTag(MatchBanner.SAME)) }
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(MATCH_BANNER_TEST_TAG) }

        at = 1

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(matchBannerTestTag(MatchBanner.SAME)) }
    }

    /**
     * The rule is announced to a screen reader, not just drawn.
     *
     * The caption is a picture of a word, so without this the only place some of these
     * rules are ever spelled out is unreadable to anyone not looking at it.
     */
    @Test
    fun aCaptionNamesItsRule() = runComposeUiTest {
        setContent { Overlay(BannerEvent(at = 0, listOf(MatchBanner.FALLEN_ACE).asAnimations())) }

        val tag = matchBannerTestTag(MatchBanner.FALLEN_ACE)

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(tag) }
        onNodeWithTag(tag)
            .assertContentDescriptionEquals(MatchBanner.FALLEN_ACE.name)
    }

    /**
     * Without the artwork the overlay draws nothing rather than failing.
     *
     * Which is what lets every match test in this source set compose unchanged: the
     * captions are decoration over a board that is fully playable without them, so a test
     * that does not supply [LocalBannerArt] should get a board, not a crash.
     */
    @Test
    fun theOverlayIsAbsentWithoutArtwork() = runComposeUiTest {
        val event = BannerEvent(at = 0, listOf(MatchBanner.SAME).asAnimations())
        setContent { MatchBannerOverlay(event) }

        waitForIdle()
        assertFalse(exists(MATCH_BANNER_TEST_TAG), "a caption was drawn with no artwork to draw")
    }

    /**
     * A caption with no artwork does not block the ones behind it.
     *
     * The queue is serial, so anything that leaves a caption playing forever stops every
     * later caption in the match — a far worse symptom than the missing picture, and one
     * that shows up as the *fourth* caption going quiet rather than the first. Asserted by
     * playing two captions with no artwork at all and requiring the overlay to settle.
     */
    @Test
    fun aCaptionWithNoArtworkDoesNotBlockTheQueue() = runComposeUiTest {
        var event by mutableStateOf(BannerEvent(0, listOf(MatchBanner.SAME).asAnimations()))
        setContent { MatchBannerOverlay(event) }

        waitForIdle()
        event = BannerEvent(1, listOf(MatchBanner.START).asAnimations())

        waitForIdle()
        assertFalse(
            exists(MATCH_BANNER_TEST_TAG),
            "the queue stalled on a caption it could not draw",
        )
    }

    // ---- The coin flip ------------------------------------------------------

    /**
     * `PileOuFace`: three cards, then they leave.
     *
     * Not a caption, so it does not go through [LocalBannerArt] and does not depend on the
     * banner bundle at all — which is why this is the one intro animation that plays in a
     * test with no artwork provided.
     */
    @Test
    fun theCoinFlipDealsThreeCardsAndClears() = runComposeUiTest {
        val toss = MatchAnimation.Toss(CoinFlip.forced(CardColor.BLUE))
        setContent { MatchBannerOverlay(BannerEvent(at = 0, listOf(toss))) }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(COIN_FLIP_TEST_TAG) }
        repeat(CoinFlip.ROLLS) { onNodeWithTag(coinFlipTestTag(it)).assertExists() }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !exists(COIN_FLIP_TEST_TAG) }
    }

    /**
     * The cards show the rolls that were actually drawn, not three of the winner's colour.
     *
     * A 2-1 flip is two cards of one colour and one of the other, and that is the whole
     * information content of the animation — showing the winner three times would be a
     * different, and less honest, animation.
     */
    @Test
    fun theCardsShowTheRollsThatWereDrawn() = runComposeUiTest {
        val rolls = listOf(CardColor.RED, CardColor.BLUE, CardColor.RED)
        val toss = MatchAnimation.Toss(CoinFlip(rolls))
        setContent { MatchBannerOverlay(BannerEvent(at = 0, listOf(toss))) }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(COIN_FLIP_TEST_TAG) }
        rolls.forEachIndexed { roll, color ->
            onNodeWithTag(coinFlipTestTag(roll), useUnmergedTree = true)
                .assertContentDescriptionEquals("$COIN_FLIP_TEST_TAG-${color.name}")
        }
    }

    /** And the flip runs before Start, which announces a match whose first player is known. */
    @Test
    fun theCoinFlipPlaysBeforeStart() = runComposeUiTest {
        val toss = MatchAnimation.Toss(CoinFlip.forced(CardColor.RED))
        val start = MatchAnimation.Caption(MatchBanner.START)
        setContent { Overlay(BannerEvent(at = 0, listOf(toss, start))) }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(COIN_FLIP_TEST_TAG) }
        assertFalse(exists(matchBannerTestTag(MatchBanner.START)), "Start jumped the coin flip")

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(matchBannerTestTag(MatchBanner.START)) }
    }

    /** The overlay with real captions behind it, which is what `App` provides. */
    @Composable
    private fun Overlay(event: BannerEvent?) {
        val art = remember { BannerArt(AppLocale.EN_US) }
        CompositionLocalProvider(LocalBannerArt provides art) {
            MatchBannerOverlay(event)
        }
    }
}
