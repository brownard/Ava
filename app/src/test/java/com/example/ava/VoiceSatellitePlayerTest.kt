package com.example.ava

import com.example.ava.esphome.voicesatellite.VoiceSatellitePlayerImpl
import com.example.ava.players.AudioPlayer
import com.example.ava.settings.SettingState
import com.example.ava.stubs.StubAudioPlayer
import com.example.ava.stubs.StubSettingState
import org.junit.Test

class VoiceSatellitePlayerTest {
    fun createPlayer(
        ttsPlayer: AudioPlayer = StubAudioPlayer(),
        mediaPlayer: AudioPlayer = StubAudioPlayer(),
        enableWakeSound: SettingState<Boolean> = StubSettingState(true),
        wakeSound: SettingState<String> = StubSettingState(""),
        timerFinishedSound: SettingState<String> = StubSettingState(""),
        repeatTimerFinishedSound: SettingState<Boolean> = StubSettingState(true),
        duckMultiplier: Float = 1f
    ) = VoiceSatellitePlayerImpl(
        ttsPlayer = ttsPlayer,
        mediaPlayer = mediaPlayer,
        enableWakeSound = enableWakeSound,
        wakeSound = wakeSound,
        timerFinishedSound = timerFinishedSound,
        repeatTimerFinishedSound = repeatTimerFinishedSound,
        duckMultiplier = duckMultiplier
    )

    @Test
    fun should_set_volume_when_not_muted() {
        val player = createPlayer()
        val volume = 0.5f

        player.setVolume(volume)

        assert(player.ttsPlayer.volume == volume)
        assert(player.mediaPlayer.volume == volume)
    }

    @Test
    fun should_not_set_volume_when_muted() {
        val player = createPlayer()
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
        val player = createPlayer()

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
        val player = createPlayer(duckMultiplier = duckMultiplier)

        player.duck()

        assert(player.ttsPlayer.volume == player.volume.value)
        assert(player.mediaPlayer.volume == player.volume.value * duckMultiplier)

        player.unDuck()

        assert(player.ttsPlayer.volume == player.volume.value)
        assert(player.mediaPlayer.volume == player.volume.value)
    }
}