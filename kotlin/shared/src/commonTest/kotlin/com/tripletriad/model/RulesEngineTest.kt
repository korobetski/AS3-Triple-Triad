package com.tripletriad.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The test matrix from
 * [game-rules.md](../../../../../../../docs/analysis/game-rules.md) § 16 — the cases
 * that distinguish a correct engine from a plausible one.
 *
 * Every one is a pure function of board state: no UI, no coroutines, no test
 * dispatcher. That is the point of extracting the domain model first.
 */
class RulesEngineTest {
    // ---- fixtures ---------------------------------------------------------

    private fun card(
        id: Int = 1,
        top: Int = 5,
        right: Int = 5,
        bottom: Int = 5,
        left: Int = 5,
    ) = Card(
        id = id,
        collection = "test_",
        nameKey = "STR_TEST_$id",
        name = "Test $id",
        top = top,
        right = right,
        bottom = bottom,
        left = left,
        rarity = 1,
    )

    /** Board positions, row-major, matching `Board.tiles`. */
    private object At {
        const val TOP_LEFT = 0
        const val TOP_MID = 1
        const val TOP_RIGHT = 2
        const val MID_LEFT = 3
        const val CENTRE = 4
        const val MID_RIGHT = 5
        const val BOTTOM_LEFT = 6
        const val BOTTOM_MID = 7
    }

    private fun board(vararg placed: Triple<Int, Card, CardColor>): Board =
        placed.fold(Board()) { acc, (at, c, owner) -> acc.place(at, c, owner) }

    private fun engine(
        rules: GameRules = GameRules(),
        options: RulesEngineOptions = RulesEngineOptions(),
    ) = RulesEngine(rules, options)

    private fun Resolution.kindAt(position: Int): CaptureKind? =
        captures.firstOrNull { it.position == position }?.kind

    /** A plain 5/5/5/5 card carrying [type]. */
    private fun typed(type: CardType): Card = card(top = 5).copy(type = type)

    private fun beast(): Card = typed(CardType.BEAST)

    // ---- 1-6: basic capture ----------------------------------------------

    @Test
    fun higherPowerCaptures() {
        // A 3 sits above centre, showing 3 downward. We place a 9-top at centre.
        val start = board(Triple(At.TOP_MID, card(id = 1, bottom = 3), CardColor.RED))
        val result = engine().resolve(start, At.CENTRE, card(id = 2, top = 9), CardColor.BLUE)

        assertEquals(listOf(At.TOP_MID), result.capturedPositions)
        assertEquals(CardColor.BLUE, result.board[At.TOP_MID]?.owner)
    }

    @Test
    fun lowerPowerDoesNotCapture() {
        val start = board(Triple(At.TOP_MID, card(id = 1, bottom = 9), CardColor.RED))
        val result = engine().resolve(start, At.CENTRE, card(id = 2, top = 3), CardColor.BLUE)

        assertTrue(result.captures.isEmpty())
        assertEquals(CardColor.RED, result.board[At.TOP_MID]?.owner)
    }

    @Test
    fun equalPowersNeverCapture() {
        val start = board(Triple(At.TOP_MID, card(id = 1, bottom = 5), CardColor.RED))
        val attacker = card(id = 2, top = 5)

        assertTrue(
            engine().resolve(start, At.CENTRE, attacker, CardColor.BLUE).captures.isEmpty(),
            "a tie must not capture under the normal rule",
        )
        assertTrue(
            engine(GameRules(reverse = true))
                .resolve(start, At.CENTRE, attacker, CardColor.BLUE).captures.isEmpty(),
            "a tie must not capture under Reverse either — both comparisons are strict",
        )
    }

    @Test
    fun reverseInvertsTheComparison() {
        val start = board(Triple(At.TOP_MID, card(id = 1, bottom = 9), CardColor.RED))
        val result = engine(GameRules(reverse = true))
            .resolve(start, At.CENTRE, card(id = 2, top = 3), CardColor.BLUE)

        assertEquals(listOf(At.TOP_MID), result.capturedPositions)
    }

