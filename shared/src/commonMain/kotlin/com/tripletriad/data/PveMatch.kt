package com.tripletriad.data

import com.tripletriad.model.Card
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
 * - **the opponent**: its hand, from its fetish cards topped up out of its pool
 *   ([Npc.randomHand]), and its rules ([Npc.gameRules]).
 * - **the roulette**: if the opponent declares `RULE_ROULETTE`, one to three more rules on top
 *   ([Roulette.augment]). This is where that happens, matching `BaseMatchScreen.as:64-66`, which
 *   augments at screen construction and not at rule declaration.
 */
object PveMatches {
    /**
     * Assembles a match between [profile] and [npc].
     *
     * @throws IllegalArgumentException if either side cannot field five cards — a card id naming a
     *   card that is not in the collection, or a profile that owns fewer than five. Both are data
     * faults rather than states a player can reach: `NpcBundleTest` holds every shipped opponent to
     * a full hand and to resolvable ids, and [GameSave.sane] keeps a profile's card list clean. A
     * loud failure is better than a match quietly played with four cards.
     */
    fun assemble(
        profile: GameSave,
        npc: Npc,
        catalog: CardCatalog,
        random: Random = Random.Default,
    ): PveMatch {
        val cards = catalog.collection(profile.mode.prefix).associateBy { it.id }
        val declared = npc.gameRules()
        // The opponent's own rules decide whether the roulette runs, so this cannot be hoisted
        // into `Npc.gameRules()`: an opponent's declared rules are a fixed property of the
        // opponent, and what a *match* is played under is not.
        val rules = if (declared.roulette) {
            Roulette.augment(declared, profile.mode, random)
        } else {
            declared
        }

        val deck = resolve(playerDeck(profile), cards, "profile '${profile.username}' deck")
        val collection = profile.cards.mapNotNull { cards[it] }
        val redHand = resolve(npc.randomHand(random), cards, "opponent '${npc.iconId}' hand")

        return PveMatch(
            setup = MatchPreparation.prepare(
                blue = HandSource(deck, collection.ifEmpty { deck }),
                redHand = redHand,
                rules = rules,
                random = random,
            ),
            npc = npc,
            rules = rules,
        )
    }

    /**
     * The five cards the profile plays.
     *
     * The first **complete** deck, or the first five cards owned when no deck has five in it. The
     * AS3 `DeckSelector` refuses to start a match with a partial deck (`Deck.isComplete`) and
     * offers no fallback, which leaves a player whose only deck is half-built with no way to play
     * at all. Five owned cards is a better answer than a dead end, and every profile owns at least
     * five ([GameSave.DEFAULT_CARDS]).
     *
     * Deck *selection* is not offered yet — this takes the first usable one. That is a screen, and
     * it needs the collection browser to be worth having.
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
