package com.example.ava.esphome.entities

import androidx.media3.common.Player
import com.example.ava.players.MediaPlayer
import com.example.esphomeproto.ListEntitiesRequest
import com.example.esphomeproto.MediaPlayerCommand
import com.example.esphomeproto.MediaPlayerCommandRequest
import com.example.esphomeproto.MediaPlayerState
import com.example.esphomeproto.MediaPlayerStateResponse
import com.example.esphomeproto.SubscribeHomeAssistantStatesRequest
import com.example.esphomeproto.listEntitiesMediaPlayerResponse
import com.example.esphomeproto.mediaPlayerStateResponse
import com.google.protobuf.GeneratedMessage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MediaPlayerEntity(
    val ttsPlayer: MediaPlayer,
    val key: Int = KEY,
    val name: String = NAME,
    val objectId: String = OBJECT_ID,
) : Entity {

    private val mediaPlayerState = AtomicReference(MediaPlayerState.MEDIA_PLAYER_STATE_IDLE)
    private val muted = AtomicBoolean(false)

    private val _state = MutableSharedFlow<GeneratedMessage>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val state = _state.asSharedFlow()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED)
                setMediaPlayerState(MediaPlayerState.MEDIA_PLAYER_STATE_IDLE)
        }
    }

    init {
        ttsPlayer.addListener(playerListener)
    }

    override suspend fun handleMessage(message: GeneratedMessage) = sequence {
        when (message) {
            is ListEntitiesRequest -> yield(listEntitiesMediaPlayerResponse {
                key = this@MediaPlayerEntity.key
                name = this@MediaPlayerEntity.name
                objectId = this@MediaPlayerEntity.objectId
                supportsPause = true
            })

            is MediaPlayerCommandRequest -> {
                if (message.hasMediaUrl) {
                    setMediaPlayerState(MediaPlayerState.MEDIA_PLAYER_STATE_PLAYING)
                } else if (message.hasCommand) {
                    if (message.command == MediaPlayerCommand.MEDIA_PLAYER_COMMAND_PAUSE) {
                        setMediaPlayerState(MediaPlayerState.MEDIA_PLAYER_STATE_PAUSED)
                    } else if (message.command == MediaPlayerCommand.MEDIA_PLAYER_COMMAND_PLAY) {
                        setMediaPlayerState(MediaPlayerState.MEDIA_PLAYER_STATE_PLAYING)
                    }
                } else if (message.hasVolume) {
                    setVolume(message.volume)
                }
            }

            is SubscribeHomeAssistantStatesRequest -> {
                yield(getStateResponse())
            }
        }
    }

    private fun setMediaPlayerState(state: MediaPlayerState) {
        this.mediaPlayerState.set(state)
        stateChanged()
    }

    private fun setVolume(volume: Float) {
        ttsPlayer.volume = volume
        stateChanged()
    }

    private fun stateChanged() {
        _state.tryEmit(getStateResponse())
    }

    private fun getStateResponse(): MediaPlayerStateResponse {
        return mediaPlayerStateResponse {
            key = this@MediaPlayerEntity.key
            state = this@MediaPlayerEntity.mediaPlayerState.get()
            volume = ttsPlayer.volume
            muted = this@MediaPlayerEntity.muted.get()
        }
    }

    companion object {
        const val TAG = "MediaPlayerEntity"
        const val KEY = 0
        const val NAME = "Media Player"
        const val OBJECT_ID = "media_player"
    }
}