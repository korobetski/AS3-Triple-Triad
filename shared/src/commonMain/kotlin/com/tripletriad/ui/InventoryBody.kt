package com.tripletriad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.Inventory
import com.tripletriad.data.ItemUse
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.Item
import com.tripletriad.ui.theme.LocalTtoColors
import kotlinx.coroutines.launch
import kotlin.random.Random

const val INVENTORY_LIST_TEST_TAG: String = "inventory-list"
const val INVENTORY_EMPTY_TEST_TAG: String = "inventory-empty"
const val INVENTORY_USE_TEST_TAG: String = "inventory-use"
const val INVENTORY_SELL_TEST_TAG: String = "inventory-sell"
const val INVENTORY_DISCARD_TEST_TAG: String = "inventory-discard"

/** The line saying what using something did. Absent until something has been used. */
const val INVENTORY_NOTE_TEST_TAG: String = "inventory-note"

/** `inventory-row-<slug>` — see [itemSlug] for why the slug and not the bag position. */
fun inventoryRowTestTag(item: Item): String = "inventory-row-${itemSlug(item)}"

/**
 * The bag — the original's `InventoryScreen`.
 *
 * Tap an entry to select it, then Use, Sell or Discard it. That is the original's arrangement: a
 * list with a three-button footer whose buttons enable themselves from the selected item's own
 * flags (`listHandler`, `:104-117`). The flags are [Item.useable], [Item.sellable] and
 * [Item.dropable], and they are per-kind constants — see [Item].
 *
 * ### Four departures, each with a reason
 *
 * - **There is no Sort button.** `sortBtnHandler` re-ran `sortBag()`, which existed because the bag
 *   could get out of order and hold two rows of the same item — the AS3 pushed unconditionally from
 *   three different places. [Inventory.add] stacks and sorts on every insert, so the state the
 *   button repaired is unreachable and a control that can only ever do nothing is worse than none.
 * - **Discard asks twice.** `dropBtnHandler` opens on `// TODO : afficher une Alert` and then
 *   discards immediately; the bag is the only place in the game where a tap destroys something with
 *   no way back. The second tap is the alert the original meant to write, in the shape the
 *   character list already uses for deletion.
 * - **Items are named, not iconised.** `ItemIcon` resolves `potionItem`, `booster_pack_icon` and
 *   `card_r{n}_icon` out of the UI atlas, which `tools/import_card_art.py` does not import — it
 *   imports the card art. A card item draws its actual card instead, which is more information than
 *   the icon carried.
 * - **Every write goes through [ProfileSession].** The original saved from `sellBtnHandler` and
 *   `dropBtnHandler` and **not** from `useBtnHandler` — opening a pack or drinking a potion was
 *   persisted only by the `sortBag()` call at the end of the handler, which saves as a side effect
 *   (`:200`). One path here, so a use cannot be the one operation that is lost.
 *
 * @param onUnlocked a card that has just entered the collection, to be shown off. Reported upwards
 *   rather than drawn here because [UnlockedCard] covers the **whole screen**, and this is one tab
 *   of one — a full-screen overlay placed inside a column is a very tall column entry.
 * @param random the pack draw. Injected for the same reason [Inventory.use] takes one: a drop that
 *   cannot be pinned cannot be asserted on.
 */
@Composable
internal fun ColumnScope.InventoryBody(
    profile: GameSave,
    catalog: CardCatalog,
    onPersist: suspend (GameSave) -> Unit,
    onUnlocked: (Card) -> Unit,
    random: Random = Random.Default,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val cards = remember(catalog, profile.mode) {
        catalog.collection(profile.mode.prefix).associateBy { it.id }
    }
    val owned = remember(profile.cards) { profile.cards.toSet() }

    // The selection is held as an [itemKey] and looked up in the *current* bag on every
    // composition, so selling one of three leaves the same row selected and emptying the stack
    // deselects it. Holding the `Item` itself would keep a row whose stack no longer exists.
    var selectedKey by remember(profile.mode) { mutableStateOf<Item?>(null) }
    var note by remember(profile.mode) { mutableStateOf<String?>(null) }
    var armed by remember { mutableStateOf(false) }

    val selected = profile.bag.firstOrNull { itemKey(it) == selectedKey }

    note?.let { EmptyNote(it, INVENTORY_NOTE_TEST_TAG) }

    if (profile.bag.isEmpty()) {
        EmptyNote(strings[StringKeys.EMPTY_BAG], INVENTORY_EMPTY_TEST_TAG)
    } else {
        LazyColumn(
            modifier = Modifier
                .testTag(INVENTORY_LIST_TEST_TAG)
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(profile.bag, key = { itemSlug(it) }) { item ->
                ItemRow(
                    item = item,
                    cards = cards,
                    refusal = useRefusal(strings, item, owned),
                    isSelected = itemKey(item) == selectedKey,
                    onClick = {
                        selectedKey = itemKey(item).takeIf { it != selectedKey }
                        armed = false
                    },
                )
            }
        }
    }

    selected?.let { item ->
        BagActions(
            item = item,
            isArmed = armed,
            canUse = item.useable && useRefusal(strings, item, owned) == null,
            onUse = {
                val outcome = Inventory.use(profile, item, random)
                note = useNote(strings, outcome, cards)
                armed = false
                // Only a card *entering the collection* is revealed, which is the single
                // branch `useBtnHandler` plays it in (`:236-245`). Opening a pack yields
                // another bag item rather than a card, and showing it here would announce a
                // card the player does not own yet.
                (outcome as? ItemUse.CardDrawn)?.let { cards[it.cardId] }?.let(onUnlocked)
                scope.launch { onPersist(outcome.save) }
            },
            onSell = {
                armed = false
                scope.launch { onPersist(Inventory.sell(profile, item)) }
            },
            onDiscard = {
                if (armed) {
                    armed = false
                    note = null
                    scope.launch { onPersist(Inventory.remove(profile, item)) }
                } else {
                    armed = true
                }
            },
        )
    }
}

