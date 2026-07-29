package com.tripletriad.settings

/**
 * Somewhere small and durable to keep one blob of text.
 *
 * ### Why this is an interface and not `expect`/`actual`
 *
 * The obvious shape for "read a file on each platform" is an `expect class`, and
 * `docs/migration/05-PHASE-1-INFRASTRUCTURE.md` Task 1.6 asks for exactly that
 * (`expect class FileManager`). It is the wrong shape here, for two reasons.
 *
 * First, **`expect` obliges every declared target to supply an `actual`**, and `:shared` declares
 * `iosX64`, `iosArm64` and `iosSimulatorArm64`. Kotlin/Native cannot target Apple platforms from a
 * Windows host, so an iOS `actual` written here could not be compiled, let alone run, before being
 * pushed — the macOS CI job would be the first thing to see it. Writing code that cannot be
 * checked, to satisfy a target that was explicitly descoped ("Android only for now", 2026-07-25),
 * buys nothing.
 *
 * Second, the Android implementation needs a `Context`, which means either threading one into a
 * composable or parking it in a global. Inverting it instead — each host module supplies the
 * store it can build — removes the problem rather than working around it: `:androidApp` has the
 * `Context`, `:desktopApp` has `user.home`, and `:shared` needs neither.
 *
 * When iOS comes back, it implements this same interface in `iosApp` with `NSFileManager`. No
 * `expect` is needed then either.
 *
 * ### Contract
 *
 * [read] returns null when nothing has been stored yet — that is the first-run signal, and it is
 * distinct from a stored empty string. Implementations must not throw for a missing file; anything
 * else (a permission error, a corrupt filesystem) may throw and is the caller's problem, which for
 * settings means falling back to defaults rather than failing to start.
 */
interface SettingsStore {
    /** The stored text, or null if there is none. */
    suspend fun read(): String?

    /** Replaces the stored text. */
    suspend fun write(text: String)
}

/**
 * A [SettingsStore] that forgets everything when the process ends.
 *
 * The default for previews and tests, and a working state rather than a stub: everything downstream
 * of this behaves normally for one session, so a screen can be exercised without a filesystem.
 */
class InMemorySettingsStore(
    initial: String? = null,
    /**
     * Makes both operations throw, so the caller's failure path is reachable from a test.
     * The interface contract allows it, and a permission error on a real device is exactly this.
     */
    private val failure: Throwable? = null,
) : SettingsStore {
    private var text: String? = initial

    /** How many times [write] has been called, so a test can assert it was *not*. */
    var writes: Int = 0
        private set

    /**
     * What is stored, without suspending.
     *
     * For assertions from a UI test, whose body is not a coroutine. [read] stays the interface's
     * only way in; this is a window onto the same field, not a second path through it.
     */
    val stored: String? get() = text

    override suspend fun read(): String? = failure?.let { throw it } ?: text

    override suspend fun write(text: String) {
        failure?.let { throw it }
        this.text = text
        writes++
    }
}
