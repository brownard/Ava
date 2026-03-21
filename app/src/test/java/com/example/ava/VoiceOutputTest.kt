package com.example.ava

import com.example.ava.esphome.voicesatellite.VoiceOutputImpl
import com.example.ava.players.AudioPlayer
import com.example.ava.settings.SettingState
import com.example.ava.stubs.StubAudioPlayer
import com.example.ava.stubs.stubSettingState
import org.junit.Test

class VoiceOutputTest {
    fun createVoiceOutput(
        ttsPlayer: AudioPlayer = StubAudioPlayer(),
        mediaPlayer: AudioPlayer = StubAudioPlayer(),
        enableWakeSound: SettingState<Boolean> = stubSettingState(true),
        wakeSound: SettingState<String> = stubSettingState(""),
        timerFinishedSound: SettingState<String> = stubSettingState(""),
        repeatTimerFinishedSound: SettingState<Boolean> = stubSettingState(true),
        enableErrorSound: SettingState<Boolean> = stubSettingState(false),
        errorSound: SettingState<String> = stubSettingState(""),
        duckMultiplier: Float = 1f
    ) = VoiceOutputImpl(
        ttsPlayer = ttsPlayer,
        mediaPlayer = mediaPlayer,
        enableWakeSound = enableWakeSound,
        wakeSound = wakeSound,
        timerFinishedSound = timerFinishedSound,
        repeatTimerFinishedSound = repeatTimerFinishedSound,
        enableErrorSound = enableErrorSound,
        errorSound = errorSound,
        duckMultiplier = duckMultiplier
    )

    @Test
    fun should_set_volume_when_not_muted() {
        val voiceOutput = createVoiceOutput()
        val volume = 0.5f

        voiceOutput.setVolume(volume)

        assert(voiceOutput.ttsPlayer.volume == volume)
        assert(voiceOutput.mediaPlayer.volume == volume)
    }

    @Test
    fun should_not_set_volume_when_muted() {
        val voiceOutput = createVoiceOutput()
        val volume = 0.5f

        voiceOutput.setMuted(true)
        voiceOutput.setVolume(volume)

        assert(voiceOutput.ttsPlayer.volume == 0f)
        assert(voiceOutput.mediaPlayer.volume == 0f)

        voiceOutput.setMuted(false)

        assert(voiceOutput.ttsPlayer.volume == volume)
        assert(voiceOutput.mediaPlayer.volume == volume)
    }

    @Test
    fun should_set_muted() {
        val voiceOutput = createVoiceOutput()

        voiceOutput.setMuted(true)

        assert(voiceOutput.ttsPlayer.volume == 0f)
        assert(voiceOutput.mediaPlayer.volume == 0f)

        voiceOutput.setMuted(false)

        assert(voiceOutput.ttsPlayer.volume == 1f)
        assert(voiceOutput.mediaPlayer.volume == 1f)
    }

    @Test
    fun should_duck_media_player() {
        val duckMultiplier = 0.5f
        val voiceOutput = createVoiceOutput(duckMultiplier = duckMultiplier)

        voiceOutput.duck()

        assert(voiceOutput.ttsPlayer.volume == voiceOutput.volume.value)
        assert(voiceOutput.mediaPlayer.volume == voiceOutput.volume.value * duckMultiplier)

        voiceOutput.unDuck()

        assert(voiceOutput.ttsPlayer.volume == voiceOutput.volume.value)
        assert(voiceOutput.mediaPlayer.volume == voiceOutput.volume.value)
    }
}