/**
 * One bag entry: what it is, how many, and what it is worth.
 *
 * A card item draws its card beside the name — see [InventoryScreen] for why there are no icons.
 *
 * @param refusal why Use is greyed out, shown on the row rather than only implied by a dead button.
 *   [useRefusal] is the one case where the item's own flag says yes and the screen says no.
 */
@Composable
private fun ItemRow(
    item: Item,
    cards: Map<Int, Card>,
    refusal: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .testTag(inventoryRowTestTag(item))
            .fillMaxWidth()
            .rowSurface(selected = isSelected)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // A card item shows the card; everything else shows the icon `Item.iconId` has named
        // since Phase 2 and that nothing has drawn until now — the booster's own tribe pack, the
        // rarity plate, the two boosts.
        val card = itemCard(item, cards)
        if (card != null) {
            CardThumb(card = card)
        } else {
            ItemIcon(iconId = item.iconId, description = itemName(strings, item, cards))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = itemName(strings, item, cards),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = itemFacts(strings, item, refusal),
                color = if (refusal == null) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT)
                } else {
                    LocalTtoColors.current.transient
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // `×1` is drawn too. A stack column that appears only above one is a column that shifts the
        // name every time a purchase lands.
        Text(
            text = "×${item.stack}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Use, Sell and Discard, each enabled by the selected item's own flags. */
@Composable
private fun BagActions(
    item: Item,
    isArmed: Boolean,
    canUse: Boolean,
    onUse: () -> Unit,
    onSell: () -> Unit,
    onDiscard: () -> Unit,
) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            WideButton(
                label = strings[StringKeys.USE],
                tag = INVENTORY_USE_TEST_TAG,
                enabled = canUse,
                onClick = onUse,
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            WideButton(
                // Only a card item is sellable, and its price is `id × 4` — see [CardItem.value].
                label = "${strings[StringKeys.SELL]} ${item.value}",
                tag = INVENTORY_SELL_TEST_TAG,
                enabled = item.sellable && item.value > 0,
                onClick = onSell,
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            WideButton(
                // The armed label is the confirmation: the button says what the second tap does.
                label = if (isArmed) {
                    "${strings[StringKeys.DISCARD]} ?"
                } else {
                    strings[StringKeys.DISCARD]
                },
                tag = INVENTORY_DISCARD_TEST_TAG,
                enabled = item.dropable,
                onClick = onDiscard,
            )
        }
    }
}

/**
 * What a use did, in one line.
 *
 * [ItemUse.PackOpened] is the only one that needs saying — the others are visible in the bag and in
 * the character bar the moment the profile is written. A pack yields a *bag entry*, so without this
 * line the pack simply vanishes and a card appears further up a scrolled list.
 */
private fun useNote(strings: Strings, outcome: ItemUse, cards: Map<Int, Card>): String? =
    when (outcome) {
        is ItemUse.PackOpened -> strings.format(
            StringKeys.OBTAINED,
            cards[outcome.cardId]?.let { strings[it.nameKey] } ?: "#${outcome.cardId}",
        )

        is ItemUse.CardDrawn -> strings.format(
            StringKeys.OBTAINED,
            cards[outcome.cardId]?.let { strings[it.nameKey] } ?: "#${outcome.cardId}",
        )

        is ItemUse.BoonRaised, is ItemUse.NotUseable -> null
    }

/** `Sells for 52  ·  already owned`, with whichever halves apply. */
private fun itemFacts(strings: Strings, item: Item, refusal: String?): String = buildList {
    if (item.sellable && item.value > 0) add("${strings[StringKeys.SELL]} ${item.value}")
    refusal?.let(::add)
}.joinToString(DOT_SEPARATOR)
