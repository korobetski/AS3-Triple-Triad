package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tripletriad.data.SaveRepository
import com.tripletriad.model.GameSave

/**
 * Where the character in play comes from, and where its changes go.
 *
 * ### Why this exists
 *
 * Because there are now two answers and the thirteen screens behind the dashboard must not have to
 * know which one is in force. Without a server the profile is a local `.sav` file, created and
 * written by [ProfileSession]. With one it is the account, fetched and stored by [AccountSession],
 * and only the server may write the parts a match decides. The screens want the same three things
 * from either: the profile, somewhere to put a changed one, and the key its unjudged matches queue
 * under.
 *
 * ### Why a value and not an interface
 *
 * The two implementations have nothing else in common — one lists and deletes local files, the
 * other registers accounts and holds a bearer token — so an interface would have exactly these
 * three members and two classes would implement it by delegating to themselves. A value built at
 * the one place that knows which source is live says the same thing with less indirection, and it
 * makes the *absence* of a character (nobody signed in, nothing selected) a null [profile] rather
 * than a third implementation.
 *
 * @property profile the character in play, or null when there is none.
 * @property queueKey where this character's unsubmitted matches wait, or null with no character.
 *   Derived differently by each source — see [AccountSession.queueKey] — which is precisely the
 *   sort of thing the screens should not be choosing between.
 * @property persist takes a changed profile. Local, this writes a file and stamps it; server-held,
 *   it updates what is on screen and sends it on. Neither throws: a match is already over by the
 *   time this runs.
 */
class ProfileGate(
    val profile: GameSave?,
    val queueKey: String?,
    val persist: suspend (GameSave) -> Unit,
)

/** The gate for a build with no server: the locally selected `.sav` profile. */
@Composable
internal fun rememberLocalGate(session: ProfileSession): ProfileGate {
    val active = session.active
    return remember(active) {
        ProfileGate(
            profile = active,
            queueKey = active?.let(SaveRepository::keyFor),
            persist = session::persist,
        )
    }
}

/** The gate for a build with a server: the account's character. */
@Composable
internal fun rememberAccountGate(session: AccountSession): ProfileGate {
    val player = session.player
    return remember(player) {
        ProfileGate(
            profile = player?.save,
            queueKey = session.queueKey,
            persist = session::persist,
        )
    }
}
