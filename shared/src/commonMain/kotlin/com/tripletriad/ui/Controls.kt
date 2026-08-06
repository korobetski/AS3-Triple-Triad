package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripletriad.audio.LocalAudio
import com.tripletriad.audio.Sound
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave

/** The back chevron of any [ScreenScaffold]. Only one is on screen at a time. */
const val SCREEN_BACK_TEST_TAG: String = "screen-back"

/** The [CharacterBar]. Its presence is what says a screen is behind the dashboard. */
const val CHARACTER_BAR_TEST_TAG: String = "character-bar"

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

/**
 * The character's name, level, purse and active boosts.
 *
 * `display/UserBar.as`, which every dashboard screen put in its top-right corner. Two things of its
 * are not here: the **avatar** (`AVATAR_ID` names an FFXIV portrait texture that
 * `tools/import_card_art.py` does not import — there are 40 of them and nothing but this bar reads
 * one), and the **jump menu** it opened on tap, which listed every dashboard screen except the
 * current one. That menu existed because the original had no back button on these screens; this
 * port has one, so returning to the dashboard and picking again is two taps against the callout's
 * two.
 *
 * The boon markers are shown as `MGP ×n` / `XP ×n` rather than as the original's two icons, because
 * a boon is a **count of boosted matches** and the icon said only that there was at least one — see
 * [com.tripletriad.model.Boons.spending].
 */
@Composable
internal fun CharacterBar(save: GameSave) {
    val strings = LocalStrings.current
    val boons = buildList {
        if (save.boons.mgp > 0) add("${strings[StringKeys.MGP]} ×${save.boons.mgp}")
        if (save.boons.xp > 0) add("${strings[StringKeys.XP]} ×${save.boons.xp}")
    }

    Row(
        modifier = Modifier
            .testTag(CHARACTER_BAR_TEST_TAG)
            .fillMaxWidth()
            .clip(RowShape)
            .background(RowBackground.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = save.username,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (boons.isNotEmpty()) {
            Text(
                text = boons.joinToString(" "),
                color = BoonText,
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(
            text = "${strings[StringKeys.LEVEL]} ${save.level}$DOT_SEPARATOR" +
                "${save.mgp} ${strings[StringKeys.MGP]}",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** [ScreenScaffold] with a [CharacterBar] under the title — every screen behind the dashboard. */
@Composable
internal fun CharacterScaffold(
    profile: GameSave,
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ScreenScaffold(title = title, onBack = onBack) {
        CharacterBar(profile)
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp),
            content = content,
        )
    }
}

/**
 * A centred "there is nothing here" line.
 *
 * Its own composable because the tag is the assertion: `assertDoesNotExist` on a list is not the
 * same claim as "the screen says it is empty", and the four screens that can be empty should all
 * make the second one.
 */
@Composable
internal fun EmptyNote(text: String, tag: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 14.sp,
        modifier = Modifier.testTag(tag).padding(vertical = 24.dp),
    )
}

/**
 * The shared list-row surface: rounded, filled, outlined, and tappable.
 *
 * Six screens draw this same box. A modifier rather than a wrapper composable so a row keeps
 * control of its own layout — some are a `Row`, some a `Column`, and one is a grid cell.
 *
 * @param armed draws the destructive-confirmation outline instead of the ordinary one.
 * @param selected draws the card-blue outline and tints the fill, for a row that is the current
 *   choice rather than merely tappable.
 */
internal fun Modifier.rowSurface(
    armed: Boolean = false,
    selected: Boolean = false,
): Modifier = clip(RowShape)
    .background(if (selected) BlueCard.copy(alpha = 0.28f) else RowBackground)
    .border(
        width = 1.dp,
        color = when {
            armed -> ArmedBorder
            selected -> BlueCard
            else -> RowBorder
        },
        shape = RowShape,
    )

/** Keeps every list screen the same width on a desktop window that is far wider than a phone. */
internal val ContentMaxWidth = 520.dp

internal val RowShape = RoundedCornerShape(6.dp)
internal val RowBackground = Color(0xFF1E2230)
internal val RowBorder = Color(0xFF3A4152)
internal val ArmedBorder = Color(0xFFE05252)

/** The boon markers and an opponent row's rules: the same "temporary effect" gold. */
internal val BoonText = Color(0xFFF2C14E)

/** The `·`-joined metadata line used by the profile and opponent rows. */
internal const val DOT_SEPARATOR = "  ·  "
