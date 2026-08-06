package com.tripletriad.ui

import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.BoosterItem
import com.tripletriad.model.Card
import com.tripletriad.model.CardItem
import com.tripletriad.model.Item
import com.tripletriad.model.MiscItem
import com.tripletriad.model.PotionItem

/**
 * What to call an item on screen.
 *
 * Its own function because the bag and the shop name the same four kinds and would otherwise each
 * carry their own four-branch `when` — and because a **card item cannot name itself**: its label is
 * the card's name, which lives in the catalogue and not in the bag entry (see [CardItem]).
 *
 * `CardItem.as:19-22` composes the label as `STR_CARD` plus the card's name, in an order that
 * differs between English and French. That composition is not reproduced: the card's own name is
 * enough, and a word-order rule expressed as an `if (language == 'fr_FR')` inside a display class
 * is exactly what a `{0}` placeholder exists to replace — but no such string is in the imported
 * bundles to use, so the plain name it is.
 *
 * @param cards the profile's collection, by id. An id that names nothing yields `#id`, which is
 *   visible and greppable rather than blank: a bag holding an id outside its own collection is a
 *   corrupt save, not a state worth hiding.
 */
internal fun itemName(strings: Strings, item: Item, cards: Map<Int, Card>): String = when (item) {
    is CardItem -> cards[item.cardId]?.let { strings[it.nameKey] } ?: "#${item.cardId}"
    is BoosterItem -> strings[item.boosterType.nameKey]
    is PotionItem -> strings[item.potionType.nameKey]
    is MiscItem -> strings[StringKeys.UNKNOWN_ITEM]
}

/**
 * A stable identity for a bag entry, ignoring how many are held.
 *
 * A row's selection has to survive the stack changing under it — selling one of three must leave
 * the same row selected — and [Item] is a data class whose `equals` includes [Item.stack]. This is
 * the same key [com.tripletriad.data.Inventory] stacks on, for the same reason.
 */
internal fun itemKey(item: Item): Item = item.withStack(1)

/**
 * A short printable identity: `card-13`, `booster-GOLD`, `potion-MGP`, `misc`.
 *
 * What a `LazyColumn` key and a test tag need, which [itemKey] cannot be — it is an object. Derived
 * from the same fields, so two entries share a slug exactly when they would share a stack.
 *
 * The AS3 addressed a row by its **position in `BAG`** (`Item.bagIndex`, assigned during
 * `refreshList`) and that is the one thing not reproduced: selling from a stack re-sorts the bag,
 * so the index a handler captured could name a different item by the time it ran.
 */
internal fun itemSlug(item: Item): String = when (item) {
    is CardItem -> "card-${item.cardId}"
    is BoosterItem -> "booster-${item.boosterType.name}"
    is PotionItem -> "potion-${item.potionType.name}"
    is MiscItem -> "misc"
}

/** The card a [CardItem] stands for, or null for anything else. Null too if the id names none. */
internal fun itemCard(item: Item, cards: Map<Int, Card>): Card? =
    (item as? CardItem)?.let { cards[it.cardId] }

/**
 * Why the bag's Use control is refused for [item], or null when it is not.
 *
 * The one non-obvious case is a card already in the collection: `InventoryScreen.as:111-113`
 * disables Use for it after enabling it from `item.useable`, so the flag says yes and the screen
 * says no. That is right — using it would consume the card to grant something the profile has — and
 * it is the only reason the port has to state out loud, because
 * [com.tripletriad.data.Inventory.use] would happily consume it (see there).
 */
internal fun useRefusal(strings: Strings, item: Item, owned: Set<Int>): String? = when {
    item is CardItem && item.cardId in owned -> strings[StringKeys.ALREADY_OWNED]
    else -> null
}
