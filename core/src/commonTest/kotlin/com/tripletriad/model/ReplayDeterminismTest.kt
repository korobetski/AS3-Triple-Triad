package com.tripletriad.model

import com.tripletriad.data.CardCatalog
import com.tripletriad.data.PveMatches
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The engine reaches the same result from the same seed — on every target, and across time.
 *
 * ### Why this exists
 *
 * Determinism used to be an elegant property of the Phase 3 engine. The network design recorded in
 * [09-PHASE-5-NETWORK.md](../../../../../../../docs/migration/09-PHASE-5-NETWORK.md) makes it
 * **load-bearing**: a match is verified by a server *replaying* it, so if the server and a client
 * diverge by one value, every verification fails and no result can be trusted.
 *
 * This file is in `commonTest` rather than `desktopTest` deliberately — the suite runs once on the
 * desktop JVM and again on Android's, so "the same everywhere" is asserted rather than assumed.
 *
 * ### Two kinds of assertion, and they catch different things
 *
 * - **Self-consistency**: run the same computation twice from a fresh generator and compare. It
 *   catches anything that varies *within* a build — a `HashSet` leaking into an outcome, an
 *   iteration order that depends on object identity.
 * - **Golden values**: compare against numbers recorded from a real run. Catches anything that
 *   varies *between* builds — a standard-library generator changing, an iteration order changing, a
 *   rule being edited.
 *
 * Neither alone is enough. Self-consistency would happily pass on two machines that disagree with
 * each other; goldens alone would miss nondeterminism that is stable within a single run.
 *
 * ### What a failure here means
 *
 * Not "update the expected value". A golden breaking means **transcripts written by earlier builds
 * no longer verify** — the stored record of a match now replays to a different answer. That is a
 * data-version bump and a migration question, not a test to edit. See § What the server becomes.
 */
class ReplayDeterminismTest {
    // ---- The foundation ---------------------------------------------------

    /**
     * `kotlin.random.Random(seed)` is the same generator on every target.
     *
     * Everything else in this file rests on this. The standard library specifies a seeded `Random`
     * as XorWow, so the sequence should be identical on Kotlin/JVM, Kotlin/Native and anywhere
     * else — but "should" is what a test is for, and this one is cheap.
     *
     * The four draws below are the four kinds the engine actually consumes: `nextInt(bound)` for
     * the roulette pool and the AI's tie-breaks, `nextInt(from, until)` for the number of roulette
     * draws, `nextBoolean()` for the AI's caution and the coin flip, and `nextDouble()` for the
     * booster pools.
     */
    @Test
    fun theSeededGeneratorProducesTheSameSequenceEverywhere() {
        // A fresh generator per kind, so each golden is the self-contained claim "the first eight
        // draws of this kind from this seed" rather than "…once the three lists above have
        // consumed an unstated amount of the stream".
        fun <T> draws(draw: Random.() -> T): List<T> =
            Random(GOLDEN_SEED).let { random -> List(GOLDEN_DRAWS) { random.draw() } }

        assertEquals(GOLDEN_INTS, draws { nextInt(GOLDEN_BOUND) }, "nextInt")
        assertEquals(GOLDEN_RANGE, draws { nextInt(1, RANGE_UNTIL) }, "nextInt(from, until)")
        assertEquals(GOLDEN_FLIPS, draws { nextBoolean() }, "nextBoolean")
        assertEquals(GOLDEN_DOUBLES, draws { nextDouble() }, "nextDouble")
    }

    /**
     * The two collection operations the engine draws through are stable too.
     *
     * `shuffled` decides which three cards `RULE_THREE_OPEN` reveals and which five `RULE_RANDOM`
     * deals; `random` picks a cell for a turn that timed out. Both are standard-library algorithms
     * over a seeded generator, and both are load-bearing for replay.
     */
    @Test
    fun theCollectionDrawsAreStable() {
        val subject = (1..10).toList()

        assertEquals(GOLDEN_SHUFFLE, subject.shuffled(Random(GOLDEN_SEED)), "shuffled")
        assertEquals(
            GOLDEN_PICKS,
            Random(GOLDEN_SEED).let { random -> List(GOLDEN_DRAWS) { subject.random(random) } },
            "random()",
        )
    }

