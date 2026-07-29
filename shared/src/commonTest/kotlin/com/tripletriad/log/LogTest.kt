package com.tripletriad.log

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [Log] is process-wide mutable state, so every test here installs its own sink and [AfterTest]
 * puts it back. Without that, one test's [RecordingSink] would still be collecting during the next.
 */
class LogTest {
    private val sink = RecordingSink()

    @AfterTest
    fun restore() = Log.reset()

    @Test
    fun everyLevelReachesTheSinkWithItsTagAndMessage() {
        Log.install(sink)

        Log.d("A") { "debug" }
        Log.i("B") { "info" }
        Log.w("C") { "warn" }
        Log.e("D") { "error" }

        assertEquals(
            listOf(
                LogLevel.DEBUG to "debug",
                LogLevel.INFO to "info",
                LogLevel.WARN to "warn",
                LogLevel.ERROR to "error",
            ),
            sink.lines.map { it.level to it.message },
        )
        assertEquals(listOf("A", "B", "C", "D"), sink.lines.map { it.tag })
    }

    @Test
    fun linesBelowTheMinimumAreDropped() {
        Log.install(sink, minLevel = LogLevel.WARN)

        Log.d("T") { "no" }
        Log.i("T") { "no" }
        Log.w("T") { "yes" }
        Log.e("T") { "yes" }

        assertEquals(listOf("yes", "yes"), sink.lines.map { it.message })
    }

    /**
     * The reason the message is a lambda at all. If a suppressed line still built its string, no
     * one would log from the paths where logging is most useful.
     */
    @Test
    fun aSuppressedMessageIsNeverBuilt() {
        Log.install(sink, minLevel = LogLevel.ERROR)
        var built = 0

        val expensive = {
            built++
            "expensive"
        }
        Log.d("T", expensive)
        Log.i("T", expensive)
        Log.w("T", message = expensive)

        assertEquals(0, built, "the lambda ran even though the line was dropped")
        assertTrue(sink.lines.isEmpty())
    }

    @Test
    fun theThrowableIsCarriedThroughRatherThanFlattenedIntoTheMessage() {
        Log.install(sink)
        val boom = IllegalStateException("boom")

        Log.e("T", boom) { "failed" }

        val entry = sink.lines.single()
        assertSame(boom, entry.error)
        assertEquals("failed", entry.message, "the message must not absorb the exception")
    }

    @Test
    fun resetPutsTheDefaultSinkBack() {
        Log.install(sink, minLevel = LogLevel.ERROR)
        Log.reset()

        Log.d("T") { "goes to println now" }

        assertEquals(LogLevel.DEBUG, Log.minLevel)
        assertTrue(sink.lines.isEmpty(), "the old sink is still installed")
    }

    /** [Log] compares by ordinal, so the declaration order is load-bearing. */
    @Test
    fun levelsAreDeclaredInAscendingSeverity() {
        assertEquals(
            listOf(LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR),
            LogLevel.entries.toList(),
        )
    }

    @Test
    fun aRecordingSinkCanBeFilteredAndCleared() {
        Log.install(sink)

        Log.d("T") { "d" }
        Log.w("T") { "w" }

        assertEquals(listOf("w"), sink.at(LogLevel.WARN).map { it.message })
        sink.clear()
        assertTrue(sink.lines.isEmpty())
    }
}
