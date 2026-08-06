package com.tripletriad.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.CardType
import com.tripletriad.model.GameRules
import com.tripletriad.model.GameSave
import com.tripletriad.model.HAND_SIZE
import com.tripletriad.model.HandVisibility
import com.tripletriad.model.MatchAi
import com.tripletriad.model.MatchOutcome
import com.tripletriad.model.MatchPreparation
import com.tripletriad.model.MatchResult
import com.tripletriad.model.MatchState
import com.tripletriad.model.Npc
import com.tripletriad.model.PlacedCard
import com.tripletriad.time.Clock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Test tags for `shared/src/desktopTest`. */
const val BOARD_TEST_TAG: String = "board"
const val TURN_TEST_TAG: String = "turn"
const val SCORE_TEST_TAG: String = "score"
const val OUTCOME_TEST_TAG: String = "outcome"
const val NEW_MATCH_TEST_TAG: String = "new-match"

/** The active-rule strip above the board. Absent when no special rule is in force. */
const val MATCH_RULES_TEST_TAG: String = "match-rules"

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
 *
 * Slots close up as cards are played, so slot 0 is always the first remaining card. That makes
 * a test able to say "play whatever is first" without knowing the deal. Slot numbering is
 * independent of how the slots are arranged on screen, so the same tag finds the same card in
 * either orientation.
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
 * of thinking time. That delay covered a `setTimeout` cascade of turn announcements; with no
 * cascade to cover, five seconds of staring at a static board is dead time, so [OPPONENT_PAUSE_MS]
 * is short and fixed. Pacing is Phase 6's business and this is the one number it will want to
 * revisit.
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
 */
