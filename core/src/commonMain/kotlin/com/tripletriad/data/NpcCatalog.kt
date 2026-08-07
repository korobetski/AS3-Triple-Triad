package com.tripletriad.data

import com.tripletriad.model.CardCollection
import com.tripletriad.model.Npc
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The two NPC tables of `sources/src/tto/datas/NPCs.as`, as extracted by
 * `tools/extract_npcs.py`.
 *
 * Selected by the profile's mode exactly as the AS3 does — `NPCs.LIST` returns
 * `NPCs[MODE.toUpperCase() + 'NPCS']` — so a profile sees one table's opponents and never the
 * other's.
 */
@Serializable
data class NpcCatalog(
    val ff14: List<Npc>,
    val ff8: List<Npc>,
) {
    /** All opponents of both collections, ff14 first, in AS3 array order. */
    val all: List<Npc> get() = ff14 + ff8

    fun collection(collection: CardCollection): List<Npc> = when (collection) {
        CardCollection.FF14 -> ff14
        CardCollection.FF8 -> ff8
    }

    /**
     * One opponent by its `iconID`, searched within [collection].
     *
     * Keyed by icon rather than by `id` because ids are **not unique** — the ff8 table repeats 2
     * and 13 — and because `NPC_W` records wins under the icon id anyway. See [Npc.id].
     */
    fun byIcon(iconId: String, collection: CardCollection): Npc? =
        collection(collection).firstOrNull { it.iconId == iconId }

    /**
     * The opponents challengeable at [hour], in the order the opponent list shows them.
     *
     * `NPCs.toListCollection()` sorts on `difficulty`, then `matchFee`, then `name`
     * (`NPCs.as:1141`) and then filters by availability. Both are reproduced; the availability test
     * is not — see [com.tripletriad.model.Availability] for why the original's cannot work.
     *
     * @param hour wall-clock hour, 0..23. Injected rather than read from a clock so the list is
     *   testable, which is the same reason nothing else in this module reads one.
     */
    fun available(collection: CardCollection, hour: Int): List<Npc> =
        collection(collection)
            .filter { it.availability.isOpenAtHour(hour) }
            .sortedWith(compareBy({ it.difficulty }, { it.matchFee }, { it.nameKey.lowercase() }))
}

/** Parses the NPC catalog. Split from its loader for the same reason [CardCatalogParser] is. */
object NpcCatalogParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): NpcCatalog = json.decodeFromString(text)
}
