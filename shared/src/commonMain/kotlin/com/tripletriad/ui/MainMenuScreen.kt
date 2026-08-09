package com.tripletriad.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave
import androidx.compose.foundation.Image as ComposeImage

const val MENU_PLAY_TEST_TAG: String = "menu-play"
const val MENU_PROFILES_TEST_TAG: String = "menu-profiles"
const val MENU_SERVERS_TEST_TAG: String = "menu-servers"
const val MENU_OPTIONS_TEST_TAG: String = "menu-options"
const val MENU_QUIT_TEST_TAG: String = "menu-quit"

/** The line naming the loaded character, or saying there is none. */
const val MENU_PROFILE_TEST_TAG: String = "menu-profile"

/** The server line. Absent entirely on an offline build, which has no server to have a state. */
const val MENU_SERVER_TEST_TAG: String = "menu-server"

/**
 * The card offering the account the app remembers. Absent when it remembers none.
 *
 * Its presence is the assertion the whole of [SessionState] exists to make: something was restored,
 * or something lapsed. Before it, both looked identical from the menu — a form that did not appear.
 */
const val MENU_RESUME_TEST_TAG: String = "menu-resume"

/** What the remembered account's session is currently doing. One of [SessionState]'s labels. */
const val MENU_RESUME_STATE_TEST_TAG: String = "menu-resume-state"

/** The resume card's primary action: continue, or sign in again. */
const val MENU_RESUME_GO_TEST_TAG: String = "menu-resume-go"

/** Forget this account and sign in as somebody else. */
const val MENU_RESUME_SWITCH_TEST_TAG: String = "menu-resume-switch"

/**
 * What the app knows about the remembered account, and therefore what the card offers.
 *
 * Three states and not two, because "we are still asking the server" is a real one: [restore] runs
 * a round trip on every launch and on a slow network the card would otherwise claim the session had
 * lapsed for as long as that took.
 *
 * @property labelKey the app-owned string that names it.
 */
internal enum class SessionState(val labelKey: String) {
    /** A stored token was accepted. The player is signed in and never saw a form. */
    RESTORED(StringKeys.SESSION_RESTORED),

    /** [AccountSession.restore] is in flight. */
    CONNECTING(StringKeys.SESSION_CONNECTING),

    /**
     * The name is remembered and the token is not usable — expired, or refused.
     *
     * The app stores no password (see `AccountScreen`), so there is nothing it could try. What it
     * can do is say so and take the player to a form with their name already in it.
     */
    LAPSED(StringKeys.SESSION_LAPSED),
}

/**
 * The main menu: logo, whoever the app remembers, then one card per action.
 *
 * Follows `MenuScreen.as` in shape — the `logo_white_512` wordmark centred above a stack — and in
 * contents: the original offered Continue / New Game / Load Game / Options / Quit, and with
 * profiles in place this is **Play** (Continue when a character is loaded, Load Game when none
 * is), **Characters**, Servers, Options and Quit, in `MenuScreen.as:52-58`'s order.
 *
 * ### The cards, and the one part of the shell this screen does not take
 *
 * The buttons are [HomeCard]s, which is what the dashboard behind them has been since the Material
 * 3 shell landed — a menu that looked like a different app from the screen one tap away was the
 * last of that. What it does **not** take is [ScreenScaffold]: this screen is the root, so there is
 * no up to draw, and its title is the wordmark rather than a line of text. A `TopAppBar` here would
 * be an empty bar with a back arrow that quit the game.
 *
 * The server line stays where it was, under the character and above the actions: it is context for
 * what the actions are about to do, and the player about to press Play is the one who wants it.
 *
 * @param active the loaded character, or null. Shown under the logo rather than folded into the
 *   Play label: "Play" has to stay one short word in four languages, and *which* character is about
 *   to be played is the thing a player needs to see before pressing it.
 * @param remembered the account the app has a name for, with what its session is doing — or null on
 *   an offline build, or when nothing is remembered. See [SessionState].
 * @param connectivity what is known about the servers, or null on a build with none. Null removes
 *   the line rather than showing it as "offline": an offline build is not a build whose server is
 *   down, and telling a player their connection has a problem when the game never had one is the
 *   sort of message that gets a bug report.
 * @param onQuit supplied by the host, because leaving is platform business: `finish()` on Android,
 *   `exitApplication` on desktop, and on iOS nothing at all — Apple's guidelines have no "quit".
 *   `:shared` has no way to express any of that, and should not pretend to.
 */
