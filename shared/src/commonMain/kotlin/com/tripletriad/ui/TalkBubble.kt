package com.tripletriad.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** The bubble while it is on screen. */
const val TALK_BUBBLE_TEST_TAG: String = "talk-bubble"

/**
 * `TalkAnim` — an NPC saying one line.
 *
 * The bubble scales down from 1.5x as it fades in, the speaker's name and their line appear once
 * it has arrived, both are up for five seconds, and then it shrinks away to half size. Its three
 * callers in the original are the two group ladders and `TutorialScreen`, which is what this
 * unblocks: the tutorial *is* a sequence of these over a scripted match.
 *
 * ### Why the text is text and not a picture
 *
 * Unlike the twenty rule captions, which are images of words in four languages, this one is a
 * frame with live text drawn into it — `TextField(450, 75, _message, 'Raleway', 16, …)`. So the
 * asset is one file for every language, and the line arrives as a string rather than as a texture
 * id. That is the original's design and it is the better one; the captions are pictures only
 * because they are stylised wordmarks.
 *
 * ### Why the text appears after the bubble
 *
 * `predispose()` — the entry tween's `onComplete` — is what constructs both `TextField`s. A line
 * readable while its bubble is still flying in would be readable at 1.5x and moving, which is
 * where the original's structure is making an argument rather than an accident.
 *
 * @param message the line, already translated. Wrapped rather than clipped: the AS3 sizes its
 *   field 450x75 with `autoSize = VERTICAL`, so a long line grows downward instead of vanishing.
 * @param speaker who is talking. `RED_PLAYER_NAME` in a ladder match, `STR_NPC_TT_Master` in the
 *   tutorial.
 * @param onFinished the line is done. Called once, from this composable's own coroutine, so a
 *   caller can advance a script on it.
 */
@Composable
internal fun TalkBubble(message: String, speaker: String, onFinished: () -> Unit) {
    val art = LocalCardArt.current
    val scale = remember(message) { Animatable(ENTER_SCALE) }
    val alpha = remember(message) { Animatable(0f) }
    var textUp by remember(message) { mutableStateOf(false) }

    LaunchedEffect(message) {
        val entering = tween<Float>(ENTER_MILLIS, easing = LinearEasing)
        alpha.animateTo(1f, entering)
        scale.animateTo(1f, entering)

        textUp = true // predispose(): the two TextFields are built here, not before.
        delay(HOLD_MILLIS.toLong())
        textUp = false // setTimeout(… visible = false, 5000)

        val leaving = tween<Float>(EXIT_MILLIS, easing = LinearEasing)
        alpha.animateTo(0f, leaving)
        scale.animateTo(EXIT_SCALE, leaving)

        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().testTag(TALK_BUBBLE_TEST_TAG),
        // `gfx.y = stage.height / 6` — high on the screen, over whatever is behind it.
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(top = BubbleTop)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                },
            contentAlignment = Alignment.Center,
        ) {
            Frame(art?.talk)
            if (textUp) {
                Line(message = message, speaker = speaker)
            }
        }
    }
}

/**
 * The bubble itself, at its authored size.
 *
 * A fixed size rather than one derived from the text, because the artwork is a **fixed frame**
 * with a tail and a highlight — a nine-slice would need slice metrics the AS3 never had, since it
 * draws the texture at 1:1 and lets the text overflow if it must.
 */
@Composable
private fun Frame(bitmap: ImageBitmap?) {
    val modifier = Modifier.size(BubbleWidth, BubbleHeight)
    if (bitmap == null) {
        // No artwork, no frame — the line is still readable, which is the part that matters.
        Box(modifier = modifier)
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    }
}

/** The speaker above their line: white over the frame's dark lip, dark grey inside it. */
@Composable
private fun Line(message: String, speaker: String) {
    Column(
        modifier = Modifier.widthIn(max = TextWidth).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = speaker,
            color = SpeakerText,
            fontSize = SpeakerSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
        Text(
            text = message,
            color = MessageText,
            fontSize = MessageSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** `Starling.juggler.tween(gfx, 0.4, …)` — in, and out again. */
private const val ENTER_MILLIS = 400
private const val EXIT_MILLIS = 400

/** `delay: 5` on the exit tween, and the `setTimeout` that hides the text at the same moment. */
private const val HOLD_MILLIS = 5_000

/** `gfx.scaleX = gfx.scaleY = 1.5` before the entry tween, and `0.5` after the exit one. */
private const val ENTER_SCALE = 1.5f
private const val EXIT_SCALE = 0.5f

/** The `talk_basic.tex` texture is 544x144. Drawn at its authored size — see [Frame]. */
private val BubbleWidth = 272.dp
private val BubbleHeight = 72.dp

/** `TextField(450, …)` against a 544-wide frame: the text stops short of the border. */
private val TextWidth = 225.dp

/** `stage.height / 6`, as a distance from the top rather than a fraction of an unknown stage. */
private val BubbleTop = 48.dp

/** `0xffffff`, `14` — the name sits on the frame's darker lip. */
private val SpeakerText = Color(0xFFFFFFFF)
private val SpeakerSize = 12.sp

/** `0x202020`, `16` — the line is inside the frame, which is light. */
private val MessageText = Color(0xFF202020)
private val MessageSize = 13.sp
