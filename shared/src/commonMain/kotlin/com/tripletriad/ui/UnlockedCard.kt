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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import kotlinx.coroutines.delay

/** The reveal while it is on screen. */
const val UNLOCKED_CARD_TEST_TAG: String = "unlocked-card"

/**
 * `UnlockCardAnim` — the card you just added to your collection, shown to you.
 *
 * It flies in from the upper right at a tilt, settles enlarged in the middle, holds 1.4s, then
 * slides away to the lower left. `InventoryScreen.useBtnHandler` (`:236-245`) plays it in exactly
 * one branch: a **card item** being used, which is the moment a card enters `CARDS`. Opening a
 * pack does not play it, because a pack yields another bag item rather than a card — see
 * [com.tripletriad.data.ItemUse.PackOpened] for why that indirection is deliberate.
 *
 * ### Why it is bigger than a card ever is elsewhere
 *
 * `scaleX: 1.5` at rest, against 1.0 everywhere else in the game. This is the one place a card is
 * not a game piece but a prize, and the original says so by drawing it half again as large.
 *
 * ### Why the geometry is relative
 *
 * Fractions of the card's own size rather than the original's stage pixels, for the reason
 * [CoinFlipCards] gives: those numbers are absolute on a fixed 1136x640 stage, and this port draws
 * on whatever the device is.
 *
 * @param card the card that was won, drawn face up. Blue, as `card.color = "BLUE"` — a card in
 *   your collection has no opponent to belong to.
 * @param onFinished the reveal is over. Called once, from this composable's own coroutine.
 */
@Composable
internal fun UnlockedCard(card: Card, onFinished: () -> Unit) {
    val entry = remember(card.id) { Animatable(0f) }
    val exit = remember(card.id) { Animatable(0f) }

    LaunchedEffect(card.id) {
        entry.animateTo(1f, tween(ENTER_MILLIS, easing = EaseOut))
        delay(HOLD_MILLIS.toLong())
        exit.animateTo(1f, tween(EXIT_MILLIS, easing = LinearEasing))
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().testTag(UNLOCKED_CARD_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        CardFace(
            card = card.copy(owner = CardColor.BLUE),
            modifier = Modifier
                // The card's name rather than a generic label: this *is* the announcement, and
                // a player who cannot see it is otherwise told nothing at all.
                .semantics { contentDescription = card.name }
                .graphicsLayer {
                    val arrived = entry.value
                    val leaving = exit.value
                    translationX = size.width *
                        (ENTER_X * (1f - arrived) + EXIT_X * leaving)
                    translationY = size.height *
                        (ENTER_Y * (1f - arrived) + EXIT_Y * leaving)
                    rotationZ = ENTER_DEGREES * (1f - arrived)
                    // 1.2 on the way in, 1.5 at rest, back to 1 on the way out. Three values and
                    // two legs, so the resting scale is the middle rather than either end.
                    val scale = lerp(ENTER_SCALE, REST_SCALE, arrived) +
                        (1f - REST_SCALE) * leaving
                    scaleX = scale
                    scaleY = scale
                    alpha = arrived * (1f - leaving)
                },
        )
    }
}

private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

/** `Starling.juggler.tween(card, 0.3, ...)` — in from the upper right. */
private const val ENTER_MILLIS = 300

/** The exit tween's `delay: 1.4` — long enough to read a card you have not seen before. */
private const val HOLD_MILLIS = 1_400

/** `Starling.juggler.tween(card, 0.2, ...)` in `predispose`. */
private const val EXIT_MILLIS = 200

/** `x: stage.width * 0.75, y: 0` — up and to the right of where it lands. */
private const val ENTER_X = 3f
private const val ENTER_Y = -3f

/** `x: 0, y: stage.height * 0.75` — down and to the left. */
private const val EXIT_X = -3f
private const val EXIT_Y = 3f

/** `rotation: 55 * Math.PI / 180`, straightening to 0 as it lands. */
private const val ENTER_DEGREES = 55f

/** `card.scaleX = card.scaleY = 1.2` before the tween takes over. */
private const val ENTER_SCALE = 1.2f

/** `scaleX: 1.5` at rest — see the KDoc. */
private const val REST_SCALE = 1.5f
