package com.tripletriad.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.powerLabel
import kotlinx.coroutines.launch

/** Duration of a capture flip. The AS3 yoyo is 4 x 0.1 s legs; see [FlippableCard]. */
private const val FLIP_DURATION_MS = 400

/**
 * A card that flips and comes back owned by the other side — the visual half of a
 * Triple Triad capture.
 *
 * The rotation uses [Animatable] rather than `rememberInfiniteTransition`: this is a
 * one-shot animation whose *completion* matters (the owner switch has to land exactly
 * at the halfway point, and the caller has to be told when the flip is over).
 *
 * **This is not the original animation.** `Card.flip()` does not rotate anything: it
 * runs a four-leg `scaleX` yoyo (1 -> 0 -> 1.2 -> 0 -> 1, 0.1 s per leg, `EASE_IN` on
 * the way in and `EASE_OUT` on the way out), swapping to the card back and changing
 * the colour at each pinch. A `rotationY` flip is the modern equivalent and reads
 * better on a high-DPI screen, but it is a deliberate substitution, not a port. If
 * pixel-parity with the original is a requirement, this has to be rewritten.
 */
@Composable
fun FlippableCard(
    card: Card,
    modifier: Modifier = Modifier,
    onOwnerChanged: (CardColor) -> Unit = {},
) {
    val rotation = remember { Animatable(0f) }
    var owner by remember(card.id) { mutableStateOf(card.owner) }
    var flipping by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .size(CardSpriteWidth, CardSpriteHeight)
            .graphicsLayer {
                // Assigning the state to the layer property of the same name would be a
                // no-op self-assignment; read the Animatable explicitly.
                rotationY = rotation.value
                cameraDistance = 12f * density
            }
            .clickable(enabled = !flipping) {
                scope.launch {
                    flipping = true
                    rotation.snapTo(0f)
                    var switched = false
                    rotation.animateTo(
                        targetValue = 180f,
                        animationSpec = tween(FLIP_DURATION_MS, easing = FastOutSlowInEasing),
                    ) {
                        // Swap the owner at the point where the card is edge-on, so the
                        // colour change is never visible mid-turn.
                        if (!switched && value >= 90f) {
                            switched = true
                            owner = owner.opposite()
                            onOwnerChanged(owner)
                        }
                    }
                    // At 180 deg with the face mirrored the card is pixel-identical to 0 deg,
                    // so resetting here is invisible and keeps the next flip starting from 0.
                    rotation.snapTo(0f)
                    flipping = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        CardFace(
            card = card.copy(owner = owner),
            modifier = Modifier.graphicsLayer {
                // Past 90 deg we are looking at the back of the layer: un-mirror the content.
                scaleX = if (rotation.value > 90f) -1f else 1f
            },
        )
    }
}

/**
 * The static face of a card.
 *
 * Everything is placed by absolute offset in the AS3 sprite's coordinate space, so the
 * numbers below can be checked line-for-line against `Card.as` — see the table in
 * [CardWidth]'s file. The face itself is the 88x118 colour quad; the surrounding
 * 104x128 sprite bounds are [FlippableCard]'s concern.
 */
@Composable
internal fun CardFace(card: Card, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .size(CardWidth, CardHeight)
            .clip(shape)
            .background(card.owner.background)
            .border(2.dp, card.owner.edge, shape),
    ) {
        // `Card.as:176-178` draws the `{rarity}stars` texture at (9, 6) in sprite space,
        // i.e. (1, 1) relative to the face. No such texture here, so: literal stars.
        Text(
            text = "★".repeat(card.rarity),
            color = Color(0xFFF2C14E),
            fontSize = 8.sp,
            modifier = Modifier.offset(x = 1.dp, y = 1.dp),
        )

        // `Card.as:181-183` draws `type-{type}` at (80, 3) in sprite space = (72, -2)
        // relative to the face, so the icon overhangs the top edge. Clamped to 0 here
        // because the face is clipped and a negative offset would be invisible.
        card.type?.let { type ->
            Text(
                text = type.name.take(2),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = 72.dp, y = 0.dp),
            )
        }

        // The original draws no name text at all — the name is baked into the per-card
        // artwork. This label is a PoC stand-in for the missing texture.
        Text(
            text = card.name,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 30.dp, start = 3.dp, end = 3.dp),
        )

        // (28, 88) in sprite space is (20, 83) relative to the face.
        CardDigits(
            card = card,
            modifier = Modifier.offset(
                x = DigitsOriginX - CardFaceOffsetX,
                y = DigitsOriginY - CardFaceOffsetY,
            ),
        )
    }
}

/** Face origin inside the 104x128 sprite: `colorBackground.x/.y` — `Card.as:74-75`. */
private val CardFaceOffsetX = 8.dp
private val CardFaceOffsetY = 5.dp

/**
 * The four edge powers, laid out exactly as `tto.display.CardDigits`:
 * ```
 * private static const positions:Array = [{x:14,y:0},{x:26,y:6},{x:14,y:12},{x:2,y:6}];
 * // power [top, right, bottom, left];
 * ```
 * Those are the *top-left corners* of 18x18 digit textures, over a 28x28 `cdbg` plate
 * drawn at (8, 1) with alpha 0.5. The digits therefore overhang the plate on the left
 * and the top, which is what gives the badge its diamond silhouette — so the cluster's
 * own bounds are 44x30, wider than the plate.
 */
@Composable
private fun CardDigits(card: Card, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(DigitsClusterWidth, DigitsClusterHeight)) {
        Box(
            modifier = Modifier
                .offset(x = DigitsPlateOffsetX, y = DigitsPlateOffsetY)
                .size(DigitsPlateSize)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(3.dp)),
        )
        Digit(card.top, x = 14.dp, y = 0.dp)
        Digit(card.right, x = 26.dp, y = 6.dp)
        Digit(card.bottom, x = 14.dp, y = 12.dp)
        Digit(card.left, x = 2.dp, y = 6.dp)
    }
}

/** Cluster bounds: x spans 2..26+18 = 44, y spans 0..12+18 = 30. */
private val DigitsClusterWidth = 44.dp
private val DigitsClusterHeight = 30.dp

/**
 * One power digit.
 *
 * [x] and [y] are the AS3 values unmodified — the top-left corner of an 18x18 texture.
 * The glyph is then centred inside that 18x18 box, which is what the digit textures
 * themselves do. An earlier revision subtracted 4 dp here to "centre" the text and
 * pushed the left digit to x = -2, off the plate.
 */
@Composable
private fun Digit(power: Int, x: Dp, y: Dp) {
    Box(
        modifier = Modifier.offset(x = x, y = y).size(DigitSize),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = powerLabel(power),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.wrapContentSize(),
        )
    }
}
