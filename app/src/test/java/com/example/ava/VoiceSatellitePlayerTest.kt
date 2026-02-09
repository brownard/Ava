package com.example.ava

import com.example.ava.esphome.voicesatellite.VoiceSatellitePlayer
import com.example.ava.stubs.StubAudioPlayer
import com.example.ava.stubs.StubSettingState
import org.junit.Test

class VoiceSatellitePlayerTest {
    @Test
    fun should_set_volume_when_not_muted() {
        val player = VoiceSatellitePlayer(
            ttsPlayer = StubAudioPlayer(),
            mediaPlayer = StubAudioPlayer(),
            enableWakeSound = StubSettingState(true),
            wakeSound = StubSettingState(""),
            timerFinishedSound = StubSettingState(""),
            repeatTimerFinishedSound = StubSettingState(true)
        )

        val volume = 0.5f
        player.setVolume(volume)

        assert(player.ttsPlayer.volume == volume)
        assert(player.mediaPlayer.volume == volume)
    }

    @Test
    fun should_not_set_volume_when_muted() {
        val player = VoiceSatellitePlayer(
            ttsPlayer = StubAudioPlayer(),
            mediaPlayer = StubAudioPlayer(),
            enableWakeSound = StubSettingState(true),
            wakeSound = StubSettingState(""),
            timerFinishedSound = StubSettingState(""),
            repeatTimerFinishedSound = StubSettingState(true)
        )

        val volume = 0.5f
        player.setMuted(true)
        player.setVolume(volume)

        assert(player.ttsPlayer.volume == 0f)
        assert(player.mediaPlayer.volume == 0f)

        player.setMuted(false)

        assert(player.ttsPlayer.volume == volume)
        assert(player.mediaPlayer.volume == volume)
    }

    @Test
    fun should_set_muted() {
        val player = VoiceSatellitePlayer(
            ttsPlayer = StubAudioPlayer(),
            mediaPlayer = StubAudioPlayer(),
            enableWakeSound = StubSettingState(true),
            wakeSound = StubSettingState(""),
            timerFinishedSound = StubSettingState(""),
            repeatTimerFinishedSound = StubSettingState(true)
        )

        player.setMuted(true)

        assert(player.ttsPlayer.volume == 0f)
        assert(player.mediaPlayer.volume == 0f)

        player.setMuted(false)

        assert(player.ttsPlayer.volume == 1f)
        assert(player.mediaPlayer.volume == 1f)
    }

    @Test
    fun should_duck_media_player() {
        val duckMultiplier = 0.5f
        val player = VoiceSatellitePlayer(
            ttsPlayer = StubAudioPlayer(),
            mediaPlayer = StubAudioPlayer(),
            enableWakeSound = StubSettingState(true),
            wakeSound = StubSettingState(""),
            timerFinishedSound = StubSettingState(""),
            duckMultiplier = duckMultiplier,
            repeatTimerFinishedSound = StubSettingState(true)
        )

        player.duck()

        assert(player.ttsPlayer.volume == player.volume.value)
        assert(player.mediaPlayer.volume == player.volume.value * duckMultiplier)

        player.unDuck()

        assert(player.ttsPlayer.volume == player.volume.value)
        assert(player.mediaPlayer.volume == player.volume.value)
    }
}