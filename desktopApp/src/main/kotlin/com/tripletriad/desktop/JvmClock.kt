package com.tripletriad.desktop

import com.tripletriad.time.Clock
import java.time.LocalTime

/**
 * The real clock, from `java.time`.
 *
 * `:shared` has none of its own — see [Clock] for why that is not an oversight. This is the whole
 * implementation, and its Android counterpart is the same three lines: `java.time` is available on
 * both JVM targets, and duplicating it is cheaper than an `expect`/`actual` pair with an iOS side
 * that cannot be built from a Windows host.
 *
 * [LocalTime.now] reads the default zone, which is what the availability windows mean by an hour.
 */
object JvmClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun localHour(): Int = LocalTime.now().hour
}
