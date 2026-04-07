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
     * The title of the currently playing media, or null if unavailable.
     */
    val mediaTitle: StateFlow<String?>

    /**
     * The artist of the currently playing media, or null if unavailable.
     */
    val mediaArtist: StateFlow<String?>

    /**
     * The current playback position in milliseconds, or 0 if not playing.
     */
    val currentPosition: Long

    /**
     * The duration of the currently playing media in milliseconds, or 0 if unknown.
     */
    val duration: Long

    /**
     * Raw artwork image bytes, or null if unavailable.
     */
    val artworkData: StateFlow<ByteArray?>

    /**
     * URL of the artwork image, or null if unavailable.
     */
    val artworkUri: StateFlow<String?>

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
     * Toggles between play and pause.
     */
    fun togglePlayback()

    /**
     * Skips to the next track.
     */
    fun skipToNext()

    /**
     * Returns to the previous track.
     */
    fun skipToPrevious()

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
                // Explicit feature flag bitmask so HA knows exactly what we support.
                // Bits: PAUSE=1, VOLUME_SET=4, VOLUME_MUTE=8, PLAY_MEDIA=512, STOP=4096,
                //       PLAY=16384, SHUFFLE_SET=32768
                // SHUFFLE_SET is advertised so HA accepts shuffle_set calls from Music Assistant
                // LLM scripts (which always emit shuffle:false). The ESPHome proto has no shuffle
                // command so the call is silently accepted and ignored — correct, since
                // shuffle:false is a no-op on a device that doesn't have a queue.
                // NEXT_TRACK(32) and PREVIOUS_TRACK(16) are absent: no ESPHome proto commands
                // exist for them, so HA could never send them; skip buttons are UI-only.
                featureFlags = 1 or 4 or 8 or 512 or 4096 or 16384 or 32768
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

                            MediaPlayerCommand.MEDIA_PLAYER_COMMAND_TOGGLE ->
                                mediaPlayer.togglePlayback()

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