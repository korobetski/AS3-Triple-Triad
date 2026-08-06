package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave

const val DASHBOARD_PLAY_TEST_TAG: String = "dashboard-play"
const val DASHBOARD_PVP_TEST_TAG: String = "dashboard-pvp"
const val DASHBOARD_STATS_TEST_TAG: String = "dashboard-stats"
const val DASHBOARD_CARDS_TEST_TAG: String = "dashboard-cards"
const val DASHBOARD_DECKS_TEST_TAG: String = "dashboard-decks"
const val DASHBOARD_INVENTORY_TEST_TAG: String = "dashboard-inventory"
const val DASHBOARD_SHOP_TEST_TAG: String = "dashboard-shop"
const val DASHBOARD_HELP_TEST_TAG: String = "dashboard-help"
const val DASHBOARD_LOGOUT_TEST_TAG: String = "dashboard-logout"

/**
 * Everything a loaded character can do — the original's `dashboardScreen`.
 *
 * ### Why this screen exists at all
 *
 * It is the piece the first pass of Phase 4 left out, and its absence is what made the six screens
 * behind it impossible to place. `dashboardScreen.as:49-59` builds this exact stack, and **every
 * one of the screens it opens returns here** (`dispatchEventWith('gotoScreen', false, 'DASHBOARD')`
 * appears in all seven). So the original's flow is Menu → Load → *Dashboard* → everything, and
 * putting Play on the main menu — which is what this port did while it had only one destination —
 * gives the collection, the decks, the bag and the shop nowhere to hang.
 *
 * ### The two entries that lead nowhere
 *
 * - **Multiplayer** is drawn disabled. `dashboardScreen.as:50` pushes it with `enabled:true`, and
 *   the `PVPScreen` behind it needs the socket layer that is Phase 5. Listed rather than omitted
 *   because a menu that grows an entry later is worse than one that says what is coming.
 * - **Backstage** is not here. The original appends it when `PROFILE_DATAS.ADMIN` is set
 *   (`:56-57`); nothing in the game ever sets `ADMIN`, and the screen behind it is a data-dump
 *   debug pane — see `BackstageScreen.as`. It is Tier 5 in the plan and unreachable in the
 *   original.
 *
 * @param onLogout leaves this character. `STR_LOGOUT` and `screenId: 'MENU_SCREEN'` in the
 *   original, which sent the player to the main menu and left `Game.PROFILE_DATAS` loaded — so its
 *   "logout" changed the screen and nothing else. Here it goes to the character list, which is
 *   where leaving one character actually leads: to choosing another. See `Screen.up`.
 */
@Composable
internal fun DashboardScreen(
    profile: GameSave,
    onPlay: () -> Unit,
    onStats: () -> Unit,
    onCards: () -> Unit,
    onDecks: () -> Unit,
    onInventory: () -> Unit,
    onShop: () -> Unit,
    onHelp: () -> Unit,
    onLogout: () -> Unit,
) {
    val strings = LocalStrings.current

    // The title is the character's name rather than a screen name: the original had no title here
    // either — the `UserBar` in the corner was the only thing identifying whose dashboard it was.
    ScreenScaffold(title = profile.username, onBack = onLogout) {
        CharacterBar(profile)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WideButton(strings[StringKeys.PLAY], DASHBOARD_PLAY_TEST_TAG, onClick = onPlay)
            WideButton(
                label = strings[StringKeys.MULTIPLAYER],
                tag = DASHBOARD_PVP_TEST_TAG,
                enabled = false,
                onClick = {},
            )
            WideButton(strings[StringKeys.PROFILE], DASHBOARD_STATS_TEST_TAG, onClick = onStats)
            WideButton(strings[StringKeys.CARD_LIST], DASHBOARD_CARDS_TEST_TAG, onClick = onCards)
            WideButton(strings[StringKeys.CARD_DECKS], DASHBOARD_DECKS_TEST_TAG, onClick = onDecks)
            WideButton(
                label = strings[StringKeys.INVENTORY],
                tag = DASHBOARD_INVENTORY_TEST_TAG,
                onClick = onInventory,
            )
            WideButton(strings[StringKeys.SHOP], DASHBOARD_SHOP_TEST_TAG, onClick = onShop)
            WideButton(strings[StringKeys.HELP], DASHBOARD_HELP_TEST_TAG, onClick = onHelp)
            WideButton(strings[StringKeys.LOGOUT], DASHBOARD_LOGOUT_TEST_TAG, onClick = onLogout)
            // The stack scrolls on a short window, and a scrolling column has no natural bottom
            // margin — nine buttons is taller than a phone in landscape.
            Box(modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}
