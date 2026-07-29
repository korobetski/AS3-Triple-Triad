package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.rememberDeviceLocale
import com.tripletriad.settings.SettingsStore
import com.tripletriad.settings.UserSettings
import com.tripletriad.settings.UserSettingsRepository

/**
 * What the splash is waiting for, in the order it waits.
 *
 * Declared as an ordered enum rather than a boolean pair so the splash can *say* what it is doing
 * and show honest progress. That matters for what comes next: an update check has to run before
 * assets load, can be slow, and can fail — a spinner with no phase would have nowhere to put it.
 * Adding it is one entry here, one branch in [rememberStartup] and one string.
 *
 * @property labelKey the line shown under the logo while this phase runs.
 */
enum class StartupPhase(val labelKey: String) {
    /** Read `UserSettings.json`, which decides the language everything after this is shown in. */
    SETTINGS(StringKeys.STARTUP_SETTINGS),

    /** `cards.json`: 263 records, ~60 KB. */
    CARDS(StringKeys.LOADING_CARDS),

    /** The nineteen shared textures — card back, digit atlas, rarity rows, type icons. */
    ART(StringKeys.STARTUP_ART),

    /** Nothing left to wait for. Terminal. */
    READY(StringKeys.STARTUP_READY),
    ;

    /** 0f on the first phase, 1f on [READY]. */
    val progress: Float get() = ordinal / (entries.size - 1f)
}

/**
 * Everything the app needs before it can show a menu, and how far along it is.
 *
 * @property settings null until [StartupPhase.SETTINGS] completes.
 * @property catalog null until [StartupPhase.CARDS] completes. Non-null once [isReady].
 * @property art may be null even when [isReady] — see [rememberStartup].
 */
data class StartupState(
    val phase: StartupPhase = StartupPhase.SETTINGS,
    val settings: UserSettings? = null,
    val catalog: CardCatalog? = null,
    val art: CardArt? = null,
) {
    val isReady: Boolean get() = phase == StartupPhase.READY
}

/**
 * Runs the startup sequence once and republishes as each phase completes.
 *
 * **Sequential, unlike what this replaced.** The two loads used to run as concurrent
 * `produceState`s with the match gated on neither: cards appeared, then artwork popped in over
 * them. That was the right call with no splash — the board was usable a fraction of a second
 * sooner. With a splash it is the wrong one, because the pop-in happens *in front of the user*
 * instead of behind a progress line, and because an update check will have to be strictly ordered
 * anyway.
 *
 * What has *not* changed: [CardArt] is still nullable everywhere downstream and a card still
 * composes without it. So if artwork ever fails to load, the match is reached and playable, drawn
 * as flat coloured quads. The splash waits for it; the app does not depend on it.
 */
@Composable
fun rememberStartup(store: SettingsStore): StartupState {
    // Read here rather than inside the producer: `Locale.current` is a composition-local read and
    // the producer's body is a coroutine, not a composable.
    val device = rememberDeviceLocale()
    val state by produceState(StartupState(), store, device) {
        val settings = UserSettingsRepository(store).load(device)
        value = StartupState(StartupPhase.CARDS, settings)

        val catalog = loadCardCatalog()
        value = StartupState(StartupPhase.ART, settings, catalog)

        val art = loadCardArt()
        value = StartupState(StartupPhase.READY, settings, catalog, art)
    }
    return state
}

/**
 * Holds the loaded settings and writes every change back.
 *
 * The options screen mutates this and the whole tree recomposes, which is what makes changing the
 * language take effect on the spot rather than on the next launch. Saving is fire-and-forget: the
 * new value is already on screen, and [UserSettingsRepository.save] logs its own failures.
 */
class SettingsHolder internal constructor(
    initial: UserSettings,
    private val save: (UserSettings) -> Unit,
) {
    var value: UserSettings by mutableStateOf(initial)
        private set

    /** Applies [transform], normalises it, shows it, and persists it. */
    fun update(transform: (UserSettings) -> UserSettings) {
        val next = transform(value).sane()
        if (next != value) {
            value = next
            save(next)
        }
    }
}
