package com.example.ava.esphome.android.mediaplayer

import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.ava.esphome.mediaplayer.MediaPlayer
import com.example.esphomeproto.api.MediaPlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

@OptIn(UnstableApi::class)
fun Context.media3MediaPlayer(usage: Int, contentType: Int, focusGain: Int): MediaPlayer {
    val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
    return Media3MediaPlayer(audioManager, focusGain) {
        ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(contentType)
                    .build(),
                false
            ).build()
    }
}

@UnstableApi
class Media3MediaPlayer(
    private val audioManager: AudioManager,
    val focusGain: Int,
    private val playerBuilder: () -> Player
) : MediaPlayer, AutoCloseable {
    private var _player: Player? = null
    private var isPlayerInit = false
    private var focusRegistration: AudioFocusRegistration? = null

    private val _mediaState = MutableStateFlow(MediaPlayerState.MEDIA_PLAYER_STATE_IDLE)
    override val state = _mediaState.asStateFlow()

    private val isPlaying: Boolean get() = _player?.isPlaying ?: false

    private val isPaused: Boolean
        get() = _player?.let {
            !it.isPlaying && it.playbackState != Player.STATE_IDLE && it.playbackState != Player.STATE_ENDED
        } ?: false

    private var _volume = 1.0f
    override var volume: Float
        get() = _volume
        set(value) {
            _volume = value
            _player?.volume = volumeOrMuted
        }


    private var _muted = false
    override var muted
        get() = _muted
        set(value) {
            _muted = value
            _player?.volume = volumeOrMuted
        }

    private val volumeOrMuted: Float
        get() = if (_muted) 0.0f else _volume

    fun init() {
        close()
        _player = playerBuilder().apply {
            volume = volumeOrMuted
        }

        focusRegistration = AudioFocusRegistration.request(
            audioManager = audioManager,
            audioAttributes = _player!!.audioAttributes,
            focusGain = focusGain
        )
        isPlayerInit = true
    }

    override fun requestFocus() {
        init()
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
                } else Timber.w("Ignoring empty media uri")
            }
            player.playWhenReady = true
            player.prepare()
        }.onFailure {
            Timber.e(it, "Error playing media $mediaUris")
            onCompletion()
            close()
        }
    }

    override fun setPaused(paused: Boolean) {
        if (paused && isPlaying)
            _player?.pause()
        else if (!paused && isPaused)
            _player?.play()
    }

    override fun stop() {
        close()
    }

    private fun getPlayerListener(onCompletion: () -> Unit) = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Timber.d("Playback state changed to $playbackState")
            // If there's a playback error then the player state will return to idle
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                onCompletion()
                close()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying)
                _mediaState.value = MediaPlayerState.MEDIA_PLAYER_STATE_PLAYING
            else if (isPaused)
                _mediaState.value = MediaPlayerState.MEDIA_PLAYER_STATE_PAUSED
            else
                _mediaState.value = MediaPlayerState.MEDIA_PLAYER_STATE_IDLE
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "Error playing media")
        }
    }

    override fun close() {
        isPlayerInit = false
        _player?.release()
        _player = null
        focusRegistration?.close()
        focusRegistration = null
        _mediaState.value = MediaPlayerState.MEDIA_PLAYER_STATE_IDLE
    }
}