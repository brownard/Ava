package com.example.ava.services

import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC
import androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH
import androidx.media3.common.C.USAGE_ASSISTANT
import androidx.media3.common.C.USAGE_MEDIA
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.ava.esphome.EspHomeDevice
import com.example.ava.esphome.android.logger.TimberLogger
import com.example.ava.esphome.entities.MediaPlayerEntity
import com.example.ava.esphome.entities.SwitchEntity
import com.example.ava.esphome.voiceassistant.VoiceAssistant
import com.example.ava.esphome.voiceassistant.VoiceInputImpl
import com.example.ava.esphome.voiceassistant.VoiceOutputImpl
import com.example.ava.players.AudioPlayer
import com.example.ava.players.AudioPlayerImpl
import com.example.ava.server.ServerImpl
import com.example.ava.settings.MicrophoneSettingsStore
import com.example.ava.settings.PlayerSettingsStore
import com.example.ava.settings.VoiceSatelliteSettingsStore
import com.example.ava.settings.activeStopWords
import com.example.ava.settings.activeWakeWords
import com.example.esphomeproto.api.VoiceAssistantFeature
import com.example.esphomeproto.api.deviceInfoResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class DeviceBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val satelliteSettingsStore: VoiceSatelliteSettingsStore,
    private val microphoneSettingsStore: MicrophoneSettingsStore,
    private val playerSettingsStore: PlayerSettingsStore
) {
    suspend fun buildVoiceSatellite(coroutineContext: CoroutineContext): EspHomeDevice {
        val satelliteSettings = satelliteSettingsStore.get()
        // Need a reference to voiceOutput as it needs to be passed to
        // both the VoiceAssistant and MediaPlayerEntity
        val voiceOutput = playerSettingsStore.toVoiceOutput()
        return EspHomeDevice(
            coroutineContext = coroutineContext,
            port = satelliteSettings.serverPort,
            server = ServerImpl(),
            deviceInfo = deviceInfoResponse {
                name = satelliteSettings.name
                macAddress = satelliteSettings.macAddress
                voiceAssistantFeatureFlags = VoiceAssistantFeature.VOICE_ASSISTANT.flag or
                        VoiceAssistantFeature.API_AUDIO.flag or
                        VoiceAssistantFeature.TIMERS.flag or
                        VoiceAssistantFeature.ANNOUNCE.flag or
                        VoiceAssistantFeature.START_CONVERSATION.flag
            },
            voiceAssistant = VoiceAssistant(
                coroutineContext = coroutineContext,
                voiceInput = microphoneSettingsStore.toVoiceInput(),
                voiceOutput = voiceOutput
            ),
            logger = TimberLogger(),
            entities = listOf(
                MediaPlayerEntity(
                    key = 0,
                    name = "Media Player",
                    objectId = "media_player",
                    mediaPlayer = voiceOutput
                ),
                SwitchEntity(
                    key = 1,
                    name = "Mute Microphone",
                    objectId = "mute_microphone",
                    getState = microphoneSettingsStore.muted
                ) { microphoneSettingsStore.muted.set(it) },
                SwitchEntity(
                    key = 2,
                    name = "Enable Wake Sound",
                    objectId = "enable_wake_sound",
                    getState = playerSettingsStore.enableWakeSound
                ) { playerSettingsStore.enableWakeSound.set(it) },
                SwitchEntity(
                    key = 3,
                    name = "Repeat Timer Sound",
                    objectId = "repeat_timer_sound",
                    getState = playerSettingsStore.repeatTimerFinishedSound
                ) { playerSettingsStore.repeatTimerFinishedSound.set(it) }
            )
        )
    }

    private fun MicrophoneSettingsStore.toVoiceInput() = VoiceInputImpl(
        availableWakeWords = availableWakeWords,
        availableStopWords = availableStopWords,
        activeWakeWords = activeWakeWords,
        activeStopWords = activeStopWords,
        muted = muted
    )

    private suspend fun PlayerSettingsStore.toVoiceOutput(): VoiceOutputImpl {
        val playerSettings = get()
        return VoiceOutputImpl(
            ttsPlayer = createAudioPlayer(
                USAGE_ASSISTANT,
                AUDIO_CONTENT_TYPE_SPEECH,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK

            ),
            mediaPlayer = createAudioPlayer(
                USAGE_MEDIA,
                AUDIO_CONTENT_TYPE_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ),
            enableWakeSound = { enableWakeSound.get() },
            wakeSound = { wakeSound.get() },
            timerFinishedSound = { timerFinishedSound.get() },
            repeatTimerFinishedSound = { repeatTimerFinishedSound.get() },
            enableErrorSound = { enableErrorSound.get() },
            errorSound = { errorSound.get() },
            volume = playerSettings.volume,
            volumeChanged = { volume.set(it) },
            muted = playerSettings.muted,
            mutedChanged = { muted.set(it) }
        )
    }

    @OptIn(UnstableApi::class)
    fun createAudioPlayer(usage: Int, contentType: Int, focusGain: Int): AudioPlayer {
        val audioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager
        return AudioPlayerImpl(audioManager, focusGain) {
            ExoPlayer.Builder(context).setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(contentType)
                    .build(),
                false
            ).build()
        }
    }
}