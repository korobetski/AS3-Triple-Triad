package com.tripletriad.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tripletriad.model.CardColor
import com.tripletriad.model.CoinFlip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The coin flip while it is on screen. */
const val COIN_FLIP_TEST_TAG: String = "coin-flip"

/** `coin-flip-0` … `coin-flip-2`, one per roll, in the order they land. */
fun coinFlipTestTag(roll: Int): String = "coin-flip-$roll"

/**
 * How long the whole flip occupies the screen.
 *
 * `pileOuFace` hands over with `setTimeout(letsGetStarted, 1000)` (`BaseMatchScreen.as:245`),
 * which is the number that actually governs — the tweens themselves finish at 0.7s
 * (three staggered 0.3s entries ending at 0.5s, then a 0.2s exit) and the remaining 0.3s
 * is the pause the player reads the result in. Transcribed as a hold so the sum is the
 * original's second, rather than as a 0.7s animation followed by a mystery gap.
 */
internal const val COIN_FLIP_TOTAL_MILLIS: Int = 1_000

/**
 * `PileOuFace` — three cards dealt face down to decide who moves first.
 *
 * ### Why this is not a caption
 *
 * Every other pre-match animation is a picture of a word. This one **shows the player the
 * result**: three card backs, blue or red, and the majority takes the first move. It is
 * the only piece of the intro sequence carrying information rather than announcing a rule,
 * which is why it renders cards and why the rolls come from [CoinFlip] rather than being
 * invented here — the flip has already happened, in `MatchSetup`, and drawing a fresh
 * random here would show the player a result that contradicts whose turn it then is.
 *
 * ### Why the geometry is relative
 *
 * The AS3 works in absolute stage coordinates — cards land at `(308+116, 166+116)` and so
 * on — because Starling gave it a fixed 1136x640 stage. Everything here is a fraction of
 * the viewport or of the card's own size, for the reason the caption slides are: a fan
 * sized for one screen is off the edge of a smaller one and huddled in the middle of a
 * larger one.
 *
 * The three rotations, 55°, 45° and 35°, are transcribed unchanged. They are what makes it
 * read as a scattered hand rather than a stack.
 *
 * @param flip the rolls to show, already drawn. Three of them, and their majority is
 *   [CoinFlip.winner] — the side [com.tripletriad.model.MatchState.order] starts with.
 * @param onFinished the queue's cue to move on. Called once, from this composable's own
 *   coroutine, exactly as a caption's is.
 */
@Composable
internal fun CoinFlipCards(flip: CoinFlip, onFinished: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().testTag(COIN_FLIP_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        flip.rolls.take(FAN.size).forEachIndexed { roll, color ->
            TossedCard(roll = roll, color = color)
        }
    }

    // One timer for the whole flip rather than a completion callback on the last card, so
    // the queue's timing does not depend on which of three parallel animations happens to
    // settle last.
    LaunchedEffect(flip) {
        delay(COIN_FLIP_TOTAL_MILLIS.toLong())
        onFinished()
    }
}

/**
 * One of the three, flying in from its own edge and out through another.
 *
 * The stagger is [FanCard.delayMillis]: 0, 0.1s, 0.2s, so they arrive in order and the
 * player can count them. The exit is created in `predispose()` — the third card's
 * `onComplete` — so all three leave together once the last has landed, and the exit's own
 * `delay` is nothing rather than a hold.
 */