    @Test
    fun ownCardsAreNeverCaptured() {
        val start = board(Triple(At.TOP_MID, card(id = 1, bottom = 1), CardColor.BLUE))
        val result = engine().resolve(start, At.CENTRE, card(id = 2, top = 9), CardColor.BLUE)

        assertTrue(result.captures.isEmpty())
    }

    @Test
    fun wallsAndEmptyCellsAreSkipped() {
        val result = engine().resolve(Board(), At.TOP_LEFT, card(top = 9, left = 9), CardColor.BLUE)

        assertTrue(result.captures.isEmpty())
        assertEquals(1, result.board.placedCount)
    }

    @Test
    fun allFourNeighboursCanBeCapturedAtOnce() {
        val weak = card(id = 1, top = 1, right = 1, bottom = 1, left = 1)
        val start = board(
            Triple(At.TOP_MID, weak, CardColor.RED),
            Triple(At.MID_LEFT, weak.copy(id = 2), CardColor.RED),
            Triple(At.MID_RIGHT, weak.copy(id = 3), CardColor.RED),
            Triple(At.BOTTOM_MID, weak.copy(id = 4), CardColor.RED),
        )
        val strong = card(id = 5, top = 9, right = 9, bottom = 9, left = 9)
        val result = engine().resolve(start, At.CENTRE, strong, CardColor.BLUE)

        assertEquals(4, result.captures.size)
        assertEquals(
            listOf(At.TOP_MID, At.MID_LEFT, At.MID_RIGHT, At.BOTTOM_MID).sorted(),
            result.capturedPositions.sorted(),
        )
    }

    // ---- 7-9: Fallen Ace -------------------------------------------------

    @Test
    fun fallenAceTurnsTenIntoZeroAndLosesToOne() {
        val ace = card(id = 1, top = ACE_POWER, right = ACE_POWER, bottom = ACE_POWER, left = 1)
        val start = board(Triple(At.CENTRE, ace, CardColor.RED))
        // A 1 on the left edge attacks the ace's left side... which is also 1. Use top instead:
        // place at BOTTOM_MID attacking centre's bottom (an ace -> 0) with a 1.
        val result = engine(GameRules(fallenAce = true))
            .resolve(start, At.BOTTOM_MID, card(id = 2, top = 1), CardColor.BLUE)

        assertEquals(listOf(At.CENTRE), result.capturedPositions)
    }

    @Test
    fun fallenAcePlusAscensionReadsOneNotZero() {
        val ace = card(id = 1, bottom = ACE_POWER).copy(type = CardType.BEAST)
        val rules = GameRules(fallenAce = true, typeRule = TypeRule.ASCENSION)
        val tally = AscensionTally(mapOf(CardType.BEAST to 1))

        assertEquals(
            1,
            effectivePower(ace, Side.BOTTOM, rules, tally = tally),
            "Fallen Ace zeroes the 10 first, then Ascension adds 1",
        )
    }

    @Test
    fun fallenAceDisablesSameWallOnThatSide() {
        // Centre-top wall is not reachable; use TOP_MID: its TOP side faces a wall.
        val start = board(Triple(At.TOP_LEFT, card(id = 1, right = 4), CardColor.RED))
        val placed = card(id = 2, top = ACE_POWER, left = 4)
        val rules = GameRules(sameWall = true)

        val without = engine(rules).resolve(start, At.TOP_MID, placed, CardColor.BLUE)
        assertEquals(
            CaptureKind.SAME_WALL,
            without.kindAt(At.TOP_LEFT),
            "the ace faces the top wall, so Same Wall should fire",
        )

        val with = engine(rules.copy(fallenAce = true))
            .resolve(start, At.TOP_MID, placed, CardColor.BLUE)
        assertFalse(
            with.captures.any { it.kind == CaptureKind.SAME_WALL },
            "Fallen Ace turns the ace into 0, so the wall no longer counts",
        )
    }

    // ---- 10-16: Same, Plus, Same Wall ------------------------------------

