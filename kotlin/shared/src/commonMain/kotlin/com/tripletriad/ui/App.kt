package com.tripletriad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.model.CardColor

private val Backdrop = Color(0xFF14161C)

/** Test tags used by `shared/src/desktopTest`. */
const val CARD_TEST_TAG: String = "flippable-card"
const val NEXT_CARD_TEST_TAG: String = "next-card"
const val CATALOG_SUMMARY_TEST_TAG: String = "catalog-summary"

@Composable
fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize(), color = Backdrop) {
            // `produceState` runs the suspending load once and republishes when it
            // finishes; null is the loading state.
            val catalog by produceState<CardCatalog?>(initialValue = null) {
                value = loadCardCatalog()
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Triple Triad — Kotlin Multiplatform PoC",
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))

                val loaded = catalog
                if (loaded == null) {
                    Text(
                        text = "loading cards…",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        modifier = Modifier.testTag(CATALOG_SUMMARY_TEST_TAG),
                    )
                } else {
                    CardBrowser(loaded)
                }
            }
        }
    }
}

/**
 * The one interactive screen of the PoC: a card out of the loaded catalog that flips
 * when tapped, and a way to step to the next card.
 */
@Composable
private fun CardBrowser(catalog: CardCatalog) {
    // The FF8 collection first, because its card 1 (Geezard, 1/4/1/5) is the canonical
    // Triple Triad example.
    val cards = remember(catalog) { catalog.ff8 + catalog.ff14 }
    var index by remember { mutableStateOf(0) }
    var owner by remember { mutableStateOf(CardColor.BLUE) }
    var flips by remember { mutableStateOf(0) }

    val card = cards[index]

    Text(
        text = "catalog: ${cards.size} cards " +
            "(ff14 ${catalog.ff14.size} / ff8 ${catalog.ff8.size})",
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 12.sp,
        modifier = Modifier.testTag(CATALOG_SUMMARY_TEST_TAG),
    )
    Spacer(Modifier.height(16.dp))

    // `FlippableCard` owns the flip; it re-keys on `card.id`, so stepping to the next
    // card resets it to the JSON owner (blue) on its own. The `owner` state here only
    // drives the label below.
    FlippableCard(
        card = card,
        modifier = Modifier.testTag(CARD_TEST_TAG),
        onOwnerChanged = {
            owner = it
            flips++
        },
    )

    Spacer(Modifier.height(16.dp))
    Text(
        text = "#${card.id} ${card.collection}${card.name}  " +
            "${card.top}/${card.right}/${card.bottom}/${card.left}",
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = "Tap the card to flip it.",
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 13.sp,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "owner: ${owner.name.lowercase()}   ·   flips: $flips",
        color = owner.edge,
        fontSize = 13.sp,
    )
    Spacer(Modifier.height(14.dp))
    Text(
        text = "next card ▸",
        color = Color.White,
        fontSize = 13.sp,
        modifier = Modifier
            .testTag(NEXT_CARD_TEST_TAG)
            .clickable {
                index = (index + 1) % cards.size
                // A new card arrives face-up owned by blue, as it would from a hand.
                owner = CardColor.BLUE
            }
            .padding(8.dp),
    )
}
