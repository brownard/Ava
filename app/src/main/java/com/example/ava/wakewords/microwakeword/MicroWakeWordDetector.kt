package com.example.ava.wakewords.microwakeword

import com.example.ava.utils.fillFrom
import com.example.microfeatures.MicroFrontend
import java.nio.ByteBuffer

private const val SAMPLES_PER_SECOND = 16000
private const val SAMPLES_PER_CHUNK = 160  // 10ms
private const val BYTES_PER_SAMPLE = 2  // 16-bit
private const val BYTES_PER_CHUNK = SAMPLES_PER_CHUNK * BYTES_PER_SAMPLE

class MicroWakeWordDetector(private val wakeWords: List<MicroWakeWordModel>) : AutoCloseable {
    private val frontend = MicroFrontend()
    private val buffer = ByteBuffer.allocateDirect(BYTES_PER_CHUNK)

    fun detect(audio: ByteBuffer) = sequence {
        val detections = mutableSetOf<String>()
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
                if (result && !detections.contains(wakeWord.id)) {
                    detections.add(wakeWord.id)
                    yield(wakeWord)
                }
            }
        }
        buffer.compact()
    }

    override fun close() {
        frontend.close()
        for (model in wakeWords)
            model.close()
    }
}