    @Test
    fun sameFiresOnTwoMatchesAndNotOnOne() {
        val rules = GameRules(same = true)
        val placed = card(id = 9, top = 4, left = 6)

        val one = board(Triple(At.TOP_MID, card(id = 1, bottom = 4), CardColor.RED))
        assertFalse(
            engine(rules).resolve(one, At.CENTRE, placed, CardColor.BLUE)
                .captures.any { it.kind == CaptureKind.SAME },
            "one match is not a Same",
        )

        val two = one.place(At.MID_LEFT, card(id = 2, right = 6), CardColor.RED)
        val result = engine(rules).resolve(two, At.CENTRE, placed, CardColor.BLUE)
        assertEquals(CaptureKind.SAME, result.kindAt(At.TOP_MID))
        assertEquals(CaptureKind.SAME, result.kindAt(At.MID_LEFT))
    }

    @Test
    fun sameCapturesOnlyTheEnemyOfAMixedPair() {
        val start = board(
            Triple(At.TOP_MID, card(id = 1, bottom = 4), CardColor.RED),
            Triple(At.MID_LEFT, card(id = 2, right = 6), CardColor.BLUE),
        )
        val result = engine(GameRules(same = true))
            .resolve(start, At.CENTRE, card(id = 9, top = 4, left = 6), CardColor.BLUE)

        assertEquals(listOf(At.TOP_MID), result.capturedPositions)
        assertEquals(CaptureKind.SAME, result.kindAt(At.TOP_MID))
    }

    @Test
    fun plusFiresOnEqualSumsWithUnequalPowers() {
        // 3 + 7 == 10 and 5 + 5 == 10.
        val start = board(
            Triple(At.TOP_MID, card(id = 1, bottom = 7), CardColor.RED),
            Triple(At.MID_LEFT, card(id = 2, right = 5), CardColor.RED),
        )
        val result = engine(GameRules(plus = true))
            .resolve(start, At.CENTRE, card(id = 9, top = 3, left = 5), CardColor.BLUE)

        assertEquals(CaptureKind.PLUS, result.kindAt(At.TOP_MID))
        assertEquals(CaptureKind.PLUS, result.kindAt(At.MID_LEFT))
    }

    @Test
    fun sameWallFiresWithOneNeighbourByDefault() {
        val start = board(Triple(At.TOP_LEFT, card(id = 1, right = 4), CardColor.RED))
        val placed = card(id = 2, top = ACE_POWER, left = 4)

        val fixed = engine(GameRules(sameWall = true))
            .resolve(start, At.TOP_MID, placed, CardColor.BLUE)
        assertEquals(
            CaptureKind.SAME_WALL,
            fixed.kindAt(At.TOP_LEFT),
            "a wall showing an ace is the second match",
        )
    }

    @Test
    fun sameWallIsInoperativeWithOneNeighbourWhenFaithful() {
        val start = board(Triple(At.TOP_LEFT, card(id = 1, right = 4), CardColor.RED))
        val placed = card(id = 2, top = ACE_POWER, left = 4)

        val faithful = engine(GameRules(sameWall = true), RulesEngineOptions.FAITHFUL)
            .resolve(start, At.TOP_MID, placed, CardColor.BLUE)
        assertFalse(
            faithful.captures.any { it.kind == CaptureKind.SAME_WALL },
            "the AS3 `same.length > 1` gate blocks it — game-rules.md 15.2",
        )
    }

    @Test
    fun plusTakesPrecedenceOverBasic() {
        // TOP_MID loses on raw power (7 < 8) and also completes a Plus.
        val start = board(
            Triple(At.TOP_MID, card(id = 1, bottom = 7), CardColor.RED),
            Triple(At.MID_LEFT, card(id = 2, right = 5), CardColor.RED),
        )
        val result = engine(GameRules(plus = true))
            .resolve(start, At.CENTRE, card(id = 9, top = 8, left = 10), CardColor.BLUE)

        assertEquals(
            CaptureKind.PLUS,
            result.kindAt(At.TOP_MID),
            "PLUS sorts before ZZ in the AS3 precedence, so Plus is attributed",
        )
    }

