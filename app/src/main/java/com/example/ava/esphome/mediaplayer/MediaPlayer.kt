package com.example.ava.esphome.mediaplayer

import com.example.esphomeproto.api.MediaPlayerState
import kotlinx.coroutines.flow.StateFlow

interface MediaPlayer {
    /**
     * Called when audio focus is requested and all other playback should be ducked,
     * e.g. when microphone input is being captured and/or playback on this player is about to begin.
     * Implementations should not rely on this method being called before playback.
     */
    fun requestFocus()

    /**
     * The current state of media playback.
     */
    val state: StateFlow<MediaPlayerState>

    /**
     * Starts playback of the specified media.
     * Convenience method for calling [play] for a single url.
     */
    fun play(mediaUrl: String, onCompletion: () -> Unit = {}) =
        play(listOf(mediaUrl), onCompletion)

    /**
     * Starts playback of the specified media.
     */
    fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit = {})

    /**
     * Sets the paused state of media playback.
     */
    fun setPaused(paused: Boolean)

    /**
     * Stops media playback.
     */
    fun stop()

    /**
     * Gets or sets the playback volume.
     */
    var volume: Float

    /**
     * Gets or sets whether playback is muted.
     */
    var muted: Boolean
}