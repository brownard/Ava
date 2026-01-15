package com.example.ava.esphome.voicesatellite

import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.ava.esphome.Connected
import com.example.ava.esphome.EspHomeDevice
import com.example.ava.esphome.EspHomeState
import com.example.ava.esphome.entities.MediaPlayerEntity
import com.example.ava.esphome.entities.SwitchEntity
import com.example.ava.settings.VoiceSatelliteSettingsStore
import com.example.esphomeproto.api.DeviceInfoResponse
import com.example.esphomeproto.api.VoiceAssistantAnnounceRequest
import com.example.esphomeproto.api.VoiceAssistantConfigurationRequest
import com.example.esphomeproto.api.VoiceAssistantEventResponse
import com.example.esphomeproto.api.VoiceAssistantFeature
import com.example.esphomeproto.api.VoiceAssistantSetConfiguration
import com.example.esphomeproto.api.VoiceAssistantTimerEvent
import com.example.esphomeproto.api.VoiceAssistantTimerEventResponse
import com.example.esphomeproto.api.deviceInfoResponse
import com.example.esphomeproto.api.voiceAssistantAnnounceFinished
import com.example.esphomeproto.api.voiceAssistantConfigurationResponse
import com.example.esphomeproto.api.voiceAssistantWakeWord
import com.google.protobuf.MessageLite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

data object Listening : EspHomeState
data object Responding : EspHomeState
data object Processing : EspHomeState

