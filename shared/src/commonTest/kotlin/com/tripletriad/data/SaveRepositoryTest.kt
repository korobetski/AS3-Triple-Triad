package com.tripletriad.data

import com.tripletriad.model.CardCollection
import com.tripletriad.model.CardItem
import com.tripletriad.model.GameSave
import com.tripletriad.storage.InMemoryDocumentStore
import com.tripletriad.storage.SaveCodec
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [SaveRepository] against an [InMemoryDocumentStore], so this runs on every target with no
 * filesystem.
 *
 * The clock is a parameter throughout, which is what makes "the save stamped the right time" an
 * assertion rather than a hope.
 */
class SaveRepositoryTest {
    private fun repository(store: InMemoryDocumentStore = InMemoryDocumentStore()) =
        SaveRepository(store, Random(1)) to store

    @Test
    fun aSavedProfileLoadsBackIdentically() = runTest {
        val (repository, _) = repository()
        val original = GameSave.new(username = "Mao", createdAt = 1_000).copy(
            mgp = 4_200,
            cards = listOf(1, 3, 6, 7, 10, 42),
            bag = listOf(CardItem(42, 2)),
        )

        val written = repository.save(original, at = 2_000)
        val loaded = repository.load(SaveRepository.keyFor(written)).getOrThrow()

        assertEquals(written, loaded)
        assertEquals(4_200, loaded.mgp)
        assertEquals(listOf(CardItem(42, 2)), loaded.bag)
    }

    /** `Save.as:82-83`: the save stamps `LAST_SAVE` and bumps `SAVE_NUMBER` itself. */
    @Test
    fun savingStampsTheTimeAndIncrementsTheSaveNumber() = runTest {
        val (repository, _) = repository()

        val first = repository.save(GameSave.new(createdAt = 1_000), at = 5_000)
        val second = repository.save(first, at = 9_000)

        assertEquals(5_000, first.lastSave)
        assertEquals(1, first.saveNumber)
        assertEquals(9_000, second.lastSave)
        assertEquals(2, second.saveNumber)
        assertEquals(1_000, second.creationDate, "the creation date never moves")
    }

    @Test
    fun whatIsOnDiskIsNotReadableText() = runTest {
        val (repository, store) = repository()

        repository.save(GameSave.new(username = "Kuplu Kopo", createdAt = 1), at = 1)

        val blob = store.stored.values.single()
        assertTrue(blob.startsWith(SaveCodec.MAGIC))
        assertFalse(blob.contains("Kuplu"), "the save is obfuscated")
        assertFalse(blob.contains("USERNAME"))
    }

    /** `Save.as:85` names the file from the username and the creation date. Both are needed. */
    @Test
    fun theKeyCombinesTheLowercasedUsernameAndTheCreationDate() {
        val save = GameSave.new(username = "Kuplu Kopo", createdAt = 1_700_000_000_000)

        assertEquals("kuplu kopo - 1700000000000", SaveRepository.keyFor(save))
    }

    /**
     * Two profiles with the same name must not overwrite each other — the date keeps them apart.
     */
    @Test
    fun twoProfilesWithTheSameNameCoexist() = runTest {
        val (repository, _) = repository()

        repository.save(GameSave.new(username = "Mao", createdAt = 1_000), at = 1_000)
        repository.save(GameSave.new(username = "Mao", createdAt = 2_000), at = 2_000)

        assertEquals(2, repository.keys().size)
    }

    /** The username is player-typed, so a slash in it must produce a save, not an error. */
    @Test
    fun anAwkwardUsernameStillProducesAUsableKey() = runTest {
        val (repository, _) = repository()
        val save = GameSave.new(username = "a/b\\c:d", createdAt = 7)

        val written = repository.save(save, at = 7)
        val loaded = repository.load(SaveRepository.keyFor(written))

        assertEquals("a_b_c_d - 7", SaveRepository.keyFor(written))
        assertTrue(loaded.isSuccess)
    }

    @Test
    fun aBlankUsernameFallsBackRatherThanProducingABlankKey() {
        val key = SaveRepository.keyFor(GameSave.new(username = "   ", createdAt = 3))

        assertEquals("profile - 3", key)
    }

    @Test
    fun loadingSomethingThatIsNotThereReportsNotFound() = runTest {
        val (repository, _) = repository()

        val failure = repository.load("no such profile").exceptionOrNull()

        assertIs<SaveLoadException>(failure)
        assertEquals(SaveLoadFailure.NotFound, failure.failure)
    }

    @Test
    fun loadingAnAlteredFileReportsCorruptRatherThanThrowing() = runTest {
        val store = InMemoryDocumentStore(mapOf("tampered" to "${SaveCodec.MAGIC}0000000100ff"))
        val (repository, _) = repository(store)

        val failure = repository.load("tampered").exceptionOrNull()

        assertIs<SaveLoadException>(failure)
        assertIs<SaveLoadFailure.Corrupt>(failure.failure)
    }

