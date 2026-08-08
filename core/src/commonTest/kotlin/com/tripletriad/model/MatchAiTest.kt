package com.tripletriad.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [MatchAi] — `PVEMatchScreen.AI`, ported as a pure function.
 *
 * The AI is one move deep and randomised, so most of what is worth asserting is *structural*: that
 * it never plays illegally, that it prefers the placement capturing the most, and that its
 * defensive score means what the source says it means. A test that pinned the exact move for a seed
 * would break on any change to the random draw order and prove nothing about the strategy.
 */
class MatchAiTest {
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

    private object At {
        const val TOP_LEFT = 0
        const val TOP_MID = 1
        const val CENTRE = 4
    }

    private val seeds = 0 until 40

    /**
     * A match with red to move, one enemy card already on the board, and a hand red can act on.
     *
     * Hands are stubbed onto the state directly rather than dealt, so a test can say exactly what
     * red is holding.
     */
    private fun state(
        redHand: List<Card>,
        board: Board = Board(),
        rules: GameRules = GameRules(),
        placement: Int = 0,
    ) = MatchState(
        rules = rules,
        board = board,
        hands = mapOf(
            CardColor.BLUE to List(HAND_SIZE) { card(id = 100 + it) },
            CardColor.RED to redHand,
        ),
        order = TurnOrder(CardColor.RED),
        placement = placement,
    )

    private fun ai(options: MatchAiOptions = MatchAiOptions()) = MatchAi(options)

    // ---- cover ------------------------------------------------------------

    /** An empty board leaves four open flanks on the centre, so cover is the card's own powers. */
    @Test
    fun coverOnAnOpenCentreIsTheSumOfTheCardsPowers() {
        val subject = card(top = 1, right = 2, bottom = 3, left = 4)

        assertEquals(1 + 2 + 3 + 4, ai().cover(state(listOf(subject)), subject, At.CENTRE))
    }

    /** A corner faces two walls, and a wall contributes the maximum: nothing attacks from one. */
    @Test
    fun wallsContributeTheMaximum() {
        val subject = card(top = 1, right = 1, bottom = 1, left = 1)

        assertEquals(
            ACE_POWER + ACE_POWER + 1 + 1,
            ai().cover(state(listOf(subject)), subject, At.TOP_LEFT),
            "top and left are walls; right and bottom are open",
        )
    }

    /** An occupied neighbour is no threat either: a card on the board never initiates a capture. */
    @Test
    fun anOccupiedNeighbourContributesTheMaximum() {
        val subject = card(top = 1, right = 1, bottom = 1, left = 1)
        val occupied = Board().place(At.TOP_MID, card(id = 9), CardColor.BLUE)

        assertEquals(
            ACE_POWER + 1 + 1 + 1,
            ai().cover(state(listOf(subject), board = occupied), subject, At.CENTRE),
        )
    }

    /** `(RULES.REVERSE) ? (10 - tile.<side>Pow) : tile.<side>Pow` — `PVEMatchScreen.as:203`. */
    @Test
    fun reverseInvertsWhatCountsAsSafe() {
        val strong = card(top = 9, right = 9, bottom = 9, left = 9)
        val reversed = state(listOf(strong), rules = GameRules(reverse = true))

        assertEquals(4, ai().cover(reversed, strong, At.CENTRE), "a 9 is exposed under Reverse")
        assertEquals(
            MAX_COVER,
            ai().cover(
                state(listOf(strong)),
                strong.copy(top = 10, right = 10, bottom = 10, left = 10),
                At.CENTRE,
            ),
        )
    }

    /** Cover reads the *effective* powers, which is why the AS3 computes it after `applyRules`. */
    @Test
    fun coverUsesEffectivePowersNotPrinted() {
        val ace = card(top = 10, right = 10, bottom = 10, left = 10)
        val fallen = state(listOf(ace), rules = GameRules(fallenAce = true))

        assertEquals(0, ai().cover(fallen, ace, At.CENTRE), "Fallen Ace makes every edge a 0")
    }

    @Test
    fun coverNeverLeavesItsRange() {
        for (seed in seeds) {
            val random = Random(seed)
            val subject = card(
                top = random.nextInt(1, ACE_POWER + 1),
                right = random.nextInt(1, ACE_POWER + 1),
                bottom = random.nextInt(1, ACE_POWER + 1),
                left = random.nextInt(1, ACE_POWER + 1),
            )
            for (position in 0 until Board.SIZE) {
                val value = ai().cover(state(listOf(subject)), subject, position)
                assertTrue(
                    value in MIN_EFFECTIVE_POWER..MAX_COVER,
                    "seed $seed at $position: $value",
                )
            }
        }
    }

