package com.example.ava.esphome.voicesatellite

import android.Manifest
import androidx.annotation.RequiresPermission
import com.example.ava.esphome.Connected
import com.example.ava.esphome.Disconnected
import com.example.ava.esphome.EspHomeState
import com.example.ava.esphome.voicesatellite.VoiceTimer.Companion.timerFromEvent
import com.example.ava.tasker.StopRingingRunner
import com.example.ava.tasker.WakeSatelliteRunner
import com.example.esphomeproto.api.VoiceAssistantAnnounceRequest
import com.example.esphomeproto.api.VoiceAssistantConfigurationRequest
import com.example.esphomeproto.api.VoiceAssistantEventResponse
import com.example.esphomeproto.api.VoiceAssistantSetConfiguration
import com.example.esphomeproto.api.VoiceAssistantTimerEvent
import com.example.esphomeproto.api.VoiceAssistantTimerEventResponse
import com.example.esphomeproto.api.voiceAssistantConfigurationResponse
import com.example.esphomeproto.api.voiceAssistantWakeWord
import com.google.protobuf.MessageLite
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

data object Listening : EspHomeState
data object Responding : EspHomeState
data object Processing : EspHomeState

data class VoiceError(val message: String) : EspHomeState

class VoiceSatellite(
    coroutineContext: CoroutineContext,
    val voiceInput: VoiceInput,
    val voiceOutput: VoiceOutput,
) : AutoCloseable {
    private val scope = CoroutineScope(
        coroutineContext + Job(coroutineContext.job) + CoroutineName("${this.javaClass.simpleName} Scope")
    )
    private val isConnected = MutableStateFlow(false)
    private val subscription = MutableSharedFlow<MessageLite>()
    protected val _state = MutableStateFlow<EspHomeState>(Disconnected)
    val state = _state.asStateFlow()

    private var pipeline: VoicePipeline? = null
    private var announcement: Announcement? = null
    private val _pendingTimers = MutableStateFlow<Map<String, VoiceTimer>>(emptyMap())
    private val _ringingTimer = MutableStateFlow<VoiceTimer?>(null)

    val allTimers = combine(_pendingTimers, _ringingTimer) { pending, ringing ->
        listOfNotNull(ringing) + pending.values.sorted()
    }

    private val isRinging: Boolean
        get() = _ringingTimer.value != null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        startVoiceInput()

        // Wire up tasker actions
        WakeSatelliteRunner.register { scope.launch { wakeSatellite() } }
        StopRingingRunner.register { scope.launch { stopTimer() } }
    }
    fun subscribe() = subscription.asSharedFlow()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startVoiceInput() = isConnected
        .flatMapLatest { isConnected ->
            if (isConnected) voiceInput.start() else emptyFlow()
        }
        .onEach {
            handleAudioResult(audioResult = it)
        }
        .launchIn(scope)

    suspend fun onConnected() {
        isConnected.value = true
        resetState()
    }

    suspend fun onDisconnected() {
        isConnected.value = false
        resetState(Disconnected)
    }

    suspend fun handleMessage(message: MessageLite) {
        when (message) {
            is VoiceAssistantConfigurationRequest -> subscription.emit(
                voiceAssistantConfigurationResponse {
                    availableWakeWords += voiceInput.getAvailableWakeWords().map {
                        voiceAssistantWakeWord {
                            id = it.id
                            wakeWord = it.wakeWord.wake_word
                            trainedLanguages += it.wakeWord.trained_languages.toList()
                        }
                    }
                    activeWakeWords += voiceInput.activeWakeWords.get()
                    maxActiveWakeWords = 2
                })

            is VoiceAssistantSetConfiguration -> {
                val availableWakeWords = voiceInput.getAvailableWakeWords()
                val activeWakeWords =
                    message.activeWakeWordsList.filter { availableWakeWords.any { wakeWord -> wakeWord.id == it } }
                Timber.d("Setting active wake words: $activeWakeWords")
                if (activeWakeWords.isNotEmpty()) {
                    voiceInput.activeWakeWords.set(activeWakeWords)
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

            is VoiceAssistantEventResponse ->
                pipeline?.handleEvent(message) ?: Timber.w("No pipeline to handle event: $message")

            is VoiceAssistantTimerEventResponse -> handleTimerMessage(message)
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
                    voiceOutput.duck()
                    voiceOutput.playTimerFinishedSound { repeat ->
                        scope.launch { onTimerSoundFinished(repeat) }
                    }
                }
            }

            VoiceAssistantTimerEvent.UNRECOGNIZED -> {}
        }
    }

    private suspend fun handleAnnouncement(
        startConversation: Boolean,
        mediaId: String,
        preannounceId: String
    ) {
        resetState()
        announcement = Announcement(
            scope = scope,
            voiceOutput = voiceOutput,
            sendMessage = { subscription.emit(it) },
            stateChanged = { _state.value = it },
            ended = { onTtsFinished(it) }
        ).apply {
            voiceOutput.duck()
            announce(mediaId, preannounceId, startConversation)
        }
    }

    private suspend fun handleAudioResult(audioResult: AudioResult) {
        when (audioResult) {
            is AudioResult.Audio -> pipeline?.processMicAudio(audioResult.audio)

            is AudioResult.WakeDetected ->
                onWakeDetected(audioResult.wakeWord)

            is AudioResult.StopDetected ->
                onStopDetected()
        }
    }

    private suspend fun onWakeDetected(wakeWordPhrase: String) {
        // Allow using the wake word to stop the timer
        // TODO: Should the satellite also wake?
        if (isRinging) {
            stopTimer()
        } else {
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
        // Multiple wake detections from the same wake word can be triggered
        // so ensure the satellite is only woken once. Currently this is
        // achieved by creating a pipeline in the Listening state
        // on the first wake detection and checking for that here.
        if (pipeline?.state == Listening) return

        Timber.d("Wake satellite")
        resetState()
        pipeline = createPipeline()
        if (!isContinueConversation) {
            voiceOutput.duck()
            // Start streaming audio only after the wake sound has finished
            voiceOutput.playWakeSound {
                scope.launch { pipeline?.start(wakeWordPhrase) }
            }
        } else {
            pipeline?.start()
        }
    }

    private fun createPipeline() = VoicePipeline(
        scope = scope,
        voiceOutput = voiceOutput,
        sendMessage = { subscription.emit(it) },
        listeningChanged = {
            if (it) voiceOutput.duck()
            voiceInput.isStreaming = it
        },
        stateChanged = { _state.value = it },
        ended = { onTtsFinished(it) }
    )

    private suspend fun stopSatellite() {
        // Ignore the stop request if the satellite is idle or currently streaming
        // microphone audio as there's either nothing to stop or the stop word was
        // used incidentally as part of the voice command.
        val state = _state.value
        if (state is Connected || state is Listening) return
        Timber.d("Stop satellite")
        resetState()
        voiceOutput.unDuck()
    }

    private fun stopTimer() {
        Timber.d("Stop timer")
        if (isRinging) {
            _ringingTimer.update { null }
            voiceOutput.stopTTS()
            voiceOutput.unDuck()
        }
    }

    private suspend fun onTtsFinished(continueConversation: Boolean) {
        Timber.d("TTS finished")
        if (continueConversation) {
            Timber.d("Continuing conversation")
            wakeSatellite(isContinueConversation = true)
        } else {
            voiceOutput.unDuck()
        }
    }

    private suspend fun onTimerSoundFinished(repeat: Boolean) {
        delay(1000)
        if (isRinging) {
            if (repeat) {
                voiceOutput.playTimerFinishedSound {
                    scope.launch { onTimerSoundFinished(it) }
                }
            } else {
                stopTimer()
            }
        } else {
            voiceOutput.unDuck()
        }
    }

    private suspend fun resetState(newState: EspHomeState = Connected) {
        pipeline?.stop()
        pipeline = null
        announcement?.stop()
        announcement = null
        _ringingTimer.update { null }
        voiceInput.isStreaming = false
        voiceOutput.stopTTS()
        _state.value = newState
    }

    override fun close() {
        scope.cancel()
        voiceOutput.close()
        WakeSatelliteRunner.unregister()
        StopRingingRunner.unregister()
    }
}