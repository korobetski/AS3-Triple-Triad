package com.tripletriad.ui

import com.tripletriad.model.Board
import com.tripletriad.model.Capture
import com.tripletriad.model.CaptureKind
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.CardType
import com.tripletriad.model.CoinFlip
import com.tripletriad.model.GameRules
import com.tripletriad.model.HandVisibility
import com.tripletriad.model.MatchIntroStep
import com.tripletriad.model.MatchPreparation
import com.tripletriad.model.MatchSetup
import com.tripletriad.model.MatchState
import com.tripletriad.model.OpenRule
import com.tripletriad.model.OrderRule
import com.tripletriad.model.PlacedCard
import com.tripletriad.model.PlayResult
import com.tripletriad.model.TypeRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which caption a placement earns.
 *
 * This is the whole of Phase 6's logic: the motion is a table transcribed from the AS3 and
 * checked by eye, but *when* a caption plays is a decision, and it is the one that would
 * be wrong in a way nobody notices — a missing SAME looks like a dropped frame, and a
 * SAME played three times for three flipped cards looks like a stutter.
 */
class MatchBannerTest {

    @Test
    fun aBasicCaptureEarnsNoCaption() {
        val play = placement(CaptureKind.BASIC)

        assertEquals(emptyList(), MatchBanner.captionsFor(play))
    }

    @Test
    fun playingIntoAnEmptyBoardEarnsNoCaption() {
        assertEquals(emptyList(), MatchBanner.captionsFor(placement()))
    }

    @Test
    fun aSameEarnsTheSameCaption() {
        assertEquals(listOf(MatchBanner.SAME), MatchBanner.captionsFor(placement(CaptureKind.SAME)))
    }

    @Test
    fun aPlusEarnsThePlusCaption() {
        assertEquals(listOf(MatchBanner.PLUS), MatchBanner.captionsFor(placement(CaptureKind.PLUS)))
    }

    /**
     * Same Wall borrows Same's caption, because the original has no texture of its own for
     * it — `TTOCore` tags the capture apart but plays the same `SameAnim`.
     */
    @Test
    fun sameWallBorrowsTheSameCaption() {
        assertEquals(
            listOf(MatchBanner.SAME),
            MatchBanner.captionsFor(placement(CaptureKind.SAME_WALL)),
        )
    }

    /**
     * Three cards flipped by one Same is **one** caption.
     *
     * The captures are per card and the caption is about the rule, so a one-to-one map over
     * `Resolution.captures` — which is the obvious implementation, and the loop is right
     * there — would play SAME three times over itself.
     */
    @Test
    fun oneRuleFlippingThreeCardsIsStillOneCaption() {
        val play = placement(CaptureKind.SAME, CaptureKind.SAME, CaptureKind.SAME)

        assertEquals(listOf(MatchBanner.SAME), MatchBanner.captionsFor(play))
    }

    /**
     * A Same that starts a combo owes both, in that order.
     *
     * The order is the point: [MatchBanner.COMBO] carries an 0.8s lead-in sized to let the
     * caption that caused it finish, so playing them the other way round would stack them.
     */
    @Test
    fun aComboFollowsTheCaptionThatCausedIt() {
        val play = placement(CaptureKind.SAME, CaptureKind.COMBO)

        assertEquals(listOf(MatchBanner.SAME, MatchBanner.COMBO), MatchBanner.captionsFor(play))
    }

    /** And the ordering does not depend on the order the engine happened to list them in. */
    @Test
    fun theComboIsSecondEvenWhenItIsCapturedFirst() {
        val play = placement(CaptureKind.COMBO, CaptureKind.PLUS)

        assertEquals(listOf(MatchBanner.PLUS, MatchBanner.COMBO), MatchBanner.captionsFor(play))
    }

    /** A combo with no surviving direct capture still says combo rather than nothing. */
    @Test
    fun aComboOnItsOwnIsStillACaption() {
        val play = placement(CaptureKind.COMBO)

        assertEquals(listOf(MatchBanner.COMBO), MatchBanner.captionsFor(play))
    }

    /** A basic capture alongside a special one does not silence the special one. */
    @Test
    fun aBasicCaptureAlongsideASpecialOneIsIgnoredRatherThanWinning() {
        val play = placement(CaptureKind.BASIC, CaptureKind.SAME)

        assertEquals(listOf(MatchBanner.SAME), MatchBanner.captionsFor(play))
    }

