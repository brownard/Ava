package com.example.ava.esphome.entities

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.ava.esphome.mediaplayer.MediaPlayer
import com.example.esphomeproto.api.ListEntitiesRequest
import com.example.esphomeproto.api.MediaPlayerCommand
import com.example.esphomeproto.api.MediaPlayerCommandRequest
import com.example.esphomeproto.api.listEntitiesMediaPlayerResponse
import com.example.esphomeproto.api.mediaPlayerStateResponse
import com.google.protobuf.MessageLite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

@OptIn(UnstableApi::class)
class MediaPlayerEntity(
    val key: Int,
    val name: String,
    val objectId: String,
    val mediaPlayer: MediaPlayer,
    val getVolumeState: Flow<Float>,
    val setVolume: suspend (Float) -> Unit,
    val getMutedState: Flow<Boolean>,
    val setMuted: suspend (Boolean) -> Unit
) : Entity {

    override fun handleMessage(message: MessageLite) = flow {
        when (message) {
            is ListEntitiesRequest -> emit(listEntitiesMediaPlayerResponse {
                key = this@MediaPlayerEntity.key
                name = this@MediaPlayerEntity.name
                objectId = this@MediaPlayerEntity.objectId
                supportsPause = true
            })

            is MediaPlayerCommandRequest -> {
                if (message.key == key) {
                    if (message.hasMediaUrl) {
                        mediaPlayer.play(message.mediaUrl)
                    } else if (message.hasCommand) {
                        when (message.command) {
                            MediaPlayerCommand.MEDIA_PLAYER_COMMAND_PAUSE ->
                                mediaPlayer.setPaused(true)

                            MediaPlayerCommand.MEDIA_PLAYER_COMMAND_PLAY ->
                                mediaPlayer.setPaused(false)

                            MediaPlayerCommand.MEDIA_PLAYER_COMMAND_STOP ->
                                mediaPlayer.stop()

                            MediaPlayerCommand.MEDIA_PLAYER_COMMAND_MUTE -> {
                                mediaPlayer.muted = true
                                setMuted(true)
                            }

                            MediaPlayerCommand.MEDIA_PLAYER_COMMAND_UNMUTE -> {
                                mediaPlayer.muted = false
                                setMuted(false)
                            }

                            else -> {}
                        }
                    } else if (message.hasVolume) {
                        mediaPlayer.volume = message.volume
                        setVolume(message.volume)
                    }
                }
            }
        }
    }

    override fun subscribe() = combine(
        mediaPlayer.state,
        getVolumeState,
        getMutedState,
    ) { state, volume, muted ->
        mediaPlayerStateResponse {
            key = this@MediaPlayerEntity.key
            this.state = state
            this.volume = volume
            this.muted = muted
        }
    }
}