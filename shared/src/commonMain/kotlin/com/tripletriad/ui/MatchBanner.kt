package com.tripletriad.ui

import com.tripletriad.model.CaptureKind
import com.tripletriad.model.CardColor
import com.tripletriad.model.CardType
import com.tripletriad.model.MatchIntroStep
import com.tripletriad.model.MatchOutcome
import com.tripletriad.model.MatchSetup
import com.tripletriad.model.MatchState
import com.tripletriad.model.PlayResult
import com.tripletriad.model.TypeRule

/**
 * The twenty full-screen captions the match shows: SAME, PLUS, COMBO, whose turn it is,
 * who won, and each special rule as it fires.
 *
 * ### Twenty-four classes, one animation
 *
 * `tto/anims/` holds 24 files averaging 56 lines, and nineteen of them are the *same*
 * fifty lines with a different texture id: load an image, tween it in, hold, tween it
 * out, dispose. The plan in `10-PHASE-6-ANIMATIONS.md` reads that directory as 24 pieces
 * of work. It is four motion shapes and a table — which is what this file is.
 *
 * The five that genuinely differ are not here: `PileOuFace` (three cards fanning in),
 * `TalkAnim` (a bubble with live text), `UnlockCardAnim` (a card, not a caption), `Mogu`
 * (a sprite sheet) and `SuddenDeathAnim` (the banner shape plus a bounce). They get their
 * own composables.
 *
 * ### Why the durations are transcribed rather than rounded
 *
 * Every number below is the AS3 tween it came from, named in the KDoc so it can be
 * checked against the source rather than trusted. They are not uniform — the entry is
 * 0.2s, 0.3s or 0.4s and the hold is 0.4s to 0.8s depending on the banner — and that
 * unevenness is deliberate on the original's part: the turn indicator has to get out of
 * the way quickly, and a win card is allowed to linger. Normalising them would be a
 * redesign wearing the clothes of a port.
 *
 * ### How to read an AS3 tween pair
 *
 * Worth stating once, because getting it wrong changes every number here. The exit tween
 * is created inside `predispose()`, which is the *entry's* `onComplete` — so the exit's
 * `delay` is measured from the moment the entry finishes and is therefore the **hold**.
 * The entry's own `delay`, where it has one, is a wait before anything appears at all:
 * [leadInMillis]. Read as if both delays ran from the same clock, `ComboAnim` looks like
 * it fades out before it fades in; read correctly, it simply waits 0.8s for the Same or
 * Plus caption that precedes it to clear.
 */
