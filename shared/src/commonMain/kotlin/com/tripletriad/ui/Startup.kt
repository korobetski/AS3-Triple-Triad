package com.tripletriad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import com.tripletriad.data.CampaignCatalog
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.NpcCatalog
import com.tripletriad.data.loadCampaignCatalog
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.data.loadNpcCatalog
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

    /**
     * The nineteen shared card textures — back, digit atlas, rarity rows, type icons — and the
     * interface artwork behind them: the three thumbnail sheets and the bag icons ([UiArt]).
     * Avatars and opponent portraits are not here; they load as a screen asks for one.
     */
    ART(StringKeys.STARTUP_ART),

    /** `npcs.json`: the 85 PvE opponents of both collections, then `campaigns.json`'s thirteen. */
    OPPONENTS(StringKeys.STARTUP_OPPONENTS),

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
 * @property ui the interface artwork, on the same footing as [art]: a screen composes without it,
 *   drawing its fallbacks, so a failed load costs appearance and not use.
 * @property opponents null until [StartupPhase.OPPONENTS] completes. Non-null once [isReady].
 * @property campaigns the tournament ladders, loaded with the opponents and on the same footing:
 *   null until that phase completes, non-null once [isReady].
 */
data class StartupState(
    val phase: StartupPhase = StartupPhase.SETTINGS,
    val settings: UserSettings? = null,
    val catalog: CardCatalog? = null,
    val art: CardArt? = null,
    val ui: UiArt? = null,
    val opponents: NpcCatalog? = null,
    val campaigns: CampaignCatalog? = null,
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
        val ui = loadUiArt()
        value = StartupState(StartupPhase.OPPONENTS, settings, catalog, art, ui)

        val opponents = loadNpcCatalog()
        val campaigns = loadCampaignCatalog()
        value = StartupState(
            phase = StartupPhase.READY,
            settings = settings,
            catalog = catalog,
            art = art,
            ui = ui,
            opponents = opponents,
            campaigns = campaigns,
        )
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
