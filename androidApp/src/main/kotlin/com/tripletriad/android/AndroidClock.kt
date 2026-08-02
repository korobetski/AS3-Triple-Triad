package com.tripletriad.android

import com.tripletriad.time.Clock
import java.util.Calendar

/**
 * The real clock.
 *
 * `:shared` has none of its own — see [Clock] for why that is not an oversight. The desktop host's
 * `JvmClock` is the same two methods: `java.time` is available on both JVM targets, and two small
 * copies cost less than an `expect`/`actual` pair whose iOS side cannot be built from a Windows
 * host. A second implementation also keeps the interface honest, which is the same argument
 * `DesktopSettingsStore` makes.
 *
 * ### Why `Calendar` here and `java.time` there
 *
 * `LocalTime.getHour` needs API 26 and this module's `minSdk` is 24, so lint refuses it —
 * desugaring is not on by default, and enabling it means `desugar_jdk_libs` and a version to keep
 * pinned for the sake of one integer. `Calendar.HOUR_OF_DAY` has been there since API 1 and reads
 * the default zone, which is exactly what the availability windows mean by an hour. The desktop
 * copy is unconstrained and uses the better API.
 */
object AndroidClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun localHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
}
