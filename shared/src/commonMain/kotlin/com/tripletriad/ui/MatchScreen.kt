package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tripletriad.audio.AudioPlayer
import com.tripletriad.audio.LocalAudio
import com.tripletriad.audio.Sound
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.MatchPlan
import com.tripletriad.data.MatchReward
import com.tripletriad.data.MatchRewards
import com.tripletriad.data.PveMatches
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Board
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchAi
import com.tripletriad.model.MatchOutcome
import com.tripletriad.model.MatchPreparation
import com.tripletriad.model.MatchResult
import com.tripletriad.model.MatchState
import com.tripletriad.model.Npc
import com.tripletriad.protocol.MatchTranscript
import com.tripletriad.protocol.TranscriptMove
import com.tripletriad.time.Clock
import com.tripletriad.ui.theme.LocalTtoColors
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Test tags for `shared/src/desktopTest`. */
const val BOARD_TEST_TAG: String = "board"
const val TURN_TEST_TAG: String = "turn"
const val SCORE_TEST_TAG: String = "score"
const val OUTCOME_TEST_TAG: String = "outcome"
const val NEW_MATCH_TEST_TAG: String = "new-match"

/** The active-rule strip above the board. Absent when no special rule is in force. */
const val MATCH_RULES_TEST_TAG: String = "match-rules"

/** `rule-help-RULE_REVERSE` — one per rule, and only while the strip is open. */
fun ruleHelpTestTag(ruleKey: String): String = "rule-help-$ruleKey"

/** The end-of-match panel. Its presence is the signal that the match is over and credited. */
const val MATCH_RESULT_TEST_TAG: String = "match-result"

/** The MGP and XP the finished match paid. */
const val MATCH_PAYOUT_TEST_TAG: String = "match-payout"

/** The control that leaves the board for the opponent list. */
const val MATCH_DONE_TEST_TAG: String = "match-done"

/** The chevron back to the main menu. */
const val MATCH_EXIT_TEST_TAG: String = "match-exit"

/** The opponent's name in the status bar. */
const val MATCH_OPPONENT_TEST_TAG: String = "match-opponent"

/** The turn timer's track. Always present; its fill is not. */
const val TURN_TIMER_TEST_TAG: String = "turn-timer"

/** The part of the track still running. Absent when it is not the player's turn. */
const val TURN_TIMER_FILL_TEST_TAG: String = "turn-timer-fill"

/**
 * `turn-blue` / `turn-red` — present only while that side is to move.
 *
 * A tag rather than the wording of [TURN_TEST_TAG], which is what the tests used to read. That
 * coupled every match test to `en_US`: the line says "blue to play" in English and "au bleu de
 * jouer" in French, so a test that wanted to know whose turn it was could only run in one language
 * — and the two tests that deliberately run in French and German had to avoid asking.
 */
fun turnTestTag(player: CardColor): String = "turn-${player.name.lowercase()}"

/** `tile-0` … `tile-8`, row-major, matching `Board.cells`. */
fun tileTestTag(position: Int): String = "tile-$position"

/**
 * `hand-blue-0` … `hand-blue-4`, by **slot** rather than card id.
 * Slots close up as cards are played, so slot 0 is always the first remaining card. That makes a
 * test able to say "play whatever is first" without knowing the deal. Slot numbering is independent
 * of how the slots are arranged on screen, so the same tag finds the same card in either
 * orientation.
 */
fun handCardTestTag(owner: CardColor, slot: Int): String =
    "hand-${owner.name.lowercase()}-$slot"

