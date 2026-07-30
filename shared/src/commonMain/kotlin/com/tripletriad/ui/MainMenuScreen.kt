package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripletriad.audio.LocalAudio
import com.tripletriad.audio.Sound
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import androidx.compose.foundation.Image as ComposeImage

const val MENU_PLAY_TEST_TAG: String = "menu-play"
const val MENU_OPTIONS_TEST_TAG: String = "menu-options"
const val MENU_QUIT_TEST_TAG: String = "menu-quit"

/**
 * The main menu: logo, then one button per action.
 *
 * Follows `MenuScreen.as` in shape — the `logo_white_512` wordmark centred above a vertical stack
 * with an 8 px gap — but not in contents. The original offered Continue / New Game / Load Game /
 * Options / Quit, and four of those need save games, which do not exist here yet. So: **Play,
 * Options, Quit**, which is what was asked for. When saves land, this list grows the way the
 * original's did, and `MenuScreen.as:52-58` is the order to grow it in.
 *
 * @param onQuit supplied by the host, because leaving is platform business: `finish()` on Android,
 *   `exitApplication` on desktop, and on iOS nothing at all — Apple's guidelines have no "quit".
 *   `:shared` has no way to express any of that, and should not pretend to.
 */
@Composable
internal fun MainMenuScreen(
    onPlay: () -> Unit,
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

        Column(
            modifier = Modifier.padding(top = 40.dp).widthIn(max = ButtonMaxWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MenuButton(strings[StringKeys.PLAY], MENU_PLAY_TEST_TAG, onPlay)
            MenuButton(strings[StringKeys.SETTINGS], MENU_OPTIONS_TEST_TAG, onOptions)
            MenuButton(strings[StringKeys.QUIT], MENU_QUIT_TEST_TAG, onQuit)
        }
    }
}

/**
 * One row of the stack.
 *
 * `softWrap = false` with ellipsis rather than a second line: `STR_BACKGROUND_VOLUME` in German is
 * `Hintergrundlautstärke`, and a menu whose rows change height by language is a menu that jumps
 * when the language does. A truncated label is a visible problem; a stack that changes height
 * between languages is a subtle one.
 */
@Composable
private fun MenuButton(label: String, tag: String, onClick: () -> Unit) {
    // `TouchLabel.as:31` played this on any tap on a control, so it belongs to the control and not
    // to each caller — otherwise the next screen added is the one that forgets it.
    val audio = LocalAudio.current
    Button(
        onClick = {
            audio.play(Sound.UI_CLICK)
            onClick()
        },
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag(tag),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            // The card edge blue, so the menu belongs to the same palette as the board rather
            // than to Material's default purple.
            containerColor = BlueCard,
            contentColor = Color.White,
        ),
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val LogoMaxWidth = 512.dp
private val LogoHeight = 128.dp
private val ButtonMaxWidth = 280.dp
