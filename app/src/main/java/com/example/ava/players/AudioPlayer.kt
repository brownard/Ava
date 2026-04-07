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
     * The title of the currently playing media, or null if unavailable.
     */
    val mediaTitle: StateFlow<String?>

    /**
     * The artist of the currently playing media, or null if unavailable.
     */
    val mediaArtist: StateFlow<String?>

    /**
     * The current playback position in milliseconds, or 0 if not playing.
     */
    val currentPosition: Long

    /**
     * The duration of the currently playing media in milliseconds, or 0 if unknown.
     */
    val duration: Long

    /**
     * Raw artwork image bytes for the currently playing media, or null if unavailable.
     */
    val artworkData: StateFlow<ByteArray?>

    /**
     * URL of the artwork image for the currently playing media, or null if unavailable.
     */
    val artworkUri: StateFlow<String?>

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
     * Skips to the next item in the queue, if any.
     */
    fun skipToNext()

    /**
     * Skips to the previous item in the queue (or restarts current if near the start).
     */
    fun skipToPrevious()

    /**
     * Stops playback and releases all resources.
     */
    fun stop()
}