package com.tripletriad.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

/**
 * The game's Material scheme, built from `BaseTTOTheme`'s constants.
 *
 * ### Why a scheme at all, when nothing draws from it directly
 *
 * Every Material control does. Before this existed the app ran on `darkColorScheme()` — Material's
 * default, whose primary is a lavender purple — and each of the five Material components in the
 * tree had to be hand-coloured at its call site to hide it: two `OutlinedTextField`s, a `Slider`, a
 * `FilterChip` and the `Button` behind `WideButton`. That is the shape of a missing theme: every
 * new control is one more place to remember, and the one that forgets is purple.
 *
 * ### The mapping, and the two choices in it
 *
 * Most slots are direct — `background`, `surface`, `onSurface`, `error`, `outline`. Two are not:
 *
 * - **`primary` is [CardBlue], not [SelectedText].** The AS3's only accent is
 *   `SELECTED_TEXT_COLOR`, an orange it uses for the *selected* item in a list, and Material's
 *   `primary` drives filled buttons, sliders and chips — every primary action, selected or not.
 *   Painting them orange would read as "everything is selected". The card blue is a real colour
 *   from the source (`Card.BLUE_COLOR`), it is what this port's buttons already were, and it
 *   belongs to the same palette as the board.
 * - **`secondary` is [SelectedText]**, which keeps the orange doing the job it does in the
 *   original: marking the current choice.
 */
private val TtoColorScheme = darkColorScheme(
    primary = CardBlue,
    onPrimary = LightText,
    secondary = SelectedText,
    onSecondary = SurfaceSunken,
    // The bright cyan of `largeBlueElementFormat`, which this app uses wherever something is
    // affirmative rather than merely present: a filled progress bar, an affordable price, a
    // complete deck, the splash's own line. `primary` is the *dark* card blue and cannot do that
    // job — a bar filled with it reads as empty.
    tertiary = CardBlueEdge,
    onTertiary = SurfaceSunken,
    background = Background,
    onBackground = LightText,
    surface = Surface,
    onSurface = LightText,
    // The row surface this port draws lists on, which is a shade above `surface`.
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = LightText,
    // `MODAL_OVERLAY_COLOR`'s own base, so a scrim and a sunken panel agree.
    surfaceContainerLowest = SurfaceSunken,
    outline = Outline,
    outlineVariant = Outline,
    error = Danger,
    onError = LightText,
    scrim = ModalOverlay,
)

/**
 * Corner radii.
 *
 * Feathers rounds nothing itself — every rounded edge in the original is a nine-slice texture out
 * of the UI atlas, which this port does not import. So there is nothing to transcribe and these are
 * the port's own, kept small: a 6 dp row and a 4 dp control is what the fourteen screens already
 * drew before the values were named.
 */
private val TtoShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
)

/**
 * The theme every screen renders inside.
 *
 * One theme and no light variant, which is a decision rather than an omission: the original is a
 * dark game with artwork drawn for a dark ground, and a light scheme would need a second set of
 * card frames before it was anything but inverted text on white.
 *
 * `docs/migration/08-PHASE-4-UI-LAYER.md` Task 4.1's snippet had this as
 * `val AppTheme = MaterialTheme(...)`, which does not compile — `MaterialTheme` is a `@Composable`
 * function and cannot be assigned to a top-level `val`. The document records that correction
 * already; this is the shape it settles on.
 */
@Composable
fun TripleTriadTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTtoColors provides TtoColors()) {
        MaterialTheme(
            colorScheme = TtoColorScheme,
            typography = appTypography(),
            shapes = TtoShapes,
            content = content,
        )
    }
}
