package com.tripletriad.data

import com.tripletriad.model.CardCollection
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameRules
import com.tripletriad.model.MatchRecord
import com.tripletriad.model.MatchResult
import com.tripletriad.model.OpenRule
import com.tripletriad.model.OpponentKind
import com.tripletriad.storage.InMemoryDocumentStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** [MatchHistoryRepository]: appending, querying, tallying and the row limit. */
class MatchHistoryRepositoryTest {
    private val profile = "mao - 1000"

    /**
     * A plausible row. Callers vary the rest with `.copy()` rather than through more parameters:
     * six named arguments on a test factory is where the factory stops being easier to read
     * than the thing it builds.
     */
    private fun record(
        id: String,
        timestamp: Long,
        result: MatchResult = MatchResult.WIN,
    ) = MatchRecord(
        id = id,
        mode = CardCollection.FF14,
        opponentKind = OpponentKind.NPC,
        opponentName = "jonas",
        timestamp = timestamp,
        result = result,
        scoreBlue = if (result == MatchResult.WIN) 6 else 4,
        scoreRed = if (result == MatchResult.WIN) 4 else 6,
    )

    @Test
    fun anEmptyHistoryReadsAsEmpty() = runTest {
        val repository = MatchHistoryRepository(InMemoryDocumentStore())

        assertTrue(repository.all(profile).isEmpty())
        assertEquals(MatchTally(), repository.tally(profile))
    }

    @Test
    fun appendedMatchesComeBackNewestFirst() = runTest {
        val repository = MatchHistoryRepository(InMemoryDocumentStore())

        repository.append(profile, record("a", 1_000))
        repository.append(profile, record("b", 3_000))
        repository.append(profile, record("c", 2_000))

        assertEquals(listOf("b", "c", "a"), repository.all(profile).map { it.id })
    }

    /** Re-appending the same id replaces rather than duplicating, so a retry is safe. */
    @Test
    fun appendingTheSameIdTwiceReplacesIt() = runTest {
        val repository = MatchHistoryRepository(InMemoryDocumentStore())

        repository.append(profile, record("a", 1_000, MatchResult.WIN))
        repository.append(profile, record("a", 1_000, MatchResult.LOSE))

        val all = repository.all(profile)
        assertEquals(1, all.size)
        assertEquals(MatchResult.LOSE, all.single().result)
    }

    @Test
    fun historyIsPerProfile() = runTest {
        val repository = MatchHistoryRepository(InMemoryDocumentStore())

        repository.append(profile, record("a", 1_000))
        repository.append("other - 2000", record("b", 1_000))

        assertEquals(listOf("a"), repository.all(profile).map { it.id })
        assertEquals(listOf("b"), repository.all("other - 2000").map { it.id })
    }

    @Test
    fun recentTakesTheNewest() = runTest {
        val repository = MatchHistoryRepository(InMemoryDocumentStore())
        repeat(20) { repository.append(profile, record("m$it", it.toLong())) }

        val recent = repository.recent(profile, count = 3)

        assertEquals(listOf("m19", "m18", "m17"), recent.map { it.id })
    }

    @Test
    fun matchesCanBeFilteredByOpponent() = runTest {
        val repository = MatchHistoryRepository(InMemoryDocumentStore())
        repository.append(profile, record("a", 1))
        repository.append(profile, record("b", 2).copy(opponentName = "tt-master"))
        repository.append(profile, record("c", 3).copy(opponentKind = OpponentKind.PVP))

        val againstJonas = repository.againstNpc(profile, "jonas")

        assertEquals(listOf("a"), againstJonas.map { it.id }, "the PVP row is a different opponent")
    }

    @Test
    fun matchesCanBeFilteredByRule() = runTest {
        val repository = MatchHistoryRepository(InMemoryDocumentStore())
        repository.append(profile, record("plain", 1))
        repository.append(profile, record("plus", 2).copy(rules = GameRules(plus = true)))
        val open = record("open", 3).copy(rules = GameRules(open = OpenRule.ALL_OPEN))
        repository.append(profile, open)

        assertEquals(listOf("plus"), repository.withRule(profile, "RULE_PLUS").map { it.id })
        assertEquals(listOf("open"), repository.withRule(profile, "RULE_ALL_OPEN").map { it.id })
        assertTrue(repository.withRule(profile, "RULE_SWAP").isEmpty())
    }

