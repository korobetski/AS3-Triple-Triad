package com.tripletriad.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.CardType
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.HandVisibility
import com.tripletriad.model.MatchState
import com.tripletriad.model.PlacedCard
import com.tripletriad.ui.theme.LocalTtoColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Red hand, board, blue hand — as a row in landscape, a column in portrait.
 *
 * `SpaceBetween` puts the board dead centre: both hand areas are given the same fixed size by
 * [MatchLayout], so the board does not drift as a hand empties.
 *
 * Also the drag's own scope. [BoardDragState] is hoisted here because it is the nearest thing that
 * contains both a hand and the board, and the floating card is drawn here for the same reason —
 * anywhere lower and it would be clipped by the hand it came out of.
 */
@Composable
@Suppress("LongParameterList")
internal fun PlayArea(
    state: MatchState,
    selected: Card?,
    visibility: HandVisibility,
    layout: MatchLayout,
    playable: List<Card>,
    onSelect: (Card) -> Unit,
    onPlace: (Int) -> Unit,
    onDrop: (Card, Int) -> Unit,
) {
    val drag = rememberBoardDragState()
    val hand: @Composable (CardColor) -> Unit = { owner ->
        HandArea(
            state = state,
            owner = owner,
            selected = selected,
            visibility = visibility,
            layout = layout,
            playable = playable,
            drag = drag,
            onSelect = onSelect,
            onDrop = onDrop,
        )
    }
    val board: @Composable () -> Unit = {
        BoardGrid(
            state = state,
            scale = layout.boardScale,
            drag = drag,
            // Armed by either gesture: a tapped card and a lifted one both need somewhere to go.
            armed = selected != null || drag.isDragging,
            onPlace = onPlace,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { drag.origin = it.positionInRoot() },
    ) {
        PlayAreaContents(layout = layout, hand = hand, board = board)
        DragGhost(drag = drag, scale = layout.scale)
    }
}

/** The three panels, arranged for the orientation. */
@Composable
private fun PlayAreaContents(
    layout: MatchLayout,
    hand: @Composable (CardColor) -> Unit,
    board: @Composable () -> Unit,
) {
    if (layout.landscape) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            hand(CardColor.RED)
            board()
            hand(CardColor.BLUE)
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            hand(CardColor.RED)
            board()
            hand(CardColor.BLUE)
        }
    }
}

/**
 * The card under the finger, drawn where the finger is.
 *
 * `Card.onTouch` builds a second `Card` and hands it to `DragDropManager.startDrag` as the drag
 * avatar (`Card.as:141-144`), leaving the original in the hand — so the ghost is the original's
 * behaviour and not a Compose necessity. The card in the hand dims rather than disappearing, which
 * is what keeps the hand from re-laying-out under a gesture that has not finished.
 *
 * Centred on the finger, so the cell being aimed at is the one under the card's middle and the one
 * [BoardDragState.hovered] is testing.
 */
@Composable
private fun DragGhost(drag: BoardDragState, scale: Float) {
    val card = drag.card ?: return
    if (!drag.pointer.isSpecified) return
    val width = CardSpriteWidth * scale
    val height = CardSpriteHeight * scale

    Box(
        modifier = Modifier
            .offset {
                val local = drag.pointer - drag.origin
                IntOffset(
                    (local.x - (width.toPx() / 2)).roundToInt(),
                    (local.y - (height.toPx() / 2)).roundToInt(),
                )
            }
            .graphicsLayer { alpha = DRAG_GHOST_ALPHA },
    ) {
        CardFace(card = card, scale = scale)
    }
}

