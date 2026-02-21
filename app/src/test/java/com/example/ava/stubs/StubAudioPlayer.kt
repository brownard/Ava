package com.example.ava.stubs

import com.example.ava.players.AudioPlayer
import com.example.ava.players.AudioPlayerState
import kotlinx.coroutines.flow.MutableStateFlow

open class StubAudioPlayer : AudioPlayer {
    override val state = MutableStateFlow(AudioPlayerState.IDLE)
    override var volume = 1f
    override fun init() {}
    override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
        onCompletion()
    }

    override fun pause() {}
    override fun unpause() {}
    override fun stop() {}
    override fun close() {}
}