/**
 * A match against an opponent: the 3×3 board, both hands, and the opponent playing itself.
 *
 * All game logic lives below this file — the composable holds a `var state`, calls
 * `state.play(card, position)` for the player and `MatchAi.play(state)` for the opponent, and hands
 * the finished match to [MatchRewards]. Nothing here knows a rule. That separation is the point of
 * the port: the AS3 equivalent (`BaseMatchScreen` + `PVEMatchScreen`, 700 lines between them) *is*
 * the rules engine, the board, the score, the turn sequencer, the AI and the save writer at once.
 *
 * ### The opponent plays itself
 *
 * `BaseMatchScreen.opponentPhase()` is an empty stub with its body commented out and
 * `PVEMatchScreen` overrides it with `setTimeout(AI, 1000 + rand(4) * 1000)` — one to five seconds
 * of thinking time. That delay covered a `setTimeout` cascade of turn announcements, and now that
 * [MatchBanner] states how long each announcement takes, the cover is added up rather than guessed
 * at: the opponent waits for the captions the last placement earned, plus [OPPONENT_PAUSE_MS].
 *
 * ### Everything a match needs is a parameter
 *
 * The original reads the profile out of the global `Game.PROFILE_DATAS`, the opponent off a screen
 * property, and the clock off `new Date()`. All three arrive here instead, which is what lets a
 * test play a whole match against a chosen opponent with a pinned clock.
 *
 * @param profile the character playing. Read once, at assembly: the copy this screen holds is
 *   deliberately *not* updated as [onPersist] writes, because re-reading it mid-match would re-deal
 *   the hands.
 * @param onPersist writes the profile. Called at the start of the match — so abandoning it counts
 *   as a forfeit — and again once it is credited.
 * @param onTranscript hands the finished match to whoever submits it. Called **after** the credit
 *   and never in place of it: the reward is the player's and must not depend on a server being
 *   reachable. Defaults to doing nothing, which is what the many tests that only care about the
 *   game want. Not called at all when the match went to sudden death — see `suddenDeath` below.
 * @param turnLimit how long the player has to move before a card is played for them. The AS3's
 *   thirty seconds by default; a parameter so a test can reach the expiry without waiting for it.
 *   Overridden by [MatchScript.turnLimit] when there is a script.
 * @param script a match written in advance — see [MatchScript], [TutorialScreen] and
 *   [CampaignMatchScreen]. It can fix the deal, who starts, how the opponent plays and what is
 *   said, which is the whole of what the three AS3 subclasses of `PVEMatchScreen` override. Null
 *   for an ordinary match.
 * @param scriptExit what the end panel offers where an ordinary match offers Rematch, and **null
 *   for nothing at all** — the last rung of a ladder. A scripted match is never replayable in
 *   place: the script assumes the opening it forced, and re-running one would make it a repeatable
 *   source of MGP.
 * @param onResult how the match ended, once it has been credited and written. Exists for the
 *   tournament ladders, where the result decides which rung comes next; ignored by everything
 *   else, which reads the outcome off the state it already holds.
 */
