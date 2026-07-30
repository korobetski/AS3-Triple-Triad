package com.tripletriad.android

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.tripletriad.audio.AudioPlayer
import com.tripletriad.audio.MUSIC_LOOP_START_MS
import com.tripletriad.audio.Sound
import com.tripletriad.log.Log

/**
 * [AudioPlayer] on Android: `SoundPool` for the effects, `MediaPlayer` for the music.
 *
 * ### No Media3, no ExoPlayer
 *
 * The migration plan's Task 1.5 names Media3. It is not needed, and it is a large dependency:
 * `SoundPool` and `MediaPlayer` are platform classes, available since well before `minSdk 24`, and
 * between them they do everything the AS3's `SoundManager` did. Media3 would buy accurate seeking
 * and gapless concatenation; the one seek here is to a fixed point in a file that carries a
 * Xing header, and there is nothing to concatenate. See the
 * [README](../../../../../../../README.md#audio).
 *
 * ### Two engines, because they are for two different jobs
 *
 * `SoundPool` keeps short sounds decoded in memory and can overlap them, which is what an effects
 * channel is — three cards flipping in a combo must not cut each other off. It is the wrong
 * tool for a 64-second track: it would hold the whole thing as PCM (about 11 MB) and cannot
 * loop to a point.
 * `MediaPlayer` streams and seeks, and is the wrong tool for effects: one instance plays one thing,
 * and constructing them per tap is how audio latency happens.
 *
 * That split is also `SoundManager`'s: `NOISE_CHANNEL` against `BACKGROUND_CHANNEL`.
 *
 * ### Loading
 *
 * `SoundPool.load` is asynchronous, so all ten effects are queued in the constructor and a sound
 * asked for before its load completes is **dropped rather than queued**. Dropping is right: the
 * moment a sound belonged to has passed by the time it would arrive, and the window is the first
 * few hundred milliseconds of the app's life, spent on the splash.
 *
 * @param context any context; only `resources` is used, so an application context is fine and
 *   avoids holding an activity.
 */
class AndroidAudioPlayer(context: Context) : AudioPlayer {
    private val resources = context.applicationContext.resources

    private val pool = SoundPool.Builder()
        // Four cards can flip from one placement, plus the combo sound over them.
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    /** `Sound` to `SoundPool` id, filled in as loads complete. */
    private val loaded = mutableMapOf<Sound, Int>()
    private val pending = mutableMapOf<Int, Sound>()

    private var music: MediaPlayer? = null
    private var playingMusic: Sound? = null

    /** True only between [pauseMusic] and [resumeMusic], so a user-initiated stop is not undone. */
    private var pausedByLifecycle = false

