package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tripletriad.model.Card
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import tripletriad.shared.generated.resources.Res

/**
 * The artwork the screens *around* a match are drawn with — everything [CardArt] is not.
 *
 * Imported by [`tools/import_ui_art.py`](../../../../../../../tools/import_ui_art.py), which
 * records why the card thumbnails stay packed in three atlases when the card faces were unpacked
 * into 263 files: the faces are shown nineteen at a time and the thumbnails are shown all at once,
 * which inverts every term of that trade.
 *
 * ### What loads when
 *
 * The three thumbnail sheets and the seventeen icons load **once, up front**: the sheets because
 * the collection grid asks for every frame in them the moment it opens, and the icons because
 * they are 40x40 and there are seventeen. Avatars and portraits load **on demand and stay
 * cached**, the same way card faces do — a screen shows one avatar and a dozen portraits, not all
 * 111.
 *
 * ### Names, and where they come from
 *
 * Nothing here invents an identifier. An avatar is `GameSave.avatarId`, a portrait is
 * `Npc.iconId`, an icon is `Item.iconId` or `Achievement.iconId`, and a thumbnail is the card's
 * own texture id. Every one of those was already in the model before there was an image to go with
 * it, which is why this class is a set of lookups rather than a mapping table.
 */
class UiArt internal constructor(
    private val icons: Map<String, ImageBitmap>,
    private val thumbs: Map<String, Painter>,
) {
    // Not synchronised, for the reason given on `CardArt.faces`: a race costs one redundant
    // decode and nothing else.
    private val avatars = mutableMapOf<String, ImageBitmap>()
    private val portraits = mutableMapOf<String, ImageBitmap>()

    /**
     * A bag or achievement icon, by the name the model carries.
     *
     * Null is a real answer and has to be handled: `PotionItem.as` names a `potionItem` texture
     * that is in no shipped asset folder, so one item in the game genuinely has no icon. A caller
     * that assumed non-null would draw nothing there and never say why.
     */
    fun icon(name: String): ImageBitmap? = icons[name]

    /**
     * A card's 40x40 thumbnail.
     *
     * A [BitmapPainter] over the sheet rather than a bitmap of its own — the source rectangle does
     * the cropping, so 263 thumbnails cost three decoded images between them.
     */
    fun thumb(card: Card): Painter? = thumbs[card.textureId]

    /** The thumbnail behind an achievement that uses one — `ac-fob`'s `ff14_thumb_37`. */
    fun thumb(textureId: String): Painter? = thumbs[textureId]

    internal fun cachedAvatar(id: String): ImageBitmap? = avatars[id]

    internal suspend fun avatar(id: String): ImageBitmap? =
        avatars[id] ?: loadOrNull("avatars/$id.png")?.also { avatars[id] = it }

    internal fun cachedPortrait(iconId: String): ImageBitmap? = portraits[iconId]

    internal suspend fun portrait(iconId: String): ImageBitmap? =
        portraits[iconId] ?: loadOrNull("npcs/$iconId.png")?.also { portraits[iconId] = it }
}

/**
 * The avatar for [avatarId], or null while it loads — and null for good if there is no such file.
 *
 * Keyed on the id rather than left to `produceState`, for the reason spelled out on
 * [rememberCardFace]: an unkeyed holder would keep the first image it was given when the slot is
 * reused for a different one.
 */
@Composable
internal fun rememberAvatar(art: UiArt?, avatarId: String): ImageBitmap? {
    if (art == null) return null
    var image by remember(art, avatarId) { mutableStateOf(art.cachedAvatar(avatarId)) }
    LaunchedEffect(art, avatarId) {
        if (image == null) image = art.avatar(avatarId)
    }
    return image
}

