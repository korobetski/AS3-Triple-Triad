package com.tripletriad.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor

/**
 * The face of a card, at [scale] times its authored size.
 *
 * ### Layer order
 *
 * This is `Card.as`'s display list in `addChild` order, and the reason the layers are here
 * rather than baked into one image: the artwork is a 104x128 frame with a **translucent
 * centre**, and what shows through it is the owner's colour quad. One artwork therefore
 * serves both sides, which is how the original gets away with 263 images and not 526.
 *
 * | # | Layer | Where | Source |
 * |--:|---|---|---|
 * | 0 | `cardSelected` glow | (-16, -4) | `Card.as:66-70` — **not ported**, see below |
 * | 1 | owner colour quad 88x118 | (8, 5) | `Card.as:73-76` |
 * | 2 | artwork 104x128 | (0, 0) | `Card.as:169-170` |
 * | 3 | rarity row 29x28 | (9, 6) | `Card.as:176-178` |
 * | 4 | type icon 20x20 | (80, 3) | `Card.as:181-183` |
 * | 5 | `_modifier` text 32x32 | (36, 48) | `Card.as:81-85` — **not ported**, see below |
 * | 6 | digit cluster 44x30 | (28, 88) | `Card.as:88-90` |
 * | 7 | card back 104x128 | (0, 0) | `Card.as:93-94`, shown while flipping |
 *
 * Layer 0 is the selection glow; this port rings the card instead ([HandCard]), because the
 * glow texture is drawn outside even the sprite bounds and would have to grow every slot.
 * Layer 5 is the Ascension/Descension `±N` badge, which no rule in this UI switches on yet.
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
internal fun CardFace(
    card: Card,
    scale: Float = 1f,
    showBack: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val art = LocalCardArt.current
    val face = rememberCardFace(art, card)

    Box(modifier = modifier.size(CardSpriteWidth * scale, CardSpriteHeight * scale)) {
        // Layer 1. A `Quad` in the original, so a fill here and not a texture.
        Box(
            modifier = Modifier
                .offset(x = CardFaceOffsetX * scale, y = CardFaceOffsetY * scale)
                .size(CardWidth * scale, CardHeight * scale)
                .background(card.owner.background),
        )
        Layer(face, x = 0.dp, y = 0.dp, width = CardSpriteWidth, height = CardSpriteHeight, scale)
        Layer(art?.starsFor(card.rarity), RarityX, RarityY, RarityWidth, RarityHeight, scale)
        card.type?.let { Layer(art?.typeIcon(it), TypeX, TypeY, TypeSize, TypeSize, scale) }
        CardDigits(
            card = card,
            art = art,
            scale = scale,
            modifier = Modifier.offset(x = DigitsOriginX * scale, y = DigitsOriginY * scale),
        )
        if (showBack) {
            Layer(art?.back, 0.dp, 0.dp, CardSpriteWidth, CardSpriteHeight, scale)
        }
    }
}

/**
 * A face-down card in a colour, with **no card behind it**.
 *
 * `new Card()` followed by `draw('blue')` — the original's idiom for a card back that is
 * not any particular card. `PileOuFace` uses it for the three coin-flip cards and `Mogu`
 * for its stack.
 *
 * Deliberately not [CardFace]`(showBack = true)`, which was the first attempt and fails:
 * that reads `card.textureId` and asks for that card's artwork, so a placeholder card
 * throws `MissingResourceException` for a picture nothing was ever going to draw. The
 * covered layers are not merely hidden here, they are absent.
 *
 * @param color which side's colour shows through the back's transparent parts.
 */
@Composable
internal fun CardBack(color: CardColor, scale: Float = 1f, modifier: Modifier = Modifier) {
    val art = LocalCardArt.current
    Box(modifier = modifier.size(CardSpriteWidth * scale, CardSpriteHeight * scale)) {
        Box(
            modifier = Modifier
                .offset(x = CardFaceOffsetX * scale, y = CardFaceOffsetY * scale)
                .size(CardWidth * scale, CardHeight * scale)
                .background(color.background),
        )
        Layer(art?.back, 0.dp, 0.dp, CardSpriteWidth, CardSpriteHeight, scale)
    }
}

