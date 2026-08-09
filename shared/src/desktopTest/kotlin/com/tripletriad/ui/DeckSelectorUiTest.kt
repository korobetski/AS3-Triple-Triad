package com.tripletriad.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.data.loadNpcCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.CardCollection
import com.tripletriad.model.CardColor
import com.tripletriad.model.Deck
import com.tripletriad.model.GameSave
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.storage.InMemoryDocumentStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Choosing which deck to play — the original's `DeckSelector`.
 *
 * The two things worth pinning are both about *when* it appears: it is a step inside the match, so
 * the match is already counted as started while it is on screen, and it is skipped entirely under
 * `RULE_RANDOM`. Both come straight from `BaseMatchScreen.deckSelectionPhase`.
 */
@OptIn(ExperimentalTestApi::class)
class DeckSelectorUiTest {
    private val cards = runBlocking { loadCardCatalog() }
    private val english = runBlocking { loadStrings(AppLocale.EN_US) }

    /** A profile with two complete decks that share no card, plus a partial one. */
    private fun withDecks(): GameSave = GameSave.new(createdAt = 0L).copy(
        cards = (STARTER + EXTRA).sorted(),
        decks = listOf(
            Deck(name = "Starters", cards = STARTER),
            Deck(name = "Halfling", cards = STARTER.take(2)),
            Deck(name = "Heavies", cards = EXTRA),
        ),
    )

