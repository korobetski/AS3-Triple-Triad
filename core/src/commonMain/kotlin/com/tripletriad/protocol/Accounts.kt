package com.tripletriad.protocol

import com.tripletriad.model.CardCollection
import com.tripletriad.model.GameSave
import com.tripletriad.model.Item
import com.tripletriad.model.MatchResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The account protocol: who a player is, and what the server holds for them.
 *
 * ### Why these types are in `:core` and not in either half
 *
 * The same reason `MatchTranscript` is: both ends must agree, and the only way to guarantee that
 * they do is for there to be **one** definition that both link. A DTO duplicated across a
 * repository boundary is a wire format maintained by convention, and `AppVersion` exists precisely
 * because convention is not enough.
 *
 * ### What an account is, as of decision 2 in its full form
 *
 * The account **is** the character. Signing in loads the profile from the server; there is no local
 * character that a server account merely annotates. That is what makes the progression trustworthy:
 * the only writer of a [GameSave] is the server, and it only writes one in response to a match it
 * replayed itself.
 */

/**
 * What a player types to create an account or to come back to one.
 *
 * The two are one type on purpose. They carry the same fields and are validated by the same rules,
 * and splitting them would be two chances to disagree about what a legal username is.
 *
 * @property username the account name, and also the character's name. Compared case-insensitively
 *   by the server, so `Kuplu` and `kuplu` are the same account and cannot both be created.
 * @property password never stored, never logged, never echoed back in any response. The server
 *   keeps only a hash — see the server's `PasswordHasher`.
 */
@Serializable
data class Credentials(
    val username: String,
    val password: String,
) {
    /**
     * Whether these could possibly be valid, checked **before** they are sent.
     *
     * Client-side validation is not a security measure and is not treated as one — the server
     * checks the same rules and is the only check that counts. It is here so a player learns that
     * their password is too short without a round trip, and so the two ends cannot disagree about
     * what they are asking for.
     */
    fun looksValid(): Boolean =
        username.trim().length in USERNAME_LENGTH && password.length in PASSWORD_LENGTH

    companion object {
        /**
         * Short enough for a name, long enough not to be a single character.
         *
         * The upper bound is not arbitrary: it is what a save file, a scoreboard and an opponent
         * list can all render without truncation, and refusing at the boundary is better than
         * silently cutting somebody's name in half later.
         */
        val USERNAME_LENGTH = 3..24

        /**
         * A floor, and only a floor.
         *
         * Eight is short by any modern advice and is deliberately not raised further here: this is
         * a game account holding no payment details and no personal data, and a rule strict enough
         * to be worked around with `Password1!` buys nothing. The upper bound exists because bcrypt
         * silently ignores input past 72 bytes, and a limit the algorithm imposes should be one the
         * player is told about rather than one that quietly makes their long password shorter.
         */
        val PASSWORD_LENGTH = 8..72
    }
}

/**
 * A signed-in session.
 *
 * @property token the bearer token. **Secret**: it is exactly as good as the password for as long
 *   as it lives, so it belongs in the client's own storage and nowhere else — not in a log, not in
 *   a URL, not in an error message.
 * @property expiresAt epoch millis after which the server will refuse it. Sent so the client can
 *   ask for a new one before a player is dropped mid-match rather than after.
 * @property player everything the client needs to render the game, so signing in is one round trip
 *   rather than two.
 */
@Serializable
data class Session(
    val token: String,
    val expiresAt: Long,
    val player: PlayerState,
)

/**
 * The server's whole view of a player: the profile, and what it is derived from.
 *
 * [save] and [stats] are sent together because they are written together — a verified match updates
 * both in one transaction — and a client holding one without the other would show a profile whose
 * win count disagreed with its own match list.
 */
@Serializable
data class PlayerState(
    val save: GameSave,
    val stats: PlayerStats = PlayerStats(),
)

/**
 * What the server has recorded about a player's matches.
 *
 * ### Why the counters and the history travel together
 *
 * Because the counters are **derived from** the history rather than kept alongside it. The server
 * aggregates the match rows on read, so the two cannot drift; sending both lets a client show a
 * summary and the matches behind it without a second request, and lets anyone reading this see
 * at a glance that one is a function of the other.
 *
 * Note this is *not* the same as [GameSave.stats], which the AS3 kept and this port preserves.
 * Those counters belong to the profile and include things the server has never seen — cards
 * flipped, a perfect-win tally. These are the server's own record of matches it replayed, and
 * where the two disagree, this one is the one that is checkable.
 *
 * @property recent the newest matches, newest first, bounded by the server. Not the whole history:
 *   a client wants a recent-form list, and shipping thousands of rows on every sign-in to draw
 *   ten of them is the sort of thing that is invisible until somebody has played for a year.
 */
