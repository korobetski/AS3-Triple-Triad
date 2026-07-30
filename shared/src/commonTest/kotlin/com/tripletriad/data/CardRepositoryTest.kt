package com.tripletriad.data

import com.tripletriad.model.Card
import com.tripletriad.model.CardCollection
import com.tripletriad.model.CardType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [BundledCardRepository] and [InMemoryCardRepository] against a hand-built catalog.
 *
 * The real bundle is covered by `CardBundleTest`, which needs a resource loader. Everything here is
 * about the query behaviour, and both implementations are held to the same assertions — an
 * interface with two implementations that behave differently is worse than one implementation.
 */
class CardRepositoryTest {
    private fun card(
        id: Int,
        collection: String = "ff14_",
        name: String = "Card $id",
        rarity: Int = 1,
        type: CardType? = null,
    ) = Card(
        id = id,
        collection = collection,
        nameKey = "STR_CARD_$id",
        name = name,
        top = 1,
        right = 2,
        bottom = 3,
        left = 4,
        rarity = rarity,
        type = type,
    )

    private val ff14 = listOf(
        card(1, name = "Dodo", rarity = 1),
        card(2, name = "Tonberry", rarity = 2, type = CardType.BEAST),
        card(3, name = "Sabotender", rarity = 2, type = CardType.BEAST),
        card(4, name = "Spriggan", rarity = 5, type = CardType.PRIMALS),
    )
    private val ff8 = listOf(
        card(1, collection = "ff8_", name = "Geezard", rarity = 1),
        card(2, collection = "ff8_", name = "Funguar", rarity = 3, type = CardType.FIRE),
    )
    private val catalog = CardCatalog(ff14 = ff14, ff8 = ff8)

    private fun repositories(): List<CardRepository> = listOf(
        BundledCardRepository { catalog },
        InMemoryCardRepository(ff14 + ff8),
    )

    @Test
    fun eachCollectionIsReturnedWhole() = runTest {
        for (repository in repositories()) {
            assertEquals(ff14, repository.all(CardCollection.FF14))
            assertEquals(ff8, repository.all(CardCollection.FF8))
        }
    }

    /** The two collections index from 1 independently, so an id alone is ambiguous. */
    @Test
    fun anIdIsResolvedWithinItsCollection() = runTest {
        for (repository in repositories()) {
            assertEquals("Dodo", repository.byId(1, CardCollection.FF14)?.name)
            assertEquals("Geezard", repository.byId(1, CardCollection.FF8)?.name)
        }
    }

    /** A save file can name an id this build's table does not have. Null, not an exception. */
    @Test
    fun anUnknownIdIsNull() = runTest {
        for (repository in repositories()) {
            assertNull(repository.byId(999, CardCollection.FF14))
            assertNull(repository.byId(4, CardCollection.FF8))
        }
    }

    /**
     * Order follows the request, because callers pass a deck or a hand and the order is the point.
     */
    @Test
    fun byIdsFollowsTheRequestedOrderAndSkipsWhatIsMissing() = runTest {
        for (repository in repositories()) {
            val cards = repository.byIds(listOf(4, 1, 999, 2), CardCollection.FF14)

            assertEquals(listOf(4, 1, 2), cards.map { it.id })
        }
    }

    /** A hand can hold two of the same card, so a duplicated id must yield a duplicated card. */
    @Test
    fun byIdsKeepsDuplicates() = runTest {
        for (repository in repositories()) {
            assertEquals(
                listOf(1, 1, 2),
                repository.byIds(listOf(1, 1, 2), CardCollection.FF14).map { it.id },
            )
        }
    }

    @Test
    fun byIdsOfNothingIsNothing() = runTest {
        for (repository in repositories()) {
            assertTrue(repository.byIds(emptyList(), CardCollection.FF14).isEmpty())
        }
    }

    @Test
    fun cardsCanBeFilteredByRarityAndType() = runTest {
        for (repository in repositories()) {
            assertEquals(listOf(2, 3), repository.byRarity(2, CardCollection.FF14).map { it.id })
            assertTrue(repository.byRarity(4, CardCollection.FF14).isEmpty())
            assertEquals(
                listOf(2, 3),
                repository.byType(CardType.BEAST, CardCollection.FF14).map { it.id },
            )
            // Most cards have no type at all, and that is a queryable value.
            assertEquals(listOf(1), repository.byType(null, CardCollection.FF14).map { it.id })
        }
    }

    @Test
    fun searchIsCaseInsensitiveAndPartial() = runTest {
        for (repository in repositories()) {
            assertEquals(
                listOf(2),
                repository.search("tonberry", CardCollection.FF14).map { it.id },
            )
            assertEquals(listOf(2), repository.search("BERR", CardCollection.FF14).map { it.id })
            assertTrue(repository.search("chocobo", CardCollection.FF14).isEmpty())
        }
    }

    /** An empty search field should show the collection, not nothing. */
    @Test
    fun anEmptySearchReturnsEverything() = runTest {
        for (repository in repositories()) {
            assertEquals(ff14, repository.search("", CardCollection.FF14))
            assertEquals(ff14, repository.search("   ", CardCollection.FF14))
        }
    }

    /** `cards.getCardsByRarities` (`cards.as:308-317`) — what two NPCs draw their pool from. */
    @Test
    fun idsByRaritiesMatchesTheAs3Helper() = runTest {
        for (repository in repositories()) {
            assertEquals(
                listOf(1, 2, 3),
                repository.idsByRarities(setOf(1, 2), CardCollection.FF14),
            )
            assertEquals(
                listOf(1, 2, 3, 4),
                repository.idsByRarities((1..5).toSet(), CardCollection.FF14),
            )
            assertTrue(repository.idsByRarities(emptySet(), CardCollection.FF14).isEmpty())
        }
    }

    /** 263 immutable records: loaded once and kept, with no eviction to get wrong. */
    @Test
    fun theCatalogIsLoadedOnceAndReused() = runTest {
        var loads = 0
        val repository = BundledCardRepository {
            loads++
            catalog
        }

        repository.all(CardCollection.FF14)
        repository.all(CardCollection.FF8)
        repository.byId(1, CardCollection.FF14)

        assertEquals(1, loads)
        assertSame(catalog, repository.catalog())
    }

    @Test
    fun invalidatingForcesAReload() = runTest {
        var loads = 0
        val repository = BundledCardRepository {
            loads++
            catalog
        }

        repository.all(CardCollection.FF14)
        repository.invalidate()
        repository.all(CardCollection.FF14)

        assertEquals(2, loads)
    }

    @Test
    fun theCatalogKeysItsCollectionsByTheAs3Prefix() {
        assertEquals(ff14, catalog.collection("ff14_"))
        assertEquals(ff8, catalog.collection("ff8_"))
        assertEquals(ff14 + ff8, catalog.all)
    }
}
