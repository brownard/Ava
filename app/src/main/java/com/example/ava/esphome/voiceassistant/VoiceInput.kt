package com.example.ava.esphome.voiceassistant

import android.Manifest
import androidx.annotation.RequiresPermission
import com.example.ava.audio.MicrophoneInput
import com.example.ava.settings.SettingState
import com.example.ava.wakewords.microwakeword.MicroWakeWord
import com.example.ava.wakewords.microwakeword.MicroWakeWordDetector
import com.example.ava.wakewords.models.WakeWordWithId
import com.google.protobuf.ByteString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.yield
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow

sealed class AudioResult {
    data class Audio(val audio: ByteString) : AudioResult()
    data class WakeDetected(val wakeWord: String) : AudioResult()
    class StopDetected() : AudioResult()
}

interface VoiceInput {
    /**
     * The list of wake words available for selection.
     */
    suspend fun getAvailableWakeWords(): List<WakeWordWithId>

    /**
     * The list of stop words available for selection.
     */
    suspend fun getAvailableStopWords(): List<WakeWordWithId>

    /**
     * The list of currently active wake words.
     */
    val activeWakeWords: SettingState<List<String>>

    /**
     * The list of currently active stop words.
     */
    val activeStopWords: SettingState<List<String>>

    /**
     * Whether the microphone is muted.
     */
    val muted: SettingState<Boolean>

    /**
     * Whether the microphone is currently streaming audio.
     */
    var isStreaming: Boolean

    /**
     * Starts listening to audio from the microphone for wake word detection and streaming.
     * If an active wake word or stop word is detected, emits [AudioResult.WakeDetected] or
     * [AudioResult.StopDetected] respectively. If [isStreaming] is true, [AudioResult.Audio] is
     * also emitted with the raw audio data.
     */
    fun start(): Flow<AudioResult>
}

private data class AudioHardwareConfig(
    val audioSource: Int,
    val enableNoiseSuppressor: Boolean,
    val enableAutomaticGainControl: Boolean,
    val enableAcousticEchoCanceler: Boolean
)

private data class DetectorConfig(
    val activeWakeWords: List<String>,
    val activeStopWords: List<String>,
    val gainLinear: Float,
    val probabilityCutoffOverride: Float?,
    val slidingWindowSizeOverride: Int?
)