    /**
     * A blob that decodes cleanly but is not a profile: the codec was happy, the schema was not.
     */
    @Test
    fun loadingValidObfuscationWrappingInvalidJsonReportsCorrupt() = runTest {
        val store = InMemoryDocumentStore(
            mapOf("nonsense" to SaveCodec.encode("[1,2,3]", Random(1))),
        )
        val (repository, _) = repository(store)

        assertIs<SaveLoadFailure.Corrupt>(
            (repository.load("nonsense").exceptionOrNull() as SaveLoadException).failure,
        )
    }

    @Test
    fun anUnreadableStoreIsReportedAsSuch() = runTest {
        val store = InMemoryDocumentStore(failure = IllegalStateException("permission denied"))
        val repository = SaveRepository(store)

        val failure = repository.load("anything").exceptionOrNull()

        assertIs<SaveLoadException>(failure)
        assertIs<SaveLoadFailure.Unreadable>(failure.failure)
    }

    /** A failed *write* must not be swallowed the way a settings write is: it is data loss. */
    @Test
    fun aFailedWritePropagates() = runTest {
        val store = InMemoryDocumentStore(failure = IllegalStateException("disk full"))
        val repository = SaveRepository(store)

        val thrown = runCatching { repository.save(GameSave.new(createdAt = 1), at = 1) }

        assertTrue(thrown.isFailure)
    }

    @Test
    fun listingReturnsProfilesNewestSaveFirst() = runTest {
        val (repository, _) = repository()
        repository.save(GameSave.new(username = "old", createdAt = 1), at = 1_000)
        repository.save(GameSave.new(username = "new", createdAt = 2), at = 9_000)
        repository.save(GameSave.new(username = "middle", createdAt = 3), at = 5_000)

        val slots = repository.list()

        assertEquals(listOf("new", "middle", "old"), slots.map { it.username })
        assertEquals(9_000, slots.first().lastSave)
        // The slot carries the key, so a caller can load it back without re-deriving it.
        assertEquals("new - 2", slots.first().key)
    }

    /** One damaged file must not make the others unlistable. */
    @Test
    fun listingSkipsWhatItCannotRead() = runTest {
        val (repository, store) = repository()
        repository.save(GameSave.new(username = "good", createdAt = 1), at = 1)
        store.write("broken", "this is not a save")

        val slots = repository.list()

        assertEquals(listOf("good"), slots.map { it.username })
        assertEquals(2, repository.keys().size, "the broken file is still there to be deleted")
    }

    @Test
    fun anUnreadableStoreListsAsEmptyRatherThanThrowing() = runTest {
        val store = InMemoryDocumentStore(failure = IllegalStateException("gone"))

        assertTrue(SaveRepository(store).list().isEmpty())
    }

    @Test
    fun creatingAProfileStoresItImmediately() = runTest {
        val (repository, _) = repository()

        assertTrue(repository.isEmpty())

        val save = repository.create(username = "Mao", createdAt = 4_242)

        assertFalse(repository.isEmpty())
        assertEquals("Mao", save.username)
        assertEquals(4_242, save.creationDate)
        assertEquals(4_242, save.lastSave)
        assertNotNull(repository.load(SaveRepository.keyFor(save)).getOrNull())
    }

    @Test
    fun deletingRemovesTheProfile() = runTest {
        val (repository, _) = repository()
        val save = repository.create(createdAt = 1)
        val key = SaveRepository.keyFor(save)

        repository.delete(key)

        assertTrue(repository.isEmpty())
        assertNull(repository.load(key).getOrNull())
    }

    /**
     * `Save.as:59` recomputes FORFEITS on load; `sane()` does the equivalent for LEVEL and RANK.
     */
    @Test
    fun loadingAppliesSaneSoDerivedFieldsAgreeWithTheirSources() = runTest {
        val (repository, store) = repository()
        // Hand-written blob claiming level 20 on 250 XP, as an edited save file would.
        val doctored = """{"USERNAME":"Cheat","CREATION_DATE":1,"XP":250,"LEVEL":20,"MGP":-5}"""
        store.write("cheat - 1", SaveCodec.encode(doctored, Random(1)))

        val loaded = repository.load("cheat - 1").getOrThrow()

        assertEquals(2, loaded.level)
        assertEquals(0, loaded.mgp)
    }

    @Test
    fun theModeSurvivesARoundTrip() = runTest {
        val (repository, _) = repository()
        val ff8 = GameSave.new(mode = CardCollection.FF8, createdAt = 1)

        val written = repository.save(ff8, at = 1)

        assertEquals(
            CardCollection.FF8,
            repository.load(SaveRepository.keyFor(written)).getOrThrow().mode,
        )
    }
}
