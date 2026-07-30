package com.tripletriad.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** [MatchRecord] and [MatchResult]: building a row from a finished match, and the round trip. */
class MatchRecordTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun card(id: Int) = Card(
        id = id,
        collection = "ff14_",
        nameKey = "STR_CARD_$id",
        name = "Card $id",
        top = 5,
        right = 5,
        bottom = 5,
        left = 5,
        rarity = 1,
    )

    /** A finished match: nine placements, so [MatchState.outcome] returns something. */
    private fun finished(): MatchState {
        var state = MatchState.start(
            blueHand = (1..HAND_SIZE).map { card(it) },
            redHand = (6..10).map { card(it) },
        )
        while (!state.isFinished) {
            val card = state.currentHand.first()
            state = state.play(card, state.playablePositions().first())
        }
        return state
    }

    @Test
    fun aResultIsReadFromTheSideTheProfilePlayed() {
        val score = MatchScore(6, 4)
        val win = MatchOutcome.Win(CardColor.BLUE, score)

        assertEquals(MatchResult.WIN, MatchResult.of(win, CardColor.BLUE))
        assertEquals(MatchResult.LOSE, MatchResult.of(win, CardColor.RED))
        assertEquals(
            MatchResult.DRAW,
            MatchResult.of(MatchOutcome.Draw(MatchScore(5, 5)), CardColor.BLUE),
        )
    }

    /**
     * An unresolved sudden death is not a finished match: recording it would count one match twice.
     */
    @Test
    fun anUnresolvedSuddenDeathHasNoResult() {
        val outcome = MatchOutcome.SuddenDeath(MatchScore(5, 5))

        assertNull(MatchResult.of(outcome, CardColor.BLUE))
    }

    @Test
    fun aRowIsBuiltFromAFinishedMatch() {
        val state = finished()

        val record = MatchRecord.of(
            id = "m1",
            state = state,
            mode = CardCollection.FF14,
            opponentKind = OpponentKind.NPC,
            timestamp = 1_700_000_000_000,
            opponentName = "jonas",
            npcId = 2,
            durationMillis = 90_000,
            mgpDelta = 27,
            xpGained = 27,
        )!!

        assertEquals("m1", record.id)
        assertEquals(OpponentKind.NPC, record.opponentKind)
        assertEquals("jonas", record.opponentName)
        assertEquals(2, record.npcId)
        assertEquals(1_700_000_000_000, record.timestamp)
        assertEquals(state.rules, record.rules)
        assertEquals(state.score, record.score)
        assertEquals(
            TOTAL_CARDS,
            record.scoreBlue + record.scoreRed,
            "every card counts for a side",
        )
    }

    @Test
    fun anUnfinishedMatchYieldsNoRow() {
        val running = MatchState.start(
            blueHand = (1..HAND_SIZE).map { card(it) },
            redHand = (6..10).map { card(it) },
        )

        assertNull(
            MatchRecord.of(
                id = "m1",
                state = running,
                mode = CardCollection.FF14,
                opponentKind = OpponentKind.PVP,
                timestamp = 1,
            ),
        )
    }

    @Test
    fun theScoreIsReadableFromTheProfilesSide() {
        val record = MatchRecord(
            id = "m1",
            mode = CardCollection.FF14,
            opponentKind = OpponentKind.PVP,
            timestamp = 1,
            result = MatchResult.LOSE,
            self = CardColor.RED,
            scoreBlue = 7,
            scoreRed = 3,
        )

        assertEquals(3, record.ownScore)
        assertEquals(7, record.opponentScore)
    }

    @Test
    fun aRowRoundTrips() {
        val record = MatchRecord(
            id = "m1",
            mode = CardCollection.FF8,
            opponentKind = OpponentKind.NPC,
            opponentName = "kid",
            npcId = 1,
            timestamp = 1_700_000_000_000,
            result = MatchResult.WIN,
            scoreBlue = 6,
            scoreRed = 4,
            durationMillis = 60_000,
            rules = GameRules(open = OpenRule.ALL_OPEN, plus = true),
            mgpDelta = 10,
            xpGained = 27,
        )

        assertEquals(record, json.decodeFromString<MatchRecord>(json.encodeToString(record)))
    }

    @Test
    fun aRowWithoutAnIdIsAProgrammingError() {
        assertFailsWith<IllegalArgumentException> {
            MatchRecord(
                id = "  ",
                mode = CardCollection.FF14,
                opponentKind = OpponentKind.PVP,
                timestamp = 1,
                result = MatchResult.DRAW,
                scoreBlue = 5,
                scoreRed = 5,
            )
        }
    }

    @Test
    fun aNegativeScoreIsAProgrammingError() {
        assertFailsWith<IllegalArgumentException> {
            MatchRecord(
                id = "m1",
                mode = CardCollection.FF14,
                opponentKind = OpponentKind.PVP,
                timestamp = 1,
                result = MatchResult.DRAW,
                scoreBlue = -1,
                scoreRed = 5,
            )
        }
    }
}
