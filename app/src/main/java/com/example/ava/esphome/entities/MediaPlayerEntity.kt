package com.example.ava.esphome.entities

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.esphomeproto.api.ListEntitiesRequest
import com.example.esphomeproto.api.MediaPlayerCommand
import com.example.esphomeproto.api.MediaPlayerCommandRequest
import com.example.esphomeproto.api.MediaPlayerState
import com.example.esphomeproto.api.listEntitiesMediaPlayerResponse
import com.example.esphomeproto.api.mediaPlayerStateResponse
import com.google.protobuf.MessageLite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

interface MediaPlayer {
    /**
     * The current state of media playback.
     */
    val mediaState: Flow<MediaPlayerState>

    /**
     * Starts playback of the specified media.
     */
    fun playMedia(mediaUrl: String)

    /**
     * Sets the paused state of media playback.
     */
    fun setMediaPaused(paused: Boolean)

    /**
     * Stops media playback.
     */
    fun stopMedia()

    /**
     * Gets the playback volume.
     */
    val volume: StateFlow<Float>

    /**
     * Sets the playback volume.
     */
    suspend fun setVolume(value: Float)

    /**
     * Gets whether playback is muted.
     */
    val muted: StateFlow<Boolean>

    /**
     * Sets whether playback is muted.
     */
    suspend fun setMuted(value: Boolean)
}

@OptIn(UnstableApi::class)
class MediaPlayerEntity(
    val key: Int,
    val name: String,
    val objectId: String,
    val mediaPlayer: MediaPlayer
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
                        mediaPlayer.playMedia(message.mediaUrl)
                    } else if (message.hasCommand) {
                        when (message.command) {
                            MediaPlayerCommand.MEDIA_PLAYER_COMMAND_PAUSE ->
                                mediaPlayer.setMediaPaused(true)

                            MediaPlayerCommand.MEDIA_PLAYER_COMMAND_PLAY ->
                                mediaPlayer.setMediaPaused(false)

                            MediaPlayerCommand.MEDIA_PLAYER_COMMAND_STOP ->
                                mediaPlayer.stopMedia()

                            MediaPlayerCommand.MEDIA_PLAYER_COMMAND_MUTE ->
                                mediaPlayer.setMuted(true)

                            MediaPlayerCommand.MEDIA_PLAYER_COMMAND_UNMUTE ->
                                mediaPlayer.setMuted(false)

                            else -> {}
                        }
                    } else if (message.hasVolume) {
                        mediaPlayer.setVolume(message.volume)
                    }
                }
            }
        }
    }

    override fun subscribe() = combine(
        mediaPlayer.mediaState,
        mediaPlayer.volume,
        mediaPlayer.muted,
    ) { state, volume, muted ->
        mediaPlayerStateResponse {
            key = this@MediaPlayerEntity.key
            this.state = state
            this.volume = volume
            this.muted = muted
        }
    }
}