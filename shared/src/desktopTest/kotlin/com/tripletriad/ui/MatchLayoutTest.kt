package com.tripletriad.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tripletriad.model.HAND_SIZE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [matchLayout] is a pure function of two measured numbers, so the arrangement can be checked
 * without a screen — which is the reason it was extracted.
 *
 * Three earlier revisions of this screen estimated the space left for the board and got it
 * wrong on a landscape phone, twice producing hands drawn over the board. The invariant that
 * catches every one of those is [theArrangementAlwaysFitsInTheSpaceItWasGiven]: whatever the
 * bounds, the arrangement's own footprint must not exceed them.
 */
class MatchLayoutTest {
    @Test
    fun landscapePutsTheHandsBesideTheBoardInTwoColumns() {
        val layout = matchLayout(width = 914.dp, height = 385.dp)

        assertTrue(layout.landscape, "wider than tall is landscape")
        assertEquals(2, layout.handColumns, "a landscape hand is a block, not a strip")
        assertEquals(
            LANDSCAPE_HAND_ROWS,
            layout.handRows,
            "five cards over two columns needs three rows",
        )
    }

    @Test
    fun portraitPutsTheHandsAboveAndBelowInOneStrip() {
        val layout = matchLayout(width = 411.dp, height = 890.dp)

        assertTrue(!layout.landscape, "taller than wide is portrait")
        assertEquals(HAND_SIZE, layout.handColumns, "a portrait hand is one row of five")
        assertEquals(1, layout.handRows)
    }

    @Test
    fun aSquareViewportCountsAsLandscape() {
        // Not an important choice, but it has to be *a* choice: `width >= height`.
        assertTrue(matchLayout(600.dp, 600.dp).landscape)
    }

    @Test
    fun theArrangementAlwaysFitsInTheSpaceItWasGiven() {
        for ((width, height) in VIEWPORTS) {
            val layout = matchLayout(width, height)
            // Only meaningful above the floor: below it the cards are already as small as they
            // are allowed to get and overflow is preferred to illegible cards.
            if (layout.scale <= MIN_TESTED_SCALE) continue

            val (usedWidth, usedHeight) = footprint(layout)
            assertTrue(
                usedWidth <= width + TOLERANCE,
                "$width x $height: needs $usedWidth across, has $width",
            )
            assertTrue(
                usedHeight <= height + TOLERANCE,
                "$width x $height: needs $usedHeight down, has $height",
            )
        }
    }

    @Test
    fun theScaleGrowsWithTheViewportUntilItReachesTheAuthoredSize() {
        val small = matchLayout(400.dp, 200.dp).scale
        val medium = matchLayout(800.dp, 400.dp).scale
        val huge = matchLayout(4000.dp, 2000.dp).scale

        assertTrue(small < medium, "a bigger viewport should draw bigger cards")
        assertEquals(1f, huge, "cards never exceed the 88x118 they were authored at")
    }

    @Test
    fun theBoardIsNeverSmallerThanTheHandsAndFillsWhatTheyLeave() {
        // Portrait: a five-card strip is width-bound, so the board — three across — has height
        // to spare and must use it. This is the case that left a third of the screen empty.
        val portrait = matchLayout(411.dp, 890.dp)
        assertTrue(
            portrait.boardScale > portrait.scale,
            "portrait board ${portrait.boardScale} should exceed hand ${portrait.scale}",
        )

        for ((width, height) in VIEWPORTS) {
            val layout = matchLayout(width, height)
            assertTrue(
                layout.boardScale >= layout.scale,
                "$width x $height: board tiles must never be smaller than hand cards",
            )
        }
    }

    /** What the arrangement actually occupies: two hand areas plus the board, on both axes. */
    private fun footprint(layout: MatchLayout): Pair<Dp, Dp> {
        val boardWidth = (CardSpriteWidth * BOARD_SIDE + BoardGapTotal) * layout.boardScale
        val boardHeight = (CardSpriteHeight * BOARD_SIDE + BoardGapTotal) * layout.boardScale
        return if (layout.landscape) {
            (layout.handWidth * 2 + boardWidth) to maxOf(layout.handHeight, boardHeight)
        } else {
            maxOf(layout.handWidth, boardWidth) to (layout.handHeight * 2 + boardHeight)
        }
    }

    private companion object {
        const val BOARD_SIDE = 3
        const val LANDSCAPE_HAND_ROWS = 3

        /** `TileGap` four times over — the outer padding plus the two inner gaps. */
        val BoardGapTotal = 16.dp

        /** Rounding in `Dp` arithmetic, not a real overflow allowance. */
        val TOLERANCE = 0.5.dp

        const val MIN_TESTED_SCALE = 0.23f

        /** Phones both ways up, a tablet, a desktop window, and two degenerate cases. */
        val VIEWPORTS = listOf(
            411.dp to 890.dp,
            914.dp to 385.dp,
            360.dp to 640.dp,
            640.dp to 360.dp,
            800.dp to 1280.dp,
            1280.dp to 800.dp,
            1920.dp to 1080.dp,
            200.dp to 200.dp,
            100.dp to 900.dp,
        )
    }
}
