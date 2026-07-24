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

/** `new Quad(88, 118, ...)` in `Card.as:73`. */
internal val CardWidth = 88.dp
internal val CardHeight = 118.dp

/** `CardDigits` badge is a 36x24 diamond of four 12x12 digit textures. */
internal val DigitsWidth = 36.dp
internal val DigitsHeight = 24.dp
