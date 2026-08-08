package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tripletriad.model.MatchSetup
import com.tripletriad.model.MatchState
import kotlinx.coroutines.delay

/*
 * When each animation plays, as opposed to what it looks like.
 *
 * `MatchBannerOverlay` draws one thing at a time and `MatchBanner` says how long each takes; this
 * is the layer between them and the match — which moment owes which animation, how a repeat is
 * told from a replay, and how long the rest of the screen has to wait for the intro. It sat inside
 * `MatchScreen` until that file passed detekt's limit on how many functions one file should hold,
 * and the split is the honest one: nothing here draws, and nothing here is about a match either.
 */

/**
 * The captions to play right now, as an event the overlay can tell from the last one.
 *
 * Pushed from an effect rather than derived in place, because "this is a **new** event"
 * needs a counter and a counter written during composition is a counter that counts
 * recompositions. Two openings are the case that forces the issue: a sudden-death rematch
 * resets the board to placement 0 with no last play, so its opening chain is structurally
 * equal to the first match's, and [MatchBannerOverlay] keys on equality — without [epoch]
 * the rematch would open in silence.
 *
 * Its own composable rather than four lines inside [MatchScreen] for the same reason
 * [turnClock] is: the match function is at the branch count detekt allows, and this is
 * separable — nothing above it needs to know that captions are sequenced at all.
 *
 * @param key resets both the queue and the counter. The match, so a rematch starts over.
 */
@Composable
internal fun bannerQueue(key: Any, state: MatchState, setup: MatchSetup): BannerEvent? {
    var event by remember(key) { mutableStateOf<BannerEvent?>(null) }
    var epoch by remember(key) { mutableStateOf(0) }

    LaunchedEffect(key, state.placement, state.lastPlay) {
        animationsFor(state, setup)
            .takeIf { it.isNotEmpty() }
            ?.let {
                event = BannerEvent(epoch, it)
                epoch++
            }
    }

    return event
}

/**
 * What this state owes the player, whichever moment of the match it is.
 *
 * Derived from state rather than fired from the placement handler on purpose. The handler
 * is not the only thing that places a card — the turn timer auto-plays, and the opponent
 * plays on its own — and an animation wired to one call site would be missing from the
 * other two. `lastPlay` is set by whichever of them moved, and is also what distinguishes a
 * board nothing has been played on yet (the intro sequence) from one that has.
 *
 * Read twice per placement, and deliberately cheap enough for that: [MatchBannerOverlay]
 * needs to know *what* to play and the opponent needs to know how long to wait for it.
 * Answering both from the same function is what stops the AI from moving over a caption
 * that is still describing the move before.
 *
 * The intro comes from [MatchSetup.intro] rather than from the rules, because the setup
 * knows what actually happened — see [MatchBanner.forIntroStep]. [MatchSetup.coinFlip] is
 * what fills in the one step that has no caption; it is null on a sudden-death rematch,
 * where the turn order carries over and no flip is drawn, and the step is absent from the
 * list there anyway. Guarding on both is belt and braces for a pair that must agree.
 */
internal fun animationsFor(state: MatchState, setup: MatchSetup): List<MatchAnimation> =
    if (state.lastPlay == null) {
        introAnimations(setup)
    } else {
        MatchBanner.afterPlacement(state).asAnimations()
    }

/**
 * The pre-match sequence: a caption per rule, the coin flip, and Start.
 *
 * Separate from [animationsFor] because it is also what the match has to *wait* for — see
 * [introFinished] — and computing a duration by asking for the animations of a state that has
 * not been played on is the sort of indirection that stops being true the moment either changes.
 */
internal fun introAnimations(setup: MatchSetup): List<MatchAnimation> =
    setup.intro.mapNotNull { step ->
        MatchBanner.forIntroStep(step)?.let(MatchAnimation::Caption)
            ?: setup.coinFlip?.let(MatchAnimation::Toss)
    }

/**
 * False while the pre-match announcements are playing, true once the match has begun.
 *
 * `letsGetStarted` calls `nextTurn` after the cascade, and `nextTurn` is what arms the clocks
 * (`BaseMatchScreen.as:250`, `:377-387`). A timer to the *right* of that call is what this stands
 * in for.
 *
 * It gates the clock and nothing else. The board stays live — the captions are drawn over it
 * without consuming touches, deliberately, so a player who already knows the rules can open with
 * a card while Reverse is still on screen. What they must not do is lose part of their first turn
 * to an announcement.
 *
 * @param key the match, so a rematch waits through its own intro again.
 */
@Composable
internal fun introFinished(key: Any, setup: MatchSetup): Boolean {
    // Keyed on the setup as well: a sudden-death rematch replaces it in place without changing
    // the match, and plays a second intro that has to be waited through in turn.
    var done by remember(key, setup) { mutableStateOf(false) }

    LaunchedEffect(key, setup) {
        done = false
        delay(introAnimations(setup).sumOf { it.totalMillis }.toLong())
        done = true
    }

    return done
}
