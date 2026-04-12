package com.example.ava.esphome.android.wakeword

import com.example.ava.esphome.wakeword.WakeWord
import com.example.ava.wakewords.microwakeword.MicroWakeWordDetector
import com.example.ava.wakewords.microwakeword.MicroWakeWordModel
import com.example.ava.wakewords.models.WakeWordWithId
import timber.log.Timber
import java.nio.ByteBuffer

class MicroWakeWord() : WakeWord {
    private var detector: MicroWakeWordDetector? = null

    override suspend fun setWakeWords(wakeWords: List<WakeWordWithId>) {
        clearWakeWords()
        Timber.d("Setting wake words ${wakeWords.map { it.id }}")
        detector = MicroWakeWordDetector(wakeWords.mapNotNull { it.toMicroWakeWordOrNull() })
    }

    override fun clearWakeWords() {
        detector?.let {
            it.close()
            detector = null
            Timber.d("Cleared wake words")
        }
    }

    override fun detect(audio: ByteBuffer) =
        detector?.detect(audio)?.map { it.id }?.toList() ?: emptyList()

    override fun close() {
        clearWakeWords()
    }

    private suspend fun WakeWordWithId.toMicroWakeWordOrNull() = runCatching {
        MicroWakeWordModel(
            id = id,
            model = load(),
            probabilityCutoff = wakeWord.micro.probability_cutoff,
            slidingWindowSize = wakeWord.micro.sliding_window_size
        )
    }.onFailure {
        Timber.e(it, "Error loading wake word: $id")
    }.getOrNull()
}