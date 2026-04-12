package com.example.ava

import com.example.ava.esphome.mediaplayer.MediaPlayer
import com.example.ava.esphome.voiceassistant.VoiceOutputImpl
import com.example.ava.stubs.StubMediaPlayer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceOutputTest {
    fun createVoiceOutput(
        ttsPlayer: MediaPlayer = StubMediaPlayer(),
        mediaPlayer: MediaPlayer = StubMediaPlayer(),
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
        val ttsPlayer = StubMediaPlayer()
        val mediaPlayer = StubMediaPlayer()
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
        val ttsPlayer = StubMediaPlayer()
        val mediaPlayer = StubMediaPlayer()
        val muted = true
        createVoiceOutput(
            ttsPlayer = ttsPlayer,
            mediaPlayer = mediaPlayer,
            muted = muted
        )

        assert(ttsPlayer.muted)
        assert(mediaPlayer.muted)
    }

    @Test
    fun should_set_volume() = runTest {
        val ttsPlayer = StubMediaPlayer()
        val mediaPlayer = StubMediaPlayer()
        val voiceOutput = createVoiceOutput(
            ttsPlayer = ttsPlayer,
            mediaPlayer = mediaPlayer
        )
        val volume = 0.5f

        voiceOutput.volume = volume

        assert(ttsPlayer.volume == volume)
        assert(mediaPlayer.volume == volume)
    }

    @Test
    fun should_set_muted() = runTest {
        val ttsPlayer = StubMediaPlayer()
        val mediaPlayer = StubMediaPlayer()
        val voiceOutput = createVoiceOutput(
            ttsPlayer = ttsPlayer,
            mediaPlayer = mediaPlayer
        )

        voiceOutput.muted = true

        assert(ttsPlayer.muted)
        assert(mediaPlayer.muted)
    }

    @Test
    fun should_duck_media_player() {
        val ttsPlayer = StubMediaPlayer()
        val mediaPlayer = StubMediaPlayer()
        val volume = 0.8f
        val duckMultiplier = 0.5f
        val voiceOutput = createVoiceOutput(
            ttsPlayer = ttsPlayer,
            mediaPlayer = mediaPlayer,
            volume = volume,
            duckMultiplier = duckMultiplier
        )

        voiceOutput.duck()

        assert(ttsPlayer.volume == volume)
        assert(mediaPlayer.volume == volume * duckMultiplier)

        voiceOutput.unDuck()

        assert(ttsPlayer.volume == volume)
        assert(mediaPlayer.volume == volume)
    }

    @Test
    fun should_not_play_empty_preannounce_sound() = runTest {
        val playedUrls = mutableListOf<String>()
        val voiceOutput = createVoiceOutput(
            ttsPlayer = object : StubMediaPlayer() {
                override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
                    playedUrls.addAll(mediaUris)
                }
            }
        )
        voiceOutput.playAnnouncement(preannounceUrl = "", mediaUrl = "media")
        assertEquals(listOf("media"), playedUrls)
    }
}