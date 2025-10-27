package com.example.ava.esphome

import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.ava.audio.MicrophoneInput
import com.example.ava.esphome.entities.Entity
import com.example.ava.esphome.entities.MediaPlayerEntity
import com.example.ava.microwakeword.WakeWordDetector
import com.example.ava.microwakeword.WakeWordProvider
import com.example.ava.players.TtsPlayer
import com.example.ava.preferences.VoiceSatelliteSettings
import com.example.esphomeproto.VoiceAssistantAnnounceRequest
import com.example.esphomeproto.VoiceAssistantConfigurationRequest
import com.example.esphomeproto.VoiceAssistantEvent
import com.example.esphomeproto.VoiceAssistantEventResponse
import com.example.esphomeproto.VoiceAssistantFeature
import com.example.esphomeproto.VoiceAssistantSetConfiguration
import com.example.esphomeproto.deviceInfoResponse
import com.example.esphomeproto.voiceAssistantAnnounceFinished
import com.example.esphomeproto.voiceAssistantAudio
import com.example.esphomeproto.voiceAssistantConfigurationResponse
import com.example.esphomeproto.voiceAssistantRequest
import com.example.esphomeproto.voiceAssistantWakeWord
import com.google.protobuf.ByteString
import com.google.protobuf.GeneratedMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

