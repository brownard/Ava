package com.example.ava.esphome.voicesatellite

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.ava.players.AudioPlayer
import com.example.ava.settings.SettingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface VoiceSatellitePlayer : AutoCloseable {
    /**
     * The player to use for TTS playback, will also be used for wake and timer finished sounds.
     */
    val ttsPlayer: AudioPlayer

    /**
     * The player to use for media playback.
     */
    val mediaPlayer: AudioPlayer

    /**
     * Whether to enable the wake sound.
     */
    val enableWakeSound: SettingState<Boolean>

    /**
     * The wake sound to play when the satellite is woken.
     */
    val wakeSound: SettingState<String>

    /**
     * The timer finished sound to play when a timer is finished.
     */
    val timerFinishedSound: SettingState<String>

    /**
     * Whether to repeat the timer finished sound when the timer is finished.
     */
    val repeatTimerFinishedSound: SettingState<Boolean>

    /**
     * The playback volume.
     */
    val volume: StateFlow<Float>

    /**
     * Sets the playback volume.
     */
    fun setVolume(value: Float)

    /**
     * Whether playback is muted.
     */
    val muted: StateFlow<Boolean>

    /**
     * Sets whether playback is muted.
     */
    fun setMuted(value: Boolean)

    /**
     * Plays an announcement, optionally with a preannounce sound.
     */
    fun playAnnouncement(
        preannounceUrl: String = "",
        mediaUrl: String,
        onCompletion: () -> Unit = {}
    )

    /**
     * Plays the wake sound if [enableWakeSound] is true.
     */
    suspend fun playWakeSound(onCompletion: () -> Unit = {})

    /**
     * Plays the timer finished sound.
     */
    suspend fun playTimerFinishedSound(onCompletion: () -> Unit = {})

    /**
     * Ducks the media player volume.
     */
    fun duck()

    /**
     * Un-ducks the media player volume.
     */
    fun unDuck()
}

@OptIn(UnstableApi::class)
class VoiceSatellitePlayerImpl(
    override val ttsPlayer: AudioPlayer,
    override val mediaPlayer: AudioPlayer,
    override val enableWakeSound: SettingState<Boolean>,
    override val wakeSound: SettingState<String>,
    override val timerFinishedSound: SettingState<String>,
    override val repeatTimerFinishedSound: SettingState<Boolean>,
    private val duckMultiplier: Float = 0.5f
) : VoiceSatellitePlayer {
    private var _isDucked = false
    private val _volume = MutableStateFlow(1.0f)
    private val _muted = MutableStateFlow(false)

    override val volume get() = _volume.asStateFlow()
    override fun setVolume(value: Float) {
        _volume.value = value
        if (!_muted.value) {
            ttsPlayer.volume = value
            mediaPlayer.volume = if (_isDucked) value * duckMultiplier else value
        }
    }

    override val muted get() = _muted.asStateFlow()
    override fun setMuted(value: Boolean) {
        _muted.value = value
        if (value) {
            mediaPlayer.volume = 0.0f
            ttsPlayer.volume = 0.0f
        } else {
            ttsPlayer.volume = _volume.value
            mediaPlayer.volume = if (_isDucked) _volume.value * duckMultiplier else _volume.value
        }
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
        if (enableWakeSound.get()) {
            ttsPlayer.play(wakeSound.get(), onCompletion)
        } else onCompletion()
    }

    override suspend fun playTimerFinishedSound(onCompletion: () -> Unit) {
        ttsPlayer.play(timerFinishedSound.get(), onCompletion)
    }

    override fun duck() {
        _isDucked = true
        if (!_muted.value) {
            mediaPlayer.volume = _volume.value * duckMultiplier
        }
    }

    override fun unDuck() {
        _isDucked = false
        if (!_muted.value) {
            mediaPlayer.volume = _volume.value
        }
    }

    override fun close() {
        ttsPlayer.close()
        mediaPlayer.close()
    }
}