/**
 * The 3×3 board. Empty cells show their element, if the board has one.
 *
 * Every cell is also a drop target: it registers its own bounds with [drag] and lights up while the
 * finger is over it. `Tile.onDragEnter` accepts the drag and `onDragDrop` refuses an occupied cell
 * (`Tile.as:107-127`), which is the same pair of rules — an occupied cell simply never highlights,
 * so the refusal is visible before the finger lifts rather than after.
 *
 * @param armed a card is in hand and waiting for a cell. Every free cell outlines faintly while it
 *   is — "where can this go" answered before the attempt rather than by it, which matters most on a
 *   phone, where the finger covers the cell it is aiming at.
 */
@Composable
private fun BoardGrid(
    state: MatchState,
    scale: Float,
    drag: BoardDragState,
    armed: Boolean,
    onPlace: (Int) -> Unit,
) {
    val hovered = drag.hovered()

    Column(
        modifier = Modifier.testTag(BOARD_TEST_TAG).padding(TileGap * scale),
        verticalArrangement = Arrangement.spacedBy(TileGap * scale),
    ) {
        for (row in 0 until BOARD_WIDTH) {
            Row(horizontalArrangement = Arrangement.spacedBy(TileGap * scale)) {
                for (column in 0 until BOARD_WIDTH) {
                    val position = row * BOARD_WIDTH + column
                    val free = state.board.isEmpty(position)

                    DisposableEffect(position) {
                        onDispose { drag.unregisterCell(position) }
                    }

                    TileCell(
                        placed = state.board[position],
                        element = state.board.elements[position],
                        scale = scale,
                        isTarget = hovered == position && free,
                        isOpen = armed && free,
                        modifier = Modifier
                            .testTag(tileTestTag(position))
                            .onGloballyPositioned { coordinates ->
                                // Only a free cell registers, so a drag over a taken one finds
                                // nothing and [BoardDragState.drop] returns null.
                                if (free) {
                                    drag.registerCell(position, coordinates.boundsInRoot())
                                } else {
                                    drag.unregisterCell(position)
                                }
                            }
                            .clickable { onPlace(position) },
                    )
                }
            }
        }
    }
}

/**
 * One cell.
 *
 * @param isTarget the finger is over it with a card, and it can take one. The border is what says
 *   so — Feathers drew a `dropIndicatorSkin` over the whole tile, and a border is the same claim
 *   without an atlas.
 * @param isOpen it could take the card currently in hand, but is not the one being aimed at. The
 *   same ring at a third of its weight: three states on one border, so a cell never has to grow.
 */
