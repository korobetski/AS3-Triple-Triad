package com.tripletriad.ui

/**
 * Which screen is showing.
 *
 * A `remember`ed value and not a navigation library. There are nineteen destinations now and the
 * flow is still a **tree of depth three** — menu → characters → dashboard → one of seven — with one
 * [up] per screen and no deep links, no arguments beyond what the session already holds, and no
 * state to restore across process death that is not already on disk. Compose Navigation would buy a
 * `NavHost`, a route DSL and typed arguments; what it would replace is [up] and two `when`s.
 *
 * The point to reconsider was named in Phase 4's first pass as "a screen reachable from two places
 * with a different back destination from each", and the dashboard is what keeps that from
 * happening: every screen behind it has exactly one way in. [MATCH] is the nearest thing to an
 * exception — a rematch re-enters it from itself — and that is a state change rather than a
 * navigation.
 */
internal enum class Screen {
    SPLASH,
    MENU,
    PROFILES,
    PROFILE_NEW,
    ACCOUNT,
    SERVERS,
    DASHBOARD,
    OPPONENTS,
    MATCH,
    TUTORIAL,
    CAMPAIGN,
    CAMPAIGN_MATCH,
    STATS,
    CARDS,
    DECKS,
    INVENTORY,
    SHOP,
    HELP,
    OPTIONS,
    ;

    /**
     * Where the back gesture goes from here.
     *
     * [SPLASH] and [MENU] return themselves, which is what makes back on the menu fall through to
     * the host and leave the app — the behaviour a main menu should have — without the
     * `BackHandler` needing a list of which screens are exempt.
     *
     * [DASHBOARD] goes to the character list rather than to the menu, which is also where its own
     * Logout leads: leaving a character means choosing another, and the list is where that is done.
     * The original sent Logout to `MENU_SCREEN` and left `Game.PROFILE_DATAS` loaded, so its
     * "logout" changed the screen and nothing else.
     *
     * On a build with a server the dashboard is reached from [ACCOUNT] rather than [PROFILES], and
     * back from it goes to the menu instead — that difference is [App]'s to apply, because it is
     * the only thing that knows whether there is a server, and encoding both flows here would put a
     * conditional in a value that is supposed to be a constant.
     */
    val up: Screen
        get() = when (this) {
            SPLASH, MENU -> this
            PROFILES, ACCOUNT, SERVERS, OPTIONS -> MENU
            PROFILE_NEW -> PROFILES
            DASHBOARD -> PROFILES
            OPPONENTS, STATS, CARDS, DECKS, INVENTORY, SHOP, HELP -> DASHBOARD
            MATCH, TUTORIAL, CAMPAIGN -> OPPONENTS
            CAMPAIGN_MATCH -> CAMPAIGN
        }
}
