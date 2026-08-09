package com.tripletriad.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * `theme/BaseTTOTheme.as:124-137`, transcribed. The AS3 writes them as `uint` RGB with no alpha
 * channel, so each gains an `FF`.
 *
 * `docs/migration/08-PHASE-4-UI-LAYER.md` Task 4.1 lists most of these correctly and two of them
 * wrongly: `0xFF1a1a1a` and `0xFF2a2a2a` appear in its snippet and in no AS3 file. The values below
 * are the ones that are actually in the source.
 */

/** `PRIMARY_BACKGROUND_COLOR`. */
internal val Background = Color(0xFF202020)

/** `LIST_BACKGROUND_COLOR`, and also `DARK_DISABLED_TEXT_COLOR` — the AS3 reuses the value. */
internal val Surface = Color(0xFF383430)

/** `GROUPED_LIST_HEADER_BACKGROUND_COLOR`, which is also the footer's. */
internal val SurfaceRaised = Color(0xFF2E2A26)

/** `TAB_BACKGROUND_COLOR`, and also `DARK_TEXT_COLOR`. */
internal val SurfaceSunken = Color(0xFF1A1816)

/** `LIGHT_TEXT_COLOR`. */
internal val LightText = Color(0xFFE5E5E5)

/** `SELECTED_TEXT_COLOR` — the accent, and the only warm colour the theme declares. */
internal val SelectedText = Color(0xFFFF9900)

/** `DISABLED_TEXT_COLOR`. */
internal val DisabledText = Color(0xFF8A8A8A)

/** `MODAL_OVERLAY_COLOR`, at its declared `MODAL_OVERLAY_ALPHA` of 0.8. */
internal val ModalOverlay = Color(0xFF29241E).copy(alpha = 0.8f)

/*
 * `display/Card.as:29-31`. These are card colours rather than theme colours — the AS3 keeps them in
 * the display class and not in the theme — and Material's `ColorScheme` has no slot that means
 * "the blue player", so they are carried in [TtoColors] instead of being forced into one.
 */

/** `Card.GREY_COLOR`. */
internal val CardGrey = Color(0xFF5A595A)

/** `Card.BLUE_COLOR`. */
internal val CardBlue = Color(0xFF2D4660)

/** `Card.RED_COLOR`. */
internal val CardRed = Color(0xFF602D2D)

/**
 * `largeBlueElementFormat` / `largeRedElementFormat` — `BaseTTOTheme.as:1537-1544`.
 *
 * The *text* colours of the two sides, which is why they are so much brighter than the two card
 * quads above. The port draws them as the card's edge, which the AS3 does not have: its frame is
 * part of the per-card artwork. Task 4.1's snippet mixed these two pairs up, naming the text
 * colours as the card backgrounds.
 */
internal val CardBlueEdge = Color(0xFF43A7C8)
internal val CardRedEdge = Color(0xFFBB594F)

/**
 * A row's outline, and the only colour here with no AS3 source.
 *
 * Feathers draws a list row's edge with a nine-slice texture out of the UI atlas, which this port
 * does not import — see `CardListScreen` for the same decision about card thumbnails. A one-dp line
 * needs a colour, and this is [Surface] lightened until it reads as an edge against it.
 */
internal val Outline = Color(0xFF56504A)

/*
 * The board's own three colours, which lived in `MatchBoard.kt` as private top-level vals until
 * the theme was the only place left that could not reach them. They have no AS3 source — Feathers
 * drew an empty tile with `emptyTileSkin` out of the UI atlas, which this port does not import — so
 * these are the port's own. They are here rather than there because a colour a screen keeps to
 * itself is a screen that will never follow a theme.
 */

/** An empty cell: cooler than [SurfaceSunken], so the grid reads as a board and not as a list. */
internal val BoardTile = Color(0xFF1E2230)

/** Its edge — [BoardTile] lightened until the 3x3 grid is legible on the background. */
internal val BoardTileOutline = Color(0xFF3A4152)

/**
 * The ring around the held card and around the cell being aimed at.
 *
 * Warmer than [SelectedText] and deliberately not equal to it: the accent marks *the current
 * choice* across the app, and on the board it would land next to five orange element glyphs.
 */
internal val SelectionRing = Color(0xFFF2C14E)

/**
 * The destructive-confirmation outline.
 *
 * `Button.ALTERNATE_STYLE_NAME_DANGER_BUTTON` is the AS3's name for it (`InventoryScreen.as:138`)
 * and it is a texture, not a colour, so there is nothing to transcribe. Material's `error` slot is
 * where it belongs and this is what fills it.
 */
internal val Danger = Color(0xFFE05252)

/**
 * Game colours Material's [androidx.compose.material3.ColorScheme] has no slot for.
 *
 * A `ColorScheme` is thirty-odd named roles — primary, surface, error — and "the blue player's
 * card" is not one of them. Forcing them into `tertiary` and `tertiaryContainer` would make every
 * call site read as a lie about what it is drawing, so they travel beside the scheme instead. This
 * is the shape Task 4.1 sketched, with the values corrected.
 */
@Immutable
data class TtoColors(
    val cardBlue: Color = CardBlue,
    val cardRed: Color = CardRed,
    val cardGrey: Color = CardGrey,
    val cardBlueEdge: Color = CardBlueEdge,
    val cardRedEdge: Color = CardRedEdge,
    /** A row that is the current choice: the fill tint and the outline. */
    val selectedFill: Color = CardBlue.copy(alpha = 0.28f),
    val selectedOutline: Color = CardBlue,
    /** The two boon markers and an opponent row's rules — a temporary effect. */
    val transient: Color = SelectedText,
    /** An empty board cell, its edge, and the ring on whatever is currently being aimed. */
    val boardTile: Color = BoardTile,
    val boardTileOutline: Color = BoardTileOutline,
    val selectionRing: Color = SelectionRing,
    /** Behind everything, and darker than [Background] so a screen's content reads as a layer. */
    val backdrop: Color = SurfaceSunken,
)

/**
 * [TtoColors] for the tree below [TripleTriadTheme].
 *
 * `staticCompositionLocalOf` rather than `compositionLocalOf`: the value never changes for the life
 * of the app — there is one theme and no light variant — so the reader-tracking a dynamic local
 * pays for would buy nothing.
 */
val LocalTtoColors = staticCompositionLocalOf { TtoColors() }
