package com.example.ava.esphome.voicesatellite

import android.Manifest
import androidx.annotation.RequiresPermission
import com.example.ava.audio.MicrophoneInput
import com.example.ava.microwakeword.WakeWordDetector
import com.example.ava.microwakeword.WakeWordProvider
import com.google.protobuf.ByteString
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class VoiceSatelliteAudioInput(
    private val wakeWordProvider: WakeWordProvider,
    private val stopWordProvider: WakeWordProvider
) {
    val availableWakeWords = wakeWordProvider.getWakeWords()
    val availableStopWords = stopWordProvider.getWakeWords()

    private val activeWakeWordsChanged = AtomicBoolean(false)
    private val _activeWakeWords = AtomicReference(listOf<String>())
    var activeWakeWords: List<String>
        get() = _activeWakeWords.get()
        set(value) {
            _activeWakeWords.set(value.toList())
            activeWakeWordsChanged.set(true)
        }

    private val activeStopWordsChanged = AtomicBoolean(false)
    private val _activeStopWords = AtomicReference(listOf<String>())
    var activeStopWords: List<String>
        get() = _activeStopWords.get()
        set(value) {
            _activeStopWords.set(value.toList())
            activeStopWordsChanged.set(true)
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

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() = flow {
        val microphoneInput = MicrophoneInput()
        val wakeWordDetector = WakeWordDetector(wakeWordProvider).apply {
            setActiveWakeWords(activeWakeWords)
        }
        val stopWordDetector = WakeWordDetector(stopWordProvider).apply {
            setActiveWakeWords(activeStopWords)
        }
        try {
            microphoneInput.start()
            while (true) {
                if (activeWakeWordsChanged.compareAndSet(true, false)) {
                    wakeWordDetector.setActiveWakeWords(activeWakeWords)
                }

                if (activeStopWordsChanged.compareAndSet(true, false)) {
                    stopWordDetector.setActiveWakeWords(activeStopWords)
                }

                val audio = microphoneInput.read()
                if (isStreaming) {
                    emit(AudioResult.Audio(ByteString.copyFrom(audio)))
                    audio.rewind()
                }

                // Always run audio through the models to keep
                // their internal state up to date
                val wakeDetections = wakeWordDetector.detect(audio)
                audio.rewind()
                if (wakeDetections.isNotEmpty()) {
                    emit(AudioResult.WakeDetected(wakeDetections.first().wakeWordPhrase))
                }

                val stopDetections = stopWordDetector.detect(audio)
                audio.rewind()
                if (stopDetections.isNotEmpty()) {
                    emit(AudioResult.StopDetected(stopDetections.first().wakeWordPhrase))
                }

                // yield to ensure upstream emissions and
                // cancellation have a chance to occur
                yield()
            }
        } finally {
            microphoneInput.close()
            wakeWordDetector.close()
            stopWordDetector.close()
        }
    }
}