package com.tripletriad.i18n

/**
 * Every key the UI looks up, named once.
 *
 * Not just tidiness: `Strings[key]` falls back to returning the key, so a typo in a literal is
 * invisible in review and shows up as `STR_NEXT_MACTH` on a device. Naming them here means
 * [`StringsBundleTest`](../../../../../desktopTest/kotlin/com/tripletriad/i18n/StringsBundleTest.kt)
 * can walk [all] and assert each one resolves in each locale — a check that is worth more than
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
    const val STARTUP_READY: String = "APP_STARTUP_READY"

    /** Every key above, for the tests that assert each resolves. */
    val all: List<String> = listOf(
        NEXT_MATCH, YOU_WIN, YOU_LOSE, DRAW, SUDDEN_DEATH,
        LOADING_CARDS, SIDE_BLUE, SIDE_RED, TURN_PICK_CARD, TURN_PICK_CELL,
        PLAY, SETTINGS, QUIT,
        GENERAL_SETTINGS, AUDIO_SETTINGS, LANGUAGE, BACKGROUND_VOLUME, NOISE_VOLUME,
        BACK, AUDIO_PENDING,
        STARTUP_SETTINGS, STARTUP_ART, STARTUP_READY,
    )

    /** The subset this port authored, which is the subset that may be untranslated. */
    val appOwned: List<String> = all.filter { it.startsWith("APP_") }
}
