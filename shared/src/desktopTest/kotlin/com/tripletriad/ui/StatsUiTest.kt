package com.tripletriad.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.GameSave
import com.tripletriad.model.Stats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The character's record and its achievements — the original's `profileScreen`.
 *
 * Two of the assertions here are about things the original could not show at all: a win *rate*, and
 * an achievement the profile has not earned yet. See [StatsScreen] for why both are here.
 */
@OptIn(ExperimentalTestApi::class)
class StatsUiTest {
    private fun ComposeUiTest.openStats() {
        openFromDashboard(DASHBOARD_STATS_TEST_TAG, STATS_TABLE_TEST_TAG)
    }

    /** A fresh profile reads zero everywhere, and 0% rather than a division by zero. */
    @Test
    fun aFreshCharacterReadsZeroWithoutDividingByZero() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openStats()

        onNodeWithTag(statsRowTestTag(StringKeys.MATCHES)).assertTextEquals("0")
        onNodeWithTag(statsRowTestTag(StringKeys.WIN_RATE)).assertTextEquals("0%")
        onNodeWithTag(statsRowTestTag(StringKeys.MGP))
            .assertTextEquals("${GameSave.STARTING_MGP}")
    }

    /** The counters are the profile's, and forfeits are derived from the two match totals. */
    @Test
    fun theCountersComeFromTheProfile() = runComposeUiTest {
        val played = GameSave.new(createdAt = 0L).copy(
            stats = Stats(wins = 3, defeats = 1, draws = 0),
            startedMatches = 6,
            endedMatches = 4,
        )
        val documents = seeded(played)
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openStats()

        onNodeWithTag(statsRowTestTag(StringKeys.WINS)).assertTextEquals("3")
        onNodeWithTag(statsRowTestTag(StringKeys.DEFEATS)).assertTextEquals("1")
        onNodeWithTag(statsRowTestTag(StringKeys.MATCHES)).assertTextEquals("4")
        // `STARTED_MATCHES - ENDED_MATCHES`, which is never stored — see [GameSave.forfeits].
        onNodeWithTag(statsRowTestTag(StringKeys.FORFEITS)).assertTextEquals("2")
        onNodeWithTag(statsRowTestTag(StringKeys.WIN_RATE)).assertTextEquals("75%")
    }

    /**
     * Every achievement is listed, earned or not, with how far along the profile is.
     *
     * `profileScreen.as:210-220` walks `PROFILE_DATAS.ACHIEVEMENTS`, so an unearned one was
     * invisible and the screen could not say what there was to aim at.
     */
    @Test
    fun unearnedAchievementsAreListedWithTheirProgress() = runComposeUiTest {
        setContent { App(store = settingsFor(AppLocale.EN_US)) }
        newCharacter()
        openStats()

        // `ac-td1` wants ten cards and a fresh profile has five, so it is both unearned and
        // measurably close — the pair a Boolean condition could not express.
        onNodeWithTag(STATS_ACHIEVEMENTS_TEST_TAG)
            .performScrollToNode(hasTestTag(achievementRowTestTag(COLLECTOR_I)))
        assertTrue(isVisible("${GameSave.DEFAULT_CARDS.size} / 10"), "five of the ten it wants")
    }

    /** An earned achievement heads the list, which is the original's `unlockDate` ordering. */
    @Test
    fun anEarnedAchievementIsListedFirst() = runComposeUiTest {
        val decorated = GameSave.new(createdAt = 0L)
            .copy(npcWins = mapOf("tt-master" to 1))
            .withAchievement(FIRST_WIN, instant = 1_000L)
        val documents = seeded(decorated)
        setContent { App(store = settingsFor(AppLocale.EN_US), documents = documents) }
        loadCharacter(documents)
        openStats()

        assertTrue(
            exists(achievementRowTestTag(FIRST_WIN)),
            "an earned achievement should be composed without scrolling",
        )
    }

    /** The catalogue really is the 22 of `Achievements.as`, so the list cannot silently shrink. */
    @Test
    fun theCatalogueIsTheOneTheAs3Declares() {
        assertEquals(ACHIEVEMENTS, AchievementCatalog.all.size)
    }

    private companion object {
        /** `ac-td1` — the Triple-decker tier's first step, at ten cards. */
        const val COLLECTOR_I = "ac-td1"

        /** `ac-tt1` — defeat one NPC. */
        const val FIRST_WIN = "ac-tt1"

        const val ACHIEVEMENTS = 22
    }
}
