package com.tripletriad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripletriad.audio.LocalAudio
import com.tripletriad.audio.Sound

/** The back chevron of any [ScreenScaffold]. Only one is on screen at a time. */
const val SCREEN_BACK_TEST_TAG: String = "screen-back"

/**
 * A full-width action button.
 *
 * The menu's stack and every screen's primary action are the same control, so it is one composable.
 * `softWrap = false` with ellipsis rather than a second line: `STR_BACKGROUND_VOLUME` in German is
 * `Hintergrundlautstärke`, and a stack whose rows change height by language is a stack that jumps
 * when the language does. A truncated label is a visible problem; a layout that shifts between
 * languages is a subtle one.
 */
@Composable
internal fun WideButton(
    label: String,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    // `TouchLabel.as:31` played this on any tap on a control, so it belongs to the control and not
    // to each caller — otherwise the next screen added is the one that forgets it.
    val audio = LocalAudio.current
    Button(
        onClick = {
            audio.play(Sound.UI_CLICK)
            onClick()
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag(tag),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            // The card edge blue, so the menu belongs to the same palette as the board rather
            // than to Material's default purple.
            containerColor = BlueCard,
            contentColor = Color.White,
            disabledContainerColor = RowBackground,
            disabledContentColor = Color.White.copy(alpha = 0.4f),
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

/**
 * Title, a back chevron, and a column for the screen's own content.
 *
 * Extracted because the profile, creation and opponent screens would otherwise repeat the same
 * header, the same max width and the same padding, and the first one to be edited alone is the one
 * that starts looking different.
 */
@Composable
internal fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "‹",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 20.sp,
                modifier = Modifier
                    .testTag(SCREEN_BACK_TEST_TAG)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 4.dp),
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 12.dp),
            content = content,
        )
    }
}

/** Keeps every list screen the same width on a desktop window that is far wider than a phone. */
internal val ContentMaxWidth = 520.dp

internal val RowShape = RoundedCornerShape(6.dp)
internal val RowBackground = Color(0xFF1E2230)
internal val RowBorder = Color(0xFF3A4152)
internal val ArmedBorder = Color(0xFFE05252)

/** The `·`-joined metadata line used by the profile and opponent rows. */
internal const val DOT_SEPARATOR = "  ·  "
