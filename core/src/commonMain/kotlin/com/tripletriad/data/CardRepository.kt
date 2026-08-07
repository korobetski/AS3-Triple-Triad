package com.tripletriad.data

import com.tripletriad.model.Card
import com.tripletriad.model.CardCollection
import com.tripletriad.model.CardType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Read access to the card tables.
 *
 * The interface exists so callers depend on the queries and not on where the 263 records came from:
 * [BundledCardRepository] reads them through Compose resources, and a test hands over a list.
 */
interface CardRepository {
    suspend fun all(collection: CardCollection): List<Card>

    suspend fun byId(id: Int, collection: CardCollection): Card?

    suspend fun byIds(ids: List<Int>, collection: CardCollection): List<Card>

    suspend fun byRarity(rarity: Int, collection: CardCollection): List<Card>

    suspend fun byType(type: CardType?, collection: CardCollection): List<Card>

    /**
     * Cards whose [Card.name] contains [query], case-insensitively. Blank matches everything, which
     * is what a search field with nothing typed in it should show.
     */
    suspend fun search(query: String, collection: CardCollection): List<Card>

    /**
     * The ids of every card of any of [rarities].
     *
     * `cards.getCardsByRarities` (`cards.as:308-317`), which `NPCs.as` uses to give two opponents a
     * pool of every card at or below a rarity. Returns ids rather than cards because that is what
     * [com.tripletriad.model.Npc.cards] holds.
     */
    suspend fun idsByRarities(rarities: Set<Int>, collection: CardCollection): List<Int>
}

/**
 * The bundled card tables, loaded once and held in memory.
 *
 * ### Why there is no eviction
 *
 * `docs/migration/06-PHASE-2-DATA-LAYER.md` Task 2.6 asks for an LRU cache. There are **263 cards**
 * in total, they are immutable bundled data, and the whole set is well under 100 KB — so the cache
 * would never evict anything, and the eviction logic would be untested code guarding against a
 * condition that cannot arise. The document's own margin note reaches the same conclusion. This
 * loads everything on first use and keeps it.
 *
 * (It also could not have been an `android.util.LruCache`, which is Android-only and does not exist
 * in `commonMain`. Card *images* are a separate matter and are handled by Compose Resources.)
 *
 * ### Loading
 *
 * [load] is called at most once even under concurrent first calls — the double-check around the
 * mutex is the standard shape, and it matters here because [com.tripletriad.ui.rememberStartup] and
 * a screen could plausibly both ask first. The suspend-friendly `kotlinx.coroutines.sync.Mutex` is
 * used rather than a lock, since the loader itself suspends.
 *
 * ### Why [load] has no default any more
 *
 * It used to default to `loadCardCatalog()`, which reads the Compose resource bundle — the one
 * thing `:core` may not link against. Removing the default cost nothing: **no production code
 * constructs this class**, so the default was only ever exercised by not being used. Callers that
 * want the bundled catalog pass `{ loadCardCatalog() }`, which is where that dependency belongs.
 *
 * @param load reads the catalog, once. A test passes a lambda; the client passes the loader from
 *   `:shared`; the server will pass whatever it reads the catalog from.
 */
class BundledCardRepository(
    private val load: suspend () -> CardCatalog,
) : CardRepository {
    private val mutex = Mutex()
    private var catalog: CardCatalog? = null

    /** The whole catalog, loading it if this is the first call. */
    suspend fun catalog(): CardCatalog =
        catalog ?: mutex.withLock { catalog ?: load().also { catalog = it } }

    /**
     * Drops the loaded catalog, so the next call reads it again. For tests and for a mode switch.
     */
    suspend fun invalidate() {
        mutex.withLock { catalog = null }
    }

    override suspend fun all(collection: CardCollection): List<Card> =
        catalog().collection(collection.prefix)

    /**
     * Card [id] of [collection], or null if the table has no such index.
     *
     * The AS3 indexes `cards.DATAS[id]` directly and would return `undefined` for an id past the
     * end; every caller there has an id that came out of the same table. Null here for the same
     * situation, because a save file can name a card id this build's table does not have — a
     * profile from a later version, or from the other collection.
     */
    override suspend fun byId(id: Int, collection: CardCollection): Card? =
        all(collection).firstOrNull { it.id == id }

    /**
     * The cards for [ids], skipping any the table does not have.
     *
     * Order follows [ids], not the table, because callers pass a deck or a hand and the order is
     * the point. Duplicated ids yield duplicated cards, which is what a hand of two identical cards
     * needs.
     */
    override suspend fun byIds(ids: List<Int>, collection: CardCollection): List<Card> {
        val byId = all(collection).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    override suspend fun byRarity(rarity: Int, collection: CardCollection): List<Card> =
        all(collection).filter { it.rarity == rarity }

    /** [type] of null selects the cards with no type, which is most of them. */
    override suspend fun byType(type: CardType?, collection: CardCollection): List<Card> =
        all(collection).filter { it.type == type }

    override suspend fun search(query: String, collection: CardCollection): List<Card> {
        val needle = query.trim()
        if (needle.isEmpty()) return all(collection)
        return all(collection).filter { it.name.contains(needle, ignoreCase = true) }
    }

    override suspend fun idsByRarities(rarities: Set<Int>, collection: CardCollection): List<Int> =
        all(collection).filter { it.rarity in rarities }.map { it.id }
}

/**
 * A [CardRepository] over a fixed list. For tests, and for previews that need three cards rather
 * than a resource bundle.
 */
class InMemoryCardRepository(private val cards: List<Card>) : CardRepository {
    override suspend fun all(collection: CardCollection): List<Card> =
        cards.filter { it.collection == collection.prefix }

    override suspend fun byId(id: Int, collection: CardCollection): Card? =
        all(collection).firstOrNull { it.id == id }

    override suspend fun byIds(ids: List<Int>, collection: CardCollection): List<Card> {
        val byId = all(collection).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    override suspend fun byRarity(rarity: Int, collection: CardCollection): List<Card> =
        all(collection).filter { it.rarity == rarity }

    override suspend fun byType(type: CardType?, collection: CardCollection): List<Card> =
        all(collection).filter { it.type == type }

    override suspend fun search(query: String, collection: CardCollection): List<Card> {
        val needle = query.trim()
        if (needle.isEmpty()) return all(collection)
        return all(collection).filter { it.name.contains(needle, ignoreCase = true) }
    }

    override suspend fun idsByRarities(rarities: Set<Int>, collection: CardCollection): List<Int> =
        all(collection).filter { it.rarity in rarities }.map { it.id }
}
