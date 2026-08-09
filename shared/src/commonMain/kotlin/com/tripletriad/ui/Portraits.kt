package com.tripletriad.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tripletriad.model.Card
import com.tripletriad.model.GameSave
import com.tripletriad.model.Npc

/**
 * The pictures that identify someone or something at a glance — an avatar, an opponent, a card.
 *
 * Written once here rather than in each screen because they share the part that is easy to get
 * wrong: **every one of them can be absent**, and each has to say so without a gap in the layout.
 * Eleven opponents ship no portrait, one item ships no icon, and a bundle can always be built
 * short. A missing image is drawn as its own initial on a tinted plate, which keeps the row the
 * same height and still tells the player which one it is.
 *
 * ### Why the images are pixel-scaled rather than smoothed
 *
 * [FilterQuality.None]. The source art is 40x40 and 50x50 sprites from a 2013 Flash game, drawn at
 * 1:1 on a display of the era. On a 3x phone they are enlarged three or four times, and bilinear
 * smoothing turns a crisp sprite into a blur. Nearest-neighbour keeps the edges the artist drew.
 */
@Composable
internal fun AvatarBadge(
    profile: GameSave,
    size: Dp = AVATAR_SIZE,
    modifier: Modifier = Modifier,
) {
    val image = rememberAvatar(LocalUiArt.current, profile.avatarId)
    val shape = CircleShape

    Box(
        modifier = modifier
            .testTag(AVATAR_TEST_TAG)
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // A hairline ring, which is what lifts a portrait off a dark backdrop. The original
            // draws a heavy bevelled frame; at this size on a phone that reads as noise.
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = RING), shape),
        contentAlignment = Alignment.Center,
    ) {
        Bitmap(image = image, description = profile.username, fallback = profile.username)
    }
}

/**
 * An opponent's 50x50 portrait, or their monogram.
 *
 * Square with a soft corner rather than circular: these are cropped character art, and a circle
 * takes the crop further in and cuts heads. The avatars are framed portraits and survive it.
 */
@Composable
internal fun NpcPortrait(
    npc: Npc,
    name: String,
    size: Dp = PORTRAIT_SIZE,
    modifier: Modifier = Modifier,
) {
    val image = rememberPortrait(LocalUiArt.current, npc.iconId)
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .testTag(portraitTestTag(npc.iconId))
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Bitmap(image = image, description = name, fallback = name)
    }
}

/**
 * A bag or achievement icon, by the name the model carries.
 *
 * No monogram fallback: an item's name is already next to it in every place one of these is drawn,
 * so a letter would be repeating what the row says. An absent icon leaves its plate, which holds
 * the alignment of the column it is in.
 */
@Composable
internal fun ItemIcon(
    iconId: String,
    description: String,
    size: Dp = ICON_SIZE,
    modifier: Modifier = Modifier,
) {
    val image = LocalUiArt.current?.icon(iconId)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = description,
                filterQuality = FilterQuality.None,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
            )
        }
    }
}

/**
 * An achievement's badge, which is an icon for most of them and a card thumbnail for one.
 *
 * `Achievement.iconId` is not one namespace. Most rows name a `misc/` icon — the tiers reuse
 * `card_r{n}_icon` to say how hard they are — but `ac-fob` names `ff14_thumb_37`, the thumbnail of
 * the card it is about. That is the atlas's frame under the AS3's own name for it: the sheet calls
 * the same frame `ff14_37`, which is what a card's [textureId] is. Rather than rename the model or
 * the table, the two spellings are reconciled in the one place that has to know both.
 */
@Composable
internal fun AchievementIcon(
    iconId: String,
    description: String,
    size: Dp = ICON_SIZE,
    modifier: Modifier = Modifier,
) {
    val art = LocalUiArt.current
    val bitmap = art?.icon(iconId)
    val painter = if (bitmap == null) art?.thumb(thumbTextureId(iconId)) else null

    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = description,
                filterQuality = FilterQuality.None,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
            )

            painter != null -> Image(
                painter = painter,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** `ff14_thumb_37` is the frame the atlas calls `ff14_37`. */
internal fun thumbTextureId(iconId: String): String = iconId.replace("_thumb_", "_")

/**
 * A card's 40x40 thumbnail — the tile the original's collection grid is built from.
 *
 * Takes a [Painter] rather than an [ImageBitmap] because that is what a slice of an atlas is; see
 * [UiArt.thumb]. Null while the sheets load, and null for a card with no frame, which is why the
 * plate is drawn whether or not there is an image on it.
 */
@Composable
internal fun CardThumb(
    card: Card,
    size: Dp = THUMB_SIZE,
    modifier: Modifier = Modifier,
) {
    val painter: Painter? = LocalUiArt.current?.thumb(card)

    Box(
        modifier = modifier
            .testTag(thumbTestTag(card.textureId))
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null) {
            // No `filterQuality` here: the painter overload has none, so the slices carry it
            // themselves — see `ThumbFrame.painterOn`.
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** The image if there is one, its first letter if there is not. */
@Composable
private fun Bitmap(image: ImageBitmap?, description: String, fallback: String) {
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = description,
            filterQuality = FilterQuality.None,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Text(
            text = fallback.take(1).uppercase(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** `avatar` — one profile is shown at a time, so one tag is enough. */
const val AVATAR_TEST_TAG: String = "avatar"

/** `portrait-<iconId>`, so a test can name the opponent whose picture it is looking for. */
fun portraitTestTag(iconId: String): String = "portrait-$iconId"

/** `thumb-<textureId>` — the card's own id, the same key the grid keys its items on. */
fun thumbTestTag(textureId: String): String = "thumb-$textureId"

private val AVATAR_SIZE = 56.dp
private val PORTRAIT_SIZE = 44.dp
private val ICON_SIZE = 32.dp
private val THUMB_SIZE = 40.dp
private const val RING = 0.4f
