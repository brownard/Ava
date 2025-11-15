package com.example.ava.players

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.util.EventLogger
import kotlin.apply

class TtsPlayer(context: Context) : MediaPlayer, AutoCloseable {
    private val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        // addAnalyticsListener(EventLogger())
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(TAG, "Playback state changed to $playbackState")
                // If there's a playback error then the player state will return to idle
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    fireAndRemoveCompletionHandler()
                }
            }
        })
    }

    private var ttsStreamUrl: String? = null
    private var _ttsPlayed: Boolean = false
    val ttsPlayed: Boolean
        get() = _ttsPlayed

    private var onCompletion: (() -> Unit)? = null

    val isPlaying get() = player.isPlaying

    override var volume
        get() = player.volume
        set(value) {
            player.volume = value
        }

    fun runStart(ttsStreamUrl: String?, onCompletion: () -> Unit) {
        this.ttsStreamUrl = ttsStreamUrl
        this.onCompletion = onCompletion
        _ttsPlayed = false
    }

    fun runEnd() {
        // Manually fire the completion handler only
        // if tts playback was not started, else it
        // will (or was) fired when the playback ended
        if (!_ttsPlayed)
            fireAndRemoveCompletionHandler()
        _ttsPlayed = false
        ttsStreamUrl = null
    }

    fun streamTts() {
        playTts(ttsStreamUrl)
    }

    fun playTts(ttsUrl: String?) {
        if (!ttsUrl.isNullOrBlank()) {
            _ttsPlayed = true
            play(ttsUrl, null)
        } else {
            Log.w(TAG, "TTS URL is null or blank")
        }
    }

    fun playSound(soundUrl: String?, onCompletion: () -> Unit) {
        playAnnouncement(soundUrl, null, onCompletion)
    }

    fun playAnnouncement(mediaUrl: String?, preannounceUrl: String?, onCompletion: () -> Unit) {
        if (!mediaUrl.isNullOrBlank()) {
            this.onCompletion = onCompletion
            play(mediaUrl, preannounceUrl)
        }
    }

    fun stop() {
        onCompletion = null
        _ttsPlayed = false
        ttsStreamUrl = null
        player.stop()
    }

    private fun play(mediaUrl: String, preannounceUrl: String?) {
        runCatching {
            player.clearMediaItems()
            if (!preannounceUrl.isNullOrBlank())
                player.addMediaItem(MediaItem.fromUri(preannounceUrl))
            player.addMediaItem(MediaItem.fromUri(mediaUrl))
            player.playWhenReady = true
            player.prepare()
        }.onFailure {
            Log.e(TAG, "Error playing media $mediaUrl", it)
        }
    }

    private fun fireAndRemoveCompletionHandler() {
        val completion = onCompletion
        onCompletion = null
        completion?.invoke()
    }

    override fun addListener(listener: Player.Listener) {
        player.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        player.removeListener(listener)
    }

    override fun close() {
        player.release()
    }

    companion object {
        private const val TAG = "TtsPlayer"
    }
}