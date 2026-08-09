package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tripletriad.data.CardCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave

/** The tab bar of the cards screen — the collection, and the decks built out of it. */
const val COLLECTION_TABS_TEST_TAG: String = "collection-tabs"

/**
 * The two halves of the cards screen, in the order they are shown.
 *
 * An enum and not two booleans because it is the *destination* a caller asks for: the dashboard's
 * two entries and, later, the navigation bar all open this screen at one tab or the other.
 */
internal enum class CollectionTab {
    /** Everything in the profile's table, owned and not. */
    CARDS,

    /** The five deck slots, and the editor behind one. */
    DECKS,
}

/**
 * The collection and the decks, on one screen with two tabs.
 *
 * ### Why they were merged
 *
 * They were two of the dashboard's nine entries and they are one activity. A deck is built by
 * picking from the collection, so the question the browser answers — *what do I have?* — is the
 * question the editor asks, and the original made you leave one screen and enter another to carry
 * the answer in your head. `DecksScreen.as` even drew its own owned-card pager for exactly this
 * reason: it needed the collection, so it grew a second copy of it.
 *
 * The merge is also what makes a four-destination navigation bar possible without hiding anything:
 * eight entries do not fit a bar and six do not either, and these two plus the shop and the bag are
 * the four that were pairs all along.
 *
 * ### Back, which is the one thing this screen owns
 *
 * The deck editor is a state of the decks tab and not a screen, so back has to leave the editor
 * before it leaves the screen. That check lived in `DecksScreen` while it had its own app bar; the
 * bar is here now, so [DecksBody] takes its `editing` slot as a parameter and this decides what
 * back means. Nothing else about either tab moved.
 *
 * @param initial which tab to open on. The screen keeps its own selection from then on: a player
 *   who switched to the decks and went to play should come back to the decks.
 */
@Composable
internal fun CollectionScreen(
    profile: GameSave,
    catalog: CardCatalog,
    initial: CollectionTab,
    onPersist: suspend (GameSave) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    var tab by remember { mutableStateOf(initial) }
    var editing by remember { mutableStateOf<Int?>(null) }

    CharacterScaffold(
        profile = profile,
        title = strings[StringKeys.CARDS],
        onBack = { if (editing != null) editing = null else onBack() },
        // The one screen that lays out two panes, so the one that asks for the wider column. See
        // [WideContentMaxWidth] for why the other screens do not get it for free.
        wide = true,
    ) {
        ScreenTabs(
            tabs = listOf(
                strings[StringKeys.CARD_LIST] to screenTabTestTag("cards"),
                strings[StringKeys.CARD_DECKS] to screenTabTestTag("decks"),
            ),
            selected = tab.ordinal,
            onSelect = { index -> tab = CollectionTab.entries[index] },
            modifier = Modifier.testTag(COLLECTION_TABS_TEST_TAG),
        )

        when (tab) {
            CollectionTab.CARDS -> CardListBody(profile = profile, catalog = catalog)

            CollectionTab.DECKS -> DecksBody(
                profile = profile,
                catalog = catalog,
                editing = editing,
                onEdit = { editing = it },
                onPersist = onPersist,
            )
        }
    }
}