@Composable
@Suppress("LongParameterList")
private fun TileCell(
    placed: PlacedCard?,
    element: CardType?,
    scale: Float,
    isTarget: Boolean,
    isOpen: Boolean,
    modifier: Modifier,
) {
    val game = LocalTtoColors.current

    Box(
        modifier = modifier
            .size(CardSpriteWidth * scale, CardSpriteHeight * scale)
            .clip(TileShape)
            .background(game.boardTile)
            .border(
                width = if (isTarget) SelectionRingWidth else 1.dp,
                color = when {
                    isTarget -> game.selectionRing
                    isOpen -> game.selectionRing.copy(alpha = OPEN_CELL_ALPHA)
                    else -> game.boardTileOutline
                },
                shape = TileShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (placed == null) {
            element?.let {
                Text(
                    text = it.name.take(ELEMENT_LABEL_CHARS),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    fontSize = ElementFontSize * scale,
                )
            }
        } else {
            BoardCard(placed, scale)
        }
    }
}

/**
 * A placed card that flips when its owner changes.
 *
 * Re-triggered by a [LaunchedEffect] on the owner rather than by a tap: on the board a flip is
 * something the rules *did*, not something the player asked for.
 *
 * **A port now, not a substitution.** `Card.flip()` (`Card.as:249-291`) chains four 0.1 s
 * tweens — `flip` → `yoyo` → `unflip` → `yoyo2` — squashing `scaleY` to 0 and back twice while
 * `scaleX` widens to 1.2 for the duration. The colour switches and the back appears at the first
 * pinch; the new face returns at the second.
 *
 * An earlier revision used a `rotationY` half-turn instead, which **mirrored the card's contents
 * between 90° and 180°** — every glyph on it drawn backwards for a fifth of a second. A squash
 * cannot do that, because the scale never goes negative. The original's choice was the right one.
 */
@Composable
private fun BoardCard(placed: PlacedCard, scale: Float) {
    val squashY = remember { Animatable(1f) }
    val stretchX = remember { Animatable(1f) }
    val landing = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(placed.owner) }
    var showBack by remember { mutableStateOf(false) }

    // `afterFly`'s second tween. It runs once, on the composition that first has a card in this
    // cell, which is exactly when a card is played onto it — [TileCell] composes nothing here
    // while the cell is empty, so the state above is created fresh with the card.
    LaunchedEffect(Unit) {
        landing.animateTo(1f, tween(LAND_MS, easing = EaseOut))
    }

    LaunchedEffect(placed.owner) {
        if (shown == placed.owner) return@LaunchedEffect
        // `horizon = false` is the default and the only value the match screens pass, so the
        // squash is vertical and the widening horizontal.
        coroutineScope {
            launch { stretchX.animateTo(FLIP_STRETCH, tween(FLIP_LEG_MS, easing = EaseIn)) }
            squashY.animateTo(0f, tween(FLIP_LEG_MS, easing = EaseIn))
        }
        shown = placed.owner // yoyo(): switchColor()
        showBack = true // yoyo(): hide()
        squashY.animateTo(FLIP_STRETCH, tween(FLIP_LEG_MS, easing = EaseOut))
        squashY.animateTo(0f, tween(FLIP_LEG_MS, easing = EaseIn)) // unflip()
        showBack = false // yoyo2(): show()
        coroutineScope {
            launch { stretchX.animateTo(1f, tween(FLIP_LEG_MS, easing = EaseOut)) }
            squashY.animateTo(1f, tween(FLIP_LEG_MS, easing = EaseOut))
        }
    }

    CardFace(
        card = placed.card.copy(owner = shown),
        scale = scale,
        showBack = showBack,
        modifier = Modifier.graphicsLayer {
            // The landing and the flip multiply rather than override: a card captured while it
            // is still settling keeps settling. They cannot both be at rest and disagree, since
            // `landing` only ever runs once and only at the start.
            val arriving = lerp(LAND_SCALE, 1f, landing.value)
            scaleX = stretchX.value * arriving
            scaleY = squashY.value * arriving
            rotationZ = LAND_DEGREES * (1f - landing.value)
            translationX = LAND_OFFSET_X * size.width * (1f - landing.value)
            translationY = LAND_OFFSET_Y * size.height * (1f - landing.value)
            alpha = landing.value
        },
    )
}

private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

/**
 * One side's remaining cards, in a fixed-size box so the board stays put as the hand empties.
 *
 * Dimmed when it is not that side's turn. [HAND_SIZE] slots are always laid out; the empty ones
 * are spacers, which is what holds the arrangement steady.
 */
