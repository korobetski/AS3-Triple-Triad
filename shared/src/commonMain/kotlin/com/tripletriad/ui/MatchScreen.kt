package com.tripletriad.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripletriad.data.CardCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.CardType
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.MatchOutcome
import com.tripletriad.model.MatchState
import com.tripletriad.model.PlacedCard
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Test tags for `shared/src/desktopTest`. */
const val BOARD_TEST_TAG: String = "board"
const val TURN_TEST_TAG: String = "turn"
const val SCORE_TEST_TAG: String = "score"
const val OUTCOME_TEST_TAG: String = "outcome"
const val NEW_MATCH_TEST_TAG: String = "new-match"

/** The chevron back to the main menu. */
const val MATCH_EXIT_TEST_TAG: String = "match-exit"

/** `tile-0` … `tile-8`, row-major, matching `Board.cells`. */
fun tileTestTag(position: Int): String = "tile-$position"

/**
 * `hand-blue-0` … `hand-blue-4`, by **slot** rather than card id.
 *
 * Slots close up as cards are played, so slot 0 is always the first remaining card. That makes
 * a test able to say "play whatever is first" without knowing the deal. Slot numbering is
 * independent of how the slots are arranged on screen, so the same tag finds the same card in
 * either orientation.
 */
fun handCardTestTag(owner: CardColor, slot: Int): String =
    "hand-${owner.name.lowercase()}-$slot"

/**
 * A playable match: the 3×3 board, both hands, and turn-by-turn placement.
 *
 * All game logic lives in [MatchState] — this composable holds one `var state` and calls
 * `state.play(card, position)`. Nothing here knows a rule. That separation is the point of
 * the port: the AS3 equivalent (`BaseMatchScreen`, 437 lines) *is* the rules engine, the
 * board, the score and the turn sequencer at once.
 *
 * Only the basic capture rule is active by default ([MatchState.rules] is `GameRules()`), so
 * a card flips when its facing power loses the comparison. Same, Plus, Same Wall, combo and
 * the type rules are implemented and tested but not yet exposed in this UI.
 *
 * The arrangement follows the FFXIV board: the board centred, the player's hand (blue) on the
 * player's side and the opponent's (red) opposite — left/right in landscape, bottom/top in
 * portrait. See [matchLayout].
 */
@Composable
internal fun MatchScreen(catalog: CardCatalog, onExit: () -> Unit = {}) {
    var matchIndex by remember { mutableStateOf(0) }
    // Seeded from the match index so a given match is reproducible, but "new match" deals a
    // different pair of hands.
    var state by remember(matchIndex) { mutableStateOf(deal(catalog, matchIndex)) }
    var selected by remember(matchIndex) { mutableStateOf<Card?>(null) }

    // Only the side to move can select, and only from its own hand.
    val selectable = state.currentHand

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusBar(
            state = state,
            selected = selected,
            onNewMatch = { matchIndex++ },
            onExit = onExit,
        )

        // The play area takes whatever the status bar leaves and sizes every card to what it
        // actually got. Nothing below this line guesses at a screen size or a "chrome"
        // constant: `matchLayout` is handed measured bounds and derives one scale that the
        // whole arrangement is known to fit inside.
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            PlayArea(
                state = state,
                selected = selected,
                layout = matchLayout(maxWidth, maxHeight),
                onSelect = { if (it in selectable) selected = it },
                onPlace = { position ->
                    val card = selected
                    if (card != null && state.board.isEmpty(position)) {
                        state = state.play(card, position)
                        selected = null
                    }
                },
            )
        }
    }
}

/**
 * Red hand, board, blue hand — as a row in landscape, a column in portrait.
 *
 * `SpaceBetween` puts the board dead centre: both hand areas are given the same fixed size by
 * [MatchLayout], so the board does not drift as a hand empties.
 */
