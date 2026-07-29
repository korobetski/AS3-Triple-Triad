package com.tripletriad.log

/**
 * The project's logger.
 *
 * ### Why not Napier, which the migration plan named
 *
 * Napier is a dependency whose whole job is to forward to `android.util.Log` on Android and to
 * `println` elsewhere. That is the twenty lines below, and a host module can install the platform
 * sink itself in three — see `MainActivity`. A dependency earns its place by doing something hard;
 * this one would not.
 *
 * ### Why the message is a lambda
 *
 * `Log.d(TAG) { "resolved ${card.name} to ${texture.id}" }` builds no string when `DEBUG` is
 * below [minLevel]. A logger that formats first and discards after is a logger nobody dares call
 * from the code that would benefit most — the per-frame and per-card paths.
 *
 * ### Why mutable global state
 *
 * A log sink is process-wide by nature, and threading one through every constructor to reach the
 * three call sites that exist would be worse. [install] and [reset] keep it testable:
 * [RecordingSink] captures lines and asserts on them, which is what `UserSettingsTest` does.
 */
object Log {
    /** Lines below this are not formatted at all, let alone written. */
    var minLevel: LogLevel = LogLevel.DEBUG
        private set

    private var sink: LogSink = PrintlnSink

    /** Routes output to [sink], writing [minLevel] and above. */
    fun install(sink: LogSink, minLevel: LogLevel = LogLevel.DEBUG) {
        this.sink = sink
        this.minLevel = minLevel
    }

    /** Back to `println` at [LogLevel.DEBUG]. Tests use this to undo themselves. */
    fun reset() {
        sink = PrintlnSink
        minLevel = LogLevel.DEBUG
    }

    fun d(tag: String, message: () -> String) = write(LogLevel.DEBUG, tag, null, message)

    fun i(tag: String, message: () -> String) = write(LogLevel.INFO, tag, null, message)

    fun w(tag: String, error: Throwable? = null, message: () -> String) =
        write(LogLevel.WARN, tag, error, message)

    fun e(tag: String, error: Throwable? = null, message: () -> String) =
        write(LogLevel.ERROR, tag, error, message)

    // Named `write` and not `log`: detekt's MemberNameEqualsClassName fires on `Log.log`.
    private inline fun write(
        level: LogLevel,
        tag: String,
        error: Throwable?,
        message: () -> String,
    ) {
        if (level.ordinal >= minLevel.ordinal) {
            sink.write(level, tag, message(), error)
        }
    }
}

/**
 * Severity. Declared in ascending order, because [Log] compares by [Enum.ordinal].
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

/**
 * Where log lines go. A `fun interface` so a host can install one as a lambda.
 */
fun interface LogSink {
    fun write(level: LogLevel, tag: String, message: String, error: Throwable?)
}

/**
 * The default. Goes to stdout on the JVM and to logcat under Android's `System.out` redirect,
 * which is enough that a missing [Log.install] degrades rather than loses output.
 */
object PrintlnSink : LogSink {
    override fun write(level: LogLevel, tag: String, message: String, error: Throwable?) {
        println("${level.name.first()}/$tag: $message")
        error?.let { println("  ${it::class.simpleName}: ${it.message}") }
    }
}

/**
 * A sink that keeps what it was given, for tests.
 *
 * Not thread-safe, and deliberately so — a `commonMain` mutex to serialise a test double would
 * be more machinery than the thing it guards. Tests that log from several threads at once should
 * install something else.
 */
class RecordingSink : LogSink {
    private val entries = mutableListOf<LogEntry>()

    val lines: List<LogEntry> get() = entries.toList()

    override fun write(level: LogLevel, tag: String, message: String, error: Throwable?) {
        entries += LogEntry(level, tag, message, error)
    }

    fun clear() = entries.clear()

    /** Every line at [level], for asserting that something was reported without pinning wording. */
    fun at(level: LogLevel): List<LogEntry> = entries.filter { it.level == level }
}

/** One captured line. */
data class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val error: Throwable? = null,
)
