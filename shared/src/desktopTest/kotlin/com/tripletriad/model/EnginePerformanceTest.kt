package com.tripletriad.model

import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Task 3.6 of
 * [07-PHASE-3-CORE-LOGIC.md](../../../../../../../docs/migration/07-PHASE-3-CORE-LOGIC.md) — the
 * engine's cost per operation.
 *
 * In `desktopTest` rather than `commonTest` because `commonMain` has no clock: measuring needs
 * [measureNanoTime], which is JVM-only. The engine itself is common code, so what is measured here
 * is the same code Android runs, on a different runtime — a desktop JVM figure is an *upper bound
 * indicator*, not a device measurement. Anything that regresses by an order of magnitude will show
 * up here; a 20% device-specific regression will not, and this file does not claim otherwise.
 *
 * ### Why the budgets are loose
 *
 * The phase plan asks for "card flip < 1 ms" and "full rule application < 10 ms". Those are met by
 * roughly three orders of magnitude, which is the useful finding — the engine is pure allocation
 * and arithmetic over a nine-element list, with no I/O, no reflection and no display objects to
 * walk. It is also why asserting the plan's numbers directly would be a bad test: a threshold three
 * orders of magnitude above the real cost catches nothing, and one set near the real cost fails on
 * a loaded CI machine or a cold JIT rather than on a regression.
 *
 * So each budget below is set about 100x the measured cost. It is a **smoke alarm for accidental
 * quadratic behaviour** — a `resolve` that started rebuilding the board per capture, or an AI that
 * started evaluating the whole game tree — not a benchmark. Real numbers go in
 * [performance-baseline.md](../../../../../../../docs/analysis/performance-baseline.md); this only
 * fails when something has gone badly wrong.
 */
class EnginePerformanceTest {
    private fun card(id: Int, top: Int, right: Int, bottom: Int, left: Int) = Card(
        id = id,
        collection = "test_",
        nameKey = "STR_TEST_$id",
        name = "Test $id",
        top = top,
        right = right,
        bottom = bottom,
        left = left,
        rarity = 1,
        type = MatchState.FF8_ELEMENTS[id % MatchState.FF8_ELEMENTS.size],
    )

    /** Twenty cards spanning the power range, so no measurement rests on one shape of card. */
    private val pool = (1..20).map {
        card(
            it,
            top = it % 10 + 1,
            right = 10 - it % 9,
            bottom = (it * 3) % 10 + 1,
            left = it % 7 + 2,
        )
    }

    /**
     * Every capture rule at once, which is the engine's worst case: Same, Same Wall and Plus all
     * evaluate, and any of them can start a combo cascade.
     */
    private val heaviest = GameRules(
        same = true,
        sameWall = true,
        plus = true,
        typeRule = TypeRule.ELEMENTAL,
        fallenAce = true,
    )

    /** Median of [runs] timings, in microseconds, after a warm-up of the same size. */
    private fun medianMicros(runs: Int = SAMPLES, block: (Int) -> Unit): Double {
        repeat(runs) { block(it) }
        val timings = (0 until runs).map { measureNanoTime { block(it) } }
        return timings.sorted()[runs / 2] / MICROS.toDouble()
    }

    private fun report(what: String, micros: Double, budgetMicros: Double) {
        println("$what: ${(micros * MICROS).toInt()} ns (budget ${budgetMicros.toInt()} us)")
        assertTrue(
            micros < budgetMicros,
            "$what took $micros us, which is past the ${budgetMicros.toInt()} us budget — " +
                "something has changed complexity, not constants",
        )
    }

    /** One placement onto a nearly-full board with every rule live. */
    @Test
    fun resolvingOnePlacementIsWellInsideItsBudget() {
        val engine = RulesEngine(heaviest)
        val boards = (0 until SAMPLES).map { seed -> crowdedBoard(Random(seed)) }

        val micros = medianMicros { i ->
            val (board, free) = boards[i]
            engine.resolve(board, free, pool[i % pool.size], CardColor.BLUE)
        }

        report("resolve (8 cards placed, all rules)", micros, RESOLVE_BUDGET_MICROS)
    }