@Composable
@Suppress("LongParameterList")
private fun HandArea(
    state: MatchState,
    owner: CardColor,
    selected: Card?,
    visibility: HandVisibility,
    layout: MatchLayout,
    playable: List<Card>,
    drag: BoardDragState,
    onSelect: (Card) -> Unit,
    onDrop: (Card, Int) -> Unit,
) {
    val cards = state.hands[owner].orEmpty()
    val active = state.currentPlayer == owner
    val gap = HandGap * layout.scale

    Box(
        modifier = Modifier
            .size(layout.handWidth, layout.handHeight)
            .graphicsLayer { alpha = if (active) 1f else INACTIVE_HAND_ALPHA },
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0 until layout.handRows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (column in 0 until layout.handColumns) {
                        val slot = row * layout.handColumns + column
                        val card = cards.getOrNull(slot)
                        if (card == null) {
                            Spacer(
                                Modifier.size(
                                    CardSpriteWidth * layout.scale,
                                    CardSpriteHeight * layout.scale,
                                ),
                            )
                        } else {
                            // Keyed by the card, not by the slot. Slots close up when a card is
                            // played, so without this every slot behind the played one is handed
                            // a different card and silently keeps the previous one's composition
                            // state. That is what made cards draw each other's artwork
                            // (`rememberCardFace`, and `CardFaceTest`); nothing else in a slot
                            // holds state today, and this is what stops the next thing that does.
                            key(card.textureId) {
                                HandCard(
                                    card = card,
                                    owner = owner,
                                    slot = slot,
                                    isSelected = active && selected?.id == card.id,
                                    active = active,
                                    // Only the player's own playable cards are draggable.
                                    // `Card._draggable` is the same gate (`Card.as:137`), and it
                                    // matters more here than it looks: dragging a card that
                                    // `RULE_ORDER` forbids and having the drop silently do nothing
                                    // is worse feedback than not being able to lift it.
                                    drag = drag.takeIf {
                                        owner == CardColor.BLUE && active && card in playable
                                    },
                                    onDrop = onDrop,
                                    // The player always sees their own hand whatever the Open rule
                                    // says — `openPhase` assigns `RULE_ALL_OPEN` to `bluePlayer` on
                                    // both of its branches (`BaseMatchScreen.as:172`, `:176`), so
                                    // Open is only ever about the opponent.
                                    faceUp = owner == CardColor.BLUE || visibility.isVisible(card),
                                    scale = layout.scale,
                                    onSelect = onSelect,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One selectable card in a hand, and — for the player's own playable cards — one that can be picked
 * up and carried to a cell.
 *
 * The selection ring is a border on the card's own bounds rather than a frame around them: a
 * frame would have to grow the slot, and a growing slot moves every card beside it.
 *
 * ### Tapping still works, and that is deliberate
 *
 * Task 4.7 ends on "do not ship drag-only", and the original does not: `Card.onTouch` dispatches
 * `TRIGGERED` on a tap *and* starts a drag on a move, `Tile.onTouch` handles the second tap, and
 * `BaseMatchScreen` listens for both. Dragging a card into a 3×3 grid on a phone is fiddly; tapping
 * twice is not. Compose keeps them apart on its own — `clickable` cancels once the pointer passes
 * touch slop, which is the same threshold the drag starts at.
 *
 * @param drag the board's drag state, or **null** when this card may not be dragged. Null rather
 *   than a boolean beside it, so a card that cannot be dragged cannot reach the state at all.
 */
@Composable
@Suppress("LongParameterList")
private fun HandCard(
    card: Card,
    owner: CardColor,
    slot: Int,
    isSelected: Boolean,
    active: Boolean,
    faceUp: Boolean,
    scale: Float,
    drag: BoardDragState?,
    onSelect: (Card) -> Unit,
    onDrop: (Card, Int) -> Unit,
) {
    // Captured so the pointer can be converted out of this card's own space and into root, which
    // is where the cells registered their bounds. See [BoardDragState].
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // The second read needs no `?.`: reaching it means the first comparison was true, and a null
    // `drag` cannot equal a card id — Kotlin 2.4's smart cast now says so.
    val isBeingDragged = drag?.card?.id == card.id && drag.isDragging

    Box(
        modifier = Modifier
            .testTag(handCardTestTag(owner, slot))
            .onGloballyPositioned { coordinates = it }
            .then(
                if (drag == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(card.id, drag) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                coordinates?.let { drag.start(card, it.localToRoot(offset)) }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                coordinates?.let { drag.moveTo(it.localToRoot(change.position)) }
                            },
                            onDragEnd = { drag.drop()?.let { (c, at) -> onDrop(c, at) } },
                            onDragCancel = { drag.cancel() },
                        )
                    }
                },
            )
            .clickable(enabled = active) { onSelect(card) },
    ) {
        // Dimmed rather than removed while it is in the air: taking it out of the hand would
        // re-lay-out the four cards beside it in the middle of the gesture.
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = if (isBeingDragged) DRAG_SOURCE_ALPHA else 1f
            },
        ) {
            CardFace(card = card, scale = scale, showBack = !faceUp)
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(CardSpriteWidth * scale, CardSpriteHeight * scale)
                    .border(SelectionRingWidth, LocalTtoColors.current.selectionRing, TileShape),
            )
        }
    }
}