    // ---- The outcome and turn captions --------------------------------------

    @Test
    fun theOutcomeCaptionFollowsTheWinner() {
        assertEquals(MatchBanner.BLUE_WIN, MatchBanner.outcome(CardColor.BLUE))
        assertEquals(MatchBanner.RED_WIN, MatchBanner.outcome(CardColor.RED))
        assertEquals(MatchBanner.DRAW, MatchBanner.outcome(null))
    }

    @Test
    fun theTurnCaptionFollowsTheSideToMove() {
        assertEquals(MatchBanner.BLUE_TURN, MatchBanner.turn(CardColor.BLUE))
        assertEquals(MatchBanner.RED_TURN, MatchBanner.turn(CardColor.RED))
    }

    // ---- The pre-match chain ------------------------------------------------

    /**
     * Every intro step has a caption, except the one that is not a caption.
     *
     * `MatchSetup.introSteps` decides *which* steps a match owes and this decides what each
     * one looks like, so the risk is a step added there and forgotten here — which the
     * exhaustive `when` catches at compile time — or a step mapped to the wrong picture,
     * which it does not. Asserted as a whole table so a transposition is visible.
     */
    @Test
    fun everyIntroStepMapsToItsOwnCaption() {
        val mapped = MatchIntroStep.entries.associateWith(MatchBanner::forIntroStep)

        assertEquals(
            mapOf(
                MatchIntroStep.RANDOM to MatchBanner.RANDOM,
                MatchIntroStep.ALL_OPEN to MatchBanner.ALL_OPEN,
                MatchIntroStep.THREE_OPEN to MatchBanner.THREE_OPEN,
                MatchIntroStep.ORDER to MatchBanner.ORDER,
                MatchIntroStep.CHAOS to MatchBanner.CHAOS,
                MatchIntroStep.REVERSE to MatchBanner.REVERSE,
                MatchIntroStep.FALLEN_ACE to MatchBanner.FALLEN_ACE,
                MatchIntroStep.SWAP to MatchBanner.SWAP,
                MatchIntroStep.COIN_FLIP to null,
                MatchIntroStep.START to MatchBanner.START,
            ),
            mapped,
        )
    }

    /**
     * The coin flip is the only step without a caption, and it is deliberate.
     *
     * `PileOuFace` deals three cards rather than showing a word. Stated as its own test
     * because "returns null" reads like an oversight at the call site, and the call site is
     * a `mapNotNull` that would silently drop a step genuinely forgotten.
     */
    @Test
    fun onlyTheCoinFlipHasNoCaption() {
        val silent = MatchIntroStep.entries.filter { MatchBanner.forIntroStep(it) == null }

        assertEquals(listOf(MatchIntroStep.COIN_FLIP), silent)
    }

    /** No two steps share a caption, so the sequence cannot say the same thing twice. */
    @Test
    fun noTwoIntroStepsShareACaption() {
        val captions = MatchIntroStep.entries.mapNotNull(MatchBanner::forIntroStep)

        assertEquals(captions.size, captions.toSet().size, "two intro steps share a caption")
    }

    /**
     * The rules that announce themselves later, or not at all.
     *
     * Elemental has no step — `openPhase` paints the board and says nothing. Same, Same
     * Wall and Plus announce themselves when they fire. Sudden Death announces itself at
     * the draw. Read through `introSteps` rather than asserted here directly, so this stays
     * true of whatever that decides rather than of a second opinion about it.
     */
    @Test
    fun theRulesWithNoOpeningCaptionStaySilent() {
        val rules = GameRules(
            typeRule = TypeRule.ELEMENTAL,
            same = true,
            sameWall = true,
            plus = true,
            suddenDeath = true,
        )

        val captions = MatchPreparation.introSteps(rules).mapNotNull(MatchBanner::forIntroStep)

        assertEquals(listOf(MatchBanner.START), captions)
    }