    // ---- evaluate ---------------------------------------------------------

    /**
     * Evaluation leaves the state alone. The original cannot: it assigns `tile.card`, lets
     * `applyRules` overwrite the tile's powers and `card.modifier`, then patches the damage back
     * out by hand (`:212-213`).
     */
    @Test
    fun evaluatingAMoveDoesNotChangeTheState() {
        val before = state(listOf(card(top = 9)), rules = GameRules(typeRule = TypeRule.ELEMENTAL))
        val snapshot = before.copy()

        ai().candidates(before, Random(1))

        assertEquals(snapshot, before, "no reset patch should be needed")
    }

    @Test
    fun capturesCountWhatThePlacementWouldFlip() {
        val board = Board().place(At.TOP_MID, card(id = 9, bottom = 3), CardColor.BLUE)
        val attacker = card(id = 1, top = 9)

        val move = ai().evaluate(state(listOf(attacker), board = board), attacker, At.CENTRE)

        assertEquals(1, move.captures)
        assertEquals(At.CENTRE, move.position)
        assertEquals(attacker, move.card)
    }

    @Test
    fun aPlacementThatFlipsNothingScoresZeroCaptures() {
        val board = Board().place(At.TOP_MID, card(id = 9, bottom = 9), CardColor.BLUE)
        val weak = card(id = 1, top = 2)

        assertEquals(0, ai().evaluate(state(listOf(weak), board = board), weak, At.CENTRE).captures)
    }

    /** Combos count too: `checking = true` walks the waves and de-duplicates by tile. */
    @Test
    fun combosCountTowardsTheScore() {
        // Blue at 1 and 3 both show 5 facing centre; a 5/5 placement makes a Same and the
        // captured cards then beat their own neighbours at 0 and 2.
        val board = Board()
            .place(1, card(id = 11, bottom = 5, left = 9, right = 9), CardColor.BLUE)
            .place(3, card(id = 12, right = 5, top = 9), CardColor.BLUE)
            .place(0, card(id = 13, right = 1, bottom = 1), CardColor.BLUE)
        val placed = card(id = 1, top = 5, left = 5)

        val move = ai().evaluate(
            state(listOf(placed), board = board, rules = GameRules(same = true)),
            placed,
            At.CENTRE,
        )

        assertTrue(move.captures >= 2, "the Same pair alone is two cards, was ${move.captures}")
    }

    // ---- candidates -------------------------------------------------------

    @Test
    fun everyCardIsPairedWithEveryFreeCell() {
        val hand = List(3) { card(id = it + 1) }

        val candidates = ai().candidates(state(hand), Random(1))

        assertEquals(3 * Board.SIZE, candidates.size)
    }

    @Test
    fun occupiedCellsAreNotCandidates() {
        val board = Board().place(At.CENTRE, card(id = 9), CardColor.BLUE)

        val candidates = ai().candidates(state(listOf(card()), board = board), Random(1))

        assertEquals(Board.SIZE - 1, candidates.size)
        assertTrue(candidates.none { it.position == At.CENTRE })
    }

    @Test
    fun candidatesAreRankedByCapturesThenCover() {
        val board = Board().place(At.TOP_MID, card(id = 9, bottom = 3), CardColor.BLUE)
        val hand = listOf(card(id = 1, top = 9), card(id = 2, top = 1))

        val candidates = ai().candidates(state(hand, board = board), Random(1))

        val captures = candidates.map { it.captures }
        assertEquals(captures.sortedDescending(), captures)
        for (window in candidates.windowed(2)) {
            val (first, second) = window
            if (first.captures == second.captures) {
                assertTrue(first.cover >= second.cover, "$first should outrank $second")
            }
        }
    }

    /** Order narrows the AI's own candidate list before scoring (`:185-186`). */
    @Test
    fun underOrderOnlyTheFirstCardIsConsidered() {
        val hand = List(HAND_SIZE) { card(id = it + 1) }

        val candidates = ai().candidates(
            state(hand, rules = GameRules(order = OrderRule.ORDER)),
            Random(1),
        )

        assertEquals(setOf(1), candidates.map { it.card.id }.toSet())
        assertEquals(Board.SIZE, candidates.size)
    }

    @Test
    fun underChaosOnlyOneRandomCardIsConsidered() {
        val hand = List(HAND_SIZE) { card(id = it + 1) }
        val rules = GameRules(order = OrderRule.CHAOS)

        val chosen = seeds.map { seed ->
            ai().candidates(state(hand, rules = rules), Random(seed))
                .map { it.card.id }
                .distinct()
                .single()
        }

        assertTrue(chosen.distinct().size > 1, "Chaos must not always pick the same card")
    }

