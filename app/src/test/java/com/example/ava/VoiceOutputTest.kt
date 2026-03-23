package com.example.ava

import com.example.ava.esphome.voiceassistant.VoiceOutputImpl
import com.example.ava.players.AudioPlayer
import com.example.ava.stubs.StubAudioPlayer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class VoiceOutputTest {
    fun createVoiceOutput(
        ttsPlayer: AudioPlayer = StubAudioPlayer(),
        mediaPlayer: AudioPlayer = StubAudioPlayer(),
        volume: Float = 1f,
        muted: Boolean = false,
        duckMultiplier: Float = 1f
    ) = VoiceOutputImpl(
        ttsPlayer = ttsPlayer,
        mediaPlayer = mediaPlayer,
        enableWakeSound = { false },
        wakeSound = { "" },
        timerFinishedSound = { "" },
        repeatTimerFinishedSound = { true },
        enableErrorSound = { true },
        errorSound = { "" },
        volume = volume,
        muted = muted,
        duckMultiplier = duckMultiplier
    )

    @Test
    fun should_set_initial_volume() {
        val ttsPlayer = StubAudioPlayer()
        val mediaPlayer = StubAudioPlayer()
        val volume = 0.5f
        createVoiceOutput(
            ttsPlayer = ttsPlayer,
            mediaPlayer = mediaPlayer,
            volume = volume
        )

        assert(ttsPlayer.volume == volume)
        assert(mediaPlayer.volume == volume)
    }

    @Test
    fun should_set_initial_muted_state() {
        val ttsPlayer = StubAudioPlayer()
        val mediaPlayer = StubAudioPlayer()
        val muted = true
        createVoiceOutput(
            ttsPlayer = ttsPlayer,
            mediaPlayer = mediaPlayer,
            muted = muted
        )

        assert(ttsPlayer.volume == 0f)
        assert(mediaPlayer.volume == 0f)
    }

    @Test
    fun should_set_volume_when_not_muted() = runTest {
        val ttsPlayer = StubAudioPlayer()
        val mediaPlayer = StubAudioPlayer()
        val voiceOutput = createVoiceOutput(
            ttsPlayer = ttsPlayer,
            mediaPlayer = mediaPlayer
        )
        val volume = 0.5f

        voiceOutput.setVolume(volume)

        assert(ttsPlayer.volume == volume)
        assert(mediaPlayer.volume == volume)
    }

    @Test
    fun should_not_set_volume_when_muted() = runTest {
        val ttsPlayer = StubAudioPlayer()
        val mediaPlayer = StubAudioPlayer()
        val voiceOutput = createVoiceOutput(
            ttsPlayer = ttsPlayer,
            mediaPlayer = mediaPlayer
        )
        val volume = 0.5f

        voiceOutput.setMuted(true)
        voiceOutput.setVolume(volume)

        assert(ttsPlayer.volume == 0f)
        assert(mediaPlayer.volume == 0f)

        voiceOutput.setMuted(false)

        assert(ttsPlayer.volume == volume)
        assert(mediaPlayer.volume == volume)
    }

    @Test
    fun should_set_muted() = runTest {
        val ttsPlayer = StubAudioPlayer()
        val mediaPlayer = StubAudioPlayer()
        val voiceOutput = createVoiceOutput(
            ttsPlayer = ttsPlayer,
            mediaPlayer = mediaPlayer
        )

        voiceOutput.setMuted(true)

        assert(ttsPlayer.volume == 0f)
        assert(mediaPlayer.volume == 0f)

        voiceOutput.setMuted(false)

        assert(ttsPlayer.volume == 1f)
        assert(mediaPlayer.volume == 1f)
    }

    @Test
    fun should_duck_media_player() {
        val ttsPlayer = StubAudioPlayer()
        val mediaPlayer = StubAudioPlayer()
        val duckMultiplier = 0.5f
        val voiceOutput = createVoiceOutput(
            ttsPlayer = ttsPlayer,
            mediaPlayer = mediaPlayer,
            duckMultiplier = duckMultiplier
        )

        voiceOutput.duck()

        assert(ttsPlayer.volume == voiceOutput.volume.value)
        assert(mediaPlayer.volume == voiceOutput.volume.value * duckMultiplier)

        voiceOutput.unDuck()

        assert(ttsPlayer.volume == voiceOutput.volume.value)
        assert(mediaPlayer.volume == voiceOutput.volume.value)
    }

    @Test
    fun should_not_play_empty_preannounce_sound() = runTest {
        val playedUrls = mutableListOf<String>()
        val voiceOutput = createVoiceOutput(
            ttsPlayer = object : StubAudioPlayer() {
                override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                    playedUrls.addAll(mediaUris)
                }
            }
        )
        voiceOutput.playAnnouncement(preannounceUrl = "", mediaUrl = "media")
        assertEquals(listOf("media"), playedUrls)
    }
}