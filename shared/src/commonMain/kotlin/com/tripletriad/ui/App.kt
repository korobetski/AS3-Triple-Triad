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
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.rememberStrings
import com.tripletriad.settings.InMemorySettingsStore
import com.tripletriad.settings.SettingsStore
import com.tripletriad.settings.UserSettings
import com.tripletriad.settings.UserSettingsRepository
import kotlinx.coroutines.launch

private val Backdrop = Color(0xFF14161C)

/**
 * Which screen is showing.
 *
 * A `remember`ed value and not a navigation library: there are four destinations, no deep links,
 * no back stack worth the name, and no arguments to pass. Compose Navigation would be a dependency
 * and a `NavHost` to earn nothing. If this grows to the original's fourteen screens with a real
 * back stack, that is the point to reconsider — not before.
 */
internal enum class Screen {
    SPLASH,
    MENU,
    OPTIONS,
    MATCH,
}

/**
 * The whole app: splash while it loads, then the menu, then a match or the options.
 *
 * @param store where `UserSettings.json` lives. Supplied by the host, because `:shared` has no
 *   platform file access of its own — see `SettingsStore`. Defaults to an in-memory store so a
 *   preview or a test needs no filesystem, and so that a test can pin the language by handing in
 *   `InMemorySettingsStore("""{"language":"en_US"}""")` rather than inheriting whatever locale the
 *   machine running it happens to be set to.
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

            var screen by remember { mutableStateOf(Screen.SPLASH) }
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
            BackHandler(enabled = screen == Screen.MATCH || screen == Screen.OPTIONS) {
                screen = Screen.MENU
            }

            CompositionLocalProvider(LocalStrings provides strings) {
                // Only a hairline of padding: the board and ten cards want every dp there is.
                Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                    // Crossfade so the splash does not snap to the menu. 220 ms is short enough
                    // not to feel like a wait and long enough to read as a transition.
                    Crossfade(targetState = screen, label = "screen") { destination ->
                        when (destination) {
                            Screen.SPLASH -> SplashScreen(startup)
                            Screen.MENU -> MainMenuScreen(
                                onPlay = { screen = Screen.MATCH },
                                onOptions = { screen = Screen.OPTIONS },
                                onQuit = onQuit,
                            )
                            Screen.OPTIONS -> settings?.let {
                                OptionsScreen(settings = it, onBack = { screen = Screen.MENU })
                            }
                            Screen.MATCH -> startup.catalog?.let { catalog ->
                                // Deliberately provided even when null: a card composes correctly
                                // with no textures at all — flat colour quad, empty layers — so a
                                // failed art load costs appearance, not playability.
                                CompositionLocalProvider(LocalCardArt provides startup.art) {
                                    MatchScreen(
                                        catalog = catalog,
                                        onExit = { screen = Screen.MENU },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