enum class MatchBanner(
    /** The texture id, which is also the file name — see `tools/import_rule_banners.py`. */
    val textureId: String,
    val motion: BannerMotion,
    /** Milliseconds. `Starling.juggler.tween`'s first argument, times 1000. */
    val enterMillis: Int,
    /** How long it sits at full opacity before leaving. The exit tween's `delay`. */
    val holdMillis: Int,
    val exitMillis: Int,
    /** A wait before it appears at all. The *entry* tween's `delay`. Usually none. */
    val leadInMillis: Int = 0,
) {
    /** `StartAnim` — 121601. */
    START("121601", BannerMotion.ZOOM, enterMillis = 400, holdMillis = 600, exitMillis = 400),

    /** `BlueTurnAnim` — 121602. Enters from the left, leaves to the right. */
    BLUE_TURN(
        "121602",
        BannerMotion.SLIDE_RIGHT,
        enterMillis = 200,
        holdMillis = 800,
        exitMillis = 200,
    ),

    /** `RedTurnAnim` — 121603. The mirror: enters from the right, leaves to the left. */
    RED_TURN(
        "121603",
        BannerMotion.SLIDE_LEFT,
        enterMillis = 200,
        holdMillis = 800,
        exitMillis = 200,
    ),

    /** `BlueWinAnim` — 121604. */
    BLUE_WIN(
        "121604",
        BannerMotion.ZOOM_FADE,
        enterMillis = 400,
        holdMillis = 800,
        exitMillis = 400,
    ),

    /** `RedWinAnim` — 121605. */
    RED_WIN(
        "121605",
        BannerMotion.ZOOM_FADE,
        enterMillis = 400,
        holdMillis = 800,
        exitMillis = 400,
    ),

    /** `DrawAnim` — 121606. */
    DRAW("121606", BannerMotion.ZOOM_FADE, enterMillis = 400, holdMillis = 800, exitMillis = 400),

    /** `AllOpenAnim` — 121611. Waits 0.3s first, which is the only lead-in besides Combo's. */
    ALL_OPEN(
        "121611",
        BannerMotion.SLIDE_LEFT,
        enterMillis = 300,
        holdMillis = 800,
        exitMillis = 200,
        leadInMillis = 300,
    ),

    /** `ThreeOpenAnim` — 121612. Identical to [ALL_OPEN] but for the texture. */
    THREE_OPEN(
        "121612",
        BannerMotion.SLIDE_LEFT,
        enterMillis = 300,
        holdMillis = 800,
        exitMillis = 200,
        leadInMillis = 300,
    ),

    /** `ReverseAnim` — 121613. */
    REVERSE(
        "121613",
        BannerMotion.ZOOM_FADE,
        enterMillis = 400,
        holdMillis = 600,
        exitMillis = 400,
    ),

    /** `ChaosAnim` — 121614. */
    CHAOS("121614", BannerMotion.ZOOM_FADE, enterMillis = 400, holdMillis = 600, exitMillis = 400),

    /** `OrderAnim` — 121615. */
    ORDER("121615", BannerMotion.ZOOM_FADE, enterMillis = 400, holdMillis = 600, exitMillis = 400),

    /** `RandomAnim` — 121616. */
    RANDOM("121616", BannerMotion.ZOOM_FADE, enterMillis = 400, holdMillis = 600, exitMillis = 400),

    /** `SwapAnim` — 121617. */
    SWAP("121617", BannerMotion.ZOOM_FADE, enterMillis = 400, holdMillis = 600, exitMillis = 400),

    /**
     * `ComboAnim` — 121618. [SAME]'s timing with a 0.8s lead-in.
     *
     * The lead-in is the whole point of it: a combo is always *caused* by a Same or a
     * Plus, so this caption waits for that one's 0.4s entry and 0.6s hold to be nearly
     * done before it starts. Shortening it stacks two captions on top of each other.
     */
    COMBO(
        "121618",
        BannerMotion.ZOOM,
        enterMillis = 400,
        holdMillis = 600,
        exitMillis = 200,
        leadInMillis = 800,
    ),

    /** `PlusAnim` — 121619. */
    PLUS("121619", BannerMotion.ZOOM, enterMillis = 400, holdMillis = 600, exitMillis = 200),

    /** `SameAnim` — 121620. Also shown for Same Wall, which has no banner of its own. */
    SAME("121620", BannerMotion.ZOOM, enterMillis = 400, holdMillis = 600, exitMillis = 200),

    /** `FallenAceAnim` — 121621. */
    FALLEN_ACE(
        "121621",
        BannerMotion.ZOOM_FADE,
        enterMillis = 400,
        holdMillis = 600,
        exitMillis = 400,
    ),

    /** `AscensionAnim` — 121622. Zooms in, then leaves *upward* (`y:0`). */
    ASCENSION(
        "121622",
        BannerMotion.ZOOM_UP,
        enterMillis = 400,
        holdMillis = 400,
        exitMillis = 200,
    ),

    /** `DescensionAnim` — 121623. The mirror: leaves downward (`y:stage.height`). */
    DESCENSION(
        "121623",
        BannerMotion.ZOOM_DOWN,
        enterMillis = 400,
        holdMillis = 400,
        exitMillis = 200,
    ),

    /**
     * `SuddenDeathAnim` — 121624.
     *
     * Zooms from 3x rather than 2x and adds a bounced rotation on the way out
     * (`Transitions.EASE_IN_OUT_BOUNCE`, `15 * PI / 360` — 7.5°, not the 15° the constant
     * reads as). [BannerMotion.ZOOM_BOUNCE] carries both.
     */
    SUDDEN_DEATH(
        "121624",
        BannerMotion.ZOOM_BOUNCE,
        enterMillis = 400,
        holdMillis = 600,
        exitMillis = 400,
    ),
    ;

    /** How long the whole thing lasts, which is what a caller has to wait for. */
    val totalMillis: Int get() = leadInMillis + enterMillis + holdMillis + exitMillis

    companion object {
        /** The banner for a win, a loss or a tie. Null is a draw, as `MatchState` means it. */
        fun outcome(winner: CardColor?): MatchBanner = when (winner) {
            CardColor.BLUE -> BLUE_WIN
            CardColor.RED -> RED_WIN
            null -> DRAW
        }

        /** Whose turn it is. */
        fun turn(player: CardColor): MatchBanner =
            if (player == CardColor.BLUE) BLUE_TURN else RED_TURN

        /**
         * The caption a capture earns, or null for one that earns none.
         *
         * Same Wall shows [SAME] because the original has no separate texture for it —
         * `TTOCore` tags the capture differently but `animate` plays the same animation.
         * A basic capture is silent, which is what makes the special ones read as special.
         */
        fun forCapture(kind: CaptureKind): MatchBanner? = when (kind) {
            CaptureKind.SAME, CaptureKind.SAME_WALL -> SAME
            CaptureKind.PLUS -> PLUS
            CaptureKind.COMBO -> COMBO
            CaptureKind.BASIC -> null
        }

        /**
         * Every caption one placement earns, in the order they play.
         *
         * ### Why this is a list and not a single banner
         *
         * Because a placement can earn two. A Same that starts a combo owes the player
         * SAME **and** COMBO, and [COMBO]'s 0.8s lead-in exists precisely so it lands
         * after the caption that caused it. Collapsing to "the most important one" would
         * throw away the pairing the timings were designed around.
         *
         * ### Why each caption appears at most once
         *
         * A placement that flips three cards by Same is one Same, not three: the
         * captures are per *card*, and the caption is about the *rule*. The original gets
         * this for free by creating one `SameAnim` before the flip loop; here it is a
         * `distinct`, and the reason is worth naming because the loop is right there in
         * `Resolution.captures` waiting to be mapped one-to-one.
         *
         * The order is [SAME]/[PLUS] before [COMBO] regardless of capture order, because
         * a combo is by definition propagated *from* one of the others.
         */
        fun captionsFor(play: PlayResult): List<MatchBanner> {
            val kinds = play.captures.map { it.kind }.toSet()
            val direct = kinds.firstNotNullOfOrNull { kind ->
                forCapture(kind).takeIf { it != COMBO }
            }
            val combo = if (CaptureKind.COMBO in kinds) COMBO else null
            return listOfNotNull(direct, combo)
        }

        /**
         * The caption an intro step is announced with, or null for the one that has none.
         *
         * ### Why the *step* and not the rules
         *
         * Because [MatchSetup.introSteps] already answers "what does this match owe the
         * player before the first move", and it answers it from the setup that actually
         * happened rather than from the rule set. That distinction has teeth: a
         * sudden-death rematch is played under a Random rule whose hand was **not**
         * re-dealt, so the rules say announce it and the setup says do not. This file
         * asking `rules.random` again would be a second copy of a table that is already
         * written down, free to drift from it, and wrong in exactly that case.
         *
         * [MatchIntroStep.COIN_FLIP] is the null: it is `PileOuFace`, three cards rather
         * than a caption, and it plays through the same queue as its own kind of
         * animation — see [MatchAnimation].
         */
        fun forIntroStep(step: MatchIntroStep): MatchBanner? = when (step) {
            MatchIntroStep.RANDOM -> RANDOM
            MatchIntroStep.ALL_OPEN -> ALL_OPEN
            MatchIntroStep.THREE_OPEN -> THREE_OPEN
            MatchIntroStep.ORDER -> ORDER
            MatchIntroStep.CHAOS -> CHAOS
            MatchIntroStep.REVERSE -> REVERSE
            MatchIntroStep.FALLEN_ACE -> FALLEN_ACE
            MatchIntroStep.SWAP -> SWAP
            MatchIntroStep.START -> START
            MatchIntroStep.COIN_FLIP -> null
        }

        /**
         * What a state owes the player *after* a card has been placed on it.
         *
         * The tail of `BaseMatchScreen`'s per-move sequence, in its order: the capture
         * captions from `animate`, then `ascensionPhase` (`:330-360`), then `nextTurn`
         * (`:363-395`) — which either announces the next side or ends the match.
         *
         * Deriving this from the state rather than firing it from the placement handler is
         * what makes it right for all three ways a card gets played: the player tapping,
         * the turn timer auto-playing, and the opponent moving. Only [MatchState.lastPlay]
         * knows which of them it was, and none of them has to remember to call this.
         *
         * Returns nothing for a state nothing has been played on yet, which the intro
         * sequence covers instead — see [forIntroStep].
         */
        fun afterPlacement(state: MatchState): List<MatchBanner> {
            val play = state.lastPlay ?: return emptyList()
            return buildList {
                addAll(captionsFor(play))
                ascension(state.rules.typeRule, play.card.type)?.let(::add)
                when (val result = state.outcome()) {
                    null -> state.currentPlayer?.let { add(turn(it)) }
                    is MatchOutcome.Win -> add(outcome(result.winner))
                    is MatchOutcome.Draw -> add(DRAW)
                    // Both, in that order: `PVEMatchScreen.as:63-68` announces the draw and
                    // then that it is not over. The rematch's own `opening` follows.
                    is MatchOutcome.SuddenDeath -> {
                        add(DRAW)
                        add(SUDDEN_DEATH)
                    }
                }
            }
        }

        /**
         * The Ascension or Descension caption, which only a **typed** card earns.
         *
         * `ascensionPhase` guards on `tile.card.type` and skips the animation *and* the
         * 1.2s wait when the card has none (`:337`, `:344`, `:357`). Under FF8 rules no
         * card has a type at all, so this is silent for a whole collection — which is
         * correct, and is why the guard is not an oversight worth "fixing".
         */
        private fun ascension(rule: TypeRule, type: CardType?): MatchBanner? = when {
            type == null -> null
            rule == TypeRule.ASCENSION -> ASCENSION
            rule == TypeRule.DESCENSION -> DESCENSION
            else -> null
        }
    }
}

/**
 * The four shapes the twenty banners move in, plus Sudden Death's variant.
 *
 * Named after what they do rather than after the class they came from, because five
 * different rules share [ZOOM_FADE] and naming it `ReverseMotion` would suggest otherwise.
 */
enum class BannerMotion {
    /** Scale 2→1 in, scale 1→2 out. `SameAnim`, `PlusAnim`, `ComboAnim`, `StartAnim`. */
    ZOOM,

    /** Scale 2→1 in, then fade with no scale. The rule captions and the win cards. */
    ZOOM_FADE,

    /** Scale 3→1 in, then a bounced 7.5° tilt while fading. `SuddenDeathAnim` only. */
    ZOOM_BOUNCE,

    /** Scale 2→1 in, then slide off the top. `AscensionAnim`. */
    ZOOM_UP,

    /** Scale 2→1 in, then slide off the bottom. `DescensionAnim`. */
    ZOOM_DOWN,

    /** In from the left edge, out to the right. `BlueTurnAnim`. */
    SLIDE_RIGHT,

    /** In from the right edge, out to the left. `RedTurnAnim`, `AllOpenAnim`, `ThreeOpenAnim`. */
    SLIDE_LEFT,
}
