package com.tripletriad.i18n

/**
 * Every key the UI looks up, named once.
 *
 * Not just tidiness: `Strings[key]` falls back to returning the key, so a typo in a literal is
 * invisible in review and shows up as `STR_NEXT_MACTH` on a device. Naming them here means
 * [`StringsBundleTest`](../../../../../desktopTest/kotlin/com/tripletriad/i18n/StringsBundleTest.kt
 * ) can walk [all] and assert each one resolves in each locale — a check that is worth more than
 * the constants are.
 *
 * `STR_*` and `RULE_*` come from the AS3 bundles; `APP_*` are this port's own. See [loadStrings].
 */
object StringKeys {
    /** `Next Match` — reused for the reset control; the AS3 had no "new match" of its own. */
    const val NEXT_MATCH: String = "STR_NEXT_MATCH"

    /**
     * `You win !` / `You lose...` — the outcome is phrased from the local player's side, which
     * is blue. `data-flow.md` §`openPhase` records the same assumption in the original: the
     * local player always sees their own hand, and it is always the blue one.
     */
    const val YOU_WIN: String = "STR_YOU_WIN"
    const val YOU_LOSE: String = "STR_YOU_LOSE"

    const val DRAW: String = "STR_DRAW"

    /** The rule's name, used to qualify a draw that goes to a decider. */
    const val SUDDEN_DEATH: String = "RULE_SUDDEN_DEATH"

    const val LOADING_CARDS: String = "APP_LOADING_CARDS"
    const val SIDE_BLUE: String = "APP_SIDE_BLUE"
    const val SIDE_RED: String = "APP_SIDE_RED"

    /** `{0}` is the side. */
    const val TURN_PICK_CARD: String = "APP_TURN_PICK_CARD"

    /** `{0}` is the side, `{1}` the selected card's name. */
    const val TURN_PICK_CELL: String = "APP_TURN_PICK_CELL"

    // ---- Main menu. All three come from the AS3 bundles, so all four languages have them:
    // `MenuScreen.as` builds its stack from `STR_CONTINUE`/`STR_NEW_GAME`/`STR_LOAD_GAME`/
    // `STR_SETTINGS`/`STR_QUIT`. This port shows three of them for now.
    const val PLAY: String = "STR_PLAY"

    /** The AS3's own label for its settings screen, and it really is "Options" in en_US. */
    const val SETTINGS: String = "STR_SETTINGS"
    const val QUIT: String = "STR_QUIT"

    // ---- Options screen.
    const val GENERAL_SETTINGS: String = "STR_GENERAL_SETTINGS"
    const val AUDIO_SETTINGS: String = "STR_AUDIO_SETTINGS"
    const val LANGUAGE: String = "STR_LANGUAGE"
    const val BACKGROUND_VOLUME: String = "STR_BACKGROUND_VOLUME"
    const val NOISE_VOLUME: String = "STR_NOISE_VOLUME"

    /**
     * No AS3 equivalent: its screens all used `STR_CANCEL`, which is the wrong word for
     * leaving a pane that has already saved everything.
     */
    const val BACK: String = "APP_BACK"

    /** Says out loud that the two volume sliders persist but nothing plays yet. */
    const val AUDIO_PENDING: String = "APP_AUDIO_PENDING"

    // ---- Splash. One key per `StartupPhase`, in the same order.
    const val STARTUP_SETTINGS: String = "APP_STARTUP_SETTINGS"
    const val STARTUP_ART: String = "APP_STARTUP_ART"
    const val STARTUP_OPPONENTS: String = "APP_STARTUP_OPPONENTS"
    const val STARTUP_READY: String = "APP_STARTUP_READY"

    // ---- Profiles. `STR_PROFILE` is "Character" in en_US, which is the original's word for a
    // save: `LoadScreen` lists characters, not files. Kept, rather than substituting "profile".
    const val PROFILE: String = "STR_PROFILE"
    const val PROFILES: String = "STR_LOAD_GAME"
    const val NEW_PROFILE: String = "STR_NEW_GAME"
    const val USERNAME: String = "STR_USERNAME"

    /** `Collection` — the AS3's own label for the `ff14_` / `ff8_` choice. */
    const val COLLECTION: String = "STR_MODE"
    const val LEVEL: String = "STR_LEVEL"
    const val MGP: String = "STR_MGP"
    const val WINS: String = "STR_WINS"
    const val DEFEATS: String = "STR_DEFEATS"
    const val DRAWS: String = "STR_DRAWS"
    const val DELETE: String = "STR_DELETE"

    /** `Do you really wants to delete this game ?` — the original's wording, typo included. */
    const val DELETE_CONFIRM: String = "STR_DELETE_SAVE_CONFIRMATION_MESSAGE"
    const val CANCEL: String = "STR_CANCEL"
    const val START: String = "STR_START"

    /** No AS3 key: nothing in the original ever said a save list was empty. */
    const val NO_PROFILE: String = "APP_NO_PROFILE"

    // ---- Opponents.
    const val OPPONENTS: String = "STR_OPPONENTS"
    const val RULES: String = "STR_RULES"
    const val MATCH_FEE: String = "STR_MATCH_FEE"
    const val REWARDS: String = "STR_REWARDS"

    /** `Defy` — the AS3's verb for challenging an opponent. */
    const val CHALLENGE: String = "STR_REGISTER_MATCH"

    /** No AS3 key: its opponent list simply omitted whoever was unavailable, saying nothing. */
    const val NO_OPPONENT: String = "APP_NO_OPPONENT"

    /** `XP` — no `STR_XP` exists, though `STR_MGP` does. */
    const val XP: String = "APP_XP"

    /** `Difficulty` — the field is in the data, but the AS3 never labelled it. */
    const val DIFFICULTY: String = "APP_DIFFICULTY"

    // ---- Match.
    /** `{0}` is the side. Replaces the "pick a card" line while the opponent moves. */
    const val OPPONENT_TURN: String = "APP_OPPONENT_TURN"

    /** Heading over the newly-earned achievements in the end-of-match panel. */
    const val ACHIEVEMENT_EARNED: String = "APP_ACHIEVEMENT_EARNED"

    /** `Rematch` — the control that plays the same opponent again. */
    const val REMATCH: String = "STR_REMATCH"

    /** Every key above, for the tests that assert each resolves. */
    val all: List<String> = listOf(
        NEXT_MATCH, YOU_WIN, YOU_LOSE, DRAW, SUDDEN_DEATH,
        LOADING_CARDS, SIDE_BLUE, SIDE_RED, TURN_PICK_CARD, TURN_PICK_CELL,
        PLAY, SETTINGS, QUIT,
        GENERAL_SETTINGS, AUDIO_SETTINGS, LANGUAGE, BACKGROUND_VOLUME, NOISE_VOLUME,
        BACK, AUDIO_PENDING,
        STARTUP_SETTINGS, STARTUP_ART, STARTUP_OPPONENTS, STARTUP_READY,
        PROFILE, PROFILES, NEW_PROFILE, USERNAME, COLLECTION,
        LEVEL, MGP, WINS, DEFEATS, DRAWS,
        DELETE, DELETE_CONFIRM, CANCEL, START, NO_PROFILE,
        OPPONENTS, RULES, MATCH_FEE, REWARDS, CHALLENGE, NO_OPPONENT,
        XP, DIFFICULTY, OPPONENT_TURN, ACHIEVEMENT_EARNED, REMATCH,
    )

    /** The subset this port authored, which is the subset that may be untranslated. */
    val appOwned: List<String> = all.filter { it.startsWith("APP_") }
}
