package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
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
 * The main menu: logo, the active character, then one button per action.
 *
 * Follows `MenuScreen.as` in shape — the `logo_white_512` wordmark centred above a vertical stack
 * with an 8 px gap — and now in contents too. The original offered Continue / New Game / Load Game
 * / Options / Quit; with profiles in place this shows **Play** (which is Continue when a character
 * is loaded and Load Game when none is), **Characters**, Options and Quit. `MenuScreen.as:52-58` is
 * the order it grows in.
 *
 * @param active the loaded character, or null. Shown as a line under the logo rather than folded
 *   into the Play label: "Play" has to stay one short word in four languages, and *which*
 *   character is about to be played is the thing a player needs to see before pressing it.
 * @param connectivity what is known about the servers, or null on a build with none. Null removes
 *   the line rather than showing it as "offline": an offline build is not a build whose server is
 *   down, and telling a player their connection has a problem when the game never had one is the
 *   sort of message that gets a bug report.
 * @param onQuit supplied by the host, because leaving is platform business: `finish()` on Android,
 *   `exitApplication` on desktop, and on iOS nothing at all — Apple's guidelines have no "quit".
 *   `:shared` has no way to express any of that, and should not pretend to.
 */
@Composable
internal fun MainMenuScreen(
    active: GameSave?,
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
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
            modifier = Modifier.testTag(MENU_PROFILE_TEST_TAG).padding(top = 16.dp),
        )

        // Under the character and above the buttons: it is context for what the buttons are about
        // to do, and a player who is about to press Play is the one who wants to know whether the
        // server is there. Probed once on arrival, and again whenever the menu is returned to.
        connectivity?.let {
            LaunchedEffect(it) { it.refreshSelected() }
            ServerIndicator(it, onClick = onServers)
        }

        Column(
            modifier = Modifier.padding(top = 24.dp).widthIn(max = ButtonMaxWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WideButton(strings[StringKeys.PLAY], MENU_PLAY_TEST_TAG, onClick = onPlay)
            WideButton(strings[StringKeys.PROFILE], MENU_PROFILES_TEST_TAG, onClick = onProfiles)
            // Only with a server. A build with none has nothing to list, and a button leading to
            // an empty screen is worse than no button.
            if (connectivity != null) {
                WideButton("Servers", MENU_SERVERS_TEST_TAG, onClick = onServers)
            }
            WideButton(strings[StringKeys.SETTINGS], MENU_OPTIONS_TEST_TAG, onClick = onOptions)
            WideButton(strings[StringKeys.QUIT], MENU_QUIT_TEST_TAG, onClick = onQuit)
        }
    }
}

private val LogoMaxWidth = 512.dp
private val LogoHeight = 128.dp
private val ButtonMaxWidth = 280.dp