@Composable
internal fun MatchScreen(
    catalog: CardCatalog,
    profile: GameSave,
    npc: Npc,
    clock: Clock,
    onPersist: suspend (GameSave) -> Unit,
    onExit: () -> Unit,
) {
    val audio = LocalAudio.current
    val strings = LocalStrings.current
    var matchIndex by remember(npc.iconId) { mutableStateOf(0) }

    // One mutable generator for the whole match: the deal, the coin flip and every AI tie-break
    // draw from it. Seeded from the clock so successive matches differ, which also makes a test
    // with a `FixedClock` fully deterministic.
    val random = remember(matchIndex, npc.iconId) { Random(clock.nowMillis() + matchIndex) }

    // Resolved before the deck is asked for, because the answer decides whether it is asked at all:
    // under `RULE_RANDOM` the hand comes from the whole collection and the selector never opens
    // (`BaseMatchScreen.as:120-135`). Drawn once and passed into `assemble`, which would otherwise
    // roll the roulette a second time and play under rules the player was never shown.
    val rules = remember(matchIndex, npc.iconId) {
        PveMatches.rulesFor(npc, profile.mode, random)
    }

    /*
     * The profile this match is played by, captured **once** when the match screen opens.
     *
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

    // Null until the player has chosen, which under Random they never are asked to.
    var deck by remember(matchIndex, npc.iconId) {
        mutableStateOf(if (rules.random) PveMatches.playerDeck(profile) else null)
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
            random = random,
        )
        return
    }

    val match = remember(matchIndex, npc.iconId, chosen) {
        PveMatches.assemble(profile, npc, catalog, random, MatchPlan(rules, chosen))
    }
    val ai = remember { MatchAi() }

    var state by remember(match) { mutableStateOf(match.setup.state) }
    var visibility by remember(match) { mutableStateOf(match.setup.opponentVisibility) }
    var selected by remember(match) { mutableStateOf<Card?>(null) }
    var reward by remember(match) { mutableStateOf<MatchReward?>(null) }

    // The deal, which is now a frame later than the screen opening: the cards are dealt once a deck
    // is settled on, and that is what this sound is announcing.
    LaunchedEffect(match) {
        audio.play(Sound.MATCH_OPEN)
    }

    // The opponent's turn. Keyed on the placement count, so it fires once per turn and again
    // after a sudden-death rematch resets it — and never while the player is to move.
    LaunchedEffect(match, state.placement, state.isFinished) {
        if (state.isFinished || state.currentPlayer != CardColor.RED) return@LaunchedEffect
        delay(OPPONENT_PAUSE_MS)
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
            selected = null
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
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusBar(
            state = state,
            selected = selected,
            opponentName = strings[npc.nameKey],
            onExit = onExit,
        )
        RulesStrip(match.rules)

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
                onSelect = { if (it in playable(state)) selected = it },
                onPlace = { position ->
                    val card = selected
                    if (card != null && state.currentPlayer == CardColor.BLUE &&
                        state.board.isEmpty(position)
                    ) {
                        val next = state.play(card, position)
                        state = next
                        selected = null
                        sound(audio, next)
                    }
                },
            )
            reward?.let {
                OutcomePanel(
                    reward = it,
                    opponentName = strings[npc.nameKey],
                    onRematch = {
                        audio.play(Sound.NEW_MATCH)
                        matchIndex++
                    },
                    onDone = onExit,
                )
            }
        }
    }
}

/**
 * Which of the player's cards may be played this turn — Order and Chaos.
 *
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
 * The rules in force, named.
 *
 * `RulesDigest.as` did the same job on the board, and it matters more than it looks: Reverse or
 * Fallen Ace silently changes which card beats which, and a player who has not been told is playing
 * a different game from the one they think. The keys are the AS3 rule constants, which are also
 * their own i18n keys — so this is `activeRuleKeys()` looked up, with no mapping table in between.
 */
@Composable
private fun RulesStrip(rules: GameRules) {
    val keys = rules.activeRuleKeys()
    if (keys.isEmpty()) return
    val strings = LocalStrings.current
    Text(
        text = keys.joinToString(DOT_SEPARATOR) { strings[it] },
        color = RuleStripText,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .testTag(MATCH_RULES_TEST_TAG)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
    )
}

/**
 * What the match paid, over the board it was won on.
 *
 * Stands in for `RematchPanel.as`, which the original opened over the finished board with the same
 * contents: the result, the MGP, the XP, any dropped items and any achievement just earned. Two
 * actions, as it had: play the same opponent again, or leave.
 */
@Composable
private fun OutcomePanel(
    reward: MatchReward,
    opponentName: String,
    onRematch: () -> Unit,
    onDone: () -> Unit,
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .testTag(MATCH_RESULT_TEST_TAG)
            .widthIn(max = ContentMaxWidth)
            .padding(16.dp)
            .clip(MaterialTheme.shapes.small)
            .background(PanelBackground)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = when (reward.result) {
                MatchResult.WIN -> strings[StringKeys.YOU_WIN]
                MatchResult.LOSE -> strings[StringKeys.YOU_LOSE]
                MatchResult.DRAW -> strings[StringKeys.DRAW]
            },
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = opponentName,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Always shown, and always positive: every result pays in this game — see `MatchRewards`.
        Text(
            text = buildList {
                add("+${reward.mgp} ${strings[StringKeys.MGP]}")
                if (reward.xp > 0) add("+${reward.xp} ${strings[StringKeys.XP]}")
            }.joinToString(DOT_SEPARATOR),
            color = PayoutText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(MATCH_PAYOUT_TEST_TAG),
        )

        if (reward.items.isNotEmpty()) {
            Text(
                text = "${strings[StringKeys.REWARDS]}: ${reward.items.size}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        for (achievement in reward.achievements) {
            Text(
                text = strings[StringKeys.ACHIEVEMENT_EARNED] + " — " +
                    strings[achievement.labelKey],
                color = RuleStripText,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                WideButton(strings[StringKeys.REMATCH], NEW_MATCH_TEST_TAG, onClick = onRematch)
            }
            Box(modifier = Modifier.weight(1f)) {
                WideButton(strings[StringKeys.BACK], MATCH_DONE_TEST_TAG, onClick = onDone)
            }
        }
    }
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
 * Red hand, board, blue hand — as a row in landscape, a column in portrait.
 *
 * `SpaceBetween` puts the board dead centre: both hand areas are given the same fixed size by
 * [MatchLayout], so the board does not drift as a hand empties.
 */
@Composable
private fun PlayArea(
    state: MatchState,
    selected: Card?,
    visibility: HandVisibility,
    layout: MatchLayout,
    onSelect: (Card) -> Unit,
    onPlace: (Int) -> Unit,
) {
    val hand: @Composable (CardColor) -> Unit = { owner ->
        HandArea(
            state = state,
            owner = owner,
            selected = selected,
            visibility = visibility,
            layout = layout,
            onSelect = onSelect,
        )
    }
    val board: @Composable () -> Unit = {
        BoardGrid(state = state, scale = layout.boardScale, onPlace = onPlace)
    }

    if (layout.landscape) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            hand(CardColor.RED)
            board()
            hand(CardColor.BLUE)
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            hand(CardColor.RED)
            board()
            hand(CardColor.BLUE)
        }
    }
}