    @Test
    fun sameArithmeticFollowsTheConfiguredPowerBasis() {
        // Centre tile is a fire tile; the placed card is fire, so +1 to every side.
        val elements = List(Board.SIZE) { if (it == At.CENTRE) CardType.FIRE else null }
        val start = Board(elements = elements)
            .place(At.TOP_MID, card(id = 1, bottom = 5), CardColor.RED)
            .place(At.MID_LEFT, card(id = 2, right = 7), CardColor.RED)
        val placed = card(id = 9, top = 4, left = 6).copy(type = CardType.FIRE)
        val rules = GameRules(same = true, typeRule = TypeRule.ELEMENTAL)

        val effective = engine(rules).resolve(start, At.CENTRE, placed, CardColor.BLUE)
        assertEquals(
            CaptureKind.SAME,
            effective.kindAt(At.TOP_MID),
            "with EFFECTIVE, 4+1 == 5 and 6+1 == 7, so both match",
        )

        val printed = engine(rules, RulesEngineOptions.FAITHFUL)
            .resolve(start, At.CENTRE, placed, CardColor.BLUE)
        assertFalse(
            printed.captures.any { it.kind == CaptureKind.SAME },
            "with PRINTED, 4 != 5 and 6 != 7 — the AS3 behaviour, game-rules.md 15.4",
        )
    }

    // ---- 17-21: combo ----------------------------------------------------

    @Test
    fun comboPropagatesFromASameCapture() {
        // MID_LEFT is captured by Same; its own bottom (9) then beats BOTTOM_LEFT's top (2).
        val start = board(
            Triple(At.TOP_MID, card(id = 1, bottom = 4), CardColor.RED),
            Triple(At.MID_LEFT, card(id = 2, right = 6, bottom = 9), CardColor.RED),
            Triple(At.BOTTOM_LEFT, card(id = 3, top = 2), CardColor.RED),
        )
        val result = engine(GameRules(same = true))
            .resolve(start, At.CENTRE, card(id = 9, top = 4, left = 6), CardColor.BLUE)

        assertEquals(CaptureKind.SAME, result.kindAt(At.MID_LEFT))
        assertEquals(CaptureKind.COMBO, result.kindAt(At.BOTTOM_LEFT))
        assertEquals(1, result.captures.first { it.position == At.BOTTOM_LEFT }.wave)
        assertEquals(CardColor.BLUE, result.board[At.BOTTOM_LEFT]?.owner)
    }

    @Test
    fun comboNeverRevisitsATile() {
        // Two mutually-beating cards would loop without a visited set.
        val start = board(
            Triple(At.TOP_MID, card(id = 1, bottom = 4), CardColor.RED),
            Triple(At.MID_LEFT, card(id = 2, right = 6, bottom = 9), CardColor.RED),
            Triple(At.BOTTOM_LEFT, card(id = 3, top = 2, right = 9), CardColor.RED),
            Triple(At.BOTTOM_MID, card(id = 4, left = 1, top = 1), CardColor.RED),
        )
        val result = engine(GameRules(same = true))
            .resolve(start, At.CENTRE, card(id = 9, top = 4, left = 6), CardColor.BLUE)

        assertEquals(
            result.capturedPositions.size,
            result.capturedPositions.distinct().size,
            "each position must be captured at most once",
        )
    }

    @Test
    fun comboRespectsReverse() {
        val start = board(
            Triple(At.TOP_MID, card(id = 1, bottom = 4), CardColor.RED),
            Triple(At.MID_LEFT, card(id = 2, right = 6, bottom = 1), CardColor.RED),
            Triple(At.BOTTOM_LEFT, card(id = 3, top = 9), CardColor.RED),
        )
        val result = engine(GameRules(same = true, reverse = true))
            .resolve(start, At.CENTRE, card(id = 9, top = 4, left = 6), CardColor.BLUE)

        assertEquals(
            CaptureKind.COMBO,
            result.kindAt(At.BOTTOM_LEFT),
            "under Reverse a 1 beats a 9 in the combo step too",
        )
    }

    @Test
    fun comboDoesNotCaptureOwnCards() {
        val start = board(
            Triple(At.TOP_MID, card(id = 1, bottom = 4), CardColor.RED),
            Triple(At.MID_LEFT, card(id = 2, right = 6, bottom = 9), CardColor.RED),
            Triple(At.BOTTOM_LEFT, card(id = 3, top = 2), CardColor.BLUE),
        )
        val result = engine(GameRules(same = true))
            .resolve(start, At.CENTRE, card(id = 9, top = 4, left = 6), CardColor.BLUE)

        assertFalse(At.BOTTOM_LEFT in result.capturedPositions)
    }

