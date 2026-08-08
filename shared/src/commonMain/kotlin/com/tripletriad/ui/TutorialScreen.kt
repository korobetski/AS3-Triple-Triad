package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.NpcCatalog
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.CardCollection
import com.tripletriad.model.CardColor
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchAiOptions
import com.tripletriad.model.Npc
import com.tripletriad.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * The lesson — `TutorialScreen.as`, which is `PVEMatchScreen` with four methods overridden.
 * A scripted match under All Open, on a fixed hand, with the opponent moving first and playing the
 * worst card it can find, while nine lines explain what is happening. Everything that differs from
 * an ordinary match is in the [MatchScript] this builds; the match itself is [MatchScreen],
 * unmodified.
 *
 * ### Why it uses the shipped opponent rather than the AS3's inline one
 * `TutorialScreen.as:37-49` declares its own `NPC` inline — the same id, name, icon, rule and
 * fetish cards as the tt-master already in `npcs.json`, but with `matchFee: 0`, a smaller
 * `MGPReward` and item drop rates a third of the catalogue's. That is the shape `shopScreen` had
 * before Phase 2 pulled its price table out into `ShopCatalog`: data written into a screen.
 * This port reads the catalogue entry instead, and so **charges the tutorial's entry fee and pays
 * its full reward**. Both differences are deliberate and small — five MGP against a starting
 * balance of several hundred — and the alternative is a second tt-master record whose only purpose
 * is to be slightly different from the first, which is exactly the duplication the phase has been
 * removing. The lesson is not repeatable-for-profit either way: [MatchScreen]'s Rematch is replaced
 * with a route to the rule book, below.
 *
 * ### Three lines the original never showed
 * `opponentPhase` is called from **one** place — the red branch of `BaseMatchScreen.nextTurn`
 * (`:380`) — so it never runs on the player's turn. But `TutorialScreen` overrides it with branches
 * on `turn == 2` and `turn == 4`, which are blue's turns and therefore unreachable: lines 4, 5 and
 * 8 of its nine never appear. They are also the three most instructional ones ("Now it's your
 * turn!", "The numbers you can see each correspond to one side of the card"), which is what makes
 * it a defect rather than a design — the branches were written for the player's turn and hooked to
 * a callback that only fires on the opponent's.
 *
 * Here the lesson is driven off the placement count for both sides, so all nine play.
 *
 * @param tutor who teaches it — [tutorFor], which is the Triple Triad Master for an `ff14_`
 *   character and Kid for an `ff8_` one. The original could only ever have been the first, since it
 *   hard-codes `MODE = 'ff14_'`.
 * @param onHelp where the end panel's first action goes — the rule book, replacing Rematch
 *   (`TutorialRematchPanel.nextLesson` dispatches `NEXT_SCREEN`, which is `HELP_SCREEN`).
 * @param onExit the second action, and the back chevron. `exitBtnHandler` sends both to
 *   `PVE_SCREEN`, which here is the opponent list.
 */
@Composable
@Suppress("LongParameterList")
internal fun TutorialScreen(
    catalog: CardCatalog,
    profile: GameSave,
    tutor: Npc,
    clock: Clock,
    onPersist: suspend (GameSave) -> Unit,
    onHelp: () -> Unit,
    onExit: () -> Unit,
) {
    val script = remember(tutor.nameKey) {
        MatchScript(
            speakerKey = tutor.nameKey,
            deck = TUTORIAL_DECK,
            firstPlayer = CardColor.RED,
            turnLimit = TUTORIAL_TURN_LIMIT,
            aiOptions = MatchAiOptions.TUTOR,
            lesson = ::tutorialLines,
        )
    }
    MatchScreen(
        catalog = catalog,
        profile = profile,
        npc = tutor,
        clock = clock,
        onPersist = onPersist,
        onExit = onExit,
        script = script,
        scriptExit = ScriptExit(StringKeys.HELP, onHelp),
    )
}

/**
 * Who teaches the lesson in [collection] — the opponent with the lowest id.
 *
 * `tt-master` in `ff14`, `kid` in `ff8`, and the choice is not arbitrary: both are id 1, both are
 * `LEVEL_NOVICE`, and **both declare `RULE_ALL_OPEN` and nothing else**, which is what the lesson's
 * second line announces. An opponent imposing Reverse would make the script say something untrue.
 *
 * Null only for an empty table, which `NpcBundleTest` rules out; the caller shows nothing rather
 * than crashing, on the same footing as the rest of [App]'s `?.let` chain.
 */
internal fun tutorFor(catalog: NpcCatalog, collection: CardCollection): Npc? =
    catalog.collection(collection).minByOrNull { it.id }

/**
 * The nine lines, placed on the turns they were written for.
 * Red opens, so red holds the even placements and the player the odd ones. Against the AS3's
 * 1-based `turn`, every index here is one lower.
 *
 * | Placement | AS3 turn | Lines | Reached in the original? |
 * |---|---|---|---|
 * | 0 | 1, red | 1, 2, 3 | yes |
 * | 1 | 2, blue | 4, 5 | **no** |
 * | 2 | 3, red | 6 if the player captured, then 7 | yes |
 * | 3 | 4, blue | 8 | **no** |
 * | 8 | 9, red | 9 | yes |
 *
 * @param blueScore the player's score. Line 6 says "See how my card changed color?" and the
 * original guards it with `if (scores.BLUE > 5)` — the score opens at five all, so above five means
 * the player has captured something. A congratulation for a capture that did not happen would be
 * worse than silence.
 */
internal fun tutorialLines(placement: Int, blueScore: Int): List<String> = when (placement) {
    TUTOR_OPENS -> listOf(StringKeys.TUTORIAL_1, StringKeys.TUTORIAL_2, StringKeys.TUTORIAL_3)
    PLAYER_OPENS -> listOf(StringKeys.TUTORIAL_4, StringKeys.TUTORIAL_5)
    TUTOR_REPLIES -> buildList {
        if (blueScore > OPENING_SCORE) add(StringKeys.TUTORIAL_6)
        add(StringKeys.TUTORIAL_7)
    }
    PLAYER_REPLIES -> listOf(StringKeys.TUTORIAL_8)
    LAST_PLACEMENT -> listOf(StringKeys.TUTORIAL_9)
    else -> emptyList()
}

/** The AS3's turns 1 to 4, which are placements 0 to 3 — see the table above. */
private const val TUTOR_OPENS = 0
private const val PLAYER_OPENS = 1
private const val TUTOR_REPLIES = 2
private const val PLAYER_REPLIES = 3

/**
 * `BLUE_CARDS = [1, 3, 6, 7, 10]` (`TutorialScreen.as:54`) — the hand the lesson is written around.
 *
 * Fixed rather than chosen, and it has to be: line 5 tells the player to pick a card with a bigger
 * number on the touching side, which is only sound advice if the hand is known to contain one.
 * These are ids in whichever collection the character plays, so an `ff8_` character is dealt the
 * first, third, sixth, seventh and tenth FF8 cards instead. That is the same indirection every
 * other screen uses ([GameSave.mode] decides which table an id reads from) and the lesson holds
 * either way, because it never names a card — the original could not have known, since it
 * hard-codes `MODE = 'ff14_'` and never changes it.
 */
@Suppress("MagicNumber") // Transcribed card ids: naming each one would say nothing it does not.
private val TUTORIAL_DECK = listOf(1, 3, 6, 7, 10)

/** `bluePlayer.timer = 60` — see [MatchScript.turnLimit] for why it is double. */
private val TUTORIAL_TURN_LIMIT = 60.seconds

/** Five all, before anything is captured. */
private const val OPENING_SCORE = 5

/** The ninth and last placement, index 8 — the AS3's `turn == 9`. */
private const val LAST_PLACEMENT = 8
