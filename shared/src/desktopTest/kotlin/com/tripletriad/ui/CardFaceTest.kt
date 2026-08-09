package com.tripletriad.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.model.Card
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

/**
 * That a card is drawn with **its own** picture.
 *
 * This exists because it was not. [rememberCardFace] used `produceState`, whose value lives in an
 * unkeyed `remember`: changing the keys restarts the producer but keeps the old value, and the
 * producer only loaded when the value was null. So a composable slot handed a second card went on
 * drawing the first card's artwork.
 *
 * Nothing in the suite could see it. The bug needs a *reused* slot, which the UI produces
 * constantly — hand slots close up as cards are played, so playing one card hands every slot
 * behind it a different card — and no assertion looked at which bitmap a slot had. It was found
 * by playing the game.
 *
 * Asserting on the bitmap identity rather than on a screenshot is what makes this cheap: the
 * decoded [ImageBitmap] is cached per texture id, so "the right picture" is exactly "the same
 * instance `CardArt` hands out for this card".
 */
@OptIn(ExperimentalTestApi::class)
class CardFaceTest {
    private val art = runBlocking { loadCardArt() }
    private val cards = runBlocking { loadCardCatalog() }.all

    @Test
    fun theFaceFollowsTheCardWhenASlotIsReused() = runComposeUiTest {
        val first = cards.first()
        val second = cards.first { it.textureId != first.textureId }
        val card = mutableStateOf(first)
        var drawn: ImageBitmap? = null

        setContent { drawn = rememberCardFace(art, card.value) }

        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { drawn != null }
        val firstFace = drawn
        assertSame(art.cachedFace(first), firstFace, "the first card's own artwork")

        // The slot is kept — only the card in it changes, exactly as when a hand closes up.
        card.value = second
        waitUntil(timeoutMillis = UI_TIMEOUT_MS) { drawn != null && drawn !== firstFace }

        assertSame(art.cachedFace(second), drawn, "the second card's own artwork")
        assertNotEquals(firstFace, drawn, "two different cards cannot share one bitmap")
    }

    /**
     * The other half of the same fix: an already-decoded face is returned in the *first*
     * composition, without a null frame. A hand redraws on every turn change, and a card that
     * blinked out and back each time would be worse than the stale picture.
     */
    @Test
    fun anAlreadyDecodedFaceIsReturnedWithoutABlankFrame() = runComposeUiTest {
        val card: Card = cards.last()
        art.face(card)
        val frames = mutableListOf<ImageBitmap?>()

        setContent { frames += rememberCardFace(art, card) }

        waitForIdle()
        assertSame(art.cachedFace(card), frames.first(), "the very first frame is already drawn")
    }
}
