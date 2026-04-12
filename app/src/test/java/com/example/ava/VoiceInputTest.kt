package com.example.ava

import com.example.ava.esphome.microphone.Microphone
import com.example.ava.esphome.voiceassistant.AudioResult
import com.example.ava.esphome.voiceassistant.VoiceInputImpl
import com.example.ava.esphome.wakeword.WakeWord
import com.example.ava.settings.SettingState
import com.example.ava.stubs.StubMicrophone
import com.example.ava.stubs.StubWakeWord
import com.example.ava.stubs.stubSettingState
import com.example.ava.stubs.stubWakeWordWithId
import com.example.ava.wakewords.models.WakeWordWithId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceInputTest {
    fun createVoiceInput(
        microphone: Microphone = StubMicrophone(),
        wakeWord: WakeWord = StubWakeWord(),
        availableWakeWords: List<WakeWordWithId> = emptyList(),
        availableStopWords: List<WakeWordWithId> = emptyList(),
        activeWakeWords: List<String> = availableWakeWords.map { it.id },
        activeStopWords: List<String> = availableStopWords.map { it.id },
        muted: SettingState<Boolean> = stubSettingState(false),
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) = VoiceInputImpl(
        microphone = microphone,
        wakeWord = wakeWord,
        availableWakeWords = { availableWakeWords },
        availableStopWords = { availableStopWords },
        activeWakeWords = stubSettingState(activeWakeWords),
        activeStopWords = stubSettingState(activeStopWords),
        muted = muted,
        dispatcher = dispatcher
    )

    @Test
    fun should_close_microphone_when_flow_cancelled() = runTest {
        val started = CompletableDeferred<Unit>()
        var microphoneStopped = false
        val microphone = object : StubMicrophone() {
            override fun start() {
                started.complete(Unit)
            }

            override fun stop() {
                microphoneStopped = true
            }
        }
        val voiceInput = createVoiceInput(
            microphone = microphone
        )

        val job = voiceInput.start().launchIn(this)
        started.await()
        job.cancelAndJoin()

        assertTrue(microphoneStopped)
    }

    @Test
    fun should_clear_wake_words_when_flow_cancelled() = runTest {
        val started = CompletableDeferred<Unit>()
        var wakeWordsCleared = false
        val voiceInput = createVoiceInput(
            wakeWord = object : StubWakeWord() {
                override suspend fun setWakeWords(wakeWords: List<WakeWordWithId>) {
                    started.complete(Unit)
                    super.setWakeWords(wakeWords)
                }

                override fun clearWakeWords() {
                    wakeWordsCleared = true
                }
            }
        )

        val job = voiceInput.start().launchIn(this)
        started.await()
        job.cancelAndJoin()

        assertTrue(wakeWordsCleared)
    }

    @Test
    fun should_stop_microphone_and_clear_wake_words_when_muted() = runTest {
        val started = CompletableDeferred<Unit>()
        val microphoneStopped = CompletableDeferred<Unit>()
        val wakeWordsCleared = CompletableDeferred<Unit>()
        val voiceInput = createVoiceInput(
            microphone = object : StubMicrophone() {
                override fun start() {
                    started.complete(Unit)
                }

                override fun stop() {
                    microphoneStopped.complete(Unit)
                }
            },
            wakeWord = object : StubWakeWord() {
                override fun clearWakeWords() {
                    wakeWordsCleared.complete(Unit)
                }
            }
        )

        voiceInput.start().launchIn(backgroundScope)
        started.await()
        voiceInput.muted.set(true)

        microphoneStopped.await()
        wakeWordsCleared.await()
    }

    @Test
    fun should_emit_wake_detected_for_wake_word() = runTest {
        val wakeWords = listOf(
            stubWakeWordWithId(id = "wake1", wakeWord = "wake1"),
            stubWakeWordWithId(id = "wake2", wakeWord = "wake2")
        )
        val stopWords = listOf(stubWakeWordWithId(id = "stop", wakeWord = "stop"))
        val voiceInput = createVoiceInput(
            wakeWord = object : StubWakeWord() {
                override fun detect(audio: ByteBuffer): List<String> =
                    listOf(wakeWords[0]).map { it.id }
            },
            availableWakeWords = wakeWords,
            availableStopWords = stopWords
        )

        val result = voiceInput.start().first()
        assertEquals(
            (result as AudioResult.WakeDetected).wakeWord,
            wakeWords[0].wakeWord.wake_word
        )
    }

    @Test
    fun should_emit_stop_detected_for_stop_word() = runTest {
        val wakeWords = listOf(stubWakeWordWithId(id = "wake", wakeWord = "wake"))
        val stopWords = listOf(stubWakeWordWithId(id = "stop", wakeWord = "stop"))
        val voiceInput = createVoiceInput(
            wakeWord = object : StubWakeWord() {
                override fun detect(audio: ByteBuffer): List<String> = stopWords.map { it.id }
            },
            availableWakeWords = wakeWords,
            availableStopWords = stopWords
        )

        val result = voiceInput.start().first()
        assert(result is AudioResult.StopDetected)
    }
}