    @Test
    fun comboFiresWithNoComboFlagSet() {
        assertTrue(
            GameRules().comboEnabled,
            "RULE_COMBO is a dead constant; combo is unconditional — game-rules.md 10",
        )
    }

    @Test
    fun basicCapturesNeverTriggerCombo() {
        // No special rule active, so the chain must stop at the direct capture.
        val start = board(
            Triple(At.MID_LEFT, card(id = 2, right = 1, bottom = 9), CardColor.RED),
            Triple(At.BOTTOM_LEFT, card(id = 3, top = 2), CardColor.RED),
        )
        val result = engine().resolve(start, At.CENTRE, card(id = 9, left = 9), CardColor.BLUE)

        assertEquals(listOf(At.MID_LEFT), result.capturedPositions)
    }

    // ---- 22-27: type rules -----------------------------------------------

    @Test
    fun elementalGivesPlusOneMinusOneOrNothing() {
        val rules = GameRules(typeRule = TypeRule.ELEMENTAL)
        val fire = card(top = 5).copy(type = CardType.FIRE)

        assertEquals(6, effectivePower(fire, Side.TOP, rules, element = CardType.FIRE))
        assertEquals(4, effectivePower(fire, Side.TOP, rules, element = CardType.ICE))
        assertEquals(5, effectivePower(fire, Side.TOP, rules, element = null))
    }

    @Test
    fun elementalPenalisesUntypedCards() {
        val rules = GameRules(typeRule = TypeRule.ELEMENTAL)
        val plain = card(top = 5).copy(type = null)

        assertEquals(
            4,
            effectivePower(plain, Side.TOP, rules, element = CardType.FIRE),
            "an element-less card is levelled down too — confirmed intended, game-rules.md 15.5",
        )
        assertEquals(5, effectivePower(plain, Side.TOP, rules, element = null))
    }

    @Test
    fun ascensionAppliesToEveryCardOfTheType() {
        val rules = GameRules(typeRule = TypeRule.ASCENSION)
        val tally = AscensionTally.EMPTY
            .record(CardType.BEAST, TypeRule.ASCENSION)
            .record(CardType.BEAST, TypeRule.ASCENSION)

        assertEquals(2, tally[CardType.BEAST])
        assertEquals(
            7,
            effectivePower(beast(), Side.TOP, rules, tally = tally),
        )
        assertEquals(
            5,
            effectivePower(typed(CardType.SCIONS), Side.TOP, rules, tally = tally),
            "other types are unaffected",
        )
    }

    @Test
    fun descensionDecrementsSymmetrically() {
        val rules = GameRules(typeRule = TypeRule.DESCENSION)
        val tally = AscensionTally.EMPTY.record(CardType.GARLEAN, TypeRule.DESCENSION)

        assertEquals(-1, tally[CardType.GARLEAN])
        assertEquals(
            4,
            effectivePower(typed(CardType.GARLEAN), Side.TOP, rules, tally = tally),
        )
    }

    @Test
    fun untypedCardsDoNotMoveTheTally() {
        assertEquals(
            AscensionTally.EMPTY,
            AscensionTally.EMPTY.record(null, TypeRule.ASCENSION),
        )
    }

    @Test
    fun modifiersCannotEscapeZeroToTen() {
        val rules = GameRules(typeRule = TypeRule.ASCENSION)
        val high = AscensionTally(mapOf(CardType.BEAST to 20))
        val low = AscensionTally(mapOf(CardType.BEAST to -20))
        val subject = card(top = 5).copy(type = CardType.BEAST)

        assertEquals(ACE_POWER, effectivePower(subject, Side.TOP, rules, tally = high))
        assertEquals(MIN_EFFECTIVE_POWER, effectivePower(subject, Side.TOP, rules, tally = low))
    }

