package com.example.ava.esphome.voicesatellite

import android.Manifest
import androidx.annotation.RequiresPermission
import com.example.ava.audio.MicrophoneInput
import com.example.ava.wakewords.microwakeword.MicroWakeWord
import com.example.ava.wakewords.microwakeword.MicroWakeWordDetector
import com.example.ava.wakewords.models.WakeWordWithId
import com.google.protobuf.ByteString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.yield
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

sealed class AudioResult {
    data class Audio(val audio: ByteString) : AudioResult()
    data class WakeDetected(val wakeWord: String) : AudioResult()
    data class StopDetected(val stopWord: String) : AudioResult()
}

interface VoiceSatelliteAudioInput {
    /**
     * The list of wake words available for selection.
     */
    val availableWakeWords: List<WakeWordWithId>

    /**
     * The list of stop words available for selection.
     */
    val availableStopWords: List<WakeWordWithId>

    /**
     * The list of currently active wake words.
     */
    val activeWakeWords: StateFlow<List<String>>

    /**
     * Sets the currently active wake words.
     */
    fun setActiveWakeWords(value: List<String>)

    /**
     * The list of currently active stop words.
     */
    val activeStopWords: StateFlow<List<String>>

    /**
     * Sets the currently active stop words.
     */
    fun setActiveStopWords(value: List<String>)

    /**
     * Whether the microphone is muted.
     */
    val muted: StateFlow<Boolean>

    /**
     * Sets whether the microphone is muted.
     */
    fun setMuted(value: Boolean)

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

class VoiceSatelliteAudioInputImpl(
    activeWakeWords: List<String>,
    activeStopWords: List<String>,
    override val availableWakeWords: List<WakeWordWithId>,
    override val availableStopWords: List<WakeWordWithId>,
    muted: Boolean = false,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : VoiceSatelliteAudioInput {
    private val _availableWakeWords = availableWakeWords.associateBy { it.id }
    private val _availableStopWords = availableStopWords.associateBy { it.id }

    private val _activeWakeWords = MutableStateFlow(activeWakeWords)
    override val activeWakeWords = _activeWakeWords.asStateFlow()
    override fun setActiveWakeWords(value: List<String>) {
        _activeWakeWords.value = value
    }

    private val _activeStopWords = MutableStateFlow(activeStopWords)
    override val activeStopWords = _activeStopWords.asStateFlow()
    override fun setActiveStopWords(value: List<String>) {
        _activeStopWords.value = value
    }

    private val _muted = MutableStateFlow(muted)
    override val muted = _muted.asStateFlow()
    override fun setMuted(value: Boolean) {
        _muted.value = value
    }

    private val _isStreaming = AtomicBoolean(false)
    override var isStreaming: Boolean
        get() = _isStreaming.get()
        set(value) = _isStreaming.set(value)

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start() = muted.flatMapLatest {
        // Stop microphone when muted
        if (it) emptyFlow()
        else flow {
            val microphoneInput = MicrophoneInput()
            var wakeWords = activeWakeWords.value
            var stopWords = activeStopWords.value
            var detector = createDetector(wakeWords, stopWords)
            try {
                microphoneInput.start()
                while (true) {
                    if (wakeWords != activeWakeWords.value || stopWords != activeStopWords.value) {
                        wakeWords = activeWakeWords.value
                        stopWords = activeStopWords.value
                        detector.close()
                        detector = createDetector(wakeWords, stopWords)
                    }

                    val audio = microphoneInput.read()
                    if (isStreaming) {
                        emit(AudioResult.Audio(ByteString.copyFrom(audio)))
                        audio.rewind()
                    }

                    // Always run audio through the models, even if not currently streaming, to keep
                    // their internal state up to date
                    val detections = detector.detect(audio)
                    for (detection in detections) {
                        if (detection.wakeWordId in wakeWords) {
                            emit(AudioResult.WakeDetected(detection.wakeWordPhrase))
                        } else if (detection.wakeWordId in stopWords) {
                            emit(AudioResult.StopDetected(detection.wakeWordPhrase))
                        }
                    }

                    // yield to ensure upstream emissions and
                    // cancellation have a chance to occur
                    yield()
                }
            } finally {
                microphoneInput.close()
                detector.close()
            }
        }
    }.flowOn(dispatcher)

    private suspend fun createDetector(
        wakeWords: List<String>,
        stopWords: List<String>
    ) = MicroWakeWordDetector(
        loadWakeWords(wakeWords, _availableWakeWords) +
                loadWakeWords(stopWords, _availableStopWords)
    )

    private suspend fun loadWakeWords(
        ids: List<String>,
        wakeWords: Map<String, WakeWordWithId>
    ): List<MicroWakeWord> = buildList {
        for (id in ids) {
            wakeWords[id]?.let { wakeWord ->
                runCatching {
                    add(MicroWakeWord.fromWakeWord(wakeWord))
                }.onFailure {
                    Timber.e(it, "Error loading wake word: $id")
                }
            }
        }
    }
}