@Composable
@Suppress("LongParameterList")
internal fun MatchScreen(
    catalog: CardCatalog,
    profile: GameSave,
    npc: Npc,
    clock: Clock,
    onPersist: suspend (GameSave) -> Unit,
    onExit: () -> Unit,
    onTranscript: suspend (MatchTranscript) -> Unit = {},
    turnLimit: Duration = DEFAULT_TURN_LIMIT,
    script: MatchScript? = null,
    scriptExit: ScriptExit? = null,
    onResult: (MatchResult) -> Unit = {},
) {
    val audio = LocalAudio.current
    val strings = LocalStrings.current
    var matchIndex by remember(npc.iconId) { mutableStateOf(0) }

    // The seed, kept rather than thrown away, because it is the *whole* of a transcript's
    // randomness: from it alone the server re-derives the roulette, the deal, the coin flip and
    // every one of the opponent's moves. An `Int` because that is what `MatchTranscript.seed`
    // carries — the truncation is harmless, a seed only has to be unpredictable and reproducible.
    val seed = remember(matchIndex, npc.iconId) { (clock.nowMillis() + matchIndex).toInt() }

    // The match generator. The deal, the coin flip and every AI tie-break draw from it, in exactly
    // the order `TranscriptVerifier` replays them.
    val random = remember(matchIndex, npc.iconId) { Random(seed) }

    /*
     * The generator for everything that must NOT touch the one above.
     *
     * `TranscriptVerifier` states the invariant: **on the player's own turn, nothing may draw from
     * the match generator.** The server replays a match by re-running the same stream, so any draw
     * the server does not know about shifts every later value and the transcript stops replaying —
     * which surfaces as a rejection, and a rejection is indistinguishable from cheating.
     *
     * Two callers here would otherwise break it, and neither is obvious:
     *
     *   - the deck selector's Random button, which draws *before* the deal;
     *   - the turn timer's auto-play, which draws twice on the player's turn.
     *
     * Derived from the same seed rather than freshly created, so a test with a `FixedClock` stays
     * fully deterministic. (The Chaos rule already had its own generator and needs nothing here.)
     */
    val sideRandom = remember(matchIndex, npc.iconId) { Random(seed.inv()) }

    // Resolved before the deck is asked for, because the answer decides whether it is asked at all:
    // under `RULE_RANDOM` the hand comes from the whole collection and the selector never opens
    // (`BaseMatchScreen.as:120-135`). Drawn once and passed into `assemble`, which would otherwise
    // roll the roulette a second time and play under rules the player was never shown.
    val rules = remember(matchIndex, npc.iconId) {
        PveMatches.rulesFor(npc, profile.mode, random)
    }

    /*
     * The profile this match is played by, captured **once** when the match screen opens.
     * Not `profile` read inside the effects below: that parameter tracks `session.active`, which
     * changes the moment `onPersist` returns, so the crediting effect would see a profile that had
     * already had `startingMatch` applied and apply it a second time — one match counted as two
     * started. Capturing it here is also what makes the two effects agree on which profile they are
     * amending.
     */
    val playing = remember(matchIndex, npc.iconId) { profile.startingMatch(againstNpc = true) }

    // `PVEScreen.as:244` — the match is counted as started when it is launched, not when it ends,
    // which is what makes `STATS.FORFEITS` (`STARTED_MATCHES - ENDED_MATCHES`) mean anything. That
    // is *before* the deck selector in the original too, since the selector is inside the match
    // screen: walking out of it is the forfeit this counter was designed for.
    //
    // Written here, unlike the original: the AS3 increments the counter on a global and only saves
    // in `endGame`, so a match abandoned before the last placement loses the increment and forfeits
    // can never be anything but zero. Persisting at the start is what the field was designed for.
    LaunchedEffect(matchIndex, npc.iconId) {
        onPersist(playing)
    }

    // Null until the player has chosen, which under Random — or under a script, which deals a hand
    // its lines are written around — they never are asked to.
    var deck by remember(matchIndex, npc.iconId) {
        mutableStateOf(script.deckFor(rules, profile))
    }
    val chosen = deck
    if (chosen == null) {
        DeckSelectorScreen(
            profile = profile,
            catalog = catalog,
            npc = npc,
            rules = rules,
            onChoose = { deck = it },
            onBack = onExit,
            // `sideRandom`, not the match generator: the Random button draws before the deal, so
            // using it here would shift every value the server expects. See `sideRandom`.
            random = sideRandom,
        )
        return
    }

    val match = remember(matchIndex, npc.iconId, chosen) {
        PveMatches.assemble(
            profile = profile,
            npc = npc,
            catalog = catalog,
            random = random,
            plan = MatchPlan(rules, chosen),
            forcedFlip = script.flip(),
        )
    }
    val ai = remember(script) { MatchAi(script.aiOptions()) }

    var state by remember(match) { mutableStateOf(match.setup.state) }
    var visibility by remember(match) { mutableStateOf(match.setup.opponentVisibility) }

    /*
     * What to announce before the first move, carried from the setup rather than re-derived.
     *
     * `MatchSetup` decides this from the setup that actually happened, which is not the same as
     * reading the rules back: a sudden-death rematch is played under the same Random rule but its
     * hand was not re-dealt, so the rules say announce it and the setup says do not. Held as state
     * alongside `visibility` and for the same reason — the rematch replaces both.
     */
    var setup by remember(match) { mutableStateOf(match.setup) }
    var selected by remember(match) { mutableStateOf<Card?>(null) }
    var reward by remember(match) { mutableStateOf<MatchReward?>(null) }

    /*
     * What the player did, in order — the only part of the match the server cannot derive for
     * itself, and therefore the only part a transcript has to carry.
     *
     * A plain list rather than a `mutableStateListOf`: nothing renders it, and making it observable
     * would recompose the whole screen on every placement for no visible effect.
     */
    val moves = remember(match) { mutableListOf<TranscriptMove>() }

    /*
     * Whether this match went to a sudden-death rematch.
     * It suppresses submission, because `MatchTranscript` describes **one** nine-placement match:
     * it has a single seed, a single deck and a single list of moves, with no way to say "and then
     * the hands were regrouped and it was played again". Submitting the first nine moves of such a
     * match would be worse than submitting nothing — the server would happily replay them, score
     * the draw, and return a verdict that contradicts the reward already credited for the
     * sudden-death result.
     *
     * A known gap in the format rather than a bug here. See
     * docs/migration/09-PHASE-5-NETWORK.md.
     */
    var suddenDeath by remember(match) { mutableStateOf(false) }

    /*
     * Whether this match is one the server could replay. See `reportTranscript`.
     * Computed here rather than at the call site so the crediting effect reads a value instead of
     * an expression — `MatchScreen` is at the complexity detekt allows and this is the branch that
     * carries its reasoning best on its own.
     */
    val unrepeatable = suddenDeath || script != null

    // The deal, which is now a frame later than the screen opening: the cards are dealt once a deck
    // is settled on, and that is what this sound is announcing.
    LaunchedEffect(match) {
        audio.play(Sound.MATCH_OPEN)
    }

    val banners = bannerQueue(match, state, setup)

    /*
     * Whether the pre-match announcements are over and the match has actually begun.
     *
     * The original arms both clocks in `nextTurn`, which `letsGetStarted` calls **after** the whole
     * phase cascade (`BaseMatchScreen.as:250-252`). So the player's thirty seconds start when they
     * can move, not while Reverse and Start are still on screen. This port started the clock at
     * composition and had the intro running against it — under an ordinary thirty-second limit that
     * is a few seconds of a turn silently spent, and a test with a short limit found the match
     * playing itself out before the Start banner had left.
     */
    val underway = introFinished(match, setup)

    /*
     * What the script has to say before this placement, fixed for the whole of it.
     *
     * Read once per placement rather than on each recomposition, because the opponent's delay is
     * computed from its length and the bubbles are played from the same list: the two must not be
     * able to disagree about how many lines there are. The score is read here for the same reason —
     * one line branches on whether the player has captured anything, and it is asking about the
     * board as it stands *before* the move being announced.
     */
    val lesson = remember(match, state.placement) { script.linesBefore(state) }

    // The opponent's turn. Keyed on the placement count, so it fires once per turn and again
    // after a sudden-death rematch resets it — and never while the player is to move.
    LaunchedEffect(match, state.placement, state.isFinished) {
        if (state.isFinished || state.currentPlayer != CardColor.RED) return@LaunchedEffect
        delay(
            OPPONENT_PAUSE_MS + animationsFor(state, setup).sumOf { it.totalMillis } +
                lessonPause(lesson),
        )
        val next = ai.play(state, random)
        if (next.placement > state.placement) {
            state = next
            sound(audio, next)
        }
    }

    // Crediting, once, when the match resolves. A sudden-death draw credits nothing and plays on
    // (`PVEMatchScreen.as:63-68`), so the same effect handles both by branching on the outcome.
    LaunchedEffect(match, state.isFinished, state.placement) {
        val outcome = state.outcome() ?: return@LaunchedEffect
        if (reward != null) return@LaunchedEffect
        val result = MatchResult.of(outcome, CardColor.BLUE)
        if (result == null) {
            val rematch = MatchPreparation.prepareRematch(state, random)
            state = rematch.state
            visibility = rematch.opponentVisibility
            setup = rematch
            selected = null
            suddenDeath = true
            return@LaunchedEffect
        }
        val credit = MatchRewards.credit(
            save = playing,
            npc = npc,
            result = result,
            rules = match.rules,
            at = clock.nowMillis(),
            random = random,
        )
        reward = credit.reward
        onPersist(credit.save)

        // Told after the credit and after the persist, so a caller acting on it — a ladder
        // deciding which rung comes next — cannot observe a result the profile has not yet been
        // paid for.
        onResult(result)

        // After the credit, and never instead of it. The profile is the player's; the transcript is
        // the server's business, and a server that is down must not cost anybody their reward.
        reportTranscript(
            onTranscript = onTranscript,
            // A scripted match is not replayable either, and for a sharper reason than sudden
            // death: the server re-runs the seed through the *real* setup and the *real* AI, and a
            // script forces the coin flip, fixes the deal and makes the opponent play its worst
            // move. Nothing about it would reproduce, so submitting one asks to be rejected —
            // which is indistinguishable from being caught cheating.
            unrepeatable = unrepeatable,
            seed = seed,
            profile = profile,
            npc = npc,
            deck = chosen,
            moves = moves.toList(),
        )
    }

    // Rematch, or whatever a script says instead — see `nextAction`.
    val next = nextAction(script, scriptExit) {
        audio.play(Sound.NEW_MATCH)
        matchIndex += 1
    }

    // The one guard, for both ways of playing a card. Tapping a cell plays whatever is selected;
    // dropping one plays what the finger is holding — and both have to check the same three
    // things, which is exactly the sort of pair that drifts apart.
    val place: (Card, Int) -> Unit = { card, position ->
        if (canPlay(state, card, position)) {
            val next = state.play(card, position)
            // Recorded here and nowhere else, which is the point of `place` being the single guard:
            // tapping a cell, dropping a card and the turn timer all arrive through it, so a
            // timed-out turn is written down exactly like a chosen one.
            moves += TranscriptMove(cardId = card.id, position = position)
            state = next
            selected = null
            sound(audio, next)
        }
    }

    val turnFraction = turnClock(match, state, script.turnLimitOr(turnLimit), underway) {
        // `sideRandom`: this is the player's turn, and the match generator is off limits there.
        autoPlay(state, sideRandom)?.let { (card, position) -> place(card, position) }
    }

    val wide = LocalWideLayout.current

    val log = rememberMoveLog(match, state)

    MatchFrame(
        wide = wide,
        side = {
            MatchSidePanel(
                npc = npc,
                opponentName = strings[npc.nameKey],
                rules = match.rules,
                log = log,
            )
        },
    ) {
        StatusBar(
            state = state,
            selected = selected,
            npc = npc,
            opponentName = strings[npc.nameKey],
            turnFraction = turnFraction,
            // On a wide window the opponent has a whole panel of their own, and drawing a 26 dp
            // face beside a 50 dp one is the sort of duplicate that looks like a bug.
            showOpponent = !wide,
            onExit = onExit,
        )
        BoardRules(match.rules, wide)

        // The play area takes whatever the status bar leaves and sizes every card to what it
        // actually got. Nothing below this line guesses at a screen size or a "chrome"
        // constant: `matchLayout` is handed measured bounds and derives one scale that the
        // whole arrangement is known to fit inside.
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            PlayArea(
                state = state,
                selected = selected,
                visibility = visibility,
                layout = matchLayout(maxWidth, maxHeight),
                playable = playable(state),
                onSelect = { if (it in playable(state)) selected = it },
                onPlace = { position -> selected?.let { place(it, position) } },
                onDrop = place,
            )
            reward?.let {
                OutcomePanel(
                    reward = it,
                    opponentName = strings[npc.nameKey],
                    next = next,
                    onDone = onExit,
                )
            }
            // Over the board and under the outcome panel: a caption is allowed to cover
            // the cards it is describing, but never the thing the player has to tap.
            MatchBannerOverlay(banners)

            // Held behind the pre-match announcements, which is where the original puts it:
            // `opponentPhase` is reached from `nextTurn`, and `nextTurn` runs after the whole
            // cascade. A lesson talking over the Start banner would also be two things at once.
            LessonBubbles(
                key = state.placement,
                lines = lesson,
                script = script,
                enabled = underway,
            )

            // And what the opponent says about how it went, over the panel as in `endGame`.
            OutcomeBubble(script = script, result = reward?.result)
        }
    }
}

