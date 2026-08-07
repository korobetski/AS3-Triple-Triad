package com.tripletriad.model

/**
 * The experience ladder: 22 thresholds, shared by the PvE level and the PvP rank.
 *
 * ### One table, not two
 *
 * `datas/Level.as` and `datas/Rank.as` are **byte-identical** apart from the class name and the
 * method name (`xpToLevel` / `xpToRank`): the same 22-entry `steps` vector and the same loop. They
 * are two copies of one thing, kept apart because AS3 gave no cheaper way to have `Level.xpToLevel`
 * and `Rank.xpToRank` read well at the call site. Here [levelFor] and [rankFor] are two names on
 * one table, so the two ladders cannot drift apart by an edit to one file.
 *
 * If the design ever wants them to differ — a slower PvP climb, say — this is the place to split
 * them, and the split will be deliberate rather than accidental.
 *
 * ### The AS3 loop has an off-by-one at the top
 *
 * ```actionscript
 * public static function xpToLevel(xp:uint):uint {
 *     var level:uint = 1;
 *     for (var i:uint = 0; i < steps.length; i++ ) {
 *         if (xp >= steps[i]) {
 *             if (xp < steps[i+1]) { level = (i + 1); break; }
 *             else { continue; }
 *         } else { level = (i); break; }
 *     }
 *     return level;
 * }
 * ```
 *
 * At the last entry (`i == 21`) `steps[22]` is `undefined`, so `xp < undefined` is `false` (NaN
 * comparison), the branch `continue`s, the loop ends, and `level` is still its initial `1`. **A
 * player with 31,863,670 XP or more is reported as level 1.** [levelFor] returns [MAX_LEVEL] there
 * instead. This is a bug fix, not a port deviation to be preserved: nothing depends on the wrong
 * value, and reproducing it would mean a maxed profile displaying as a fresh one.
 *
 * The rest of the loop is reproduced exactly, including that `xp` below `steps[0]` cannot happen
 * (`steps[0]` is 0 and XP is unsigned), so the `level = i` branch is unreachable for i == 0.
 */
object XpTable {
    /**
     * Cumulative XP required to *enter* each level, `Level.as:9`. `steps[0]` is 0, so level 1
     * starts at zero XP and the table has one entry per level.
     */
    val steps: List<Long> = listOf(
        0L, 250L, 450L, 810L, 1_458L, 2_624L, 4_723L, 8_501L, 15_302L, 27_544L, 49_579L,
        89_242L, 160_636L, 289_145L, 520_461L, 936_830L, 1_686_294L, 3_035_329L, 5_463_592L,
        9_834_466L, 17_702_039L, 31_863_670L,
    )

    /** The highest level the table describes: 22, one per entry in [steps]. */
    val MAX_LEVEL: Int = steps.size

    /** XP needed to reach [level], or the table's top for anything beyond it. */
    fun thresholdFor(level: Int): Long = steps[(level - 1).coerceIn(0, steps.lastIndex)]

    /**
     * The level [xp] buys, 1..[MAX_LEVEL].
     *
     * `Level.xpToLevel` (`:16-33`), with the top-of-table bug described above fixed.
     */
    fun levelFor(xp: Long): Int {
        require(xp >= 0) { "xp must not be negative, was $xp" }
        // The count of thresholds already met is exactly the level, since steps[0] == 0.
        return steps.count { xp >= it }.coerceAtLeast(1)
    }

    /** The PvP rank [pvpXp] buys. The same ladder as [levelFor] — see the class comment. */
    fun rankFor(pvpXp: Long): Int = levelFor(pvpXp)

    /**
     * How far through the current level [xp] is, 0f..1f. 1f at [MAX_LEVEL], which has no next
     * threshold to be a fraction of.
     *
     * Not in the AS3, which drew its XP bar from `XPLabel.as` against the next step directly. Here
     * because every caller that wants a progress bar would otherwise recompute the same two
     * thresholds and get the max-level division-by-zero wrong.
     */
    fun progressWithinLevel(xp: Long): Float {
        val level = levelFor(xp)
        if (level >= MAX_LEVEL) return 1f
        val floor = thresholdFor(level)
        val ceiling = thresholdFor(level + 1)
        return ((xp - floor).toFloat() / (ceiling - floor).toFloat()).coerceIn(0f, 1f)
    }
}
