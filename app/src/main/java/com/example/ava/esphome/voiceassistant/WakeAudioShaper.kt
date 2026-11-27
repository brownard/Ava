package com.example.ava.esphome.voiceassistant

import com.google.protobuf.ByteString

/**
 * Shapes the microphone audio sent to the pipeline around a wake word detection, so that the words
 * said right after the wake word are not lost and the wake sound is not taken for the start of the
 * command. Audio is 16 bit mono at 16 kHz.
 *
 * - While idle, the last [historyMs] of audio are kept.
 * - On a detection the audio is held for [decideMs] to see whether the speech goes on right after
 *   the wake word.
 *   - If it does, the last [lookbackMs] before the detection are sent first, preceded by [leadInMs]
 *     of silence. The wake word model fires a little after the end of the wake word, and the start
 *     of the command said in the meantime would otherwise be lost. A longer lookback includes the
 *     wake word itself, which is only useful if the speech to text removes it.
 *   - If it does not (the user waits for the wake sound), quiet audio is sent as silence until the
 *     speech starts, keeping the last [keepMs] real so that the soft start of the first word is not
 *     cut. Otherwise the voice activity detection of the pipeline can take the wake sound for the
 *     start of the command and end it during the pause.
 *
 * A chunk counts as speech when its peak is at least four times the background level (the quieter
 * fifth of the history), and at least [minSpeechPeak].
 */
class WakeAudioShaper(
    private val lookbackMs: Int,
    private val historyMs: Int = 1000,
    private val decideMs: Int = 350,
    private val keepMs: Int = 200,
    private val leadInMs: Int = 300,
    private val minSpeechPeak: Int = 800
) {
    private enum class Mode { PASS_THROUGH, DECIDING, GATING }

    private val history = ArrayDeque<ByteString>()
    private var historySize = 0
    private var mode = Mode.PASS_THROUGH
    private var lookback: List<ByteString> = emptyList()
    private val pending = ArrayDeque<ByteString>()
    private var pendingSize = 0
    private var speechThreshold = minSpeechPeak

    /**
     * Audio received while not streaming: kept as history for the next detection.
     */
    fun idle(chunk: ByteString) {
        if (mode != Mode.PASS_THROUGH) {
            mode = Mode.PASS_THROUGH
            clearPending()
            lookback = emptyList()
        }
        if (chunk.isEmpty) return
        history.addLast(chunk)
        historySize += chunk.size()
        val maxSize = maxOf(historyMs, lookbackMs) * BYTES_PER_MS
        while (historySize - history.first().size() >= maxSize) {
            historySize -= history.removeFirst().size()
        }
    }

    /**
     * A wake word was detected and streaming starts now.
     */
    fun wake() {
        val peaks = history.map { it.peak() }.sorted()
        val background = if (peaks.isEmpty()) 0 else peaks[peaks.size / 5]
        speechThreshold = maxOf(minSpeechPeak, background * 4)
        lookback = history.takeLastBytes(lookbackMs * BYTES_PER_MS)
        history.clear()
        historySize = 0
        clearPending()
        mode = Mode.DECIDING
    }

    /**
     * Audio received while streaming: returns the audio to send for it, possibly none.
     */
    fun stream(chunk: ByteString): List<ByteString> = when (mode) {
        Mode.PASS_THROUGH -> listOf(chunk)

        Mode.DECIDING -> {
            addPending(chunk)
            when {
                chunk.peak() >= speechThreshold -> {
                    mode = Mode.PASS_THROUGH
                    val out = buildList {
                        if (lookback.isNotEmpty() && leadInMs > 0) add(silence(leadInMs * BYTES_PER_MS))
                        addAll(lookback)
                        addAll(pending)
                    }
                    lookback = emptyList()
                    clearPending()
                    out
                }

                pendingSize >= decideMs * BYTES_PER_MS -> {
                    mode = Mode.GATING
                    lookback = emptyList()
                    silenceOldPending()
                }

                else -> emptyList()
            }
        }

        Mode.GATING -> {
            addPending(chunk)
            if (chunk.peak() >= speechThreshold) {
                mode = Mode.PASS_THROUGH
                pending.toList().also { clearPending() }
            } else {
                silenceOldPending()
            }
        }
    }

    private fun addPending(chunk: ByteString) {
        pending.addLast(chunk)
        pendingSize += chunk.size()
    }

    private fun clearPending() {
        pending.clear()
        pendingSize = 0
    }

    /**
     * Sends as silence the pending audio older than [keepMs].
     */
    private fun silenceOldPending(): List<ByteString> {
        val out = mutableListOf<ByteString>()
        while (pending.isNotEmpty() && pendingSize - pending.first().size() >= keepMs * BYTES_PER_MS) {
            val old = pending.removeFirst()
            pendingSize -= old.size()
            out.add(silence(old.size()))
        }
        return out
    }

    companion object {
        /** 16 kHz, 16 bit mono */
        const val BYTES_PER_MS = 32

        private fun silence(size: Int): ByteString = ByteString.copyFrom(ByteArray(size))

        private fun List<ByteString>.takeLastBytes(size: Int): List<ByteString> {
            var skip = (sumOf { it.size() } - size).coerceAtLeast(0)
            return mapNotNull { chunk ->
                when {
                    skip >= chunk.size() -> { skip -= chunk.size(); null }
                    skip > 0 -> chunk.substring(skip).also { skip = 0 }
                    else -> chunk
                }
            }
        }

        /** Largest absolute value of the 16 bit little endian samples */
        private fun ByteString.peak(): Int {
            var max = 0
            var i = 0
            while (i + 1 < size()) {
                val sample = ((byteAt(i + 1).toInt() shl 8) or (byteAt(i).toInt() and 0xff)).toShort().toInt()
                val abs = if (sample < 0) -sample else sample
                if (abs > max) max = abs
                i += 2
            }
            return max
        }
    }
}
