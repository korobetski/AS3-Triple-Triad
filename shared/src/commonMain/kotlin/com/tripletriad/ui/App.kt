package com.tripletriad.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.tripletriad.audio.AudioPlayer
import com.tripletriad.audio.LocalAudio
import com.tripletriad.audio.SilentAudioPlayer
import com.tripletriad.audio.Sound
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.SaveRepository
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.rememberStrings
import com.tripletriad.model.GameSave
import com.tripletriad.model.Npc
import com.tripletriad.net.MatchReporter
import com.tripletriad.net.ServerConnection
import com.tripletriad.net.accountQueueKey
import com.tripletriad.settings.InMemorySettingsStore
import com.tripletriad.settings.SettingsStore
import com.tripletriad.settings.UserSettings
import com.tripletriad.settings.UserSettingsRepository
import com.tripletriad.storage.DocumentStore
import com.tripletriad.storage.InMemoryDocumentStore
import com.tripletriad.time.Clock
import com.tripletriad.time.FixedClock
import com.tripletriad.ui.theme.LocalTtoColors
import com.tripletriad.ui.theme.TripleTriadTheme
import kotlinx.coroutines.launch

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
 * @param server the connection to the account server, or null for an offline build. **Null is a
 *   supported configuration and not a degraded one**: without a server the game plays exactly as it
 *   did before accounts existed, off local `.sav` profiles, and every preview, screenshot and UI
 *   test gets that for free. With one, the character comes from the server instead and the local
 *   profile list is not reachable — see [ProfileGate]. The base URL and the HTTP engine are the
 *   host's business, as the file paths are.
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
    server: ServerConnection? = null,
) {
    TripleTriadTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = LocalTtoColors.current.backdrop) {
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
            val account = server?.let { rememberAccountSession(it, clock) }
            val connectivity = server?.let { rememberConnectivity(it) }
            var screen by remember { mutableStateOf(Screen.SPLASH) }
            var opponent by remember { mutableStateOf<Npc?>(null) }

            val gate = rememberGate(session, account)
            val reporter = server?.reporter ?: MatchReporter.None

            StartupEffects(startup, server, account, session) {
                if (screen == Screen.SPLASH) screen = Screen.MENU
            }

            // Draining "at launch" means, in practice, when a character is in play: the queue is
            // per character, so before there is one there is nothing to drain and no key to drain
            // it under. Keyed on the key rather than on the save, because `persist` replaces the
            // save object after every match and re-running the effect then would put a network call
            // at the end of each one — which is the thing the queue exists to avoid.
            LaunchedEffect(gate.queueKey, reporter) {
                gate.queueKey?.let { reporter.drain(it) }
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
                            account = account,
                            connectivity = connectivity,
                            gate = gate,
                            opponent = opponent,
                            clock = clock,
                            reporter = reporter,
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
 * The three things that have to happen once, in order, before the menu is shown.
 *
 * Extracted from [App] because they are a sequence with a reason — the server, then the session on
 * it, then the local profiles if there is no session — and because [App] is otherwise the shell:
 * theme, locale, audio, back gesture. They were inline until detekt called the shell out at a
 * cyclomatic complexity of 15, and the rule was right; a reader wanting to know why the splash ends
 * when it does had to find three effects among the volume plumbing.
 *
 * @param onReady called when every phase has completed and a stored session, if any, has been
 *   tried. A callback and not a returned flag because the destination is [App]'s decision: from
 *   then on the *user* decides where they are and startup must stop having an opinion.
 */
@Composable
private fun StartupEffects(
    startup: StartupState,
    server: ServerConnection?,
    account: AccountSession?,
    session: ProfileSession,
    onReady: () -> Unit,
) {
    // The chosen server first, then the session on it — in that order and in one effect, because a
    // session is stored per server and restoring one before knowing which server we are on would
    // read the wrong key. Runs once: `restore` sets `isRestored` whatever it finds, and re-running
    // it on every recomposition would be a request per frame.
    LaunchedEffect(server, account) {
        server?.directory?.restore()
        account?.restore()
    }

    // Read once, when the app is ready — not on entering the profile list, so the menu can already
    // name the character and so the list is never briefly empty on arrival. Skipped entirely with a
    // server: the local profiles are not reachable then, and reading them would be a disk scan for
    // a list nothing renders.
    LaunchedEffect(startup.isReady, account) {
        if (startup.isReady && account == null) session.refresh()
    }

    // The stored session is waited for as well, so a returning player is never shown the menu's
    // "Play" leading to a sign-in form they did not need.
    val isRestored = account?.isRestored ?: true
    LaunchedEffect(startup.isReady, isRestored) {
        if (startup.isReady && isRestored) onReady()
    }
}

/**
 * Where the character in play comes from.
 *
 * The one place in the app that answers "account or local profile", so that everything below it —
 * the routing table, the thirteen screens, the drain — is written against [ProfileGate] and does
 * not know which. A function rather than two lines inside [App] because it is the decision, not
 * plumbing: naming it is what makes it findable.
 */
@Composable
private fun rememberGate(session: ProfileSession, account: AccountSession?): ProfileGate =
    if (account != null) rememberAccountGate(account) else rememberLocalGate(session)

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
@Suppress("LongParameterList")
private fun Destination(
    destination: Screen,
    startup: StartupState,
    settings: SettingsHolder?,
    session: ProfileSession,
    account: AccountSession?,
    connectivity: Connectivity?,
    gate: ProfileGate,
    opponent: Npc?,
    clock: Clock,
    reporter: MatchReporter,
    onNavigate: (Screen) -> Unit,
    onChoose: (Npc) -> Unit,
    onQuit: () -> Unit,
) {
    // Where "choose a character" leads. The account screen with a server, the local profile list
    // without one — the one place the two flows differ, named once so the four call sites below do
    // not each decide it again.
    val chooser = if (account != null) Screen.ACCOUNT else Screen.PROFILES

    when (destination) {
        Screen.SPLASH -> SplashScreen(startup)

        Screen.MENU -> MainMenuScreen(
            active = gate.profile,
            connectivity = connectivity,
            // Play goes straight to the dashboard when a character is loaded and to the chooser
            // when none is — the original's Continue and Load Game behind one button, chosen by
            // what is actually loaded rather than by asking which of the two the player meant.
            onPlay = { onNavigate(if (gate.profile == null) chooser else Screen.DASHBOARD) },
            onProfiles = { onNavigate(chooser) },
            onServers = { onNavigate(Screen.SERVERS) },
            onOptions = { onNavigate(Screen.OPTIONS) },
            onQuit = onQuit,
        )

        // The two screens that exist only on a build with a server. Grouped so the routing table
        // has one arm for "the account flow" rather than two that each re-derive whether there is
        // an account to have a flow about.
        Screen.ACCOUNT, Screen.SERVERS -> AccountDestination(
            destination = destination,
            account = account,
            connectivity = connectivity,
            onNavigate = onNavigate,
        )

        Screen.PROFILES -> ProfileListScreen(
            session = session,
            // A deleted profile takes its unjudged matches with it. Not merely tidiness: keys are
            // derived from the username and creation date, so a profile created again with the same
            // name on the same day would inherit the old one's queue and submit somebody else's
            // matches under its own progression.
            onDeleted = { reporter.forget(it) },
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
        -> gate.profile?.let { profile ->
            CharacterDestination(
                destination = destination,
                profile = profile,
                startup = startup,
                gate = gate,
                account = account,
                chooser = chooser,
                opponent = opponent,
                clock = clock,
                reporter = reporter,
                onNavigate = onNavigate,
                onChoose = onChoose,
            )
        }
    }
}

/**
 * The sign-in form and the server list.
 *
 * Both need an [AccountSession] and a [Connectivity], and both come from the same `server`, so
 * neither can be present without the other — the `?.let` pair is Kotlin's requirement rather than a
 * state the app can reach. Rendering nothing if it ever were is the honest answer.
 */
@Composable
private fun AccountDestination(
    destination: Screen,
    account: AccountSession?,
    connectivity: Connectivity?,
    onNavigate: (Screen) -> Unit,
) {
    val session = account ?: return
    val state = connectivity ?: return

    when (destination) {
        Screen.SERVERS -> ServersScreen(
            connectivity = state,
            // The switch first, then the fresh reading. Probing after rather than before means the
            // row the player just chose shows what it is *now*, on a connection that has already
            // been through a sign-out and a restore.
            onSelect = { entry -> if (session.useServer(entry)) state.refreshAll() },
            onBack = { onNavigate(Screen.MENU) },
        )

        else -> AccountScreen(
            session = session,
            update = state.update,
            onSignedIn = { onNavigate(Screen.DASHBOARD) },
            onBack = { onNavigate(Screen.MENU) },
        )
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
@Suppress("LongParameterList")
private fun CharacterDestination(
    destination: Screen,
    profile: GameSave,
    startup: StartupState,
    gate: ProfileGate,
    account: AccountSession?,
    chooser: Screen,
    opponent: Npc?,
    clock: Clock,
    reporter: MatchReporter,
    onNavigate: (Screen) -> Unit,
    onChoose: (Npc) -> Unit,
) {
    val toDashboard = { onNavigate(Screen.DASHBOARD) }
    val scope = rememberCoroutineScope()

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
            // With a server, Logout means *sign out*: the token is dropped and the session ended,
            // not merely the screen changed. That is the distinction the original never made — its
            // Logout navigated away and left `Game.PROFILE_DATAS` loaded — and here it matters,
            // because leaving the token behind on a shared device would leave the account behind.
            onLogout = {
                if (account != null) scope.launch { account.signOut() }
                onNavigate(chooser)
            },
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
                        onPersist = gate.persist,
                        onExit = { onNavigate(Screen.OPPONENTS) },
                        // The key is derived from the profile the *match* was played with, not
                        // from the gate, whose profile the credit has already replaced by the time
                        // this runs. Both name the same queue — neither key is built from anything
                        // a match changes — and using the one in hand says so rather than relying
                        // on it.
                        onTranscript = { reporter.report(queueKeyFor(profile, account), it) },
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
                    onPersist = gate.persist,
                    onBack = toDashboard,
                )
            }
        }

        // The seven screens ahead of a loaded character. [Destination] routes those itself and
        // never calls this with one; the branch exists because Kotlin requires a complete `when`.
        Screen.SPLASH, Screen.MENU, Screen.PROFILES, Screen.PROFILE_NEW,
        Screen.ACCOUNT, Screen.SERVERS, Screen.OPTIONS,
        -> Unit
    }
}

/**
 * Which queue a finished match belongs in.
 *
 * The two sources key it differently and both are stable across a match — the local one by name and
 * creation date, the account one by the server and the account name — so this is a choice of
 * *which* stable key, not a computation. Written as a function rather than read off the gate so the
 * call site can pass the profile the match was actually played with; see its comment.
 */
private fun queueKeyFor(
    profile: GameSave,
    account: AccountSession?,
): String =
    if (account != null) {
        accountQueueKey(account.serverId, profile.username)
    } else {
        SaveRepository.keyFor(profile)
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
