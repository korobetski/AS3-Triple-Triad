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

    /** Every key above, for the tests that assert each resolves. */
    val all: List<String> = listOf(
        NEXT_MATCH, YOU_WIN, YOU_LOSE, DRAW, SUDDEN_DEATH,
        LOADING_CARDS, SIDE_BLUE, SIDE_RED, TURN_PICK_CARD, TURN_PICK_CELL,
    )

    /** The subset this port authored, which is the subset that may be untranslated. */
    val appOwned: List<String> = all.filter { it.startsWith("APP_") }
}