    @Test
    fun typeRuleIsExclusiveByConstruction() {
        // Not an assertion about behaviour but about the model: one slot, one value.
        val rules = GameRules(typeRule = TypeRule.ASCENSION)
        assertEquals(TypeRule.ASCENSION, rules.typeRule)
        assertEquals(TypeRule.ELEMENTAL, rules.copy(typeRule = TypeRule.ELEMENTAL).typeRule)
    }

    // ---- 28-31: turn and scoring -----------------------------------------

    @Test
    fun ninePlacementsFillTheBoard() {
        assertEquals(Board.SIZE, PLACEMENTS_PER_MATCH)
        val full = (0 until Board.SIZE).fold(Board()) { acc, at ->
            acc.place(at, card(id = at + 1), CardColor.BLUE)
        }
        assertTrue(full.isFull)
        assertTrue(full.emptyPositions().isEmpty())
    }

    @Test
    fun firstPlayerPlacesFiveAndSecondPlacesFour() {
        val order = TurnOrder(CardColor.BLUE)

        assertEquals(HAND_SIZE, order.placementsFor(CardColor.BLUE))
        assertEquals(HAND_SIZE - 1, order.placementsFor(CardColor.RED))
        assertEquals(CardColor.BLUE, order.colorAt(0))
        assertEquals(CardColor.RED, order.colorAt(1))
        assertEquals(CardColor.BLUE, order.colorAt(PLACEMENTS_PER_MATCH - 1))
    }

    @Test
    fun unplayedCardsCountForTheirOwnerAndTheTotalIsAlwaysTen() {
        val order = TurnOrder(CardColor.BLUE)
        val full = (0 until Board.SIZE).fold(Board()) { acc, at ->
            acc.place(at, card(id = at + 1), order.colorAt(at))
        }
        val result = score(full, unplayedCounts(full, order))

        assertEquals(TOTAL_CARDS, result.blue + result.red)
        assertEquals(HAND_SIZE, result.blue, "blue placed 5, all still blue")
        assertEquals(HAND_SIZE, result.red, "red placed 4 and holds 1")
    }

    @Test
    fun fiveFiveIsADraw() {
        val drawn = MatchScore(HAND_SIZE, HAND_SIZE)
        assertTrue(drawn.isDraw)
        assertEquals(null, drawn.winner())
        assertEquals(CardColor.BLUE, MatchScore(6, 4).winner())
        assertEquals(CardColor.RED, MatchScore(4, 6).winner())
    }

    @Test
    fun scoreTracksCapturesTurnByTurn() {
        val start = board(Triple(At.TOP_MID, card(id = 1, bottom = 1), CardColor.RED))
        val order = TurnOrder(CardColor.RED)
        assertEquals(MatchScore(blue = 5, red = 5), score(start, unplayedCounts(start, order)))

        // Blue places at centre and flips TOP_MID: two blue cards on the board, and each
        // side now holds four. 4 + 2 versus 4 + 0.
        val after = engine().resolve(start, At.CENTRE, card(id = 2, top = 9), CardColor.BLUE).board
        val moved = score(after, unplayedCounts(after, order))
        assertEquals(MatchScore(blue = 6, red = 4), moved)
        assertEquals(TOTAL_CARDS, moved.blue + moved.red)
    }

    // ---- board model -----------------------------------------------------

    @Test
    fun neighboursRespectWalls() {
        val empty = Board()

        assertEquals(null, empty.neighbour(At.TOP_LEFT, Side.TOP))
        assertEquals(null, empty.neighbour(At.TOP_LEFT, Side.LEFT))
        assertEquals(At.TOP_MID, empty.neighbour(At.TOP_LEFT, Side.RIGHT))
        assertEquals(At.MID_LEFT, empty.neighbour(At.TOP_LEFT, Side.BOTTOM))
        assertEquals(null, empty.neighbour(At.TOP_RIGHT, Side.RIGHT))
        assertEquals(At.CENTRE, empty.neighbour(At.TOP_MID, Side.BOTTOM))
    }

    @Test
    fun facingSidesPairUp() {
        assertEquals(Side.BOTTOM, Side.TOP.facing())
        assertEquals(Side.LEFT, Side.RIGHT.facing())
        assertEquals(Side.TOP, Side.BOTTOM.facing())
        assertEquals(Side.RIGHT, Side.LEFT.facing())
    }
}
