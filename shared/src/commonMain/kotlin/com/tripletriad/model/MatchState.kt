package com.tripletriad.model

import kotlin.random.Random

/** What one placement did. Kept on the state so the UI can animate the last move. */
data class PlayResult(
    val player: CardColor,
    val card: Card,
    val position: Int,
    val captures: List<Capture>,
)

/** How a finished match ended. */
sealed interface MatchOutcome {
    val score: MatchScore

    data class Win(val winner: CardColor, override val score: MatchScore) : MatchOutcome

    data class Draw(override val score: MatchScore) : MatchOutcome

    /**
     * A draw with Sudden Death active. Call [MatchState.suddenDeathRematch] for the
     * next match.
     *
     * The AS3 signals this by dispatching a `'sudden_death'` event carrying the
     * rebuilt hands and the existing timeline (`PVEMatchScreen.as:175`,
     * `PVPMatchScreen.as:369`) — a new screen instance, not a loop. That shape exists
     * because Starling screens cannot return values; here it is a return value.
     */
    data class SuddenDeath(override val score: MatchScore) : MatchOutcome
}

/**
 * A whole match, as one immutable value.
 *
 * Replaces the original's `setTimeout` cascade and mutable screen state. `BaseMatchScreen`
 * sequences a match through fourteen deferred callbacks, each guessing how long the previous
 * animation takes, with `turn`, `timeline`, `ascensionByType` and the board all living as
 * mutable fields on the screen
 * ([data-flow.md](../../../../../../../docs/analysis/data-flow.md) § 1.2). Nothing there can
 * be tested without a Starling stage.
 *
 * Here every transition is `MatchState -> MatchState`. Animation is the UI's problem: it reads
 * [lastPlay] and takes as long as it likes, because nothing downstream is waiting on a timer.
 */
