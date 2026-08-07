package com.tripletriad.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [XpTable], including the top-of-table case the AS3 gets wrong.
 *
 * The thresholds themselves are pinned against `Level.as:9` rather than re-derived: the table is
 * hand-authored data, and a test that recomputed it would only prove the recomputation agrees with
 * itself.
 */
class XpTableTest {
    @Test
    fun theTableIsTheOneInLevelAs() {
        assertEquals(22, XpTable.steps.size)
        assertEquals(0L, XpTable.steps.first())
        assertEquals(250L, XpTable.steps[1])
        assertEquals(31_863_670L, XpTable.steps.last())
        assertEquals(22, XpTable.MAX_LEVEL)
        assertEquals(
            XpTable.steps.sorted(),
            XpTable.steps,
            "thresholds must ascend or levelFor's count is meaningless",
        )
    }

    @Test
    fun zeroXpIsLevelOne() {
        assertEquals(1, XpTable.levelFor(0))
    }

    @Test
    fun levelRisesAtEachThreshold() {
        assertEquals(1, XpTable.levelFor(249))
        assertEquals(2, XpTable.levelFor(250))
        assertEquals(2, XpTable.levelFor(449))
        assertEquals(3, XpTable.levelFor(450))
        assertEquals(4, XpTable.levelFor(810))
    }

    /**
     * `Level.xpToLevel(31863670)` returns **1** in the AS3: at the last index `steps[i+1]` is
     * `undefined`, `xp < undefined` is false, the loop `continue`s off the end and `level` is still
     * its initial value. A maxed profile displaying as a fresh one is not behaviour worth
     * preserving.
     */
    @Test
    fun theTopOfTheTableIsMaxLevelAndNotOne() {
        assertEquals(XpTable.MAX_LEVEL, XpTable.levelFor(31_863_670))
        assertEquals(XpTable.MAX_LEVEL, XpTable.levelFor(99_999_999_999))
    }

    @Test
    fun rankUsesTheSameLadder() {
        for (xp in listOf(0L, 249L, 250L, 1_000L, 31_863_670L)) {
            assertEquals(XpTable.levelFor(xp), XpTable.rankFor(xp), "xp=$xp")
        }
    }

    @Test
    fun thresholdForIsTheInverseOfLevelFor() {
        for (level in 1..XpTable.MAX_LEVEL) {
            assertEquals(level, XpTable.levelFor(XpTable.thresholdFor(level)), "level=$level")
        }
    }

    @Test
    fun thresholdForClampsOutsideTheTable() {
        assertEquals(0L, XpTable.thresholdFor(0))
        assertEquals(0L, XpTable.thresholdFor(-5))
        assertEquals(XpTable.steps.last(), XpTable.thresholdFor(999))
    }

    @Test
    fun progressRunsFromZeroToOneWithinALevel() {
        assertEquals(0f, XpTable.progressWithinLevel(0))
        // Level 1 spans 0..249, so 125 is roughly halfway.
        assertTrue(XpTable.progressWithinLevel(125) in 0.4f..0.6f)
        assertEquals(0f, XpTable.progressWithinLevel(250), "the start of level 2")
    }

    /**
     * No next threshold to be a fraction of — this is where a naive implementation divides by zero.
     */
    @Test
    fun progressIsFullAtMaxLevel() {
        assertEquals(1f, XpTable.progressWithinLevel(31_863_670))
        assertEquals(1f, XpTable.progressWithinLevel(Long.MAX_VALUE))
    }

    @Test
    fun negativeXpIsAProgrammingError() {
        assertFailsWith<IllegalArgumentException> { XpTable.levelFor(-1) }
    }
}