@Composable
@Suppress("LongParameterList")
internal fun MainMenuScreen(
    active: GameSave?,
    remembered: RememberedAccount?,
    connectivity: Connectivity?,
    onPlay: () -> Unit,
    onProfiles: () -> Unit,
    onServers: () -> Unit,
    onOptions: () -> Unit,
    onQuit: () -> Unit,
) {
    val strings = LocalStrings.current
    val logo by produceState<ImageBitmap?>(initialValue = null) { value = loadLogo() }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.height(LogoHeight).widthIn(max = LogoMaxWidth),
            contentAlignment = Alignment.Center,
        ) {
            logo?.let {
                ComposeImage(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Text(
            text = active?.let {
                listOf(
                    it.username,
                    collectionLabel(it.mode),
                    "${strings[StringKeys.LEVEL]} ${it.level}",
                ).joinToString(DOT_SEPARATOR)
            } ?: strings[StringKeys.NO_PROFILE],
            color = MaterialTheme.colorScheme.onSurface
                .copy(alpha = if (active == null) 0.5f else 0.8f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(MENU_PROFILE_TEST_TAG).padding(top = 12.dp),
        )

        // Probed once on arrival, and again whenever the menu is returned to.
        connectivity?.let {
            LaunchedEffect(it) { it.refreshSelected() }
            ServerIndicator(it, onClick = onServers)
        }

        Column(
            modifier = Modifier.padding(top = 16.dp).widthIn(max = MenuMaxWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            remembered?.let { ResumeCard(it, active) }

            LazyVerticalGrid(
                columns = GridCells.Fixed(MENU_COLUMNS),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HomeCard(
                        label = strings[StringKeys.PLAY],
                        icon = TtoIcons.Play,
                        tag = MENU_PLAY_TEST_TAG,
                        accented = true,
                        onClick = onPlay,
                    )
                }
                item {
                    HomeCard(
                        label = strings[StringKeys.PROFILE],
                        icon = TtoIcons.Person,
                        tag = MENU_PROFILES_TEST_TAG,
                        onClick = onProfiles,
                    )
                }
                // Only with a server. A build with none has nothing to list, and a card leading to
                // an empty screen is worse than no card.
                if (connectivity != null) {
                    item {
                        HomeCard(
                            label = strings[StringKeys.SERVERS],
                            icon = TtoIcons.Home,
                            tag = MENU_SERVERS_TEST_TAG,
                            onClick = onServers,
                        )
                    }
                }
                item {
                    HomeCard(
                        label = strings[StringKeys.SETTINGS],
                        icon = TtoIcons.Options,
                        tag = MENU_OPTIONS_TEST_TAG,
                        onClick = onOptions,
                    )
                }
                item {
                    HomeCard(
                        label = strings[StringKeys.QUIT],
                        icon = TtoIcons.Logout,
                        tag = MENU_QUIT_TEST_TAG,
                        onClick = onQuit,
                    )
                }
            }
        }
    }
}

/**
 * The account the app remembers, and what to do about it.
 *
 * A parameter object rather than five parameters on [MainMenuScreen], because they are only ever
 * meaningful together: a state with no name, or a "sign in again" action on a session that was
 * restored, are combinations that should not be expressible.
 *
 * @property username who is remembered. Never blank — a null [RememberedAccount] is how "nobody" is
 *   said.
 * @property onGo continue, or open the sign-in form with the name already filled in. Which one it
 *   is follows from [state] and is the caller's to decide.
 * @property onSwitch sign out and sign in as somebody else. Signing out is what clears
 *   [AccountSession.lastUsername], which is what makes this card disappear.
 */
@Immutable
internal class RememberedAccount(
    val username: String,
    val state: SessionState,
    val onGo: () -> Unit,
    val onSwitch: () -> Unit,
)

/**
 * "This is who you were, and here is what happened to it."
 *
 * The visible half of a feature that has worked silently since sessions landed: a stored token is
 * restored on launch and the form is simply never shown, which is indistinguishable from the form
 * being broken. A [SessionState.LAPSED] card is the same statement in the other direction — the app
 * has not forgotten the player, it has run out of the thirty days the token was good for.
 *
 * Outlined rather than filled, and above the actions rather than among them: it is a statement with
 * two things to do about it, not a sixth destination.
 */
@Composable
private fun ResumeCard(account: RememberedAccount, active: GameSave?) {
    val strings = LocalStrings.current
    val lapsed = account.state == SessionState.LAPSED

    Surface(
        modifier = Modifier.testTag(MENU_RESUME_TEST_TAG).fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = 1.dp,
            color = if (lapsed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // The character's own avatar when there is one, and its plate when there is not:
                // the lapsed case has a name and no profile behind it yet.
                if (active != null) {
                    AvatarBadge(profile = active, size = ResumeAvatarSize)
                } else {
                    Box(modifier = Modifier.size(ResumeAvatarSize))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.username,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = strings[account.state.labelKey],
                        color = if (lapsed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(MENU_RESUME_STATE_TEST_TAG),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(RESUME_GO_WEIGHT)) {
                    WideButton(
                        label = strings[
                            if (lapsed) StringKeys.SIGN_IN_AGAIN else StringKeys.CONTINUE,
                        ],
                        tag = MENU_RESUME_GO_TEST_TAG,
                        // Nothing to press while the round trip is in flight, and disabled rather
                        // than absent: a control that appears once the network answers is a
                        // control the player's thumb arrives at after it has moved.
                        enabled = account.state != SessionState.CONNECTING,
                        onClick = account.onGo,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    WideButton(
                        label = strings[StringKeys.SWITCH_ACCOUNT],
                        tag = MENU_RESUME_SWITCH_TEST_TAG,
                        filled = false,
                        onClick = account.onSwitch,
                    )
                }
            }
        }
    }
}

/** Two across, as the dashboard's grid is — the one full-width card is the one that matters. */
private const val MENU_COLUMNS = 2

/** The primary action gets twice the width of "sign in as somebody else". */
private const val RESUME_GO_WEIGHT = 2f

private val LogoMaxWidth = 512.dp
private val LogoHeight = 128.dp

/** [ContentMaxWidth] would let the two-column grid grow cards wider than they read well. */
private val MenuMaxWidth = 380.dp
private val ResumeAvatarSize = 40.dp