/**
 * The four edge powers, laid out exactly as `tto.display.CardDigits`:
 * ```
 * private static const positions:Array = [{x:14,y:0},{x:26,y:6},{x:14,y:12},{x:2,y:6}];
 * // power [top, right, bottom, left];
 * ```
 * Those are the *top-left corners* of 18x18 digit textures, over a 28x28 `cdbg` plate
 * drawn at (8, 1). The digits therefore overhang the plate on the left and the top, which
 * is what gives the badge its diamond silhouette — so the cluster's own bounds are 44x30,
 * wider than the plate.
 *
 * `CardDigits.as:29` sets `alpha = 0.5` on the plate, but the `cdbg` texture is already
 * semi-transparent, so nothing dims it again here.
 */
@Composable
private fun CardDigits(card: Card, art: CardArt?, scale: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(DigitsClusterWidth * scale, DigitsClusterHeight * scale)) {
        Glyph(art?.digitPlate, DigitsPlateOffsetX, DigitsPlateOffsetY, DigitsPlateSize, scale)
        Glyph(art?.digit(card.top), x = 14.dp, y = 0.dp, size = DigitSize, scale = scale)
        Glyph(art?.digit(card.right), x = 26.dp, y = 6.dp, size = DigitSize, scale = scale)
        Glyph(art?.digit(card.bottom), x = 14.dp, y = 12.dp, size = DigitSize, scale = scale)
        Glyph(art?.digit(card.left), x = 2.dp, y = 6.dp, size = DigitSize, scale = scale)
    }
}

/**
 * One absolutely-positioned layer: a `Starling.Image` at a fixed offset and size.
 *
 * A null [bitmap] leaves the slot empty rather than drawing a placeholder, so a card composes
 * correctly before its artwork has decoded and in a preview with no [CardArt] at all.
 *
 * Filtering is left at Compose's default (bilinear), which is also Starling's
 * (`TextureSmoothing.BILINEAR`). These are 104x128 textures authored for a 1:1 stage and drawn
 * here on a 2.6x-density screen, so they are being *up*scaled — nearest-neighbour would show
 * the card frame as a staircase.
 */
@Composable
private fun Layer(
    bitmap: ImageBitmap?,
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
    scale: Float,
) {
    val placed = Modifier.offset(x = x * scale, y = y * scale).size(width * scale, height * scale)
    if (bitmap == null) {
        Box(modifier = placed)
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = placed,
            contentScale = ContentScale.FillBounds,
        )
    }
}

/**
 * As [Layer], for the square atlas-backed glyphs.
 *
 * They arrive as [BitmapPainter]s holding a source rectangle rather than as separate bitmaps —
 * see [CardArt] — so they need the painter overload of `Image`.
 */
@Composable
private fun Glyph(painter: Painter?, x: Dp, y: Dp, size: Dp, scale: Float) {
    val placed = Modifier.offset(x = x * scale, y = y * scale).size(size * scale)
    if (painter == null) {
        Box(modifier = placed)
    } else {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = placed,
            contentScale = ContentScale.FillBounds,
        )
    }
}

/** Face origin inside the 104x128 sprite: `colorBackground.x/.y` — `Card.as:74-75`. */
private val CardFaceOffsetX = 8.dp
private val CardFaceOffsetY = 5.dp

/** `rc.x = 9; rc.y = 6` — `Card.as:177-178`. Size read off the `card_rarities` PNGs. */
private val RarityX = 9.dp
private val RarityY = 6.dp
private val RarityWidth = 29.dp
private val RarityHeight = 28.dp

/** `type.x = 80; type.y = 3` — `Card.as:182-183`. Size read off the `card_types` PNGs. */
private val TypeX = 80.dp
private val TypeY = 3.dp
private val TypeSize = 20.dp

/** Cluster bounds: x spans 2..26+18 = 44, y spans 0..12+18 = 30. */
private val DigitsClusterWidth = 44.dp
private val DigitsClusterHeight = 30.dp