/**
 * How the board and the two hands are arranged, and at what size.
 *
 * @property landscape true when the hands sit either side of the board rather than above and
 *   below it.
 * @property handColumns cards across in one hand area — five in a portrait strip, two in a
 *   landscape block.
 * @property handRows rows needed to hold [HAND_SIZE] cards at [handColumns] across.
 * @property scale the factor a **hand** card is drawn at. 1.0 is the authored AS3 size.
 * @property boardScale the factor a **board** tile is drawn at, always at least [scale]. The
 *   board is only three cards across where a portrait hand is five, so it is not bound by the
 *   same budget and would otherwise leave a third of a phone screen empty. The FFXIV board
 *   draws it larger than the hands too.
 */
internal data class MatchLayout(
    val landscape: Boolean,
    val handColumns: Int,
    val handRows: Int,
    val scale: Float,
    val boardScale: Float,
) {
    /** Fixed size of one hand area, empty slots included. */
    val handWidth: Dp
        get() = (CardSpriteWidth * handColumns + HandGap * (handColumns + 1)) * scale
    val handHeight: Dp
        get() = (CardSpriteHeight * handRows + HandGap * (handRows + 1)) * scale
}

/**
 * Chooses the arrangement for a **measured** [width] x [height] and the largest scale that
 * fits inside it.
 *
 * A pure function of two numbers, which is the whole point: three earlier attempts estimated
 * the leftover space from a screen size minus a constant and each one over-subscribed the
 * column on some device. An over-subscribed column is not a visible error either — `Modifier
 * .size` silently coerces into the constraints it is given, so children collapse to zero
 * height while continuing to draw at full size, and the symptom is overlap rather than a
 * clipped or complaining layout. Deriving the scale from real bounds cannot do that.
 *
 * Both hands are the same shape, so in landscape the total width is two hand areas plus the
 * board and the height is whichever of hand or board is taller; in portrait the axes swap.
 */
private const val BOARD_WIDTH = 3

/** Two columns of cards either side of the board: taller than wide, which landscape has. */
private const val LANDSCAPE_HAND_COLUMNS = 2
private const val MIN_CARD_SCALE = 0.22f

/** 1.0 is the authored 88x118 face. Drawing bigger than the source art would only blur it. */
private const val MAX_CARD_SCALE = 1f
private const val ELEMENT_LABEL_CHARS = 3
private const val INACTIVE_HAND_ALPHA = 0.45f

/** The card following the finger. Slightly transparent, so the cell under it stays readable. */
private const val DRAG_GHOST_ALPHA = 0.85f

/** What is left in the hand while its card is in the air. */
private const val DRAG_SOURCE_ALPHA = 0.3f

/**
 * A free cell while a card is held: present, but not louder than the cell being aimed at.
 *
 * Nine cells lit at full strength would compete with the drop indicator they are meant to lead to.
 */
private const val OPEN_CELL_ALPHA = 0.38f

/**
 * `Card.afterFly`'s tween — the card dropping into the cell it was played on.
 *
 * `Card.fly` (`:195-208`) is two halves and this is the second. The first raises the card 100px
 * out of the hand and fades it out over 0.4s, and it is **not ported**: this port removes the
 * card from the hand the instant it is played, and there is nothing left there to raise. Under a
 * drag it would be wrong as well as absent — the player's own finger has already carried the card
 * across, and replaying that journey afterwards would show it twice.
 *
 * The half that lands is the half that reads as a placement, and it is the same under a tap and
 * under a drop.
 */
