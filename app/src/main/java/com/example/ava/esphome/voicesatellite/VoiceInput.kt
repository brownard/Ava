package com.example.ava.esphome.voicesatellite

import android.Manifest
import androidx.annotation.RequiresPermission
import com.example.ava.audio.MicrophoneInput
import com.example.ava.settings.SettingState
import com.example.ava.wakewords.microwakeword.MicroWakeWord
import com.example.ava.wakewords.microwakeword.MicroWakeWordDetector
import com.example.ava.wakewords.models.WakeWordWithId
import com.google.protobuf.ByteString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.yield
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

sealed class AudioResult {
    data class Audio(val audio: ByteString) : AudioResult()
    data class WakeDetected(val wakeWord: String) : AudioResult()
    class StopDetected() : AudioResult()
}

interface VoiceInput {
    /**
     * The list of wake words available for selection.
     */
    suspend fun getAvailableWakeWords(): List<WakeWordWithId>

    /**
     * The list of stop words available for selection.
     */
    suspend fun getAvailableStopWords(): List<WakeWordWithId>

    /**
     * The list of currently active wake words.
     */
    val activeWakeWords: SettingState<List<String>>

    /**
     * The list of currently active stop words.
     */
    val activeStopWords: SettingState<List<String>>

    /**
     * Whether the microphone is muted.
     */
    val muted: SettingState<Boolean>

    /**
     * Whether the microphone is currently streaming audio.
     */
    var isStreaming: Boolean

    /**
     * Starts listening to audio from the microphone for wake word detection and streaming.
     * If an active wake word or stop word is detected, emits [AudioResult.WakeDetected] or
     * [AudioResult.StopDetected] respectively. If [isStreaming] is true, [AudioResult.Audio] is
     * also emitted with the raw audio data.
     */
    fun start(): Flow<AudioResult>
}

class VoiceInputImpl(
    private val availableWakeWords: Flow<List<WakeWordWithId>>,
    private val availableStopWords: Flow<List<WakeWordWithId>>,
    override val activeWakeWords: SettingState<List<String>>,
    override val activeStopWords: SettingState<List<String>>,
    override val muted: SettingState<Boolean>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : VoiceInput {
    override suspend fun getAvailableWakeWords() = availableWakeWords.first()
    override suspend fun getAvailableStopWords() = availableStopWords.first()

    private val _isStreaming = AtomicBoolean(false)
    override var isStreaming: Boolean
        get() = _isStreaming.get()
        set(value) = _isStreaming.set(value)

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start() = muted.flatMapLatest {
        // Stop microphone when muted
        if (it) emptyFlow()
        else flow {
            MicrophoneInput().use { microphoneInput ->
                microphoneInput.start()
                emitAll(
                    combine(activeWakeWords, activeStopWords) { activeWakeWords, activeStopWords ->
                        readMicrophone(microphoneInput, activeWakeWords, activeStopWords)
                    }.flatMapLatest { it }
                )
            }
        }
    }.flowOn(dispatcher)

    private fun readMicrophone(
        microphoneInput: MicrophoneInput,
        activeWakeWords: List<String>,
        activeStopWords: List<String>
    ) = flow {
        createDetector(activeWakeWords, activeStopWords).use { detector ->
            Timber.d("Created wake word detector")
            while (true) {
                val audio = microphoneInput.read()
                if (isStreaming) {
                    emit(AudioResult.Audio(ByteString.copyFrom(audio)))
                    audio.rewind()
                }

                // Always run audio through the models, even if not currently streaming, to keep
                // their internal state up to date
                val detections = detector.detect(audio)
                for (detection in detections) {
                    if (detection.wakeWordId in activeWakeWords) {
                        emit(AudioResult.WakeDetected(detection.wakeWordPhrase))
                    } else if (detection.wakeWordId in activeStopWords) {
                        emit(AudioResult.StopDetected())
                    }
                }

                // This flow needs to be cancellable and not block upstream emissions, therefore
                // it needs to regularly suspend. Currently the only suspension points are during
                // emissions, but because this flow only emits values when a wake word is detected
                // or microphone audio is streaming. most of the time no emissions and no
                // suspensions occur. Yield to ensure there's always a suspension point.
                yield()
            }
        }
    }

    private suspend fun createDetector(
        wakeWords: List<String>,
        stopWords: List<String>
    ) = MicroWakeWordDetector(
        loadWakeWords(wakeWords, availableWakeWords.first()) +
                loadWakeWords(stopWords, availableStopWords.first())
    )

    private suspend fun loadWakeWords(
        ids: List<String>,
        wakeWords: List<WakeWordWithId>
    ): List<MicroWakeWord> = buildList {
        for (id in ids) {
            wakeWords.firstOrNull { it.id == id }?.let { wakeWord ->
                runCatching {
                    add(MicroWakeWord.fromWakeWord(wakeWord))
                }.onFailure {
                    Timber.e(it, "Error loading wake word: $id")
                }
            }
        }
    }
}