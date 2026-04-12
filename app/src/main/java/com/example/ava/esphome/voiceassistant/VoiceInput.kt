package com.example.ava.esphome.voiceassistant

import com.example.ava.esphome.microphone.Microphone
import com.example.ava.esphome.wakeword.WakeWord
import com.example.ava.settings.SettingState
import com.example.ava.wakewords.models.WakeWordWithId
import com.google.protobuf.ByteString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicBoolean

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

class VoiceInputImpl(
    private val microphone: Microphone,
    private val wakeWord: WakeWord,
    private val availableWakeWords: suspend () -> List<WakeWordWithId>,
    private val availableStopWords: suspend () -> List<WakeWordWithId>,
    override val activeWakeWords: SettingState<List<String>>,
    override val activeStopWords: SettingState<List<String>>,
    override val muted: SettingState<Boolean>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : VoiceInput {
    private val _isStreaming = AtomicBoolean(false)
    override var isStreaming: Boolean
        get() = _isStreaming.get()
        set(value) = _isStreaming.set(value)

    override suspend fun getAvailableWakeWords() = availableWakeWords()
    override suspend fun getAvailableStopWords() = availableStopWords()

    private val activeWakeWordModels =
        combine(activeWakeWords, activeStopWords) { activeWakeWords, activeStopWords ->
            Pair(
                getAvailableWakeWords().filter { it.id in activeWakeWords }.associateBy { it.id },
                getAvailableStopWords().filter { it.id in activeStopWords }.associateBy { it.id }
            )
        }

    override fun start() = muted.flatMapLatest { muted ->
        if (muted) emptyFlow()
        else activeWakeWordModels.flatMapLatest { (wakeWords, stopWords) ->
            channelFlow {
                while (isActive) {
                    val audio = microphone.read()
                    if (isStreaming) {
                        send(AudioResult.Audio(ByteString.copyFrom(audio)))
                        audio.rewind()
                    }
                    // Always run audio through the models, even if not currently streaming, to keep
                    // their internal state up to date
                    wakeWord.detect(audio).forEach {
                        wakeWords.get(it)?.let {
                            send(AudioResult.WakeDetected(it.wakeWord.wake_word))
                        } ?: stopWords.get(it)?.let {
                            send(AudioResult.StopDetected())
                        }
                    }
                }
            }.onStart {
                microphone.start()
                wakeWord.setWakeWords(wakeWords.values.toList() + stopWords.values.toList())
            }.onCompletion {
                microphone.stop()
                wakeWord.clearWakeWords()
            }.flowOn(dispatcher)
        }
    }
}