package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tripletriad.data.CampaignMessages
import com.tripletriad.data.PveMatches
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.CardColor
import com.tripletriad.model.CoinFlip
import com.tripletriad.model.GameRules
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchAiOptions
import com.tripletriad.model.MatchResult
import com.tripletriad.model.MatchState
import kotlin.time.Duration

/**
 * A match that has been written in advance.
 *
 * One parameter on [MatchScreen] rather than six, and **pure data**: no lambdas, because the script
 * is a `remember` key down there and a lambda rebuilt on each recomposition would look like a new
 * script and re-deal the hands.
 *
 * The AS3 does all of this by subclassing: `TutorialScreen`, `CCGroupMatchScreen` and
 * `GSGroupMatchScreen` all extend `PVEMatchScreen` and override between two and four methods.
 * Composables do not subclass, and the alternative worth avoiding was three copies of the same
 * 400-line match screen with a few lines different in each.
 *
 * **Every field is optional except the speaker**, because the two kinds of scripted match want
 * almost disjoint halves of it: the tutorial fixes the deal, the flip, the clock and the opponent's
 * strategy and says a great deal; a tournament rung changes none of those and says at most two
 * sentences.
 *
 * Every field is read through one of the nullable extensions below rather than with an elvis at the
 * call site. That is not decoration: six `script?.x ?: y` in one composable is six branches, and
 * [MatchScreen] is already at the cyclomatic complexity detekt allows.
 *
 * @property speakerKey whose name goes on the bubbles. The opponent's own, rather than the
 *   `STR_NPC_TT_Master` the AS3 writes into every `new TalkAnim(...)`: which opponent speaks
 *   depends on the collection and, in a ladder, on the rung.
 * @property deck the five cards the player is dealt, or null to ask as usual. Fixed in the
 *   tutorial, so the deck selector never opens and the lesson can talk about what is in the hand;
 *   a tournament rung deals normally.
 * @property firstPlayer who moves first, or null for a real toss. [CardColor.RED] in the tutorial,
 *   so the opponent has something to demonstrate before the player is asked to do anything. The
 *   ladders call `pof.randomRolls()` and take their chances.
 * @property turnLimit how long a turn lasts, or null for the ordinary thirty seconds.
 *   `TutorialScreen.as:58-59` doubles it, which is the original compensating for its own lesson:
 *   the lines play *during* the turn they describe, and a thirty-second clock would time out
 *   behind them.
 * @property aiOptions how the opponent plays. [MatchAiOptions.TUTOR] loses on purpose; a ladder
 *   opponent plays to win like any other.
 * @property lesson the lines to speak before a given placement. Keys, not sentences — resolving
 *   them needs [LocalStrings], which is not available where a script is built. A ladder's lines
 *   *are* their own keys, deliberately; see [CampaignMessages].
 * @property outcomeLines what is said once the result is known — `talk(_messages.win)` and its two
 *   siblings, which the ladders play from `endGame` just before the panel opens. A map rather than
 *   a function so the whole type stays comparable.
 */
internal data class MatchScript(
    val speakerKey: String,
    val deck: List<Int>? = null,
    val firstPlayer: CardColor? = null,
    val turnLimit: Duration? = null,
    val aiOptions: MatchAiOptions = MatchAiOptions(),
    val lesson: Lesson = Lesson.Silent,
    val outcomeLines: Map<MatchResult, String> = emptyMap(),
)

/**
 * What the end panel offers where an ordinary match offers Rematch.
 *
 * Both scripted screens replace that control rather than adding a third: `TutorialRematchPanel`'s
 * two buttons are Help and Quit, and `CCGroupRematchPanel`'s are Next Match and Quit — each in the
 * places Rematch and Back had. **Null means no control at all**, which is the last rung of a
 * ladder: `if (_params.NEXT_STEP < 7)` simply does not build the button.
 */
internal class ScriptExit(val labelKey: String, val onLeave: () -> Unit)

/**
 * What is said, and when.
 *
 * Keyed on the **placement index** rather than on a turn counter, because that is the number
 * [MatchState] actually carries. The AS3's `turn` is 1-based and pre-incremented, so its turn *n*
 * is placement *n − 1*.
 */
internal fun interface Lesson {
    /**
     * The string keys to speak before the placement at [placement].
     *
     * @param blueScore the player's score as the lines are about to play, which one line branches
     *   on — it is the sentence that congratulates a capture, and it must not appear if nothing was
     *   captured.
     */
    fun linesBefore(placement: Int, blueScore: Int): List<String>

    companion object {
        /** Says nothing — ten of the thirteen tournament rungs, and every ordinary match. */
        val Silent: Lesson = Lesson { _, _ -> emptyList() }

        /**
         * One line as the match opens and nothing after: a ladder's `messages.start`.
         *
         * `letsGetStarted` is where the ladders play it, which is after the whole pre-match
         * cascade and before the first card goes down — placement zero.
         */
        fun opening(line: String?): Lesson = Lesson { placement, _ ->
            listOfNotNull(line.takeIf { placement == FIRST_PLACEMENT })
        }
    }
}

/** Before any card is down. */
private const val FIRST_PLACEMENT = 0

