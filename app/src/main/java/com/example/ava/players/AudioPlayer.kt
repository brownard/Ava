package com.example.ava.players

import kotlinx.coroutines.flow.StateFlow

enum class AudioPlayerState {
    PLAYING, PAUSED, IDLE
}

/**
 * Interface for an audio player that can play audio from a url.
 */
interface AudioPlayer : AutoCloseable {
    /**
     * The current state of the player.
     */
    val state: StateFlow<AudioPlayerState>

    /**
     * Gets or sets the playback volume.
     */
    var volume: Float

    /**
     * Gains system audio focus and prepares the player for playback.
     */
    fun init()

    /**
     * Plays the specified media and fires the onCompletion callback when playback has finished.
     * This is a convenience method for calling play(listOf(mediaUri), onCompletion) for a single url.
     */
    fun play(mediaUri: String, onCompletion: () -> Unit = {}) {
        play(listOf(mediaUri), onCompletion)
    }

    /**
     * Plays the specified media and fires the onCompletion callback when playback has finished.
     */
    fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit = {})

    /**
     * Pauses playback if currently playing.
     */
    fun pause()

    /**
     * Unpauses playback if currently paused.
     */
    fun unpause()

    /**
     * Stops playback and releases all resources.
     */
    fun stop()
}