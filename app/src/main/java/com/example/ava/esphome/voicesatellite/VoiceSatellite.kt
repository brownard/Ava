package com.example.ava.esphome.voicesatellite

import android.Manifest
import androidx.annotation.RequiresPermission
import com.example.ava.esphome.Connected
import com.example.ava.esphome.EspHomeDevice
import com.example.ava.esphome.EspHomeState
import com.example.ava.esphome.entities.MediaPlayerEntity
import com.example.ava.esphome.entities.SwitchEntity
import com.example.ava.esphome.voicesatellite.VoiceTimer.Companion.timerFromEvent
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

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
    private var pipeline: VoicePipeline? = null

    private val _pendingTimers = MutableStateFlow<Map<String, VoiceTimer>>(emptyMap())
    private val _ringingTimer = MutableStateFlow<VoiceTimer?>(null)

    val allTimers = combine(_pendingTimers, _ringingTimer) { pending, ringing ->
        listOfNotNull(ringing) + pending.values.sorted()
    }

    private val isRinging: Boolean
        get() = _ringingTimer.value != null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start() {
        super.start()
        startAudioInput()
    }

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
        _ringingTimer.update { null }
        player.ttsPlayer.stop()
    }

    override suspend fun getDeviceInfo(): DeviceInfoResponse = deviceInfoResponse {
        val settings = settingsStore.get()
        name = settings.name
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
                    maxActiveWakeWords = 2
                })

            is VoiceAssistantSetConfiguration -> {
                val activeWakeWords =
                    message.activeWakeWordsList.filter { audioInput.availableWakeWords.any { wakeWord -> wakeWord.id == it } }
                Timber.d("Setting active wake words: $activeWakeWords")
                if (activeWakeWords.isNotEmpty()) {
                    audioInput.setActiveWakeWords(activeWakeWords)
                }
                val ignoredWakeWords =
                    message.activeWakeWordsList.filter { !activeWakeWords.contains(it) }
                if (ignoredWakeWords.isNotEmpty())
                    Timber.w("Ignoring wake words: $ignoredWakeWords")
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

    private suspend fun handleTimerMessage(event: VoiceAssistantTimerEventResponse) {
        Timber.d("Timer event: ${event.eventType}")
        val timer = timerFromEvent(event, Clock.System)
        when (event.eventType) {
            VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_STARTED, VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_UPDATED -> {
                _pendingTimers.update { it + (timer.id to timer) }
            }

            VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_CANCELLED -> {
                _pendingTimers.update { it - timer.id }
            }

            VoiceAssistantTimerEvent.VOICE_ASSISTANT_TIMER_FINISHED -> {
                // Remove the timer now and stash it into _ringingTimer to avoid
                // race conditions if several timers finish at the same time.
                val wasNotRinging = !isRinging
                _pendingTimers.update { it - timer.id }
                _ringingTimer.update { timer }

                if (wasNotRinging) {
                    player.duck()
                    player.playTimerFinishedSound {
                        scope.launch { onTimerSoundFinished() }
                    }
                }
            }

            VoiceAssistantTimerEvent.UNRECOGNIZED -> {}
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
        // Allow using the wake word to stop the timer
        // TODO: Should the satellite also wake?
        if (isRinging) {
            stopTimer()
        }
        // Multiple wake detections from the same wake word can be triggered
        // so care needs to be taken to ensure the satellite is only woken once.
        // Currently this is achieved by creating a pipeline in the Listening state
        // on the first wake detection and checking for that here.
        else if (pipeline?.state != Listening) {
            wakeSatellite(wakeWordPhrase)
        }
    }

    private suspend fun onStopDetected() {
        if (isRinging) {
            stopTimer()
        } else {
            stopSatellite()
        }
    }

    private suspend fun wakeSatellite(
        wakeWordPhrase: String = "",
        isContinueConversation: Boolean = false
    ) {
        Timber.d("Wake satellite")
        player.duck()
        pipeline = createPipeline()
        if (!isContinueConversation) {
            // Start streaming audio only after the wake sound has finished
            player.playWakeSound {
                scope.launch { pipeline?.start(wakeWordPhrase) }
            }
        } else {
            pipeline?.start()
        }
    }

    private fun createPipeline() = VoicePipeline(
        player = player.ttsPlayer,
        sendMessage = { sendMessage(it) },
        listeningChanged = { audioInput.isStreaming = it },
        stateChanged = { _state.value = it },
        ended = {
            scope.launch { onTtsFinished(it) }
        }
    )

    private suspend fun stopSatellite() {
        Timber.d("Stop satellite")
        pipeline = null
        audioInput.isStreaming = false
        player.ttsPlayer.stop()
        _state.value = Connected
        sendMessage(voiceAssistantAnnounceFinished { })
    }

    private fun stopTimer() {
        Timber.d("Stop timer")
        if (isRinging) {
            _ringingTimer.update { null }
            player.ttsPlayer.stop()
        }
    }

    private suspend fun onTtsFinished(continueConversation: Boolean) {
        Timber.d("TTS finished")
        stopSatellite()
        if (continueConversation) {
            Timber.d("Continuing conversation")
            wakeSatellite(isContinueConversation = true)
        } else {
            player.unDuck()
            _state.value = Connected
        }
    }

    private suspend fun onTimerSoundFinished() {
        delay(1000)
        if (isRinging) {
            if (player.repeatTimerFinishedSound.get()) {
                player.playTimerFinishedSound {
                    scope.launch { onTimerSoundFinished() }
                }
            } else {
                stopTimer()
            }
        } else {
            player.unDuck()
        }
    }

    override fun close() {
        super.close()
        player.close()
    }
}