/**
 * Hands the finished match over to be verified, unless it is one a transcript cannot describe.
 *
 * A function rather than four more lines inside the crediting effect, because `MatchScreen` is at
 * the cyclomatic complexity detekt allows and this is the branch that is genuinely separable: what
 * a transcript can express is a property of the *format*, and nothing above cares about it.
 *
 * @param unrepeatable suppresses the whole thing — a match the server could not replay even if it
 *   were honest. Two cases reach it.
 *
 *   **Sudden death.** [MatchTranscript] describes one nine-placement match — one seed, one deck,
 * one list of moves — with no way to say "and then the hands were regrouped and it was played
 * again". Submitting the first nine moves would be worse than submitting nothing: the server would
 * replay them, score the draw, and answer with a verdict contradicting the reward already credited
 * for the sudden-death result.
 *
 *   **A [MatchScript].** It forces the coin flip, fixes the deal and hands the opponent a different
 *   strategy, none of which the seed carries.
 * @param profile the profile as it was when the match began, so `ownedCards` describes the
 *   collection the deck was legal against rather than one a reward has since added to.
 */
@Suppress("LongParameterList")
private suspend fun reportTranscript(
    onTranscript: suspend (MatchTranscript) -> Unit,
    unrepeatable: Boolean,
    seed: Int,
    profile: GameSave,
    npc: Npc,
    deck: List<Int>,
    moves: List<TranscriptMove>,
) {
    if (unrepeatable) return
    onTranscript(
        MatchTranscript(
            seed = seed,
            collection = profile.mode,
            opponentIconId = npc.iconId,
            deck = deck,
            // Stated by the client, which is backwards and known to be — the server will hold the
            // profile once accounts exist. See `MatchTranscript.ownedCards`.
            ownedCards = profile.cards,
            moves = moves,
        ),
    )
}

