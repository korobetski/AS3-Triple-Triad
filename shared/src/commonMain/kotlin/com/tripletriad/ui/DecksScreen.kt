package com.tripletriad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.CardCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Card
import com.tripletriad.model.Deck
import com.tripletriad.model.GameSave
import com.tripletriad.model.HAND_SIZE
import kotlinx.coroutines.launch

const val DECK_LIST_TEST_TAG: String = "deck-list"
const val DECK_EDITOR_TEST_TAG: String = "deck-editor"
const val DECK_NAME_TEST_TAG: String = "deck-name"
const val DECK_SAVE_TEST_TAG: String = "deck-save"
const val DECK_RESET_TEST_TAG: String = "deck-reset"
const val DECK_POWER_TEST_TAG: String = "deck-power"
const val DECK_PICK_GRID_TEST_TAG: String = "deck-pick-grid"

/** `deck-slot-<index>`, 0-based over the five slots. */
fun deckSlotTestTag(index: Int): String = "deck-slot-$index"

/** `deck-position-<index>`, 0-based over the five positions of the deck being edited. */
fun deckPositionTestTag(index: Int): String = "deck-position-$index"

/** `deck-pick-<cardId>` in the owned-cards grid of the editor. */
fun deckPickTestTag(cardId: Int): String = "deck-pick-$cardId"

/**
 * The five deck slots and the deck editor — the original's `DecksScreen`.
 *
 * ### One screen, two modes
 *
 * The original put the slot list, the owned-card pager, a card-detail list, the five deck
 * positions, a name field and two buttons on **one 1024-wide stage**, positioned by hand in
 * `draw()`. That does not fit a phone. Here the slots are a list, and picking one replaces it with
 * the editor; back goes to the slots and then to the dashboard. It is the same two things to do,
 * one at a time.
 *
 * ### What editing means
 *
 * Tap an owned card to add it, tap a card in the deck to take it out. The original was
 * slot-addressed — tap a card, then tap which of the five positions it goes in — which only matters
 * for the play order under `RULE_ORDER` and which is the same number of taps. See [Deck.plusCard]
 * for why holes are not representable.
 *
 * **Nothing is written until Save.** The editor holds its own copy, so backing out abandons the
 * edit; the original saved on Save too, but *also* saved from its Reset handler, which is where its
 * one real defect lives — see [GameSave.clearingDeck].
 *
 * @param editing which slot is open in the editor, or null for the list of five. **Hoisted**, not
 *   held here: back has to leave the editor before it leaves the screen, and the back button now
 *   belongs to the screen this is a tab of. See [CollectionScreen].
 * @param onPersist writes the profile. Goes through [ProfileSession], so the copy on screen stays
 *   the copy on disk — see there.
 */
@Composable
internal fun ColumnScope.DecksBody(
    profile: GameSave,
    catalog: CardCatalog,
    editing: Int?,
    onEdit: (Int?) -> Unit,
    onPersist: suspend (GameSave) -> Unit,
) {
    val cards = remember(catalog, profile.mode) {
        catalog.collection(profile.mode.prefix).associateBy { it.id }
    }

    if (editing == null) {
        DeckSlots(profile = profile, cards = cards, onEdit = { onEdit(it) })
    } else {
        DeckEditor(
            profile = profile,
            slot = editing,
            cards = cards,
            onPersist = onPersist,
            onDone = { onEdit(null) },
        )
    }
}

/** The five slots, empty ones included — `DecksScreen.as:128-157` always draws five. */
@Composable
private fun DeckSlots(profile: GameSave, cards: Map<Int, Card>, onEdit: (Int) -> Unit) {
    Column(
        modifier = Modifier.testTag(DECK_LIST_TEST_TAG).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (index in 0 until GameSave.MAX_DECKS) {
            val deck = profile.decks.getOrNull(index) ?: Deck(name = "", cards = emptyList())
            DeckSlotRow(
                index = index,
                deck = deck,
                cards = cards,
                onClick = { onEdit(index) },
            )
        }
    }
}

@Composable
private fun DeckSlotRow(index: Int, deck: Deck, cards: Map<Int, Card>, onClick: () -> Unit) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .testTag(deckSlotTestTag(index))
            .fillMaxWidth()
            .rowSurface()
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = deckLabel(strings, deck, index),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${deck.cards.size} / $HAND_SIZE$DOT_SEPARATOR" +
                    "${strings[StringKeys.DECK_POWER]} ${deckPower(deck, cards)}",
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