/**
 * An opponent's portrait, or null.
 *
 * **Null is expected here**, not exceptional: eleven of the 84 opponents in `npcs.json` — the FF8
 * ladder rungs, `club` through `spade` — have no 50px portrait anywhere in the AS3 asset tree. The
 * row draws a monogram instead, which is why this returns null rather than a placeholder image:
 * the fallback is a layout decision, and this is not the layout.
 */
@Composable
internal fun rememberPortrait(art: UiArt?, iconId: String): ImageBitmap? {
    if (art == null) return null
    var image by remember(art, iconId) { mutableStateOf(art.cachedPortrait(iconId)) }
    LaunchedEffect(art, iconId) {
        if (image == null) image = art.portrait(iconId)
    }
    return image
}

/**
 * Reads the sheets, the frame table and the icons. Call once, at boot.
 *
 * Failures are swallowed per file, not per call: a missing icon should cost that icon and not the
 * whole screen's artwork. The importer is what makes sure the set is complete — it exits non-zero
 * on anything absent — so a null here means the bundle was built wrong, and the app carrying on
 * without one 40x40 image is the right way to find that out.
 */
suspend fun loadUiArt(): UiArt {
    val table = ThumbTableParser.parse(readText(THUMBS_TABLE))
    val sheets = table.frames.values.map { it.sheet }.distinct()
        .mapNotNull { name -> loadOrNull("thumbs/$name.png")?.let { name to it } }
        .toMap()

    return UiArt(
        icons = ICON_NAMES.mapNotNull { name -> loadOrNull("icons/$name.png")?.let { name to it } }
            .toMap(),
        thumbs = table.frames.mapNotNull { (id, frame) ->
            sheets[frame.sheet]?.let { sheet -> id to frame.painterOn(sheet) }
        }.toMap(),
    )
}

/** Where one thumbnail sits on which sheet — `thumbs.json`, written by the importer. */
@Serializable
private data class ThumbFrame(
    val sheet: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    /** [FilterQuality.None] for the reason given on [CardThumb]: these are 40x40 sprites. */
    fun painterOn(sheet: ImageBitmap): Painter = BitmapPainter(
        image = sheet,
        srcOffset = IntOffset(x, y),
        srcSize = IntSize(width, height),
        filterQuality = FilterQuality.None,
    )
}

@Serializable
private data class ThumbTable(@SerialName("frames") val frames: Map<String, ThumbFrame>)

private object ThumbTableParser {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(text: String): ThumbTable = json.decodeFromString(text)
}

@OptIn(ExperimentalResourceApi::class)
private suspend fun readText(path: String): String = Res.readBytes(path).decodeToString()

/**
 * Decodes `files/art/<path>`, or null if it is not there.
 *
 * The catch is deliberately broad. What a missing or corrupt resource throws differs by platform —
 * Compose's own resource layer on one, the image decoder on another — and the answer is the same
 * whichever it is: this image is not available, draw the fallback.
 */
@OptIn(ExperimentalResourceApi::class)
@Suppress("SwallowedException", "TooGenericExceptionCaught")
private suspend fun loadOrNull(path: String): ImageBitmap? = try {
    Res.readBytes("$ART_PATH/$path").decodeToImageBitmap()
} catch (error: Exception) {
    null
}

/** Provided by [App] next to [LocalCardArt]. Null until the startup phase that reads it is done. */
val LocalUiArt = staticCompositionLocalOf<UiArt?> { null }

private const val THUMBS_TABLE = "files/thumbs.json"

/** Exactly what `tools/import_ui_art.py` copies — the two lists have to say the same thing. */
private val ICON_NAMES = listOf(
    "card_r1_icon", "card_r2_icon", "card_r3_icon", "card_r4_icon", "card_r5_icon",
    "beast_booster", "garlean_booster", "primal_booster", "scion_booster", "booster_pack_icon",
    "xp_boost_icon", "mgp_boost_icon", "PGS", "XP",
    "item_borders", "achievement_border",
    // A raw FFXIV icon id, which is how `AchievementCatalog.NPC_ICON` names it.
    "000713",
)
