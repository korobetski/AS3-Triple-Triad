package com.tripletriad.data

import com.tripletriad.log.Log
import com.tripletriad.model.CardCollection
import com.tripletriad.model.GameSave
import com.tripletriad.storage.DocumentStore
import com.tripletriad.storage.SaveCodec
import com.tripletriad.storage.SaveCorruptException
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * A profile as the load screen needs to list it: enough to draw a row, without decoding the rest.
 *
 * The AS3 load screen reads every `.sav` in full to build its list (`LoadScreen.as`), which is
 * affordable at five profiles and is not the reason this type exists — [key] is. A caller that has
 * listed profiles needs the exact key to load one back, and deriving it from the username a second
 * time would duplicate the derivation rule.
 */
data class SaveSlot(
    /** The [DocumentStore] key, as [SaveRepository.keyFor] built it. */
    val key: String,
    val save: GameSave,
) {
    val username: String get() = save.username
    val lastSave: Long get() = save.lastSave
}

/** What went wrong loading a profile. */
sealed interface SaveLoadFailure {
    /** Nothing is stored under that key. */
    data object NotFound : SaveLoadFailure

    /** The file is not a save of this format, or has been altered. */
    data class Corrupt(val cause: Throwable) : SaveLoadFailure

    /** The store could not be read at all — permissions, a broken filesystem. */
    data class Unreadable(val cause: Throwable) : SaveLoadFailure
}

/**
 * Load, save, list and delete profiles.
 *
 * ### The AS3 this replaces
 *
 * `datas/Save.as` is 97 lines with three overlapping entry points — an instance `save()`, a static
 * `save(DATAS)` with the same body, and `load()`/`loadFile()` likewise duplicated — reading and
 * writing a global `DATAS:Object` through `TTOFiles` and `CryptoHelper`. Four behaviors from it are
 * preserved:
 *
 * 1. **`LAST_SAVE` and `SAVE_NUMBER` are stamped by the save itself** (`Save.as:82-83`), not by the
 *    caller. So a profile always records when it was last written and how many times, and no caller
 *    can forget to.
 * 2. **The file name embeds the username and the creation date** (`:85`), which is what allows two
 *    profiles with the same name to coexist. See [keyFor].
 * 3. **Missing fields are defaulted on load** (`:55-58`): `MODE`, `ACHIEVEMENTS`, `NPC_W` and
 *    `RULES_W` are filled in if absent. Handled by every [GameSave] field having a default plus
 *    `ignoreUnknownKeys`, which covers more than the four the original names.
 * 4. **`FORFEITS` is recomputed on load** (`:59`) rather than trusted — [GameSave.forfeits].
 *
 * What is not preserved is the AES-from-a-GIF encryption; see [SaveCodec].
 *
 * ### Failures are returned, not thrown
 *
 * [load] returns a [Result] because all three of its failure modes are *expected*: a profile can be
 * missing, a file can be corrupt, and storage can be unavailable. The load screen has something
 * different to say about each, and none of them is a bug. [save] throws instead — a failed write is
 * data loss and must not be swallowed the way a settings write can be.
 */
