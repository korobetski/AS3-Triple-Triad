package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tripletriad.model.Card
import com.tripletriad.model.CardType
import com.tripletriad.model.powerLabel
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import tripletriad.shared.generated.resources.Res

/**
 * Every texture `tto.display.Card` draws, read out of the Compose resource bundle.
 *
 * Imported by [`tools/import_card_art.py`](../../../../../../../tools/import_card_art.py),
 * which also records why the artwork is 263 individual files rather than the two sprite
 * sheets that ship with the AS3 source: the sheets are 2 MB smaller to download but cost
 * ~24 MB of permanently resident bitmap, and `ff14_cards.xml` only covers 80 of 153 cards.
 *
 * Card faces load **on demand and stay cached** — a match needs at most nineteen of them.
 * Everything else (the back, the digit atlas, five rarity rows, twelve type icons: 19 files,
 * 85 KB) is loaded once up front because every card needs some of it.
 */
class CardArt internal constructor(
    /** `back` — `Card.as:93`. Drawn over everything while a card is mid-flip. */
    val back: ImageBitmap,
    /** `talk_basic.tex` — [TalkBubble]'s frame. One image for every language; it holds no text. */
    val talk: ImageBitmap,
    private val stars: Map<Int, ImageBitmap>,
    private val types: Map<CardType, ImageBitmap>,
    private val digits: Map<String, Painter>,
) {
    // Not synchronised. Two coroutines racing on the same card decode it twice and the
    // second write wins, which costs one redundant decode and is otherwise harmless; a
    // mutex here would be protecting nothing worth protecting.
    private val faces = mutableMapOf<String, ImageBitmap>()

    /** `{rarity}stars` at (9, 6) — `Card.as:176-178`. */
    fun starsFor(rarity: Int): ImageBitmap? = stars[rarity]

    /** `type-{type}` at (80, 3) — `Card.as:181-183`. */
    fun typeIcon(type: CardType): ImageBitmap? = types[type]

    /** The `cdbg` plate — `CardDigits.as:26-29`. */
    val digitPlate: Painter? get() = digits[PLATE_TEXTURE]

    /** `cd1`…`cd9`, `cdA` — one per edge power. */
    fun digit(power: Int): Painter? = digits["cd${powerLabel(power)}"]

    /** The face already decoded for [card], or null if it has not been asked for yet. */
    fun cachedFace(card: Card): ImageBitmap? = faces[card.textureId]

    /** Decodes [card]'s artwork, or returns the cached copy. */
    suspend fun face(card: Card): ImageBitmap {
        val id = card.textureId
        return faces.getOrPut(id) { loadImage("$id.png") }
    }
}

/**
 * `_collection + newID` — `Card.as:166`. The AS3 texture name, and therefore the file name
 * the importer writes.
 */
internal val Card.textureId: String get() = "$collection$id"

/**
 * `type-{type}` — `Card.as:181`. The AS3 type string is the lowercase enum name, which is
 * also `CardType`'s `@SerialName`; `CardBundleTest` asserts a file exists for all twelve.
 */
internal val CardType.textureName: String get() = "type-${name.lowercase()}"

/**
 * Makes [CardArt] ambient rather than threading it through four composables.
 *
 * Defaults to `null`, which is a working state and not a broken one: [CardFace] then draws
 * the flat coloured quad and nothing else, so a preview or a test can compose without the
 * 7 MB of artwork.
 */
val LocalCardArt = staticCompositionLocalOf<CardArt?> { null }

/**
 * [card]'s artwork, decoding it if this is the first time it has been seen.
 *
 * Returns the cached bitmap synchronously when there is one, so a card that has already
 * been drawn does not blink through a null frame on recomposition.
 *
 * ### Why this is `remember(art, id)` and not `produceState`
 *
 * It *was* `produceState(art.cachedFace(card), art, card.textureId)`, which showed the **wrong
 * card's picture**. `produceState` holds its value in an unkeyed `remember`, so changing the keys
 * restarts the producer but leaves the previous value in place — the initial value is only ever
 * used once, on first composition. The producer then found `value != null` and returned without
 * loading anything, so the slot kept whatever face it had first been given.
 *
 * That is invisible until a composable slot is reused for a different card, and a hand slot is
 * reused constantly: slots close up as cards are played, so playing the first card moves every
 * card behind it down one and each of those slots is asked for a new face. Keying the state on the
 * card resets it in the same composition the card changes in, so the face can never lag behind.
 */