    @Test
    fun aFinishedMatchOffersNoCandidates() {
        val over = state(emptyList(), placement = PLACEMENTS_PER_MATCH)

        assertTrue(ai().candidates(over, Random(1)).isEmpty())
        assertNull(ai().choose(over, Random(1)))
    }

    @Test
    fun anEmptyHandOffersNoCandidates() {
        assertTrue(ai().candidates(state(emptyList()), Random(1)).isEmpty())
    }

    // ---- choose -----------------------------------------------------------

    @Test
    fun theCapturingMoveIsPreferredToTheSaferOne() {
        // Blue's 3 at top-mid loses to a 9 played at centre. Every other cell captures nothing,
        // and several of them are safer, so only a capture-first AI plays the centre.
        val board = Board().place(At.TOP_MID, card(id = 9, bottom = 3), CardColor.BLUE)
        val attacker = card(id = 1, top = 9, right = 1, bottom = 1, left = 1)

        for (seed in seeds) {
            val move =
                assertNotNull(ai().choose(state(listOf(attacker), board = board), Random(seed)))

            assertEquals(At.CENTRE, move.position, "seed $seed")
            assertEquals(1, move.captures, "seed $seed")
        }
    }

    /**
     * The tutor refuses the capture it can see — `MatchAiOptions.TUTOR`.
     *
     * The same board as [theCapturingMoveIsPreferredToTheSaferOne], so the two read as a pair: the
     * capture is there, the default AI takes it, and the tutorial's takes the bottom of the same
     * ranking instead. `// c'est le tuto, le pnj jour toujours la pire solution`.
     */
    @Test
    fun theTutorPlaysTheWorstMoveItCanFind() {
        val board = Board().place(At.TOP_MID, card(id = 9, bottom = 3), CardColor.BLUE)
        val attacker = card(id = 1, top = 9, right = 1, bottom = 1, left = 1)
        val tutor = ai(MatchAiOptions.TUTOR)

        for (seed in seeds) {
            val situation = state(listOf(attacker), board = board)
            val move = assertNotNull(tutor.choose(situation, Random(seed)))

            assertEquals(0, move.captures, "seed $seed took a capture it was meant to decline")
            assertEquals(
                tutor.candidates(situation, Random(seed)).last(),
                move,
                "seed $seed did not play the bottom of the ranking",
            )
        }
    }

    @Test
    fun theMoveCapturingTheMostIsChosen() {
        // Three blue cards surround the centre, each losing to a 9.
        val board = Board()
            .place(1, card(id = 11, bottom = 3), CardColor.BLUE)
            .place(3, card(id = 12, right = 3), CardColor.BLUE)
            .place(5, card(id = 13, left = 3), CardColor.BLUE)
        val attacker = card(id = 1, top = 9, right = 9, bottom = 9, left = 9)

        for (seed in seeds) {
            val move =
                assertNotNull(ai().choose(state(listOf(attacker), board = board), Random(seed)))

            assertEquals(3, move.captures, "seed $seed played ${move.position}")
            assertEquals(At.CENTRE, move.position, "seed $seed")
        }
    }

    /**
     * The default settles a captures tie by cover; the AS3 sorts by cover and then ignores it.
     *
     * Two blue cards sit in opposite corners, each showing a 1 on exactly one side and a 9 on the
     * others, so each is capturable from exactly one cell: the card at 0 from top-mid, the one at
     * 8 from bottom-mid. Both placements capture one card. The attacker's weak edge is its bottom,
     * which top-mid leaves open onto the centre and bottom-mid points at a wall — so bottom-mid
     * covers better by seven.
     */
    @Test
    fun coverBreaksACapturesTieByDefaultAndNotUnderTheFaithfulOption() {
        val board = Board()
            .place(0, card(id = 11, top = 9, right = 1, bottom = 9, left = 9), CardColor.BLUE)
            .place(8, card(id = 12, top = 9, right = 9, bottom = 9, left = 1), CardColor.BLUE)
        val attacker = card(id = 1, top = 9, right = 9, bottom = 2, left = 9)
        val situation = state(listOf(attacker), board = board)
        val topMid = 1
        val bottomMid = 7

        val capturing = ai().candidates(situation, Random(1)).filter { it.captures > 0 }
        assertEquals(
            listOf(bottomMid, topMid),
            capturing.map { it.position },
            "the fixture must offer exactly two capturing cells, best cover first",
        )
        assertTrue(capturing.all { it.captures == 1 }, "and they must tie on captures")

        val fixed = seeds.map {
            assertNotNull(
                ai().choose(situation, Random(it)),
            ).position
        }.distinct()
        val faithful = seeds
            .map {
                assertNotNull(
                    ai(MatchAiOptions.FAITHFUL).choose(situation, Random(it)),
                ).position
            }
            .distinct()

        assertEquals(listOf(bottomMid), fixed, "the best cover should win every time")
        assertEquals(
            setOf(topMid, bottomMid),
            faithful.toSet(),
            "the faithful option ignores cover and picks either",
        )
    }