class VoiceInputImpl(
    private val availableWakeWords: Flow<List<WakeWordWithId>>,
    private val availableStopWords: Flow<List<WakeWordWithId>>,
    override val activeWakeWords: SettingState<List<String>>,
    override val activeStopWords: SettingState<List<String>>,
    override val muted: SettingState<Boolean>,
    private val audioSource: Flow<Int>,
    private val enableNoiseSuppressor: Flow<Boolean>,
    private val enableAutomaticGainControl: Flow<Boolean>,
    private val enableAcousticEchoCanceler: Flow<Boolean>,
    private val micGainDb: Flow<Int>,
    private val probabilityCutoffOverride: Flow<Float?>,
    private val slidingWindowSizeOverride: Flow<Int?>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : VoiceInput {
    override suspend fun getAvailableWakeWords() = availableWakeWords.first()
    override suspend fun getAvailableStopWords() = availableStopWords.first()

    private val _isStreaming = AtomicBoolean(false)
    override var isStreaming: Boolean
        get() = _isStreaming.get()
        set(value) = _isStreaming.set(value)

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start() = combine(
        muted, audioSource, enableNoiseSuppressor, enableAutomaticGainControl, enableAcousticEchoCanceler
    ) { isMuted, src, ns, agc, aec ->
        isMuted to AudioHardwareConfig(src, ns, agc, aec)
    }.flatMapLatest { (isMuted, hwConfig) ->
        if (isMuted) emptyFlow()
        else flow {
            MicrophoneInput(
                audioSource = hwConfig.audioSource,
                enableNoiseSuppressor = hwConfig.enableNoiseSuppressor,
                enableAutomaticGainControl = hwConfig.enableAutomaticGainControl,
                enableAcousticEchoCanceler = hwConfig.enableAcousticEchoCanceler
            ).use { microphoneInput ->
                microphoneInput.start()
                emitAll(
                    combine(
                        activeWakeWords,
                        activeStopWords,
                        micGainDb,
                        probabilityCutoffOverride,
                        slidingWindowSizeOverride
                    ) { ww, sw, gainDb, cutoff, window ->
                        DetectorConfig(
                            activeWakeWords = ww,
                            activeStopWords = sw,
                            gainLinear = 10f.pow(gainDb / 20f),
                            probabilityCutoffOverride = cutoff,
                            slidingWindowSizeOverride = window
                        )
                    }.flatMapLatest { detectorConfig ->
                        readMicrophone(microphoneInput, detectorConfig)
                    }
                )
            }
        }
    }.flowOn(dispatcher)

    private fun readMicrophone(
        microphoneInput: MicrophoneInput,
        config: DetectorConfig
    ) = flow {
        // Only enable far-field path when gain > 1.0 (i.e. micGainDb > 0)
        val hasFarField = config.gainLinear > 1.0f
        val boostedBuffer = if (hasFarField)
            ByteBuffer.allocate(microphoneInput.bufferSize).order(ByteOrder.LITTLE_ENDIAN)
        else null

        createDetector(
            config.activeWakeWords, config.activeStopWords,
            config.probabilityCutoffOverride, config.slidingWindowSizeOverride
        ).use { nearDetector ->
            // Far-field detector is a separate TFLite instance running on gained audio.
            // Both detectors maintain independent state so either path can trigger a wake.
            val farDetector = if (hasFarField) createDetector(
                config.activeWakeWords, config.activeStopWords,
                config.probabilityCutoffOverride, config.slidingWindowSizeOverride
            ) else null
            Timber.d("Created wake word detector(s), far-field: $hasFarField")
            try {
                while (true) {
                    val audio = microphoneInput.read()

                    if (boostedBuffer != null) {
                        applyGain(audio, boostedBuffer, config.gainLinear)
                    }

                    if (isStreaming) {
                        // Send the boosted version to HA unless it has clipped — in that case
                        // the person is close and the original audio is a better STT input.
                        val streamAudio =
                            if (boostedBuffer != null && !isClipped(boostedBuffer)) boostedBuffer
                            else audio
                        emit(AudioResult.Audio(ByteString.copyFrom(streamAudio)))
                    }

                    // Rewind before feeding detectors (ByteString.copyFrom or applyGain may
                    // have advanced position; applyGain uses duplicate so audio is untouched,
                    // but rewind is cheap and keeps the logic explicit).
                    audio.rewind()
                    boostedBuffer?.rewind()

                    val nearDetections = nearDetector.detect(audio)
                    val farDetections = farDetector?.detect(boostedBuffer!!) ?: emptyList()

                    val allDetections = (nearDetections + farDetections).distinctBy { it.wakeWordId }
                    for (detection in allDetections) {
                        if (detection.wakeWordId in config.activeWakeWords) {
                            emit(AudioResult.WakeDetected(detection.wakeWordPhrase))
                        } else if (detection.wakeWordId in config.activeStopWords) {
                            emit(AudioResult.StopDetected())
                        }
                    }

                    // Yield to keep this flow cancellable (see original comment).
                    yield()
                }
            } finally {
                farDetector?.close()
            }
        }
    }

    /**
     * Copies [source] into [dest] with each PCM-16 sample multiplied by [gainLinear], hard-clamped
     * to the 16-bit range. Uses a duplicate of [source] so the original position is not advanced.
     * [dest] is left with position=0 and limit=bytes written.
     */
    private fun applyGain(source: ByteBuffer, dest: ByteBuffer, gainLinear: Float) {
        dest.clear()
        val srcView = source.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val dstView = dest.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val count = srcView.remaining()
        for (i in 0 until count) {
            val sample = srcView.get()
            val boosted = (sample * gainLinear).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            dstView.put(boosted)
        }
        dest.limit(count * 2)
        dest.rewind()
    }

    /**
     * Returns true if more than 5 % of samples in [buffer] are hard-clipped (at ±32767/32768).
     * Uses a duplicate so [buffer]'s position is not advanced.
     */
    private fun isClipped(buffer: ByteBuffer): Boolean {
        val view = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val total = view.remaining()
        if (total == 0) return false
        var clippedCount = 0
        while (view.hasRemaining()) {
            val sample = view.get()
            if (sample == Short.MAX_VALUE || sample == Short.MIN_VALUE) clippedCount++
        }
        return clippedCount.toFloat() / total > 0.05f
    }

    private suspend fun createDetector(
        wakeWords: List<String>,
        stopWords: List<String>,
        probabilityCutoffOverride: Float?,
        slidingWindowSizeOverride: Int?
    ) = MicroWakeWordDetector(
        loadWakeWords(wakeWords, availableWakeWords.first(), probabilityCutoffOverride, slidingWindowSizeOverride) +
                loadWakeWords(stopWords, availableStopWords.first(), probabilityCutoffOverride, slidingWindowSizeOverride)
    )

    private suspend fun loadWakeWords(
        ids: List<String>,
        wakeWords: List<WakeWordWithId>,
        probabilityCutoffOverride: Float? = null,
        slidingWindowSizeOverride: Int? = null
    ): List<MicroWakeWord> = buildList {
        for (id in ids) {
            wakeWords.firstOrNull { it.id == id }?.let { wakeWord ->
                runCatching {
                    add(MicroWakeWord.fromWakeWord(wakeWord, probabilityCutoffOverride, slidingWindowSizeOverride))
                }.onFailure {
                    Timber.e(it, "Error loading wake word: $id")
                }
            }
        }
    }
}
