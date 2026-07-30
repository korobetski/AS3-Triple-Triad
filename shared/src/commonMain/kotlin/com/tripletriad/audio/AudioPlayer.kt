package com.tripletriad.audio

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Every sound this port plays, and the AS3 id it plays.
 *
 * The ids are `Assets.manager.getSound(...)` keys, i.e. the file stems under
 * `sources/bin/sounds/`. `SoundManager.playSound(id, isNoise)` took the id as a string from
 * fourteen scattered call sites; naming them once here means a typo cannot silently play nothing,
 * and [SoundCatalogTest] can assert every one of them is actually in the bundle.
 *
 * **Ten of the original's twenty-two sounds are here.** The rest are accounted for in the
 * [README](../../../../../../../README.md#audio): ten are referenced by no call site at all in the
 * AS3 source (including `flip`, whose two calls are commented out in favour of
 * `se_ttriad.scd_157`), and two — `card` and `se_ttriad.scd_156` — belong to the coin flip
 * (`anims/PileOuFace.as`), which this port has not implemented. Shipping a sound nothing can play
 * would be dead weight in the APK.
 *
 * @property file the file stem, without extension. The files are MP3, unconverted — see the README
 *   for why no format change was needed.
 * @property music true for the one long track, which loops and is mixed on its own channel.
 *   `SoundManager` split exactly this way: `BACKGROUND_CHANNEL` against `NOISE_CHANNEL`, and
 *   `playSound(id, isNoise = true)` at every effect call site.
 */
enum class Sound(val file: String, val music: Boolean = false) {
    /** `BaseMatchScreen.as:114` — `if (!SoundManager._isPlaying) SoundManager.shuffleLoop()`. */
    MATCH_MUSIC("shuffle_or_boogie", music = true),

    /** `BaseMatchScreen.as:157`, `openPhase()` — the hands are dealt. */
    MATCH_OPEN("se_ttriad.scd_2"),

    /** `TTOCore.as:87` — a card placed that captures nothing. */
    CARD_PLACED("se_ttriad.scd_1"),

    /** `Card.as:229` and `:251`, in `flipTo` — one card changes hands. */
    CARD_CAPTURED("se_ttriad.scd_157"),

    /** `TTOCore.as:125`, on `flipData.waveEffect` — a combo propagates. */
    COMBO("se_ttriad.scd_15"),

    /** `BaseMatchScreen.as:374` and `:382` — either side's turn begins. */
    TURN_CHANGE("se_ttriad.scd_4"),

    /** `PVEMatchScreen.as:95` — blue wins. Blue is the local player. */
    BLUE_WINS("se_ttriad.scd_7"),

    /** `PVEMatchScreen.as:139` — red wins. */
    RED_WINS("se_ttriad.scd_8"),

    /** `TouchLabel.as:31` — any tap on a control. */
    UI_CLICK("se_ui.scd_72"),

    /** `RematchPanel.as:36` — the original played this when the rematch panel appeared. */
    NEW_MATCH("se_gs.scd_162"),
    ;

    companion object {
        /** The one music track, so callers do not have to know which member it is. */
        val Music: Sound = MATCH_MUSIC

        val effects: List<Sound> get() = entries.filterNot { it.music }
    }
}

/**
 * Where the music restarts when it reaches the end, in milliseconds.
 *
 * `SoundManager.progressHandler` polled the channel every frame and, past 64 300 ms, restarted the
 * sound at 16 374 ms — so the first 16 seconds are an intro played once and the rest loops. The
 * track is 64.39 s long, which is why the threshold is effectively "the end".
 *
 * Reproducing it on a completion callback rather than a polled position is both simpler and
 * tighter: the original could overshoot by up to a frame.
 */
const val MUSIC_LOOP_START_MS: Int = 16_374

/**
 * Plays sounds, if the host can.
 *
 * ### Why an interface, again
 *
 * Same reasoning as `SettingsStore`, and more forcefully: there is no multiplatform audio API at
 * all. Compose Multiplatform has none, Android wants `SoundPool`/`MediaPlayer`, the JVM's
 * `javax.sound.sampled` cannot decode MP3 without a third-party library, and iOS wants
 * `AVAudioPlayer`. An `expect`/`actual` would oblige an `actual` for three iOS targets that cannot
 * be compiled from a Windows host. So `:shared` names the sounds and the moments; each host plays
 * them or does not.
 *
 * ### Volumes
 *
 * Both are 0..1, straight from `UserSettings`, which is straight from the AS3's
 * `SoundTransform.volume`. Implementations are told about changes rather than reading settings
 * themselves, so nothing below this interface knows what a `UserSettings` is.
 */
interface AudioPlayer {
    /**
     * Starts [sound].
     *
     * Effects overlap; a second effect does not cut off the first. Music does not: asking for the
     * music while the same track is already playing is a no-op, which is `SoundManager`'s
     * `if (sound !== _playingSound)` guard.
     */
    fun play(sound: Sound)

    /** Stops the music. Effects already in flight are left to finish. */
    fun stopMusic()

    /** Applies new volumes, taking effect on the music immediately. */
    fun volumes(background: Float, noise: Float)
}

/**
 * An [AudioPlayer] that plays nothing.
 *
 * The default, and what the desktop host installs. Not a stub to be replaced: on desktop it is the
 * honest implementation, because the JVM cannot decode these MP3s without adding a decoder library
 * to a host that exists only so the UI can be run without an emulator.
 */
object SilentAudioPlayer : AudioPlayer {
    override fun play(sound: Sound) = Unit

    override fun stopMusic() = Unit

    override fun volumes(background: Float, noise: Float) = Unit
}

/**
 * Records what it was asked to play, for tests.
 *
 * This is how the UI's audio is testable at all: nothing can assert that a sound was *audible*, but
 * a test can assert that placing a card asked for [Sound.CARD_PLACED] and not for
 * [Sound.CARD_CAPTURED]. The mapping from game events to sounds is the part with decisions in it.
 */
class RecordingAudioPlayer : AudioPlayer {
    private val calls = mutableListOf<Sound>()

    val played: List<Sound> get() = calls.toList()

    var musicStops: Int = 0
        private set

    var volumes: Pair<Float, Float>? = null
        private set

    override fun play(sound: Sound) {
        calls += sound
    }

    override fun stopMusic() {
        musicStops++
    }

    override fun volumes(background: Float, noise: Float) {
        volumes = background to noise
    }

    fun clear() {
        calls.clear()
        musicStops = 0
    }

    /** True if [sound] was asked for at least once. */
    operator fun contains(sound: Sound): Boolean = sound in calls
}

/**
 * The player the UI reaches for.
 *
 * `staticCompositionLocalOf` rather than `compositionLocalOf`: the player is installed once by the
 * host and never swapped, so nothing should recompose on its account.
 */
val LocalAudio = staticCompositionLocalOf<AudioPlayer> { SilentAudioPlayer }