/**
 * The hand to play with, or null when the player still has to be asked for one.
 *
 * A script always deals its own; otherwise the selector opens unless `RULE_RANDOM` is in force,
 * which draws from the whole collection and never asks (`BaseMatchScreen.as:120-135`).
 */
internal fun MatchScript?.deckFor(rules: GameRules, profile: GameSave): List<Int>? =
    this?.deck ?: PveMatches.playerDeck(profile).takeIf { rules.random }

/** The rigged flip a script needs, or null for a real toss. */
internal fun MatchScript?.flip(): CoinFlip? = this?.firstPlayer?.let(CoinFlip::forced)

/** How the opponent plays: the script's strategy, or the ordinary one. */
internal fun MatchScript?.aiOptions(): MatchAiOptions = this?.aiOptions ?: MatchAiOptions()

/** The script's turn length, or [otherwise]. */
internal fun MatchScript?.turnLimitOr(otherwise: Duration): Duration = this?.turnLimit ?: otherwise

/**
 * The end panel's first action: a script's own exit, or an ordinary rematch.
 *
 * Null when a script says there is nowhere to go, which is the end of a ladder. The choice lives
 * here for the reason given on [MatchScript]: one branch fewer in [MatchScreen].
 */
internal fun nextAction(
    script: MatchScript?,
    exit: ScriptExit?,
    rematch: () -> Unit,
): ScriptExit? = if (script == null) ScriptExit(StringKeys.REMATCH, rematch) else exit

/** What is said before [state]'s next placement. Empty without a script. */
internal fun MatchScript?.linesBefore(state: MatchState): List<String> =
    this?.lesson?.linesBefore(state.placement, state.score.blue).orEmpty()

/**
 * Plays a lesson's lines one after another, as bubbles.
 *
 * Sequential and gapped, matching the original's `setTimeout(talk, 6100, n)` cascade: a bubble
 * lives 5.8s — 0.4s in, 5s up, 0.4s out — and the next is scheduled at 6.1s, so there are 300ms of
 * quiet between two lines. Reproduced rather than run back-to-back because the pause is what makes
 * two paragraphs read as two paragraphs.
 *
 * ### The clock is deliberately not stopped
 *
 * Unlike the pre-match captions — which [MatchScreen] does hold the turn timer behind — a lesson
 * line plays *during* the turn it is explaining, and the original's answer is to double the turn to
 * sixty seconds rather than to pause it ([MatchScript.turnLimit]). Pausing would be the larger
 * change and would also stop the bar moving for a minute at a time, which reads as a freeze.
 *
 * @param key restarts the queue. The placement, so each turn's lines play once.
 * @param enabled whether the match has actually begun — false while the pre-match captions are
 *   still playing. The gate lives here rather than at the call site for the reason given on
 *   [MatchScript]: one branch fewer in a composable that has no room for another.
 */
@Composable
internal fun LessonBubbles(key: Any, lines: List<String>, script: MatchScript?, enabled: Boolean) {
    if (!enabled || lines.isEmpty() || script == null) return
    val strings = LocalStrings.current
    var spoken by remember(key) { mutableStateOf(0) }

    lines.getOrNull(spoken)?.let { line ->
        TalkBubble(
            message = strings[line],
            speaker = strings[script.speakerKey],
            // Keyed on the line as well as the queue: two consecutive lines are two bubbles, and
            // `TalkBubble` is keyed on its own message, so this only has to advance the index.
            onFinished = { spoken++ },
        )
    }
}

/**
 * What the opponent says once the result is known — `talk(_messages.win)` and its two siblings.
 *
 * Over the outcome panel rather than instead of it, which is what the original does: `endGame`
 * adds the `TalkAnim` and then schedules `rematch` behind `intervalDuration`, so for two seconds
 * both are on screen. The bubble sits at the top and the panel in the middle, so neither covers
 * the other.
 *
 * The line is a sentence rather than a key, and goes through [Strings.get] anyway: a key no bundle
 * defines resolves to itself, which is the documented fallback and is exactly what is wanted here.
 * The ladders' dialogue has no keys because the original never gave it any: see
 * [CampaignMessages].
 */
@Composable
internal fun OutcomeBubble(script: MatchScript?, result: MatchResult?) {
    val line = result?.let { script?.outcomeLines?.get(it) } ?: return
    val strings = LocalStrings.current
    var said by remember(line) { mutableStateOf(false) }

    if (!said) {
        TalkBubble(message = line, speaker = strings[checkNotNull(script).speakerKey]) {
            said = true
        }
    }
}

/**
 * How much time a turn's lines add before the opponent may move.
 *
 * `TutorialScreen.opponentPhase` schedules its AI behind its own talk cascade — `setTimeout(AI,
 * 18300)` after three lines at 6.1s apart, `setTimeout(AI, 12200)` after two. Both are the line
 * count times [TALK_STEP_MILLIS], so the rule is stated once here instead of as four magic numbers.
 */
internal fun lessonPause(lines: List<String>): Long = lines.size * TALK_STEP_MILLIS

/** `setTimeout(talk, 6100, …)` — one bubble's life, plus a breath. */
private const val TALK_STEP_MILLIS = 6_100L
