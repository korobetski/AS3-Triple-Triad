package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.loadStrings
import com.tripletriad.model.GameRules
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rules screen: an accordion over [HELP_RULES], and the bundle behind it.
 *
 * The second half is what earns the file. `Strings[key]` falls back to returning the key, so a rule
 * whose `_HELP` text was never imported renders as `RULE_SWAP_HELP` on a device and nowhere else —
 * the exact failure the accordion is most likely to have and the least likely to be noticed.
 */
@OptIn(ExperimentalTestApi::class)
class HelpUiTest {
    private val english = runBlocking { loadStrings(AppLocale.EN_US) }

    private fun ComposeUiTest.openHelp() {
        newCharacter()
        openFromDashboard(DASHBOARD_HELP_TEST_TAG, HELP_LIST_TEST_TAG)
    }

    /** Tap to open, tap again to close. Only one is open at a time. */
    @Test
    fun tappingARuleOpensItsTextAndTappingAgainClosesIt() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openHelp()

        assertFalse(existsUnmerged(helpTextTestTag(FIRST_RULE)), "nothing is open on arrival")

        onNodeWithTag(helpRuleTestTag(FIRST_RULE)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(helpTextTestTag(FIRST_RULE)) }

        onNodeWithTag(helpRuleTestTag(FIRST_RULE)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { !existsUnmerged(helpTextTestTag(FIRST_RULE)) }
    }

    @Test
    fun openingASecondRuleClosesTheFirst() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openHelp()

        onNodeWithTag(helpRuleTestTag(FIRST_RULE)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(helpTextTestTag(FIRST_RULE)) }
        onNodeWithTag(helpRuleTestTag(SECOND_RULE)).performClick()
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { existsUnmerged(helpTextTestTag(SECOND_RULE)) }

        assertFalse(existsUnmerged(helpTextTestTag(FIRST_RULE)), "two rules were open at once")
    }

    /** Every entry is reachable, including the ones past the first screenful. */
    @Test
    fun everyRuleHasARowOnTheScreen() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        openHelp()

        for (ruleKey in HELP_RULES) {
            onNodeWithTag(HELP_LIST_TEST_TAG)
                .performScrollToNode(hasTestTag(helpRuleTestTag(ruleKey)))
        }
    }

    /**
     * Every rule has a name and a help text in the fallback bundle.
     *
     * Three of the texts — `RULE_SAME_WALL_HELP`, `RULE_COMBO_HELP`, `RULE_ELEMENTAL_HELP` —
     * resolve to the rule's own name in all four bundles, so the original explained nothing for
     * them. That is a gap in the imported Square Enix wording rather than in this screen, and it is
     * asserted as such: they must still *resolve*, and the day someone writes a real paragraph this
     * test keeps passing.
     */
    @Test
    fun everyRuleResolvesToBothALabelAndAText() {
        val unresolved = HELP_RULES.flatMap { ruleKey ->
            listOf(ruleKey, "${ruleKey}_HELP").filter { english[it] == it }
        }
        assertTrue(unresolved.isEmpty(), "unresolved: $unresolved")
    }

    /** The list is deduplicated: `HelpScreen.as` lists `RULE_CHAOS` at both `:77` and `:84`. */
    @Test
    fun theListHasNoDuplicates() {
        assertEquals(HELP_RULES.size, HELP_RULES.toSet().size, HELP_RULES.toString())
        assertEquals(RULES_LISTED, HELP_RULES.size)
    }

    /**
     * `RULE_COMBO` is in this list and in no other.
     *
     * It is a dead constant everywhere else — nothing writes or reads a combo flag, and combo fires
     * unconditionally whenever Same, Same Wall or Plus captures. So the help screen keeps its own
     * order rather than deriving one from the rules engine, which correctly excludes it. This is
     * what says the two lists are allowed to differ on purpose.
     */
    @Test
    fun comboIsExplainedHereAndIsNotARuleTheEngineToggles() {
        assertTrue(COMBO in HELP_RULES, "the one place combo is legitimately named")
        assertFalse(
            COMBO in GameRules().activeRuleKeys(),
            "combo is not a flag; it fires whenever Same or Plus captures",
        )
    }

    private companion object {
        val FIRST_RULE = HELP_RULES.first()
        val SECOND_RULE = HELP_RULES[1]

        const val COMBO = "RULE_COMBO"

        /** `HelpScreen.as:72-89` lists eighteen, with `RULE_CHAOS` twice. */
        const val RULES_LISTED = 17
    }
}
