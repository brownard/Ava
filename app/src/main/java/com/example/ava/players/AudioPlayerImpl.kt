package com.example.ava.players

import android.media.AudioManager
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementation of AudioPlayer backed by an ExoPlayer.
 */
@UnstableApi
class AudioPlayerImpl(
    private val audioManager: AudioManager,
    val focusGain: Int,
    private val playerBuilder: () -> Player
) : AudioPlayer {
    private var _player: Player? = null
    private var isPlayerInit = false
    private var focusRegistration: AudioFocusRegistration? = null

    private val _state = MutableStateFlow(AudioPlayerState.IDLE)
    override val state = _state.asStateFlow()

    private val isPlaying: Boolean get() = _player?.isPlaying ?: false

    private val isPaused: Boolean
        get() = _player?.let {
            !it.isPlaying && it.playbackState != Player.STATE_IDLE && it.playbackState != Player.STATE_ENDED
        } ?: false

    private var _volume: Float = 1.0f
    override var volume
        get() = _volume
        set(value) {
            _volume = value
            _player?.volume = value
        }

    override fun init() {
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

    override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
        if (!isPlayerInit)
            init()
        // Force recreation of player next time its needed
        isPlayerInit = false
        val player = _player
        check(player != null) { "player not initialized" }

        player.addListener(getPlayerListener(onCompletion))
        runCatching {
            for (mediaUri in mediaUris) {
                if (mediaUri.isNotEmpty()) {
                    player.addMediaItem(MediaItem.fromUri(mediaUri))
                } else Log.w(TAG, "Ignoring empty media uri")
            }
            player.playWhenReady = true
            player.prepare()
        }.onFailure {
            Log.e(TAG, "Error playing media $mediaUris", it)
            onCompletion()
            close()
        }
    }

    override fun pause() {
        if (isPlaying)
            _player?.pause()
    }

    override fun unpause() {
        if (isPaused)
            _player?.play()
    }

    override fun stop() {
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

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying)
                _state.value = AudioPlayerState.PLAYING
            else if (isPaused)
                _state.value = AudioPlayerState.PAUSED
            else
                _state.value = AudioPlayerState.IDLE
        }
    }

    override fun close() {
        isPlayerInit = false
        _player?.release()
        _player = null
        focusRegistration?.close()
        focusRegistration = null
        _state.value = AudioPlayerState.IDLE
    }

    companion object {
        private const val TAG = "AudioPlayer"
    }
}