class VoiceSatellite(
    coroutineContext: CoroutineContext,
    val settings: VoiceSatelliteSettings,
    wakeWordProvider: WakeWordProvider,
    stopWordProvider: WakeWordProvider,
    val ttsPlayer: TtsPlayer
) : EspHomeDevice(
    coroutineContext,
    settings.name,
    settings.serverPort,
    createDeviceInfo(settings),
    listOf<Entity>(MediaPlayerEntity(ttsPlayer))
) {
    private val wakeWordDetector = WakeWordDetector(wakeWordProvider).apply {
        activeWakeWords = listOf(settings.wakeWord)
    }

    private val stopWordDetector = WakeWordDetector(stopWordProvider).apply {
        // Currently only detects the first available stop word.
        // TODO make this configurable
        activeWakeWords = stopWordProvider.getWakeWords()
            .take(1)
            .map { it.id }
    }

    private var continueConversation = false
    private val isStreaming = AtomicBoolean(false)
    private val enableWakeWord = AtomicBoolean(true)
    private val enableStopWord = AtomicBoolean(false)

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start() {
        super.start()
        startMicrophoneInput()
    }

    override suspend fun handleMessage(message: GeneratedMessage) {
        when (message) {
            is VoiceAssistantConfigurationRequest -> sendMessage(
                voiceAssistantConfigurationResponse {
                    availableWakeWords += wakeWordDetector.wakeWords.map {
                        voiceAssistantWakeWord {
                            id = it.id
                            wakeWord = it.wakeWord.wake_word
                            trainedLanguages += it.wakeWord.trained_languages.toList()
                        }
                    }
                    activeWakeWords += wakeWordDetector.activeWakeWords
                    maxActiveWakeWords = 1
                })

            is VoiceAssistantSetConfiguration -> {
                val activeWakeWords =
                    message.activeWakeWordsList.filter { wakeWordDetector.wakeWords.any { wakeWord -> wakeWord.id == it } }
                Log.d(TAG, "Setting active wake words: $activeWakeWords")
                wakeWordDetector.activeWakeWords = activeWakeWords
                val ignoredWakeWords =
                    message.activeWakeWordsList.filter { !activeWakeWords.contains(it) }
                if (ignoredWakeWords.isNotEmpty())
                    Log.w(TAG, "Ignoring wake words: $ignoredWakeWords")
            }

            is VoiceAssistantAnnounceRequest -> {
                continueConversation = message.startConversation
                ttsPlayer.playAnnouncement(message.mediaId, ::ttsFinishedCallback)
            }

            is VoiceAssistantEventResponse -> handleVoiceAssistantMessage(message)

            else -> super.handleMessage(message)
        }
    }

    private fun handleVoiceAssistantMessage(voiceEvent: VoiceAssistantEventResponse) {
        when (voiceEvent.eventType) {
            VoiceAssistantEvent.VOICE_ASSISTANT_RUN_START -> {
                val ttsUrl = voiceEvent.dataList.firstOrNull { data -> data.name == "url" }?.value
                ttsPlayer.runStart(ttsUrl, ::ttsFinishedCallback)
                continueConversation = false
            }

            VoiceAssistantEvent.VOICE_ASSISTANT_STT_VAD_END, VoiceAssistantEvent.VOICE_ASSISTANT_STT_END -> {
                isStreaming.set(false)
            }

            VoiceAssistantEvent.VOICE_ASSISTANT_INTENT_PROGRESS -> {
                if (voiceEvent.dataList.firstOrNull { data -> data.name == "tts_start_streaming" }?.value == "1") {
                    ttsPlayer.startStreaming()
                }
            }

            VoiceAssistantEvent.VOICE_ASSISTANT_INTENT_END -> {
                if (voiceEvent.dataList.firstOrNull { data -> data.name == "continue_conversation" }?.value == "1") {
                    continueConversation = true
                }
            }

            VoiceAssistantEvent.VOICE_ASSISTANT_TTS_END -> {
                if (!ttsPlayer.played) {
                    val ttsUrl =
                        voiceEvent.dataList.firstOrNull { data -> data.name == "url" }?.value
                    ttsPlayer.play(ttsUrl)
                }
            }

            VoiceAssistantEvent.VOICE_ASSISTANT_RUN_END -> {
                isStreaming.set(false)
                enableWakeWord.set(true)
                enableStopWord.set(false)
                ttsPlayer.runEnd()
            }

            else -> {
                Log.d(TAG, "Unhandled voice assistant event: ${voiceEvent.eventType}")
            }
        }
    }

    private sealed class AudioResult {
        data class Audio(val audio: ByteString) : AudioResult()
        data class WakeDetected(val wakeWord: String) : AudioResult()
        class StopDetected() : AudioResult()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startMicrophoneInput() = server.isConnected
            .flatMapLatest { isConnected ->
                if (isConnected) {
                    handleMicrophoneAudio()
                } else {
                    emptyFlow()
                }
            }
            .flowOn(Dispatchers.IO)
            .onEach {
                when (it) {
                    is AudioResult.Audio -> sendMessage(voiceAssistantAudio { data = it.audio })
                    is AudioResult.WakeDetected -> wakeSatellite(it.wakeWord)
                    is AudioResult.StopDetected -> stopSatellite()
                }
            }
            .launchIn(scope)

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun handleMicrophoneAudio() = flow<AudioResult> {
        MicrophoneInput().use { microphoneInput ->
            microphoneInput.start()
            while (true) {
                val audio = microphoneInput.read()
                if (isStreaming.get()) {
                    emit(AudioResult.Audio(ByteString.copyFrom(audio)))
                    audio.rewind()
                }
                // Always run audio through the models to keep
                // their internal state up to date
                val wakeDetections = wakeWordDetector.detect(audio)
                audio.rewind()
                if (!isStreaming.get() && enableWakeWord.get() && wakeDetections.isNotEmpty()) {
                    emit(AudioResult.WakeDetected(wakeDetections.values.first().wakeWordPhrase))
                }

                val stopDetections = stopWordDetector.detect(audio)
                audio.rewind()
                if (!isStreaming.get() && enableStopWord.get() && stopDetections.isNotEmpty()) {
                    emit(AudioResult.StopDetected())
                }

                // yield to ensure upstream emissions and
                // cancellation have a chance to occur
                yield()
            }
        }
    }

    private suspend fun wakeSatellite(wakeWordPhrase: String = "") {
        Log.d(TAG, "Wake satellite")
        isStreaming.set(true)
        sendMessage(
            voiceAssistantRequest
            {
                start = true
                this.wakeWordPhrase = wakeWordPhrase
            })
    }

    private suspend fun stopSatellite() {
        Log.d(TAG, "Stop satellite")
        isStreaming.set(false)
        continueConversation = false
        enableWakeWord.set(true)
        enableStopWord.set(false)
        ttsPlayer.runStopped()
        sendMessage(voiceAssistantAnnounceFinished { })
    }

    private fun ttsFinishedCallback() {
        scope.launch { ttsFinished() }
    }

    private suspend fun ttsFinished() {
        Log.d(TAG, "TTS finished")
        sendMessage(voiceAssistantAnnounceFinished { })
        if (continueConversation) {
            Log.d(TAG, "Continuing conversation")
            wakeSatellite()
        }
    }

    override fun onScopeCompleted(cause: Throwable?) {
        super.onScopeCompleted(cause)
        wakeWordDetector.close()
        stopWordDetector.close()
    }

    companion object {
        private const val TAG = "VoiceSatellite"
        private fun createDeviceInfo(settings: VoiceSatelliteSettings) = deviceInfoResponse {
            name = settings.name
            usesPassword = false
            macAddress = settings.macAddress
            voiceAssistantFeatureFlags = VoiceAssistantFeature.VOICE_ASSISTANT.flag or
                    VoiceAssistantFeature.API_AUDIO.flag or
                    VoiceAssistantFeature.TIMERS.flag or
                    VoiceAssistantFeature.ANNOUNCE.flag or
                    VoiceAssistantFeature.START_CONVERSATION.flag
        }
    }
}