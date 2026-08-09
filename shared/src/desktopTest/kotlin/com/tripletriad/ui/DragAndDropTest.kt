package com.tripletriad.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.Board
import com.tripletriad.model.CardColor
import com.tripletriad.model.HAND_SIZE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Dragging a card out of the hand and onto a cell — Task 4.7.
 *
 * `Card` implements `IDragSource` and `Tile` implements `IDropTarget` in the original, coordinated
 * by Feathers' `DragDropManager`. Compose has no in-process equivalent —
 * `Modifier.dragAndDropTarget` is for drags *between applications* — so the port hit-tests by hand;
 * see [BoardDragState].
 *
 * **Tapping is not replaced.** Task 4.7 ends on "do not ship drag-only", and the original ships
 * both: `Card.onTouch` dispatches `TRIGGERED` on a tap and starts a drag on a move. The existing
 * `MatchUiTest` covers the tap path, and one test here asserts the two still coexist.
 */
@OptIn(ExperimentalTestApi::class)
class DragAndDropTest {
    /** Where a node sits on screen, so a gesture can be aimed from one node at another. */
    private fun ComposeUiTest.centreOf(tag: String): Offset =
        onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center

    private fun ComposeUiTest.topLeftOf(tag: String): Offset =
        onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.topLeft

    /**
     * Drags the player's card in [slot] to wherever [target] is and lets go.
     *
     * `performTouchInput` speaks in the node's **own** coordinates, so the destination is the
     * target's centre in root space minus this card's origin. Moved in several steps rather than
     * one: a single jump can be delivered as one event, and a gesture that never reports an
     * intermediate position is one `detectDragGestures` may see as a tap.
     */
    private fun ComposeUiTest.dragHandCard(slot: Int, target: Offset) {
        val tag = handCardTestTag(CardColor.BLUE, slot)
        val from = topLeftOf(tag)
        val to = target - from
        onNodeWithTag(tag).performTouchInput {
            down(center)
            for (step in 1..DRAG_STEPS) {
                moveTo(center + (to - center) * (step.toFloat() / DRAG_STEPS))
            }
            up()
        }
        waitForIdle()
    }

    @Test
    fun aCardDraggedOntoACellIsPlayed() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()
        val before = handSize(CardColor.BLUE)

        dragHandCard(slot = 0, target = centreOf(tileTestTag(CENTRE_CELL)))

        assertEquals(before - 1, handSize(CardColor.BLUE), "the card should have left the hand")
        assertTrue(placementsMade() >= 1, "and should be on the board")
    }

    /**
     * Let go over nothing and nothing happens.
     *
     * The card goes back to the hand rather than being lost, which is the half of the gesture a
     * drop-target test does not reach: [BoardDragState.drop] returns null and the state clears.
     */
    @Test
    fun aCardDroppedOffTheBoardStaysInTheHand() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()
        val before = handSize(CardColor.BLUE)
        // Counted rather than assumed to be zero: red plays first whenever it wins the coin flip,
        // and `startMatch` returns once it is *blue's* turn — which can be one placement in.
        val onBoard = placementsMade()

        // The top-left corner of the window: outside every cell, and reachable in one gesture.
        dragHandCard(slot = 0, target = Offset.Zero)

        assertEquals(before, handSize(CardColor.BLUE), "nothing should have been played")
        assertEquals(onBoard, placementsMade(), "and the board should be untouched")
    }

    /**
     * A cell that already holds a card refuses the drop.
     *
     * `Tile.onDragDrop` checks `this.card == null` (`Tile.as:115`) and does nothing otherwise. Here
     * a taken cell does not register its bounds at all, so the drop finds no target — the same
     * answer, reached earlier: the cell never lights up, so the refusal is visible before the
     * finger lifts rather than after.
     */
    @Test
    fun aCellThatIsAlreadyTakenRefusesTheDrop() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()

        // Fill a cell first, by dragging onto it.
        dragHandCard(slot = 0, target = centreOf(tileTestTag(CENTRE_CELL)))
        awaitPlayer()
        val taken = centreOf(tileTestTag(CENTRE_CELL))
        val before = handSize(CardColor.BLUE)

        dragHandCard(slot = 0, target = taken)

        assertEquals(before, handSize(CardColor.BLUE), "a taken cell should refuse a second card")
    }

    /**
     * The opponent's hand cannot be dragged.
     *
     * `Card._draggable` is what gates it in the original (`Card.as:137`), and here the drag state
     * is simply not handed to a card that may not move — so there is nothing to gate at gesture
     * time. Asserted against `tt-master`, whose All Open rule is what makes its hand addressable
     * by tag at all.
     */
    @Test
    fun theOpponentsHandCannotBeDragged() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()
        val before = handSize(CardColor.RED)
        val onBoard = placementsMade()

        val tag = handCardTestTag(CardColor.RED, 0)
        val from = topLeftOf(tag)
        val to = centreOf(tileTestTag(CENTRE_CELL)) - from
        onNodeWithTag(tag).performTouchInput {
            down(center)
            for (step in 1..DRAG_STEPS) {
                moveTo(center + (to - center) * (step.toFloat() / DRAG_STEPS))
            }
            up()
        }
        waitForIdle()

        assertEquals(before, handSize(CardColor.RED), "red's hand is not the player's to move")
        assertEquals(onBoard, placementsMade(), "and nothing should have reached the board")
    }

    /**
     * Tapping still plays a card, with the drag handler installed on the same node.
     *
     * The two gestures share a modifier chain, and `clickable` gives up once the pointer passes
     * touch slop — which is the same threshold `detectDragGestures` starts at. This is the
     * assertion that adding the drag did not quietly cost the tap.
     */
    @Test
    fun tappingStillPlaysACardAlongsideTheDrag() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        startMatch()

        val played = playOneCard()

        assertEquals(HAND_SIZE - 1, handSize(CardColor.BLUE), "the tap path should still work")
        assertTrue(played in 0 until Board.SIZE)
    }

    private companion object {
        /** The middle of the 3×3 board, which is free at the start of every match. */
        const val CENTRE_CELL = 4

        /** Enough intermediate positions that the gesture reads as a drag and not as a tap. */
        const val DRAG_STEPS = 8
    }
}