@Serializable
data class PlayerStats(
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val recent: List<VerifiedMatch> = emptyList(),
) {
    val played: Int get() = wins + losses + draws
    val winRate: Float get() = if (played == 0) 0f else wins.toFloat() / played
}

/**
 * One match the server replayed and accepted.
 *
 * The scores are the **server's**, recomputed from the seed, which is what makes a row here worth
 * more than the same row written by a client. [seed] is kept for the same reason: with it, any of
 * these can be replayed again years later and must reach the same numbers — which is the audit
 * trail that aggregate counters alone would have thrown away.
 *
 * @property playedAt when the server accepted it, not when the client says it was played. A
 *   transcript queued offline for three days is recorded on the day it arrived, because that is the
 *   only one of the two timestamps the server can vouch for.
 */
@Serializable
data class VerifiedMatch(
    val id: Long,
    @SerialName("at") val playedAt: Long,
    val opponentIconId: String,
    val collection: CardCollection,
    val seed: Int,
    val blue: Int,
    val red: Int,
    val result: MatchResult,
    val mgp: Int = 0,
    val xp: Int = 0,
)

/**
 * What comes back from submitting a match.
 *
 * ### Why this and not the bare verdict
 *
 * Because progression is server-held, a submission is no longer a question ("is this match real?")
 * but a transaction ("this match happened; here is what I now own"). The verdict alone would leave
 * the client to apply the reward itself, which is precisely the arrangement this design exists to
 * end — the client would once again be the thing deciding what it earned.
 *
 * @property player the profile **after** crediting, or null when nothing was credited: a rejected
 *   transcript, or an unauthenticated caller using the bare verification endpoint. A client adopts
 *   this in place of what it was holding; it does not merge.
 * @property reward what this match paid, for the end-of-match panel. Null for the same reasons as
 *   [player].
 * @property duplicate true when this transcript had already been accepted and was **not** credited
 *   again. Not an error: a queue that drains twice after a failed acknowledgement is ordinary, and
 *   the honest answer is the original verdict plus a flag, not a rejection that would tell the
 *   player their real match was fake.
 */
@Serializable
data class MatchReceipt(
    val verdict: MatchVerdict,
    val player: PlayerState? = null,
    val reward: RewardSummary? = null,
    val duplicate: Boolean = false,
)

/**
 * What a match paid, as the server computed it.
 *
 * Close to `:core`'s own `MatchReward` but not the same type, and the difference is in what the two
 * halves carry. [items] is `Item` itself, because `Item` is already a serializable sealed hierarchy
 * and a `CardItem` without its card id would be a reward the panel cannot draw. [achievementIds] is
 * ids only, because `Achievement` carries a `Requirement` — an evaluation rule, not a value — and
 * both ends hold the same catalog to look the rest up in.
 *
 * The boon flags are dropped: they say a stored boon was *spent*, which the client can see for
 * itself by comparing the profile it sent with the one it got back.
 */
@Serializable
data class RewardSummary(
    val result: MatchResult,
    val mgp: Int,
    val xp: Int,
    val items: List<Item> = emptyList(),
    val achievementIds: List<String> = emptyList(),
)

/** Why a request about an account was refused. Machine-readable, so wording can change freely. */
@Serializable
enum class AccountError {
    /** The username is taken. Only ever answered to a registration. */
    USERNAME_TAKEN,

    /** The username or the password is wrong — deliberately not saying which. */
    INVALID_CREDENTIALS,

    /** The username or password does not satisfy [Credentials.looksValid]. */
    MALFORMED_CREDENTIALS,

    /** The bearer token is absent, unknown, or past its expiry. */
    UNAUTHENTICATED,
}

/**
 * The body of a refused account request.
 *
 * [AccountError.INVALID_CREDENTIALS] covers both "no such account" and "wrong password" on purpose:
 * distinguishing them would turn the sign-in form into a way of finding out which usernames exist.
 * The cost is one confusing moment for someone who mistyped their own name, and the benefit is that
 * an account list cannot be harvested from the outside.
 */
@Serializable
data class AccountFailure(
    val error: AccountError,
    val detail: String,
)
