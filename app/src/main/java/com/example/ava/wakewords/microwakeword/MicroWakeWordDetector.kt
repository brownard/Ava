package com.example.ava.wakewords.microwakeword

import com.example.ava.utils.fillFrom
import com.example.microfeatures.MicroFrontend
import java.nio.ByteBuffer

private const val SAMPLES_PER_SECOND = 16000
private const val SAMPLES_PER_CHUNK = 160  // 10ms
private const val BYTES_PER_SAMPLE = 2  // 16-bit
private const val BYTES_PER_CHUNK = SAMPLES_PER_CHUNK * BYTES_PER_SAMPLE

class MicroWakeWordDetector(private val wakeWords: List<MicroWakeWord>) : AutoCloseable {
    private val frontend = MicroFrontend()
    private val buffer = ByteBuffer.allocateDirect(BYTES_PER_CHUNK)

    data class DetectionResult(
        val wakeWordId: String,
        val wakeWordPhrase: String
    )

    fun detect(audio: ByteBuffer): List<DetectionResult> {
        val detections = mutableListOf<DetectionResult>()
        buffer.fillFrom(audio)
        while (buffer.flip().remaining() == BYTES_PER_CHUNK) {
            val processOutput = frontend.processSamples(buffer)
            buffer.position(buffer.position() + processOutput.samplesRead * BYTES_PER_SAMPLE)
            buffer.compact()
            buffer.fillFrom(audio)
            if (processOutput.features.isEmpty())
                continue
            for (wakeWord in wakeWords) {
                val result = wakeWord.processAudioFeatures(processOutput.features)
                if (result && !detections.any { it.wakeWordId == wakeWord.id })
                    detections.add(DetectionResult(wakeWord.id, wakeWord.wakeWord))
            }
        }
        buffer.compact()
        return detections
    }

    override fun close() {
        frontend.close()
        for (model in wakeWords)
            model.close()
    }

    companion object {
        private const val TAG = "WakeWordDetector"
    }
}