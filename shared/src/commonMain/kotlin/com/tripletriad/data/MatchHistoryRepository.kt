package com.tripletriad.data

import com.tripletriad.log.Log
import com.tripletriad.model.MatchRecord
import com.tripletriad.model.MatchResult
import com.tripletriad.model.OpponentKind
import com.tripletriad.storage.DocumentStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Aggregates over a set of [MatchRecord]s, for a profile or stats screen. */
data class MatchTally(
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
) {
    val played: Int get() = wins + losses + draws
    val winRate: Float get() = if (played == 0) 0f else wins.toFloat() / played

    companion object {
        fun of(records: List<MatchRecord>): MatchTally = MatchTally(
            wins = records.count { it.result == MatchResult.WIN },
            losses = records.count { it.result == MatchResult.LOSE },
            draws = records.count { it.result == MatchResult.DRAW },
        )
    }
}

/** The stored shape: a versioned envelope around the list. */
@Serializable
private data class HistoryDocument(
    val version: Int = MatchHistoryRepository.VERSION,
    val matches: List<MatchRecord> = emptyList(),
)

/**
 * Match history, one document per profile.
 *
 * ### New in the port
 *
 * The AS3 keeps no history at all — six counters on the profile and nothing else (see
 * [MatchRecord]). This is the `MatchHistory` table of `docs/migration/06-PHASE-2-DATA-LAYER.md`
 * Task 2.3, as a document rather than a SQL table; [com.tripletriad.storage.DocumentStore] explains
 * why.
 *
 * ### Kept apart from the profile
 *
 * History is stored under the profile's key in a **separate** [DocumentStore], not inside the
 * [com.tripletriad.model.GameSave]. A profile is bounded and rewritten on every save; history grows
 * for as long as someone plays. Putting the two together would make every save get slower forever,
 * which is the one performance mistake in a save system that is genuinely hard to undo later.
 *
 * ### Bounded on write
 *
 * [limit] rows are kept per profile, oldest dropped. Not because the file would be large — 2,000
 * rows is a few hundred kilobytes — but because "grows without bound on a phone" is not a property
 * to ship without a decision, and because it is written in full on every append. [append] reports
 * what it dropped rather than trimming silently.
 *
 * That last point is also this class's known cost: an append reads, sorts and rewrites the whole
 * document, so it is O(n) per match and the document is re-serialised each time. Fine at one write
 * per finished match; the wrong shape for anything chattier. If history ever needs to be longer,
 * written more often, or queried by column, that is the point at which SQLDelight earns its place —
 * and it goes behind this class without a caller noticing.
 *
 * @param limit rows kept per profile. A parameter so a test can exercise the drop path without
 *   writing [DEFAULT_LIMIT] rows, which would be quadratic for no added coverage.
 */
class MatchHistoryRepository(
    private val store: DocumentStore,
    private val limit: Int = DEFAULT_LIMIT,
) {
    init {
        require(limit > 0) { "limit must be positive, was $limit" }
    }

    /**
     * Every match for [profileKey], newest first. Empty when there are none, or when the document
     * cannot be read — history is not worth failing a screen over, but it is worth logging.
     */
    // TooGenericExceptionCaught: as in [SaveRepository.load], the throwable depends on the host's
    // file API and there is no common supertype to name. ReturnCount: three exits, one per outcome
    // (unreadable store, no document, parsed) — all three yield a list and all three log.
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    suspend fun all(profileKey: String): List<MatchRecord> {
        val text = try {
            store.read(profileKey)
        } catch (failure: Exception) {
            Log.w(TAG, failure) { "could not read match history for '$profileKey'" }
            return emptyList()
        } ?: return emptyList()

        return try {
            Format.decodeFromString<HistoryDocument>(
                text,
            ).matches.sortedByDescending { it.timestamp }
        } catch (failure: Exception) {
            // Deliberately not rethrown and not repaired in place: a damaged history reads as
            // empty, and the next append rewrites the document from scratch. Losing history is
            // survivable in a way that losing a profile is not, which is why this differs from
            // SaveRepository.
            Log.w(
                TAG,
                failure,
            ) { "match history for '$profileKey' is unreadable; treating as empty" }
            emptyList()
        }
    }

    /**
     * Appends [record], dropping the oldest rows past [limit].
     *
     * Re-appending an id that is already present **replaces** it rather than duplicating, so a
     * retry after a failed write is safe.
     *
     * @return how many rows were dropped, so a caller can say so rather than discover it.
     */
    suspend fun append(profileKey: String, record: MatchRecord): Int {
        val existing = all(profileKey).filterNot { it.id == record.id }
        val combined = (listOf(record) + existing).sortedByDescending { it.timestamp }
        val kept = combined.take(limit)
        val dropped = combined.size - kept.size
        if (dropped > 0) {
            Log.i(TAG) { "match history for '$profileKey' is full; dropped $dropped oldest" }
        }
        store.write(profileKey, Format.encodeToString(HistoryDocument(matches = kept)))
        return dropped
    }

    /** The most recent [count] matches. */
    suspend fun recent(profileKey: String, count: Int = 10): List<MatchRecord> =
        all(profileKey).take(count)

    /** Matches against one NPC, by its `iconID` — the key wins are recorded under. */
    suspend fun againstNpc(profileKey: String, npcIconId: String): List<MatchRecord> =
        all(profileKey).filter {
            it.opponentKind == OpponentKind.NPC && it.opponentName == npcIconId
        }

    /**
     * Matches in which [ruleKey] was active. Keys are [com.tripletriad.model.GameRules] constants.
     */
    suspend fun withRule(profileKey: String, ruleKey: String): List<MatchRecord> =
        all(profileKey).filter { ruleKey in it.rules.activeRuleKeys() }

    /** Win/loss/draw counts over the whole history, or over one opponent kind. */
    suspend fun tally(profileKey: String, kind: OpponentKind? = null): MatchTally =
        MatchTally.of(all(profileKey).filter { kind == null || it.opponentKind == kind })

    /** Removes a profile's history. Called when its profile is deleted. */
    suspend fun clear(profileKey: String) = store.delete(profileKey)

    companion object {
        private const val TAG = "History"

        /** The subdirectory a host store should use. */
        const val COLLECTION = "history"

        /** Rows kept per profile unless a caller says otherwise. */
        const val DEFAULT_LIMIT = 2_000

        /** Bumped if [HistoryDocument]'s layout ever changes incompatibly. */
        const val VERSION = 1

        private val Format = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
