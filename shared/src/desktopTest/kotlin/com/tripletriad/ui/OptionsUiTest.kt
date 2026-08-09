package com.tripletriad.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.settings.InMemorySettingsStore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The options screen, through the real [App] and a real settings store.
 *
 * The point of going through the store rather than a fake holder: "the option changed" and "the
 * option was written to disk" are different claims, and only one of them survives a relaunch.
 */
@OptIn(ExperimentalTestApi::class)
class OptionsUiTest {
    @Test
    fun pickingALanguageRedrawsTheAppInItImmediately() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openOptions()

        onNodeWithTag(optionsLanguageTestTag(AppLocale.FR_FR)).performClick()
        waitForIdle()

        // Same screen, now in French — the confirmation is the screen itself, which is why there
        // is no "settings saved" toast.
        assertTrue(isVisible("Langue"), "the language label did not change")
        // And the shell around it, which is the half a screen-local assertion would have missed.
        assertTrue(isVisible("Options"), "the scaffold title did not change")
    }

    @Test
    fun aLanguageChangeIsWrittenToTheStore() = runComposeUiTest {
        val store = InMemorySettingsStore("""{"language":"en_US"}""")
        setContent { App(store = store) }
        openOptions()

        onNodeWithTag(optionsLanguageTestTag(AppLocale.JA_JA)).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { store.writes > 0 }

        assertTrue(
            store.stored.orEmpty().contains("\"ja_JA\""),
            "the store still holds: ${store.stored.orEmpty()}",
        )
    }

    @Test
    fun theChangeSurvivesLeavingAndReopeningTheScreen() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openOptions()
        onNodeWithTag(optionsLanguageTestTag(AppLocale.DE_DE)).performClick()
        waitForIdle()

        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitForIdle()
        onNodeWithTag(MENU_OPTIONS_TEST_TAG).performClick()
        waitForIdle()

        assertTrue(isVisible("Sprache"), "the German label is gone, so the choice was lost")
    }

    /**
     * The menu is the visible proof that a language change reaches the *whole* tree and not just
     * the screen that made it.
     */
    @Test
    fun theMenuFollowsTheLanguageChosenInTheOptions() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openOptions()
        onNodeWithTag(optionsLanguageTestTag(AppLocale.FR_FR)).performClick()
        waitForIdle()
        onNodeWithTag(SCREEN_BACK_TEST_TAG).performClick()
        waitForIdle()

        onNodeWithTag(MENU_PLAY_TEST_TAG).assertTextEquals("Jouer")
    }

    @Test
    fun aVolumeChangeIsClampedPersistedAndShownAsAPercentage() = runComposeUiTest {
        val store = InMemorySettingsStore("""{"language":"en_US","background_volume":1.0}""")
        setContent { App(store = store) }
        openOptions()

        // Dragged to the far left, which is 0. Using the semantics action rather than a swipe
        // keeps this independent of where the slider ended up being laid out.
        onNodeWithTag(OPTIONS_BACKGROUND_VOLUME_TEST_TAG)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        waitForIdle()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { store.writes > 0 }

        assertTrue(isVisible("0%"), "the percentage did not follow the slider")
        assertTrue(
            store.stored.orEmpty().contains("\"background_volume\": 0.0"),
            "the store holds: ${store.stored.orEmpty()}",
        )
    }

    /** Says out loud that the sliders do nothing yet, rather than letting them look broken. */
    @Test
    fun theAudioSectionAdmitsNothingPlaysYet() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openOptions()

        assertTrue(isVisible("saved, but nothing plays yet"))
    }

    /** Both AS3 labels, so a rename in the bundles cannot silently blank the section. */
    @Test
    fun bothVolumesAreOnTheScreenWithTheirAs3Labels() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openOptions()

        assertTrue(isVisible("Background Volume"))
        assertTrue(isVisible("Noise Volume"))
    }

    private fun ComposeUiTest.openOptions() {
        awaitMenu()
        onNodeWithTag(MENU_OPTIONS_TEST_TAG).performClick()
        waitForIdle()
    }
}