/**
 * Which of the player's cards may be played this turn — Order and Chaos.
 * Not `state.currentHand`: [MatchState.playableCards] narrows it to the first card under
 * `RULE_ORDER` and to one random card under `RULE_CHAOS`. The generator is derived from the
 * placement count rather than shared with the match, so Chaos picks the *same* card for the whole
 * of one turn — a fresh draw on every recomposition would move the playable card while the player
 * was reaching for it.
 */
private fun playable(state: MatchState): List<Card> =
    if (state.currentPlayer != CardColor.BLUE) {
        emptyList()
    } else {
        state.playableCards(Random(CHAOS_SEED + state.placement))
    }

/**
 * The sounds one placement makes, in the order the AS3 made them.
 *
 * The mapping is the part with decisions in it, so it is a function rather than four `if`s inside a
 * click handler, and `MatchAudioTest` asserts it through the real UI with a recording player.
 *
 * * **nothing captured** → [Sound.CARD_PLACED]. `TTOCore.as:87` plays `se_ttriad.scd_1` in exactly
 *   the branch that returns a power of 0, i.e. the placement that flips nothing.
 * * **something captured** → [Sound.CARD_CAPTURED], **once**. `Card.as:229` plays it per flipped
 *   card, inside `flipTo`; four cards flipping at once would fire it four times, which on
 *   `SoundPool` is the same sample four times in the same millisecond — a volume spike, not a
 *   richer sound. One is the faithful *result*.
 * * **a combo** → [Sound.COMBO] over the top, from `TTOCore.as:125`'s `flipData.waveEffect`. A
 *   capture with `wave >= 1` is by definition a combo generation.
 * * **the match continues** → [Sound.TURN_CHANGE], `BaseMatchScreen.as:374`, which plays it for
 *   either side.
 * * **the match ends** → the winner's sound instead, `PVEMatchScreen.as:95`/`:139`. A draw is
 *   silent, matching the original: its draw branch plays nothing.
 */