    // ---- The pipeline -----------------------------------------------------

    /** Assembling twice from the same seed deals the same match, down to the coin flip. */
    @Test
    fun theSameSeedAssemblesTheSameMatch() {
        for (seed in SEEDS) {
            val first = PveMatches.assemble(profile(), opponent, catalog, Random(seed))
            val second = PveMatches.assemble(profile(), opponent, catalog, Random(seed))

            assertEquals(first.rules, second.rules, "rules, seed $seed")
            assertEquals(first.setup.state.hands, second.setup.state.hands, "hands, seed $seed")
            assertEquals(first.setup.state.order, second.setup.state.order, "order, seed $seed")
            assertEquals(first.setup.coinFlip, second.setup.coinFlip, "flip, seed $seed")
            assertEquals(
                first.setup.opponentVisibility,
                second.setup.opponentVisibility,
                "visibility, seed $seed",
            )
        }
    }

    /**
     * The roulette draws the same rules for a seed.
     *
     * Its own test because it is the one place a *rule set* is chosen at random: an opponent that
     * declares `RULE_ROULETTE` plays under one to three rules nobody wrote down, and a replay that
     * drew different ones would evaluate every capture differently.
     */
    @Test
    fun theRouletteDrawsTheSameRules() {
        for (seed in SEEDS) {
            val declared = GameRules(roulette = true)
            val first = Roulette.augment(declared, CardCollection.FF14, Random(seed))
            val second = Roulette.augment(declared, CardCollection.FF14, Random(seed))

            assertEquals(first, second, "seed $seed")
        }
    }

    /** `RULE_THREE_OPEN` reveals the same three cards for a seed. */
    @Test
    fun theOpenRuleRevealsTheSameCards() {
        val hand = (1..HAND_SIZE).map { card(it, "ff14_") }

        for (seed in SEEDS) {
            val first = HandVisibility.forRule(OpenRule.THREE_OPEN, hand, Random(seed))
            val second = HandVisibility.forRule(OpenRule.THREE_OPEN, hand, Random(seed))

            assertEquals(first, second, "seed $seed")
            assertEquals(
                HandVisibility.THREE_OPEN_COUNT,
                first.visibleCardIds.size,
                "seed $seed reveals three",
            )
        }
    }

    // ---- A whole match ----------------------------------------------------

    /**
     * A full match replays move for move.
     *
     * This is the closest thing here to what the server will actually do: take a seed, run the
     * whole thing, and expect the same answer. Both sides are played by [MatchAi] so the match
     * needs no transcript to reproduce — the AI is itself a function of the generator.
     *
     * Repeated over several seeds rather than one, because a single seed can pass by luck: an
     * iteration order that depends on object identity might not change the outcome of one
     * particular deal.
     */
    @Test
    fun aWholeMatchReplaysMoveForMove() {
        for (seed in SEEDS) {
            assertEquals(playOut(seed), playOut(seed), "seed $seed")
        }
    }

    /**
     * The same match, pinned against a recorded run.
     *
     * The fence the other tests cannot be: it fails if the standard library's generator changes, if
     * an iteration order changes, **or if a rule is edited**. All three would invalidate stored
     * transcripts, which is why the third is in scope rather than an annoyance — see this class's
     * note on what a failure means.
     */
    @Test
    fun aWholeMatchMatchesTheRecordedRun() {
        assertEquals(GOLDEN_MATCH, playOut(GOLDEN_SEED))
    }

    /**
     * The generator is actually consulted.
     *
     * The test that keeps the rest of this file honest: every assertion above compares a run with
     * itself, and would all pass just as happily if the seed were ignored and the deal were
     * constant.
     *
     * "Not all the same" rather than "all different", because collisions are expected and real —
     * seeds 1 and 42 deal the *same* match here. The blue hand is a fixed five-card deck, red draws
     * five from a pool of seven, and the AI only consults the generator before placement 5, so the
     * space two seeds can differ in is small. Asserting all six were distinct would be asserting a
     * property the fixture does not have.
     */
    @Test
    fun differentSeedsProduceDifferentMatches() {
        val played = SEEDS.map { playOut(it) }.toSet()

        assertTrue(played.size > 1, "every seed produced the same match: $played")
    }

