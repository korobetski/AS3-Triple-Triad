package com.tripletriad.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tripletriad.model.CoinFlip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The overlay itself, present only while a caption is playing. */
const val MATCH_BANNER_TEST_TAG: String = "match-banner"

/** Which caption is playing, for tests that care. `match-banner-SAME`, and so on. */
fun matchBannerTestTag(banner: MatchBanner): String = "match-banner-${banner.name}"

/**
 * One thing the overlay can play.
 *
 * Nineteen of the AS3's twenty-four animations are a caption, so [Caption] carries a
 * [MatchBanner] and its whole table. [Toss] is the first that is not — `PileOuFace` deals
 * three cards rather than showing a word — and it is in the same sequence rather than
 * beside it because its position matters: the coin flip runs between Swap and Start, and
 * Start announcing a match whose first player has not been drawn yet is the wrong order.
 */
internal sealed interface MatchAnimation {
    /** How long it occupies the screen, which is what the caller has to wait for. */
    val totalMillis: Int

    /** One of the twenty full-screen captions. */
    data class Caption(val banner: MatchBanner) : MatchAnimation {
        override val totalMillis: Int get() = banner.totalMillis
    }

    /** `PileOuFace` — the three cards that decide who starts. */
    data class Toss(val flip: CoinFlip) : MatchAnimation {
        override val totalMillis: Int get() = COIN_FLIP_TOTAL_MILLIS
    }
}

/** [MatchAnimation.Caption], for the many call sites that only ever build captions. */
internal fun List<MatchBanner>.asAnimations(): List<MatchAnimation> =
    map(MatchAnimation::Caption)

/**
 * One moment worth animating, and what it earns.
 *
 * A wrapper around a list only so that identity can carry "this is a *new* event". Two
 * Sames in a row produce equal caption lists and must still play twice, so [at] — a
 * monotonic marker from the caller, in practice the move number — is what tells them
 * apart. Without it the second Same is silent, which is the sort of bug that looks like
 * a dropped frame.
 */
internal data class BannerEvent(val at: Int, val animations: List<MatchAnimation>)

/**
 * The full-screen rule captions, played one at a time over the board.
 *
 * ### Why a queue and not a flag
 *
 * Because one placement can earn more than one caption. A Same that starts a combo owes
 * the player SAME and then COMBO, in that order, and the original gets this by adding two
 * `Sprite`s to the stage with different `delay`s and letting the juggler sort it out.
 * There is no equivalent here — a composable is not a display list you can push two of —
 * so the ordering has to be explicit. [pending] is that ordering.
 *
 * Captions are **dropped rather than queued indefinitely** past [QUEUE_LIMIT]. A caption
 * the player sees four seconds after the move it describes is not information, and a
 * queue that can outlive the match it belongs to is a leak with a view.
 *
 * ### Why this draws nothing without [LocalBannerArt]
 *
 * So that every existing match test composes unchanged. The captions are decoration over
 * a board that is fully playable without them, and a test that does not supply the
 * artwork should get a board, not a crash.
 *
 * @param event what happened, or null. Identity is what triggers a play — a **change** of
 *   [event] enqueues its captions, and any number of recompositions with the same one
 *   enqueue nothing. That is why the caller passes a value derived from the move rather
 *   than the captions alone: two consecutive Sames are two events with equal caption
 *   lists, and a list would not distinguish them.
 */
@Composable
internal fun MatchBannerOverlay(event: BannerEvent?) {
    val pending = remember { mutableStateListOf<MatchAnimation>() }
    var playing by remember { mutableStateOf<MatchAnimation?>(null) }

    // Keyed on the event rather than run on every composition, so a recomposition for an
    // unrelated reason — a score updating, the turn clock ticking — does not replay it.
    LaunchedEffect(event) {
        event?.animations
            ?.take(QUEUE_LIMIT - pending.size)
            ?.let(pending::addAll)
    }

    LaunchedEffect(pending.size, playing) {
        if (playing == null && pending.isNotEmpty()) {
            playing = pending.removeAt(0)
        }
    }

    when (val current = playing) {
        null -> Unit
        is MatchAnimation.Caption -> Caption(current.banner) { playing = null }
        is MatchAnimation.Toss -> CoinFlipCards(current.flip) { playing = null }
    }
}

/**
 * One caption, centred over the board — and a caption that never arrives, handled.
 *
 * The two failure branches are the point of this being its own function. A caption is
 * drawn from a file that may not be there, and the queue behind it is serial, so anything
 * that leaves [onFinished] uncalled stops every later caption in the match. That is a much
 * worse symptom than the missing picture itself, and it is invisible until the fourth
 * caption fails to appear.
 *
 * - **No [LocalBannerArt] at all** — captions are switched off, which is how every match
 *   test in the suite composes. Drop it immediately; there is nothing to wait for.
 * - **Artwork present but not decoded yet** — the ordinary first play of a caption, since
 *   `rememberCaption` returns null until its coroutine finishes. Waiting the caption's own
 *   duration before giving up is what makes a genuinely missing file cost one caption
 *   rather than the rest of the match. The wait is cancelled the moment the image lands,
 *   because this branch leaves the composition and [BannerImage] takes over.
 */
