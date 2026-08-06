package com.tripletriad.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tripletriad.audio.AudioPlayer
import com.tripletriad.audio.LocalAudio
import com.tripletriad.audio.SilentAudioPlayer
import com.tripletriad.audio.Sound
import com.tripletriad.data.CardCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.rememberStrings
import com.tripletriad.model.GameSave
import com.tripletriad.model.Npc
import com.tripletriad.settings.InMemorySettingsStore
import com.tripletriad.settings.SettingsStore
import com.tripletriad.settings.UserSettings
import com.tripletriad.settings.UserSettingsRepository
import com.tripletriad.storage.DocumentStore
import com.tripletriad.storage.InMemoryDocumentStore
import com.tripletriad.time.Clock
import com.tripletriad.time.FixedClock
import kotlinx.coroutines.launch

private val Backdrop = Color(0xFF14161C)

/**
 * Which screen is showing.
 *
 * A `remember`ed value and not a navigation library. There are fourteen destinations now and the
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
    DASHBOARD,
    OPPONENTS,
    MATCH,
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
     */
    val up: Screen
        get() = when (this) {
            SPLASH, MENU -> this
            PROFILES, OPTIONS -> MENU
            PROFILE_NEW -> PROFILES
            DASHBOARD -> PROFILES
            OPPONENTS, STATS, CARDS, DECKS, INVENTORY, SHOP, HELP -> DASHBOARD
            MATCH -> OPPONENTS
        }
}

/**
 * The whole app: splash while it loads, then the menu, then a match or the options.
 *
 * @param store where `UserSettings.json` lives. Supplied by the host, because `:shared` has no
 *   platform file access of its own — see `SettingsStore`. Defaults to an in-memory store so a
 *   preview or a test needs no filesystem, and so that a test can pin the language by handing in
 *   `InMemorySettingsStore("""{"language":"en_US"}""")` rather than inheriting whatever locale the
 *   machine running it happens to be set to.
 * @param documents where the `.sav` profiles live, for the same reason and from the same host. The
 *   in-memory default means a test gets a working, empty profile list rather than the machine's
 *   real saves — and that a preview cannot delete anybody's character.
 * @param clock the wall clock. Injected so a test can pin both the save timestamps and the hour
 *   that decides which opponents are available. **Defaults to a stopped clock**, not a real one:
 *   `:shared` has no `SystemClock` — see `Clock` — and a frozen 2026-01-01T12:00 is a working,
 *   obvious default for a preview or a test, the same bargain `InMemorySettingsStore` and
 *   `SilentAudioPlayer` make. Both real hosts pass one.
 * @param audio plays the sounds. Silent by default, which is also what the desktop host installs —
 *   see `AudioPlayer`.
 * @param onQuit what the Quit action does. Nothing, by default: a host that cannot express quitting
 *   (iOS) or does not want to (a preview) is a legitimate host, and the button being inert is
 *   better than `:shared` guessing.
 */