    /**
     * The order the player sees is `BaseMatchScreen`'s phase cascade, end to end.
     *
     * Driven through `MatchSetup.introSteps` because that is what the screen reads. The
     * *order* is what the cascade encodes — each phase's `setTimeout` names the next one —
     * and a rule announced out of turn is the failure a per-rule test would pass.
     */
    @Test
    fun everyRuleIsAnnouncedInThePhaseOrder() {
        val rules = GameRules(
            open = OpenRule.ALL_OPEN,
            order = OrderRule.CHAOS,
            random = true,
            reverse = true,
            fallenAce = true,
            swap = true,
        )

        val captions = MatchPreparation.introSteps(rules).mapNotNull(MatchBanner::forIntroStep)

        assertEquals(
            listOf(
                MatchBanner.RANDOM,
                MatchBanner.ALL_OPEN,
                MatchBanner.CHAOS,
                MatchBanner.REVERSE,
                MatchBanner.FALLEN_ACE,
                MatchBanner.SWAP,
                MatchBanner.START,
            ),
            captions,
        )
    }

    // ---- The intro, assembled ----------------------------------------------

    /**
     * The coin flip takes the place of the step that has no caption.
     *
     * Position is the assertion. `PileOuFace` runs between Swap and Start (`:220-252`), and Start
     * announcing a match whose first player has not been drawn yet is the wrong order — which is
     * the failure mode of appending the flip to the end, the obvious shortcut.
     */
    @Test
    fun theCoinFlipTakesTheStepWithNoCaption() {
        val flip = CoinFlip.forced(CardColor.BLUE)

        val played = introAnimations(setup(GameRules(swap = true), flip))

        assertEquals(
            listOf(
                MatchAnimation.Caption(MatchBanner.SWAP),
                MatchAnimation.Toss(flip),
                MatchAnimation.Caption(MatchBanner.START),
            ),
            played,
        )
    }

    /** And it carries the rolls that were drawn, so the cards agree with whose turn it is. */
    @Test
    fun theTossCarriesTheRollsThatWereDrawn() {
        val flip = CoinFlip(listOf(CardColor.RED, CardColor.BLUE, CardColor.RED))

        val toss = introAnimations(setup(GameRules(), flip)).filterIsInstance<MatchAnimation.Toss>()

        assertEquals(listOf(MatchAnimation.Toss(flip)), toss)
    }

    /**
     * A sudden-death rematch has no flip, and does not pretend to.
     *
     * The turn order carries over (`BaseMatchScreen.as:238`), so `prepareRematch` reports a null
     * [CoinFlip] and leaves the step out. Both halves are checked because either alone would let
     * the other drift: a null flip against a list that still names the step would silently drop it,
     * and a step-less list against a stale flip would show cards for a toss that never happened.
     */
    @Test
    fun aRematchHasNoCoinFlip() {
        val rematch = setup(GameRules(reverse = true), flip = null, rematch = true)

        val played = introAnimations(rematch)

        assertEquals(
            listOf(
                MatchAnimation.Caption(MatchBanner.REVERSE),
                MatchAnimation.Caption(MatchBanner.START),
            ),
            played,
        )
    }

    /** Once a card is down, the intro is over and the placement's own captions take over. */
    @Test
    fun aPlayedBoardGetsItsPlacementCaptionsRatherThanTheIntro() {
        val state = midMatch(kinds = arrayOf(CaptureKind.PLUS))

        val played = animationsFor(state, setup(GameRules(), CoinFlip.forced(CardColor.BLUE)))

        assertEquals(
            listOf(
                MatchAnimation.Caption(MatchBanner.PLUS),
                MatchAnimation.Caption(MatchBanner.RED_TURN),
            ),
            played,
        )
    }

    // ---- What a placement owes ----------------------------------------------

    /** A board nothing has been played on owes nothing; the opening chain covers it. */
    @Test
    fun anUntouchedBoardOwesNothing() {
        assertEquals(emptyList(), MatchBanner.afterPlacement(MatchState()))
    }

    /** The ordinary case: a card goes down and the other side is announced. */
    @Test
    fun anOrdinaryPlacementAnnouncesTheNextSide() {
        val state = midMatch()

        assertEquals(listOf(MatchBanner.RED_TURN), MatchBanner.afterPlacement(state))
    }

    /**
     * Captures, then Ascension, then the turn — `animate`, `ascensionPhase`, `nextTurn`.
     *
     * The order is the assertion. `ascensionPhase` sits between the flips and the turn
     * change (`:330`, `:363`), and it is the one caption whose position is easy to get
     * wrong because the rule it belongs to is decided before the match rather than by
     * the move.
     */
    @Test
    fun captionsRunCapturesThenAscensionThenTheTurn() {
        val state = midMatch(
            rules = GameRules(typeRule = TypeRule.ASCENSION),
            played = typedCard,
            kinds = arrayOf(CaptureKind.SAME),
        )

        assertEquals(
            listOf(MatchBanner.SAME, MatchBanner.ASCENSION, MatchBanner.RED_TURN),
            MatchBanner.afterPlacement(state),
        )
    }

