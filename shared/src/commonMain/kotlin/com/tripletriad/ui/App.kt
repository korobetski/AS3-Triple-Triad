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
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.rememberStrings
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
 * A `remember`ed value and not a navigation library. There are seven destinations now and the flow
 * is still a line — menu → characters → opponents → board — with one `up` per screen and no deep
 * links, no arguments beyond what the session already holds, and no state to restore across process
 * death that is not already on disk. Compose Navigation would buy a `NavHost`, a route DSL and
 * typed arguments; what it would replace is [up] and one `when`. The point to reconsider is a
 * screen reachable from two places with a different back destination from each — the original's
 * fourteen screens have several. Not yet.
 */
internal enum class Screen {
    SPLASH,
    MENU,
    PROFILES,
    PROFILE_NEW,
    OPPONENTS,
    MATCH,
    OPTIONS,
    ;

    /**
     * Where the back gesture goes from here.
     *
     * [SPLASH] and [MENU] return themselves, which is what makes back on the menu fall through to
     * the host and leave the app — the behaviour a main menu should have — without the
     * `BackHandler` needing a list of which screens are exempt.
     */
    val up: Screen
        get() = when (this) {
            SPLASH, MENU -> this
            PROFILES, OPTIONS -> MENU
            PROFILE_NEW -> PROFILES
            OPPONENTS -> PROFILES
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
 * @param documents where the `.sav` profiles live, for the same reason and from the same host.
 *   The in-memory default means a test gets a working, empty profile list rather than the machine's
 *   real saves — and that a preview cannot delete anybody's character.
 * @param clock the wall clock. Injected so a test can pin both the save timestamps and the hour
 *   that decides which opponents are available. **Defaults to a stopped clock**, not a real one:
 *   `:shared` has no `SystemClock` — see `Clock` — and a frozen 2026-01-01T12:00 is a working,
 *   obvious default for a preview or a test, the same bargain `InMemorySettingsStore` and
 *   `SilentAudioPlayer` make. Both real hosts pass one.
 * @param audio plays the sounds. Silent by default, which is also what the desktop host
 *   installs — see `AudioPlayer`.
 * @param onQuit what the Quit action does. Nothing, by default: a host that cannot express
 *   quitting (iOS) or does not want to (a preview) is a legitimate host, and the button being inert
 *   is better than `:shared` guessing.
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
            // Play goes straight to the opponents when a character is loaded and to the character
            // list when none is — the original's Continue and Load Game behind one button, chosen
            // by what is actually loaded rather than by asking which of the two the player meant.
            onPlay = {
                onNavigate(if (session.active == null) Screen.PROFILES else Screen.OPPONENTS)
            },
            onProfiles = { onNavigate(Screen.PROFILES) },
            onOptions = { onNavigate(Screen.OPTIONS) },
            onQuit = onQuit,
        )

        Screen.PROFILES -> ProfileListScreen(
            session = session,
            onSelected = { onNavigate(Screen.OPPONENTS) },
            onNew = { onNavigate(Screen.PROFILE_NEW) },
            onBack = { onNavigate(Screen.MENU) },
        )

        Screen.PROFILE_NEW -> ProfileCreateScreen(
            session = session,
            onCreated = { onNavigate(Screen.OPPONENTS) },
            onBack = { onNavigate(Screen.PROFILES) },
        )

        // The two play screens need a character *and* a catalog. A missing one is a state the
        // flow cannot reach — Play only leaves the menu once startup is ready, and the opponent
        // list is only reachable with a character selected — so rendering nothing is the honest
        // answer, rather than half a screen or a placeholder claiming something is wrong.
        Screen.OPPONENTS -> Both(session.active, startup.opponents) { profile, opponents ->
            OpponentScreen(
                profile = profile,
                catalog = opponents,
                hour = clock.localHour(),
                onChallenge = {
                    onChoose(it)
                    onNavigate(Screen.MATCH)
                },
                onBack = { onNavigate(Screen.PROFILES) },
            )
        }

        Screen.MATCH -> Both(session.active, startup.catalog) { profile, catalog ->
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

        Screen.OPTIONS -> settings?.let {
            OptionsScreen(settings = it, onBack = { onNavigate(Screen.MENU) })
        }
    }
}

/**
 * Renders [content] only when both prerequisites are present.
 *
 * A guard rather than a `?.let` chain at each call site: two nullable prerequisites nested by hand
 * is how one of them ends up unchecked.
 */
@Composable
private fun <A : Any, B : Any> Both(first: A?, second: B?, content: @Composable (A, B) -> Unit) {
    if (first != null && second != null) content(first, second)
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