// `BackHandler` is still `@ExperimentalComposeUiApi` in Compose 1.9.3. Opted into here rather than
// project-wide, so the day it moves or changes shape there is exactly one call site to fix.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(
    store: SettingsStore = InMemorySettingsStore(),
    documents: DocumentStore = InMemoryDocumentStore(),
    clock: Clock = FixedClock(),
    audio: AudioPlayer = SilentAudioPlayer,
    onQuit: () -> Unit = {},
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize(), color = Backdrop) {
            val startup = rememberStartup(store)
            val settings = rememberSettingsHolder(store, startup.settings)
            // Before the settings file has been read there is no language to render in, so the
            // splash's first frames use the fallback bundle. It says "reading settings…" in
            // English for a few milliseconds; the alternative is a blank screen for the same few
            // milliseconds. The holder is preferred over `startup` once it exists, because from
            // then on the options screen owns the language.
            val locale = settings?.value?.locale ?: startup.settings?.locale ?: AppLocale.Default
            val strings = rememberStrings(locale)

            // The player is told the volumes rather than reading the settings itself, so
            // nothing under `AudioPlayer` knows what a `UserSettings` is. Keyed on the values so a
            // slider drag reaches the running music immediately.
            val settingsValue = settings?.value
            LaunchedEffect(audio, settingsValue?.backgroundVolume, settingsValue?.noiseVolume) {
                settingsValue?.let { audio.volumes(it.backgroundVolume, it.noiseVolume) }
            }

            val session = rememberProfileSession(documents, clock)
            var screen by remember { mutableStateOf(Screen.SPLASH) }
            var opponent by remember { mutableStateOf<Npc?>(null) }

            // Read once, when the app is ready — not on entering the profile list, so the menu can
            // already name the character and so the list is never briefly empty on arrival.
            LaunchedEffect(startup.isReady) {
                if (startup.isReady) session.refresh()
            }
            // Leaves the splash exactly once, when the last phase completes. Driven by an effect
            // rather than by deriving the screen from `startup.isReady`, because from then on the
            // *user* decides where they are and startup must stop having an opinion.
            LaunchedEffect(startup.isReady) {
                if (startup.isReady && screen == Screen.SPLASH) screen = Screen.MENU
            }

            // Android's system back gesture, which would otherwise finish the activity mid-match
            // — the app would appear to quit from the middle of a game. `BackHandler` is
            // multiplatform in Compose 1.9 (`androidx.compose.ui.backhandler`), so this needs no
            // Android-only source set; on desktop it simply never fires. Disabled on the menu so
            // back there still leaves the app, which is what a main menu should do.
            BackHandler(enabled = screen != screen.up) {
                screen = screen.up
            }

            // The music belongs to the match, as in `BaseMatchScreen.as:114` — it starts when a
            // match opens and stops when it is left. Nothing plays on the splash or the menu, which
            // is also the original's behavior: `MenuScreen` never called `shuffleLoop`.
            LaunchedEffect(screen, audio) {
                if (screen == Screen.MATCH) audio.play(Sound.MATCH_MUSIC) else audio.stopMusic()
            }

            CompositionLocalProvider(LocalStrings provides strings, LocalAudio provides audio) {
                // Only a hairline of padding: the board and ten cards want every dp there is.
                Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                    // Crossfade so the splash does not snap to the menu. 220 ms is short enough
                    // not to feel like a wait and long enough to read as a transition.
                    Crossfade(targetState = screen, label = "screen") { destination ->
                        Destination(
                            destination = destination,
                            startup = startup,
                            settings = settings,
                            session = session,
                            opponent = opponent,
                            clock = clock,
                            onNavigate = { screen = it },
                            onChoose = { opponent = it },
                            onQuit = onQuit,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One screen.
 *
 * Split out of [App] so that `App` is the shell — theme, startup, locale, audio, back gesture — and
 * this is the routing table. They were one function until detekt called it out at a cyclomatic
 * complexity of 20, and the rule was right: a reader wanting to know where "Play" goes had to
 * scroll past the volume plumbing to find out.
 *
 * @param onNavigate where a screen asks to go next. A single callback rather than one per
 *   destination: the transitions are `screen = x` and nothing else, and seven lambdas that each
 *   assign a constant would be seven places for a wrong constant to hide.
 */
@Composable
private fun Destination(
    destination: Screen,
    startup: StartupState,
    settings: SettingsHolder?,
    session: ProfileSession,
    opponent: Npc?,
    clock: Clock,
    onNavigate: (Screen) -> Unit,
    onChoose: (Npc) -> Unit,
    onQuit: () -> Unit,
) {
    when (destination) {
        Screen.SPLASH -> SplashScreen(startup)

        Screen.MENU -> MainMenuScreen(
            active = session.active,
            // Play goes straight to the dashboard when a character is loaded and to the character
            // list when none is — the original's Continue and Load Game behind one button, chosen
            // by what is actually loaded rather than by asking which of the two the player meant.
            onPlay = {
                onNavigate(if (session.active == null) Screen.PROFILES else Screen.DASHBOARD)
            },
            onProfiles = { onNavigate(Screen.PROFILES) },
            onOptions = { onNavigate(Screen.OPTIONS) },
            onQuit = onQuit,
        )

        Screen.PROFILES -> ProfileListScreen(
            session = session,
            onSelected = { onNavigate(Screen.DASHBOARD) },
            onNew = { onNavigate(Screen.PROFILE_NEW) },
            onBack = { onNavigate(Screen.MENU) },
        )

        Screen.PROFILE_NEW -> ProfileCreateScreen(
            session = session,
            onCreated = { onNavigate(Screen.DASHBOARD) },
            onBack = { onNavigate(Screen.PROFILES) },
        )

        Screen.OPTIONS -> settings?.let {
            OptionsScreen(settings = it, onBack = { onNavigate(Screen.MENU) })
        }

        // Everything behind the dashboard needs a character, and a missing one is a state the flow
        // cannot reach: the dashboard is only entered from the list or from creation, both of which
        // select one. Rendering nothing is the honest answer, rather than half a screen or a
        // placeholder claiming something is wrong.
        //
        // Grouped rather than delegated behind an `else`, so that adding a fourteenth screen is a
        // compile error here instead of a destination that silently renders blank.
        Screen.DASHBOARD, Screen.OPPONENTS, Screen.MATCH, Screen.STATS,
        Screen.CARDS, Screen.DECKS, Screen.INVENTORY, Screen.SHOP, Screen.HELP,
        -> session.active?.let { profile ->
            CharacterDestination(
                destination = destination,
                profile = profile,
                startup = startup,
                session = session,
                opponent = opponent,
                clock = clock,
                onNavigate = onNavigate,
                onChoose = onChoose,
            )
        }
    }
}

/**
 * One of the nine screens behind the dashboard.
 *
 * Split from [Destination] because they share a prerequisite — a loaded character — and checking it
 * once is what keeps the eight call sites from each writing their own `?.let`. It is also what
 * keeps either function under the complexity detekt rejects: the two together are the routing table
 * the original spread across a `gotoScreen` string switch in `Game.as`.
 *
 * The card catalog is the second prerequisite and is *not* hoisted the same way: the dashboard, the
 * statistics and the help screen do not need it, and gating them on it would leave them blank while
 * `cards.json` loads.
 */
@Composable
private fun CharacterDestination(
    destination: Screen,
    profile: GameSave,
    startup: StartupState,
    session: ProfileSession,
    opponent: Npc?,
    clock: Clock,
    onNavigate: (Screen) -> Unit,
    onChoose: (Npc) -> Unit,
) {
    val toDashboard = { onNavigate(Screen.DASHBOARD) }

    when (destination) {
        Screen.DASHBOARD -> DashboardScreen(
            profile = profile,
            onPlay = { onNavigate(Screen.OPPONENTS) },
            onStats = { onNavigate(Screen.STATS) },
            onCards = { onNavigate(Screen.CARDS) },
            onDecks = { onNavigate(Screen.DECKS) },
            onInventory = { onNavigate(Screen.INVENTORY) },
            onShop = { onNavigate(Screen.SHOP) },
            onHelp = { onNavigate(Screen.HELP) },
            onLogout = { onNavigate(Screen.PROFILES) },
        )

        Screen.OPPONENTS -> startup.opponents?.let { opponents ->
            OpponentScreen(
                profile = profile,
                catalog = opponents,
                hour = clock.localHour(),
                onChallenge = {
                    onChoose(it)
                    onNavigate(Screen.MATCH)
                },
                onBack = toDashboard,
            )
        }

        Screen.MATCH -> startup.catalog?.let { catalog ->
            opponent?.let { chosen ->
                // `LocalCardArt` is provided even when null: a card composes correctly with no
                // textures at all — flat colour quad, empty layers — so a failed art load costs
                // appearance, not playability.
                CompositionLocalProvider(LocalCardArt provides startup.art) {
                    MatchScreen(
                        catalog = catalog,
                        profile = profile,
                        npc = chosen,
                        clock = clock,
                        onPersist = session::persist,
                        onExit = { onNavigate(Screen.OPPONENTS) },
                    )
                }
            }
        }

        Screen.STATS -> StatsScreen(profile = profile, onBack = toDashboard)

        Screen.HELP -> HelpScreen(profile = profile, onBack = toDashboard)

        // The four that browse the card table. Grouped for the same reason the character-bearing
        // screens are grouped one level up: they share a prerequisite, and checking it four times
        // is four places for one of them to forget.
        Screen.CARDS, Screen.DECKS, Screen.INVENTORY, Screen.SHOP,
        -> startup.catalog?.let { catalog ->
            CompositionLocalProvider(LocalCardArt provides startup.art) {
                CollectionDestination(
                    destination = destination,
                    profile = profile,
                    catalog = catalog,
                    onPersist = session::persist,
                    onBack = toDashboard,
                )
            }
        }

        // The five screens ahead of a loaded character. [Destination] routes those itself and never
        // calls this with one; the branch exists because Kotlin requires the `when` to be complete.
        Screen.SPLASH, Screen.MENU, Screen.PROFILES, Screen.PROFILE_NEW, Screen.OPTIONS -> Unit
    }
}

/**
 * The four screens that read the card table: the collection, the decks, the bag and the shop.
 *
 * All four take the same four arguments and differ only in which composable they call, which is
 * what makes them worth one function — and what keeps [CharacterDestination] under the complexity
 * detekt rejects. The card art is already provided by the caller.
 */
@Composable
private fun CollectionDestination(
    destination: Screen,
    profile: GameSave,
    catalog: CardCatalog,
    onPersist: suspend (GameSave) -> Unit,
    onBack: () -> Unit,
) {
    when (destination) {
        Screen.CARDS -> CardListScreen(profile = profile, catalog = catalog, onBack = onBack)

        Screen.DECKS ->
            DecksScreen(profile, catalog, onPersist = onPersist, onBack = onBack)

        Screen.INVENTORY ->
            InventoryScreen(profile, catalog, onPersist = onPersist, onBack = onBack)

        Screen.SHOP ->
            ShopScreen(profile, catalog, onPersist = onPersist, onBack = onBack)

        else -> Unit
    }
}

/**
 * Wraps the loaded settings in something the options screen can mutate.
 *
 * Null until startup has read the file. Keyed on nothing but the store, so the holder — and any
 * change the user has since made — survives recomposition; `initial` seeds it once.
 */
@Composable
private fun rememberSettingsHolder(
    store: SettingsStore,
    initial: UserSettings?,
): SettingsHolder? {
    val scope = rememberCoroutineScope()
    val repository = remember(store) { UserSettingsRepository(store) }
    var holder by remember(store) { mutableStateOf<SettingsHolder?>(null) }
    LaunchedEffect(store, initial) {
        if (holder == null && initial != null) {
            holder = SettingsHolder(initial) { updated ->
                scope.launch { repository.save(updated) }
            }
        }
    }
    return holder
}
