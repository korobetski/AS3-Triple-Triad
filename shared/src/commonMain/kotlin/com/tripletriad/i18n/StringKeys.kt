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

    // ---- Dashboard. `dashboardScreen.as:49-59` builds its stack from exactly these.
    /** `Multiplayer` — listed, and inert: PvP is Phase 5. */
    const val MULTIPLAYER: String = "STR_MULTIPLAYER"
    const val CARD_LIST: String = "STR_CARD_LIST"

    /**
     * `Cards` — the title over the collection and the decks once they share a screen.
     *
     * No AS3 equivalent, and deliberately not one of the two it covers: `STR_CARD_LIST` over a tab
     * showing decks, or `STR_CARD_DECKS` over one showing the collection, would each be wrong half
     * the time. The two tabs keep their own names.
     */
    const val CARDS: String = "APP_CARDS"

    /**
     * `Home` — the navigation bar's name for the dashboard.
     *
     * No AS3 equivalent for the same reason as [CARDS]: the original had no bar, and its dashboard
     * was titled with the character's name. That is still its app-bar title; this is what the bar
     * calls the place it goes back to.
     */
    const val HOME: String = "APP_HOME"
    const val CARD_DECKS: String = "STR_CARD_DECKS"
    const val INVENTORY: String = "STR_INVENTORY"
    const val SHOP: String = "STR_SHOP"
    const val HELP: String = "STR_HELP"

    /** `Logout` — leaves the character, which is what returning to the main menu is. */
    const val LOGOUT: String = "STR_LOGOUT"

    // ---- Card list and card detail.
    /** `Card Informations` — the original's title, plural included. */
    const val CARD_INFOS: String = "STR_CARD_INFOS"
    const val TOTAL: String = "STR_TOTAL"
    const val SIDES: String = "STR_SIDES"
    const val RARITY: String = "STR_RARITY"
    const val CARD_TYPE: String = "STR_CARD_TYPE"

    /** No AS3 key: its detail panel simply stayed blank until a card was tapped. */
    const val PICK_CARD: String = "APP_PICK_CARD"

    /** No AS3 key: the collection screen dimmed unowned cards and said nothing. */
    const val OWNED: String = "APP_OWNED"

    // ---- Decks.
    const val DECK: String = "STR_DECK"
    const val DECK_POWER: String = "STR_DECK_POWER"
    const val RESET_DECK: String = "STR_RESET_DECK"
    const val SAVE: String = "STR_SAVE"

    /** `Play this deck` — the deck selector's confirm, shown before the deal. */
    const val CHOOSE_DECK: String = "STR_CHOOSE_DECK"

    /**
     * `Random` — the rule's own name, reused by `DeckSelector` for its "deal me anything" button
     * (`DeckSelector.as:113` looks up `RULE_RANDOM`, not a button key of its own).
     */
    const val RANDOM_DECK: String = "RULE_RANDOM"

    /**
     * No AS3 key: `DeckSelector.as:84-86` handles an empty list with an empty block, so a player
     * with no complete deck was shown a blank panel and no reason for it.
     */
    const val NO_FULL_DECK: String = "APP_NO_FULL_DECK"

    // ---- Inventory and shop.
    const val USE: String = "STR_USE"
    const val SELL: String = "STR_SELL"
    const val DISCARD: String = "STR_DISCARD"
    const val BUY: String = "STR_BUY"

    /** `Card Shop` — the shop panel's title, where `STR_SHOP` is the menu entry. */
    const val CARD_SHOP: String = "STR_CARD_SHOP"

    /** No AS3 key: an empty bag drew an empty list. */
    const val EMPTY_BAG: String = "APP_EMPTY_BAG"

    /** `{0}` is the card's name. What opening a pack yielded. */
    const val OBTAINED: String = "APP_OBTAINED"

    /** Why Use is refused on a card the profile already has — `InventoryScreen.as:111`. */
    const val ALREADY_OWNED: String = "APP_ALREADY_OWNED"

    /**
     * A bag entry whose `type` this build does not know.
     *
     * `Item.itemize`'s `else` branch — [com.tripletriad.model.MiscItem] — which the original drew
     * with an empty label and a booster icon. Reachable only from a save written by a newer build
     * or from the declared-and-unused `item-type-accessory`.
     */
    const val UNKNOWN_ITEM: String = "APP_UNKNOWN_ITEM"

    // ---- Character statistics.
    const val ACHIEVEMENTS_LIST: String = "STR_ACHIEVEMENTS_LIST"
    const val FORFEITS: String = "STR_FORFEITS"

    /**
     * `Matches`.
     *
     * An `APP_` key although `profileScreen.as:191` asks for `STR_MATCHES`: that key is **in none
     * of the four bundles**, so the original's own round chart was captioned `STR_MATCHES`. A
     * dangling key is not a translation to preserve.
     */
    const val MATCHES: String = "APP_MATCHES"

    /** No AS3 equivalent: it drew a pie chart and never wrote the number. */
    const val WIN_RATE: String = "APP_WIN_RATE"

    /** The two potion multipliers, which the original showed as two unlabelled icons. */
    const val BOONS: String = "APP_BOONS"

    /** No AS3 key: an achievement list with nothing in it rendered as an empty group. */
    const val NO_ACHIEVEMENT: String = "APP_NO_ACHIEVEMENT"

    // ---- The tutorial's nine lines.
    /**
     * `TutorialScreen.helpTexts` — and `APP_` keys although the text is nine years old, because
     * **the original never translated them**. They are Flash string literals in the middle of a
     * screen class, with no `i18n.gettext` around them and no matching key in any of the four
     * bundles: a French player was taught Triple Triad in English.
     *
     * So they enter through the port's own bundle, where they can be. The wording is the AS3's,
     * with one correction — its line 9 reads "the winnner".
     */
    const val TUTORIAL_1: String = "APP_TUTORIAL_1"
    const val TUTORIAL_2: String = "APP_TUTORIAL_2"
    const val TUTORIAL_3: String = "APP_TUTORIAL_3"
    const val TUTORIAL_4: String = "APP_TUTORIAL_4"
    const val TUTORIAL_5: String = "APP_TUTORIAL_5"
    const val TUTORIAL_6: String = "APP_TUTORIAL_6"
    const val TUTORIAL_7: String = "APP_TUTORIAL_7"
    const val TUTORIAL_8: String = "APP_TUTORIAL_8"
    const val TUTORIAL_9: String = "APP_TUTORIAL_9"

    /** The campaign entry `PVEScreen.as:79` draws as a bare `tt_tuto` texture with no label. */
    const val TUTORIAL: String = "APP_TUTORIAL"

    // ---- The two tournament ladders.
    /** `Campaigns` — the panel `PVEScreen.as:73` puts above the opponent list. */
    const val CAMPAIGNS: String = "STR_CAMPAIGNS"

    /**
     * `Match {0} of {1}` — how far up a ladder the player is.
     *
     * No AS3 equivalent: the original tracks `STEP` and never shows it, so a player seven matches
     * into the Card Club has no way to know how many are left.
     */
    const val CAMPAIGN_STEP: String = "APP_CAMPAIGN_STEP"

    // ---- The account the menu remembers.
    /**
     * `Servers` — the menu card, which was the one hard-coded English label left on that screen.
     *
     * `MenuScreen.as` has no such entry: the AS3 build talked to one host, named at compile time.
     */
    const val SERVERS: String = "APP_SERVERS"

    /** `Continue` — the AS3's own, and what the resume card offers on a live session. */
    const val CONTINUE: String = "STR_CONTINUE"

    /** `Signed in` — a stored token was accepted and no form was needed. */
    const val SESSION_RESTORED: String = "APP_SESSION_RESTORED"

    /** `Reconnecting…` — `AccountSession.restore` is still asking. */
    const val SESSION_CONNECTING: String = "APP_SESSION_CONNECTING"

    /** `Session expired` — the name is remembered and the token is not usable. */
    const val SESSION_LAPSED: String = "APP_SESSION_LAPSED"

    /** `Sign in again` — the lapsed card's action, which opens the form with the name filled. */
    const val SIGN_IN_AGAIN: String = "APP_SIGN_IN_AGAIN"

    /** `Switch` — sign out, which is also what makes the app forget the name. */
    const val SWITCH_ACCOUNT: String = "APP_SWITCH_ACCOUNT"

    // ---- The sign-in form, which was the last screen written in hard-coded English.
    /**
     * `Sign in` — the form's title and its button.
     *
     * `STR_CONNECT` exists and is *Connect*, which in the AS3 meant joining the game's lobby. It is
     * not the same claim, and reusing it would have been a translation chosen for being available.
     */
    const val SIGN_IN: String = "APP_SIGN_IN"

    /** `Create an account`. */
    const val CREATE_ACCOUNT: String = "APP_CREATE_ACCOUNT"

    /** `Password` — no `STR_PASSWORD` in any bundle; the AS3 build had no accounts. */
    const val PASSWORD: String = "APP_PASSWORD"

    /** What an account buys: the character lives on the server. */
    const val ACCOUNT_BLURB: String = "APP_ACCOUNT_BLURB"

    /** `New here? Create an account` and its opposite. */
    const val ACCOUNT_TO_REGISTER: String = "APP_ACCOUNT_TO_REGISTER"
    const val ACCOUNT_TO_SIGN_IN: String = "APP_ACCOUNT_TO_SIGN_IN"

    /** The title over a build the server will not serve. */
    const val UPDATE_NEEDED: String = "APP_UPDATE_NEEDED"

    // ---- What a refused request is told to the player. See `AccountResult.message`.
    const val ERROR_OFFLINE: String = "APP_ERROR_OFFLINE"
    const val ERROR_UPDATE: String = "APP_ERROR_UPDATE"

    /** `The server answered {0}.` — a status code, for the failures with no better name. */
    const val ERROR_STATUS: String = "APP_ERROR_STATUS"
    const val ERROR_NAME_TAKEN: String = "APP_ERROR_NAME_TAKEN"
    const val ERROR_BAD_CREDENTIALS: String = "APP_ERROR_BAD_CREDENTIALS"
    const val ERROR_EXPIRED: String = "APP_ERROR_EXPIRED"

    /**
     * `{0} more open up as you level.` — under the opponent list.
     *
     * The list is filtered by the player's level now, and a list that silently omits three quarters
     * of the table reads as a short table. See [com.tripletriad.data.NpcCatalog.available].
     */
    const val OPPONENTS_LOCKED: String = "APP_OPPONENTS_LOCKED"

    // ---- The server list, which had no translated string on it at all.
    /** What choosing another server costs, and what it does not. */
    const val SERVERS_BLURB: String = "APP_SERVERS_BLURB"

    /** The probe button, and what it says while it is out. */
    const val SERVERS_CHECK: String = "APP_SERVERS_CHECK"
    const val SERVERS_CHECKING: String = "APP_SERVERS_CHECKING"

    /**
     * One phrase per [com.tripletriad.net.ServerStatus]. `{0}` on [SERVER_ONLINE] is the latency.
     *
     * Seven states, four colours — see `ServerStatus.tint`. The wording is what tells them apart.
     */
    const val SERVER_UNKNOWN: String = "APP_SERVER_UNKNOWN"
    const val SERVER_CHECKING: String = "APP_SERVER_CHECKING"
    const val SERVER_ONLINE: String = "APP_SERVER_ONLINE"
    const val SERVER_DEGRADED: String = "APP_SERVER_DEGRADED"
    const val SERVER_OUTDATED: String = "APP_SERVER_OUTDATED"
    const val SERVER_UNREACHABLE: String = "APP_SERVER_UNREACHABLE"
    const val SERVER_UNUSABLE: String = "APP_SERVER_UNUSABLE"

    /** The update notice. `{0}` is the version the server wants. */
    const val UPDATE_REQUIRED: String = "APP_UPDATE_REQUIRED"
    const val UPDATE_REQUIRED_BODY: String = "APP_UPDATE_REQUIRED_BODY"
    const val UPDATE_AVAILABLE: String = "APP_UPDATE_AVAILABLE"
    const val UPDATE_AVAILABLE_BODY: String = "APP_UPDATE_AVAILABLE_BODY"
    const val UPDATE_GET: String = "APP_UPDATE_GET"

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
        MULTIPLAYER, CARD_LIST, CARDS, HOME, CARD_DECKS, INVENTORY, SHOP, HELP, LOGOUT,
        CARD_INFOS, TOTAL, SIDES, RARITY, CARD_TYPE, PICK_CARD, OWNED,
        DECK, DECK_POWER, RESET_DECK, SAVE, CHOOSE_DECK, RANDOM_DECK, NO_FULL_DECK,
        USE, SELL, DISCARD, BUY, CARD_SHOP, EMPTY_BAG, OBTAINED, ALREADY_OWNED, UNKNOWN_ITEM,
        ACHIEVEMENTS_LIST, FORFEITS, MATCHES, WIN_RATE, BOONS, NO_ACHIEVEMENT,
        TUTORIAL, TUTORIAL_1, TUTORIAL_2, TUTORIAL_3, TUTORIAL_4, TUTORIAL_5,
        TUTORIAL_6, TUTORIAL_7, TUTORIAL_8, TUTORIAL_9,
        CAMPAIGNS, CAMPAIGN_STEP,
        SERVERS, CONTINUE, SESSION_RESTORED, SESSION_CONNECTING, SESSION_LAPSED,
        SIGN_IN_AGAIN, SWITCH_ACCOUNT,
        SIGN_IN, CREATE_ACCOUNT, PASSWORD, ACCOUNT_BLURB,
        ACCOUNT_TO_REGISTER, ACCOUNT_TO_SIGN_IN, UPDATE_NEEDED,
        ERROR_OFFLINE, ERROR_UPDATE, ERROR_STATUS,
        ERROR_NAME_TAKEN, ERROR_BAD_CREDENTIALS, ERROR_EXPIRED,
        OPPONENTS_LOCKED,
        SERVERS_BLURB, SERVERS_CHECK, SERVERS_CHECKING,
        SERVER_UNKNOWN, SERVER_CHECKING, SERVER_ONLINE, SERVER_DEGRADED,
        SERVER_OUTDATED, SERVER_UNREACHABLE, SERVER_UNUSABLE,
        UPDATE_REQUIRED, UPDATE_REQUIRED_BODY, UPDATE_AVAILABLE, UPDATE_AVAILABLE_BODY, UPDATE_GET,
    )

    /** The subset this port authored, which is the subset that may be untranslated. */
    val appOwned: List<String> = all.filter { it.startsWith("APP_") }
}
