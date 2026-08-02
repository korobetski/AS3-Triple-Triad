package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tripletriad.data.SaveRepository
import com.tripletriad.data.SaveSlot
import com.tripletriad.log.Log
import com.tripletriad.model.CardCollection
import com.tripletriad.model.GameSave
import com.tripletriad.storage.DocumentStore
import com.tripletriad.time.Clock

/**
 * The profile the player is using, and the list they chose it from.
 *
 * ### Why this exists rather than a `var save by mutableStateOf` in `App`
 *
 * The active profile is written to disk on every match, and **the copy in memory has to be the one
 * that was written**: `SaveRepository.save` stamps `LAST_SAVE` and increments `SAVE_NUMBER`, so a
 * caller that keeps the value it passed in stops advancing the save number and re-writes a stale
 * timestamp. Every mutation here goes through [persist] and adopts what came back, which is the one
 * rule that cannot be enforced from a composable holding a plain `var`.
 *
 * That is exactly the trap the AS3 fell into from the other direction: `Game.PROFILE_DATAS` is a
 * global object that eleven screens mutate in place and four of them save, so "what is on disk" and
 * "what is on screen" are only equal by convention.
 *
 * Failures are **kept, not thrown**: a save list that cannot be read must still show a screen, and
 * a write that fails has to say so rather than disappear. [failure] is what the screens render.
 */
class ProfileSession internal constructor(
    private val repository: SaveRepository,
    private val clock: Clock,
) {
    /** Every stored profile, newest save first. Empty until [refresh] has run once. */
    var slots: List<SaveSlot> by mutableStateOf(emptyList())
        private set

    /** The profile in play, or null when none has been chosen. */
    var active: GameSave? by mutableStateOf(null)
        private set

    /** True while a load or a write is in flight, so a screen can disable its controls. */
    var isBusy: Boolean by mutableStateOf(false)
        private set

    /** The last storage failure, or null. Cleared by the next successful operation. */
    var failure: Throwable? by mutableStateOf(null)
        private set

    /** True once [refresh] has completed at least once — "the list is empty" vs "not read yet". */
    var isLoaded: Boolean by mutableStateOf(false)
        private set

    /**
     * Re-reads the profile list. Unreadable profiles are skipped by the repository, not by this.
     */
    suspend fun refresh() {
        isBusy = true
        slots = repository.list()
        isLoaded = true
        isBusy = false
    }

    /** Makes [save] the profile in play. Does not write — nothing has changed yet. */
    fun select(save: GameSave) {
        active = save
        failure = null
    }

    fun clearActive() {
        active = null
    }

    /**
     * Creates a profile, stores it, and makes it active.
     *
     * A blank [username] is allowed through as the original's default rather than rejected: the AS3
     * `setToDefaultValues()` names an unnamed character `Kuplu Kopo`, and refusing to create is a
     * worse answer than naming it something. The creation screen still asks for a name.
     */
    suspend fun create(username: String, mode: CardCollection) {
        val name = username.trim().ifEmpty { GameSave.DEFAULT_USERNAME }
        guard("create '$name'") { active = repository.create(name, mode, clock.nowMillis()) }
    }

    /** Deletes [key], and forgets the active profile if that is what was deleted. */
    suspend fun delete(key: String) {
        val wasActive = slots.firstOrNull { it.key == key }?.save == active
        guard("delete '$key'") {
            repository.delete(key)
            if (wasActive) active = null
        }
    }

    /**
     * Writes [save] and adopts the stamped copy as [active].
     *
     * The return value is deliberately not the profile: a caller that needed it would be holding a
     * second copy, which is the thing this class exists to prevent.
     */
    suspend fun persist(save: GameSave) {
        guard("write '${SaveRepository.keyFor(save)}'") {
            active = repository.save(save, clock.nowMillis())
        }
    }

    /**
     * Runs a storage operation, publishes whatever it threw, and re-reads the list either way.
     *
     * `TooGenericExceptionCaught`: a [DocumentStore] is implemented per host, so a failed write
     * throws whatever that platform's file API throws — `java.io.IOException` on the JVM, something
     * else on Native — and there is no common supertype below `Exception` to name. Nothing is
     * swallowed: the failure is logged and published for the UI to show, and [active] is left as it
     * was, so a player who has just won a match has not also lost it.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun guard(what: String, block: suspend () -> Unit) {
        isBusy = true
        failure = try {
            block()
            null
        } catch (error: Exception) {
            Log.w(TAG, error) { "could not $what" }
            error
        }
        slots = repository.list()
        isLoaded = true
        isBusy = false
    }

    private companion object {
        const val TAG = "Profile"
    }
}

/**
 * One [ProfileSession] for the life of the composition.
 *
 * Keyed on the store, so the session survives every recomposition and every navigation — losing the
 * active profile on the way from the opponent list to the board would be a bug the type above
 * cannot prevent on its own.
 */
@Composable
internal fun rememberProfileSession(documents: DocumentStore, clock: Clock): ProfileSession {
    val repository = remember(documents) { SaveRepository(documents) }
    return remember(repository, clock) { ProfileSession(repository, clock) }
}
