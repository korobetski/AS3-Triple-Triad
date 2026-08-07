package com.tripletriad.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Who the profile played against. `MatchHistory.opponentType` in the migration plan's schema. */
@Serializable
enum class OpponentKind {
    /** A `datas/NPCs.as` opponent. [MatchRecord.opponentName] is its `STR_NPC_*` key. */
    NPC,

    /** Another player, over `tto.net.TTONet`. [MatchRecord.opponentName] is their display name. */
    PVP,
}

/** How the match ended, from the profile's point of view. */
@Serializable
enum class MatchResult {
    WIN,
    LOSE,
    DRAW,
    ;

    companion object {
        /**
         * The result for the side [self] played, or null if the match has not been decided.
         *
         * [MatchOutcome.SuddenDeath] returns null on purpose: it is a draw that has *not* resolved
         * — a rematch follows and decides the match — so recording it as `DRAW` would count one
         * match twice. `PVEMatchScreen.as:175` likewise dispatches the rematch instead of writing
         * stats.
         */
        fun of(outcome: MatchOutcome, self: CardColor): MatchResult? = when (outcome) {
            is MatchOutcome.Win -> if (outcome.winner == self) WIN else LOSE
            is MatchOutcome.Draw -> DRAW
            is MatchOutcome.SuddenDeath -> null
        }
    }
}

/**
 * One finished match, as kept for history.
 *
 * ### New in the port
 *
 * The AS3 has no match history: it increments four counters on the profile (`STARTED_MATCHES`,
 * `ENDED_MATCHES`, `PVE_MATCHES`, `PVP_MATCHES`) plus `NPC_W` and `RULES_W`, and throws the match
 * itself away. `docs/migration/06-PHASE-2-DATA-LAYER.md` Task 2.3 introduces a `MatchHistory`
 * table, and this is its row — so unlike [GameSave] there is no legacy shape to stay compatible
 * with, and the field names are plain camelCase.
 *
 * It is kept **separate from the profile** rather than as another `GameSave` list: history grows
 * without bound while a profile does not, and rewriting a whole profile to append one match is the
 * one thing that would make saving get slower over a player's lifetime.
 *
 * @property id caller-supplied and unique. Not defaulted to a generated UUID: `commonMain` had no
 *   stable UUID API when this was written, and an impure default makes the type untestable — the
 *   same reasoning `docs/migration/13-DATA-MODELS.md` applies to `GameState.id`.
 * @property self which side the profile played. Needed to read [result] and [score] from the right
 *   end, since a PvP guest is red.
 * @property durationMillis wall-clock length. `0` when unknown; nothing requires it.
 * @property rules what was in force, so "wins with Plus" is answerable from history rather
 *   than only from the running `RULES_W` counters.
 */
@Serializable
data class MatchRecord(
    val id: String,
    val mode: CardCollection,
    val opponentKind: OpponentKind,
    val opponentName: String? = null,
    /** The NPC's `datas/NPCs.as` id, when [opponentKind] is [OpponentKind.NPC]. */
    val npcId: Int? = null,
    /** Epoch millis at which the match finished. */
    val timestamp: Long,
    val result: MatchResult,
    val self: CardColor = CardColor.BLUE,
    @SerialName("score_blue") val scoreBlue: Int,
    @SerialName("score_red") val scoreRed: Int,
    val durationMillis: Long = 0L,
    val rules: GameRules = GameRules(),
    /** MGP won or lost, match fee included. Signed. */
    val mgpDelta: Int = 0,
    val xpGained: Long = 0L,
) {
    init {
        require(id.isNotBlank()) { "match id must not be blank" }
        require(scoreBlue >= 0 && scoreRed >= 0) {
            "scores must not be negative, were $scoreBlue-$scoreRed"
        }
    }

    val score: MatchScore get() = MatchScore(scoreBlue, scoreRed)

    /** The score from the profile's side first, which is how a history row reads it out. */
    val ownScore: Int get() = score[self]

    val opponentScore: Int get() = score[self.opposite()]

    companion object {
        /**
         * Builds a row from a finished [MatchState], or null if it is not finished or ended in an
         * unresolved sudden death.
         *
         * Everything the state cannot know — the id, the clock, the opponent, the rewards — is a
         * parameter. That keeps the model free of both a clock and an id generator, which is what
         * makes a history row reproducible in a test.
         */
        @Suppress("LongParameterList")
        fun of(
            id: String,
            state: MatchState,
            mode: CardCollection,
            opponentKind: OpponentKind,
            timestamp: Long,
            self: CardColor = CardColor.BLUE,
            opponentName: String? = null,
            npcId: Int? = null,
            durationMillis: Long = 0L,
            mgpDelta: Int = 0,
            xpGained: Long = 0L,
        ): MatchRecord? {
            val outcome = state.outcome() ?: return null
            return MatchResult.of(outcome, self)?.let { result ->
                MatchRecord(
                    id = id,
                    mode = mode,
                    opponentKind = opponentKind,
                    opponentName = opponentName,
                    npcId = npcId,
                    timestamp = timestamp,
                    result = result,
                    self = self,
                    scoreBlue = outcome.score.blue,
                    scoreRed = outcome.score.red,
                    durationMillis = durationMillis,
                    rules = state.rules,
                    mgpDelta = mgpDelta,
                    xpGained = xpGained,
                )
            }
        }
    }
}