private fun sound(audio: AudioPlayer, state: MatchState) {
    val captures = state.lastPlay?.captures.orEmpty()
    audio.play(if (captures.isEmpty()) Sound.CARD_PLACED else Sound.CARD_CAPTURED)
    if (captures.any { it.wave >= 1 }) audio.play(Sound.COMBO)

    when (val outcome = state.outcome()) {
        null -> audio.play(Sound.TURN_CHANGE)
        is MatchOutcome.Win ->
            audio.play(if (outcome.winner == CardColor.BLUE) Sound.BLUE_WINS else Sound.RED_WINS)
        else -> Unit
    }
}

/**
 * Score, whose turn it is, and a reset. One compact line so the board gets the rest.
 * The score is two numbers and a dash, with each number in its side's colour and no colour *word* —
 * it used to read "blue 5 — 5 red". Nothing in the AS3 bundles names a side, so those two words
 * would have been the only untranslatable text on screen, and the FFXIV board they are modelled on
 * shows the score without them too.
 */
@Composable
private fun StatusBar(
    state: MatchState,
    selected: Card?,
    npc: Npc,
    opponentName: String,
    turnFraction: Float?,
    showOpponent: Boolean,
    onExit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StatusRow(
            state = state,
            selected = selected,
            npc = npc,
            opponentName = opponentName,
            showOpponent = showOpponent,
            onExit = onExit,
        )
        TurnTimerBar(fraction = turnFraction)
    }
}

/**
 * How much of the turn is left, as `playerPanel`'s `ProgressBar` was.
 *
 * ### One bar, not two
 *
 * The original gives **both** players a timer and starts both (`BaseMatchScreen.as:377-387`) — but
 * only the blue one is listened to: `:93` attaches `TIME_UP_EVENT` to `bluePlayer` and to nothing
 * else, so red's bar runs down and expiring does nothing. Red is driven by `opponentPhase` instead,
 * which in this port answers in [OPPONENT_PAUSE_MS] and could never reach thirty seconds anyway. A
 * bar that cannot expire is decoration, so there is one.
 * It goes under the status line rather than over the hand, which is where `playerPanel` put it: the
 * hand here is sized to the cards by [MatchLayout], and a bar inside it would either shrink them or
 * be drawn across them.
 *
 * @param fraction 1f at the start of the turn, 0f when it is up. Null while it is not the player's
 *   turn, which is when the original calls `razTimer()`.
 */
@Composable
private fun TurnTimerBar(fraction: Float?) {
    Box(
        modifier = Modifier
            .testTag(TURN_TIMER_TEST_TAG)
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(TurnTimerHeight)
            .clip(TurnTimerShape)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = TIMER_TRACK_ALPHA)),
    ) {
        if (fraction != null) {
            Box(
                modifier = Modifier
                    .testTag(TURN_TIMER_FILL_TEST_TAG)
                    .fillMaxWidth(fraction)
                    .height(TurnTimerHeight)
                    .clip(TurnTimerShape)
                    // Amber near the end, which the original does not do — its bar is one colour
                    // the whole way down. Thirty seconds is long enough that a bar shortening is
                    // easy to miss, and the penalty for missing it is a card played at random.
                    .background(
                        if (fraction <= TIMER_URGENT) {
                            MaterialTheme.colorScheme.error
                        } else {
                            LocalTtoColors.current.transient
                        },
                    ),
            )
        }
    }
}

