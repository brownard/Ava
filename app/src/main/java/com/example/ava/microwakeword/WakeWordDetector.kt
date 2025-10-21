package com.example.ava.microwakeword

import android.util.Log
import com.example.ava.utils.fillFrom
import com.example.microfeatures.MicroFrontend
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val SAMPLES_PER_SECOND = 16000
private const val SAMPLES_PER_CHUNK = 160  // 10ms
private const val BYTES_PER_SAMPLE = 2  // 16-bit
private const val BYTES_PER_CHUNK = SAMPLES_PER_CHUNK * BYTES_PER_SAMPLE

class WakeWordDetector(private val wakeWordProvider: WakeWordProvider): AutoCloseable {
    private val frontend = MicroFrontend()
    private val buffer = ByteBuffer.allocateDirect(BYTES_PER_CHUNK)
    private var loadedModels: MutableMap<String, MicroWakeWord> = mutableMapOf()
    private val activeWakeWordsChanged = AtomicBoolean(false)
    private val _activeWakeWords = AtomicReference<List<String>>(listOf())

    val wakeWords = wakeWordProvider.getWakeWords()

    var activeWakeWords: List<String>
        get() = _activeWakeWords.get()
        set(value) {
            _activeWakeWords.set(value.toList())
            activeWakeWordsChanged.set(true)
        }

    data class DetectionResult(val detected: Boolean, val wakeWordId: String, val wakeWordPhrase: String)

    fun detect(audio: ByteBuffer) : Map<String, DetectionResult> {
        checkLoadedWakeWordModels()
        val detections = mutableMapOf<String, DetectionResult>()
        buffer.fillFrom(audio)
        while (buffer.flip().remaining() == BYTES_PER_CHUNK) {
            val processOutput = frontend.processSamples(buffer)
            buffer.position(buffer.position() + processOutput.samplesRead * BYTES_PER_SAMPLE)
            buffer.compact()
            buffer.fillFrom(audio)
            if (processOutput.features.isEmpty())
                continue
            for (wakeWord in loadedModels.values) {
                val result = wakeWord.processAudioFeatures(processOutput.features)
                if (result && !detections.containsKey(wakeWord.id))
                    detections[wakeWord.id] = DetectionResult(true, wakeWord.id, wakeWord.wakeWord)
            }
        }
        buffer.compact()
        return detections
    }

    private fun checkLoadedWakeWordModels() {
        if (!activeWakeWordsChanged.compareAndSet(true, false))
            return
        val activeWakeWords = this.activeWakeWords
        val modelsToRemove = loadedModels.keys.filter { !activeWakeWords.contains(it) }
        for (model in modelsToRemove) {
            loadedModels.remove(model)?.close()
        }
        for (wakeWord in activeWakeWords) {
            if (!loadedModels.containsKey(wakeWord)) {
                val wakeWordWithId = wakeWords.firstOrNull { it.id == wakeWord }
                if (wakeWordWithId == null) {
                    Log.w(TAG, "Wake word with id $wakeWord not found")
                    continue
                }
                loadedModels[wakeWord] = MicroWakeWord(
                    wakeWordWithId.id,
                    wakeWordWithId.wakeWord.wake_word,
                    wakeWordProvider.loadWakeWordModel(wakeWordWithId.wakeWord.model),
                    wakeWordWithId.wakeWord.micro.probability_cutoff,
                    wakeWordWithId.wakeWord.micro.sliding_window_size
                )
            }
        }
    }

    override fun close() {
        frontend.close()
        for(model in loadedModels.values)
            model.close()
        loadedModels.clear()
        activeWakeWords = listOf()
    }

    companion object {
        private const val TAG = "WakeWordDetector"
    }
}