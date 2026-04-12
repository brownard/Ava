package com.example.ava.stubs

import com.example.ava.esphome.wakeword.WakeWord
import com.example.ava.wakewords.models.Micro
import com.example.ava.wakewords.models.WakeWordWithId
import java.nio.ByteBuffer

open class StubWakeWord : WakeWord {
    var wakeWords: List<WakeWordWithId> = emptyList()
    override suspend fun setWakeWords(wakeWords: List<WakeWordWithId>) {
        this.wakeWords = wakeWords
    }

    override fun clearWakeWords() {
        wakeWords = emptyList()
    }

    override fun detect(audio: ByteBuffer): List<String> {
        return wakeWords.map { it.id }
    }

    override fun close() {
        clearWakeWords()
    }
}

fun stubWakeWordWithId(id: String, wakeWord: String = "") = WakeWordWithId(
    id = id,
    wakeWord = com.example.ava.wakewords.models.WakeWord(
        type = "",
        wake_word = wakeWord,
        model = "",
        micro = Micro(
            probability_cutoff = 0f,
            sliding_window_size = 0
        )
    )
) { ByteBuffer.allocate(0) }