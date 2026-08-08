package com.tripletriad.data

import com.tripletriad.model.Card
import com.tripletriad.model.CardCollection
import com.tripletriad.model.CoinFlip
import com.tripletriad.model.Deck
import com.tripletriad.model.GameRules
import com.tripletriad.model.GameSave
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.HandSource
import com.tripletriad.model.MatchPreparation
import com.tripletriad.model.MatchSetup
import com.tripletriad.model.Npc
import com.tripletriad.model.Roulette
import kotlin.random.Random

/**
 * A match against an opponent, ready to play.
 *
 * @property rules what the match is actually played under, roulette draws included. Kept separately
 * from `setup.state.rules`, which holds the same value, because the end-of-match crediting needs it
 * and reaching through two objects for it invites reading the opponent's *declared* rules by
 * mistake — which under the roulette are not the ones that were played.
 */
data class PveMatch(
    val setup: MatchSetup,
    val npc: Npc,
    val rules: GameRules,
)

/**
 * The two things settled before the cards are dealt.
 *
 * They travel together because they are settled together and in that order: the rules decide
 * whether the player is even asked for a deck — under `RULE_RANDOM` the hand is drawn from the
 * whole collection and `DeckSelector` never opens. See [PveMatches.rulesFor].
 *
 * @property rules what the match is played under, roulette drawn.
 * @property deck the five card ids the player brings, by id rather than as a [Deck] because the
 *   selector's Random button produces a hand that belongs to no deck.
 */
data class MatchPlan(val rules: GameRules, val deck: List<Int>)

/**
 * Turns a profile and an opponent into a playable match.
 *
 * This is the join the AS3 makes by passing a properties object into a screen navigator
 * (`Game.prepareMatch`, `:106-118`) and then reading `Game.PROFILE_DATAS` out of a global for the
 * other half. Both halves are arguments here, so assembling a match is a function that can be
 * tested without a screen.
 *
 * ### What each side brings
 *
 * - **the player**: the deck, or the collection under `RULE_RANDOM` — see [HandSource].
 * - **the opponent**: its hand, from its fetish cards topped up out of its pool ([Npc.randomHand]),
 *   and its rules ([Npc.gameRules]).
 * - **the roulette**: if the opponent declares `RULE_ROULETTE`, one to three more rules on top
 *   ([Roulette.augment]). This is where that happens, matching `BaseMatchScreen.as:64-66`, which
 *   augments at screen construction and not at rule declaration.
 */
object PveMatches {
    /**
     * Assembles a match between [profile] and [npc].
     *
     * @param forcedFlip who starts, when something other than a coin has already decided — the
     *   tutorial, which needs the opponent to move first so it has something to demonstrate
     *   (`TutorialScreen.as:64`). Left null, the flip is a real toss.
     * @throws IllegalArgumentException if either side cannot field five cards — a card id naming a
     *   card that is not in the collection, or a profile that owns fewer than five. Both are data
     * faults rather than states a player can reach: `NpcBundleTest` holds every shipped opponent to
     * a full hand and to resolvable ids, and [GameSave.sane] keeps a profile's card list clean. A
     * loud failure is better than a match quietly played with four cards.
     */
    @Suppress("LongParameterList")
    fun assemble(
        profile: GameSave,
        npc: Npc,
        catalog: CardCatalog,
        random: Random = Random.Default,
        plan: MatchPlan = MatchPlan(rulesFor(npc, profile.mode, random), playerDeck(profile)),
        forcedFlip: CoinFlip? = null,
    ): PveMatch {
        val (rules, deck) = plan
        val cards = catalog.collection(profile.mode.prefix).associateBy { it.id }
        val blueDeck = resolve(deck, cards, "profile '${profile.username}' deck")
        val collection = profile.cards.mapNotNull { cards[it] }
        val redHand = resolve(npc.randomHand(random), cards, "opponent '${npc.iconId}' hand")

        return PveMatch(
            setup = MatchPreparation.prepare(
                blue = HandSource(blueDeck, collection.ifEmpty { blueDeck }),
                redHand = redHand,
                rules = rules,
                random = random,
                forcedFlip = forcedFlip,
            ),
            npc = npc,
            rules = rules,
        )
    }

    /**
     * What the match will be played under, roulette drawn.
     *
     * Split out of [assemble] because **the rules decide whether the player is asked for a deck at
     * all**: under `RULE_RANDOM` the hand comes from the whole collection and the selector never
     * opens (`BaseMatchScreen.as:120-135`). So the rules have to be known before the question can
     * be put, and a roulette opponent's rules are not known until they are drawn.
     *
     * This cannot be folded into [Npc.gameRules]: an opponent's *declared* rules are a fixed
     * property of the opponent, and what a *match* is played under is not.
     *
     * @param random consumed only when [Npc] declares the roulette. [assemble] defaults to calling
     *   this, so the draw order is unchanged for a caller that does not resolve the rules itself —
     *   and a caller that does must pass what it got back, or the roulette is drawn twice.
     */
    fun rulesFor(npc: Npc, collection: CardCollection, random: Random): GameRules {
        val declared = npc.gameRules()
        return if (declared.roulette) Roulette.augment(declared, collection, random) else declared
    }

    /**
     * The decks the profile could actually field: complete, and resolvable in its own collection.
     *
     * `DeckSelector.as:58-83` lists only complete decks — it counts non-zero card ids and adds the
     * row only `if (fullDeck)`. Resolvability is checked too, which the original does not: a stored
     * id naming no card in the profile's table would make [assemble] throw on "Play this deck", and
     * an unplayable row is better left off the list than offered and refused.
     *
     * Indexed, and the index is the **save slot** rather than the position in this list: an unnamed
     * deck is labelled by its slot number ([DeckSelectorScreen]), so filtering out the incomplete
     * ones would otherwise rename the survivors.
     */
    fun playableDecks(profile: GameSave, catalog: CardCatalog): List<IndexedValue<Deck>> {
        val ids = catalog.collection(profile.mode.prefix).mapTo(mutableSetOf()) { it.id }
        return profile.decks.withIndex()
            .filter { (_, deck) -> deck.isComplete && ids.containsAll(deck.cards) }
    }

    /**
     * The five cards the profile plays when nothing has been chosen.
     *
     * The first **complete** deck, or the first five cards owned when no deck has five in it. The
     * AS3 `DeckSelector` refuses to start a match with a partial deck (`Deck.isComplete`) and
     * offers no fallback — `if (deckCollection.length == 0) { }` is literally an empty block —
     * which leaves a player whose only deck is half-built looking at an empty list. Five owned
     * cards is a better answer than a dead end, and every profile owns at least five
     * ([GameSave.DEFAULT_CARDS]).
     *
     * Still the default rather than dead code now that [DeckSelectorScreen] exists: it is what a
     * caller assembling a match without asking anybody gets, which is every test that does not care
     * which five cards it plays.
     */
    fun playerDeck(profile: GameSave): List<Int> =
        profile.decks.firstOrNull { it.isComplete }?.cards ?: profile.cards.take(HAND_SIZE)

    private fun resolve(ids: List<Int>, cards: Map<Int, Card>, what: String): List<Card> {
        val resolved = ids.mapNotNull { cards[it] }
        require(resolved.size == HAND_SIZE) {
            "$what needs $HAND_SIZE cards in this collection, resolved ${resolved.size} of " +
                "${ids.size} (ids $ids)"
        }
        return resolved
    }
}
