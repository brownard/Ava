package com.example.ava.esphome.voicesatellite

import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.ava.audio.MicrophoneInput
import com.example.ava.wakewords.microwakeword.MicroWakeWord
import com.example.ava.wakewords.microwakeword.MicroWakeWordDetector
import com.example.ava.wakewords.models.WakeWordWithId
import com.google.protobuf.ByteString
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicBoolean

class VoiceSatelliteAudioInput(
    activeWakeWords: List<String>,
    activeStopWords: List<String>,
    val availableWakeWords: List<WakeWordWithId>,
    val availableStopWords: List<WakeWordWithId>,
    muted: Boolean = false
) {
    private val _availableWakeWords = availableWakeWords.associateBy { it.id }
    private val _availableStopWords = availableStopWords.associateBy { it.id }

    private val _activeWakeWords = MutableStateFlow(activeWakeWords)
    val activeWakeWords = _activeWakeWords.asStateFlow()
    fun setActiveWakeWords(value: List<String>) {
        _activeWakeWords.value = value
    }

    private val _activeStopWords = MutableStateFlow(activeStopWords)
    val activeStopWords = _activeStopWords.asStateFlow()
    fun setActiveStopWords(value: List<String>) {
        _activeStopWords.value = value
    }

    private val _muted = MutableStateFlow(muted)
    val muted = _muted.asStateFlow()
    fun setMuted(value: Boolean) {
        _muted.value = value
    }

    private val _isStreaming = AtomicBoolean(false)
    var isStreaming: Boolean
        get() = _isStreaming.get()
        set(value) = _isStreaming.set(value)

    sealed class AudioResult {
        data class Audio(val audio: ByteString) : AudioResult()
        data class WakeDetected(val wakeWord: String) : AudioResult()
        data class StopDetected(val stopWord: String) : AudioResult()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() = muted.flatMapLatest {
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
    }

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
            wakeWords.get(id)?.let {
                runCatching {
                    add(MicroWakeWord.fromWakeWord(it))
                }.onFailure {
                    Log.e(TAG, "Error loading wake word: $id", it)
                }
            }
        }
    }

    companion object {
        private const val TAG = "VoiceSatelliteAudioInput"
    }
}