/**
 * Score, whose turn it is, and a reset. One compact line so the board gets the rest.
 *
 * The score is two numbers and a dash, with each number in its side's colour and no colour *word*
 * — it used to read "blue 5 — 5 red". Nothing in the AS3 bundles names a side, so those two words
 * would have been the only untranslatable text on screen, and the FFXIV board they are modelled on
 * shows the score without them too.
 */
@Composable
private fun StatusBar(
    state: MatchState,
    selected: Card?,
    opponentName: String,
    onExit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val strings = LocalStrings.current
        // A bare chevron, not "‹ Back". The row already learned once that a fixed-width control
        // sized for English squeezes the turn line in French (see below), and this bar now has two
        // of them; a glyph costs the same in every language. The Android system back gesture
        // reaches the same place — see `App`.
        Text(
            text = "‹",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .testTag(MATCH_EXIT_TEST_TAG)
                .clickable(onClick = onExit)
                .padding(horizontal = 4.dp),
        )
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
        // The opponent's name where the "next match" control used to be. Abandoning a match is the
        // back chevron; restarting one is the end-of-match panel's business, and a reset control
        // beside a live board is one mis-tap away from discarding a game in progress.
        Text(
            text = opponentName,
            color = CardColor.RED.edge,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(MATCH_OPPONENT_TEST_TAG).padding(4.dp),
        )
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

/** The 3×3 board. Empty cells show their element, if the board has one. */
@Composable
private fun BoardGrid(state: MatchState, scale: Float, onPlace: (Int) -> Unit) {
    Column(
        modifier = Modifier.testTag(BOARD_TEST_TAG).padding(TileGap * scale),
        verticalArrangement = Arrangement.spacedBy(TileGap * scale),
    ) {
        for (row in 0 until BOARD_WIDTH) {
            Row(horizontalArrangement = Arrangement.spacedBy(TileGap * scale)) {
                for (column in 0 until BOARD_WIDTH) {
                    val position = row * BOARD_WIDTH + column
                    TileCell(
                        placed = state.board[position],
                        element = state.board.elements[position],
                        scale = scale,
                        modifier = Modifier
                            .testTag(tileTestTag(position))
                            .clickable { onPlace(position) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TileCell(
    placed: PlacedCard?,
    element: CardType?,
    scale: Float,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .size(CardSpriteWidth * scale, CardSpriteHeight * scale)
            .clip(TileShape)
            .background(EmptyTile)
            .border(1.dp, TileBorder, TileShape),
        contentAlignment = Alignment.Center,
    ) {
        if (placed == null) {
            element?.let {
                Text(
                    text = it.name.take(ELEMENT_LABEL_CHARS),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    fontSize = ElementFontSize * scale,
                )
            }
        } else {
            BoardCard(placed, scale)
        }
    }
}

/**
 * A placed card that flips when its owner changes.
 *
 * Re-triggered by a [LaunchedEffect] on the owner rather than by a tap: on the board a flip is
 * something the rules *did*, not something the player asked for.
 *
 * **A port now, not a substitution.** `Card.flip()` (`Card.as:249-291`) chains four 0.1 s
 * tweens — `flip` → `yoyo` → `unflip` → `yoyo2` — squashing `scaleY` to 0 and back twice while
 * `scaleX` widens to 1.2 for the duration. The colour switches and the back appears at the first
 * pinch; the new face returns at the second.
 *
 * An earlier revision used a `rotationY` half-turn instead, which **mirrored the card's contents
 * between 90° and 180°** — every glyph on it drawn backwards for a fifth of a second. A squash
 * cannot do that, because the scale never goes negative. The original's choice was the right one.
 */
@Composable
private fun BoardCard(placed: PlacedCard, scale: Float) {
    val squashY = remember { Animatable(1f) }
    val stretchX = remember { Animatable(1f) }
    var shown by remember { mutableStateOf(placed.owner) }
    var showBack by remember { mutableStateOf(false) }

    LaunchedEffect(placed.owner) {
        if (shown == placed.owner) return@LaunchedEffect
        // `horizon = false` is the default and the only value the match screens pass, so the
        // squash is vertical and the widening horizontal.
        coroutineScope {
            launch { stretchX.animateTo(FLIP_STRETCH, tween(FLIP_LEG_MS, easing = EaseIn)) }
            squashY.animateTo(0f, tween(FLIP_LEG_MS, easing = EaseIn))
        }
        shown = placed.owner // yoyo(): switchColor()
        showBack = true // yoyo(): hide()
        squashY.animateTo(FLIP_STRETCH, tween(FLIP_LEG_MS, easing = EaseOut))
        squashY.animateTo(0f, tween(FLIP_LEG_MS, easing = EaseIn)) // unflip()
        showBack = false // yoyo2(): show()
        coroutineScope {
            launch { stretchX.animateTo(1f, tween(FLIP_LEG_MS, easing = EaseOut)) }
            squashY.animateTo(1f, tween(FLIP_LEG_MS, easing = EaseOut))
        }
    }

    CardFace(
        card = placed.card.copy(owner = shown),
        scale = scale,
        showBack = showBack,
        modifier = Modifier.graphicsLayer {
            scaleX = stretchX.value
            scaleY = squashY.value
        },
    )
}

/**
 * One side's remaining cards, in a fixed-size box so the board stays put as the hand empties.
 *
 * Dimmed when it is not that side's turn. [HAND_SIZE] slots are always laid out; the empty ones
 * are spacers, which is what holds the arrangement steady.
 */
@Composable
private fun HandArea(
    state: MatchState,
    owner: CardColor,
    selected: Card?,
    visibility: HandVisibility,
    layout: MatchLayout,
    onSelect: (Card) -> Unit,
) {
    val cards = state.hands[owner].orEmpty()
    val active = state.currentPlayer == owner
    val gap = HandGap * layout.scale

    Box(
        modifier = Modifier
            .size(layout.handWidth, layout.handHeight)
            .graphicsLayer { alpha = if (active) 1f else INACTIVE_HAND_ALPHA },
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0 until layout.handRows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (column in 0 until layout.handColumns) {
                        val slot = row * layout.handColumns + column
                        val card = cards.getOrNull(slot)
                        if (card == null) {
                            Spacer(
                                Modifier.size(
                                    CardSpriteWidth * layout.scale,
                                    CardSpriteHeight * layout.scale,
                                ),
                            )
                        } else {
                            // Keyed by the card, not by the slot. Slots close up when a card is
                            // played, so without this every slot behind the played one is handed
                            // a different card and silently keeps the previous one's composition
                            // state. That is what made cards draw each other's artwork
                            // (`rememberCardFace`, and `CardFaceTest`); nothing else in a slot
                            // holds state today, and this is what stops the next thing that does.
                            key(card.textureId) {
                                HandCard(
                                    card = card,
                                    owner = owner,
                                    slot = slot,
                                    isSelected = active && selected?.id == card.id,
                                    active = active,
                                    // The player always sees their own hand whatever the Open rule
                                    // says — `openPhase` assigns `RULE_ALL_OPEN` to `bluePlayer` on
                                    // both of its branches (`BaseMatchScreen.as:172`, `:176`), so
                                    // Open is only ever about the opponent.
                                    faceUp = owner == CardColor.BLUE || visibility.isVisible(card),
                                    scale = layout.scale,
                                    onSelect = onSelect,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One selectable card in a hand.
 *
 * The selection ring is a border on the card's own bounds rather than a frame around them: a
 * frame would have to grow the slot, and a growing slot moves every card beside it.
 */
@Composable
@Suppress("LongParameterList")
private fun HandCard(
    card: Card,
    owner: CardColor,
    slot: Int,
    isSelected: Boolean,
    active: Boolean,
    faceUp: Boolean,
    scale: Float,
    onSelect: (Card) -> Unit,
) {
    Box(
        modifier = Modifier
            .testTag(handCardTestTag(owner, slot))
            .clickable(enabled = active) { onSelect(card) },
    ) {
        CardFace(card = card, scale = scale, showBack = !faceUp)
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(CardSpriteWidth * scale, CardSpriteHeight * scale)
                    .border(SelectionRingWidth, SelectionRing, TileShape),
            )
        }
    }
}

/**
 * How the board and the two hands are arranged, and at what size.
 *
 * @property landscape true when the hands sit either side of the board rather than above and
 *   below it.
 * @property handColumns cards across in one hand area — five in a portrait strip, two in a
 *   landscape block.
 * @property handRows rows needed to hold [HAND_SIZE] cards at [handColumns] across.
 * @property scale the factor a **hand** card is drawn at. 1.0 is the authored AS3 size.
 * @property boardScale the factor a **board** tile is drawn at, always at least [scale]. The
 *   board is only three cards across where a portrait hand is five, so it is not bound by the
 *   same budget and would otherwise leave a third of a phone screen empty. The FFXIV board
 *   draws it larger than the hands too.
 */
internal data class MatchLayout(
    val landscape: Boolean,
    val handColumns: Int,
    val handRows: Int,
    val scale: Float,
    val boardScale: Float,
) {
    /** Fixed size of one hand area, empty slots included. */
    val handWidth: Dp
        get() = (CardSpriteWidth * handColumns + HandGap * (handColumns + 1)) * scale
    val handHeight: Dp
        get() = (CardSpriteHeight * handRows + HandGap * (handRows + 1)) * scale
}

/**
 * Chooses the arrangement for a **measured** [width] x [height] and the largest scale that
 * fits inside it.
 *
 * A pure function of two numbers, which is the whole point: three earlier attempts estimated
 * the leftover space from a screen size minus a constant and each one over-subscribed the
 * column on some device. An over-subscribed column is not a visible error either — `Modifier
 * .size` silently coerces into the constraints it is given, so children collapse to zero
 * height while continuing to draw at full size, and the symptom is overlap rather than a
 * clipped or complaining layout. Deriving the scale from real bounds cannot do that.
 *
 * Both hands are the same shape, so in landscape the total width is two hand areas plus the
 * board and the height is whichever of hand or board is taller; in portrait the axes swap.
 */
internal fun matchLayout(width: Dp, height: Dp): MatchLayout {
    val landscape = width >= height
    val columns = if (landscape) LANDSCAPE_HAND_COLUMNS else HAND_SIZE
    val rows = (HAND_SIZE + columns - 1) / columns

    val handWidth = CardSpriteWidth.value * columns + HandGap.value * (columns + 1)
    val handHeight = CardSpriteHeight.value * rows + HandGap.value * (rows + 1)
    val boardWidth = CardSpriteWidth.value * BOARD_WIDTH + TileGap.value * (BOARD_WIDTH + 1)
    val boardHeight = CardSpriteHeight.value * BOARD_WIDTH + TileGap.value * (BOARD_WIDTH + 1)

    val neededWidth = if (landscape) handWidth * 2 + boardWidth else maxOf(handWidth, boardWidth)
    val neededHeight =
        if (landscape) maxOf(handHeight, boardHeight) else handHeight * 2 + boardHeight

    val scale = minOf(width.value / neededWidth, height.value / neededHeight)
        .coerceIn(MIN_CARD_SCALE, MAX_CARD_SCALE)

    // Whatever the hands did not need, on the axis they are stacked along.
    val boardWidthBudget = if (landscape) width.value - handWidth * 2 * scale else width.value
    val boardHeightBudget =
        if (landscape) height.value else height.value - handHeight * 2 * scale
    val boardScale = minOf(boardWidthBudget / boardWidth, boardHeightBudget / boardHeight)
        .coerceIn(scale, MAX_CARD_SCALE)

    return MatchLayout(landscape, columns, rows, scale, boardScale)
}

private const val BOARD_WIDTH = 3

/** Two columns of cards either side of the board: taller than wide, which landscape has. */
private const val LANDSCAPE_HAND_COLUMNS = 2

private const val MIN_CARD_SCALE = 0.22f

/** 1.0 is the authored 88x118 face. Drawing bigger than the source art would only blur it. */
private const val MAX_CARD_SCALE = 1f
private const val ELEMENT_LABEL_CHARS = 3
private const val INACTIVE_HAND_ALPHA = 0.45f

/** `Starling.juggler.tween(this, 0.1, ...)`, four times over -- `Card.as:249-291`. */
private const val FLIP_LEG_MS = 100

/** `scaleX: 1.2` / `scaleY: 1.2` -- the overshoot each leg tweens to. */
private const val FLIP_STRETCH = 1.2f

/**
 * How long the opponent appears to think.
 *
 * `PVEMatchScreen.as:42` waits `1000 + tools.rand(4) * 1000` — one to five seconds — which covered
 * a `setTimeout` cascade of turn announcements this port does not have. Long enough that a
 * placement reads as the opponent's move rather than as part of the player's, and short enough not
 * to be a wait.
 */
private const val OPPONENT_PAUSE_MS = 700L

/**
 * Seeds the per-turn generator that Chaos draws its card from.
 *
 * Derived from the placement count rather than taken from the match's own generator, so the same
 * card stays playable for the whole of one turn: `playableCards` draws on every call, and a call
 * per recomposition would move the target while the player reached for it.
 */
private const val CHAOS_SEED = 20260802

/*
 * `Transitions.EASE_IN` / `EASE_OUT`, per the mapping in
 * [api-mapping.md](../../../../../../../docs/analysis/api-mapping.md). Starling's curves are
 * not identical to Compose's; a visual diff pass is still owed.
 */
private val EaseIn = FastOutLinearInEasing
private val EaseOut = LinearOutSlowInEasing

private val TileGap = 4.dp
private val TileShape = RoundedCornerShape(6.dp)
private val EmptyTile = Color(0xFF1E2230)
private val TileBorder = Color(0xFF3A4152)
private val SelectionRing = Color(0xFFF2C14E)
private val RuleStripText = Color(0xFFF2C14E)
private val PayoutText = Color(0xFF7FD18B)
private val PanelBackground = Color(0xFF11141C)
private val SelectionRingWidth = 2.dp
private val ElementFontSize = 9.sp
private val HandGap = 3.dp