@Composable
private fun PlayArea(
    state: MatchState,
    selected: Card?,
    layout: MatchLayout,
    onSelect: (Card) -> Unit,
    onPlace: (Int) -> Unit,
) {
    val hand: @Composable (CardColor) -> Unit = { owner ->
        HandArea(
            state = state,
            owner = owner,
            selected = selected,
            layout = layout,
            onSelect = onSelect,
        )
    }
    val board: @Composable () -> Unit = {
        BoardGrid(state = state, scale = layout.boardScale, onPlace = onPlace)
    }

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
 * Score, whose turn it is, and a reset. One compact line so the board gets the rest.
 *
 * The score is two numbers and a dash, with each number in its side's colour and no colour *word*
 * — it used to read "blue 5 — 5 red". Nothing in the AS3 bundles names a side, so those two words
 * would have been the only untranslatable text on screen, and the FFXIV board they are modelled on
 * shows the score without them too.
 */
@Composable
private fun StatusBar(
    state: MatchState,
    selected: Card?,
    onNewMatch: () -> Unit,
    onExit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val strings = LocalStrings.current
        // A bare chevron, not "‹ Back". The row already learned once that a fixed-width control
        // sized for English squeezes the turn line in French (see below), and this bar now has two
        // of them; a glyph costs the same in every language. The Android system back gesture
        // reaches the same place — see `App`.
        Text(
            text = "‹",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 18.sp,
            modifier = Modifier
                .testTag(MATCH_EXIT_TEST_TAG)
                .clickable(onClick = onExit)
                .padding(horizontal = 4.dp),
        )
        Score(state)
        // The turn line takes whatever the two fixed ends leave, and elides rather than growing.
        //
        // It used to be three items in a centred `spacedBy` row, which fitted because every
        // string was English: French is "au bleu de jouer — choisissez une carte" where English is
        // "blue to play — pick a card", and the extra 14 characters pushed "Match suivant" onto a
        // second line on a 1080 px screen. A row sized for one language is the oldest
        // localisation bug there is, so the *sentence* is the part that gives, not the controls.
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            TurnLine(state = state, selected = selected)
        }
        Text(
            text = "${strings[StringKeys.NEXT_MATCH]} ▸",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .testTag(NEW_MATCH_TEST_TAG)
                .clickable(onClick = onNewMatch)
                .padding(4.dp),
        )
    }
}

/** `5 — 5`, each half in its owner's colour. */
@Composable
private fun Score(state: MatchState) {
    val score = state.score
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = CardColor.BLUE.edge)) { append(score.blue.toString()) }
            append(" — ")
            withStyle(SpanStyle(color = CardColor.RED.edge)) { append(score.red.toString()) }
        },
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag(SCORE_TEST_TAG),
    )
}

/**
 * Whose turn it is, what is selected, or the result once the board is full.
 *
 * The outcome is phrased from **blue's** side — `You win !` / `You lose...` — because that is what
 * the bundles offer and it matches the original, where the local player is always the blue one
 * (`data-flow.md`, `openPhase`). When there is an AI or a second player this needs revisiting; a
 * neutral "red wins" has no key in any of the four locales.
 */
@Composable
private fun TurnLine(state: MatchState, selected: Card?) {
    val strings = LocalStrings.current
    val outcome = state.outcome()
    if (outcome != null) {
        Text(
            text = when (outcome) {
                is MatchOutcome.Win ->
                    if (outcome.winner == CardColor.BLUE) {
                        strings[StringKeys.YOU_WIN]
                    } else {
                        strings[StringKeys.YOU_LOSE]
                    }
                is MatchOutcome.Draw -> strings[StringKeys.DRAW]
                is MatchOutcome.SuddenDeath ->
                    "${strings[StringKeys.DRAW]} — ${strings[StringKeys.SUDDEN_DEATH]}"
            },
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(OUTCOME_TEST_TAG),
        )
        return
    }
    val player = state.currentPlayer ?: return
    val side = strings[if (player == CardColor.BLUE) StringKeys.SIDE_BLUE else StringKeys.SIDE_RED]
    Text(
        text = if (selected == null) {
            strings.format(StringKeys.TURN_PICK_CARD, side)
        } else {
            strings.format(StringKeys.TURN_PICK_CELL, side, selected.name)
        },
        color = player.edge,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(TURN_TEST_TAG),
    )
}

