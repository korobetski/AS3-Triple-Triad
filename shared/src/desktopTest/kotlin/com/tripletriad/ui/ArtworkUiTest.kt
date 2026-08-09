package com.tripletriad.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.data.loadNpcCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.GameSave
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That the artwork reaches the screens, and that a screen survives it being absent.
 *
 * Two halves, and the second is the one worth writing down. Eleven opponents ship no portrait and
 * one item ships no icon ([UiArtTest] pins both), so **a missing image is a normal state**, not a
 * broken build. Every one of these composables therefore has to draw something in the space — a
 * monogram, or an empty plate — and a row that silently collapsed would only be noticed by
 * someone looking at a phone.
 */
@OptIn(ExperimentalTestApi::class)
class ArtworkUiTest {
    private val art = runBlocking { loadUiArt() }

    /** The profile picture `AVATAR_ID` names, on the screen that is about the profile. */
    @Test
    fun theRecordShowsTheCharactersAvatar() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromDashboard(DASHBOARD_STATS_TEST_TAG, STATS_TABLE_TEST_TAG)

        assertTrue(existsUnmerged(AVATAR_TEST_TAG), "the record should show the avatar")
    }

    /** The grid is thumbnails now, keyed by the card's own texture id. */
    @Test
    fun theCollectionGridDrawsThumbnails() = runComposeUiTest {
        val card = runBlocking { loadCardCatalog() }.ff14
            .first { it.id == GameSave.DEFAULT_CARDS.first() }
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromBar("cards", CARD_GRID_TEST_TAG)

        assertTrue(
            existsUnmerged(thumbTestTag(card.textureId)),
            "the grid should draw ${card.name} as its thumbnail",
        )
    }

    /** You should be able to see who you are about to play. */
    @Test
    fun theOpponentListShowsPortraits() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()

        assertTrue(
            existsUnmerged(portraitTestTag(TEST_OPPONENT)),
            "the row for $TEST_OPPONENT should carry its portrait",
        )
    }

    /**
     * A deck slot is a thumbnail, and an empty one still holds its place.
     *
     * A fresh character has no saved deck, so the editor opens on five empty positions — which is
     * exactly the case where a collapsing slot would go unnoticed: five nothings in a row look
     * like a screen that has not loaded yet.
     */
    @Test
    fun aDeckSlotFillsWithAThumbnailWhenACardIsPicked() = runComposeUiTest {
        val first = GameSave.DEFAULT_CARDS.first()
        val card = runBlocking { loadCardCatalog() }.ff14.first { it.id == first }
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openFromDashboard(DASHBOARD_DECKS_TEST_TAG, DECK_LIST_TEST_TAG)
        onNodeWithTag(deckSlotTestTag(0)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(DECK_EDITOR_TEST_TAG) }

        onNodeWithTag(deckPickTestTag(first)).performClick()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(thumbTestTag(card.textureId)) }
    }

    /**
     * An opponent with no portrait is drawn as their initial, not as a gap.
     *
     * Composed directly rather than reached through the Card Club, because the fallback is the
     * whole subject: navigating there would make the test depend on the ladder, the entry fee and
     * which rung comes first, none of which has anything to do with what happens when a file is
     * missing.
     */
    @Test
    fun anOpponentWithNoPortraitIsDrawnAsAMonogram() = runComposeUiTest {
        val npc = runBlocking { loadNpcCatalog() }.all.first { it.iconId == "jack" }

        setContent {
            CompositionLocalProvider(LocalUiArt provides art) {
                TripleTriadTheme {
                    NpcPortrait(npc = npc, name = "Jack")
                }
            }
        }

        waitForIdle()
        assertTrue(existsUnmerged(portraitTestTag("jack")), "the plate is drawn either way")
        assertVisible("J", "a portrait-less opponent shows their initial")
    }
}
