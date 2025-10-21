package com.example.ava.microwakeword

import android.util.Log
import com.example.microfeatures.MicroFrontend
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer

private const val SAMPLES_PER_SECOND = 16000
private const val SAMPLES_PER_CHUNK = 160  // 10ms
private const val BYTES_PER_SAMPLE = 2  // 16-bit
private const val BYTES_PER_CHUNK = SAMPLES_PER_CHUNK * BYTES_PER_SAMPLE
private const val SECONDS_PER_CHUNK = SAMPLES_PER_CHUNK / SAMPLES_PER_SECOND
private const val STRIDE = 3
private const val DEFAULT_REFRACTORY = 2  // seconds

class MicroWakeWord(
    val id: String,
    val wakeWord: String,
    model: ByteBuffer,
    private val probabilityCutoff: Float,
    private val slidingWindowSize: Int
) : AutoCloseable {
    private val frontend: MicroFrontend
    private val interpreter: Interpreter
    private val inputTensorBuffer: TensorBuffer
    private val outputScale: Float
    private val outputZeroPoint: Int
    private val probabilities = ArrayDeque<Float>(slidingWindowSize)

    init {
        frontend = MicroFrontend()
        interpreter = Interpreter(model)
        interpreter.allocateTensors()

        val inputDetails = interpreter.getInputTensor(0)
        val inputQuantParams = inputDetails.quantizationParams()
        inputTensorBuffer = TensorBuffer.create(
            inputDetails.dataType(),
            inputDetails.shape(),
            inputQuantParams.scale,
            inputQuantParams.zeroPoint
        )

        val outputDetails = interpreter.getOutputTensor(0)

        val outputQuantParams = outputDetails.quantizationParams()
        outputScale = outputQuantParams.scale
        outputZeroPoint = outputQuantParams.zeroPoint
    }

    fun processAudioFeatures(features: FloatArray): Boolean {
        if (features.isEmpty())
            return false
        if (features.size * STRIDE != inputTensorBuffer.flatSize)
            error("Unexpected feature size ${features.size} for stride $STRIDE and tensor size ${inputTensorBuffer.flatSize}")

        inputTensorBuffer.put(features)
        if (!inputTensorBuffer.isComplete)
            return false

        val probability = getWakeWordProbability(inputTensorBuffer.getTensor())
        inputTensorBuffer.clear()
        return isWakeWordDetected(probability)
    }

    private fun getWakeWordProbability(input: ByteBuffer): Float {
        val output = Array(1) { ByteArray(1) }
        interpreter.run(input, output)
        val probability = (output[0][0].toUByte().toFloat() - outputZeroPoint) * outputScale
        return probability
    }

    private fun isWakeWordDetected(probability: Float): Boolean {
        if (probability > 0.3)
            Log.d(TAG, "Probability: $probability")

        if (probabilities.size == slidingWindowSize)
            probabilities.removeFirst()
        probabilities.add(probability)
        return probabilities.size == slidingWindowSize && probabilities.average() > probabilityCutoff
    }

    override fun close() {
        interpreter.close()
        frontend.close()
    }

    companion object {
        val TAG = "MicroWakeWord"
    }
}