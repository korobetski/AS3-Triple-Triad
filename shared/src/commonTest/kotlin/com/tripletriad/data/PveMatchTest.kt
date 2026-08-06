package com.tripletriad.data

import com.tripletriad.model.Card
import com.tripletriad.model.CardCollection
import com.tripletriad.model.CardColor
import com.tripletriad.model.Deck
import com.tripletriad.model.GameSave
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.MatchIntroStep
import com.tripletriad.model.Npc
import com.tripletriad.model.OpenRule
import com.tripletriad.model.OrderRule
import com.tripletriad.model.Roulette
import com.tripletriad.model.TypeRule
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [PveMatches] — the join between a profile and an opponent that the AS3 makes half through a
 * screen property and half through the global `Game.PROFILE_DATAS`.
 */
class PveMatchTest {
    private fun card(id: Int, collection: String) = Card(
        id = id,
        collection = collection,
        nameKey = "STR_TEST_$id",
        name = "Test $id",
        top = 5,
        right = 5,
        bottom = 5,
        left = 5,
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

    private fun profile(
        mode: CardCollection = CardCollection.FF14,
        cards: List<Int> = (1..12).toList(),
        decks: List<Deck> = listOf(Deck("Starter", listOf(1, 2, 3, 4, 5))),
    ) = GameSave.new(createdAt = 0L, mode = mode).copy(cards = cards, decks = decks)

    private val seeds = 0 until 40

    @Test
    fun bothSidesGetFiveCardsFromTheProfilesCollection() {
        val match = PveMatches.assemble(profile(), opponent, catalog, Random(1))
        val hands = match.setup.state.hands

        assertEquals(HAND_SIZE, hands[CardColor.BLUE]?.size)
        assertEquals(HAND_SIZE, hands[CardColor.RED]?.size)
        assertTrue(
            hands.values.flatten().all { it.collection == "ff14_" },
            "an ff14_ profile must only ever see ff14_ cards",
        )
    }

    @Test
    fun anFf8ProfileGetsFf8Cards() {
        val match = PveMatches.assemble(profile(CardCollection.FF8), opponent, catalog, Random(1))

        assertTrue(match.setup.state.hands.values.flatten().all { it.collection == "ff8_" })
    }

    // ---- Choosing a deck ---------------------------------------------------

    /** An explicitly chosen deck beats the "first complete one" default. */
    @Test
    fun theDeckPassedInIsTheDeckDealt() {
        val decks = listOf(
            Deck("First", listOf(1, 2, 3, 4, 5)),
            Deck("Second", listOf(6, 7, 8, 9, 10)),
        )
        val save = profile(decks = decks)
        val random = Random(1)
        val plan = MatchPlan(PveMatches.rulesFor(opponent, save.mode, random), decks[1].cards)

        val match = PveMatches.assemble(save, opponent, catalog, random, plan)

        assertEquals(decks[1].cards, match.setup.state.hands[CardColor.BLUE]?.map { it.id })
    }

    /**
     * Resolving the rules separately and passing them in gives the same match as letting
     * [PveMatches.assemble] do it.
     *
     * This is the contract the deck selector depends on: it needs the rules *before* the match is
     * assembled, in order to know whether to ask for a deck at all, and it must not cost a second
     * roulette draw. Asserted against a roulette opponent, where a second draw would show.
     */
    @Test
    fun resolvingTheRulesFirstDoesNotChangeTheMatch() {
        val roulette = opponent.copy(ruleKeys = listOf("RULE_ROULETTE"))
        val save = profile()

        val whole = PveMatches.assemble(save, roulette, catalog, Random(7))

        val split = Random(7).let { random ->
            val rules = PveMatches.rulesFor(roulette, save.mode, random)
            val plan = MatchPlan(rules, PveMatches.playerDeck(save))
            PveMatches.assemble(save, roulette, catalog, random, plan)
        }

        assertEquals(whole.rules, split.rules, "the roulette must be drawn exactly once")
        assertEquals(whole.setup.state.hands, split.setup.state.hands)
    }

    /** Only complete decks are offered, and the index is the save slot rather than the row. */
    @Test
    fun onlyCompleteAndResolvableDecksArePlayable() {
        val save = profile(
            cards = (1..12).toList(),
            decks = listOf(
                Deck("Partial", listOf(1, 2)),
                Deck("Full", listOf(1, 2, 3, 4, 5)),
                // Five ids, but 99 is in neither table, so this one would throw if it were offered.
                Deck("Broken", listOf(1, 2, 3, 4, 99)),
            ),
        )

        val playable = PveMatches.playableDecks(save, catalog)

        assertEquals(listOf(1), playable.map { it.index }, "the slot, not the row")
        assertEquals("Full", playable.single().value.name)
    }

    @Test
    fun aProfileWithNoCompleteDeckOffersNothingToChooseFrom() {
        val save = profile(decks = listOf(Deck("Partial", listOf(1, 2))))

        assertTrue(PveMatches.playableDecks(save, catalog).isEmpty())
        // …and still has something to play, which is what the fallback is for.
        assertEquals(HAND_SIZE, PveMatches.playerDeck(save).size)
    }

    /** The complete deck is played, not the first five cards owned. */
    @Test
    fun theFirstCompleteDeckIsPlayed() {
        val chosen = listOf(6, 7, 8, 9, 10)
        val decks = listOf(Deck("Partial", listOf(1, 2)), Deck("Full", chosen))

        val match = PveMatches.assemble(profile(decks = decks), opponent, catalog, Random(1))

        assertEquals(chosen, match.setup.state.hands[CardColor.BLUE]?.map { it.id })
    }

    /**
     * A profile whose only deck is half-built still gets a match.
     *
     * The AS3 `DeckSelector` refuses to start with a partial deck and offers no fallback, which
     * leaves such a profile with no way to play at all. See [PveMatches.playerDeck].
     */
    @Test
    fun aProfileWithNoCompleteDeckFallsBackToTheCardsItOwns() {
        val partial = profile(decks = listOf(Deck("Partial", listOf(1, 2))))

        val hand = PveMatches.assemble(partial, opponent, catalog, Random(1))
            .setup.state.hands[CardColor.BLUE]
            .orEmpty()

        assertEquals(HAND_SIZE, hand.size)
        assertTrue(hand.all { it.id in partial.cards })
    }

    @Test
    fun theOpponentAlwaysPlaysItsFetishCards() {
        for (seed in seeds) {
            val red = PveMatches.assemble(profile(), opponent, catalog, Random(seed))
                .setup.state.hands[CardColor.RED]
                .orEmpty()
                .map { it.id }

            assertTrue(red.containsAll(opponent.fetishCards), "seed $seed dropped a fetish card")
            assertEquals(HAND_SIZE, red.size, "seed $seed")
        }
    }

    // ---- Rules -----------------------------------------------------------

    @Test
    fun theOpponentsDeclaredRulesAreInForce() {
        val strict = opponent.copy(ruleKeys = listOf("RULE_REVERSE", "RULE_ORDER"))

        val match = PveMatches.assemble(profile(), strict, catalog, Random(1))

        assertTrue(match.rules.reverse)
        assertEquals(OrderRule.ORDER, match.rules.order)
        assertEquals(match.rules, match.setup.state.rules, "the state must play the same rules")
    }

    @Test
    fun anOpponentWithNoRulesPlaysTheBasicGame() {
        val match = PveMatches.assemble(profile(), opponent, catalog, Random(1))

        assertTrue(match.rules.activeRuleKeys().isEmpty())
    }

    /**
     * `RULE_ROULETTE` adds one to three more rules — and does so **here**, not in [Npc.gameRules].
     * `BaseMatchScreen.as:64-66` augments at screen construction, so an opponent's declared rules
     * are a fixed property of the opponent and what a *match* is played under is not.
     */
    @Test
    fun aRouletteOpponentGetsExtraRules() {
        val gambler = opponent.copy(ruleKeys = listOf("RULE_ROULETTE"))
        val pool = Roulette.pool(CardCollection.FF14).toSet()

        val extras = seeds.map { seed ->
            PveMatches.assemble(profile(), gambler, catalog, Random(seed))
                .rules
                .activeRuleKeys()
                .toSet() - "RULE_ROULETTE"
        }

        assertTrue(extras.all { it.isNotEmpty() }, "the roulette must always add at least one rule")
        assertTrue(extras.all { drawn -> drawn.all { it in pool } }, "drew outside the ff14 pool")
        assertTrue(extras.distinct().size > 1, "the draw must not be the same every time")
    }

    @Test
    fun anFf8RouletteOpponentDrawsFromTheFf8Pool() {
        val gambler = opponent.copy(ruleKeys = listOf("RULE_ROULETTE"))
        val pool = Roulette.pool(CardCollection.FF8).toSet()

        for (seed in seeds) {
            val drawn = PveMatches
                .assemble(profile(CardCollection.FF8), gambler, catalog, Random(seed))
                .rules
                .activeRuleKeys()
                .toSet() - "RULE_ROULETTE"

            assertTrue(drawn.all { it in pool }, "seed $seed drew ${drawn - pool}")
        }
    }

    @Test
    fun aNonRouletteOpponentGetsNothingExtra() {
        val plain = opponent.copy(ruleKeys = listOf("RULE_SAME"))

        for (seed in seeds) {
            val keys = PveMatches.assemble(profile(), plain, catalog, Random(seed))
                .rules
                .activeRuleKeys()

            assertEquals(listOf("RULE_SAME"), keys, "seed $seed")
        }
    }

    // ---- The pre-match chain reaches the match ---------------------------

    @Test
    fun anElementalOpponentGetsAnElementalBoard() {
        val elemental = opponent.copy(ruleKeys = listOf("RULE_ELEMENTAL"))

        val match = PveMatches.assemble(profile(CardCollection.FF8), elemental, catalog, Random(1))

        assertEquals(TypeRule.ELEMENTAL, match.rules.typeRule)
        assertTrue(match.setup.state.board.elements.any { it != null })
    }

    @Test
    fun anAllOpenOpponentRevealsItsWholeHand() {
        val open = opponent.copy(ruleKeys = listOf("RULE_ALL_OPEN"))

        val match = PveMatches.assemble(profile(), open, catalog, Random(1))
        val red = match.setup.state.hands[CardColor.RED].orEmpty()

        assertEquals(OpenRule.ALL_OPEN, match.rules.open)
        assertEquals(red.size, match.setup.opponentVisibility.visible(red).size)
    }

    @Test
    fun aDefaultOpponentRevealsNothing() {
        val match = PveMatches.assemble(profile(), opponent, catalog, Random(1))

        assertTrue(match.setup.opponentVisibility.visibleCardIds.isEmpty())
    }

    /** Under Random the deck is ignored and the hand comes from the whole collection. */
    @Test
    fun aRandomOpponentMakesTheHandComeFromTheCollection() {
        val chaotic = opponent.copy(ruleKeys = listOf("RULE_RANDOM"))
        val deck = Deck("Deck", listOf(1, 2, 3, 4, 5))
        val owner = profile(cards = (1..12).toList(), decks = listOf(deck))
        val outsideTheDeck = (6..12).toSet()

        val dealtOutside = seeds.count { seed ->
            PveMatches.assemble(owner, chaotic, catalog, Random(seed))
                .setup.state.hands[CardColor.BLUE]
                .orEmpty()
                .any { it.id in outsideTheDeck }
        }

        assertTrue(dealtOutside > 0, "a Random hand must be able to leave the deck behind")
    }

    @Test
    fun theIntroAnnouncesWhatTheOpponentImposes() {
        val showy = opponent.copy(ruleKeys = listOf("RULE_ALL_OPEN", "RULE_REVERSE"))

        val intro = PveMatches.assemble(profile(), showy, catalog, Random(1)).setup.intro

        assertEquals(
            listOf(
                MatchIntroStep.ALL_OPEN,
                MatchIntroStep.REVERSE,
                MatchIntroStep.COIN_FLIP,
                MatchIntroStep.START,
            ),
            intro,
        )
    }

    @Test
    fun aMatchStartsAtPlacementZeroWithACoinFlip() {
        val match = PveMatches.assemble(profile(), opponent, catalog, Random(1))

        assertEquals(0, match.setup.state.placement)
        assertEquals(match.setup.coinFlip?.winner, match.setup.state.currentPlayer)
    }

    @Test
    fun bothSidesCanWinTheFlipAcrossSeeds() {
        val first = seeds.map {
            PveMatches.assemble(profile(), opponent, catalog, Random(it)).setup.state.currentPlayer
        }

        assertEquals(setOf(CardColor.BLUE, CardColor.RED), first.toSet())
    }

    @Test
    fun aMatchIsReproducibleForAGivenSeed() {
        assertEquals(
            PveMatches.assemble(profile(), opponent, catalog, Random(3)),
            PveMatches.assemble(profile(), opponent, catalog, Random(3)),
        )
    }

    // ---- Data faults are loud -------------------------------------------

    /**
     * A card id that names nothing is refused rather than dropped.
     *
     * Both directions are data faults that cannot be reached by playing: `NpcBundleTest` holds
     * every shipped opponent to resolvable ids and a full hand, and [GameSave.sane] keeps a
     * profile's card list clean. A match quietly played with four cards would be worse than a
     * crash.
     */
    @Test
    fun anUnresolvableOpponentCardIsAProgrammingError() {
        val broken = opponent.copy(fetishCards = listOf(1, 2, 3, 4, 999), cards = emptyList())

        val failure = assertFailsWith<IllegalArgumentException> {
            PveMatches.assemble(profile(), broken, catalog, Random(1))
        }
        assertTrue("test-npc" in failure.message.orEmpty(), failure.message.orEmpty())
    }

    @Test
    fun aProfileWhoseCardsAreNotInItsCollectionIsAProgrammingError() {
        // ff8_ has 30 cards, so these ids exist in ff14_ only.
        val impossible = profile(
            mode = CardCollection.FF8,
            cards = listOf(31, 32, 33, 34, 35),
            decks = emptyList(),
        )

        assertFailsWith<IllegalArgumentException> {
            PveMatches.assemble(impossible, opponent, catalog, Random(1))
        }
    }
}
