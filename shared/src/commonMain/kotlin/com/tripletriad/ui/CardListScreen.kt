package com.tripletriad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripletriad.data.CardCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.powerLabel

const val CARD_GRID_TEST_TAG: String = "card-grid"
const val CARD_TOTAL_TEST_TAG: String = "card-total"
const val CARD_DETAIL_TEST_TAG: String = "card-detail"
const val CARD_DETAIL_EMPTY_TEST_TAG: String = "card-detail-empty"

/** `card-cell-<id>`. Ids are per-collection, and only one collection is ever on screen. */
fun cardCellTestTag(cardId: Int): String = "card-cell-$cardId"

/**
 * The whole collection, owned and not — the original's `cardListScreen`.
 *
 * Every card in the profile's table is drawn; the ones it does not own are dimmed. That is the
 * original's arrangement (`:101-106` walks the whole card table and sets `enabled` from membership
 * of `CARDS`), and it is the point of the screen: a collection browser that showed only what you
 * have would not tell you what there is to get.
 *
 * ### Two visual departures
 *
 * - **Cards are drawn at a small [CardFace], not from a thumbnail atlas.** `CardThumb` reads
 *   `ff14_thumb_N` out of the `card_thumbs` atlases — three sprite sheets totalling 8.3 MB that
 *   duplicate artwork already imported at full size. Slicing them would cost an importer, a parser
 *   and the download; scaling the real card costs a multiply, and it shows the **sides and the
 *   rarity** too — which is what a browser is for. See [CardFace] for why the geometry is
 *   multiplied rather than the layer scaled.
 * - **Unowned cards are dimmed, not desaturated.** `CardThumb.enabled = false` applies a Starling
 *   `ColorMatrixFilter` at −1 saturation, with a `TODO : make a grey card thumbs atlas` next to it.
 *   Compose Multiplatform has no portable colour-matrix filter for a multi-layer composable —
 *   `RenderEffect` is platform-specific — so alpha carries the same one bit of information.
 *
 * @param catalog both card tables. Only the profile's own is read: card ids index whichever table
 *   `MODE` names, so showing the other collection's card for an id would be showing a different
 *   card.
 */
@Composable
internal fun CardListScreen(profile: GameSave, catalog: CardCatalog, onBack: () -> Unit) {
    val strings = LocalStrings.current
    val cards = remember(catalog, profile.mode) { catalog.collection(profile.mode.prefix) }
    val owned = remember(profile.cards) { profile.cards.toSet() }
    var selected by remember(profile.mode) { mutableStateOf<Card?>(null) }

    CharacterScaffold(profile = profile, title = strings[StringKeys.CARD_LIST], onBack = onBack) {
        Text(
            // Counted over the *table* and not over `CARDS`, so an id the profile holds that names
            // no card in its own collection cannot push the total past the collection's size.
            text = "${strings[StringKeys.OWNED]}$DOT_SEPARATOR" +
                "${cards.count { it.id in owned }} / ${cards.size}",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.testTag(CARD_TOTAL_TEST_TAG).padding(bottom = 8.dp),
        )

        CardDetail(selected)

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = ThumbWidth + 4.dp),
            modifier = Modifier
                .testTag(CARD_GRID_TEST_TAG)
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(cards, key = { it.id }) { card ->
                CardCell(
                    card = card,
                    isOwned = card.id in owned,
                    isSelected = selected?.id == card.id,
                    onClick = { selected = if (selected?.id == card.id) null else card },
                )
            }
        }
    }
}

@Composable
private fun CardCell(card: Card, isOwned: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .testTag(cardCellTestTag(card.id))
            .rowSurface(selected = isSelected)
            .clickable(onClick = onClick)
            .padding(1.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Every card is tappable, owned or not: the original made unowned thumbs untouchable, which
        // meant the description of a card you were hunting for was the one thing you could not
        // read.
        CardFace(
            card = card,
            scale = THUMB_SCALE,
            modifier = if (isOwned) Modifier else Modifier.alpha(UNOWNED_ALPHA),
        )
    }
}

/**
 * The selected card at full size, with what the original's right-hand panel showed.
 *
 * A fixed height whether or not anything is selected, so the grid does not jump under the finger
 * that just tapped it.
 */
@Composable
private fun CardDetail(card: Card?) {
    val strings = LocalStrings.current

    Box(
        modifier = Modifier.fillMaxWidth().height(DetailHeight).rowSurface().padding(8.dp),
        contentAlignment = if (card == null) Alignment.Center else Alignment.TopStart,
    ) {
        if (card == null) {
            EmptyNote(strings[StringKeys.PICK_CARD], CARD_DETAIL_EMPTY_TEST_TAG)
        } else {
            Row(
                modifier = Modifier.testTag(CARD_DETAIL_TEST_TAG).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CardFace(card = card, scale = DETAIL_SCALE)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = strings[card.nameKey],
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = cardFacts(strings, card),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // The `ff8_` bundle has names for all 110 cards and descriptions for none, so
                    // this really is absent rather than merely untranslated — leaving the key on
                    // screen would read as a defect in the port.
                    val description = "${card.nameKey}_DESC"
                    if (strings.has(description)) {
                        Text(
                            text = strings[description],
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        }
    }
}

/**
 * `Sides A 5 3 2 · Rarity ★★★`.
 *
 * The side order is top, right, bottom, left — `power[0..3]` as `CardDigits.display()` states it
 * and as `DecksScreen.as:296` prints it. Rarity is written as stars rather than drawn from the
 * `{n}stars` texture because the card beside it already carries that row, and because a star count
 * reads the same in all four languages.
 */
private fun cardFacts(strings: Strings, card: Card): String = listOf(
    "${strings[StringKeys.SIDES]} " + listOf(card.top, card.right, card.bottom, card.left)
        .joinToString(" ", transform = ::powerLabel),
    "${strings[StringKeys.RARITY]} ${"★".repeat(card.rarity)}",
).joinToString(DOT_SEPARATOR)

/** Small enough for eight columns on a phone, large enough for the digits to stay legible. */
private const val THUMB_SCALE = 0.46f
private val ThumbWidth = CardSpriteWidth * THUMB_SCALE

/** Two thirds, so the panel fits a card, three lines and a scrolling description on a phone. */
private const val DETAIL_SCALE = 0.66f
private val DetailHeight = CardSpriteHeight * DETAIL_SCALE + 20.dp

/** `adjustSaturation(-1)` in the original; alpha here. See [CardListScreen]. */
private const val UNOWNED_ALPHA = 0.28f