@Composable
private fun StatusRow(
    state: MatchState,
    selected: Card?,
    npc: Npc,
    opponentName: String,
    showOpponent: Boolean,
    onExit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val strings = LocalStrings.current
        // The same control every other screen's back is, rather than the bare `‹` glyph this row
        // carried while it was the one screen outside the shell. An `IconButton` is a 48 dp touch
        // target where a `Text` was a 20 dp one, and it is the only back on screen that a player
        // could previously miss — the reason for the glyph was width, and an icon costs the same
        // in every language too. The Android system gesture still reaches the same place.
        IconButton(
            onClick = onExit,
            modifier = Modifier.testTag(MATCH_EXIT_TEST_TAG).size(ExitButtonSize),
        ) {
            Icon(
                imageVector = TtoIcons.Back,
                contentDescription = strings[StringKeys.BACK],
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            )
        }
        Score(state)
        // The turn line takes whatever the two fixed ends leave, and elides rather than growing.
        //
        // It used to be three items in a centred `spacedBy` row, which fitted because every
        // string was English: French is "au bleu de jouer — choisissez une carte" where English is
        // "blue to play — pick a card", and the extra 14 characters pushed "Match suivant" onto a
        // second line on a 1080 px screen. A row sized for one language is the oldest
        // localisation bug there is, so the *sentence* is the part that gives, not the controls.
        Box(
            modifier = Modifier
                .weight(1f)
                // Absent once the board is full, which is what makes `turn-blue` mean "the player
                // may move" rather than "the player moved last".
                .then(state.currentPlayer?.let { Modifier.testTag(turnTestTag(it)) } ?: Modifier),
            contentAlignment = Alignment.Center,
        ) {
            TurnLine(state = state, selected = selected)
        }
        // The opponent's face and name where the "next match" control used to be. Abandoning a
        // match is the back control; restarting one is the end-of-match panel's business, and a
        // reset beside a live board is one mis-tap away from discarding a game in progress.
        //
        // The portrait is the 50 px art the opponent list already draws — the player chose a face
        // there, and until now the board did not show it the face they chose.
        if (showOpponent) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NpcPortrait(npc = npc, name = opponentName, size = BannerPortraitSize)
                Text(
                    text = opponentName,
                    color = CardColor.RED.edge,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(MATCH_OPPONENT_TEST_TAG).padding(vertical = 4.dp),
                )
            }
        }
    }
}

/** `5 — 5`, each half in its owner's colour. */
@Composable
private fun Score(state: MatchState) {
    val score = state.score
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = CardColor.BLUE.edge)) { append(score.blue.toString()) }
            append(" — ")
            withStyle(SpanStyle(color = CardColor.RED.edge)) { append(score.red.toString()) }
        },
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag(SCORE_TEST_TAG),
    )
}

/**
 * Whose turn it is, what is selected, or the result once the board is full.
 *
 * The outcome is phrased from **blue's** side — `You win !` / `You lose...` — because that is what
 * the bundles offer and it matches the original, where the local player is always the blue one
 * (`data-flow.md`, `openPhase`). When there is an AI or a second player this needs revisiting; a
 * neutral "red wins" has no key in any of the four locales.
 */
@Composable
private fun TurnLine(state: MatchState, selected: Card?) {
    val strings = LocalStrings.current
    val outcome = state.outcome()
    if (outcome != null) {
        Text(
            text = when (outcome) {
                is MatchOutcome.Win ->
                    if (outcome.winner == CardColor.BLUE) {
                        strings[StringKeys.YOU_WIN]
                    } else {
                        strings[StringKeys.YOU_LOSE]
                    }
                is MatchOutcome.Draw -> strings[StringKeys.DRAW]
                is MatchOutcome.SuddenDeath ->
                    "${strings[StringKeys.DRAW]} — ${strings[StringKeys.SUDDEN_DEATH]}"
            },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(OUTCOME_TEST_TAG),
        )
        return
    }
    val player = state.currentPlayer ?: return
    val side = strings[if (player == CardColor.BLUE) StringKeys.SIDE_BLUE else StringKeys.SIDE_RED]
    Text(
        // "red to play — pick a card" was right when a human moved both sides. With an opponent
        // playing itself, the red turn is something to wait for, not an instruction.
        text = when {
            player == CardColor.RED -> strings.format(StringKeys.OPPONENT_TURN, side)
            selected == null -> strings.format(StringKeys.TURN_PICK_CARD, side)
            else -> strings.format(StringKeys.TURN_PICK_CELL, side, selected.name)
        },
        color = player.edge,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(TURN_TEST_TAG),
    )
}

/**
 * Whether the player may put [card] on [position] right now.
 *
 * The one guard behind both ways of playing a card — tapping a cell with one selected, and dropping
 * one onto it. They check the same three things, and a pair like that is exactly what drifts apart:
 * the drag would have been the one that forgot `RULE_ORDER`.
 */
private fun canPlay(state: MatchState, card: Card, position: Int): Boolean =
    state.currentPlayer == CardColor.BLUE &&
        state.board.isEmpty(position) &&
        card in playable(state)

