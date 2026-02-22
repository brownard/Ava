package com.example.ava.stubs

import com.example.ava.esphome.voicesatellite.VoiceSatellitePlayer
import com.example.ava.players.AudioPlayer
import com.example.ava.settings.SettingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

open class StubVoiceSatellitePlayer(
    override val ttsPlayer: AudioPlayer = StubAudioPlayer(),
    override val mediaPlayer: AudioPlayer = StubAudioPlayer(),
    override val enableWakeSound: SettingState<Boolean> = stubSettingState(true),
    override val wakeSound: SettingState<String> = stubSettingState(""),
    override val timerFinishedSound: SettingState<String> = stubSettingState(""),
    override val repeatTimerFinishedSound: SettingState<Boolean> = stubSettingState(true)
) : VoiceSatellitePlayer {
    protected val _volume = MutableStateFlow(1.0f)
    override val volume: StateFlow<Float> = _volume
    override fun setVolume(value: Float) {
        _volume.value = value
    }

    protected val _muted = MutableStateFlow(false)
    override val muted: StateFlow<Boolean> = _muted
    override fun setMuted(value: Boolean) {
        _muted.value = value
    }

    override fun playAnnouncement(
        preannounceUrl: String,
        mediaUrl: String,
        onCompletion: () -> Unit
    ) {
        onCompletion()
    }

    override suspend fun playWakeSound(onCompletion: () -> Unit) {
        ttsPlayer.play(wakeSound.get(), onCompletion)
    }

    override suspend fun playTimerFinishedSound(onCompletion: () -> Unit) {
        ttsPlayer.play(timerFinishedSound.get(), onCompletion)
    }

    override fun duck() {}

    override fun unDuck() {}

    override fun close() {}
}