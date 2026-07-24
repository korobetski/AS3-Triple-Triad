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
import androidx.compose.foundation.layout.width
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

/** Duration of a capture flip. `tto.utils.TTOCore.animate` uses 0.4s tweens. */
private const val FlipDurationMs = 400

/**
 * A card that flips around its vertical axis when tapped and comes back owned by
 * the other side — the visual half of a Triple Triad capture.
 *
 * The rotation uses [Animatable] rather than `rememberInfiniteTransition`: this is a
 * one-shot animation whose *completion* matters (the owner switch has to land exactly
 * at the halfway point, and the caller has to be told when the flip is over).
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
            .size(CardWidth, CardHeight)
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
                        animationSpec = tween(FlipDurationMs, easing = FastOutSlowInEasing),
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

/** The static face of a card: background, border, power digits and name. */
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
        CardDigits(
            card = card,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 4.dp),
        )
        Text(
            text = "Lv${card.level}",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 5.dp, top = 5.dp),
        )
        Text(
            text = card.name,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 6.dp, start = 3.dp, end = 3.dp),
        )
    }
}

/**
 * The four edge powers, arranged as in `tto.display.CardDigits`:
 * ```
 * private static const positions:Array = [{x:14,y:0},{x:26,y:6},{x:14,y:12},{x:2,y:6}];
 * ```
 * i.e. top, right, bottom, left over a half-transparent `cdbg` plate.
 */
@Composable
private fun CardDigits(card: Card, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(DigitsWidth, DigitsHeight)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(3.dp)),
    ) {
        Digit(card.top, 14.dp, 0.dp)
        Digit(card.right, 26.dp, 6.dp)
        Digit(card.bottom, 14.dp, 12.dp)
        Digit(card.left, 2.dp, 6.dp)
    }
}

@Composable
private fun Digit(power: Int, x: Dp, y: Dp) {
    Text(
        text = powerLabel(power),
        color = Color.White,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.offset(x = x - 4.dp, y = y).width(12.dp),
    )
}