    @Test
    fun descensionUsesItsOwnCaption() {
        val state = midMatch(rules = GameRules(typeRule = TypeRule.DESCENSION), played = typedCard)

        assertEquals(
            listOf(MatchBanner.DESCENSION, MatchBanner.RED_TURN),
            MatchBanner.afterPlacement(state),
        )
    }

    /**
     * An untyped card earns no Ascension caption even under the rule.
     *
     * `ascensionPhase` guards on `tile.card.type` and skips both the animation and its
     * 1.2s wait without it. Under FF8 rules no card is typed at all, so the guard is the
     * difference between a silent collection and one that announces Ascension nine times
     * for nothing.
     */
    @Test
    fun anUntypedCardEarnsNoAscensionCaption() {
        val state = midMatch(rules = GameRules(typeRule = TypeRule.ASCENSION), played = card)

        assertEquals(listOf(MatchBanner.RED_TURN), MatchBanner.afterPlacement(state))
    }

    /** Elemental shares the type slot and has no caption of its own. */
    @Test
    fun elementalAnnouncesNothingOnPlacement() {
        val state = midMatch(rules = GameRules(typeRule = TypeRule.ELEMENTAL), played = typedCard)

        assertEquals(listOf(MatchBanner.RED_TURN), MatchBanner.afterPlacement(state))
    }

    /** The ninth card ends the match, so the last caption is the result, not a turn. */
    @Test
    fun theLastPlacementAnnouncesTheResultRatherThanATurn() {
        assertEquals(listOf(MatchBanner.BLUE_WIN), MatchBanner.afterPlacement(finished(6)))
        assertEquals(listOf(MatchBanner.RED_WIN), MatchBanner.afterPlacement(finished(3)))
        assertEquals(listOf(MatchBanner.DRAW), MatchBanner.afterPlacement(finished(5)))
    }

    /**
     * A sudden-death draw says both: it *is* a draw, and it is not over.
     *
     * `PVEMatchScreen.as:63-68` plays the draw and then `SuddenDeathAnim` over it. Saying
     * only the second would leave the score unexplained; saying only the first would end
     * the match as far as the player can tell.
     */
    @Test
    fun aSuddenDeathDrawSaysBoth() {
        val state = finished(blueCells = 5, rules = GameRules(suddenDeath = true))

        assertEquals(
            listOf(MatchBanner.DRAW, MatchBanner.SUDDEN_DEATH),
            MatchBanner.afterPlacement(state),
        )
    }

    /** And a sudden-death match that someone actually won just announces the winner. */
    @Test
    fun suddenDeathIsSilentWhenThereIsAWinner() {
        val state = finished(blueCells = 6, rules = GameRules(suddenDeath = true))

        assertEquals(listOf(MatchBanner.BLUE_WIN), MatchBanner.afterPlacement(state))
    }

    // ---- The table itself ---------------------------------------------------

    /**
     * Every caption has a picture, and no two share one.
     *
     * A duplicated texture id is the failure mode of a table transcribed by hand from
     * twenty near-identical files, and it would surface as the wrong word on screen —
     * which is exactly the thing a reader would assume was intentional.
     */
    @Test
    fun everyCaptionHasItsOwnTexture() {
        val ids = MatchBanner.entries.map { it.textureId }

        assertEquals(ids.size, ids.toSet().size, "two captions share a texture")
        assertTrue(ids.all { it.length == TEXTURE_ID_LENGTH && it.all(Char::isDigit) })
    }

    /**
     * No caption outlasts the turn it describes.
     *
     * The turn limit is what bounds this: a caption still on screen when the next player
     * has already moved is describing a board that has changed underneath it. The longest
     * is Combo at 2.0s, against a turn limit measured in tens of seconds.
     */
    @Test
    fun noCaptionOutlastsTwoSeconds() {
        val longest = MatchBanner.entries.maxBy { it.totalMillis }

        assertTrue(
            longest.totalMillis <= LONGEST_REASONABLE_MILLIS,
            "${longest.name} runs for ${longest.totalMillis}ms",
        )
    }