class VoiceSatellite(
    coroutineContext: CoroutineContext,
    name: String,
    port: Int,
    val audioInput: VoiceSatelliteAudioInput,
    val player: VoiceSatellitePlayer,
    val settingsStore: VoiceSatelliteSettingsStore
) : EspHomeDevice(
    coroutineContext,
    name,
    port,
    listOf(
        MediaPlayerEntity(0, "Media Player", "media_player", player),
        SwitchEntity(
            1,
            "Mute Microphone",
            "mute_microphone",
            audioInput.muted
        ) { audioInput.setMuted(it) },
        SwitchEntity(
            2,
            "Play Wake Sound",
            "play_wake_sound",
            player.enableWakeSound
        ) { player.enableWakeSound.set(it) }
    )
) {
    private var timerFinished = false
    private var pipeline: VoicePipeline? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start() {
        super.start()
        startAudioInput()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startAudioInput() = server.isConnected
        .flatMapLatest { isConnected ->
            if (isConnected) audioInput.start() else emptyFlow()
        }
        .flowOn(Dispatchers.IO)
        .onEach {
            handleAudioResult(audioResult = it)
        }
        .launchIn(scope)

    override suspend fun onDisconnected() {
        super.onDisconnected()
        audioInput.isStreaming = false
        pipeline = null
        timerFinished = false
        player.ttsPlayer.stop()
    }

    override suspend fun getDeviceInfo(): DeviceInfoResponse = deviceInfoResponse {
        val settings = settingsStore.get()
        name = settings.name
        usesPassword = false
        macAddress = settings.macAddress
        voiceAssistantFeatureFlags = VoiceAssistantFeature.VOICE_ASSISTANT.flag or
                VoiceAssistantFeature.API_AUDIO.flag or
                VoiceAssistantFeature.TIMERS.flag or
                VoiceAssistantFeature.ANNOUNCE.flag or
                VoiceAssistantFeature.START_CONVERSATION.flag
    }

    override suspend fun handleMessage(message: MessageLite) {
        when (message) {
            is VoiceAssistantConfigurationRequest -> sendMessage(
                voiceAssistantConfigurationResponse {
                    availableWakeWords += audioInput.availableWakeWords.map {
                        voiceAssistantWakeWord {
                            id = it.id
                            wakeWord = it.wakeWord.wake_word
                            trainedLanguages += it.wakeWord.trained_languages.toList()
                        }
                    }
                    activeWakeWords += audioInput.activeWakeWords.value
                    maxActiveWakeWords = 1
                })

            is VoiceAssistantSetConfiguration -> {
                val activeWakeWords =
                    message.activeWakeWordsList.filter { audioInput.availableWakeWords.any { wakeWord -> wakeWord.id == it } }
                Log.d(TAG, "Setting active wake words: $activeWakeWords")
                if (activeWakeWords.isNotEmpty()) {
                    audioInput.setActiveWakeWords(activeWakeWords)
                }
                val ignoredWakeWords =
                    message.activeWakeWordsList.filter { !activeWakeWords.contains(it) }
                if (ignoredWakeWords.isNotEmpty())
                    Log.w(TAG, "Ignoring wake words: $ignoredWakeWords")
            }

            is VoiceAssistantAnnounceRequest -> handleAnnouncement(
                startConversation = message.startConversation,
                mediaId = message.mediaId,
                preannounceId = message.preannounceMediaId
            )

            is VoiceAssistantEventResponse -> pipeline?.handleEvent(message)

            is VoiceAssistantTimerEventResponse -> handleTimerMessage(message)

            else -> super.handleMessage(message)
        }
    }

    private suspend fun handleTimerMessage(timerEvent: VoiceAssistantTimerEventResponse) {
        Log.d(TAG, "Timer event: ${timerEvent.eventType}")
        when (timerEvent.eventType) {
            VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_FINISHED -> {
                if (!timerFinished) {
                    timerFinished = true
                    player.duck()
                    player.playTimerFinishedSound {
                        scope.launch { onTimerFinished() }
                    }
                }
            }

            else -> {}
        }
    }

    private fun handleAnnouncement(
        startConversation: Boolean,
        mediaId: String,
        preannounceId: String
    ) {
        _state.value = Responding
        player.duck()
        player.playAnnouncement(preannounceId, mediaId) {
            scope.launch {
                onTtsFinished(startConversation)
            }
        }
    }

    private suspend fun handleAudioResult(audioResult: VoiceSatelliteAudioInput.AudioResult) {
        when (audioResult) {
            is VoiceSatelliteAudioInput.AudioResult.Audio ->
                pipeline?.processMicAudio(audioResult.audio)

            is VoiceSatelliteAudioInput.AudioResult.WakeDetected ->
                onWakeDetected(audioResult.wakeWord)

            is VoiceSatelliteAudioInput.AudioResult.StopDetected ->
                onStopDetected()
        }
    }

    private suspend fun onWakeDetected(wakeWordPhrase: String) {
        if (timerFinished) {
            stopTimer()
        } else if (_state.value !is Listening) {
            wakeSatellite(wakeWordPhrase)
        }
    }

    private suspend fun onStopDetected() {
        if (timerFinished) {
            stopTimer()
        } else {
            stopSatellite()
        }
    }

    private suspend fun wakeSatellite(
        wakeWordPhrase: String = "",
        isContinueConversation: Boolean = false
    ) {
        Log.d(TAG, "Wake satellite")
        player.duck()
        if (!isContinueConversation) {
            // Start streaming audio only after the wake sound has finished
            player.playWakeSound {
                scope.launch { startVoicePipeline(wakeWordPhrase) }
            }
        } else {
            startVoicePipeline(wakeWordPhrase)
        }
    }

    private suspend fun startVoicePipeline(wakeWordPhrase: String = "") {
        pipeline = VoicePipeline(
            player = player.ttsPlayer,
            sendMessage = { sendMessage(it) },
            listeningChanged = { audioInput.isStreaming = it },
            stateChanged = { _state.value = it },
            ended = {
                scope.launch { onTtsFinished(it) }
            }
        )
        pipeline?.start(wakeWordPhrase)
    }

    private suspend fun stopSatellite() {
        Log.d(TAG, "Stop satellite")
        pipeline = null
        audioInput.isStreaming = false
        player.ttsPlayer.stop()
        _state.value = Connected
        sendMessage(voiceAssistantAnnounceFinished { })
    }

    private fun stopTimer() {
        Log.d(TAG, "Stop timer")
        if (timerFinished) {
            timerFinished = false
            player.ttsPlayer.stop()
        }
    }

    private suspend fun onTtsFinished(continueConversation: Boolean) {
        Log.d(TAG, "TTS finished")
        sendMessage(voiceAssistantAnnounceFinished { })
        if (continueConversation) {
            Log.d(TAG, "Continuing conversation")
            wakeSatellite(isContinueConversation = true)
        } else {
            player.unDuck()
            _state.value = Connected
        }
    }

    private suspend fun onTimerFinished() {
        delay(1000)
        if (timerFinished) {
            player.playTimerFinishedSound {
                scope.launch { onTimerFinished() }
            }
        } else {
            player.unDuck()
        }
    }

    override fun close() {
        super.close()
        player.close()
    }

    companion object {
        private const val TAG = "VoiceSatellite"
    }
}