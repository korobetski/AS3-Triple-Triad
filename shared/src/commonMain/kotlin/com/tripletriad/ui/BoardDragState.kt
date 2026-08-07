package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import com.tripletriad.model.Card

/**
 * A card being dragged towards a cell, and where the cells are.
 *
 * ### Why this exists rather than `Modifier.dragAndDropTarget`
 *
 * That API is for drag and drop **between applications** — it speaks in `DragAndDropTransfer` and
 * platform MIME types, and it is not what a card moving three inches inside one composable needs.
 * Compose has no built-in drop target for an in-process drag, so the shape is the one
 * `docs/migration/08-PHASE-4-UI-LAYER.md` Task 4.7 sketches: one state hoisted above both the hand
 * and the board, cells that register their own bounds, and hit-testing done by hand.
 *
 * ### Everything is in root coordinates
 *
 * Task 4.7's sketch hit-tests in "the board's coordinate space" and accumulates `dragPosition +=
 * delta` from an `onDragStart` offset — but that offset is local to **the dragged card**, so the
 * two are measured from different origins and the hit test is wrong by the card's position on
 * screen. Root space avoids the whole question: a dragged card converts its pointer with
 * `localToRoot`, a cell registers `boundsInRoot`, and nothing needs a shared parent to be found and
 * threaded through.
 *
 * [origin] is the one exception, and it is only for drawing: the floating card is a child of the
 * play area, so its offset has to be measured from there.
 */
internal class BoardDragState {
    /** The card under the finger, or null when nothing is being dragged. */
    var card: Card? by mutableStateOf(null)
        private set

    /** Where the finger is, in root coordinates. [Offset.Unspecified] when not dragging. */
    var pointer: Offset by mutableStateOf(Offset.Unspecified)
        private set

    /** The play area's top-left in root coordinates, so the floating card can be placed. */
    var origin: Offset by mutableStateOf(Offset.Zero)

    /**
     * Cell bounds in root coordinates, by board position.
     *
     * A snapshot map, so a cell lighting up as the finger crosses it is an ordinary recomposition
     * rather than something that has to be pushed.
     */
    private val cells = mutableStateMapOf<Int, Rect>()

    val isDragging: Boolean get() = card != null

    fun registerCell(position: Int, bounds: Rect) {
        cells[position] = bounds
    }

    fun unregisterCell(position: Int) {
        cells.remove(position)
    }

    fun start(card: Card, at: Offset) {
        this.card = card
        pointer = at
    }

    fun moveTo(at: Offset) {
        if (isDragging) pointer = at
    }

    /**
     * The cell under the finger, or null.
     *
     * Null while nothing is being dragged, which is what lets a cell ask unconditionally.
     */
    fun hovered(): Int? {
        if (!isDragging || !pointer.isSpecified) return null
        return cells.entries.firstOrNull { it.value.contains(pointer) }?.key
    }

    /**
     * Ends the drag and returns what was dropped where, or null if it was not over a cell.
     *
     * Clears either way: a drag that ends on nothing is over, and leaving the card attached to the
     * finger is how a ghost card outlives the gesture that made it.
     */
    fun drop(): Pair<Card, Int>? {
        val dropped = card
        val target = hovered()
        cancel()
        return if (dropped != null && target != null) dropped to target else null
    }

    fun cancel() {
        card = null
        pointer = Offset.Unspecified
    }
}

/**
 * One [BoardDragState] for the life of the board.
 *
 * Not keyed on the match: the cells are re-registered on every layout pass anyway, and a drag
 * cannot outlive the composition that started it.
 */
@Composable
internal fun rememberBoardDragState(): BoardDragState = remember { BoardDragState() }