@Composable
private fun DeckEditor(
    profile: GameSave,
    slot: Int,
    cards: Map<Int, Card>,
    onPersist: suspend (GameSave) -> Unit,
    onDone: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val stored = profile.decks.getOrNull(slot) ?: Deck(name = "", cards = emptyList())
    // The editor's own copy, keyed on the slot so switching slots restarts it rather than carrying
    // the previous deck's cards across. Nothing here reaches the profile until Save.
    var draft by remember(slot) { mutableStateOf(stored) }
    var name by remember(slot) { mutableStateOf(deckLabel(strings, stored, slot)) }
    val owned = remember(profile.cards, cards) { profile.cards.mapNotNull(cards::get) }

    Column(
        modifier = Modifier.testTag(DECK_EDITOR_TEST_TAG).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_DECK_NAME) },
            label = { Text(strings[StringKeys.DECK]) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier.testTag(DECK_NAME_TEST_TAG).fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (position in 0 until HAND_SIZE) {
                val card = draft.cards.getOrNull(position)?.let(cards::get)
                // The tag sits on the *clickable* box and not on the frame inside it: a
                // `clickable` merges its descendants' semantics, so a tag one level down is
                // absorbed and unreachable from the merged tree a test drives.
                Box(
                    modifier = Modifier
                        .testTag(deckPositionTestTag(position))
                        .clickable(enabled = card != null) {
                            draft = draft.minusCardAt(position)
                        },
                ) {
                    DeckPosition(card = card)
                }
            }
        }

        Text(
            text = "${strings[StringKeys.DECK_POWER]} ${deckPower(draft, cards)}" +
                "$DOT_SEPARATOR${draft.cards.size} / $HAND_SIZE",
            color = if (draft.isComplete) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT)
            },
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.testTag(DECK_POWER_TEST_TAG),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                WideButton(strings[StringKeys.RESET_DECK], DECK_RESET_TEST_TAG) {
                    draft = draft.emptied()
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                WideButton(strings[StringKeys.SAVE], DECK_SAVE_TEST_TAG) {
                    scope.launch {
                        onPersist(profile.withDeck(slot, draft.copy(name = name.trim())))
                        onDone()
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = DeckThumbSize + 4.dp),
            modifier = Modifier
                .testTag(DECK_PICK_GRID_TEST_TAG)
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(owned, key = { it.id }) { card ->
                Box(
                    modifier = Modifier
                        .testTag(deckPickTestTag(card.id))
                        .rowSurface(selected = card.id in draft.cards)
                        .clickable(enabled = !draft.isComplete) { draft = draft.plusCard(card.id) }
                        .padding(1.dp),
                ) {
                    CardThumb(card = card, size = DeckThumbSize)
                }
            }
        }
    }
}

/**
 * One position in a deck: a card, or the empty frame.
 *
 * `CardThumb(0)` with `enabled = false` in the original, which draws the `voidCardThumb` texture.
 * There is no such texture in the imported atlases, so an empty position is an outlined box of the
 * same size — which is the whole point of drawing five of these in a row: a deck that is short a
 * card should look short a card.
 */
@Composable
internal fun DeckPosition(card: Card?) {
    if (card == null) {
        Box(modifier = Modifier.size(DeckThumbSize).rowSurface())
    } else {
        CardThumb(card = card, size = DeckThumbSize)
    }
}

/**
 * A slot's display name: what it is called, or `Deck 3` when it has never been named.
 *
 * `DecksScreen.as:154` labels an empty slot `STR_DECK` plus its 1-based index. Named here rather
 * than in the model, because the label is a translation and a save file should not carry one — see
 * [GameSave.withDeck].
 *
 * `resetDeckHandler` and `saveDeck_Handler` both reach for `STR_NEW_DECK` (`:344`, `:366`) for a
 * slot with no deck in it. That key is **in none of the four bundles**, so the original named such
 * a deck `STR_NEW_DECK`. The numbered label is what its own list already used.
 */
internal fun deckLabel(strings: Strings, deck: Deck, index: Int): String =
    deck.name.ifBlank { "${strings[StringKeys.DECK]} ${index + 1}" }

/**
 * `updateDeckPower` (`DecksScreen.as:332-339`) — the **sum of the rarities**, not of the sides.
 *
 * Which is worth stating out loud, because "deck power" reads like it should be about the numbers
 * on the cards: it is `deckPower += cardClip.data.rarity`, so a deck of five one-star cards scores
 * 5 and five five-star cards score 25. It is a measure of how rare a deck is, and the original's
 * label for it is the misleading part rather than the arithmetic.
 */
internal fun deckPower(deck: Deck, cards: Map<Int, Card>): Int =
    deck.cards.sumOf { cards[it]?.rarity ?: 0 }

/** Long enough for any deck name that will lay out in a row; the original's field had no limit. */
private const val MAX_DECK_NAME = 24

/**
 * Five of these plus a label have to fit the width of a phone.
 *
 * A little over the artwork's own 40 — these are shown next to a deck's name and power, and at 1:1
 * on a 3x display they sit lower than the text they belong to.
 */
internal val DeckThumbSize = 44.dp