    /** And every phase is a real duration — a zero would be a caption that never appears. */
    @Test
    fun everyCaptionActuallyPlays() {
        MatchBanner.entries.forEach { banner ->
            assertTrue(banner.enterMillis > 0, "${banner.name} has no entry")
            assertTrue(banner.holdMillis > 0, "${banner.name} is never held")
            assertTrue(banner.exitMillis > 0, "${banner.name} never leaves")
            assertTrue(banner.leadInMillis >= 0, "${banner.name} has a negative lead-in")
        }
    }

    /**
     * Combo waits long enough for the caption it follows.
     *
     * The pairing the AS3 timings were built around: SAME enters in 0.4s and holds 0.6s,
     * and COMBO's lead-in has to cover most of that or the two overlap. Asserted against
     * SAME rather than against 800 so that changing one forces a look at the other.
     */
    @Test
    fun theComboLeadInCoversTheCaptionItFollows() {
        val precedes = MatchBanner.SAME.enterMillis + MatchBanner.SAME.holdMillis

        assertTrue(
            MatchBanner.COMBO.leadInMillis >= precedes - MatchBanner.SAME.enterMillis,
            "COMBO would land on top of SAME",
        )
    }

    // ---- Fixtures -----------------------------------------------------------

    private fun placement(vararg kinds: CaptureKind) = PlayResult(
        player = CardColor.BLUE,
        card = card,
        position = 4,
        captures = kinds.mapIndexed { index, kind ->
            Capture(position = index, kind = kind, wave = if (kind == CaptureKind.COMBO) 1 else 0)
        },
    )

    /**
     * A prepared match, built the way the screen's really is.
     *
     * The intro comes from [MatchPreparation.introSteps] rather than being written out here, so
     * these tests describe what the screen will actually be handed instead of a second opinion
     * about it. Only [MatchSetup.intro] and [MatchSetup.coinFlip] are read; the state and the
     * visibility are filler.
     */
    private fun setup(rules: GameRules, flip: CoinFlip?, rematch: Boolean = false) = MatchSetup(
        state = MatchState(rules = rules),
        opponentVisibility = HandVisibility.HIDDEN,
        coinFlip = flip,
        intro = MatchPreparation.introSteps(rules, rematch = rematch),
    )

    /**
     * A board mid-match: blue has just moved, so red is to play.
     *
     * Assembled rather than played out, because nine legal placements to reach a state
     * would make the fixture the test. Only [MatchState.lastPlay], [MatchState.rules] and
     * whose turn it is are read here.
     */
    private fun midMatch(
        rules: GameRules = GameRules(),
        played: Card = card,
        kinds: Array<CaptureKind> = emptyArray(),
    ) = MatchState(
        rules = rules,
        placement = 1,
        hands = mapOf(CardColor.BLUE to listOf(card), CardColor.RED to listOf(card)),
        lastPlay = PlayResult(
            player = CardColor.BLUE,
            card = played,
            position = 4,
            captures = kinds.mapIndexed { index, kind -> Capture(index, kind, wave = 0) },
        ),
    )

    /**
     * A finished board owned [blueCells] to nine, with red still holding the tenth card.
     *
     * That last card is what makes the score add to ten: after nine placements one side
     * has played all five and the other four, and unplayed cards count for their owner.
     * Without it every result here would read one point short and the draw case would
     * not be a draw.
     */
    private fun finished(blueCells: Int, rules: GameRules = GameRules()) = MatchState(
        rules = rules,
        placement = 9,
        board = Board(
            cells = List(Board.SIZE) {
                PlacedCard(card, if (it < blueCells) CardColor.BLUE else CardColor.RED)
            },
        ),
        hands = mapOf(CardColor.BLUE to emptyList(), CardColor.RED to listOf(card)),
        lastPlay = PlayResult(CardColor.BLUE, card, position = 8, captures = emptyList()),
    )

    /** Any card. Nothing here reads a power — the caption is decided by the capture kind. */
    private val card = Card(
        id = 1,
        collection = "ff14_",
        nameKey = "STR_FF14_CARD_1",
        name = "Test",
        top = 1,
        right = 2,
        bottom = 3,
        left = 4,
        rarity = 1,
    )

    /** The same card with a tribe, which is what Ascension and Descension key off. */
    private val typedCard = card.copy(type = CardType.PRIMALS)

    private companion object {
        const val TEXTURE_ID_LENGTH = 6
        const val LONGEST_REASONABLE_MILLIS = 2_000
    }
}