    private fun ComposeUiTest.reachSelector(documents: InMemoryDocumentStore) {
        loadCharacter(documents)
        openOpponents()
        onNodeWithTag(opponentRowTestTag(TEST_OPPONENT)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_SELECT_CHOOSE_TEST_TAG) }
    }

    /** The English name of an ff14 card, which is what a hand shows once a card is picked up. */
    private fun nameOf(cardId: Int): String {
        val card = cards.collection(CardCollection.FF14.prefix).first { it.id == cardId }
        return english[card.nameKey]
    }

    /** The turn line, which names the card currently in hand. */
    private fun ComposeUiTest.turnLine(): String {
        val node = onNodeWithTag(TURN_TEST_TAG).fetchSemanticsNode()
        return node.config[SemanticsProperties.Text].joinToString("") { it.text }
    }

    /** Only complete decks are offered — `DeckSelector.as:79` adds a row only `if (fullDeck)`. */
    @Test
    fun onlyCompleteDecksAreOffered() = runComposeUiTest {
        val documents = seeded(withDecks())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        reachSelector(documents)

        onNodeWithTag(deckChoiceTestTag(0)).assertExists()
        onNodeWithTag(deckChoiceTestTag(1)).assertExists()
        assertFalse(exists(deckChoiceTestTag(2)), "only two of the three decks are complete")
        assertTrue(isVisible("Starters"))
        assertTrue(isVisible("Heavies"))
        assertFalse(isVisible("Halfling"), "a partial deck is not playable")
    }

    /**
     * The deck picked is the deck dealt.
     *
     * Read off the **turn line**, which names the card the player has picked up, because a hand is
     * drawn art and carries no text of its own. Tapping the first slot and finding a `Heavies` card
     * there is what says the selection reached the deal rather than only highlighting a row.
     */
    @Test
    fun theChosenDeckIsTheHandThatIsDealt() = runComposeUiTest {
        val documents = seeded(withDecks())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        reachSelector(documents)

        // Row 1 is `Heavies`: save slot 2, slot 1 having been filtered out as incomplete.
        onNodeWithTag(deckChoiceTestTag(1)).performClick()
        onNodeWithTag(DECK_SELECT_CHOOSE_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
        awaitPlayer()

        assertEquals(HAND_SIZE, handSize(CardColor.BLUE))
        onNodeWithTag(handCardTestTag(CardColor.BLUE, 0)).performClick()
        waitForIdle()

        val line = turnLine()
        assertTrue(
            EXTRA.any { line.contains(nameOf(it)) },
            "the hand should be the Heavies deck; the turn line reads \"$line\"",
        )
        assertFalse(
            STARTER.any { line.contains(nameOf(it)) },
            "and not the starter deck; the turn line reads \"$line\"",
        )
    }

    /**
     * A deck's label follows its **save slot**, not its position in the offered list.
     *
     * Filtering the incomplete decks out would otherwise rename the survivors: an unnamed deck in
     * slot 5 must still read `Deck 5` when it is the second row shown.
     */
    @Test
    fun anUnnamedDeckIsLabelledByItsSaveSlotAndNotItsRow() = runComposeUiTest {
        val profile = GameSave.new(createdAt = 0L).copy(
            cards = (STARTER + EXTRA).sorted(),
            decks = listOf(
                Deck(name = "Starters", cards = STARTER),
                Deck(name = "", cards = emptyList()),
                Deck(name = "", cards = emptyList()),
                Deck(name = "", cards = emptyList()),
                Deck(name = "", cards = EXTRA),
            ),
        )
        val documents = seeded(profile)
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        reachSelector(documents)

        onNodeWithTag(deckChoiceTestTag(1)).assertExists()
        assertTrue(isVisible("Deck 5"), "the label follows the slot")
        assertFalse(isVisible("Deck 2"), "and is not renumbered to the row it happens to sit on")
    }

    /**
     * The first offered deck starts selected.
     *
     * `chooseBtn.isEnabled = false` with nothing picked in the original (`:117`), so playing always
     * cost two taps. One deck is the common case and it should be one tap.
     */
    @Test
    fun theFirstDeckIsAlreadySelected() = runComposeUiTest {
        val documents = seeded(withDecks())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        reachSelector(documents)

        onNodeWithTag(DECK_SELECT_CHOOSE_TEST_TAG).assertIsEnabled()
    }

    /**
     * With no complete deck the list says so, and Random is the way through.
     *
     * `if (deckCollection.length == 0) { }` in the original — an empty block, so the panel showed
     * an empty list and no reason for it. Random always works: it draws from the collection, and
     * every profile owns at least five cards ([GameSave.DEFAULT_CARDS]).
     */
    @Test
    fun withNoCompleteDeckTheListSaysSoAndRandomStillPlays() = runComposeUiTest {
        val profile = GameSave.new(createdAt = 0L)
            .copy(decks = listOf(Deck(name = "Half", cards = STARTER.take(HALF_A_DECK))))
        val documents = seeded(profile)
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        reachSelector(documents)

        onNodeWithTag(DECK_SELECT_EMPTY_TEST_TAG).assertExists()
        onNodeWithTag(DECK_SELECT_CHOOSE_TEST_TAG).assertIsNotEnabled()

        onNodeWithTag(DECK_SELECT_RANDOM_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }
        awaitPlayer()

        assertEquals(HAND_SIZE, handSize(CardColor.BLUE))
    }

    /**
     * Under `RULE_RANDOM` the selector never opens.
     *
     * `deckSelectionPhase` branches on `RULES.RANDOM` *before* it constructs the panel and deals
     * from the whole collection instead — so a chosen deck would have been ignored, and asking
     * would be a question with no answer. This is why the rules are resolved before the deck is
     * asked for: see `PveMatches.rulesFor`.
     */
    @Test
    fun theRandomRuleSkipsTheSelectorEntirely() = runComposeUiTest {
        val documents = seeded(GameSave.new(createdAt = 0L, mode = CardCollection.FF8))
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openOpponents()

        onNodeWithTag(OPPONENT_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(opponentRowTestTag(RANDOM_OPPONENT)))
        onNodeWithTag(opponentRowTestTag(RANDOM_OPPONENT)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(BOARD_TEST_TAG) }

        assertFalse(exists(DECK_SELECT_CHOOSE_TEST_TAG), "Random must not ask for a deck")
    }

    /** The fixture's premise: that opponent really does impose Random, and without a roulette. */
    @Test
    fun theRandomOpponentIsTheOneTheFixtureAssumes() {
        val npcs = runBlocking { loadNpcCatalog() }
        val npc = npcs.available(CardCollection.FF8, NOON, ANY_LEVEL)
            .first { it.iconId == RANDOM_OPPONENT }

        assertTrue(npc.gameRules().random, "$RANDOM_OPPONENT should impose RULE_RANDOM")
        assertFalse(npc.gameRules().roulette, "and should not draw more rules on top")
    }

    /**
     * The match is already counted as started while the selector is up.
     *
     * `PVEScreen.as:244` increments `STARTED_MATCHES` when the match screen is launched, which in
     * the original is before the selector opens — the panel is a child of the match screen. Backing
     * out from here is therefore a forfeit, which is what that counter is for.
     */
    @Test
    fun backingOutOfTheSelectorStillCountsAsAForfeit() = runComposeUiTest {
        val documents = seeded(withDecks())
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        reachSelector(documents)

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).startedMatches == 1 }
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        awaitOpponents()

        val save = storedSave(documents)
        assertEquals(1, save.startedMatches)
        assertEquals(0, save.endedMatches)
        assertEquals(1, save.forfeits, "started minus ended")
    }

    private companion object {
        /** The five a fresh profile owns. */
        val STARTER = GameSave.DEFAULT_CARDS

        /** Five ff14 cards outside the starter set, so the two decks share nothing. */
        val EXTRA = listOf(44, 45, 51, 63, 74)

        /** `ma-dincht` imposes All Open, Random and Elemental, and declares no roulette. */
        const val RANDOM_OPPONENT = "ma-dincht"

        /** `FixedClock.DEFAULT_HOUR`, which is when every test without its own clock plays. */
        const val NOON = 12

        /** Fewer than [HAND_SIZE], so the deck is not playable. */
        const val HALF_A_DECK = 3
    }
}