/**
 * Counts the player's turn down, and reports how much of it is left.
 *
 * `playerPanel`'s thirty-second limit, re-armed on every turn: `setTimer()` on the side to move and
 * `razTimer()` on the other (`BaseMatchScreen.as:377-387`). Its own composable rather than an
 * effect inside the match, because the loop and its three guards are the sort of thing that makes a
 * screen function too complex to read — which is what detekt said when they were.
 *
 * @param key restarts the whole clock. The match, so a rematch gets a fresh one.
 * @param running whether the match has actually begun — false while the pre-match announcements
 *   are still playing. The original arms the timer in `nextTurn`, after the whole cascade, so a
 *   turn does not start counting down behind the Start banner.
 * @param onExpired the turn ran out. Called once, from the effect's own coroutine.
 * @return 1f at the start of the turn falling to 0f, or **null** when the clock is not running —
 *   the opponent's turn, or a finished match.
 */
@Composable
private fun turnClock(
    key: Any,
    state: MatchState,
    limit: Duration,
    running: Boolean,
    onExpired: () -> Unit,
): Float? {
    var remaining by remember(key) { mutableStateOf(limit) }
    val counting = running && !state.isFinished && state.currentPlayer == CardColor.BLUE

    LaunchedEffect(key, state.placement, state.currentPlayer, state.isFinished, running) {
        remaining = limit
        if (counting) {
            while (remaining > Duration.ZERO) {
                delay(TIMER_TICK)
                remaining -= TIMER_TICK
            }
            onExpired()
        }
    }

    return (remaining / limit).toFloat().takeIf { counting }
}

/**
 * A card and a cell chosen at random, for a turn that ran out of time.
 *
 * `BaseMatchScreen.autoPlay` (`:422-437`), and its randomness is the point: the penalty for letting
 * the clock run out is a move you did not choose. Under `RULE_ORDER` it takes `remainingCards[0]`
 * instead, which [playable] already narrows to — so the rule is honoured without being named here.
 * Null when the board is full or the hand is empty, which the caller cannot reach: [turnClock] does
 * not run once the match is finished.
 */
private fun autoPlay(state: MatchState, random: Random): Pair<Card, Int>? {
    val cards = playable(state)
    val free = (0 until Board.SIZE).filter { state.board.isEmpty(it) }
    return if (cards.isEmpty() || free.isEmpty()) {
        null
    } else {
        cards.random(random) to free.random(random)
    }
}

/**
 * The opponent's thinking time, **on top of** whatever captions are still playing.
 *
 * `PVEMatchScreen` waits `1000 + rand(4) * 1000` before the AI moves, and that range is
 * not thinking time — it is cover for the `setTimeout` cascade that was announcing the
 * turn and the captures. Now that the captions state their own durations, the cover can be
 * computed instead of guessed at: the caller adds up [MatchBanner.totalMillis] for
 * everything the placement earned and this is what is left, the pause that would exist
 * even if nothing were on screen.
 *
 * Short, because it is now additive. A red turn costs this plus the 1.2s [MatchBanner.RED_TURN]
 * takes, which lands inside the original's own range without any of its randomness.
 */
private const val OPPONENT_PAUSE_MS = 700L

/**
 * `playerPanel._timer = 30` — the turn limit, and the AS3's own default.
 *
 * `TutorialScreen.as:58` raises it to 60 for its lesson and `PVPScreen.as:277` sets it back to 30
 * for a network match, so 30 is what a PvE match plays under.
 */
private val DEFAULT_TURN_LIMIT = 30.seconds

/** How often the bar is redrawn. Fine enough to look continuous, coarse enough to be cheap. */
private val TIMER_TICK = 100.milliseconds

/** Below this the bar turns red. */
private const val TIMER_URGENT = 0.25f

/** The unfilled part of the track. */
private const val TIMER_TRACK_ALPHA = 0.4f

/**
 * Seeds the per-turn generator that Chaos draws its card from.
 *
 * Derived from the placement count rather than taken from the match's own generator, so the same
 * card stays playable for the whole of one turn: `playableCards` draws on every call, and a call
 * per recomposition would move the target while the player reached for it.
 */
private const val CHAOS_SEED = 20260802

/** Thin enough to read as a rule under the status line rather than as a control. */
private val TurnTimerHeight = 3.dp
private val TurnTimerShape = RoundedCornerShape(2.dp)

/** The back control's own footprint: a 48 dp `IconButton` would own a fifth of the banner. */
private val ExitButtonSize = 34.dp

/** Smaller than the opponent list's 50 px plate — a face, not a portrait. */
private val BannerPortraitSize = 26.dp
