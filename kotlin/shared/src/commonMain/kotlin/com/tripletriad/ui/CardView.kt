package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import com.tripletriad.model.Card
import com.tripletriad.model.powerLabel

/**
 * The static face of a card, at [scale] times its authored size.
 *
 * Everything is placed by absolute offset in the AS3 sprite's coordinate space, so the
 * numbers below can be checked line-for-line against `Card.as` — see the table in
 * `CardColors.kt`. The face itself is the 88x118 colour quad; the surrounding 104x128
 * sprite bounds are not modelled.
 *
 * ### Why every dimension is multiplied rather than the layer scaled
 *
 * The obvious way to shrink this is to measure it at full size and scale the render layer
 * (`requiredSize` + `graphicsLayer { scaleX = scale }`). That was the first implementation
 * and it was wrong: the composable then *reports* a small size while *drawing* a large one,
 * so anything that puts it in an offscreen layer clips it. In particular the dimmed hand
 * applies `alpha`, which forces exactly such a layer — so the waiting side's cards rendered
 * as slivers while the active side's, drawn straight into the parent, looked fine. Multiplying
 * the geometry keeps drawn bounds and reported bounds identical, which is the only version of
 * this that composes safely.
 */
@Composable
internal fun CardFace(card: Card, scale: Float = 1f, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(FaceCorner * scale)
    Box(
        modifier = modifier
            .size(CardWidth * scale, CardHeight * scale)
            .clip(shape)
            .background(card.owner.background)
            .border(FaceBorder * scale, card.owner.edge, shape),
    ) {
        // `Card.as:176-178` draws the `{rarity}stars` texture at (9, 6) in sprite space,
        // i.e. (1, 1) relative to the face. No such texture here, so: literal stars.
        Text(
            text = "★".repeat(card.rarity),
            color = Color(0xFFF2C14E),
            fontSize = StarsFontSize * scale,
            modifier = Modifier.offset(x = 1.dp * scale, y = 1.dp * scale),
        )

        // `Card.as:181-183` draws `type-{type}` at (80, 3) in sprite space = (72, -2)
        // relative to the face, so the icon overhangs the top edge. Clamped to 0 here
        // because the face is clipped and a negative offset would be invisible.
        card.type?.let { type ->
            Text(
                text = type.name.take(TYPE_LABEL_CHARS),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = TypeFontSize * scale,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = TypeOffsetX * scale, y = 0.dp),
            )
        }

        // The original draws no name text at all — the name is baked into the per-card
        // artwork. This label is a PoC stand-in for the missing texture.
        Text(
            text = card.name,
            color = Color.White,
            fontSize = NameFontSize * scale,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(
                    top = NamePaddingTop * scale,
                    start = NamePaddingSide * scale,
                    end = NamePaddingSide * scale,
                ),
        )

        // (28, 88) in sprite space is (20, 83) relative to the face.
        CardDigits(
            card = card,
            scale = scale,
            modifier = Modifier.offset(
                x = (DigitsOriginX - CardFaceOffsetX) * scale,
                y = (DigitsOriginY - CardFaceOffsetY) * scale,
            ),
        )
    }
}

/** Face origin inside the 104x128 sprite: `colorBackground.x/.y` — `Card.as:74-75`. */
private val CardFaceOffsetX = 8.dp
private val CardFaceOffsetY = 5.dp

/*
 * Not from the AS3 source: the original's frame and glyphs are textures, so a corner
 * radius, a border width and four font sizes have to be invented for the PoC. Named
 * rather than inlined only so they can all be scaled in one place.
 */
private val FaceCorner = 6.dp
private val FaceBorder = 2.dp
private val StarsFontSize = 8.sp
private val TypeFontSize = 8.sp
private val TypeOffsetX = 72.dp
private val NameFontSize = 10.sp
private val NamePaddingTop = 30.dp
private val NamePaddingSide = 3.dp
private val DigitFontSize = 13.sp
private const val TYPE_LABEL_CHARS = 2

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
private fun CardDigits(card: Card, scale: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(DigitsClusterWidth * scale, DigitsClusterHeight * scale)) {
        Box(
            modifier = Modifier
                .offset(x = DigitsPlateOffsetX * scale, y = DigitsPlateOffsetY * scale)
                .size(DigitsPlateSize * scale)
                .background(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(PlateCorner * scale),
                ),
        )
        Digit(card.top, x = 14.dp, y = 0.dp, scale = scale)
        Digit(card.right, x = 26.dp, y = 6.dp, scale = scale)
        Digit(card.bottom, x = 14.dp, y = 12.dp, scale = scale)
        Digit(card.left, x = 2.dp, y = 6.dp, scale = scale)
    }
}

/** Cluster bounds: x spans 2..26+18 = 44, y spans 0..12+18 = 30. */
private val DigitsClusterWidth = 44.dp
private val DigitsClusterHeight = 30.dp
private val PlateCorner = 3.dp

/**
 * One power digit.
 *
 * [x] and [y] are the AS3 values unmodified — the top-left corner of an 18x18 texture,
 * scaled by [scale] like everything else. The glyph is then centred inside that box, which
 * is what the digit textures themselves do. An earlier revision subtracted 4 dp here to
 * "centre" the text and pushed the left digit to x = -2, off the plate.
 */
@Composable
private fun Digit(power: Int, x: Dp, y: Dp, scale: Float) {
    Box(
        modifier = Modifier.offset(x = x * scale, y = y * scale).size(DigitSize * scale),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = powerLabel(power),
            color = Color.White,
            fontSize = DigitFontSize * scale,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.wrapContentSize(),
        )
    }
}
