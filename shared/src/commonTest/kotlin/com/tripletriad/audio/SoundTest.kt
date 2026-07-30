package com.tripletriad.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [Sound] and the two players that need no platform.
 *
 * What is *not* covered, anywhere: `AndroidAudioPlayer`. Nothing in a unit test can hear a sound,
 * `SoundPool` and `MediaPlayer` are unavailable off-device, and a mock of them would assert that
 * the mock was called. It was verified by hand on a device instead, and the README says so.
 */
class SoundTest {
    @Test
    fun exactlyOneSoundIsMusicAndTheRestAreEffects() {
        val music = Sound.entries.filter { it.music }

        assertEquals(listOf(Sound.MATCH_MUSIC), music, "the AS3 has one background track")
        assertEquals(Sound.MATCH_MUSIC, Sound.Music)
        assertEquals(Sound.entries.size - 1, Sound.effects.size)
        assertTrue(Sound.effects.none { it.music })
    }

    /** The ids are AS3 asset keys; a typo here plays nothing at all, quietly. */
    @Test
    fun everySoundNamesADistinctAs3Id() {
        val files = Sound.entries.map { it.file }

        assertEquals(files.size, files.toSet().size, "two members share a file: $files")
        assertTrue(
            files.all { it.isNotBlank() && !it.contains(".mp3") },
            "the file is a stem, without extension: $files",
        )
    }

    /** `SoundManager.progressHandler` restarted the track at this position. */
    @Test
    fun theMusicLoopsAfterTheIntro() {
        assertEquals(16_374, MUSIC_LOOP_START_MS)
        // The track is 64.39 s, so a loop point past it would mean the music never repeated.
        assertTrue(MUSIC_LOOP_START_MS < 64_390)
    }

    @Test
    fun theSilentPlayerAcceptsEverythingAndDoesNothing() {
        for (sound in Sound.entries) SilentAudioPlayer.play(sound)
        SilentAudioPlayer.stopMusic()
        SilentAudioPlayer.volumes(0.5f, 0.5f)
    }

    @Test
    fun theRecordingPlayerKeepsOrderAndCounts() {
        val audio = RecordingAudioPlayer()

        audio.play(Sound.CARD_PLACED)
        audio.play(Sound.TURN_CHANGE)
        audio.stopMusic()
        audio.volumes(0.25f, 0.75f)

        assertEquals(listOf(Sound.CARD_PLACED, Sound.TURN_CHANGE), audio.played)
        assertTrue(Sound.CARD_PLACED in audio)
        assertTrue(Sound.COMBO !in audio)
        assertEquals(1, audio.musicStops)
        assertEquals(0.25f to 0.75f, audio.volumes)

        audio.clear()
        assertTrue(audio.played.isEmpty())
        assertEquals(0, audio.musicStops)
    }
}
