package com.tripletriad.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class CardTest {

    private val geezard = Card(
        id = 1,
        name = "Geezard",
        level = 1,
        top = 1,
        right = 4,
        bottom = 1,
        left = 5,
    )

    @Test
    fun oppositeIsAnInvolution() {
        assertEquals(CardColor.RED, CardColor.BLUE.opposite())
        assertEquals(CardColor.BLUE, CardColor.RED.opposite())
        for (color in CardColor.entries) {
            assertEquals(color, color.opposite().opposite())
        }
    }

    @Test
    fun captureChangesOwnerAndNothingElse() {
        val captured = geezard.captured()
        assertNotEquals(geezard.owner, captured.owner)
        assertEquals(CardColor.RED, captured.owner)
        assertEquals(geezard, captured.copy(owner = geezard.owner))
    }

    @Test
    fun captureTwiceReturnsTheOriginal() {
        assertEquals(geezard, geezard.captured().captured())
    }

    @Test
    fun aceIsRenderedAsA() {
        assertEquals("A", powerLabel(10))
        assertEquals("1", powerLabel(1))
        assertEquals("9", powerLabel(9))
    }

    @Test
    fun powersOutsideOneToTenAreRejected() {
        assertFailsWith<IllegalArgumentException> { geezard.copy(top = 0) }
        assertFailsWith<IllegalArgumentException> { geezard.copy(right = 11) }
        assertFailsWith<IllegalArgumentException> { geezard.copy(id = 0) }
        assertFailsWith<IllegalArgumentException> { geezard.copy(level = 0) }
    }
}
