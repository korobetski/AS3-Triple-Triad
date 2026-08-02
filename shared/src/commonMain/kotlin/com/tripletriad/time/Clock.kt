package com.tripletriad.time

/**
 * What the game needs from the wall clock, which is two things.
 *
 * Nothing in `model/` or `data/` reads a clock: `SaveRepository.save` takes an `at`,
 * `GameSave.new` takes a `createdAt` and `NpcCatalog.available` takes an `hour`, all so those
 * layers stay pure and testable. That decision has to end somewhere, and it ends here — one seam,
 * injected at the top of the composition, so a test can pin an instant and an hour instead of
 * asserting around whatever the machine happens to be set to.
 *
 * ### Why the host supplies it, like `SettingsStore` and `AudioPlayer`
 *
 * There is no `SystemClock` in `commonMain`, and that is the second attempt at this. The first read
 * the instant through `kotlin.time.Clock` and the zone through `kotlinx-datetime`, which compiles
 * as common metadata and **fails on every platform target**: as of 0.6.2 `kotlinx.datetime.Instant`
 * is a deprecated typealias onto `kotlin.time.Instant` in the metadata view and a distinct type in
 * the platform views, so `toLocalDateTime` accepts a stdlib instant in the first and refuses it in
 * the second.
 *
 * The local hour is what forces the issue — the stdlib has no time zone at all, and the
 * availability data is in wall-clock hours. Rather than carry a dependency that disagrees with the
 * stdlib about its own types, the hosts supply this: `java.time` on both JVM targets, three lines
 * each. That also means no `expect`/`actual`, and so no iOS implementation that cannot be built
 * from this machine.
 */
interface Clock {
    /** Epoch milliseconds, for `CREATION_DATE`, `LAST_SAVE` and achievement timestamps. */
    fun nowMillis(): Long

    /**
     * The local hour, 0..23.
     *
     * Local and not UTC, because that is what the availability data means: 27 of the 60 ff14
     * opponents declare a window like `{begins: 20, ends: 8}`, and an opponent that appears at
     * 20:00 has to appear at the player's 20:00. See
     * [Availability][com.tripletriad.model.Availability].
     */
    fun localHour(): Int
}

/**
 * A clock that does not move. For tests and previews, and the default a host overrides.
 *
 * Defaults to a real date — 2026-01-01T12:00Z — rather than 0, because a character listed as
 * created in 1970 reads as a bug in a screenshot, and because hour 12 is inside every daytime
 * availability window and outside none of them, so a default-clock test sees a normal opponent list
 * rather than an empty one.
 */
class FixedClock(
    private val millis: Long = DEFAULT_MILLIS,
    private val hour: Int = DEFAULT_HOUR,
) : Clock {
    init {
        require(hour in 0 until HOURS_IN_DAY) { "hour must be in 0..23, was $hour" }
    }

    override fun nowMillis(): Long = millis

    override fun localHour(): Int = hour

    companion object {
        /** 2026-01-01T12:00:00Z. */
        const val DEFAULT_MILLIS: Long = 1_767_268_800_000L
        const val DEFAULT_HOUR: Int = 12
        private const val HOURS_IN_DAY = 24
    }
}
