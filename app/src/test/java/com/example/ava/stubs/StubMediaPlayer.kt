package com.example.ava.stubs

import com.example.ava.esphome.mediaplayer.MediaPlayer
import com.example.esphomeproto.api.MediaPlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

open class StubMediaPlayer(
    override val state: StateFlow<MediaPlayerState> = MutableStateFlow(MediaPlayerState.MEDIA_PLAYER_STATE_IDLE),
    override var volume: Float = 1f,
    override var muted: Boolean = false
) : MediaPlayer {
    override fun requestFocus() {}
    override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
        onCompletion()
    }

    override fun setPaused(paused: Boolean) {}
    override fun stop() {}
}