    /** A whole match, nine placements, built as a chain of immutable states. */
    @Test
    fun playingAWholeMatchIsWellInsideItsBudget() {
        val micros = medianMicros(MATCH_SAMPLES) { seed -> playOut(Random(seed)) }

        report("full 9-placement match", micros, MATCH_BUDGET_MICROS)
    }

    /**
     * One AI turn from an empty board: five cards times nine cells, so 45 full resolutions plus 45
     * cover calculations. The most expensive single decision in the game.
     */
    @Test
    fun oneAiTurnIsWellInsideItsBudget() {
        val ai = MatchAi()
        val start = MatchState.start(
            blueHand = pool.take(HAND_SIZE),
            redHand = pool.drop(HAND_SIZE).take(HAND_SIZE),
            rules = heaviest,
            elements = MatchState.randomElements(Random(1)),
        )

        val micros = medianMicros { seed -> ai.choose(start, Random(seed)) }

        report("AI turn (45 candidates, all rules)", micros, AI_TURN_BUDGET_MICROS)
    }

    /**
     * The AI is asked for a move nine times a match, and the first turn is the dearest, so a whole
     * AI-versus-AI match bounds what a real opponent costs across a match.
     */
    @Test
    fun anEntireAiVersusAiMatchIsWellInsideItsBudget() {
        val ai = MatchAi()

        val micros = medianMicros(MATCH_SAMPLES) { seed ->
            val random = Random(seed)
            var state = MatchState.start(
                blueHand = pool.take(HAND_SIZE),
                redHand = pool.drop(HAND_SIZE).take(HAND_SIZE),
                rules = heaviest,
                elements = MatchState.randomElements(random),
            )
            while (!state.isFinished) state = ai.play(state, random)
        }

        report("AI vs AI, whole match", micros, AI_MATCH_BUDGET_MICROS)
    }

    /** A board with eight cards on it and the position of the one free cell. */
    private fun crowdedBoard(random: Random): Pair<Board, Int> {
        val free = random.nextInt(Board.SIZE)
        var board = Board(elements = MatchState.randomElements(random))
        var next = 0
        for (position in 0 until Board.SIZE) {
            if (position == free) continue
            val owner = if (next % 2 == 0) CardColor.BLUE else CardColor.RED
            board = board.place(position, pool[next % pool.size], owner)
            next++
        }
        return board to free
    }

    private fun playOut(random: Random) {
        var state = MatchState.start(
            blueHand = pool.take(HAND_SIZE),
            redHand = pool.drop(HAND_SIZE).take(HAND_SIZE),
            rules = heaviest,
            elements = MatchState.randomElements(random),
        )
        while (!state.isFinished) {
            state = state.play(state.currentHand.first(), state.playablePositions().first())
        }
    }

    private companion object {
        const val MICROS = 1_000
        const val SAMPLES = 201
        const val MATCH_SAMPLES = 51

        /**
         * All four are ~100x the cost measured on 2026-08-02: 4.8 us to resolve a placement, 62 us
         * for a whole match, 60 us for an AI turn and 751 us for an AI-versus-AI match. So a
         * machine a hundred times slower than this one still passes, and a change of complexity
         * does not.
         *
         * For the record against the plan's targets: it asks for a card flip under 1 ms and a full
         * rule application under 10 ms, and one placement with every rule live costs 4.8 us — met
         * by 200x and 2,000x respectively.
         */
        const val RESOLVE_BUDGET_MICROS = 500.0
        const val MATCH_BUDGET_MICROS = 6_000.0
        const val AI_TURN_BUDGET_MICROS = 6_000.0
        const val AI_MATCH_BUDGET_MICROS = 75_000.0
    }
}
