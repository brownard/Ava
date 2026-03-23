package com.example.ava.stubs

import com.example.ava.esphome.voiceassistant.VoiceOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

open class StubVoiceOutput(
    val wakeSound: String = "",
    val timerFinishedSound: String = "",
    val errorSound: String = "",
    val repeatTimerFinishedSound: Boolean = true
) : VoiceOutput {
    protected val _volume = MutableStateFlow(1.0f)
    override val volume: StateFlow<Float> = _volume
    override suspend fun setVolume(value: Float) {
        _volume.value = value
    }

    protected val _muted = MutableStateFlow(false)
    override val muted: StateFlow<Boolean> = _muted
    override suspend fun setMuted(value: Boolean) {
        _muted.value = value
    }

    open fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) = onCompletion()

    override fun playTTS(ttsUrl: String, onCompletion: () -> Unit) =
        play(listOf(ttsUrl), onCompletion)

    override fun playAnnouncement(
        preannounceUrl: String,
        mediaUrl: String,
        onCompletion: () -> Unit
    ) = play(listOf(preannounceUrl, mediaUrl), onCompletion)

    override suspend fun playWakeSound(onCompletion: () -> Unit) =
        play(listOf(wakeSound), onCompletion)

    override suspend fun playTimerFinishedSound(
        onCompletion: (repeat: Boolean) -> Unit
    ) = play(listOf(timerFinishedSound)) {
        onCompletion(repeatTimerFinishedSound)
    }

    override suspend fun playErrorSound(onCompletion: () -> Unit) =
        play(listOf(errorSound), onCompletion)

    override fun duck() {}

    override fun unDuck() {}

    override fun stopTTS() {}

    override fun close() {}
}