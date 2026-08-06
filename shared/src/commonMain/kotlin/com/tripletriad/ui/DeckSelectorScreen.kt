package com.tripletriad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.PveMatches
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.model.Deck
import com.tripletriad.model.GameRules
import com.tripletriad.model.GameSave
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.MatchPreparation
import com.tripletriad.model.Npc
import com.tripletriad.ui.theme.LocalTtoColors
import kotlin.random.Random

const val DECK_SELECT_TEST_TAG: String = "deck-select"
const val DECK_SELECT_CHOOSE_TEST_TAG: String = "deck-select-choose"
const val DECK_SELECT_RANDOM_TEST_TAG: String = "deck-select-random"
const val DECK_SELECT_EMPTY_TEST_TAG: String = "deck-select-empty"

/** `deck-choice-<n>`, 0-based over the *playable* decks — not over the five save slots. */
fun deckChoiceTestTag(index: Int): String = "deck-choice-$index"

/**
 * Which deck to play this match with — the original's `DeckSelector`.
 *
 * ### Why it lives inside the match and not before it
 *
 * `BaseMatchScreen.deckSelectionPhase` (`:113-143`) is where the AS3 opens this panel, and the
 * placement is load-bearing rather than incidental: **under `RULE_RANDOM` the panel never opens** —
 * the hand is dealt from the whole collection and any chosen deck is ignored. Since the roulette
 * can *add* Random to an opponent's declared rules, whether the player is asked at all is not known
 * until the roulette has been drawn. So the rules are resolved first ([PveMatches.rulesFor]) and
 * this screen is a step inside [MatchScreen], not a destination ahead of it.
 *
 * It is also why the match is already counted as started by the time this is on screen: `PVEScreen`
 * increments `STARTED_MATCHES` when the match screen is launched, which in the original is before
 * the selector opens. Backing out from here is the forfeit that counter was designed for.
 *
 * ### Only complete decks, and always Random
 *
 * `DeckSelector.as:79-80` adds a row only `if (fullDeck)`, so a partial deck is not offered — and
 * when *no* deck is complete its handling is `if (deckCollection.length == 0) { }`, an empty block,
 * leaving an empty list with no explanation. The list here says so, and Random is always available:
 * it draws from the collection rather than from a deck, so it works for a profile that has never
 * built one. Every profile owns at least five cards ([GameSave.DEFAULT_CARDS]), so it is never a
 * dead end.
 *
 * ### One change from the original: the first deck starts selected
 *
 * `chooseBtn.isEnabled = false` with nothing selected (`:117`), so the original always cost two
 * taps — pick a row, then confirm. Pre-selecting makes the common case (one deck, play it) one tap,
 * and costs nothing to a player who wants a different one. The rows still show what they hold,
 * which is what the panel is for.
 *
 * @param onChoose the five card ids to play. Resolved here rather than passed as a [Deck] because
 *   Random produces a hand that belongs to no deck.
 * @param random the Random button's draw. Injected so a test can pin which five come out.
 */
@Composable
internal fun DeckSelectorScreen(
    profile: GameSave,
    catalog: CardCatalog,
    npc: Npc,
    rules: GameRules,
    onChoose: (List<Int>) -> Unit,
    onBack: () -> Unit,
    random: Random = Random.Default,
) {
    val strings = LocalStrings.current
    val cards = remember(catalog, profile.mode) {
        catalog.collection(profile.mode.prefix).associateBy { it.id }
    }
    val decks = remember(profile.decks, catalog, profile.mode) {
        PveMatches.playableDecks(profile, catalog)
    }
    var selected by remember(decks) { mutableStateOf(if (decks.isEmpty()) null else 0) }

    CharacterScaffold(
        profile = profile,
        title = strings[StringKeys.CARD_DECKS],
        onBack = onBack,
    ) {
        // Who is being played and under what. The original showed neither here — `RulesDigest` is
        // on the board, one screen later — and the rules are exactly what a deck should answer:
        // Reverse or Fallen Ace turns a deck of aces into the wrong deck.
        OpponentLine(npc = npc, rules = rules)

        if (decks.isEmpty()) {
            EmptyNote(strings[StringKeys.NO_FULL_DECK], DECK_SELECT_EMPTY_TEST_TAG)
        } else {
            LazyColumn(
                modifier = Modifier
                    .testTag(DECK_SELECT_TEST_TAG)
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(decks) { row, (slot, deck) ->
                    DeckChoiceRow(
                        deck = deck,
                        slot = slot,
                        row = row,
                        cards = cards,
                        isSelected = selected == row,
                        onClick = { selected = row },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                WideButton(strings[StringKeys.RANDOM_DECK], DECK_SELECT_RANDOM_TEST_TAG) {
                    val collection = profile.cards.mapNotNull(cards::get)
                    onChoose(MatchPreparation.randomHand(collection, random).map { it.id })
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                WideButton(
                    label = strings[StringKeys.CHOOSE_DECK],
                    tag = DECK_SELECT_CHOOSE_TEST_TAG,
                    enabled = selected != null,
                    onClick = { selected?.let { onChoose(decks[it].value.cards) } },
                )
            }
        }
    }
}

/** `Y'shtola  ·  All Open  ·  Plus` — who, and what is in force. */
@Composable
private fun OpponentLine(npc: Npc, rules: GameRules) {
    val strings = LocalStrings.current
    val keys = rules.activeRuleKeys()

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = strings[npc.nameKey],
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (keys.isNotEmpty()) {
            Text(
                text = keys.joinToString(DOT_SEPARATOR) { strings[it] },
                color = LocalTtoColors.current.transient,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One offered deck: its name, its power, and the five cards it holds.
 *
 * @param slot where it lives in the save, which is what names an unnamed deck.
 * @param row where it sits in *this* list, which is what the tag and the selection are keyed on —
 *   a test asking for the first offered deck should not have to know which slots were skipped.
 */
@Composable
private fun DeckChoiceRow(
    deck: Deck,
    slot: Int,
    row: Int,
    cards: Map<Int, Card>,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .testTag(deckChoiceTestTag(row))
            .fillMaxWidth()
            .rowSurface(selected = isSelected)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = deckLabel(strings, deck, slot),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${strings[StringKeys.DECK_POWER]} ${deckPower(deck, cards)}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            for (position in 0 until HAND_SIZE) {
                DeckPosition(card = deck.cards.getOrNull(position)?.let(cards::get))
            }
        }
    }
}
