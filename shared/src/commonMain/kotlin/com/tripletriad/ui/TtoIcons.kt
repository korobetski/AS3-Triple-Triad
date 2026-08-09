package com.tripletriad.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The nine glyphs the shell needs, drawn here rather than depended on.
 *
 * ### Why not Material's icon set
 *
 * `material-icons-extended` is a single artifact of some twelve hundred vectors, and Compose
 * Multiplatform's own guidance is to stop shipping it — R8 strips what an Android build does not
 * use, and nothing strips it from a desktop jar or an iOS framework. Nine glyphs is not worth that,
 * and it is not worth a per-icon resource file either: these are eight to twelve path segments
 * each.
 *
 * ### Why they look like this
 *
 * They are drawn as **cards** wherever a card will do — [Play] is a fanned hand, [Collection] is a
 * grid of them — because that is the one shape this game has and Material's own set does not. The
 * rest ([Home], [Shop], [Person], [Help], [Options], [Logout], [Back]) follow Material's
 * conventions, since a bag that is not bag-shaped costs recognition and buys nothing.
 *
 * Every path is stroked rather than filled, at 1.8 units on a 24-unit grid — Material Symbols'
 * outlined weight — so they sit beside the game's own 40x40 pixel-art icons without one set
 * shouting over the other.
 */
@Suppress("MagicNumber") // Path coordinates on a 24-unit grid. Naming them would say less.
internal object TtoIcons {

    /** The dashboard: a roof over the character's own screen. */
    val Home: ImageVector by lazy {
        icon("Home") {
            moveTo(3f, 10.5f)
            lineTo(12f, 3.5f)
            lineTo(21f, 10.5f)
            lineTo(21f, 20f)
            lineTo(3f, 20f)
            close()
            moveTo(9.5f, 20f)
            lineTo(9.5f, 14f)
            lineTo(14.5f, 14f)
            lineTo(14.5f, 20f)
        }
    }

    /** Playing a match: three cards fanned, the middle one upright. */
    val Play: ImageVector by lazy {
        icon("Play") {
            moveTo(9.2f, 5.4f)
            lineTo(4.2f, 8.6f)
            lineTo(8.6f, 16.4f)
            moveTo(14.8f, 5.4f)
            lineTo(19.8f, 8.6f)
            lineTo(15.4f, 16.4f)
            moveTo(9f, 4f)
            lineTo(15f, 4f)
            lineTo(15f, 20f)
            lineTo(9f, 20f)
            close()
        }
    }

    /** The collection: nine cards on a shelf. */
    val Collection: ImageVector by lazy {
        icon("Collection") {
            for (row in 0..2) {
                for (column in 0..2) {
                    val x = 3.5f + column * 6.3f
                    val y = 3.5f + row * 6.3f
                    moveTo(x, y)
                    lineTo(x + 4.2f, y)
                    lineTo(x + 4.2f, y + 4.2f)
                    lineTo(x, y + 4.2f)
                    close()
                }
            }
        }
    }

    /** The shop: a bag with a handle. */
    val Shop: ImageVector by lazy {
        icon("Shop") {
            moveTo(4.5f, 8f)
            lineTo(19.5f, 8f)
            lineTo(18.3f, 20.5f)
            lineTo(5.7f, 20.5f)
            close()
            moveTo(8.5f, 8f)
            lineTo(8.5f, 6f)
            curveTo(8.5f, 4.1f, 9.9f, 3f, 12f, 3f)
            curveTo(14.1f, 3f, 15.5f, 4.1f, 15.5f, 6f)
            lineTo(15.5f, 8f)
        }
    }

    /** The record: head and shoulders. */
    val Person: ImageVector by lazy {
        icon("Person") {
            moveTo(12f, 4f)
            curveTo(14.2f, 4f, 15.6f, 5.6f, 15.6f, 7.8f)
            curveTo(15.6f, 10f, 14.2f, 11.6f, 12f, 11.6f)
            curveTo(9.8f, 11.6f, 8.4f, 10f, 8.4f, 7.8f)
            curveTo(8.4f, 5.6f, 9.8f, 4f, 12f, 4f)
            close()
            moveTo(4.5f, 20.5f)
            curveTo(4.5f, 16.4f, 7.9f, 14.2f, 12f, 14.2f)
            curveTo(16.1f, 14.2f, 19.5f, 16.4f, 19.5f, 20.5f)
        }
    }

    /** The rules: an open book, which is what the help screen is. */
    val Help: ImageVector by lazy {
        icon("Help") {
            moveTo(12f, 6.5f)
            curveTo(10f, 4.6f, 6.8f, 4.6f, 3.8f, 5.2f)
            lineTo(3.8f, 18.4f)
            curveTo(6.8f, 17.8f, 10f, 17.8f, 12f, 19.7f)
            curveTo(14f, 17.8f, 17.2f, 17.8f, 20.2f, 18.4f)
            lineTo(20.2f, 5.2f)
            curveTo(17.2f, 4.6f, 14f, 4.6f, 12f, 6.5f)
            close()
            moveTo(12f, 6.5f)
            lineTo(12f, 19.7f)
        }
    }

    /** The settings: three sliders, Material's `tune`. */
    val Options: ImageVector by lazy {
        icon("Options") {
            for ((index, y) in listOf(6.5f, 12f, 17.5f).withIndex()) {
                moveTo(3.5f, y)
                lineTo(20.5f, y)
                val knob = 8f + index * 4f
                moveTo(knob - 2f, y)
                curveTo(knob - 2f, y - 2f, knob + 2f, y - 2f, knob + 2f, y)
                curveTo(knob + 2f, y + 2f, knob - 2f, y + 2f, knob - 2f, y)
                close()
            }
        }
    }

    /** Leaving the character: a door and the way out of it. */
    val Logout: ImageVector by lazy {
        icon("Logout") {
            moveTo(14f, 4f)
            lineTo(4.5f, 4f)
            lineTo(4.5f, 20f)
            lineTo(14f, 20f)
            moveTo(11.5f, 12f)
            lineTo(20.5f, 12f)
            moveTo(17f, 8.5f)
            lineTo(20.5f, 12f)
            lineTo(17f, 15.5f)
        }
    }

    /** The chevron every screen's app bar carries. */
    val Back: ImageVector by lazy {
        icon("Back") {
            moveTo(15f, 4.5f)
            lineTo(7.5f, 12f)
            lineTo(15f, 19.5f)
        }
    }

    /**
     * One stroked path on a 24-unit grid.
     *
     * `defaultWidth`/`defaultHeight` are 24 dp because that is what an [androidx.compose.material3
     * .Icon] sizes itself to when it is given no modifier, and a glyph that had to be measured at
     * every call site would be nine chances to get one wrong.
     */
    private fun icon(name: String, segments: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = segments,
            )
        }.build()
}