@Composable
internal fun rememberCardFace(art: CardArt?, card: Card): ImageBitmap? {
    if (art == null) return null
    val id = card.textureId
    // Seeded from the cache so an already-decoded face is returned without a null frame.
    var face by remember(art, id) { mutableStateOf(art.cachedFace(card)) }
    LaunchedEffect(art, id) {
        if (face == null) face = art.face(card)
    }
    return face
}

/** Reads and decodes the 19 shared textures. Call once, at boot. */
suspend fun loadCardArt(): CardArt = CardArt(
    back = loadImage("back.png"),
    talk = loadImage("talk.png"),
    stars = Card.RARITY_RANGE.associateWith { loadImage("${it}stars.png") },
    types = CardType.entries.associateWith { loadImage("${it.textureName}.png") },
    digits = sliceDigitAtlas(loadImage("digits.png")),
)

/**
 * The order `sources/assets/digits/digits.xml` lists its glyphs in.
 *
 * `cdp` and `cdm` — the `+`/`-` of the Ascension modifier — and `cd0` are here because they
 * occupy grid slots and shift everything after them. Nothing draws them yet: no card has a 0,
 * and the modifier badge is not ported.
 */
private val ATLAS_ORDER = listOf(
    "cdp", "cdm", "cd0", "cd1", "cd2",
    "cd3", "cd4", "cd5", "cd6", "cd7",
    "cd8", "cd9", "cdA",
)

/**
 * Where each glyph sits in `digits.png`.
 *
 * The atlas is a **regular grid** — a 2 px margin, then five 18x18 glyphs per row on a 20 px
 * pitch — so this derives the rectangles from [ATLAS_ORDER] rather than transcribing 26
 * numbers. Checked against every `<SubTexture>` in the XML, entry by entry; a wrong pitch
 * would draw the wrong digit on every card, which is visible immediately.
 *
 * `cdbg` is the exception: 28x28 at (0, 62), outside the grid and with no margin.
 */
private fun digitFrames(): Map<String, IntRect4> = buildMap {
    ATLAS_ORDER.forEachIndexed { slot, name ->
        put(
            name,
            IntRect4(
                x = ATLAS_MARGIN_PX + DIGIT_PITCH_PX * (slot % ATLAS_COLUMNS),
                y = ATLAS_MARGIN_PX + DIGIT_PITCH_PX * (slot / ATLAS_COLUMNS),
                width = DIGIT_PX,
                height = DIGIT_PX,
            ),
        )
    }
    put(PLATE_TEXTURE, IntRect4(x = 0, y = PLATE_Y_PX, width = PLATE_PX, height = PLATE_PX))
}

/** A subtexture rectangle. Four ints, because that is what the atlas XML holds. */
private data class IntRect4(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * Turns the atlas into one [BitmapPainter] per entry.
 *
 * `BitmapPainter` takes a source rectangle, so the sheet is decoded once and every glyph is
 * a view onto it — no per-glyph bitmap and no cropping copy.
 */
private fun sliceDigitAtlas(sheet: ImageBitmap): Map<String, Painter> =
    digitFrames().mapValues { (_, frame) ->
        BitmapPainter(
            image = sheet,
            srcOffset = IntOffset(frame.x, frame.y),
            srcSize = IntSize(frame.width, frame.height),
        )
    }

@OptIn(ExperimentalResourceApi::class)
private suspend fun loadImage(name: String): ImageBitmap =
    Res.readBytes("$ART_PATH/$name").decodeToImageBitmap()

/**
 * `logo_white_512` — the wordmark `MenuScreen.as:43` centres above its button stack.
 *
 * Not part of [loadCardArt] and not a [StartupPhase] of its own: it is 15 KB of chrome that the
 * splash wants on its *first* frame, before the phase it would otherwise be loaded in. The splash
 * asks for it separately and renders the phase line without it until it arrives.
 */
suspend fun loadLogo(): ImageBitmap = loadImage("logo.png")

/** Where [`import_card_art.py`](../../../../../../../tools/import_card_art.py) writes. */
/** Where both importers write: `files/art`, and the subdirectories [UiArt] reads. */
internal const val ART_PATH = "files/art"
private const val PLATE_TEXTURE = "cdbg"

/** Every `cd*` glyph in `digits.xml` is 18x18; `cdbg` is 28x28 at y = 62. */
private const val DIGIT_PX = 18
private const val PLATE_PX = 28
private const val PLATE_Y_PX = 62

/** The glyph grid: 2 px of margin, five across, 20 px from one glyph's left edge to the next. */
private const val ATLAS_MARGIN_PX = 2
private const val ATLAS_COLUMNS = 5
private const val DIGIT_PITCH_PX = 20
