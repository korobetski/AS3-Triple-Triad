package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.loadCampaignCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.CardCollection
import com.tripletriad.model.GameSave
import com.tripletriad.storage.InMemoryDocumentStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two tournament ladders — `CCGroupScreen` and `GSGroupScreen`, and the match screens behind
 * them, through the real app.
 *
 * **One ladder per collection**, which is easy to get backwards: the Card Club (`cc`, seven rungs)
 * is the FF8 one and the Gold Saucer tournament (`gs`, six) the FF14 one, so a character sees
 * exactly one. Where the rungs go is `CampaignCatalogTest`'s business; what only the app can show
 * is that the entry is reachable, that the fee gates it and is really taken, and that a rung is a
 * scripted match.
 */
@OptIn(ExperimentalTestApi::class)
class CampaignUiTest {
    private val campaigns = runBlocking { loadCampaignCatalog() }
    private val goldSaucer = campaigns.byKey(GOLD_SAUCER) ?: error("no $GOLD_SAUCER campaign")
    private val english = runBlocking { loadStrings(AppLocale.EN_US) }

    /** A character who can afford the fee, which a new one cannot: [aFreshPurseCannotEnter]. */
    private fun withFee(): InMemoryDocumentStore =
        seeded(GameSave.new(createdAt = 0L).copy(mgp = goldSaucer.fee + POCKET_CHANGE))

    /**
     * A character sees its own collection's ladder and not the other's.
     *
     * `PVEScreen.as:83-96` builds each button behind its own `if (MODE == …)`; here the catalogue
     * has already filtered, so what this pins is that the filter runs at all. Getting it wrong
     * would offer an FF14 player a ladder of FF8 opponents they cannot meet.
     */
    @Test
    fun eachCollectionSeesOnlyItsOwnLadder() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter(CardCollection.FF14)
        openOpponents()

        assertTrue(exists(TUTORIAL_ROW_TEST_TAG), "the lesson is in both collections")
        assertTrue(exists(campaignRowTestTag(GOLD_SAUCER)), "the Gold Saucer is the ff14 ladder")
        assertFalse(exists(campaignRowTestTag(CARD_CLUB)), "the Card Club is the ff8 one")
    }

    /** And the other way round, so neither assertion above can be passing by accident. */
    @Test
    fun anFf8CharacterSeesTheCardClub() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter(CardCollection.FF8)
        openOpponents()

        assertTrue(exists(campaignRowTestTag(CARD_CLUB)), "the Card Club is the ff8 ladder")
        assertFalse(exists(campaignRowTestTag(GOLD_SAUCER)), "the Gold Saucer is the ff14 one")
    }

    /**
     * A new character cannot enter: 100 MGP against a 500 fee.
     *
     * `startCampaign.isEnabled = (Game.PROFILE_DATAS.MGP >= 500)` — disabled, not hidden, so the
     * reason is readable next to the price. This also pins the two numbers against each other: if
     * the starting purse ever covered the fee, the gate would stop meaning anything.
     */
    @Test
    fun aFreshPurseCannotEnter() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openOpponents()
        onNodeWithTag(campaignRowTestTag(GOLD_SAUCER)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CAMPAIGN_LIST_TEST_TAG) }

        onNodeWithTag(CAMPAIGN_START_TEST_TAG).assertIsNotEnabled()
    }

    /** Every rung is listed before a coin is spent — what the 500 buys, in the order it comes. */
    @Test
    fun theLadderShowsItsRungsBeforeTheFee() = runComposeUiTest {
        val documents = withFee()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openLadder(documents)

        onNodeWithTag(CAMPAIGN_START_TEST_TAG).assertIsEnabled()
        assertTrue(isVisible("${goldSaucer.fee}"), "the price should be next to the button")
        // The first rung by name, resolved the way the screen resolves it. Only the first: six rows
        // do not all fit, and a test that scrolled would be asserting the scaffold, not the ladder.
        assertVisible(
            english[goldSaucer.steps.first().npc.nameKey],
            "the ladder should open with its first opponent",
        )
    }

    /**
     * Start takes the fee, and the ladder opens on its first rung.
     *
     * Read off the file rather than off the screen: `MGP -= 500` has to survive the save, because
     * paying again after a defeat is the only thing that makes a ladder a stake.
     */
    @Test
    fun startingALadderChargesTheFee() = runComposeUiTest {
        val documents = withFee()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openLadder(documents)

        onNodeWithTag(CAMPAIGN_START_TEST_TAG).performClick()
        settleDeck()

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { storedSave(documents).mgp == POCKET_CHANGE }
        assertEquals(POCKET_CHANGE, storedSave(documents).mgp, "the fee should have been taken")
        assertTrue(existsUnmerged(CAMPAIGN_STEP_TEST_TAG), "the ladder should say which rung it is")
    }

    /**
     * A ladder match is scripted, so it offers Next Match where an ordinary one offers Rematch.
     *
     * `CCGroupRematchPanel` builds its first button behind `if (_params.NEXT_STEP < 7)` and labels
     * it Next Match — replacing Rematch rather than joining it, which is also what keeps a rung
     * from being replayable for its reward. That a scripted match is never *submitted* is
     * `MatchTranscriptTest`'s claim; this app has no server attached to observe it through.
     */
    @Test
    fun aRungOffersTheNextRungRatherThanARematch() = runComposeUiTest {
        val documents = withFee()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        openLadder(documents)
        onNodeWithTag(CAMPAIGN_START_TEST_TAG).performClick()
        settleDeck()

        playOut()

        assertFalse(isVisible("Rematch"), "a scripted match is never replayed in place")
        assertTrue(exists(MATCH_DONE_TEST_TAG), "the way out is always offered")
    }

    /** Loads the seeded character and opens the Gold Saucer's entry screen. */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.openLadder(documents: InMemoryDocumentStore) {
        loadCharacter(documents)
        openOpponents()
        onNodeWithTag(campaignRowTestTag(GOLD_SAUCER)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(CAMPAIGN_LIST_TEST_TAG) }
    }

    private companion object {
        /** Six rungs, FF14, and the one whose title key only `fr_FR` defines. */
        const val GOLD_SAUCER = "gs"

        /** Seven rungs, FF8, and the one whose title key no bundle defines at all. */
        const val CARD_CLUB = "cc"

        /** What is left after the fee — small enough that no reward can produce it by chance. */
        const val POCKET_CHANGE = 7
    }
}
