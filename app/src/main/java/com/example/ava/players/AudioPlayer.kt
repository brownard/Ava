package com.example.ava.players

import android.media.AudioManager
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

@UnstableApi
class AudioPlayer(
    private val audioManager: AudioManager,
    val focusGain: Int,
    private val playerBuilder: () -> Player
) : AutoCloseable {
    private var _player: Player? = null
    private var isPlayerInit = false
    private var focusRegistration: AudioFocusRegistration? = null

    val isPlaying: Boolean get() = _player?.isPlaying ?: false
    val isPaused: Boolean
        get() = _player?.let {
            !it.isPlaying && it.playbackState != Player.STATE_IDLE && it.playbackState != Player.STATE_ENDED
        } ?: false
    val isStopped
        get() = _player?.let { it.playbackState == Player.STATE_IDLE || it.playbackState == Player.STATE_ENDED }
            ?: true

    private var _volume: Float = 1.0f
    var volume
        get() = _volume
        set(value) {
            _volume = value
            _player?.volume = value
        }

    fun init() {
        close()
        _player = playerBuilder().apply {
            volume = _volume
        }

        focusRegistration = AudioFocusRegistration.request(
            audioManager = audioManager,
            audioAttributes = _player!!.audioAttributes,
            focusGain = focusGain
        )
        isPlayerInit = true
    }

    fun play(mediaUri: String, onCompletion: () -> Unit) {
        play(listOf(mediaUri), onCompletion)
    }

    fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
        if (!isPlayerInit)
            init()
        // Force recreation of player next time its needed
        isPlayerInit = false
        val player = _player
        check(player != null) { "player not initialized" }

        player.addListener(getPlayerListener(onCompletion))
        runCatching {
            for (mediaUri in mediaUris) {
                player.addMediaItem(MediaItem.fromUri(mediaUri))
            }
            player.playWhenReady = true
            player.prepare()
        }.onFailure {
            Log.e(TAG, "Error playing media $mediaUris", it)
            onCompletion()
            close()
        }
    }

    fun pause() {
        if (isPlaying)
            _player?.pause()
    }

    fun unpause() {
        if (isPaused)
            _player?.play()
    }

    fun stop() {
        close()
    }

    private fun getPlayerListener(onCompletion: () -> Unit) = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d(TAG, "Playback state changed to $playbackState")
            // If there's a playback error then the player state will return to idle
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                onCompletion()
                close()
            }
        }
    }

    override fun close() {
        isPlayerInit = false
        _player?.release()
        _player = null
        focusRegistration?.close()
        focusRegistration = null
    }

    companion object {
        private const val TAG = "AudioPlayer"
    }
}