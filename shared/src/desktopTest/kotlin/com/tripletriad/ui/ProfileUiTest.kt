package com.tripletriad.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.SaveRepository
import com.tripletriad.i18n.AppLocale
import com.tripletriad.model.CardCollection
import com.tripletriad.model.GameSave
import com.tripletriad.storage.InMemoryDocumentStore
import com.tripletriad.time.FixedClock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Characters: listing, creating, choosing, deleting — and that the file on disk is the same profile
 * the screen is showing.
 *
 * Driven through the real `App` with an [InMemoryDocumentStore], so what these assert about the
 * store is what a real `.sav` would contain: the same [SaveRepository] writes it and the same
 * [SaveCodec] obfuscates it.
 */
@OptIn(ExperimentalTestApi::class)
class ProfileUiTest {
    private fun store() = InMemoryDocumentStore()

    /** Decodes what is actually on "disk", so a test asserts the file and not the screen's copy. */
    private fun stored(documents: InMemoryDocumentStore): List<GameSave> =
        runBlocking { SaveRepository(documents).list().map { it.save } }

    @Test
    fun aFreshInstallHasNoCharacters() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        awaitMenu()
        onNodeWithTag(MENU_PROFILES_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_EMPTY_TEST_TAG) }

        onNodeWithTag(PROFILE_EMPTY_TEST_TAG).assertExists()
        assertFalse(exists(PROFILE_LIST_TEST_TAG), "an empty list should not draw a list")
    }

    @Test
    fun creatingACharacterWritesItAndOpensItsDashboard() = runComposeUiTest {
        val documents = store()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }

        newCharacter()

        assertEquals(1, documents.writes, "creating should have written exactly one profile")
        val saved = stored(documents).single()
        assertEquals(GameSave.DEFAULT_USERNAME, saved.username)
        assertEquals(CardCollection.FF14, saved.mode)
    }

    /** The typed name reaches the file, and the file name is derived from it. */
    @Test
    fun theTypedNameIsTheCharactersName() = runComposeUiTest {
        val documents = store()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        awaitMenu()
        onNodeWithTag(MENU_PROFILES_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_NEW_TEST_TAG) }
        onNodeWithTag(PROFILE_NEW_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_NAME_TEST_TAG) }

        onNodeWithTag(PROFILE_NAME_TEST_TAG).performTextClearance()
        onNodeWithTag(PROFILE_NAME_TEST_TAG).performTextInput(NAME)
        onNodeWithTag(PROFILE_CREATE_TEST_TAG).performClick()
        awaitDashboard()

        assertEquals(NAME, stored(documents).single().username)
        assertTrue(
            documents.stored.keys.single().startsWith(NAME.lowercase()),
            "the file should be named after the character: ${documents.stored.keys}",
        )
    }

    /**
     * **The collection is chosen at creation and it decides everything downstream.**
     *
     * The AS3 hard-codes `DATAS.MODE = 'ff14_'` in `setToDefaultValues()` and offers no way to
     * change it, so an `ff8_` profile was unreachable despite the whole second card table and 25
     * opponents shipping with the game. This is the test that says they are reachable now.
     */
    @Test
    fun choosingFf8GivesAnFf8CharacterAndFf8Opponents() = runComposeUiTest {
        val documents = store()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }

        newCharacter(CardCollection.FF8)
        openOpponents()

        assertEquals(CardCollection.FF8, stored(documents).single().mode)
        // `chocoboy` is ff8-only; `tt-master` is ff14-only. Both directions, so a list that ignored
        // the collection entirely could not pass.
        onNodeWithTag(opponentRowTestTag("chocoboy")).assertExists()
        onNodeWithTag(opponentRowTestTag(TEST_OPPONENT)).assertDoesNotExist()
    }

    @Test
    fun choosingFf14GivesFf14Opponents() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }

        newCharacter(CardCollection.FF14)
        openOpponents()

        onNodeWithTag(opponentRowTestTag(TEST_OPPONENT)).assertExists()
        onNodeWithTag(opponentRowTestTag("chocoboy")).assertDoesNotExist()
    }

    @Test
    fun aCreatedCharacterIsListedAndNamedOnTheMenu() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()

        // Back out to the menu: dashboard → characters → menu.
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }
        assertTrue(isVisible(GameSave.DEFAULT_USERNAME), "the character should be in the list")
        assertTrue(isVisible("FFXIV"), "the row should say which collection it plays")

        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        awaitMenu()

        assertTrue(
            isVisible(GameSave.DEFAULT_USERNAME),
            "the menu should name the loaded character",
        )
    }

    /**
     * Deletion is two taps: the × arms, the second confirms.
     *
     * Both halves are asserted, because a control that deleted on the first tap would pass a test
     * that only checked the profile was gone at the end.
     */
    @Test
    fun deletingTakesTwoTapsAndRemovesTheFile() = runComposeUiTest {
        val documents = store()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter()
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }

        val key = documents.stored.keys.single()
        onNodeWithTag(profileDeleteTestTag(key)).performClick()
        waitForIdle()

        assertEquals(1, stored(documents).size, "the first tap must only arm the control")
        // Armed, the × becomes the word — so the second tap is unambiguous.
        onNodeWithTag(profileDeleteTestTag(key)).assertTextEquals("Delete")

        onNodeWithTag(profileDeleteTestTag(key)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_EMPTY_TEST_TAG) }

        assertTrue(stored(documents).isEmpty(), "the profile should be gone")
    }

    @Test
    fun deletingTheLoadedCharacterUnloadsIt() = runComposeUiTest {
        val documents = store()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter()
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }

        val key = documents.stored.keys.single()
        onNodeWithTag(profileDeleteTestTag(key)).performClick()
        onNodeWithTag(profileDeleteTestTag(key)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_EMPTY_TEST_TAG) }
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        awaitMenu()

        onNodeWithTag(MENU_PROFILE_TEST_TAG).assertTextEquals(NO_CHARACTER)
    }

    @Test
    fun twoCharactersCanCoexist() = runComposeUiTest {
        val documents = store()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter(CardCollection.FF14)
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }

        onNodeWithTag(PROFILE_NEW_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_NAME_TEST_TAG) }
        onNodeWithTag(PROFILE_NAME_TEST_TAG).performTextClearance()
        onNodeWithTag(PROFILE_NAME_TEST_TAG).performTextInput(NAME)
        onNodeWithTag(collectionChoiceTestTag(CardCollection.FF8)).performClick()
        onNodeWithTag(PROFILE_CREATE_TEST_TAG).performClick()
        awaitDashboard()

        val saved = stored(documents)
        assertEquals(2, saved.size, "both characters should be on disk")
        assertEquals(
            setOf(CardCollection.FF14, CardCollection.FF8),
            saved.map { it.mode }.toSet(),
            "the two should keep their own collections",
        )
    }

    /** Choosing a listed character loads it and opens its own dashboard — and its own opponents. */
    @Test
    fun choosingAListedCharacterLoadsIt() = runComposeUiTest {
        val documents = store()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter(CardCollection.FF8)
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }

        onNodeWithTag(profileRowTestTag(documents.stored.keys.single())).performClick()
        awaitDashboard()
        openOpponents()

        onNodeWithTag(opponentRowTestTag("chocoboy")).assertExists()
    }

    /** A profile written by one launch is listed by the next. */
    @Test
    fun aStoredCharacterSurvivesARelaunch() = runComposeUiTest {
        val documents = store()
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        newCharacter()
        val written = documents.stored.toMap()

        // A second `App` over the same store is what "launching again" is, short of a new process.
        setContent {
            App(
                store = settingsFor(AppLocale.EN_US),
                documents = InMemoryDocumentStore(written),
                clock = FixedClock(),
            )
        }
        awaitMenu()
        onNodeWithTag(MENU_PROFILES_TEST_TAG).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { exists(PROFILE_LIST_TEST_TAG) }

        assertTrue(isVisible(GameSave.DEFAULT_USERNAME), "the stored character should be listed")
    }

    private companion object {
        const val NAME = "Sigfrid"

        /** `app-en_US.json`. */
        const val NO_CHARACTER = "No character yet — create one to play."
    }
}
