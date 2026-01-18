package com.example.ava.wakewords.microwakeword

import android.util.Log
import com.example.ava.wakewords.models.WakeWordWithId
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer

private const val STRIDE = 3

class MicroWakeWord(
    val id: String,
    val wakeWord: String,
    model: ByteBuffer,
    private val probabilityCutoff: Float,
    private val slidingWindowSize: Int
) : AutoCloseable {
    private val interpreter: Interpreter = Interpreter(model)
    private val inputTensorBuffer: TensorBuffer
    private val outputScale: Float
    private val outputZeroPoint: Int
    private val probabilities = ArrayDeque<Float>(slidingWindowSize)

    init {
        interpreter.allocateTensors()

        val inputDetails = interpreter.getInputTensor(0)
        val inputQuantParams = inputDetails.quantizationParams()
        inputTensorBuffer = TensorBuffer.Companion.create(
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
    }

    companion object {
        private const val TAG = "MicroWakeWord"
        suspend fun fromWakeWord(wakeWord: WakeWordWithId): MicroWakeWord = MicroWakeWord(
            id = wakeWord.id,
            wakeWord = wakeWord.wakeWord.wake_word,
            model = wakeWord.load(),
            probabilityCutoff = wakeWord.wakeWord.micro.probability_cutoff,
            slidingWindowSize = wakeWord.wakeWord.micro.sliding_window_size
        )
    }
}