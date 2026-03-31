package com.example.ava.stubs

import com.example.ava.esphome.voiceassistant.VoiceOutput

open class StubVoiceOutput(
    val wakeSound: String = "",
    val timerFinishedSound: String = "",
    val errorSound: String = "",
    val repeatTimerFinishedSound: Boolean = true
) : VoiceOutput {
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