    // ---- Fixtures ---------------------------------------------------------

    /**
     * Plays a whole match from [seed] and returns what happened, as a comparable record.
     *
     * A string rather than a `MatchState`, so that a failure prints the divergence instead of two
     * object graphs — and so the golden below is readable enough to review.
     */
    private fun playOut(seed: Int): String {
        val random = Random(seed)
        val match = PveMatches.assemble(profile(), opponent, catalog, random)
        val ai = MatchAi()
        var state = match.setup.state
        val moves = mutableListOf<String>()

        while (!state.isFinished) {
            val next = ai.play(state, random)
            check(next.placement > state.placement) { "the AI could not move at $state" }
            val played = checkNotNull(next.lastPlay) { "a placement left no record" }
            moves += "${played.player.name.first()}${played.card.id}@${played.position}"
            state = next
        }

        val score = state.score
        return "${match.rules.activeRuleKeys().sorted()} ${moves.joinToString(",")} " +
            "= ${score.blue}-${score.red}"
    }

    private fun card(id: Int, collection: String) = Card(
        id = id,
        collection = collection,
        nameKey = "STR_TEST_$id",
        name = "Test $id",
        top = (id % 9) + 1,
        right = ((id + 3) % 9) + 1,
        bottom = ((id + 5) % 9) + 1,
        left = ((id + 7) % 9) + 1,
        rarity = 1,
    )

    private val catalog = CardCatalog(
        ff14 = (1..40).map { card(it, "ff14_") },
        ff8 = (1..30).map { card(it, "ff8_") },
    )

    private val opponent = Npc(
        id = 1,
        nameKey = "STR_NPC_Test",
        iconId = "test-npc",
        fetishCards = listOf(11, 12, 13),
        cards = listOf(20, 21, 22, 23),
    )

    private fun profile() = GameSave.new(createdAt = 0L).copy(
        cards = (1..12).toList(),
        decks = listOf(Deck("Starter", listOf(1, 2, 3, 4, 5))),
    )

    private companion object {
        /** Arbitrary, and fixed forever: the goldens below were recorded against it. */
        const val GOLDEN_SEED = 20260806

        /** Enough seeds that one lucky deal cannot carry the file. */
        val SEEDS = listOf(1, 7, 42, 1337, 20260806, -3)

        const val GOLDEN_DRAWS = 8
        const val GOLDEN_BOUND = 1000

        /** `Roulette.augment` draws its count with `nextInt(1, 4)`; this mirrors that shape. */
        const val RANGE_UNTIL = 4

        /*
         * Recorded from a real run on 2026-08-06, not written by hand. Every value below is what
         * `Random(GOLDEN_SEED)` and the engine actually produced; none of them is a round number,
         * which is the point — a golden somebody could have guessed is a golden that proves
         * nothing.
         */

        val GOLDEN_INTS = listOf(44, 807, 800, 101, 725, 306, 25, 393)
        val GOLDEN_RANGE = listOf(3, 3, 1, 2, 1, 3, 2, 3)
        val GOLDEN_FLIPS = listOf(true, false, false, true, false, false, false, false)

        /**
         * Compared for **exact** equality, deliberately.
         *
         * A tolerance would be right for a physical measurement and wrong here: replay needs the
         * same bits, and `BoosterItem.open` multiplies two of these together before rounding — so a
         * difference in the last place really can change which card comes out of a pack.
         */
        val GOLDEN_DOUBLES = listOf(
            0.745018500466747,
            0.3026252677199961,
            0.47845799360402397,
            0.3190222281648454,
            0.4389593827912841,
            0.36873199768764053,
            0.20879996188065342,
            0.22641981383395993,
        )

        val GOLDEN_SHUFFLE = listOf(7, 1, 8, 10, 2, 4, 6, 3, 9, 5)
        val GOLDEN_PICKS = listOf(5, 8, 1, 2, 6, 7, 6, 4)

        /** No special rules, nine placements, and blue loses 7-3. */
        const val GOLDEN_MATCH =
            "[] R22@4,B1@1,R12@3,B2@0,R23@6,B5@7,R11@2,B3@5,R13@8 = 7-3"
    }
}