class SaveRepository(
    private val store: DocumentStore,
    /** Source of the obfuscation salt. A parameter so a test can pin the exact bytes written. */
    private val random: Random = Random.Default,
) {
    /**
     * The profile stored under [key].
     *
     * [GameSave.sane] is applied, so `LEVEL` and `RANK` agree with the XP they claim to represent
     * even if the file said otherwise.
     */
    // TooGenericExceptionCaught: a `DocumentStore` is implemented per host, so what a failing read
    // throws is whatever that platform's file API throws — `java.io.IOException` on Android and the
    // JVM, something else again on Native. There is no common supertype below `Exception` to name,
    // and this method's contract is that *any* storage failure becomes a `SaveLoadFailure` rather
    // than an escaping throwable. Nothing is swallowed: every branch logs and reports.
    // ReturnCount: three early returns, one per distinct failure, each returning at the point the
    // failure is known. Collapsing them into one exit would mean a nullable accumulator and a
    // second `when` to interpret it.
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    suspend fun load(key: String): Result<GameSave> {
        val blob = try {
            store.read(key)
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "could not read profile '$key'" }
            return Result.failure(SaveLoadException(SaveLoadFailure.Unreadable(failure)))
        } ?: return Result.failure(SaveLoadException(SaveLoadFailure.NotFound))

        return try {
            Result.success(Format.decodeFromString<GameSave>(SaveCodec.decode(blob)).sane())
        } catch (failure: SaveCorruptException) {
            Log.w(TAG, failure) { "profile '$key' is not readable" }
            Result.failure(SaveLoadException(SaveLoadFailure.Corrupt(failure)))
        } catch (failure: Exception) {
            // A well-formed blob whose JSON is not a profile: the codec was happy, the schema was
            // not. Reported as corrupt because that is what it is from the player's point of view.
            Log.w(TAG, failure) { "profile '$key' decoded but is not a valid save" }
            Result.failure(SaveLoadException(SaveLoadFailure.Corrupt(failure)))
        }
    }

    /**
     * Writes [save], stamping `LAST_SAVE` and incrementing `SAVE_NUMBER`.
     *
     * @param at epoch millis to record as the save time. Injected rather than read from a clock —
     *   `commonMain` has none, and a repository that reads the wall clock cannot be tested.
     * @return the profile as written, so the caller holds the stamped copy rather than the one
     *   it passed in. Forgetting to adopt it is how `SAVE_NUMBER` would stop advancing.
     */
    suspend fun save(save: GameSave, at: Long): GameSave {
        val stamped = save.sane().copy(lastSave = at, saveNumber = save.saveNumber + 1)
        store.write(keyFor(stamped), SaveCodec.encode(Format.encodeToString(stamped), random))
        return stamped
    }

    /**
     * Every stored profile, newest save first — the order a load screen wants.
     *
     * Unreadable profiles are **skipped and logged**, not propagated: one corrupt file must not
     * make the other four unlistable. [SaveSlot] carries the key, so a caller can offer to delete
     * what it could not read by listing [keys] separately.
     */
    // TooGenericExceptionCaught: see [load]. A store that cannot be enumerated must not stop the
    // load screen appearing, so this degrades to an empty list and logs why.
    @Suppress("TooGenericExceptionCaught")
    suspend fun list(): List<SaveSlot> {
        val keys = try {
            store.keys()
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "could not list profiles" }
            return emptyList()
        }
        return keys
            .mapNotNull { key -> load(key).getOrNull()?.let { SaveSlot(key, it) } }
            .sortedByDescending { it.lastSave }
    }

    /** The keys of every stored document, readable or not. */
    suspend fun keys(): List<String> = store.keys()

    suspend fun delete(key: String) = store.delete(key)

    /** Whether anything is stored at all — the "no profile yet, show new game" signal. */
    suspend fun isEmpty(): Boolean = keys().isEmpty()

    /**
     * Creates and stores a fresh profile.
     *
     * @param mode which card collection this profile plays with. Chosen once, at creation, and not
     *   changed afterwards: it decides which of the two card tables the profile's card ids index,
     *   which opponents it can meet ([NpcCatalog.collection]) and which rules those opponents may
     *   impose ([Roulette.pools][com.tripletriad.model.Roulette.pools]). The AS3 sets
     *   `DATAS.MODE = 'ff14_'` in `setToDefaultValues()` and offers no way to change it either.
     * @param createdAt used for both `CREATION_DATE` and `LAST_SAVE`, so the two agree on a new
     *   profile as they do in `setToDefaultValues()`.
     */
    suspend fun create(
        username: String = GameSave.DEFAULT_USERNAME,
        mode: CardCollection = CardCollection.FF14,
        createdAt: Long,
    ): GameSave = save(
        GameSave.new(username = username, mode = mode, createdAt = createdAt),
        createdAt,
    )

    companion object {
        private const val TAG = "Save"

        /** The subdirectory a host store should use, matching AS3 `saves/`. */
        const val COLLECTION = "saves"

        /**
         * The document key for a profile.
         *
         * `Save.as:85` names the file `'<username lowercased> - <CREATION_DATE>.sav'`. Both parts
         * are kept: the username so a player can recognise the file, and the creation date because
         * it is what keeps two profiles of the same name apart. The `.sav` extension is the host
         * store's business, not the key's.
         *
         * Anything a filesystem would refuse is replaced rather than rejected — the username is
         * player-typed, and a name with a slash in it should produce a save, not an error. That the
         * replacement can make two distinct names collide is why the creation date is in the key.
         */
        fun keyFor(save: GameSave): String {
            val name = save.username
                .lowercase()
                .map { if (it in ALLOWED_KEY_CHARS || it.isLetterOrDigit()) it else '_' }
                .joinToString("")
                .trim()
                .ifEmpty { "profile" }
            return "$name - ${save.creationDate}"
        }

        /** Punctuation a filename may keep; everything else becomes an underscore. */
        private const val ALLOWED_KEY_CHARS = " -_"

        /**
         * `encodeDefaults` so a written profile is complete: `ignoreUnknownKeys` on the way in
         * means an omitted field is silently defaulted, and writing a partial file would make the
         * two behaviours compound into quiet data loss across versions.
         *
         * No `prettyPrint`, unlike the settings file — this one is obfuscated, so nobody reads it.
         */
        private val Format = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

/** Carries a [SaveLoadFailure] out of [SaveRepository.load]'s [Result]. */
class SaveLoadException(val failure: SaveLoadFailure) : Exception(failure.toString())
