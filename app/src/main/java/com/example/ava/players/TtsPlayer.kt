package com.example.ava.players

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.util.EventLogger
import java.io.IOException
import kotlin.apply
import kotlin.let

class TtsPlayer(context: Context) : MediaPlayer, AutoCloseable {
    private val player = ExoPlayer.Builder(context).build().apply {
        addAnalyticsListener(EventLogger())
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(TAG, "Playback state changed to $playbackState")
                // If there's a playback error then the player state will return to idle
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    onCompletion?.invoke()
                }
            }
        })
    }

    private var ttsUrl: String? = null
    private var onCompletion: (() -> Unit)? = null
    private var _played: Boolean = false
    val played: Boolean
        get() = _played

    override var volume
        get() = player.volume
        set(value) {
            player.volume = value
        }

    fun runStart(streamUrl: String?, onCompletion: () -> Unit){
        ttsUrl = streamUrl
        this.onCompletion = onCompletion
        _played = false
    }

    fun runEnd() {
        if (!_played)
            onCompletion?.invoke()
        _played = false
        ttsUrl = null
    }

    fun runStopped() {
        _played = false
        ttsUrl = null
        onCompletion = null
        player.stop()
    }

    fun startStreaming() {
        play()
    }

    fun play(url: String?){
        ttsUrl = url
        play()
    }

    fun playAnnouncement(url: String?, onCompletion: () -> Unit) {
        ttsUrl = url
        this.onCompletion = onCompletion
        _played = false
        play()
    }

    private fun play() {
        if (_played)
            return
        ttsUrl?.let {
            _played = true
            try {
                player.playWhenReady = true
                val mediaItem = MediaItem.fromUri(it)
                player.setMediaItem(mediaItem)
                player.prepare()
            } catch (e: IOException) {
                Log.e(TAG, "Error playing media $it", e)
            }
        }
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