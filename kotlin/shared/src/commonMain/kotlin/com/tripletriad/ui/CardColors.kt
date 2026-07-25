package com.tripletriad.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tripletriad.model.CardColor

/**
 * Colours taken verbatim from `tto.display.Card`:
 * ```
 * public static const GREY_COLOR:uint = 0x5a595a;
 * public static const BLUE_COLOR:uint = 0x2d4660;
 * public static const RED_COLOR:uint  = 0x602d2d;
 * ```
 */
internal val GreyCard = Color(0xFF5A595A)
internal val BlueCard = Color(0xFF2D4660)
internal val RedCard = Color(0xFF602D2D)

/**
 * NOT from the AS3 source. The original has no border colour: the card frame is part
 * of the per-card artwork drawn over the flat [BlueCard] / [RedCard] quad. These two
 * are stand-ins so the PoC has a visible edge without any assets, and they must be
 * dropped once the real textures are wired up.
 */
internal val BlueEdge = Color(0xFF43A7C8)
internal val RedEdge = Color(0xFFBB594F)

internal val CardColor.background: Color
    get() = when (this) {
        CardColor.BLUE -> BlueCard
        CardColor.RED -> RedCard
    }

internal val CardColor.edge: Color
    get() = when (this) {
        CardColor.BLUE -> BlueEdge
        CardColor.RED -> RedEdge
    }

/*
 * Geometry, all of it read off `sources/src/tto/display/Card.as` and
 * `sources/src/tto/display/CardDigits.as`, with texture sizes from
 * `sources/assets/digits/digits.xml`. Starling works in points at scale 1, so these
 * map 1:1 onto dp.
 *
 *   Card sprite            104 x 128, pivot (52, 64)          Card.as:60-63
 *   colour quad             88 x 118 at (8, 5)                Card.as:73-75
 *   rarity stars                     at (9, 6)                Card.as:176-178
 *   type icon                        at (80, 3)               Card.as:181-183
 *   modifier field          32 x  32 at (36, 48)              Card.as:81-83
 *   digit cluster                    at (28, 88)              Card.as:88-90
 *     `cdbg` plate          28 x  28 at (8, 1), alpha 0.5     CardDigits.as:26-29
 *     digit textures        18 x  18                          digits.xml
 *       top                          at (14, 0)               CardDigits.as:13
 *       right                        at (26, 6)
 *       bottom                       at (14, 12)
 *       left                         at (2, 6)
 *
 * Note the face is centred in the sprite: 8 dp of horizontal and 5 dp of vertical
 * margin on each side. The margin exists for the `cardSelected` glow, which is drawn
 * at (-16, -4) and so bleeds past even the sprite bounds.
 */

/** `this.width = 104; this.height = 128` — `Card.as:60-61`. */
internal val CardSpriteWidth = 104.dp
internal val CardSpriteHeight = 128.dp

/** `new Quad(88, 118, 0x5a595a)` — `Card.as:73`. The coloured face, not the sprite. */
internal val CardWidth = 88.dp
internal val CardHeight = 118.dp

/** `_digits.x = 28; _digits.y = 88` — `Card.as:89-90`, relative to the sprite. */
internal val DigitsOriginX = 28.dp
internal val DigitsOriginY = 88.dp

/** `cdbg` is 28x28 and sits at (8, 1) inside the digit cluster. */
internal val DigitsPlateSize = 28.dp
internal val DigitsPlateOffsetX = 8.dp
internal val DigitsPlateOffsetY = 1.dp

/** Every `cd*` subtexture in `digits.xml` is 18x18. */
internal val DigitSize = 18.dp