/** The 3×3 board. Empty cells show their element, if the board has one. */
@Composable
private fun BoardGrid(state: MatchState, scale: Float, onPlace: (Int) -> Unit) {
    Column(
        modifier = Modifier.testTag(BOARD_TEST_TAG).padding(TileGap * scale),
        verticalArrangement = Arrangement.spacedBy(TileGap * scale),
    ) {
        for (row in 0 until BOARD_WIDTH) {
            Row(horizontalArrangement = Arrangement.spacedBy(TileGap * scale)) {
                for (column in 0 until BOARD_WIDTH) {
                    val position = row * BOARD_WIDTH + column
                    TileCell(
                        placed = state.board[position],
                        element = state.board.elements[position],
                        scale = scale,
                        modifier = Modifier
                            .testTag(tileTestTag(position))
                            .clickable { onPlace(position) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TileCell(
    placed: PlacedCard?,
    element: CardType?,
    scale: Float,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .size(CardSpriteWidth * scale, CardSpriteHeight * scale)
            .clip(TileShape)
            .background(EmptyTile)
            .border(1.dp, TileBorder, TileShape),
        contentAlignment = Alignment.Center,
    ) {
        if (placed == null) {
            element?.let {
                Text(
                    text = it.name.take(ELEMENT_LABEL_CHARS),
                    color = Color.White.copy(alpha = 0.35f),
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
    var shown by remember { mutableStateOf(placed.owner) }
    var showBack by remember { mutableStateOf(false) }

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
            scaleX = stretchX.value
            scaleY = squashY.value
        },
    )
}

/**
 * One side's remaining cards, in a fixed-size box so the board stays put as the hand empties.
 *
 * Dimmed when it is not that side's turn. [HAND_SIZE] slots are always laid out; the empty ones
 * are spacers, which is what holds the arrangement steady.
 */
@Composable
private fun HandArea(
    state: MatchState,
    owner: CardColor,
    selected: Card?,
    layout: MatchLayout,
    onSelect: (Card) -> Unit,
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
 * One selectable card in a hand.
 *
 * The selection ring is a border on the card's own bounds rather than a frame around them: a
 * frame would have to grow the slot, and a growing slot moves every card beside it.
 */
@Composable
private fun HandCard(
    card: Card,
    owner: CardColor,
    slot: Int,
    isSelected: Boolean,
    active: Boolean,
    scale: Float,
    onSelect: (Card) -> Unit,
) {
    Box(
        modifier = Modifier
            .testTag(handCardTestTag(owner, slot))
            .clickable(enabled = active) { onSelect(card) },
    ) {
        CardFace(card = card, scale = scale)
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(CardSpriteWidth * scale, CardSpriteHeight * scale)
                    .border(SelectionRingWidth, SelectionRing, TileShape),
            )
        }
    }
}

/**
 * Deals two hands out of the catalog.
 *
 * A stand-in for the pre-match phase the port does not model: `RANDOM` builds a hand from the
 * player's collection and `SWAP` exchanges a card, both of which belong to whatever assembles
 * hands rather than to the match itself. Seeded by [index] so a given match is reproducible
 * while "new match" deals something different.
 */
private fun deal(catalog: CardCatalog, index: Int): MatchState {
    val random = Random(DEAL_SEED + index)
    val pool = catalog.all.shuffled(random)
    return MatchState.start(
        blueHand = pool.take(HAND_SIZE),
        redHand = pool.drop(HAND_SIZE).take(HAND_SIZE),
        first = if (random.nextBoolean()) CardColor.BLUE else CardColor.RED,
    )
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

private const val BOARD_WIDTH = 3

/** Two columns of cards either side of the board: taller than wide, which landscape has. */
private const val LANDSCAPE_HAND_COLUMNS = 2

private const val MIN_CARD_SCALE = 0.22f

/** 1.0 is the authored 88x118 face. Drawing bigger than the source art would only blur it. */
private const val MAX_CARD_SCALE = 1f
private const val ELEMENT_LABEL_CHARS = 3
private const val INACTIVE_HAND_ALPHA = 0.45f

/** `Starling.juggler.tween(this, 0.1, ...)`, four times over -- `Card.as:249-291`. */
private const val FLIP_LEG_MS = 100

/** `scaleX: 1.2` / `scaleY: 1.2` -- the overshoot each leg tweens to. */
private const val FLIP_STRETCH = 1.2f
private const val DEAL_SEED = 20260726

/*
 * `Transitions.EASE_IN` / `EASE_OUT`, per the mapping in
 * [api-mapping.md](../../../../../../../docs/analysis/api-mapping.md). Starling's curves are
 * not identical to Compose's; a visual diff pass is still owed.
 */
private val EaseIn = FastOutLinearInEasing
private val EaseOut = LinearOutSlowInEasing

private val TileGap = 4.dp
private val TileShape = RoundedCornerShape(6.dp)
private val EmptyTile = Color(0xFF1E2230)
private val TileBorder = Color(0xFF3A4152)
private val SelectionRing = Color(0xFFF2C14E)
private val SelectionRingWidth = 2.dp
private val ElementFontSize = 9.sp
private val HandGap = 3.dp