    @Test
    fun tallyCountsResults() = runTest {
        val repository = MatchHistoryRepository(InMemoryDocumentStore())
        repository.append(profile, record("a", 1, MatchResult.WIN))
        repository.append(profile, record("b", 2, MatchResult.WIN))
        repository.append(profile, record("c", 3, MatchResult.LOSE))
        repository.append(profile, record("d", 4, MatchResult.DRAW))

        val tally = repository.tally(profile)

        assertEquals(MatchTally(wins = 2, losses = 1, draws = 1), tally)
        assertEquals(4, tally.played)
        assertEquals(0.5f, tally.winRate)
    }

    @Test
    fun tallyCanBeNarrowedToOneOpponentKind() = runTest {
        val repository = MatchHistoryRepository(InMemoryDocumentStore())
        repository.append(profile, record("a", 1, MatchResult.WIN))
        val pvp = record("b", 2, MatchResult.LOSE).copy(opponentKind = OpponentKind.PVP)
        repository.append(profile, pvp)

        assertEquals(1, repository.tally(profile, OpponentKind.NPC).wins)
        assertEquals(1, repository.tally(profile, OpponentKind.PVP).losses)
    }

    @Test
    fun winRateIsZeroWithNoMatches() {
        assertEquals(0f, MatchTally().winRate)
    }

    @Test
    fun everythingSurvivesARoundTripThroughTheStore() = runTest {
        val store = InMemoryDocumentStore()
        val written = record("a", 1_000)
            .copy(rules = GameRules(plus = true, open = OpenRule.THREE_OPEN))
            .copy(
                self = CardColor.RED,
                durationMillis = 90_000,
                mgpDelta = -13,
                xpGained = 27,
                npcId = 8,
            )

        MatchHistoryRepository(store).append(profile, written)

        assertEquals(written, MatchHistoryRepository(store).all(profile).single())
    }

    /**
     * A damaged history reads as empty and is rewritten on the next append; losing it is
     * survivable.
     */
    @Test
    fun anUnreadableDocumentReadsAsEmptyRatherThanThrowing() = runTest {
        val store = InMemoryDocumentStore(mapOf(profile to "not json at all"))
        val repository = MatchHistoryRepository(store)

        assertTrue(repository.all(profile).isEmpty())

        repository.append(profile, record("a", 1))

        assertEquals(listOf("a"), repository.all(profile).map { it.id })
    }

    @Test
    fun anUnreadableStoreReadsAsEmpty() = runTest {
        val store = InMemoryDocumentStore(failure = IllegalStateException("gone"))

        assertTrue(MatchHistoryRepository(store).all(profile).isEmpty())
    }

    /**
     * The limit drops the oldest and says how many, rather than trimming silently.
     *
     * Run at a limit of 3 rather than the default 2,000: appending is O(n) per call, so filling the
     * real limit would be quadratic and would test the same branch.
     */
    @Test
    fun theOldestRowsAreDroppedPastTheLimit() = runTest {
        val repository = MatchHistoryRepository(InMemoryDocumentStore(), limit = 3)
        for (i in 1..3) repository.append(profile, record("m$i", i.toLong()))

        assertEquals(3, repository.all(profile).size)

        val dropped = repository.append(profile, record("newest", 999_999))

        assertEquals(1, dropped)
        val all = repository.all(profile)
        assertEquals(3, all.size)
        assertEquals("newest", all.first().id)
        assertTrue(all.none { it.id == "m1" }, "the oldest row went")
    }

    @Test
    fun appendingSeveralAtOnceOverTheLimitReportsEveryDrop() = runTest {
        val repository = MatchHistoryRepository(InMemoryDocumentStore(), limit = 2)
        repository.append(profile, record("a", 1))
        repository.append(profile, record("b", 2))

        assertEquals(1, repository.append(profile, record("c", 3)))
        assertEquals(listOf("c", "b"), repository.all(profile).map { it.id })
    }

    @Test
    fun aNonPositiveLimitIsAProgrammingError() {
        assertFailsWith<IllegalArgumentException> {
            MatchHistoryRepository(InMemoryDocumentStore(), limit = 0)
        }
    }

    @Test
    fun theDefaultLimitIsTheDocumentedOne() {
        assertEquals(2_000, MatchHistoryRepository.DEFAULT_LIMIT)
    }

    @Test
    fun clearingRemovesTheHistory() = runTest {
        val repository = MatchHistoryRepository(InMemoryDocumentStore())
        repository.append(profile, record("a", 1))

        repository.clear(profile)

        assertTrue(repository.all(profile).isEmpty())
    }
}