@Composable
private fun TossedCard(roll: Int, color: CardColor) {
    val fan = FAN[roll]
    val progress = remember(roll, color) { Animatable(0f) }
    val exit = remember(roll, color) { Animatable(0f) }
    val alpha = remember(roll, color) { Animatable(0f) }

    LaunchedEffect(roll, color) {
        delay(fan.delayMillis.toLong())
        launch { alpha.animateTo(1f, tween(ENTER_MILLIS, easing = LinearEasing)) }
        progress.animateTo(1f, tween(ENTER_MILLIS, easing = EaseOut))

        // Waits out the cards behind it as well as its own hold, so the three leave
        // together however they were staggered coming in.
        delay((LAST_DELAY_MILLIS - fan.delayMillis + HOLD_MILLIS).toLong())

        val leaving = tween<Float>(EXIT_MILLIS, easing = LinearEasing)
        launch { alpha.animateTo(0f, leaving) }
        exit.animateTo(1f, leaving)
    }

    CardBack(
        color = color,
        scale = CARD_SCALE,
        modifier = Modifier
            .testTag(coinFlipTestTag(roll))
            // A card back says nothing to a screen reader, and this one is the only place
            // the player is told who won the toss.
            .semantics { contentDescription = "${COIN_FLIP_TEST_TAG}-${color.name}" }
            .graphicsLayer {
                val from = fan.from * size.minDimension
                val to = fan.to * size.minDimension
                val rest = fan.rest * size.minDimension
                translationX = lerp(from.x, rest.x, progress.value) +
                    (to.x - rest.x) * exit.value
                translationY = lerp(from.y, rest.y, progress.value) +
                    (to.y - rest.y) * exit.value
                rotationZ = fan.degrees * progress.value
                // 1.2 in the original, settling to 1. The exit does not scale at all.
                val entering = lerp(ENTER_SCALE, 1f, progress.value)
                scaleX = entering
                scaleY = entering
                this.alpha = alpha.value
            },
    )
}

private fun lerp(from: Float, to: Float, fraction: Float): Float =
    from + (to - from) * fraction

private operator fun Offset.times(scalar: Float): Offset = Offset(x * scalar, y * scalar)

/**
 * Where one card comes from, lands, leaves to, and how far it turns.
 *
 * All three offsets are in **multiples of the card's smaller dimension**, so the fan keeps
 * its proportions on any screen. The AS3's own numbers are absolute pixels on a fixed
 * stage and do not survive being carried across.
 */
private data class FanCard(
    val from: Offset,
    val rest: Offset,
    val to: Offset,
    val degrees: Float,
    val delayMillis: Int,
)

/**
 * The three, transcribed from `PileOuFace.start()` and `predispose()`.
 *
 * The rotations and the stagger are the original's. The positions are its *shape* — in
 * from three different edges, fanning down and to the right, out through three others —
 * expressed relatively. Off-screen is [OFF_SCREEN] card-widths out, which is far enough
 * that a card is never seen to appear.
 */
private val FAN = listOf(
    FanCard(
        from = Offset(OFF_SCREEN, -OFF_SCREEN),
        rest = Offset(-0.9f, -0.7f),
        to = Offset(-OFF_SCREEN, OFF_SCREEN),
        degrees = 55f,
        delayMillis = 0,
    ),
    FanCard(
        from = Offset(-OFF_SCREEN, OFF_SCREEN),
        rest = Offset(0f, 0f),
        to = Offset(OFF_SCREEN, -OFF_SCREEN),
        degrees = 45f,
        delayMillis = 100,
    ),
    FanCard(
        from = Offset(OFF_SCREEN, 0f),
        rest = Offset(0.9f, 0.7f),
        to = Offset(0f, OFF_SCREEN),
        degrees = 35f,
        delayMillis = 200,
    ),
)

/** `Starling.juggler.tween(card, 0.3, …)` — the same for all three. */
private const val ENTER_MILLIS = 300

/** `Starling.juggler.tween(card, 0.2, …)` in `predispose`. */
private const val EXIT_MILLIS = 200

/** The last card's `delay:0.2`, which the other two wait out so all three leave together. */
private const val LAST_DELAY_MILLIS = 200

/** What is left of the original's 1s once the tweens are accounted for. */
private const val HOLD_MILLIS =
    COIN_FLIP_TOTAL_MILLIS - LAST_DELAY_MILLIS - ENTER_MILLIS - EXIT_MILLIS

/** `card.scaleX = card.scaleY = 1.2` before the tween pulls it back to 1. */
private const val ENTER_SCALE = 1.2f

/** Far enough out that the card is never seen to pop into existence. */
private const val OFF_SCREEN = 4f

/** Smaller than a card in hand: three of these fan across the middle of the board. */
private const val CARD_SCALE = 0.8f