@Composable
private fun Caption(banner: MatchBanner, onFinished: () -> Unit) {
    val art = LocalBannerArt.current
    val image = rememberCaption(art, banner)

    if (art == null) {
        LaunchedEffect(banner) { onFinished() }
        return
    }
    if (image == null) {
        LaunchedEffect(banner) {
            delay(banner.totalMillis.toLong())
            onFinished()
        }
        return
    }

    Box(
        modifier = Modifier.fillMaxSize().testTag(MATCH_BANNER_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        BannerImage(banner = banner, image = image, onFinished = onFinished)
    }
}

/**
 * One caption, played through and then reported finished.
 *
 * The three phases are driven from a single coroutine rather than from three
 * `animate*AsState` calls, because they are a *sequence* — the exit cannot begin before
 * the hold has elapsed, and expressing that with target-value animations means a state
 * machine whose states exist only to order two animations.
 */
@Composable
private fun BannerImage(
    banner: MatchBanner,
    image: androidx.compose.ui.graphics.ImageBitmap,
    onFinished: () -> Unit,
) {
    val scale = remember(banner) { Animatable(banner.motion.enterScale) }
    val alpha = remember(banner) { Animatable(0f) }
    val offset = remember(banner) { Animatable(banner.motion.enterOffset) }
    val rotation = remember(banner) { Animatable(0f) }

    LaunchedEffect(banner) {
        if (banner.leadInMillis > 0) delay(banner.leadInMillis.toLong())

        val enter = tween<Float>(banner.enterMillis, easing = LinearEasing)
        launch { scale.animateTo(1f, enter) }
        launch { offset.animateTo(0f, enter) }
        alpha.animateTo(1f, enter)

        delay(banner.holdMillis.toLong())

        val exit = tween<Float>(banner.exitMillis, easing = LinearEasing)
        // `SuddenDeathAnim` tilts as it goes; nothing else rotates at all.
        if (banner.motion == BannerMotion.ZOOM_BOUNCE) {
            launch { rotation.animateTo(BOUNCE_DEGREES, exit) }
        }
        launch { scale.animateTo(banner.motion.exitScale, exit) }
        launch { offset.animateTo(banner.motion.exitOffset, exit) }
        alpha.animateTo(0f, exit)

        onFinished()
    }

    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .testTag(matchBannerTestTag(banner))
            // Named so a screen reader announces the rule rather than skipping it; the
            // caption is the only place some of these rules are ever spelled out.
            .semantics { contentDescription = banner.name }
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                rotationZ = rotation.value
                if (banner.motion.isVertical) {
                    translationY = offset.value * size.height
                } else {
                    translationX = offset.value * size.width
                }
            },
    )
}

/**
 * Where a motion starts, as a multiple of the caption's own size.
 *
 * Fractions of the *image* rather than of the stage, which is a deliberate departure: the
 * AS3 works in `stage.width` because Starling gave it a fixed 1136x640 stage, and this app
 * runs on whatever the device is. A slide keyed to screen width crosses a tablet in the
 * same 200 ms it crosses a phone and reads as twice as fast; keyed to the caption, it
 * travels its own length either way.
 */
private val BannerMotion.enterOffset: Float
    get() = when (this) {
        BannerMotion.SLIDE_RIGHT -> -SLIDE_DISTANCE
        BannerMotion.SLIDE_LEFT -> SLIDE_DISTANCE
        else -> 0f
    }

private val BannerMotion.exitOffset: Float
    get() = when (this) {
        BannerMotion.SLIDE_RIGHT -> SLIDE_DISTANCE
        BannerMotion.SLIDE_LEFT -> -SLIDE_DISTANCE
        BannerMotion.ZOOM_UP -> -SLIDE_DISTANCE
        BannerMotion.ZOOM_DOWN -> SLIDE_DISTANCE
        else -> 0f
    }

/** `gfx.scaleX = 2` in every `init`, and 3 for Sudden Death. Slides do not scale. */
private val BannerMotion.enterScale: Float
    get() = when (this) {
        BannerMotion.SLIDE_LEFT, BannerMotion.SLIDE_RIGHT -> 1f
        BannerMotion.ZOOM_BOUNCE -> 3f
        else -> 2f
    }

/** Only the four `ZOOM` captions scale back up on the way out; the rest just fade. */
private val BannerMotion.exitScale: Float
    get() = if (this == BannerMotion.ZOOM) 2f else 1f

private val BannerMotion.isVertical: Boolean
    get() = this == BannerMotion.ZOOM_UP || this == BannerMotion.ZOOM_DOWN

/**
 * `15 * Math.PI / 360` — `SuddenDeathAnim.as:50`.
 *
 * Which is 7.5°, not the 15° the expression looks like: the AS3 idiom for degrees is
 * `deg * Math.PI / 180`, and this one has 360 in the denominator. Transcribed as what it
 * computes rather than as what it appears to mean.
 */
private const val BOUNCE_DEGREES = 7.5f

/** One caption's width of travel, which is what the AS3's edge-to-centre slide amounts to. */
private const val SLIDE_DISTANCE = 1f

/**
 * Four captions is already more than one placement can honestly earn.
 *
 * A Same plus a Combo is two; the extra headroom covers a rule caption landing on the same
 * frame. Beyond that the queue is describing a match that has moved on.
 */
private const val QUEUE_LIMIT = 4