    private var backgroundVolume = 1f
    private var noiseVolume = 1f

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            val sound = pending.remove(sampleId)
            if (status == 0 && sound != null) {
                loaded[sound] = sampleId
            } else {
                Log.w(TAG) { "SoundPool failed to load $sound (status $status)" }
            }
        }
        for (sound in Sound.effects) {
            val sampleId = pool.load(resources.openRawResourceFd(rawId(sound)), PRIORITY)
            pending[sampleId] = sound
        }
    }

    override fun play(sound: Sound) {
        if (sound.music) playMusic(sound) else playEffect(sound)
    }

    private fun playEffect(sound: Sound) {
        if (noiseVolume <= 0f) return
        val sampleId = loaded[sound]
        if (sampleId == null) {
            Log.d(TAG) { "$sound is not loaded yet; dropped" }
            return
        }
        pool.play(sampleId, noiseVolume, noiseVolume, PRIORITY, 0, 1f)
    }

    /**
     * Starts the music, or leaves it alone if this track is already the one playing.
     *
     * The guard is `SoundManager.playSound`'s `if (sound !== SoundManager._playingSound)`. What is
     * *not* reproduced is its cross-fade, and deliberately: `fadeSoundChannel` was called with a
     * 150 ms delay and stepped the volume by 0.01, so fading out from 1.0 took a hundred steps —
     * **fifteen seconds**, not 150 ms. It also compared a float to `0` exactly. Reimplementing that
     * faithfully would be reimplementing a defect; there is only one track, so nothing cross-fades.
     */
    private fun playMusic(sound: Sound) {
        if (playingMusic == sound && music?.isPlaying == true) return
        stopMusic()
        val player = runCatching {
            MediaPlayer().apply {
                resources.openRawResourceFd(rawId(sound)).use {
                    setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setVolume(backgroundVolume, backgroundVolume)
                // The loop point. `SoundManager.progressHandler` polled the position every frame
                // and restarted at 16 374 ms once past 64 300 ms, which is the end of a 64.39 s
                // track — so "on completion" is the same behaviour, minus the overshoot.
                setOnCompletionListener {
                    seekTo(MUSIC_LOOP_START_MS)
                    start()
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG) { "MediaPlayer error $what/$extra on $sound" }
                    true
                }
                prepare()
                start()
            }
        }.getOrElse { failure ->
            Log.w(TAG, failure) { "could not start $sound" }
            return
        }
        music = player
        playingMusic = sound
    }

    override fun stopMusic() {
        music?.let { player ->
            runCatching {
                player.stop()
                player.release()
            }.exceptionOrNull()?.let { Log.w(TAG, it) { "releasing the music player" } }
        }
        music = null
        playingMusic = null
        pausedByLifecycle = false
    }

    override fun volumes(background: Float, noise: Float) {
        backgroundVolume = background.coerceIn(0f, 1f)
        noiseVolume = noise.coerceIn(0f, 1f)
        // Effects take the new volume at their next `play`; the music is already running, so it has
        // to be told. A muted track is left running rather than stopped, so that raising the slider
        // resumes where it was instead of restarting the intro.
        music?.let { runCatching { it.setVolume(backgroundVolume, backgroundVolume) } }
    }

    /**
     * Pauses the music, remembering that it was playing. The host calls this from `onStop`.
     *
     * Pause and not [stopMusic]: stopping loses the position, and the composition does not change
     * when the app is backgrounded, so the effect that started the music would not fire again on
     * the way back — the match would come back silent. Paired with [resumeMusic] from `onStart`.
     *
     * Not on the [AudioPlayer] interface: a process lifecycle is an Android idea, and the desktop
     * host has nothing to call this from.
     */
    fun pauseMusic() {
        music?.let { player ->
            if (player.isPlaying) {
                runCatching { player.pause() }
                    .onSuccess { pausedByLifecycle = true }
                    .onFailure { Log.w(TAG, it) { "pausing the music" } }
            }
        }
    }

    /** Resumes what [pauseMusic] paused, and nothing otherwise. */
    fun resumeMusic() {
        if (!pausedByLifecycle) return
        pausedByLifecycle = false
        music?.let { player ->
            runCatching { player.start() }.exceptionOrNull()
                ?.let { Log.w(TAG, it) { "resuming the music" } }
        }
    }

    /** Releases both engines. The host calls this from `onDestroy`. */
    fun release() {
        stopMusic()
        pool.release()
        loaded.clear()
        pending.clear()
    }

    private companion object {
        const val TAG = "Audio"
        const val MAX_STREAMS = 6
        const val PRIORITY = 1

        /**
         * `Sound` to `R.raw`, written out rather than looked up by name.
         *
         * `Resources.getIdentifier` would do this in one line and is the wrong choice twice over:
         * a rename becomes a runtime failure instead of a compile error, and R8 resource shrinking
         * cannot see a resource that is only named in a string. Written by
         * [`tools/import_sounds.py`](../../../../../../../tools/import_sounds.py)'s naming rule —
         * the AS3 id with its dots turned into underscores.
         */
        fun rawId(sound: Sound): Int = when (sound) {
            Sound.MATCH_MUSIC -> R.raw.shuffle_or_boogie
            Sound.MATCH_OPEN -> R.raw.se_ttriad_scd_2
            Sound.CARD_PLACED -> R.raw.se_ttriad_scd_1
            Sound.CARD_CAPTURED -> R.raw.se_ttriad_scd_157
            Sound.COMBO -> R.raw.se_ttriad_scd_15
            Sound.TURN_CHANGE -> R.raw.se_ttriad_scd_4
            Sound.BLUE_WINS -> R.raw.se_ttriad_scd_7
            Sound.RED_WINS -> R.raw.se_ttriad_scd_8
            Sound.UI_CLICK -> R.raw.se_ui_scd_72
            Sound.NEW_MATCH -> R.raw.se_gs_scd_162
        }
    }
}
