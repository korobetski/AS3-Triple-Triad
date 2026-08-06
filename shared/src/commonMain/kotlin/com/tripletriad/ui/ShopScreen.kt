package com.tripletriad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.ShopCatalog
import com.tripletriad.data.ShopOffer
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import kotlinx.coroutines.launch

const val SHOP_LIST_TEST_TAG: String = "shop-list"
const val SHOP_BUY_TEST_TAG: String = "shop-buy"

/** `shop-offer-<slug>` — the offer's item, since no item is on either shelf twice. */
fun shopOfferTestTag(offer: ShopOffer): String = "shop-offer-${itemSlug(offer.item)}"

/**
 * What the shop sells — the original's `shopScreen`.
 *
 * Tap an offer to select it, then Buy. That is the original's arrangement, and Buy is enabled by
 * affordability exactly as `shopList_changeHandler` (`:152-155`) computed it.
 *
 * What is on the shelf is [ShopCatalog], transcribed from the two static tables; the prices,
 * the order and the asymmetry between the collections are all documented there rather than here.
 *
 * ### The original's two defects, both fixed in the data layer
 *
 * - `buyButton_triggeredHandler` **subtracts the price and then checks whether it could be paid**
 *   (`:144-146`), so a profile could be taken below its own means; the check only decided whether
 *   the button stayed lit. [ShopCatalog.buy] is one operation that either happens or does not.
 * - It ends on a commented-out `//Save.save(Game.PROFILE_DATAS)` (`:149`), so **a purchase was
 *   never written**: the MGP and the item were both gone on quit. Persisted here, through
 *   [ProfileSession] like every other mutation.
 *
 * The unaffordable rows are shown greyed rather than hidden, which is the original's behaviour and
 * the right one: a card costing a million MGP is a goal, and a shop that hid it would only ever
 * show what the player has already outgrown.
 */
@Composable
internal fun ShopScreen(
    profile: GameSave,
    catalog: CardCatalog,
    onPersist: suspend (GameSave) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val offers = remember(profile.mode) { ShopCatalog.offers(profile.mode) }
    val cards = remember(catalog, profile.mode) {
        catalog.collection(profile.mode.prefix).associateBy { it.id }
    }
    var selectedTag by remember(profile.mode) { mutableStateOf<String?>(null) }
    val selected = offers.firstOrNull { shopOfferTestTag(it) == selectedTag }

    CharacterScaffold(profile = profile, title = strings[StringKeys.CARD_SHOP], onBack = onBack) {
        LazyColumn(
            modifier = Modifier.testTag(SHOP_LIST_TEST_TAG).fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(offers, key = ::shopOfferTestTag) { offer ->
                OfferRow(
                    offer = offer,
                    cards = cards,
                    isAffordable = offer.isAffordableBy(profile),
                    isSelected = shopOfferTestTag(offer) == selectedTag,
                    onClick = {
                        selectedTag = shopOfferTestTag(offer).takeIf { it != selectedTag }
                    },
                )
            }
        }

        WideButton(
            label = selected
                ?.let { "${strings[StringKeys.BUY]}$DOT_SEPARATOR${it.price}" }
                ?: strings[StringKeys.BUY],
            tag = SHOP_BUY_TEST_TAG,
            enabled = selected?.isAffordableBy(profile) == true,
            onClick = {
                val offer = selected ?: return@WideButton
                scope.launch { onPersist(ShopCatalog.buy(profile, offer)) }
            },
        )
    }
}

@Composable
private fun OfferRow(
    offer: ShopOffer,
    cards: Map<Int, Card>,
    isAffordable: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    // Dimmed rather than disabled: an offer out of reach is still worth reading, and it is still
    // selectable so the Buy button can say what it would cost.
    val alpha = if (isAffordable) 1f else UNAFFORDABLE_ALPHA

    Row(
        modifier = Modifier
            .testTag(shopOfferTestTag(offer))
            .fillMaxWidth()
            .rowSurface(selected = isSelected)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemCard(offer.item, cards)?.let { CardFace(card = it, scale = SHELF_CARD_SCALE) }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = itemName(strings, offer.item, cards),
                color = Color.White.copy(alpha = alpha),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = strings[offer.item.descriptionKey],
                color = Color.White.copy(alpha = alpha * DESCRIPTION_ALPHA),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = "${offer.price} ${strings[StringKeys.MGP]}",
            color = if (isAffordable) BlueEdge else Color.White.copy(alpha = alpha),
            fontSize = 13.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The same one-line row height as the bag. */
private const val SHELF_CARD_SCALE = 0.3f

/** `isEnabled = false` in the original, which greyed the whole renderer. */
private const val UNAFFORDABLE_ALPHA = 0.4f
private const val DESCRIPTION_ALPHA = 0.6f