private const val LAND_MS = 400

/**
 * `rotation: -90°` settling to `-360°` — three quarters of a turn, anticlockwise.
 *
 * Written as the *starting* angle because that is what the modifier interpolates from, so the
 * end is 0 rather than a full turn that has to be normalised. The direction survives: -90 to 0
 * the short way would be a quarter turn clockwise, and this is 270° the other way.
 */
private const val LAND_DEGREES = -270f

/** `scaleX = scaleY = 1.2` before the tween pulls it back. */
private const val LAND_SCALE = 1.2f

/**
 * `x: _x + 50, y: _y - 100` — where the card starts, relative to where it lands.
 *
 * In fractions of the card's own size rather than the original's pixels, because those were
 * pixels on a fixed 1136x640 stage and this port draws the card at whatever scale the screen
 * affords. 50 and 100 against a 104x128 sprite are these.
 */
private const val LAND_OFFSET_X = 0.48f
private const val LAND_OFFSET_Y = -0.78f

/** `Starling.juggler.tween(this, 0.1, ...)`, four times over -- `Card.as:249-291`. */
private const val FLIP_LEG_MS = 100

/** `scaleX: 1.2` / `scaleY: 1.2` -- the overshoot each leg tweens to. */
private const val FLIP_STRETCH = 1.2f

/**
 * How long the opponent appears to think.
 *
 * `PVEMatchScreen.as:42` waits `1000 + tools.rand(4) * 1000` — one to five seconds — which covered
 * a `setTimeout` cascade of turn announcements this port does not have. Long enough that a
 * placement reads as the opponent's move rather than as part of the player's, and short enough not
 * to be a wait.
 */
/*
 * `Transitions.EASE_IN` / `EASE_OUT`, per the mapping in
 * [api-mapping.md](../../../../../../../docs/analysis/api-mapping.md). Starling's curves are
 * not identical to Compose's; a visual diff pass is still owed.
 */
private val EaseIn = FastOutLinearInEasing
private val EaseOut = LinearOutSlowInEasing
private val TileGap = 4.dp
private val TileShape = RoundedCornerShape(6.dp)
private val SelectionRingWidth = 2.dp
private val ElementFontSize = 9.sp
private val HandGap = 3.dp

internal fun matchLayout(width: Dp, height: Dp): MatchLayout {
    val landscape = width >= height
    val columns = if (landscape) LANDSCAPE_HAND_COLUMNS else HAND_SIZE
    val rows = (HAND_SIZE + columns - 1) / columns

    val handWidth = CardSpriteWidth.value * columns + HandGap.value * (columns + 1)
    val handHeight = CardSpriteHeight.value * rows + HandGap.value * (rows + 1)
    val boardWidth = CardSpriteWidth.value * BOARD_WIDTH + TileGap.value * (BOARD_WIDTH + 1)
    val boardHeight = CardSpriteHeight.value * BOARD_WIDTH + TileGap.value * (BOARD_WIDTH + 1)

    val neededWidth = if (landscape) handWidth * 2 + boardWidth else maxOf(handWidth, boardWidth)
    val neededHeight =
        if (landscape) maxOf(handHeight, boardHeight) else handHeight * 2 + boardHeight

    val scale = minOf(width.value / neededWidth, height.value / neededHeight)
        .coerceIn(MIN_CARD_SCALE, MAX_CARD_SCALE)

    // Whatever the hands did not need, on the axis they are stacked along.
    val boardWidthBudget = if (landscape) width.value - handWidth * 2 * scale else width.value
    val boardHeightBudget =
        if (landscape) height.value else height.value - handHeight * 2 * scale
    val boardScale = minOf(boardWidthBudget / boardWidth, boardHeightBudget / boardHeight)
        .coerceIn(scale, MAX_CARD_SCALE)

    return MatchLayout(landscape, columns, rows, scale, boardScale)
}