    @Test
    fun aTieOnBothScoresIsBrokenAtRandom() {
        // Two identical cards: every placement scores the same, so nothing but the draw decides.
        val hand = listOf(card(id = 1), card(id = 2))

        val chosen = seeds
            .map { assertNotNull(ai().choose(state(hand), Random(it))).position }
            .distinct()

        assertTrue(chosen.size > 1, "identical candidates must not always yield the same cell")
    }

    /** `if (this.turn > 5)` — the sixth placement onward always takes the safest square. */
    @Test
    fun fromTheSixthPlacementNothingCapturingMeansTheSafestSquare() {
        val hand = listOf(card(top = 9, right = 1, bottom = 1, left = 1))

        for (seed in seeds) {
            val situation = state(hand, placement = 5)
            val ranked = ai().candidates(situation, Random(seed))
            val move = assertNotNull(ai().choose(situation, Random(seed)))

            assertEquals(0, move.captures, "seed $seed")
            assertEquals(ranked.first().cover, move.cover, "seed $seed took a worse square")
        }
    }

    /**
     * Before then it tosses a coin between the safest square and the *most exposed* one
     * (`:236-242`). Reproduced deliberately — it is what stops the opening from being
     * deterministic.
     */
    @Test
    fun beforeThenItSometimesWalksIntoTheWorstSquare() {
        val hand = listOf(card(top = 9, right = 1, bottom = 1, left = 1))
        val situation = state(hand, placement = 0)
        val worst = ai().candidates(situation, Random(1)).last().cover
        val best = ai().candidates(situation, Random(1)).first().cover

        val covers = seeds.map { assertNotNull(ai().choose(situation, Random(it))).cover }.toSet()

        assertTrue(best > worst, "the fixture needs squares of differing safety")
        assertEquals(setOf(best, worst), covers, "only the two extremes are ever played")
    }

    // ---- play -------------------------------------------------------------

    @Test
    fun playAdvancesTheMatchByOnePlacement() {
        val before = state(List(HAND_SIZE) { card(id = it + 1) })

        val after = ai().play(before, Random(1))

        assertEquals(1, after.placement)
        assertEquals(HAND_SIZE - 1, after.hands[CardColor.RED]?.size)
        assertEquals(CardColor.RED, after.lastPlay?.player)
    }

    @Test
    fun playingAFinishedMatchChangesNothing() {
        val over = state(emptyList(), placement = PLACEMENTS_PER_MATCH)

        assertEquals(over, ai().play(over, Random(1)))
    }

    /**
     * The end-to-end assertion: two AIs play a whole match and it is legal at every step.
     *
     * Swept over rule sets the roulette can actually produce, so combos, Reverse, the type rules
     * and Order all get exercised without the test naming them.
     */
    @Test
    fun twoAisPlayAnyRuleSetToACompleteMatch() {
        val pool = (1..20).map { card(id = it, top = it % 10 + 1, right = 10 - it % 9) }

        for (seed in seeds) {
            val random = Random(seed)
            val rules = Roulette.augment(GameRules(), CardCollection.FF14, random)
            var current = MatchPreparation.prepare(
                blue = HandSource(pool.shuffled(random).take(HAND_SIZE), pool),
                redHand = pool.shuffled(random).take(HAND_SIZE),
                rules = rules,
                random = random,
            ).state

            var moves = 0
            while (!current.isFinished) {
                val next = ai().play(current, random)
                assertTrue(next.placement == current.placement + 1, "seed $seed stalled at $moves")
                current = next
                moves++
            }

            assertEquals(PLACEMENTS_PER_MATCH, moves, "seed $seed")
            assertTrue(current.board.isFull, "seed $seed")
            assertNotNull(current.outcome(), "seed $seed")
            assertEquals(TOTAL_CARDS, current.score.blue + current.score.red, "seed $seed")
        }
    }
}
