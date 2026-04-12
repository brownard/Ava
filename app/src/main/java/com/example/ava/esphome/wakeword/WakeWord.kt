package com.example.ava.esphome.wakeword

import com.example.ava.wakewords.models.WakeWordWithId
import java.nio.ByteBuffer

/**
 * Interface for detecting wake words in audio.
 */
interface WakeWord : AutoCloseable {
    /**
     * Sets the wake words to detect.
     */
    suspend fun setWakeWords(wakeWords: List<WakeWordWithId>)

    /**
     * Clears the wake words to detect.
     */
    fun clearWakeWords()

    /**
     * Detects wake words in the provided audio.
     * Returns a list of wake word ids that were detected.
     */
    fun detect(audio: ByteBuffer): List<String>
}