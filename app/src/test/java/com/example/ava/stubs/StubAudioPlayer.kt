package com.example.ava.stubs

import com.example.ava.players.AudioPlayer
import com.example.ava.players.AudioPlayerState
import kotlinx.coroutines.flow.MutableStateFlow

open class StubAudioPlayer : AudioPlayer {
    override val state = MutableStateFlow(AudioPlayerState.IDLE)
    override val mediaTitle = MutableStateFlow<String?>(null)
    override val mediaArtist = MutableStateFlow<String?>(null)
    override val artworkData = MutableStateFlow<ByteArray?>(null)
    override val artworkUri = MutableStateFlow<String?>(null)
    override val currentPosition: Long = 0L
    override val duration: Long = 0L
    override var volume = 1f
    override fun init() {}
    override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
        onCompletion()
    }

    override fun pause() {}
    override fun unpause() {}
    override fun skipToNext() {}
    override fun skipToPrevious() {}
    override fun stop() {}
    override fun close() {}
}