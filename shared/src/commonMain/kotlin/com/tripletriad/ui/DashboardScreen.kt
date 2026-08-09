package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.audio.LocalAudio
import com.tripletriad.audio.Sound
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave

const val DASHBOARD_PLAY_TEST_TAG: String = "dashboard-play"
const val DASHBOARD_PVP_TEST_TAG: String = "dashboard-pvp"
const val DASHBOARD_STATS_TEST_TAG: String = "dashboard-stats"
const val DASHBOARD_DECKS_TEST_TAG: String = "dashboard-decks"
const val DASHBOARD_INVENTORY_TEST_TAG: String = "dashboard-inventory"
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
 * ### Why it is a grid of cards and no longer a stack of nine buttons
 *
 * Nine identical full-width bars say that nine things are equally likely, and they are not: playing
 * a match is what the screen is for and the other seven are places to go between matches. A stack
 * also cannot show anything *about* a destination, so the character's own progress — the avatar,
 * the level, the bar across it — had nowhere to be but a second screen. Both are fixed by the same
 * change: Play spans the grid, the rest are cards with an icon, and the header is the profile.
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
@Suppress("LongParameterList")
internal fun DashboardScreen(
    profile: GameSave,
    onPlay: () -> Unit,
    onStats: () -> Unit,
    onDecks: () -> Unit,
    onInventory: () -> Unit,
    onHelp: () -> Unit,
    onLogout: () -> Unit,
) {
    val strings = LocalStrings.current

    // The title is the character's name rather than a screen name: the original had no title here
    // either — the `UserBar` in the corner was the only thing identifying whose dashboard it was.
    ScreenScaffold(
        title = profile.username,
        onBack = onLogout,
        actions = { CharacterActions(profile) },
    ) {
        LevelBar(profile)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            // A scrolling grid has no natural bottom margin, and the last row would otherwise sit
            // against the edge of a short window.
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            // Play is on the navigation bar too, and is the one repetition worth keeping: it is
            // what the screen is *for*, and a hero action that also has a bar entry is what every
            // Material app with a primary task does.
            item(span = { GridItemSpan(maxLineSpan) }) {
                HomeCard(
                    label = strings[StringKeys.PLAY],
                    icon = TtoIcons.Play,
                    tag = DASHBOARD_PLAY_TEST_TAG,
                    accented = true,
                    onClick = onPlay,
                )
            }
            // The two that are a tab *inside* a bar destination. The collection and the shelf are
            // not here: the bar reaches both in one tap, and a home screen that repeats its own
            // navigation bar is a home screen with nothing of its own to say.
            item {
                HomeCard(
                    label = strings[StringKeys.CARD_DECKS],
                    icon = TtoIcons.Collection,
                    tag = DASHBOARD_DECKS_TEST_TAG,
                    onClick = onDecks,
                )
            }
            item {
                HomeCard(
                    label = strings[StringKeys.INVENTORY],
                    icon = TtoIcons.Shop,
                    tag = DASHBOARD_INVENTORY_TEST_TAG,
                    onClick = onInventory,
                )
            }
            item {
                HomeCard(strings[StringKeys.PROFILE], TtoIcons.Person, DASHBOARD_STATS_TEST_TAG) {
                    onStats()
                }
            }
            item {
                HomeCard(
                    label = strings[StringKeys.HELP],
                    icon = TtoIcons.Help,
                    tag = DASHBOARD_HELP_TEST_TAG,
                    onClick = onHelp,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                HomeCard(
                    label = strings[StringKeys.MULTIPLAYER],
                    icon = TtoIcons.Person,
                    tag = DASHBOARD_PVP_TEST_TAG,
                    enabled = false,
                    onClick = {},
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LogoutRow(label = strings[StringKeys.LOGOUT], onClick = onLogout)
            }
        }
    }
}

/**
 * One destination.
 *
 * The click sound is played here and not by the caller, for the reason [WideButton] gives: a screen
 * added later is the one that forgets it.
 *
 * @param accented the one card that is the point of the screen. Filled in the card blue rather than
 *   the row surface, which is the only weight difference between it and the other seven — a second
 *   size, a second shape and a second type scale would be three ways of saying the same thing.
 */
@Composable
internal fun HomeCard(
    label: String,
    icon: ImageVector,
    tag: String,
    accented: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val audio = LocalAudio.current
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED)
        accented -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        onClick = {
            audio.play(Sound.UI_CLICK)
            onClick()
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(CARD_HEIGHT).testTag(tag),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (accented) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = content,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = content,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(CARD_ICON),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Leaving the character, which is not a destination and is not drawn as one. */
@Composable
private fun LogoutRow(label: String, onClick: () -> Unit) {
    val audio = LocalAudio.current

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(
            onClick = {
                audio.play(Sound.UI_CLICK)
                onClick()
            },
            modifier = Modifier.testTag(DASHBOARD_LOGOUT_TEST_TAG),
        ) {
            Icon(
                imageVector = TtoIcons.Logout,
                contentDescription = null,
                modifier = Modifier.size(CARD_ICON),
            )
            Text(text = label, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private val CARD_HEIGHT = 72.dp
private val CARD_ICON = 22.dp
