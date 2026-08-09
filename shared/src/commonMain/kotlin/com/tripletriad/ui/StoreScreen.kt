package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.ShopCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import kotlinx.coroutines.launch
import kotlin.random.Random

/** The tab bar of the store screen — what is for sale, and what has been bought. */
const val STORE_TABS_TEST_TAG: String = "store-tabs"

/** The two halves of the store screen, in the order they are shown. */
internal enum class StoreTab {
    /** The shelf, and the Buy button under it. */
    SHOP,

    /** The bag, and what can be done with what is in it. */
    BAG,
}

/**
 * The shop and the bag, on one screen with two tabs.
 *
 * They are the two ends of one transaction — a pack is bought on the first tab and opened on the
 * second — and the original made that a trip through the dashboard, which is why its own shop ends
 * on a commented-out save: nothing about the flow encouraged looking at what you had just bought.
 * Buy, switch tab, open: the pack never leaves the screen.
 *
 * ### The two things this screen owns rather than its tabs
 *
 * - **Which offer is selected**, because the Buy button is in the app bar's bottom bar and not in
 *   the shelf. A button that commits has to be reachable without scrolling the list it commits
 *   from, and the snackbar that confirms the purchase is drawn above it — see [ScreenScaffold].
 * - **The unlocked-card reveal**, because [UnlockedCard] covers the whole screen and a tab is not
 *   one. [InventoryBody] reports the card upwards instead of drawing it.
 *
 * @param initial which tab to open on; the screen keeps its own selection from then on.
 * @param random the pack draw, threaded to [InventoryBody] — see there.
 */
@Composable
@Suppress("LongParameterList")
internal fun StoreScreen(
    profile: GameSave,
    catalog: CardCatalog,
    initial: StoreTab,
    onPersist: suspend (GameSave) -> Unit,
    onBack: () -> Unit,
    random: Random = Random.Default,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(initial) }

    val offers = remember(profile.mode) { ShopCatalog.offers(profile.mode) }
    val cards = remember(catalog, profile.mode) {
        catalog.collection(profile.mode.prefix).associateBy { it.id }
    }
    var selectedTag by remember(profile.mode) { mutableStateOf<String?>(null) }
    val selected = offers.firstOrNull { shopOfferTestTag(it) == selectedTag }
    val note = rememberNoteHost(SHOP_NOTE_TEST_TAG)

    // The card just drawn from a pack, while it is being shown off. `UnlockCardAnim` is the one
    // thing the original does in the bag that a line of text cannot: the player has often never
    // seen this card, and the note names it without showing it.
    var unlocked by remember(profile.mode) { mutableStateOf<Card?>(null) }

    CharacterScaffold(
        profile = profile,
        title = strings[StringKeys.SHOP],
        onBack = onBack,
        snackbar = note,
        bottomBar = if (tab != StoreTab.SHOP) {
            null
        } else {
            {
                WideButton(
                    label = selected
                        ?.let { "${strings[StringKeys.BUY]}$DOT_SEPARATOR${it.price}" }
                        ?: strings[StringKeys.BUY],
                    tag = SHOP_BUY_TEST_TAG,
                    enabled = selected?.isAffordableBy(profile) == true,
                    onClick = {
                        val offer = selected ?: return@WideButton
                        val bought = itemName(strings, offer.item, cards)
                        scope.launch {
                            onPersist(ShopCatalog.buy(profile, offer))
                            // After the write, not before: the note says the purchase happened,
                            // and a line shown while the save was in flight would be a promise.
                            note.show(strings.format(StringKeys.OBTAINED, bought))
                        }
                    },
                )
            }
        },
    ) {
        ScreenTabs(
            tabs = listOf(
                strings[StringKeys.CARD_SHOP] to screenTabTestTag("shop"),
                strings[StringKeys.INVENTORY] to screenTabTestTag("bag"),
            ),
            selected = tab.ordinal,
            onSelect = { index -> tab = StoreTab.entries[index] },
            modifier = Modifier.testTag(STORE_TABS_TEST_TAG),
        )

        when (tab) {
            StoreTab.SHOP -> ShopBody(
                profile = profile,
                offers = offers,
                cards = cards,
                selectedTag = selectedTag,
                onSelect = { selectedTag = it },
            )

            StoreTab.BAG -> InventoryBody(
                profile = profile,
                catalog = catalog,
                onPersist = onPersist,
                onUnlocked = { unlocked = it },
                random = random,
            )
        }
    }

    // Outside the scaffold, so it is drawn over the whole screen rather than inside the column
    // that lists the bag.
    unlocked?.let { card ->
        UnlockedCard(card = card) { unlocked = null }
    }
}