data class MatchState(
    val rules: GameRules = GameRules(),
    val options: RulesEngineOptions = RulesEngineOptions(),
    val board: Board = Board(),
    /** Cards **still in hand**, per side. Played cards move onto [board]. */
    val hands: Map<CardColor, List<Card>> = emptyMap(),
    val order: TurnOrder = TurnOrder(CardColor.BLUE),
    /** How many cards have been placed: 0..9. At 9 the match is over. */
    val placement: Int = 0,
    val tally: AscensionTally = AscensionTally.EMPTY,
    val lastPlay: PlayResult? = null,
) {
    val isFinished: Boolean get() = placement >= PLACEMENTS_PER_MATCH

    /** Whose turn it is, or `null` once the board is full. */
    val currentPlayer: CardColor? get() = if (isFinished) null else order.colorAt(placement)

    /** The cards the side to move still holds. Empty when the match is over. */
    val currentHand: List<Card>
        get() = currentPlayer?.let { hands[it] }.orEmpty()

    /**
     * Counts colors across both hands, played or not — see [score].
     *
     * Unplayed cards count for their owner, so the total is always [TOTAL_CARDS] and a
     * draw is 5-5.
     */
    val score: MatchScore get() = score(board, hands.mapValues { it.value.size })

    /**
     * Which of [currentHand] may legally be played this turn.
     *
     * - [OrderRule.FREE] — all of them.
     * - [OrderRule.ORDER] — the first remaining card only (`BaseMatchScreen.as:390`,
     *   `PVEMatchScreen.as:186`).
     * - [OrderRule.CHAOS] — one at random (`BaseMatchScreen.as:392`, `:427`).
     *
     * [random] is a parameter rather than a global so Chaos is reproducible in tests. It
     * is ignored unless the rule is Chaos.
     */
    fun playableCards(random: Random = Random.Default): List<Card> {
        val hand = currentHand
        return when {
            hand.isEmpty() -> emptyList()
            rules.order == OrderRule.ORDER -> listOf(hand.first())
            rules.order == OrderRule.CHAOS -> listOf(hand[random.nextInt(hand.size)])
            else -> hand
        }
    }

    /** Board positions a card may be placed on. */
    fun playablePositions(): List<Int> = if (isFinished) emptyList() else board.emptyPositions()

    /**
     * Plays [card] from the current hand onto [position].
     *
     * Resolves captures, updates the Ascension/Descension tally and advances the turn.
     * The tally is applied **after** resolution, mirroring `ascensionPhase` running once
     * the flips are done (`TTOCore.as:171`) — so the card just placed does not benefit
     * from its own contribution to the tally.
     */
    fun play(card: Card, position: Int): MatchState {
        // checkNotNull, not requireNotNull: "the board is full" is a state error, not a
        // bad argument, so it raises IllegalStateException.
        val player = checkNotNull(currentPlayer) { "the match is over" }
        val hand = hands[player].orEmpty()
        val index = hand.indexOfFirst { it.id == card.id }
        require(index >= 0) { "card ${card.id} is not in $player's hand" }

        val resolution = RulesEngine(rules, options).resolve(board, position, card, player, tally)
        return copy(
            board = resolution.board,
            hands = hands + (player to hand.filterIndexed { i, _ -> i != index }),
            placement = placement + 1,
            tally = tally.record(card.type, rules.typeRule),
            lastPlay = PlayResult(player, card, position, resolution.captures),
        )
    }

    /** The result, or `null` while the match is still running. */
    fun outcome(): MatchOutcome? {
        if (!isFinished) return null
        val final = score
        val winner = final.winner()
        return when {
            winner != null -> MatchOutcome.Win(winner, final)
            rules.suddenDeath -> MatchOutcome.SuddenDeath(final)
            else -> MatchOutcome.Draw(final)
        }
    }

    /**
     * The follow-up match after a Sudden Death draw: **each side takes the cards it
     * ended up owning.**
     *
     * `suddenDeathDispatcher` builds each new hand as
     * `getCardIdsByColor().BLUE.concat(...)` over both panels — board and hand alike —
     * and passes the existing `timeline` through unchanged, so **who moves first does not
     * change** and there is no new coin flip (`BaseMatchScreen.as:238-243`,
     * `:415-420`).
     *
     * Cards return to their printed owner color for the new hands; ownership is carried
     * by which hand they are in, exactly as in the original.
     */
    fun suddenDeathRematch(elements: List<CardType?> = List(Board.SIZE) { null }): MatchState {
        require(isFinished) { "sudden death needs a finished match" }
        val regrouped = CardColor.entries.associateWith { color ->
            board.cells.filterNotNull().filter { it.owner == color }.map { it.card } +
                hands[color].orEmpty()
        }
        return MatchState(
            rules = rules,
            options = options,
            board = Board(elements = elements),
            hands = regrouped.mapValues { (color, cards) ->
                cards.map { it.copy(owner = color) }
            },
            order = order,
            tally = AscensionTally.EMPTY,
        )
    }

    companion object {
        /**
         * Starts a match.
         *
         * The pre-match rule chain — Random hand, Swap, Open, the coin flip — is **not**
         * modeled: five of its seven links exist only to play an animation, and the two
         * that do work (building a random hand, swapping a card) belong to whatever
         * assembles the hands. Pass the hands you want and who moves first.
         *
         * [RulesEngineOptions] is deliberately absent here. It is a port-fidelity switch
         * for the whole application, not a per-match setting, so the default holds and the
         * rare caller that needs otherwise writes `start(…).copy(options = …)`.
         */
        fun start(
            blueHand: List<Card>,
            redHand: List<Card>,
            first: CardColor = CardColor.BLUE,
            rules: GameRules = GameRules(),
            elements: List<CardType?> = List(Board.SIZE) { null },
        ): MatchState {
            require(blueHand.size == HAND_SIZE && redHand.size == HAND_SIZE) {
                "each hand must hold exactly $HAND_SIZE cards, " +
                    "had ${blueHand.size} and ${redHand.size}"
            }
            return MatchState(
                rules = rules,
                board = Board(elements = elements),
                hands = mapOf(
                    CardColor.BLUE to blueHand.map { it.copy(owner = CardColor.BLUE) },
                    CardColor.RED to redHand.map { it.copy(owner = CardColor.RED) },
                ),
                order = TurnOrder(first),
            )
        }

        /**
         * Assigns an element to each cell with probability ½, drawn from the eight FF8
         * elements — `Board.elements()` (`:52-65`).
         *
         * Uses a uniform [Random] rather than the original's
         * `Math.round(Math.random() * to)`, which gives **half the probability mass to
         * both endpoints**
         * ([game-rules.md](../../../../../../../docs/analysis/game-rules.md) § 15.6).
         */
        fun randomElements(random: Random = Random.Default): List<CardType?> =
            List(Board.SIZE) {
                if (random.nextBoolean()) FF8_ELEMENTS[random.nextInt(FF8_ELEMENTS.size)] else null
            }

        /** The element set `Board.as:53` draws from. The four FF14 tribes are not elements. */
        val FF8_ELEMENTS = listOf(
            CardType.EARTH,
            CardType.FIRE,
            CardType.HOLY,
            CardType.ICE,
            CardType.LIGHTNING,
            CardType.POISON,
            CardType.WATER,
            CardType.WIND,
        )
    }
}
