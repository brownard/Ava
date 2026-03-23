package com.example.ava.esphome.voiceassistant

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.ava.esphome.entities.MediaPlayer
import com.example.ava.players.AudioPlayer
import com.example.ava.players.AudioPlayerState
import com.example.esphomeproto.api.MediaPlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

interface VoiceOutput : AutoCloseable {
    /**
     * The playback volume.
     */
    val volume: StateFlow<Float>

    /**
     * Sets the playback volume.
     */
    suspend fun setVolume(value: Float)

    /**
     * Whether playback is muted.
     */
    val muted: StateFlow<Boolean>

    /**
     * Sets whether playback is muted.
     */
    suspend fun setMuted(value: Boolean)

    /**
     * Plays a TTS response.
     */
    fun playTTS(ttsUrl: String, onCompletion: () -> Unit = {})

    /**
     * Plays an announcement, optionally with a preannounce sound.
     */
    fun playAnnouncement(
        preannounceUrl: String = "",
        mediaUrl: String,
        onCompletion: () -> Unit = {}
    )

    /**
     * Plays the wake sound.
     */
    suspend fun playWakeSound(onCompletion: () -> Unit = {})

    /**
     * Plays the timer finished sound. The [onCompletion] callback indicates whether the
     * the timer finished sound should be repeated.
     */
    suspend fun playTimerFinishedSound(onCompletion: (repeat: Boolean) -> Unit = {})

    /**
     * Plays the error sound.
     */
    suspend fun playErrorSound(onCompletion: () -> Unit = {})

    /**
     * Ducks the media player volume.
     */
    fun duck()

    /**
     * Un-ducks the media player volume.
     */
    fun unDuck()

    /**
     * Stops any currently playing response or sound.
     */
    fun stopTTS()
}

@OptIn(UnstableApi::class)
class VoiceOutputImpl(
    private val ttsPlayer: AudioPlayer,
    private val mediaPlayer: AudioPlayer,
    private val enableWakeSound: suspend () -> Boolean,
    private val wakeSound: suspend () -> String,
    private val timerFinishedSound: suspend () -> String,
    private val repeatTimerFinishedSound: suspend () -> Boolean,
    private val enableErrorSound: suspend () -> Boolean,
    private val errorSound: suspend () -> String,
    volume: Float = 1.0f,
    private val volumeChanged: suspend (Float) -> Unit = {},
    muted: Boolean = false,
    private val mutedChanged: suspend (Boolean) -> Unit = {},
    private val duckMultiplier: Float = 0.5f
) : VoiceOutput, MediaPlayer {
    private var _isDucked = false
    private val _volume = MutableStateFlow(volume)
    private val _muted = MutableStateFlow(muted)

    init {
        if (!muted) {
            ttsPlayer.volume = volume
            mediaPlayer.volume = if (_isDucked) volume * duckMultiplier else volume
        } else {
            mediaPlayer.volume = 0.0f
            ttsPlayer.volume = 0.0f
        }
    }

    override val volume get() = _volume.asStateFlow()
    override suspend fun setVolume(value: Float) {
        _volume.value = value
        if (!_muted.value) {
            ttsPlayer.volume = value
            mediaPlayer.volume = if (_isDucked) value * duckMultiplier else value
        }
        volumeChanged(value)
    }

    override val muted get() = _muted.asStateFlow()
    override suspend fun setMuted(value: Boolean) {
        _muted.value = value
        if (value) {
            mediaPlayer.volume = 0.0f
            ttsPlayer.volume = 0.0f
        } else {
            ttsPlayer.volume = _volume.value
            mediaPlayer.volume = if (_isDucked) _volume.value * duckMultiplier else _volume.value
        }
        mutedChanged(value)
    }

    override fun playTTS(ttsUrl: String, onCompletion: () -> Unit) {
        ttsPlayer.play(ttsUrl, onCompletion)
    }

    override fun playAnnouncement(
        preannounceUrl: String,
        mediaUrl: String,
        onCompletion: () -> Unit
    ) {
        val urls = if (preannounceUrl.isNotEmpty()) {
            listOf(preannounceUrl, mediaUrl)
        } else {
            listOf(mediaUrl)
        }
        ttsPlayer.play(urls, onCompletion)
    }

    override suspend fun playWakeSound(onCompletion: () -> Unit) {
        if (enableWakeSound()) {
            ttsPlayer.play(wakeSound(), onCompletion)
        } else onCompletion()
    }

    override suspend fun playTimerFinishedSound(onCompletion: (repeat: Boolean) -> Unit) {
        val repeat = repeatTimerFinishedSound()
        ttsPlayer.play(timerFinishedSound()) {
            onCompletion(repeat)
        }
    }

    override suspend fun playErrorSound(onCompletion: () -> Unit) {
        if (enableErrorSound()) {
            ttsPlayer.play(errorSound(), onCompletion)
        } else onCompletion()
    }

    override fun duck() {
        _isDucked = true
        if (!_muted.value) {
            mediaPlayer.volume = _volume.value * duckMultiplier
        }
        // The player should gain audio focus when initialized,
        // ducking any external audio.
        ttsPlayer.init()
    }

    override fun unDuck() {
        _isDucked = false
        if (!_muted.value) {
            mediaPlayer.volume = _volume.value
        }
    }

    override fun stopTTS() {
        ttsPlayer.stop()
    }

    // MediaPlayer implementation

    override val mediaState = mediaPlayer.state.map { state ->
        when (state) {
            AudioPlayerState.PLAYING -> MediaPlayerState.MEDIA_PLAYER_STATE_PLAYING
            AudioPlayerState.PAUSED -> MediaPlayerState.MEDIA_PLAYER_STATE_PAUSED
            AudioPlayerState.IDLE -> MediaPlayerState.MEDIA_PLAYER_STATE_IDLE
        }
    }

    override fun playMedia(mediaUrl: String) {
        mediaPlayer.play(mediaUrl)
    }

    override fun setMediaPaused(paused: Boolean) {
        if (paused) mediaPlayer.pause() else mediaPlayer.unpause()
    }

    override fun stopMedia() {
        mediaPlayer.stop()
    }

    override fun close() {
        ttsPlayer.close()
        mediaPlayer.close()
    }
}
